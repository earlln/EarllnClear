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

    fun scan(skipHidden: Boolean = false, onProgress: (Int) -> Unit): ScanResult {
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
                    if (!isExcluded(c) && !(skipHidden && c.name.startsWith("."))) {
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

    data class FolderCount(val path: String, val fileCount: Int) {
        val relative: String get() = path.removePrefix(root.absolutePath + "/")
    }

    /**
     * 자동 분류 대상 폴더: 바로 아래에 (숨김이 아닌) 파일이 있는 폴더.
     * - 숨김 폴더, Android 폴더 안은 항상 제외
     * - includeSubfolders=false 이면 최상위 폴더만
     * - excludeCamera=true 이면 DCIM(카메라) 폴더 전체 제외
     */
    fun organizableFolders(scan: ScanResult, includeSubfolders: Boolean, excludeCamera: Boolean): List<FolderCount> {
        val rootPath = root.absolutePath
        return scan.files.asSequence()
            .filter { !it.name.startsWith(".") }
            .groupingBy { it.parent }.eachCount()
            .mapNotNull { (dir, n) ->
                if (!dir.startsWith("$rootPath/")) return@mapNotNull null
                val segs = dir.removePrefix("$rootPath/").split('/')
                when {
                    segs.first() == "Android" -> null
                    segs.any { it.startsWith(".") } -> null
                    !includeSubfolders && segs.size > 1 -> null
                    excludeCamera && segs.first() == Environment.DIRECTORY_DCIM -> null
                    else -> FolderCount(dir, n)
                }
            }
            .sortedWith(
                compareBy<FolderCount>({ if (it.relative == Environment.DIRECTORY_DOWNLOADS) 0 else 1 })
                    .thenByDescending { it.fileCount }
                    .thenBy { it.relative.lowercase() }
            )
    }
}
