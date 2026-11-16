package com.earlln.clear.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.earlln.clear.data.FileItem
import com.earlln.clear.data.formatDate
import com.earlln.clear.data.formatSize
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        actions = { actions() },
    )
}

@Composable
fun FileRow(
    item: FileItem,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String = "${formatSize(item.size)} · ${formatDate(item.lastModified)}",
    showFolder: Boolean = true,
    enabled: Boolean = true,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { openFile(ctx, item.path) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(item.category.emoji, Modifier.padding(end = 8.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showFolder) {
                Text(
                    displayPath(item.parent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

fun displayPath(path: String): String =
    path.removePrefix(com.earlln.clear.data.StorageScanner.root.absolutePath).ifEmpty { "/" }

fun openFile(ctx: Context, path: String) {
    try {
        val f = File(path)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "이 파일을 열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, "파일을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

/** 하단 고정 실행 바 */
@Composable
fun ActionBar(text: String, buttonText: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onClick, enabled = enabled) { Text(buttonText) }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
fun LoadingView(text: String, progress: Float? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (progress == null) {
                CircularProgressIndicator()
            } else {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.width(220.dp))
                Spacer(Modifier.height(8.dp))
                Text("${(progress * 100).toInt()}%")
            }
            Spacer(Modifier.height(12.dp))
            Text(text)
        }
    }
}

@Composable
fun EmptyView(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
