package com.earlln.clear.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.earlln.clear.MainViewModel
import com.earlln.clear.Task
import com.earlln.clear.dataOrNull
import com.earlln.clear.data.JunkResult
import com.earlln.clear.data.formatSize

@Composable
fun JunkScreen(vm: MainViewModel, onBack: () -> Unit) {
    val state by vm.junk.collectAsState()
    val scan by vm.scan.collectAsState()
    val busy by vm.busy.collectAsState()
    LaunchedEffect(Unit) { if (vm.junk.value is Task.Idle) vm.findJunk() }

    val data = state.dataOrNull() ?: JunkResult(emptyList(), emptyList())
    var selDirs by remember(data) { mutableStateOf(data.emptyDirs.toSet()) }
    var selFiles by remember(data) { mutableStateOf(data.tempFiles.map { it.path }.toSet()) }
    var confirm by remember { mutableStateOf(false) }
    val fileSize = data.tempFiles.filter { it.path in selFiles }.sumOf { it.size }
    val total = selDirs.size + selFiles.size

    Scaffold(
        topBar = { BackTopBar("빈 폴더 · 임시파일", onBack) },
        bottomBar = {
            if (state is Task.Done && (data.emptyDirs.isNotEmpty() || data.tempFiles.isNotEmpty())) {
                ActionBar("폴더 ${selDirs.size}개 · 파일 ${selFiles.size}개 (${formatSize(fileSize)})", "청소하기", total > 0 && !busy) {
                    confirm = true
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when (val s = state) {
                is Task.Running -> LoadingView(s.text)
                Task.Idle -> LoadingView((scan as? Task.Running)?.text ?: "준비 중…")
                is Task.Done -> if (data.emptyDirs.isEmpty() && data.tempFiles.isEmpty()) EmptyView("청소할 항목이 없습니다 ✨") else {
                    LazyColumn {
                        item {
                            SectionHeader("📂 빈 폴더 ${data.emptyDirs.size}개", "내용이 없는 폴더는 바로 삭제됩니다")
                        }
                        items(data.emptyDirs, key = { "d$it" }) { d ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(d in selDirs, { c -> selDirs = if (c) selDirs + d else selDirs - d })
                                Text(displayPath(d), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        item {
                            SectionHeader(
                                "🧾 임시파일 ${data.tempFiles.size}개",
                                ".tmp · 미완료 다운로드 · 썸네일 캐시 등 (휴지통으로 이동)",
                            )
                        }
                        items(data.tempFiles, key = { "f" + it.path }) { f ->
                            FileRow(
                                item = f,
                                checked = f.path in selFiles,
                                onCheckedChange = { c -> selFiles = if (c) selFiles + f.path else selFiles - f.path },
                            )
                        }
                    }
                }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            "청소하기",
            "빈 폴더 ${selDirs.size}개를 삭제하고, 임시파일 ${selFiles.size}개(${formatSize(fileSize)})를 휴지통으로 옮깁니다.",
            "청소",
            onConfirm = { vm.cleanJunk(selDirs.toList(), selFiles) },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun SectionHeader(title: String, desc: String) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
