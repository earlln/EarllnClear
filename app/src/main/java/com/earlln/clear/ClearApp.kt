package com.earlln.clear

import android.app.Application
import com.earlln.clear.data.SettingsStore
import com.earlln.clear.worker.TrashCleanupWorker

class ClearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        TrashCleanupWorker.schedule(this)
    }
}
