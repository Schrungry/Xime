package com.kingzcheung.xime.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kingzcheung.xime.settings.DictEntry
import com.kingzcheung.xime.settings.UserDictIoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class UserDictUiState(
    /** 本机用户词库（`<词典名>.userdb`），按名称升序。 */
    val dicts: List<UserDictIoManager.UserDict> = emptyList(),
    val isLoading: Boolean = true,
    /** 正在查看的词库名；null 表示停留在列表视图。 */
    val openedDict: String? = null,
    val entries: List<DictEntry> = emptyList(),
    val filteredEntries: List<DictEntry> = emptyList(),
    val searchQuery: String = "",
    /** 正在读取某本词库的词条（native 侧需销毁/重建输入会话，较慢）。 */
    val isReading: Boolean = false,
    /** 正在导出/导入（同样要销毁/重建输入会话）。 */
    val isTransferring: Boolean = false,
    /** 一次性结果提示（导出/导入的成败与条数），看完可关掉。 */
    val message: String? = null,
    val messageIsError: Boolean = false
)

/**
 * 「用户词库」页签：浏览打字造词产生的 `<词典名>.userdb`（只读）。
 * 读取走 [UserDictIoManager]（JNI 遍历 leveldb），不提供增删改。
 */
class UserDictViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(UserDictUiState())
    val uiState: StateFlow<UserDictUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** 重新列出本机用户词库。 */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val dicts = withContext(Dispatchers.IO) { UserDictIoManager.list(context) }
            _uiState.update { it.copy(dicts = dicts, isLoading = false) }
        }
    }

    /** 打开一本词库，读取其词条。 */
    fun open(dictName: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    openedDict = dictName,
                    isReading = true,
                    entries = emptyList(),
                    filteredEntries = emptyList(),
                    searchQuery = ""
                )
            }
            val entries = withContext(Dispatchers.IO) { UserDictIoManager.readEntries(dictName) }
            _uiState.update {
                // 读取期间用户可能已返回列表或改看别的库，丢弃过期结果
                if (it.openedDict != dictName) it
                else it.copy(
                    entries = entries,
                    filteredEntries = filterEntries(entries, ""),
                    isReading = false
                )
            }
        }
    }

    /** 返回词库列表。 */
    fun close() {
        _uiState.update {
            it.copy(
                openedDict = null,
                entries = emptyList(),
                filteredEntries = emptyList(),
                searchQuery = "",
                message = null
            )
        }
    }

    /**
     * 导出当前查看的词库到 SAF 目标（在文件选择器回调里调用）。
     * 格式为 librime 的文本码表（`词<TAB>码<TAB>频率` + `#@` 元数据头），与 PC 端互通。
     */
    fun exportTo(uri: Uri) {
        val dictName = _uiState.value.openedDict ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isTransferring = true, message = null) }
            val result = withContext(Dispatchers.IO) {
                UserDictIoManager.exportTo(context, dictName, uri)
            }
            _uiState.update {
                it.copy(
                    isTransferring = false,
                    message = result.fold(
                        onSuccess = { count -> "已导出 $count 条词条到所选文件" },
                        onFailure = { e -> "导出失败：${e.message}" }
                    ),
                    messageIsError = result.isFailure
                )
            }
        }
    }

    /**
     * 从 SAF 来源导入进当前查看的词库（合并语义，不会清空原有条目）。
     * 成功后重读词条，避免界面显示的还是旧内容。
     */
    fun importFrom(uri: Uri) {
        val dictName = _uiState.value.openedDict ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isTransferring = true, message = null) }
            val result = withContext(Dispatchers.IO) {
                UserDictIoManager.importFrom(context, dictName, uri)
            }
            val entries = if (result.isSuccess) {
                withContext(Dispatchers.IO) { UserDictIoManager.readEntries(dictName) }
            } else {
                null
            }
            _uiState.update { state ->
                // 期间用户可能已返回列表或改看别的库，丢弃过期结果
                if (state.openedDict != dictName) {
                    state
                } else {
                    state.copy(
                        isTransferring = false,
                        entries = entries ?: state.entries,
                        filteredEntries = entries?.let { filterEntries(it, state.searchQuery) }
                            ?: state.filteredEntries,
                        message = result.fold(
                            onSuccess = { count -> "已导入 $count 条词条（合并进「$dictName」）" },
                            onFailure = { e -> "导入失败：${e.message}" }
                        ),
                        messageIsError = result.isFailure
                    )
                }
            }
            if (result.isSuccess) {
                // userdb 被改写 ⇒ 列表里的时间戳（甚至新库）需要刷新
                refresh()
            }
        }
    }

    /** 关掉导出/导入的结果提示。 */
    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, filteredEntries = filterEntries(it.entries, query)) }
    }

    fun clearSearch() = setSearchQuery("")

    private fun filterEntries(entries: List<DictEntry>, query: String): List<DictEntry> {
        if (query.isEmpty()) return entries
        val lower = query.lowercase(Locale.ROOT)
        return entries.filter {
            it.word.contains(query) || it.code.contains(query, ignoreCase = true) ||
                it.code.lowercase(Locale.ROOT).contains(lower)
        }
    }
}