package com.eio258.cleanchooser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eio258.cleanchooser.R

/**
 * Runtime log viewer. The home page hit counters only summarize; this page shows the actual
 * trail: module hook activity from the global log buffer (root) and this app's own crash log.
 */
@Composable
fun LogScreen(vm: MainViewModel) {
    val moduleLog by vm.moduleLog.collectAsStateWithLifecycle()
    val appLog by vm.appLog.collectAsStateWithLifecycle()
    val loading by vm.logsLoading.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize()) {
        SegmentedTabs(
            labels = listOf(
                stringResource(R.string.logs_tab_module),
                stringResource(R.string.logs_tab_app)
            ),
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = vm::refreshLogs, enabled = !loading) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.logs_refresh))
            }
            IconButton(onClick = {
                clipboard.setText(AnnotatedString((if (tab == 0) moduleLog else appLog).joinToString("\n")))
            }) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.logs_copy))
            }
            if (tab == 1 && appLog.isNotEmpty()) {
                TextButton(onClick = vm::clearAppLog) { Text(stringResource(R.string.logs_clear)) }
            }
        }
        val lines = if (tab == 0) moduleLog else appLog
        if (lines.isEmpty()) {
            Text(
                if (loading) stringResource(R.string.logs_loading)
                else stringResource(R.string.logs_empty),
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(lines) { line ->
                    Text(
                        line,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}
