package com.earlln.clear.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.earlln.clear.Permissions
import com.earlln.clear.data.SettingsStore
import com.earlln.clear.data.TrashManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** 하루 한 번 휴지통에서 보관 기간(설정)이 지난 파일을 영구 삭제 */
class TrashCleanupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (Permissions.hasAllFilesAccess(applicationContext)) {
            SettingsStore.init(applicationContext)
            TrashManager(applicationContext).purgeExpired()
        }
        Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<TrashCleanupWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("trash-cleanup", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
