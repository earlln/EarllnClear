package com.earlln.clear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.earlln.clear.data.ApkAnalyzer
import com.earlln.clear.data.ApkInfo
import com.earlln.clear.data.DuplicateFinder
import com.earlln.clear.data.FileCategory
import com.earlln.clear.data.DuplicateGroup
import com.earlln.clear.data.JunkFinder
import com.earlln.clear.data.JunkResult
import com.earlln.clear.data.MovePlan
import com.earlln.clear.data.OrganizeMode
import com.earlln.clear.data.Organizer
import com.earlln.clear.data.ScanResult
import com.earlln.clear.data.Settings
import com.earlln.clear.data.SettingsStore
import com.earlln.clear.data.StorageScanner
import com.earlln.clear.data.TrashEntry
import com.earlln.clear.data.TrashManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Task<out T> {
    data object Idle : Task<Nothing>
    data class Running(val progress: Float? = null, val text: String = "") : Task<Nothing>
    data class Done<T>(val data: T) : Task<T>
}

fun <T> Task<T>.dataOrNull(): T? = (this as? Task.Done<T>)?.data

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val trash = TrashManager(app)
    private val organizer = Organizer(app)
    private val scanMutex = Mutex()

    val scan = MutableStateFlow<Task<ScanResult>>(Task.Idle)
    val duplicates = MutableStateFlow<Task<List<DuplicateGroup>>>(Task.Idle)
    val junk = MutableStateFlow<Task<JunkResult>>(Task.Idle)
    val apks = MutableStateFlow<Task<List<ApkInfo>>>(Task.Idle)
    val trashItems = MutableStateFlow<List<TrashEntry>>(emptyList())
    val storage = MutableStateFlow(StorageScanner.StorageInfo(0, 0))
    val organizePlan = MutableStateFlow<List<MovePlan>?>(null)
    val organizeFolders = MutableStateFlow<List<StorageScanner.FolderCount>?>(null)
    val canUndoOrganize = MutableStateFlow(false)
    val busy = MutableStateFlow(false)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private var dupJob: Job? = null

    /** 권한이 확인된 뒤 호출 */
    fun onReady() {
        refreshStorage()
        viewModelScope.launch(Dispatchers.IO) {
            val purged = trash.purgeExpired()
            if (purged > 0) _messages.tryEmit("보관 기간이 지난 휴지통 파일 ${purged}개를 삭제했습니다.")
            trashItems.value = trash.list()
            canUndoOrganize.value = organizer.hasUndo()
        }
        if (scan.value is Task.Idle) rescan()
    }

    private fun refreshStorage() {
        viewModelScope.launch(Dispatchers.IO) { storage.value = StorageScanner.storageInfo() }
    }

    fun rescan() {
        duplicates.value = Task.Idle
        junk.value = Task.Idle
        apks.value = Task.Idle
        viewModelScope.launch { ensureScan(force = true) }
    }

    private suspend fun ensureScan(force: Boolean = false): ScanResult = scanMutex.withLock {
        val cur = scan.value
        if (!force && cur is Task.Done) return cur.data
        scan.value = Task.Running(text = "파일 확인 중…")
        val r = withContext(Dispatchers.IO) {
            StorageScanner.scan(SettingsStore.current.skipHidden) { n -> scan.value = Task.Running(text = "파일 ${n}개 확인") }
        }
        scan.value = Task.Done(r)
        r
    }

    fun findDuplicates() {
        dupJob?.cancel()
        dupJob = viewModelScope.launch {
            val files = ensureScan().files
            duplicates.value = Task.Running(0f, "같은 파일 비교 중…")
            val groups = withContext(Dispatchers.IO) {
                val st = SettingsStore.current
                DuplicateFinder.find(files, st.keepRule, st.dupMinSize) { p -> duplicates.value = Task.Running(p, "같은 파일 비교 중…") }
            }
            duplicates.value = Task.Done(groups)
        }
    }

    fun findJunk() {
        viewModelScope.launch {
            val s = ensureScan()
            junk.value = Task.Running(text = "정리할 항목 찾는 중…")
            junk.value = Task.Done(withContext(Dispatchers.IO) { JunkFinder.find(s) })
        }
    }

    fun findApks() {
        viewModelScope.launch {
            val s = ensureScan()
            apks.value = Task.Running(text = "설치 파일 분석 중…")
            val list = withContext(Dispatchers.IO) {
                ApkAnalyzer.analyze(getApplication<Application>(), s.files.filter { it.category == FileCategory.APK })
            }
            apks.value = Task.Done(list)
        }
    }

    /** 공통: 선택 파일을 휴지통으로 이동하고 모든 결과 목록에서 제거 */
    fun moveToTrash(paths: Set<String>) = runBusy {
        val moved = trash.moveToTrash(paths)
        removeFromResults(moved)
        trashItems.value = trash.list()
        val failed = paths.size - moved.size
        _messages.tryEmit(
            "${moved.size}개를 휴지통으로 옮겼습니다." + if (failed > 0) " (${failed}개 실패)" else ""
        )
    }

    fun cleanJunk(emptyDirs: List<String>, tempFiles: Set<String>) = runBusy {
        val dirCount = JunkFinder.deleteEmptyDirs(emptyDirs)
        val moved = trash.moveToTrash(tempFiles)
        removeFromResults(moved)
        trashItems.value = trash.list()
        junk.value = Task.Done(JunkFinder.find(scan.value.dataOrNull() ?: ensureScan()))
        _messages.tryEmit("빈 폴더 ${dirCount}개 삭제, 임시파일 ${moved.size}개를 휴지통으로 옮겼습니다.")
    }

    private fun removeFromResults(paths: Set<String>) {
        if (paths.isEmpty()) return
        scan.value.dataOrNull()?.let { scan.value = Task.Done(it.without(paths)) }
        duplicates.value.dataOrNull()?.let { d ->
            duplicates.value = Task.Done(
                d.map { g -> g.copy(files = g.files.filterNot { it.path in paths }) }.filter { it.files.size > 1 }
            )
        }
        junk.value.dataOrNull()?.let { j ->
            junk.value = Task.Done(j.copy(tempFiles = j.tempFiles.filterNot { it.path in paths }))
        }
        apks.value.dataOrNull()?.let { a ->
            apks.value = Task.Done(a.filterNot { it.file.path in paths })
        }
        refreshStorage()
    }

    // ---- 휴지통 ----
    fun restore(ids: Set<String>) = runBusy {
        val n = trash.restore(ids)
        trashItems.value = trash.list()
        scan.value = Task.Idle // 경로가 바뀌었으므로 다음에 다시 스캔
        duplicates.value = Task.Idle
        junk.value = Task.Idle
        apks.value = Task.Idle
        _messages.tryEmit("${n}개를 원래 위치로 복원했습니다.")
    }

    fun deleteForever(ids: Set<String>) = runBusy {
        val n = trash.deleteForever(ids)
        trashItems.value = trash.list()
        refreshStorage()
        _messages.tryEmit("${n}개를 영구 삭제했습니다.")
    }

    // ---- 자동 분류 ----
    fun loadOrganizeFolders() {
        viewModelScope.launch {
            val s = ensureScan()
            val st = SettingsStore.current
            organizeFolders.value = withContext(Dispatchers.Default) {
                StorageScanner.organizableFolders(s, st.includeSubfolders, st.excludeCamera)
            }
        }
    }

    fun planOrganize(dir: File, mode: OrganizeMode) = runBusy {
        organizePlan.value = organizer.plan(dir, mode, SettingsStore.current.dateSource)
    }

    fun clearPlan() {
        organizePlan.value = null
    }

    fun executeOrganize() = runBusy {
        val plans = organizePlan.value ?: return@runBusy
        val n = organizer.execute(plans)
        organizePlan.value = null
        canUndoOrganize.value = organizer.hasUndo()
        scan.value = Task.Idle
        duplicates.value = Task.Idle
        junk.value = Task.Idle
        apks.value = Task.Idle
        organizeFolders.value = null
        loadOrganizeFolders()
        _messages.tryEmit("${n}개 파일을 분류했습니다.")
    }

    fun undoOrganize() = runBusy {
        val n = organizer.undoLast()
        canUndoOrganize.value = organizer.hasUndo()
        scan.value = Task.Idle
        duplicates.value = Task.Idle
        junk.value = Task.Idle
        apks.value = Task.Idle
        organizeFolders.value = null
        loadOrganizeFolders()
        _messages.tryEmit("${n}개 파일을 원래 위치로 되돌렸습니다.")
    }

    // ---- 설정 ----
    /** 설정 변경 후, 영향을 받는 결과만 다시 계산하도록 초기화 */
    fun updateSettings(transform: (Settings) -> Settings) {
        val old = SettingsStore.current
        SettingsStore.update(transform)
        applySettingsChange(old, SettingsStore.current)
    }

    fun resetSettings() {
        val old = SettingsStore.current
        SettingsStore.reset()
        applySettingsChange(old, SettingsStore.current)
    }

    private fun applySettingsChange(old: Settings, new: Settings) {
        if (old.skipHidden != new.skipHidden) {
            scan.value = Task.Idle
            duplicates.value = Task.Idle
            junk.value = Task.Idle
            apks.value = Task.Idle
            organizeFolders.value = null
        }
        if (old.keepRule != new.keepRule || old.dupMinSize != new.dupMinSize) {
            dupJob?.cancel()
            duplicates.value = Task.Idle
        }
        if (old.includeSubfolders != new.includeSubfolders || old.excludeCamera != new.excludeCamera) {
            organizeFolders.value = null
            organizePlan.value = null
        }
        if (old.dateSource != new.dateSource) organizePlan.value = null
        if (old.trashDays != new.trashDays) {
            viewModelScope.launch(Dispatchers.IO) { trashItems.value = trash.list() }
        }
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (e: Exception) {
                _messages.tryEmit("오류가 발생했습니다: ${e.message}")
            } finally {
                busy.value = false
            }
        }
    }

}
