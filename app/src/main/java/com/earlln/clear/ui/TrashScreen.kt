package com.earlln.clear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.earlln.clear.MainViewModel
import com.earlln.clear.data.FileCategory
import com.earlln.clear.data.formatDate
import com.earlln.clear.data.formatSize

@Composable
fun TrashScreen(vm: MainViewModel, onBack: () -> Unit) {
    val items by vm.trashItems.collectAsState()
    val busy by vm.busy.collectAsState()
    var selected by remember(items) { mutableStateOf(emptySet<String>()) }
    var confirmDelete by remember { mutableStateOf<Set<String>?>(null) }
    val selSize = items.filter { it.id in selected }.sumOf { it.size }

    Scaffold(
        topBar = {
            BackTopBar("휴지통", onBack) {
                if (items.isNotEmpty()) {
                    TextButton(onClick = { confirmDelete = items.map { it.id }.toSet() }, enabled = !busy) {
                        Text("비우기")
                    }
                }
            }
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${selected.size}개 · ${formatSize(selSize)}", Modifier.weight(1f))
                        OutlinedButton(onClick = { confirmDelete = selected }, enabled = !busy) { Text("영구 삭제") }
                        Button(onClick = { vm.restore(selected) }, enabled = !busy) { Text("복원") }
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (items.isEmpty()) {
                EmptyView("휴지통이 비어 있습니다")
            } else {
                Text(
                    "${items.size}개 · ${formatSize(items.sumOf { it.size })}\n휴지통의 파일은 30일이 지나면 자동으로 영구 삭제됩니다.",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                LazyColumn {
                    items(items, key = { it.id }) { e ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(e.id in selected, { c -> selected = if (c) selected + e.id else selected - e.id })
                            Text(
                                FileCategory.of(e.name.substringAfterLast('.', "").lowercase()).emoji,
                                Modifier.padding(end = 8.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${formatSize(e.size)} · ${formatDate(e.deletedAt)} 삭제 · ${e.daysLeft}일 후 영구 삭제",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    displayPath(e.originalPath.substringBeforeLast('/')),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    confirmDelete?.let { ids ->
        ConfirmDialog(
            "영구 삭제",
            "${ids.size}개 파일을 영구 삭제합니다.\n⚠️ 이 작업은 되돌릴 수 없습니다.",
            "영구 삭제",
            onConfirm = { vm.deleteForever(ids) },
            onDismiss = { confirmDelete = null },
        )
    }
}
