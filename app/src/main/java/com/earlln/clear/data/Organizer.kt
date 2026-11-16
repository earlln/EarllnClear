package com.earlln.clear.data

import android.content.Context
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class OrganizeMode(val label: String) {
    BY_TYPE("종류별"),
    BY_DATE("날짜별(연-월)"),
    BY_TYPE_AND_DATE("종류 + 날짜별"),
}

data class MovePlan(val from: String, val to: String) {
    val name: String get() = from.substringAfterLast('/')
    val destFolder: String get() = to.substringBeforeLast('/')
}

/**
 * 선택한 폴더 "바로 아래"의 파일만 하위 폴더로 분류한다.
 * (이미 분류된 하위 폴더는 다시 건드리지 않음)
 * 마지막 실행 내역을 저장해 두어 한 번에 되돌릴 수 있다.
 */
class Organizer(private val context: Context) {
    private val logFile get() = File(context.filesDir, "organize_last.json")
    private val monthFmt = SimpleDateFormat("yyyy-MM", Locale.ROOT)

    fun plan(sourceDir: File, mode: OrganizeMode, dateSource: DateSource = DateSource.TAKEN_FIRST): List<MovePlan> {
        val files = sourceDir.listFiles { f -> f.isFile && !f.name.startsWith(".") } ?: return emptyList()
        return files.sortedBy { it.name.lowercase() }.map { f ->
            val item = FileItem.from(f)
            val date = if (mode != OrganizeMode.BY_TYPE && dateSource == DateSource.TAKEN_FIRST) {
                takenDate(item) ?: item.lastModified
            } else {
                item.lastModified
            }
            val month = monthFmt.format(Date(date))
            val sub = when (mode) {
                OrganizeMode.BY_TYPE -> item.category.label
                OrganizeMode.BY_DATE -> month
                OrganizeMode.BY_TYPE_AND_DATE -> "${item.category.label}/$month"
            }
            MovePlan(f.absolutePath, File(File(sourceDir, sub), f.name).absolutePath)
        }
    }

    /** 사진(EXIF)·동영상(메타데이터)의 촬영일. 없으면 null */
    private fun takenDate(item: FileItem): Long? = try {
        when (item.category) {
            FileCategory.IMAGE -> {
                val exif = ExifInterface(item.path)
                val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                raw?.let { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ROOT).parse(it)?.time }
            }
            FileCategory.VIDEO -> {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(item.path)
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)?.let {
                        SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.ROOT).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }.parse(it.take(15))?.time
                    }
                } finally {
                    r.release()
                }
            }
            else -> null
        }?.takeIf { it > 31_536_000_000L } // 1971년 이전(잘못된 값)은 무시
    } catch (e: Exception) {
        null
    }

    /** 계획대로 이동. 이동된 개수 반환 */
    fun execute(plans: List<MovePlan>): Int {
        val done = JSONArray()
        val scanPaths = ArrayList<String>()
        for (p in plans) {
            val src = File(p.from)
            if (!src.isFile) continue
            val destDir = File(p.destFolder)
            destDir.mkdirs()
            val dst = uniqueFile(File(p.to))
            if (src.renameTo(dst)) {
                done.put(JSONObject().put("from", src.absolutePath).put("to", dst.absolutePath))
                scanPaths += src.absolutePath
                scanPaths += dst.absolutePath
            }
        }
        if (done.length() > 0) logFile.writeText(done.toString())
        scan(scanPaths)
        return done.length()
    }

    fun hasUndo(): Boolean = logFile.exists()

    /** 마지막 자동 분류를 되돌린다. 되돌린 개수 반환 */
    fun undoLast(): Int {
        if (!logFile.exists()) return 0
        val arr = runCatching { JSONArray(logFile.readText()) }.getOrNull() ?: return 0
        var n = 0
        val scanPaths = ArrayList<String>()
        val touchedDirs = HashSet<File>()
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.getJSONObject(i)
            val from = File(o.getString("from"))
            val to = File(o.getString("to"))
            if (!to.isFile) continue
            val back = uniqueFile(from)
            if (to.renameTo(back)) {
                n++
                scanPaths += to.absolutePath
                scanPaths += back.absolutePath
                // 원래 폴더(from의 상위)보다 아래에 생긴 분류 폴더만 정리 대상
                val base = from.parentFile?.absolutePath
                var d = to.parentFile
                while (d != null && base != null && d.absolutePath.startsWith("$base/")) {
                    touchedDirs += d
                    d = d.parentFile
                }
            }
        }
        // 분류 때문에 생긴 빈 폴더 정리 (깊은 폴더부터)
        touchedDirs.sortedByDescending { it.absolutePath.length }.forEach { d ->
            if (d.listFiles()?.isEmpty() == true) d.delete()
        }
        logFile.delete()
        scan(scanPaths)
        return n
    }

    private fun scan(paths: List<String>) {
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
    }
}
