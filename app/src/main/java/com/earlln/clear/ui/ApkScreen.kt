package com.earlln.clear.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.earlln.clear.MainViewModel
import com.earlln.clear.Task
import com.earlln.clear.data.ApkAnalyzer
import com.earlln.clear.data.ApkInfo
import com.earlln.clear.data.formatDate
import com.earlln.clear.data.formatSize
import com.earlln.clear.dataOrNull
import java.util.concurrent.TimeUnit

@Composable
fun ApkScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val state by vm.apks.collectAsState()
    val scan by vm.scan.collectAsState()
    val busy by vm.busy.collectAsState()
    var usageGranted by remember { mutableStateOf(ApkAnalyzer.hasUsageAccess(ctx)) }

    LaunchedEffect(Unit) { if (vm.apks.value is Task.Idle) vm.findApks() }
    // 설정에서 권한을 켜고 돌아오면 다시 분석
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = ApkAnalyzer.hasUsageAccess(ctx)
        if (now != usageGranted) {
            usageGranted = now
            vm.findApks()
        }
    }

    val list = state.dataOrNull().orEmpty()
    val defaultSelection = remember(list) { list.filter { it.status.recommendDelete }.map { it.file.path }.toSet() }
    var selected by remember(list) { mutableStateOf(defaultSelection) }
    var confirm by remember { mutableStateOf(false) }
    val selectedSize = list.filter { it.file.path in selected }.sumOf { it.file.size }

    Scaffold(
        topBar = {
            BackTopBar("APK 정리", onBack) {
                if (list.isNotEmpty()) {
                    val all = selected.size == list.size
                    TextButton(onClick = { selected = if (all) emptySet() else list.map { it.file.path }.toSet() }) {
                        Text(if (all) "전체 해제" else "전체 선택")
                    }
                }
            }
        },
        bottomBar = {
            if (list.isNotEmpty()) {
                ActionBar("${selected.size}개 선택 · ${formatSize(selectedSize)}", "휴지통으로", selected.isNotEmpty() && !busy) {
                    confirm = true
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when (val s = state) {
                is Task.Running -> LoadingView(s.text)
                Task.Idle -> LoadingView((scan as? Task.Running)?.text ?: "준비 중…")
                is Task.Done -> LazyColumn {
                    if (!usageGranted) {
                        item {
                            Card(
                                Modifier.fillMaxWidth().padding(16.dp),
                                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer),
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("앱 마지막 사용일 보기", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "'사용 정보 접근'을 허용하면 설치된 앱을 마지막으로 언제 사용했는지 함께 보여드립니다.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    TextButton(onClick = { ApkAnalyzer.openUsageAccessSettings(ctx) }) {
                                        Text("설정에서 EarllnClear 허용하기")
                                    }
                                }
                            }
                        }
                    }
                    if (list.isEmpty()) {
                        item { EmptyView("APK 파일이 없습니다") }
                    } else {
                        item {
                            Text(
                                "'삭제 권장' 항목(이미 설치됨·예전 버전·손상)을 미리 선택했습니다. " +
                                    "APK 파일을 지워도 설치된 앱은 그대로 남습니다.",
                                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        list.groupBy { it.status }.forEach { (status, items) ->
                            item(key = "h$status") {
                                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
                                    Text(
                                        "${status.label} ${items.size}개 · ${formatSize(items.sumOf { it.file.size })}" +
                                            if (status.recommendDelete) "  (삭제 권장)" else "",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        status.desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            items(items, key = { it.file.path }) { a ->
                                FileRow(
                                    item = a.file,
                                    title = a.appName ?: a.file.name,
                                    subtitle = apkSubtitle(a),
                                    checked = a.file.path in selected,
                                    openOnClick = false,
                                    onCheckedChange = { c ->
                                        selected = if (c) selected + a.file.path else selected - a.file.path
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            "휴지통으로 이동",
            "APK ${selected.size}개(${formatSize(selectedSize)})를 휴지통으로 옮깁니다.\n설치된 앱에는 영향이 없고, 30일 안에는 복원할 수 있습니다.",
            "이동",
            onConfirm = { vm.moveToTrash(selected) },
            onDismiss = { confirm = false },
        )
    }
}

private fun apkSubtitle(a: ApkInfo): String {
    val parts = mutableListOf<String>()
    if (a.appName != null) parts += a.file.name
    a.versionName?.let { v ->
        parts += if (a.installedVersionName != null && a.installedVersionName != v) {
            "파일 v$v / 설치됨 v${a.installedVersionName}"
        } else {
            "v$v"
        }
    }
    parts += formatSize(a.file.size)
    when (val used = a.lastUsed) {
        null -> {}
        0L -> parts += "사용 기록 없음"
        else -> {
            val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - used)
            parts += "마지막 사용 ${formatDate(used)}" + if (days >= 1) " (${days}일 전)" else " (오늘)"
        }
    }
    return parts.joinToString(" · ")
}
