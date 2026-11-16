package com.earlln.clear.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.earlln.clear.MainViewModel
import com.earlln.clear.data.DateSource
import com.earlln.clear.data.KeepRule
import com.earlln.clear.data.Settings
import com.earlln.clear.data.SettingsStore
import com.earlln.clear.data.ThemeMode

/** 선택형 설정 항목 정의 */
private class Choice(
    val title: String,
    val options: List<String>,
    val selected: Int,
    val note: String? = null,
    val onSelect: (Int) -> Unit,
)

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s by SettingsStore.flow.collectAsState()
    var choice by remember { mutableStateOf<Choice?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    val version = remember {
        runCatching {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            "${info.versionName} (빌드 ${PackageInfoCompat.getLongVersionCode(info)})"
        }.getOrDefault("알 수 없음")
    }

    Scaffold(topBar = { BackTopBar("설정", onBack) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {

            Section("🗂️ 자동 분류")
            SwitchItem(
                "하위 폴더도 정리 대상에 포함",
                "끄면 Download처럼 최상위 폴더만 목록에 나옵니다",
                s.includeSubfolders,
            ) { v -> vm.updateSettings { it.copy(includeSubfolders = v) } }
            SwitchItem(
                "카메라 폴더(DCIM) 제외",
                "카메라·스크린샷 폴더의 사진을 옮기면 갤러리·클라우드 백업이 헷갈릴 수 있어 기본으로 제외합니다",
                s.excludeCamera,
            ) { v -> vm.updateSettings { it.copy(excludeCamera = v) } }
            ChoiceItem("날짜별 분류 기준", s.dateSource.label, s.dateSource.desc) {
                choice = Choice("날짜별 분류 기준", DateSource.entries.map { it.label }, s.dateSource.ordinal) { i ->
                    vm.updateSettings { it.copy(dateSource = DateSource.entries[i]) }
                }
            }

            Section("🔁 중복 파일")
            ChoiceItem("남길 파일 기준", s.keepRule.label, s.keepRule.desc) {
                choice = Choice("남길 파일 기준", KeepRule.entries.map { it.label }, s.keepRule.ordinal) { i ->
                    vm.updateSettings { it.copy(keepRule = KeepRule.entries[i]) }
                }
            }
            val minIdx = Settings.DUP_MIN_SIZES.indexOfFirst { it.first == s.dupMinSize }.coerceAtLeast(0)
            ChoiceItem("비교할 파일 크기", Settings.DUP_MIN_SIZES[minIdx].second, "작은 파일을 건너뛰면 검사가 빨라집니다") {
                choice = Choice("비교할 파일 크기", Settings.DUP_MIN_SIZES.map { it.second }, minIdx) { i ->
                    vm.updateSettings { it.copy(dupMinSize = Settings.DUP_MIN_SIZES[i].first) }
                }
            }

            Section("🐘 대용량 · 오래된 파일")
            ChoiceItem("대용량 파일 기본 기준", Settings.LARGE_OPTIONS[s.largeDefault].first, "화면을 열 때 처음 선택되는 기준입니다") {
                choice = Choice("대용량 파일 기본 기준", Settings.LARGE_OPTIONS.map { it.first }, s.largeDefault) { i ->
                    vm.updateSettings { it.copy(largeDefault = i) }
                }
            }
            ChoiceItem("오래된 파일 기본 기준", Settings.OLD_OPTIONS[s.oldDefault].first, "화면을 열 때 처음 선택되는 기준입니다") {
                choice = Choice("오래된 파일 기본 기준", Settings.OLD_OPTIONS.map { it.first }, s.oldDefault) { i ->
                    vm.updateSettings { it.copy(oldDefault = i) }
                }
            }

            Section("🗑️ 휴지통")
            ChoiceItem("보관 기간", "${s.trashDays}일", "기간이 지난 파일은 자동으로 영구 삭제됩니다") {
                choice = Choice(
                    "휴지통 보관 기간",
                    Settings.TRASH_DAYS.map { "${it}일" },
                    Settings.TRASH_DAYS.indexOf(s.trashDays).coerceAtLeast(0),
                    note = "⚠️ 기간을 줄이면, 이미 그 기간이 지난 파일은 다음 정리 때(앱 실행 시 또는 하루 1회) 영구 삭제됩니다.",
                ) { i -> vm.updateSettings { it.copy(trashDays = Settings.TRASH_DAYS[i]) } }
            }

            Section("🔍 검사")
            SwitchItem(
                "숨김 폴더 검사 제외",
                "이름이 '.'으로 시작하는 폴더를 검사하지 않습니다. 검사가 빨라지지만, 썸네일 캐시(.thumbnails) 같은 임시파일은 찾지 못합니다",
                s.skipHidden,
            ) { v -> vm.updateSettings { it.copy(skipHidden = v) } }

            Section("🎨 화면")
            ChoiceItem("테마", s.theme.label, null) {
                choice = Choice("테마", ThemeMode.entries.map { it.label }, s.theme.ordinal) { i ->
                    vm.updateSettings { it.copy(theme = ThemeMode.entries[i]) }
                }
            }

            Section("ℹ️ 앱 정보")
            InfoItem("앱 버전", version)
            InfoItem("패키지", ctx.packageName)
            OutlinedButton(
                onClick = { confirmReset = true },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text("설정 초기화") }
        }
    }

    choice?.let { c ->
        AlertDialog(
            onDismissRequest = { choice = null },
            title = { Text(c.title) },
            text = {
                Column {
                    c.options.forEachIndexed { i, label ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { c.onSelect(i); choice = null }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = i == c.selected, onClick = null)
                            Text(label, Modifier.padding(start = 12.dp))
                        }
                    }
                    c.note?.let {
                        Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choice = null }) { Text("닫기") } },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            "설정 초기화",
            "모든 설정을 처음 값으로 되돌립니다.\n(휴지통 보관 기간도 30일로 돌아갑니다)",
            "초기화",
            onConfirm = { vm.resetSettings() },
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(
        title,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ChoiceItem(title: String, value: String, desc: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        desc?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchItem(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun InfoItem(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
