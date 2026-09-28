package com.kingzcheung.xime.clipboard.sync

import android.util.Log
import com.kingzcheung.xime.clipboard.ClipboardManager
import com.kingzcheung.xime.plugin.core.api.ClipboardProfile
import com.kingzcheung.xime.plugin.core.api.ClipboardSyncPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * 剪贴板同步引擎（仿 ximed SyncEngine）。
 *
 * 规则（与 ximed 设计一致的三通道去重语义）：
 * - 本地剪贴板变化 → 与上次推送 hash 不同且非"自写内容" → 推送到远端
 * - 拉取远端（启动时 + 键盘显示时 pullOnce）→ 与本地 hash 及"自写 hash"比对 → 不同才写回本地剪贴板
 * - 自写抑制：引擎写回本地后记录 hash，监听到同一 hash 判定为回声，跳过推送
 *
 * 不再做后台轮询：拉取只在启动时和键盘显示（[pullOnce]）时各触发一次，
 * 避免 IME service 生命周期内频繁请求；推送仍由本地剪贴板变化实时触发。
 *
 * 宿主只持有引擎与剪贴板桥，具体传输协议（WebDAV/S3/ximed）由 [ClipboardSyncPlugin]
 * 的 Lua 实现承载。
 */
class ClipboardSyncBridge(
    private val clipboardManager: ClipboardManager,
    private val plugin: ClipboardSyncPlugin,
    val pluginId: String = "",
    /**
     * 拉取最小间隔（秒）的配置读取器，由宿主接入所选插件的 configStore
     * （key 为 [Companion.CONFIG_KEY_PULL_INTERVAL_SECONDS]，插件 settings.schema
     * 声明同名 NUMBER 字段）。每次 [pullOnce] 时读取，插件设置改后即时生效；
     * 未配置/非法值回退 [Companion.DEFAULT_PULL_MIN_INTERVAL_MS]。
     */
    private val pullIntervalSeconds: () -> String? = { null }
) {
    companion object {
        private const val TAG = "ClipboardSync"
        private const val PUSH_RETRY_BACKOFF_MS = 5_000L

        /** 拉取间隔配置在插件 configStore 中的 key（插件 settings.schema 需声明同名 NUMBER 字段）。 */
        const val CONFIG_KEY_PULL_INTERVAL_SECONDS = "pull_interval_seconds"

        /** 配置允许的范围（秒）：下限为纯防抖，上限防误填导致拉取停摆。 */
        const val PULL_INTERVAL_SECONDS_MIN = 1L
        const val PULL_INTERVAL_SECONDS_MAX = 600L

        /**
         * 拉取最小间隔默认值：键盘每次显示都会触发 [pullOnce]，高频切换时请求数会轻易
         * 超出 WebDAV 服务的限流阈值（坚果云免费版每 30 分钟仅允许 600 次请求，
         * 超限返回 503 且需等待解封），默认保守节流；用户可经插件配置下调。
         */
        const val DEFAULT_PULL_MIN_INTERVAL_MS = 30_000L

        /** 解析插件配置的拉取间隔（秒）→ 毫秒；空/非法回退默认，越界 clamp。 */
        fun resolvePullIntervalMs(rawSeconds: String?): Long {
            val seconds = rawSeconds?.trim()?.toLongOrNull()
                ?: return DEFAULT_PULL_MIN_INTERVAL_MS
            return seconds.coerceIn(PULL_INTERVAL_SECONDS_MIN, PULL_INTERVAL_SECONDS_MAX) * 1000L
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 上次推送/写入的 hash（去重）。 */
    @Volatile
    private var lastHash: String? = null

    /** 自写 hash：引擎写入本地剪贴板的内容 hash，用于回声抑制。 */
    @Volatile
    private var selfWritten: String? = null

    @Volatile
    private var running = false

    /** 推送失败后的退避截止时间。 */
    @Volatile
    private var retryUntil = 0L

    /** 上次真正发起拉取的时间（pullOnce 节流基准）。 */
    @Volatile
    private var lastPullAt = 0L

    private var collectJob: Job? = null

    fun start() {
        if (running) return
        running = true
        Log.d(TAG, "Sync started")

        // 1. 订阅本地剪贴板变化 → push（回声抑制：selfWritten 命中的跳过）
        collectJob = clipboardManager.clipboardChanged
            .filter { it.text.isNotBlank() }
            .onEach { item ->
                val hash = ClipboardProfile.sha256Hex(item.text.toByteArray(Charsets.UTF_8))
                if (hash == selfWritten) {
                    Log.d(TAG, "Echo suppressed (self-written)")
                    return@onEach
                }
                pushLocal(item.text, hash)
            }
            .launchIn(scope)

        // 2. 启动即拉取一次（不受节流限制）；后续由键盘显示时 pullOnce() 触发
        scope.launch {
            lastPullAt = System.currentTimeMillis()
            pullRemote()
        }
    }

    fun stop() {
        if (!running) return
        running = false
        collectJob?.cancel()
        collectJob = null
        Log.d(TAG, "Sync stopped")
    }

    fun release() {
        stop()
        scope.cancel()
    }

    /** 键盘显示时触发一次拉取；节流：间隔内的触发直接跳过，不发起请求。 */
    fun pullOnce() {
        if (!running) return
        val now = System.currentTimeMillis()
        val minIntervalMs = resolvePullIntervalMs(pullIntervalSeconds())
        if (now - lastPullAt < minIntervalMs) {
            Log.d(TAG, "Pull throttled (min interval ${minIntervalMs / 1000}s)")
            return
        }
        lastPullAt = now
        scope.launch {
            pullRemote()
        }
    }

    private suspend fun pushLocal(text: String, hash: String) {
        if (!running) return
        if (hash == lastHash) {
            Log.d(TAG, "No change, skip push")
            return
        }
        if (System.currentTimeMillis() < retryUntil) {
            Log.d(TAG, "Push backoff active, skip")
            return
        }
        val profile = ClipboardProfile.fromText(text)
        val ok = try {
            plugin.push(profile)
        } catch (e: Exception) {
            Log.e(TAG, "push failed", e)
            false
        }
        if (ok) {
            lastHash = hash
            retryUntil = 0L
        } else {
            retryUntil = System.currentTimeMillis() + PUSH_RETRY_BACKOFF_MS
        }
    }

    private suspend fun pullRemote() {
        val remote = try {
            plugin.pull()
        } catch (e: Exception) {
            Log.e(TAG, "pull failed", e)
            null
        }
        if (remote == null) return

        val remoteHash = remote.hash
        val currentClipboard = clipboardManager.getCurrentClipboardText()
        val currentHash = currentClipboard?.let {
            ClipboardProfile.sha256Hex(it.toByteArray(Charsets.UTF_8))
        }

        // 与本地当前内容相同 或 与自己写回的内容相同 → 跳过（避免循环）
        if (remoteHash == currentHash || remoteHash == selfWritten) {
            Log.d(TAG, "Remote unchanged vs local, skip write")
            return
        }

        Log.d(TAG, "Remote changed, writing to local clipboard")
        lastHash = remoteHash
        selfWritten = remoteHash
        clipboardManager.copyToSystemClipboard(remote.text)
    }
}
