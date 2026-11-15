package com.earlln.clear.data

import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * 1단계: 크기가 같은 파일끼리 묶음
 * 2단계: 앞부분 64KB 해시로 후보 축소
 * 3단계: 전체 내용 해시(SHA-256)로 최종 확인
 */
object DuplicateFinder {
    private const val PARTIAL = 64 * 1024

    suspend fun find(files: List<FileItem>, onProgress: (Float) -> Unit): List<DuplicateGroup> {
        val bySize = files.filter { it.size > 0 }.groupBy { it.size }.values.filter { it.size > 1 }
        val total = bySize.sumOf { it.size }.coerceAtLeast(1)
        var done = 0
        val result = ArrayList<DuplicateGroup>()
        for (sameSize in bySize) {
            coroutineContext.ensureActive()
            val byPartial = sameSize.groupBy { hash(it.path, PARTIAL) }
                .filterKeys { it != null }.values.filter { it.size > 1 }
            for (cand in byPartial) {
                val needFull = cand.first().size > PARTIAL
                val byFull = if (needFull) {
                    cand.groupBy { coroutineContext.ensureActive(); hash(it.path, Long.MAX_VALUE) }
                } else {
                    cand.groupBy { hash(it.path, PARTIAL) }
                }
                byFull.forEach { (h, g) ->
                    if (h != null && g.size > 1) {
                        result.add(DuplicateGroup(h, g.sortedBy { it.lastModified }))
                    }
                }
            }
            done += sameSize.size
            onProgress(done.toFloat() / total)
        }
        return result.sortedByDescending { it.wasted }
    }

    private fun hash(path: String, limit: Number): String? = try {
        val md = MessageDigest.getInstance("SHA-256")
        val max = limit.toLong()
        FileInputStream(File(path)).use { input ->
            val buf = ByteArray(64 * 1024)
            var read = 0L
            while (read < max) {
                val toRead = minOf(buf.size.toLong(), max - read).toInt()
                val n = input.read(buf, 0, toRead)
                if (n <= 0) break
                md.update(buf, 0, n)
                read += n
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        null
    }
}
