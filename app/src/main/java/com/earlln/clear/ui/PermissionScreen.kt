package com.earlln.clear.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earlln.clear.Permissions

@Composable
fun PermissionScreen(onResult: () -> Unit) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onResult() }
    Scaffold { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("📂", fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text("파일 접근 권한이 필요합니다", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text(
                "휴대폰 안의 파일을 찾아 정리하려면 '모든 파일 접근' 권한이 필요합니다.\n" +
                    "파일 정보는 휴대폰 밖으로 전송되지 않습니다.",
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Permissions.openAllFilesSettings(ctx)
                } else {
                    launcher.launch(
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    )
                }
            }) { Text("권한 허용하기") }
        }
    }
}
