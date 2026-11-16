package com.earlln.clear.data

import android.content.Context
import android.media.MediaScannerConnection
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

data class TrashEntry(
    val id: String,
    val originalPath: String,
    val size: Long,
    val deletedAt: Long,
) {
    val name: String get() = originalPath.substringAfterLast('/')
    fun daysLeft(keepDays: Int): Int {
        val elapsed = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - deletedAt)
        return (keepDays - elapsed).toInt().coerceAtLeast(0)
    }
}

/**
 * 앱 전용 휴지통. 파일을 내부 저장소의 숨김 폴더로 "이동"만 하므로
 * 보관 기간(설정, 기본 30일) 안에는 언제든 원래 위치로 복원할 수 있다.
 * 색인(index.json)도 휴지통 폴더 안에 두어 앱을 재설치해도 유지된다.
 */
class TrashManager(private val context: Context) {

    companion object {
        val trashDir: File get() = File(StorageScanner.root, ".EarllnClearTrash")
        private val lock = Any()
    }

    private val indexFile get() = File(trashDir, "index.json")

    private fun ensureDir() {
        if (!trashDir.exists()) trashDir.mkdirs()
        val nomedia = File(trashDir, ".nomedia")
        if (!nomedia.exists()) runCatching { nomedia.createNewFile() }
    }

    fun list(): List<TrashEntry> = synchronized(lock) { load() }.sortedByDescending { it.deletedAt }

    private fun load(): MutableList<TrashEntry> {
        if (!indexFile.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(indexFile.readText())
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                TrashEntry(o.getString("id"), o.getString("path"), o.getLong("size"), o.getLong("at"))
            }.filter { File(trashDir, it.id).exists() }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun save(list: List<TrashEntry>) {
        ensureDir()
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("path", it.originalPath).put("size", it.size).put("at", it.deletedAt))
        }
        val tmp = File(trashDir, "index.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(indexFile)
    }

    /** 파일들을 휴지통으로 이동. 성공한 원래 경로 목록을 반환 */
    fun moveToTrash(paths: Collection<String>): Set<String> = synchronized(lock) {
        ensureDir()
        val list = load()
        val moved = HashSet<String>()
        for (p in paths) {
            val src = File(p)
            if (!src.isFile) continue
            val id = UUID.randomUUID().toString()
            val dst = File(trashDir, id)
            val size = src.length()
            if (moveFile(src, dst)) {
                list.add(TrashEntry(id, src.absolutePath, size, System.currentTimeMillis()))
                moved.add(p)
            }
        }
        save(list)
        scanMedia(moved)
        moved
    }

    /** 원래 위치로 복원. 같은 이름이 있으면 "(복원)"을 붙인다. 복원된 개수 반환 */
    fun restore(ids: Collection<String>): Int = synchronized(lock) {
        val list = load()
        val restored = ArrayList<String>()
        val remain = list.filterNot { e ->
            if (e.id !in ids) return@filterNot false
            val target = uniqueFile(File(e.originalPath), "복원")
            target.parentFile?.mkdirs()
            val ok = moveFile(File(trashDir, e.id), target)
            if (ok) restored.add(target.absolutePath)
            ok
        }
        save(remain)
        scanMedia(restored)
        restored.size
    }

    fun deleteForever(ids: Collection<String>): Int = synchronized(lock) {
        val list = load()
        var n = 0
        val remain = list.filterNot { e ->
            if (e.id !in ids) return@filterNot false
            val f = File(trashDir, e.id)
            val ok = !f.exists() || f.delete()
            if (ok) n++
            ok
        }
        save(remain)
        n
    }

    /** 보관 기간이 지난 항목 영구 삭제 */
    fun purgeExpired(keepDays: Int = SettingsStore.current.trashDays): Int {
        val limit = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(keepDays.toLong())
        val expired = synchronized(lock) { load() }.filter { it.deletedAt < limit }.map { it.id }
        return if (expired.isEmpty()) 0 else deleteForever(expired)
    }

    private fun moveFile(src: File, dst: File): Boolean {
        if (src.renameTo(dst)) return true
        // 다른 저장 장치(SD카드 등) 간 이동은 복사 후 삭제
        return try {
            src.copyTo(dst, overwrite = false)
            if (dst.length() == src.length() && src.delete()) true else {
                dst.delete(); false
            }
        } catch (e: Exception) {
            dst.delete(); false
        }
    }

    private fun scanMedia(paths: Collection<String>) {
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
    }
}

/** 이미 같은 이름이 있으면 "이름 (태그 1).확장자" 형태로 겹치지 않는 파일을 돌려준다. */
fun uniqueFile(target: File, tag: String = ""): File {
    if (!target.exists()) return target
    val base = target.nameWithoutExtension
    val ext = target.extension.let { if (it.isEmpty()) "" else ".$it" }
    var i = 1
    while (true) {
        val label = if (tag.isEmpty()) "$i" else "$tag $i"
        val f = File(target.parentFile, "$base ($label)$ext")
        if (!f.exists()) return f
        i++
    }
}
