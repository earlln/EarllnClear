package com.earlln.clear.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class KeepRule(val label: String, val desc: String) {
    SMART("스마트 (원본 추정)", "카메라 폴더 > 이름에 (1)이 없는 파일 > 오래된 파일 순으로 남깁니다"),
    OLDEST("가장 오래된 파일", "가장 오래된 파일을 남기고 나머지를 선택합니다"),
    NEWEST("가장 최신 파일", "가장 최근 파일을 남기고 나머지를 선택합니다"),
}

enum class DateSource(val label: String, val desc: String) {
    TAKEN_FIRST("촬영일 우선", "사진·동영상은 촬영일, 그 밖의 파일은 수정일로 나눕니다"),
    MODIFIED("파일 수정일", "모든 파일을 수정일로 나눕니다"),
}

enum class ThemeMode(val label: String) { SYSTEM("시스템 설정 따르기"), LIGHT("밝게"), DARK("어둡게") }

data class Settings(
    val trashDays: Int = 30,
    val keepRule: KeepRule = KeepRule.SMART,
    val dupMinSize: Long = 0,
    val dateSource: DateSource = DateSource.TAKEN_FIRST,
    val skipHidden: Boolean = false,
    val includeSubfolders: Boolean = true,
    val excludeCamera: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val largeDefault: Int = 1,
    val oldDefault: Int = 1,
) {
    companion object {
        val TRASH_DAYS = listOf(7, 30, 60, 90)
        val DUP_MIN_SIZES = listOf(0L to "모든 파일", 100L * 1024 to "100KB 이상", 1024L * 1024 to "1MB 이상", 10L * 1024 * 1024 to "10MB 이상")
        val LARGE_OPTIONS = listOf("50MB 이상" to 50L, "100MB 이상" to 100L, "500MB 이상" to 500L, "1GB 이상" to 1024L)
        val OLD_OPTIONS = listOf("6개월 이상" to 182L, "1년 이상" to 365L, "2년 이상" to 730L, "3년 이상" to 1095L)
    }
}

/** 앱 설정 저장소 (SharedPreferences) */
object SettingsStore {
    private lateinit var prefs: SharedPreferences
    private val _flow = MutableStateFlow(Settings())
    val flow: StateFlow<Settings> get() = _flow
    val current: Settings get() = _flow.value

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _flow.value = load()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, def: E): E =
        runCatching { enumValueOf<E>(name ?: "") }.getOrDefault(def)

    private fun load(): Settings {
        val d = Settings()
        return Settings(
            trashDays = prefs.getInt("trashDays", d.trashDays),
            keepRule = enumOr(prefs.getString("keepRule", null), d.keepRule),
            dupMinSize = prefs.getLong("dupMinSize", d.dupMinSize),
            dateSource = enumOr(prefs.getString("dateSource", null), d.dateSource),
            skipHidden = prefs.getBoolean("skipHidden", d.skipHidden),
            includeSubfolders = prefs.getBoolean("includeSubfolders", d.includeSubfolders),
            excludeCamera = prefs.getBoolean("excludeCamera", d.excludeCamera),
            theme = enumOr(prefs.getString("theme", null), d.theme),
            largeDefault = prefs.getInt("largeDefault", d.largeDefault),
            oldDefault = prefs.getInt("oldDefault", d.oldDefault),
        )
    }

    fun update(transform: (Settings) -> Settings) {
        val s = transform(_flow.value)
        prefs.edit()
            .putInt("trashDays", s.trashDays)
            .putString("keepRule", s.keepRule.name)
            .putLong("dupMinSize", s.dupMinSize)
            .putString("dateSource", s.dateSource.name)
            .putBoolean("skipHidden", s.skipHidden)
            .putBoolean("includeSubfolders", s.includeSubfolders)
            .putBoolean("excludeCamera", s.excludeCamera)
            .putString("theme", s.theme.name)
            .putInt("largeDefault", s.largeDefault)
            .putInt("oldDefault", s.oldDefault)
            .apply()
        _flow.value = s
    }

    fun reset() = update { Settings() }
}
