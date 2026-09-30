package com.kingzcheung.xime.settings

import android.content.Context
import android.net.Uri
import com.kingzcheung.xime.rime.RimeEngine
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * 用户词库访问：打字造词产生的 `<词典名>.userdb`（librime leveldb 用户词典）。
 *
 * 与「方案词库」（随方案分发的静态 `.dict.yaml`）是两类数据。librime 没有
 * "读词条"的 C 接口，只能经 JNI 用内部 DbSource 遍历并转成文本码表文本
 * （见 `rime_jni.cc::readUserDictText`）；导出/导入则走 librime 自己的
 * `UserDictManager::Export/Import` —— 与 PC 端（小狼毫/鼠须管）同一实现，
 * 生成的 `.txt` 码表可互通。
 */
object UserDictIoManager {

    /** 一本用户词库。 */
    data class UserDict(
        val name: String,
        /** `<词典名>.userdb` 目录最近修改时间（毫秒；取不到为 0）。 */
        val lastModified: Long
    )

    /** 导入文件大小上限：真人词库码表只有几十 KB，超过就是选错文件了。 */
    internal const val MAX_IMPORT_BYTES = 8 * 1024 * 1024

    private const val USERDB_EXTENSION = ".userdb"
    private const val EXPORT_TEMP_NAME = "userdict_export.txt"
    private const val IMPORT_TEMP_NAME = "userdict_import.txt"

    /**
     * 列出本机用户词库，按名称升序。
     *
     * 判定规则与 librime `UserDictManager::GetUserDictList` 一致：扫描用户数据目录下
     * 以 `.userdb` 结尾的条目，去掉扩展名即为词典名。这里走文件系统而非引擎接口，
     * 引擎尚未初始化时也能可靠列出。
     */
    fun list(context: Context): List<UserDict> {
        val rimeDir = SchemaManager.getRimeDir(context)
        val files = rimeDir.listFiles() ?: return emptyList()
        return files
            .filter { it.name.endsWith(USERDB_EXTENSION) }
            .map { UserDict(it.name.removeSuffix(USERDB_EXTENSION), it.lastModified()) }
            .sortedBy { it.name }
    }

    /**
     * 读取一本用户词库的词条（文本码表格式：`词<TAB>码<TAB>频率`，只读）。
     * 空列表表示该库暂无词条或读取失败（打不开、非 userdb）。
     */
    fun readEntries(dictName: String): List<DictEntry> {
        val text = RimeEngine.getInstance().readUserDictText(dictName)
        if (text.isBlank()) return emptyList()
        return DictionaryHelper.parseCodeTable(text)
    }

    /** 导出时的建议文件名：`<词典名>.txt`（Rime 的文本码表就是 `.txt`，如 custom_phrase.txt）。 */
    fun exportFileName(dictName: String): String = "$dictName.txt"

    /**
     * 导出到系统文件选择器选定的位置。
     *
     * librime 的 Export 只接受真实路径，故先写 cacheDir 临时文件，再把字节复制进 uri；
     * 无论成败都删临时文件。已标记删除的条目不会被写出。
     *
     * @return 导出的条目数
     */
    fun exportTo(context: Context, dictName: String, uri: Uri): Result<Int> = runCatching {
        val temp = File(context.cacheDir, EXPORT_TEMP_NAME)
        try {
            val count = RimeEngine.getInstance().exportUserDict(dictName, temp.absolutePath)
            if (count < 0) throw IOException("导出失败：打不开「$dictName」用户词库")
            context.contentResolver.openOutputStream(uri)?.use { output ->
                temp.inputStream().use { it.copyTo(output) }
            } ?: throw IOException("无法写入所选文件")
            count
        } finally {
            temp.delete()
        }
    }

    /**
     * 从系统文件选择器选定的文件导入进指定用户词库。
     *
     * **合并**语义（librime `UserDictImporter`）：同词条取较大频率、负频率视为删除标记，
     * 不会清空原有条目。导入前先用 [validateImportText] 挡住明显选错的文件 ——
     * 导入会真正改动用户词库，不能把二进制垃圾写进去。
     *
     * @return 成功解析并写入的条目数
     */
    fun importFrom(context: Context, dictName: String, uri: Uri): Result<Int> = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            readAllBytesLimited(input, MAX_IMPORT_BYTES)
        } ?: throw IOException("无法读取所选文件")
        if (bytes.size > MAX_IMPORT_BYTES) {
            throw IOException("文件过大（超过 ${MAX_IMPORT_BYTES / 1024 / 1024} MB），不像是码表")
        }
        val text = bytes.toString(Charsets.UTF_8)
        validateImportText(text)?.let { throw IOException(it) }

        val temp = File(context.cacheDir, IMPORT_TEMP_NAME)
        try {
            temp.writeText(text, Charsets.UTF_8)
            val count = RimeEngine.getInstance().importUserDict(dictName, temp.absolutePath)
            if (count < 0) throw IOException("导入失败：引擎拒绝了这个文件")
            count
        } finally {
            temp.delete()
        }
    }

    /**
     * 判断文本是否像 Rime 文本码表（`词<TAB>码[<TAB>频率]`）。
     * 返回 null 表示通过，否则返回给用户看的错误说明。
     *
     * 规则刻意宽松（`#` 注释行与无制表符的行都忽略，方案 `.dict.yaml` 的数据段也能导入），
     * 只挡住"空文件 / 二进制文件 / 完全没有码表行"这三种典型误选。
     */
    internal fun validateImportText(text: String): String? {
        if (text.isBlank()) return "文件是空的"
        if (text.contains('\u0000')) return "不是文本文件（含有空字节），请选择导出的 .txt 码表"
        val entries = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith('#') }
            .count { line ->
                val parts = line.split('\t')
                parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()
            }
        if (entries == 0) return "没找到「词<制表符>码」格式的词条，请选择导出的 .txt 码表"
        return null
    }

    /**
     * 读取至多 [limit] 字节；若源更长，则只多读 1 字节返回（`size > limit` 即超限），
     * 由调用方判超。手写而不用 `InputStream.readNBytes`：后者要 API 33。
     */
    internal fun readAllBytesLimited(input: InputStream, limit: Int): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            buffer.write(chunk, 0, minOf(read, limit + 1 - buffer.size()))
            if (buffer.size() > limit) break
        }
        return buffer.toByteArray()
    }
}