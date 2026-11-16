package com.earlln.clear.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat

enum class ApkStatus(val label: String, val desc: String, val recommendDelete: Boolean) {
    SAME_INSTALLED("✅ 이미 설치됨", "같은 버전이 설치되어 있어 설치 파일이 더 필요 없습니다", true),
    OLDER_THAN_INSTALLED("⬇️ 예전 버전", "설치된 앱이 이 파일보다 최신입니다", true),
    CORRUPT("⚠️ 손상된 파일", "열 수 없는 파일입니다(다운로드 미완료 등)", true),
    NEWER_THAN_INSTALLED("⬆️ 업데이트 대기", "설치된 앱보다 최신 버전입니다", false),
    NOT_INSTALLED("❌ 설치 안 됨", "설치한 적이 없거나 이미 삭제한 앱입니다", false),
    BUNDLE("🧩 분할 APK 묶음", ".apks/.xapk 등은 내용을 분석할 수 없습니다", false),
}

data class ApkInfo(
    val file: FileItem,
    val status: ApkStatus,
    val appName: String? = null,
    val packageName: String? = null,
    val versionName: String? = null,
    val installedVersionName: String? = null,
    /** null: 사용 기록 권한 없음 또는 미설치, 0: 기록 없음 */
    val lastUsed: Long? = null,
)

object ApkAnalyzer {

    fun analyze(context: Context, files: List<FileItem>): List<ApkInfo> {
        val pm = context.packageManager
        val usage = if (hasUsageAccess(context)) lastUsedMap(context) else null
        return files.map { f ->
            if (f.extension != "apk") return@map ApkInfo(f, ApkStatus.BUNDLE)
            val archive = archiveInfo(pm, f.path) ?: return@map ApkInfo(f, ApkStatus.CORRUPT)
            val ai = archive.applicationInfo
            val name = ai?.let {
                it.sourceDir = f.path
                it.publicSourceDir = f.path
                runCatching { it.loadLabel(pm).toString() }.getOrNull()
            }
            val pkg = archive.packageName
            val installed = installedInfo(pm, pkg)
            val status = when {
                installed == null -> ApkStatus.NOT_INSTALLED
                else -> {
                    val a = PackageInfoCompat.getLongVersionCode(archive)
                    val i = PackageInfoCompat.getLongVersionCode(installed)
                    when {
                        a == i -> ApkStatus.SAME_INSTALLED
                        a < i -> ApkStatus.OLDER_THAN_INSTALLED
                        else -> ApkStatus.NEWER_THAN_INSTALLED
                    }
                }
            }
            ApkInfo(
                file = f,
                status = status,
                appName = name,
                packageName = pkg,
                versionName = archive.versionName,
                installedVersionName = installed?.versionName,
                lastUsed = if (installed != null && usage != null) usage[pkg] ?: 0L else null,
            )
        }.sortedWith(compareBy<ApkInfo>({ it.status.ordinal }, { -it.file.size }))
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, path: String): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getPackageArchiveInfo(path, 0)
        }
    } catch (e: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager, pkg: String): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getPackageInfo(pkg, 0)
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** 앱별 마지막 사용 시각 (안드로이드가 보관하는 기간 안에서) */
    private fun lastUsedMap(context: Context): Map<String, Long> = try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        usm.queryAndAggregateUsageStats(0, System.currentTimeMillis())
            .mapValues { it.value.lastTimeUsed }
            .filterValues { it > 0 }
    } catch (e: Exception) {
        emptyMap()
    }

    @Suppress("DEPRECATION")
    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
