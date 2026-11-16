package com.earlln.clear.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earlln.clear.MainViewModel
import com.earlln.clear.Task
import com.earlln.clear.data.formatSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, onOpen: (Screen) -> Unit) {
    val storage by vm.storage.collectAsState()
    val scan by vm.scan.collectAsState()
    val trashItems by vm.trashItems.collectAsState()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("EarllnClear 파일 정리") },
            actions = {
                IconButton(onClick = { vm.rescan() }, enabled = scan !is Task.Running) {
                    Icon(Icons.Filled.Refresh, contentDescription = "다시 스캔")
                }
            },
        )
    }) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("내장 저장공간", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        val ratio = if (storage.total > 0) storage.used.toFloat() / storage.total else 0f
                        LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("사용 ${formatSize(storage.used)} / 전체 ${formatSize(storage.total)} (여유 ${formatSize(storage.free)})")
                        Spacer(Modifier.height(4.dp))
                        val scanText = when (val s = scan) {
                            is Task.Running -> s.text
                            is Task.Done -> "파일 ${s.data.files.size}개 확인 완료"
                            Task.Idle -> "스캔 대기 중"
                        }
                        Text(scanText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                FeatureCard("🔁", "중복 파일", "내용이 똑같은 파일을 찾아 정리합니다") { onOpen(Screen.DUPLICATES) }
            }
            item {
                FeatureCard("🐘", "대용량 파일", "공간을 많이 차지하는 파일을 찾습니다") { onOpen(Screen.LARGE) }
            }
            item {
                FeatureCard("🕰️", "오래된 파일", "오랫동안 수정되지 않은 파일을 찾습니다") { onOpen(Screen.OLD) }
            }
            item {
                FeatureCard("🧹", "빈 폴더 · 임시파일", "빈 폴더와 임시/미완료 다운로드 파일을 청소합니다") { onOpen(Screen.JUNK) }
            }
            item {
                FeatureCard("📦", "APK 정리", "이미 설치했거나 예전 버전인 설치 파일을 찾습니다") { onOpen(Screen.APK) }
            }
            item {
                FeatureCard("🗂️", "자동 분류", "폴더 속 파일을 종류·날짜별 폴더로 정리합니다") { onOpen(Screen.ORGANIZE) }
            }
            item {
                val summary = if (trashItems.isEmpty()) "비어 있음"
                else "${trashItems.size}개 · ${formatSize(trashItems.sumOf { it.size })} (30일 후 자동 삭제)"
                FeatureCard("🗑️", "휴지통", summary) { onOpen(Screen.TRASH) }
            }
        }
    }
}

@Composable
private fun FeatureCard(emoji: String, title: String, desc: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 28.sp, modifier = Modifier.padding(end = 16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
