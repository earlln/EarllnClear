package com.earlln.clear.data

import android.os.Environment
import java.io.File

object JunkFinder {
    private val tempExts = setOf("tmp", "temp", "crdownload", "part", "partial", "download")

    /** 지우면 다른 앱이 헷갈릴 수 있는 표준 폴더(비어 있어도 유지) */
    private val protectedTopDirs = setOf(
        Environment.DIRECTORY_DCIM, Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_PICTURES,
        Environment.DIRECTORY_MUSIC, Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_DOCUMENTS,
        Environment.DIRECTORY_ALARMS, Environment.DIRECTORY_NOTIFICATIONS, Environment.DIRECTORY_PODCASTS,
        Environment.DIRECTORY_RINGTONES, "Recordings", "Audiobooks", "Android",
    )

    fun isTemp(f: FileItem): Boolean {
        if (f.name == ".nomedia") return false
        if (f.extension in tempExts) return true
        if (f.name.endsWith("~")) return true
        return f.parent.split('/').any { it == ".thumbnails" }
    }

    fun find(scan: ScanResult): JunkResult {
        val root = StorageScanner.root.absolutePath
        val temp = scan.files.filter { isTemp(it) }

        // 깊은 폴더부터 확인: 하위가 전부 "빈 폴더"인 폴더도 빈 폴더로 본다.
        val empty = HashSet<String>()
        for (d in scan.dirs.sortedByDescending { it.count { c -> c == '/' } }) {
            val rel = d.removePrefix("$root/")
            if (rel.startsWith("Android/") || rel in protectedTopDirs) continue
            val children = File(d).listFiles() ?: continue
            if (children.all { it.isDirectory && it.absolutePath in empty }) empty.add(d)
        }
        // 상위 폴더가 이미 목록에 있으면 하위는 표시에서 생략
        val top = empty.filter { p -> empty.none { o -> o != p && p.startsWith("$o/") } }.sorted()
        return JunkResult(top, temp.sortedByDescending { it.size })
    }

    /** 빈 폴더 삭제(내용이 없으므로 휴지통을 거치지 않음). 삭제된 개수 반환 */
    fun deleteEmptyDirs(paths: List<String>): Int {
        var count = 0
        for (p in paths) {
            if (deleteIfEmptyTree(File(p))) count++
        }
        return count
    }

    private fun deleteIfEmptyTree(dir: File): Boolean {
        val children = dir.listFiles() ?: return false
        for (c in children) {
            if (!c.isDirectory || !deleteIfEmptyTree(c)) return false
        }
        return dir.delete()
    }
}
