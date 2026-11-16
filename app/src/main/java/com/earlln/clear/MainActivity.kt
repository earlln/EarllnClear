package com.earlln.clear

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.earlln.clear.ui.ApkScreen
import com.earlln.clear.ui.ClearTheme
import com.earlln.clear.ui.DuplicatesScreen
import com.earlln.clear.ui.HomeScreen
import com.earlln.clear.ui.JunkScreen
import com.earlln.clear.ui.LargeOldScreen
import com.earlln.clear.ui.OrganizeScreen
import com.earlln.clear.ui.PermissionScreen
import com.earlln.clear.ui.Screen
import com.earlln.clear.ui.SettingsScreen
import com.earlln.clear.data.SettingsStore
import com.earlln.clear.ui.TrashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by SettingsStore.flow.collectAsState()
            ClearTheme(settings.theme) { AppRoot() }
        }
    }
}

@Composable
private fun AppRoot() {
    val ctx = LocalContext.current
    val vm: MainViewModel = viewModel()
    var granted by remember { mutableStateOf(Permissions.hasAllFilesAccess(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = Permissions.hasAllFilesAccess(ctx) }

    if (!granted) {
        PermissionScreen(onResult = { granted = Permissions.hasAllFilesAccess(ctx) })
        return
    }

    LaunchedEffect(Unit) { vm.onReady() }
    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
    }

    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val back = { screen = Screen.HOME }
    BackHandler(enabled = screen != Screen.HOME) { back() }

    when (screen) {
        Screen.HOME -> HomeScreen(vm) { screen = it }
        Screen.DUPLICATES -> DuplicatesScreen(vm, back)
        Screen.LARGE -> LargeOldScreen(vm, large = true, onBack = back)
        Screen.OLD -> LargeOldScreen(vm, large = false, onBack = back)
        Screen.JUNK -> JunkScreen(vm, back)
        Screen.ORGANIZE -> OrganizeScreen(vm, back)
        Screen.APK -> ApkScreen(vm, back)
        Screen.TRASH -> TrashScreen(vm, back)
        Screen.SETTINGS -> SettingsScreen(vm, back)
    }
}
