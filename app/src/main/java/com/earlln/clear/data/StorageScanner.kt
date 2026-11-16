package com.earlln.clear.data

import android.os.Environment
import android.os.StatFs
import java.io.File

object StorageScanner {
    val root: File get() = Environment.getExternalStorageDirectory()

    /** 시스템/앱 전용 영역과 앱 휴지통은 건드리지 않는다. */
    fun isExcluded(dir: File): Boolean {
        val rootPath = root.absolutePath
        val p = dir.absolutePath
        return p == "$rootPath/Android/data" ||
            p == "$rootPath/Android/obb" ||
            p == "$rootPath/Android/media" ||
            p == TrashManager.trashDir.absolutePath
    }

    fun scan(onProgress: (Int) -> Unit): ScanResult {
        val files = ArrayList<FileItem>(4096)
        val dirs = ArrayList<String>(1024)
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        var lastReport = 0
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (c in children) {
                if (c.isDirectory) {
                    if (!isExcluded(c)) {
                        dirs.add(c.absolutePath)
                        stack.addLast(c)
                    }
                } else if (c.isFile) {
                    files.add(FileItem.from(c))
                }
            }
            if (files.size - lastReport >= 200) {
                lastReport = files.size
                onProgress(files.size)
            }
        }
        onProgress(files.size)
        return ScanResult(files, dirs)
    }

    data class StorageInfo(val total: Long, val free: Long) {
        val used: Long get() = total - free
    }

    fun storageInfo(): StorageInfo = try {
        val s = StatFs(root.absolutePath)
        StorageInfo(s.totalBytes, s.availableBytes)
    } catch (e: Exception) {
        StorageInfo(0, 0)
    }

    data class FolderCount(val path: String, val name: String, val fileCount: Int)

    /** 자동 분류 대상: 바로 아래에 (숨김이 아닌) 파일이 있는 최상위 폴더만 */
    fun organizableFolders(): List<FolderCount> {
        val list = root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") && f.name != "Android" }
            ?.mapNotNull { d ->
                val n = d.listFiles { f -> f.isFile && !f.name.startsWith(".") }?.size ?: 0
                if (n > 0) FolderCount(d.absolutePath, d.name, n) else null
            }
            ?.sortedBy { it.name.lowercase() } ?: emptyList()
        // Download 폴더를 맨 앞에
        return list.sortedBy { if (it.name == Environment.DIRECTORY_DOWNLOADS) 0 else 1 }
    }
}
