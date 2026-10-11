package com.kingzcheung.xime.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/**
 * 将 Rime 的文本快照镜像到用户通过 Storage Access Framework 选择的目录。
 * 外部目录直接保存 sync 目录中的快照，Rime 的 userdb 仍保留在应用私有目录。
 */
internal object SafSyncDirectory {
    private const val TEMP_SUFFIX = ".xime-tmp"
    private const val MIME_DIRECTORY = "vnd.android.document/directory"
    private const val MIME_BINARY = "application/octet-stream"

    private data class Child(val uri: Uri, val name: String, val isDirectory: Boolean)

    fun importIntoLocal(context: Context, treeUri: Uri, localSyncDir: File): Result<Int> = runCatching {
        val rootUri = rootDocumentUri(treeUri)
        localSyncDir.mkdirs()
        var count = 0
        walk(context, treeUri, rootUri, "") { uri, relativePath ->
            if (relativePath.endsWith(TEMP_SUFFIX) || !relativePath.endsWith(".userdb.txt")) return@walk
            val target = checkedTarget(localSyncDir, relativePath)
            target.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("无法读取外部同步文件：$relativePath")
            count++
        }
        count
    }

    fun exportFromLocal(context: Context, treeUri: Uri, localSyncDir: File): Result<Int> = runCatching {
        val rootUri = rootDocumentUri(treeUri)
        if (!localSyncDir.isDirectory) return@runCatching 0
        var count = 0
        localSyncDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".userdb.txt") }
            .forEach { source ->
            val relativePath = source.relativeTo(localSyncDir).path.replace(File.separatorChar, '/')
            val parts = relativePath.split('/').filter { it.isNotEmpty() }
            require(parts.none { it == "." || it == ".." }) { "非法同步文件路径：$relativePath" }
            var parent = rootUri
            parts.dropLast(1).forEach { part ->
                parent = findOrCreateDirectory(context, treeUri, parent, part)
            }
            writeFile(context, treeUri, parent, parts.last(), source)
            count++
        }
        count
    }

    private fun rootDocumentUri(treeUri: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )

    private fun findOrCreateDirectory(
        context: Context,
        treeUri: Uri,
        parentUri: Uri,
        name: String
    ): Uri {
        findChild(context, treeUri, parentUri, name)?.let { child ->
            require(child.isDirectory) { "外部同步路径不是目录：$name" }
            return child.uri
        }
        return DocumentsContract.createDocument(
            context.contentResolver,
            parentUri,
            MIME_DIRECTORY,
            name
        ) ?: error("无法创建外部同步目录：$name")
    }

    private fun findChild(context: Context, treeUri: Uri, parentUri: Uri, name: String): Child? =
        queryChildren(context, treeUri, parentUri).firstOrNull { it.name == name }

    private fun queryChildren(context: Context, treeUri: Uri, parentUri: Uri): List<Child> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(parentUri)
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val result = mutableListOf<Child>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idColumn) ?: continue
                val name = cursor.getString(nameColumn) ?: continue
                val mime = cursor.getString(mimeColumn)
                result += Child(
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                    name = name,
                    isDirectory = mime == MIME_DIRECTORY
                )
            }
        }
        return result
    }

    private fun walk(
        context: Context,
        treeUri: Uri,
        directoryUri: Uri,
        prefix: String,
        onFile: (Uri, String) -> Unit
    ) {
        queryChildren(context, treeUri, directoryUri).forEach { child ->
            val relativePath = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
            if (child.isDirectory) walk(context, treeUri, child.uri, relativePath, onFile)
            else onFile(child.uri, relativePath)
        }
    }

    private fun checkedTarget(baseDir: File, relativePath: String): File {
        val target = File(baseDir, relativePath)
        val base = baseDir.canonicalPath + File.separator
        require(target.canonicalPath.startsWith(base)) { "非法同步文件路径：$relativePath" }
        return target
    }

    private fun writeFile(
        context: Context,
        treeUri: Uri,
        parentUri: Uri,
        name: String,
        source: File
    ) {
        val resolver = context.contentResolver
        val existing = findChild(context, treeUri, parentUri, name)
        require(existing == null || !existing.isDirectory) { "外部同步文件与目录冲突：$name" }
        val target = existing?.uri ?: DocumentsContract.createDocument(
            resolver,
            parentUri,
            MIME_BINARY,
            name
        ) ?: error("无法创建外部同步文件：$name")
        resolver.openOutputStream(target, "wt")?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: error("无法写入外部同步文件：$name")
    }
}
