package com.earlln.clear.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.earlln.clear.MainViewModel
import com.earlln.clear.data.OrganizeMode

@Composable
fun OrganizeScreen(vm: MainViewModel, onBack: () -> Unit) {
    val foldersState by vm.organizeFolders.collectAsState()
    val folders = foldersState.orEmpty()
    var chosenPath by rememberSaveable { mutableStateOf<String?>(null) }
    // 선택한 폴더가 목록에서 사라지면(분류 완료 등) 첫 폴더로
    val folderPath = chosenPath?.takeIf { p -> folders.any { it.path == p } } ?: folders.firstOrNull()?.path
    LaunchedEffect(Unit) { vm.loadOrganizeFolders() }
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
                "선택한 폴더 바로 아래에 있는 파일만 하위 폴더로 옮깁니다. 실행 후 '되돌리기'로 원래대로 돌릴 수 있습니다.",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            Text("① 정리할 폴더 (괄호 안은 정리할 파일 수)", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleSmall)
            if (foldersState != null && folders.isEmpty()) {
                Text(
                    "정리할 파일이 있는 폴더가 없습니다 ✨",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(folders, key = { it.path }) { f ->
                    FilterChip(
                        selected = folderPath == f.path,
                        onClick = { chosenPath = f.path; vm.clearPlan() },
                        label = { Text("${f.name} (${f.fileCount})") },
                    )
                }
            }
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
