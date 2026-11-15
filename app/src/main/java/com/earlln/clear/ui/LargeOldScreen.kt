package com.earlln.clear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.earlln.clear.MainViewModel
import com.earlln.clear.Task
import com.earlln.clear.dataOrNull
import com.earlln.clear.data.FileItem
import com.earlln.clear.data.formatSize
import java.util.concurrent.TimeUnit

private const val MB = 1024L * 1024

/** large = true: 대용량 파일, false: 오래된 파일 */
@Composable
fun LargeOldScreen(vm: MainViewModel, large: Boolean, onBack: () -> Unit) {
    val scan by vm.scan.collectAsState()
    val busy by vm.busy.collectAsState()

    val options: List<Pair<String, Long>> = if (large) {
        listOf("50MB 이상" to 50 * MB, "100MB 이상" to 100 * MB, "500MB 이상" to 500 * MB, "1GB 이상" to 1024 * MB)
    } else {
        listOf("6개월 이상" to 182L, "1년 이상" to 365L, "2년 이상" to 730L, "3년 이상" to 1095L)
    }
    var optIdx by rememberSaveable { mutableIntStateOf(1) }
    val threshold = options[optIdx].second

    val files: List<FileItem> = remember(scan, optIdx) {
        val all = scan.dataOrNull()?.files.orEmpty()
        if (large) {
            all.filter { it.size >= threshold }.sortedByDescending { it.size }
        } else {
            val limit = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(threshold)
            all.filter { it.lastModified in 1 until limit }.sortedBy { it.lastModified }
        }
    }
    var selected by remember(files) { mutableStateOf(emptySet<String>()) }
    var confirm by remember { mutableStateOf(false) }
    val selectedSize = files.filter { it.path in selected }.sumOf { it.size }

    Scaffold(
        topBar = {
            BackTopBar(if (large) "대용량 파일" else "오래된 파일", onBack) {
                if (files.isNotEmpty()) {
                    TextButton(onClick = {
                        selected = if (selected.size == files.size) emptySet() else files.map { it.path }.toSet()
                    }) { Text(if (selected.size == files.size) "선택 해제" else "전체 선택") }
                }
            }
        },
        bottomBar = {
            if (files.isNotEmpty()) {
                ActionBar("${selected.size}개 선택 · ${formatSize(selectedSize)}", "휴지통으로", selected.isNotEmpty() && !busy) {
                    confirm = true
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(options.size) { i ->
                    FilterChip(selected = optIdx == i, onClick = { optIdx = i }, label = { Text(options[i].first) })
                }
            }
            when (val s = scan) {
                is Task.Running -> LoadingView(s.text)
                Task.Idle -> LoadingView("준비 중…")
                is Task.Done -> if (files.isEmpty()) EmptyView("조건에 맞는 파일이 없습니다") else {
                    Text(
                        "${files.size}개 · 합계 ${formatSize(files.sumOf { it.size })}  (파일을 누르면 열어볼 수 있습니다)",
                        Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LazyColumn {
                        items(files, key = { it.path }) { f ->
                            FileRow(
                                item = f,
                                checked = f.path in selected,
                                onCheckedChange = { c -> selected = if (c) selected + f.path else selected - f.path },
                            )
                        }
                    }
                }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            "휴지통으로 이동",
            "${selected.size}개 파일(${formatSize(selectedSize)})을 휴지통으로 옮깁니다.\n30일 안에는 휴지통에서 복원할 수 있습니다.",
            "이동",
            onConfirm = { vm.moveToTrash(selected) },
            onDismiss = { confirm = false },
        )
    }
}
