package com.earlln.clear.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.earlln.clear.MainViewModel
import com.earlln.clear.data.SettingsStore
import com.earlln.clear.Task
import com.earlln.clear.dataOrNull
import com.earlln.clear.data.formatDate
import com.earlln.clear.data.formatSize

@Composable
fun DuplicatesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val state by vm.duplicates.collectAsState()
    val scan by vm.scan.collectAsState()
    val busy by vm.busy.collectAsState()
    LaunchedEffect(Unit) { if (vm.duplicates.value is Task.Idle) vm.findDuplicates() }

    val groups = state.dataOrNull().orEmpty()
    // 기본 선택: 각 그룹에서 원본으로 추정되는 첫 파일만 남기고 나머지 선택
    val defaultSelection = remember(groups) { groups.flatMap { g -> g.files.drop(1).map { it.path } }.toSet() }
    var selected by remember(groups) { mutableStateOf(defaultSelection) }
    var confirm by remember { mutableStateOf(false) }
    val selectedSize = groups.flatMap { it.files }.filter { it.path in selected }.sumOf { it.size }

    Scaffold(
        topBar = {
            BackTopBar("중복 파일", onBack) {
                if (groups.isNotEmpty()) {
                    val allSelected = selected.containsAll(defaultSelection)
                    TextButton(onClick = { selected = if (allSelected) emptySet() else defaultSelection }) {
                        Text(if (allSelected) "전체 해제" else "전체 선택")
                    }
                }
            }
        },
        bottomBar = {
            if (groups.isNotEmpty()) {
                ActionBar("${selected.size}개 선택 · ${formatSize(selectedSize)}", "휴지통으로", selected.isNotEmpty() && !busy) {
                    confirm = true
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when (val s = state) {
                is Task.Running -> LoadingView(s.text, s.progress)
                Task.Idle -> LoadingView((scan as? Task.Running)?.text ?: "준비 중…")
                is Task.Done -> if (groups.isEmpty()) EmptyView("중복 파일이 없습니다 🎉") else {
                    Text(
                        "중복 그룹 ${groups.size}개 · 확보 가능 ${formatSize(groups.sumOf { it.wasted })}\n" +
                            "원본으로 보이는 파일(카메라 폴더 > 이름에 (1)이 없는 파일 > 오래된 파일)을 남기고 나머지를 선택했습니다. " +
                            "각 그룹에서 최소 1개는 반드시 남습니다. 파일을 누르면 열어볼 수 있습니다.",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LazyColumn {
                        groups.forEachIndexed { idx, g ->
                            item(key = "h$idx") {
                                Text(
                                    "그룹 ${idx + 1} · ${g.files.size}개 × ${formatSize(g.size)}",
                                    Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            items(g.files, key = { "f$idx:" + it.path }) { f ->
                                val checked = f.path in selected
                                val othersAllChecked = g.files.all { it.path == f.path || it.path in selected }
                                FileRow(
                                    item = f,
                                    checked = checked,
                                    // 그룹 전체를 선택하는 것은 막는다
                                    enabled = checked || !othersAllChecked,
                                    subtitle = "${formatDate(f.lastModified)}" + if (f == g.files.first()) " · 남길 파일(원본 추정)" else "",
                                    onCheckedChange = { c -> selected = if (c) selected + f.path else selected - f.path },
                                )
                            }
                            item(key = "d$idx") { HorizontalDivider() }
                        }
                    }
                }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            "휴지통으로 이동",
            "${selected.size}개 파일(${formatSize(selectedSize)})을 휴지통으로 옮깁니다.\n${SettingsStore.current.trashDays}일 안에는 휴지통에서 복원할 수 있습니다.",
            "이동",
            onConfirm = { vm.moveToTrash(selected) },
            onDismiss = { confirm = false },
        )
    }
}
