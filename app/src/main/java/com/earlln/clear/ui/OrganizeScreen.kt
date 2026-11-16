package com.earlln.clear.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.earlln.clear.MainViewModel
import com.earlln.clear.data.OrganizeMode
import com.earlln.clear.data.SettingsStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeScreen(vm: MainViewModel, onBack: () -> Unit) {
    val foldersState by vm.organizeFolders.collectAsState()
    val folders = foldersState.orEmpty()
    var chosenPath by rememberSaveable { mutableStateOf<String?>(null) }
    // 선택한 폴더가 목록에서 사라지면(분류 완료 등) 첫 폴더로
    val folderPath = chosenPath?.takeIf { p -> folders.any { it.path == p } } ?: folders.firstOrNull()?.path
    LaunchedEffect(foldersState == null) { if (foldersState == null) vm.loadOrganizeFolders() }
    var showPicker by remember { mutableStateOf(false) }
    val settings by SettingsStore.flow.collectAsState()
    val settingsHint = buildString {
        append(if (settings.includeSubfolders) "하위 폴더 포함" else "최상위 폴더만")
        if (settings.excludeCamera) append(" · 카메라(DCIM) 제외")
        append(" · 날짜 기준: ${settings.dateSource.label}  (설정에서 변경)")
    }
    var mode by rememberSaveable { mutableStateOf(OrganizeMode.BY_TYPE) }
    val plan by vm.organizePlan.collectAsState()
    val busy by vm.busy.collectAsState()
    val canUndo by vm.canUndoOrganize.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { vm.clearPlan() } }

    Scaffold(
        topBar = { BackTopBar("자동 분류", onBack) },
        bottomBar = {
            val p = plan
            if (!p.isNullOrEmpty()) {
                ActionBar("${p.size}개 파일 이동 예정", "분류 실행", !busy) { confirm = true }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(
                "선택한 폴더 바로 아래에 있는 파일을 종류·날짜별 하위 폴더로 옮깁니다. 실행 후 '되돌리기'로 원래대로 돌릴 수 있습니다.",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            Text("① 정리할 폴더", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleSmall)
            when {
                foldersState == null -> Text(
                    "폴더 확인 중…",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                folders.isEmpty() -> Text(
                    "정리할 파일이 있는 폴더가 없습니다 ✨",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    val cur = folders.firstOrNull { it.path == folderPath }
                    OutlinedCard(
                        onClick = { showPicker = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("📁 ${cur?.relative ?: "선택"}", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "정리할 파일 ${cur?.fileCount ?: 0}개 · 눌러서 폴더 바꾸기 (전체 ${folders.size}개)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("▾", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
            Text(
                settingsHint,
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text("② 분류 방식", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleSmall)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(OrganizeMode.entries) { m ->
                    FilterChip(selected = mode == m, onClick = { mode = m; vm.clearPlan() }, label = { Text(m.label) })
                }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Button(
                    onClick = { folderPath?.let { vm.planOrganize(java.io.File(it), mode) } },
                    enabled = folderPath != null && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("③ 미리보기") }
                if (canUndo) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = { confirmUndo = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("↩ 마지막 분류 되돌리기")
                    }
                }
            }
            val p = plan
            when {
                p == null -> {}
                p.isEmpty() -> EmptyView("이 폴더에는 분류할 파일이 없습니다")
                else -> {
                    val byDest = p.groupBy { it.destFolder }
                    LazyColumn(contentPadding = PaddingValues(16.dp)) {
                        byDest.forEach { (dest, moves) ->
                            item(key = "h$dest") {
                                Text(
                                    "📁 ${displayPath(dest)}  (${moves.size}개)",
                                    Modifier.padding(top = 12.dp, bottom = 4.dp),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            items(moves.take(30), key = { "m" + it.from }) { m ->
                                Text("· ${m.name}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            if (moves.size > 30) {
                                item(key = "more$dest") {
                                    Text("… 외 ${moves.size - 30}개", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            "자동 분류 실행",
            "${plan?.size ?: 0}개 파일을 하위 폴더로 옮깁니다.\n문제가 있으면 '마지막 분류 되돌리기'로 복구할 수 있습니다.",
            "실행",
            onConfirm = { vm.executeOrganize() },
            onDismiss = { confirm = false },
        )
    }
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("정리할 폴더 선택") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    folders.forEach { f ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    chosenPath = f.path
                                    vm.clearPlan()
                                    showPicker = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = f.path == folderPath, onClick = null)
                            Text(f.relative, Modifier.weight(1f).padding(start = 8.dp))
                            Text("${f.fileCount}개", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("닫기") } },
        )
    }
    if (confirmUndo) {
        ConfirmDialog(
            "되돌리기",
            "마지막 자동 분류로 옮긴 파일들을 원래 위치로 되돌립니다.",
            "되돌리기",
            onConfirm = { vm.undoOrganize() },
            onDismiss = { confirmUndo = false },
        )
    }
}
