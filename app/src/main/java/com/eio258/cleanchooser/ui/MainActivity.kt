package com.eio258.cleanchooser.ui

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.data.readBackupText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ListCleanerTheme(darkTheme = androidx.compose.foundation.isSystemInDarkTheme()) {
                ListCleanerScreen()
            }
        }
    }

    @Composable
    private fun ListCleanerScreen(vm: MainViewModel = viewModel()) {
        val state by vm.state.collectAsStateWithLifecycle()
        var restartPromptDismissed by rememberSaveable { mutableStateOf(false) }
        val collectingDiagnostics by vm.collectingDiagnostics.collectAsStateWithLifecycle()
        val exportMessage by vm.exportMessage.collectAsStateWithLifecycle()
        val resetMessage by vm.resetMessage.collectAsStateWithLifecycle()
        val detailTarget = vm.detailTarget
        val logsOpen = vm.logsOpen

        LaunchedEffect(exportMessage) {
            exportMessage?.let { toast(it, true); vm.clearExportMessage() }
        }
        LaunchedEffect(resetMessage) {
            resetMessage?.let { toast(it, true); vm.clearResetMessage() }
        }
        LaunchedEffect(state.module.outdated) {
            if (!state.module.outdated) restartPromptDismissed = false
        }
        if (state.module.outdated && !restartPromptDismissed) {
            ModalBottomSheet(
                onDismissRequest = { restartPromptDismissed = true },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                    Text(
                        stringResource(R.string.restart_required_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.restart_required_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            restartPromptDismissed = true
                            vm.setDestination(Destination.STATUS)
                        }) {
                            Text(stringResource(R.string.restart_required_open_status))
                        }
                        TextButton(onClick = { restartPromptDismissed = true }) {
                            Text(stringResource(R.string.common_ok))
                        }
                    }
                }
            }
        }

        BackHandler(enabled = logsOpen) { vm.closeLogs() }
        BackHandler(enabled = detailTarget != null && !logsOpen) { vm.closeDetail() }
        BackHandler(enabled = detailTarget == null && state.destination != Destination.HOME) {
            vm.setDestination(Destination.HOME)
        }
        LifecycleResumeEffect(vm) {
            vm.refreshModuleStatus()
            onPauseOrDispose { }
        }

        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val output = contentResolver.openOutputStream(uri) ?: error(getString(R.string.backup_create_failed))
                        output.bufferedWriter().use { it.write(vm.exportJson()) }
                    }
                    toast(getString(R.string.backup_exported))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.e(TAG, "Backup export failed", failure)
                    toast(getString(R.string.backup_export_failed), true)
                }
            }
        }
        val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        vm.importJson(readLimitedText(uri, MainViewModel.MAX_BACKUP_CHARS))
                    }
                    toast(getString(R.string.backup_restored))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.e(TAG, "Backup restore failed", failure)
                    toast(getString(R.string.backup_restore_failed), true)
                }
            }
        }
        val diagnosticExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            vm.exportDiagnostics(uri)
        }
        val fileCheck = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(vm::inspectFile)
        }

        val collectDiagnostics: () -> Unit = {
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            diagnosticExport.launch("ListCleaner-diagnostic-$stamp.zip")
        }

        Scaffold(
            topBar = {
                if (logsOpen) {
                    DetailTopBar(title = stringResource(R.string.logs_title), onBack = vm::closeLogs)
                } else if (detailTarget == null) {
                    MainToolbar(
                        destination = state.destination,
                        query = state.query,
                        onQuery = vm::setQuery,
                        onRefresh = {
                            if (state.destination == Destination.WORKBENCH &&
                                vm.workbenchTab == WorkbenchTab.COMPONENTS
                            ) {
                                vm.refreshComponents()
                            } else {
                                vm.refresh(forceCatalog = true)
                            }
                        },
                        onRestore = { restore.launch(arrayOf("application/json", "text/plain")) },
                        onBackup = { export.launch("ListCleaner-backup.json") }
                    )
                } else {
                    DetailTopBar(
                        title = state.candidates.firstOrNull { it.rule.packageName == detailTarget }
                            ?.appLabel ?: detailTarget,
                        onBack = vm::closeDetail
                    )
                }
            },
            bottomBar = {
                if (detailTarget == null && !logsOpen) {
                    NavigationBar {
                        Destination.entries.forEach { dest ->
                            NavigationBarItem(
                                selected = state.destination == dest,
                                onClick = { vm.setDestination(dest) },
                                icon = { Icon(dest.icon, null) },
                                label = { Text(stringResource(dest.labelRes())) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when {
                    logsOpen -> LogScreen(vm)
                    detailTarget != null -> AppDetailScreen(detailTarget, state, vm)
                    state.destination == Destination.HOME -> HomeScreen(
                        state,
                        vm,
                        onRestore = { restore.launch(arrayOf("application/json", "text/plain")) },
                        onExport = { export.launch("ListCleaner-backup.json") },
                        onCollectDiagnostics = collectDiagnostics,
                        collectingDiagnostics = collectingDiagnostics
                    )
                    state.destination == Destination.WORKBENCH -> WorkbenchScreen(state, vm)
                    else -> StatusScreen(
                        state,
                        vm,
                        onRestore = { restore.launch(arrayOf("application/json", "text/plain")) },
                        onExport = { export.launch("ListCleaner-backup.json") },
                        collectingDiagnostics = collectingDiagnostics,
                        onCollectDiagnostics = collectDiagnostics,
                        onInspectFile = { fileCheck.launch(arrayOf("*/*")) }
                    )
                }
            }
        }
    }

    private fun readLimitedText(uri: Uri, maxChars: Int): String {
        val input = contentResolver.openInputStream(uri) ?: error(getString(R.string.backup_read_failed))
        return input.bufferedReader().use {
            it.readBackupText(maxChars, getString(R.string.backup_too_large))
        }
    }

    private fun toast(message: String, long: Boolean = false) =
        Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

    private companion object {
        const val TAG = "ListCleaner.MainActivity"
    }
}
