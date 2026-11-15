package com.earlln.clear.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 스캔된 파일 1개의 정보 */
data class FileItem(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val category: FileCategory get() = FileCategory.of(extension)
    val parent: String get() = path.substringBeforeLast('/')

    companion object {
        fun from(f: File) = FileItem(f.absolutePath, f.name, f.length(), f.lastModified())
    }
}

enum class FileCategory(val label: String, val emoji: String, private val exts: Set<String>) {
    IMAGE("사진", "🖼️", setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng", "raw")),
    VIDEO("동영상", "🎬", setOf("mp4", "mkv", "avi", "mov", "3gp", "webm", "wmv", "flv", "m4v", "ts")),
    AUDIO("음악", "🎵", setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr", "wma", "mid")),
    DOCUMENT("문서", "📄", setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "hwp", "hwpx", "csv",
        "rtf", "odt", "ods", "odp", "md", "epub",
    )),
    ARCHIVE("압축파일", "🗜️", setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "alz", "egg")),
    APK("설치파일", "📦", setOf("apk", "apks", "xapk", "apkm")),
    OTHER("기타", "📁", emptySet());

    companion object {
        fun of(ext: String): FileCategory = entries.firstOrNull { ext in it.exts } ?: OTHER
    }
}

data class ScanResult(
    val files: List<FileItem>,
    val dirs: List<String>,
    val scannedAt: Long = System.currentTimeMillis(),
) {
    fun without(paths: Set<String>) = copy(files = files.filterNot { it.path in paths })
}

data class DuplicateGroup(val hash: String, val files: List<FileItem>) {
    val size: Long get() = files.first().size
    val wasted: Long get() = size * (files.size - 1)
}

data class JunkResult(val emptyDirs: List<String>, val tempFiles: List<FileItem>)

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024; i++
    }
    return if (v >= 100) String.format(Locale.ROOT, "%.0f %s", v, units[i])
    else String.format(Locale.ROOT, "%.1f %s", v, units[i])
}

private val dateFmt = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)
fun formatDate(millis: Long): String = dateFmt.format(Date(millis))
