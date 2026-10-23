package com.eio258.cleanchooser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eio258.cleanchooser.ListCleanerApp
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.domain.VisibilityScope
import com.eio258.cleanchooser.domain.AppType
import com.eio258.cleanchooser.domain.listCleanerAppType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScopeAppEntry(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val normalizedLabel: String = label.lowercase(),
    val normalizedPackage: String = packageName.lowercase(),
)

private object ScopeIconCache {
    val icons = LruCache<String, Bitmap>(128)
}

@Suppress("DEPRECATION")
private fun loadScopeApps(pm: PackageManager, selfPackage: String): List<ScopeAppEntry> {
    val installed = if (android.os.Build.VERSION.SDK_INT >= 33) {
        pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
    } else {
        pm.getInstalledApplications(0)
    }
    return installed.asSequence()
        .filter { it.packageName != selfPackage && it.packageName != "android" }
        .map { info ->
            ScopeAppEntry(
                packageName = info.packageName,
                label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName),
                system = info.listCleanerAppType() == AppType.SYSTEM,
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy<ScopeAppEntry> { it.normalizedLabel }.thenBy { it.packageName })
        .toList()
}

@Composable
private fun scopeAppIcon(packageName: String): Bitmap? {
    val context = LocalContext.current
    val cached = remember(packageName) { ScopeIconCache.icons.get(packageName) }
    val icon by produceState<Bitmap?>(initialValue = cached, packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager.getApplicationIcon(packageName).toBitmap(64, 64)
                }.getOrNull()?.also { ScopeIconCache.icons.put(packageName, it) }
            }
        }
    }
    return icon
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppScopePickerDialog(
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    dismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as ListCleanerApp
    val visibilityScopes by app.rules.visibilityScopes.collectAsStateWithLifecycle()
    val fullPackages by app.rules.visibilityFullPackages.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf<List<ScopeAppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadScopeApps(context.packageManager, context.packageName) }
        loading = false
    }

    val visible = remember(apps, query, showSystem, selected) {
        val needle = query.trim().lowercase()
        apps.filter { entry ->
            (showSystem || !entry.system) &&
                (needle.isEmpty() || entry.normalizedLabel.contains(needle) || entry.normalizedPackage.contains(needle))
        }.sortedWith(
            compareBy<ScopeAppEntry> { if (it.packageName in selected) 0 else 1 }
                .thenBy { it.normalizedLabel }
                .thenBy { it.packageName }
        )
    }
    val activeTargets = remember(visibilityScopes, fullPackages) {
        com.eio258.cleanchooser.domain.VisibilityCompatConfig(visibilityScopes, fullPackages).activePackages()
    }

    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.visibility_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.visibility_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(VisibilityScope.entries, key = { it.name }) { scope ->
                    val checked = scope in visibilityScopes
                    FilterChip(
                        selected = checked,
                        onClick = {
                            app.rules.setVisibilityScopes(
                                if (checked) visibilityScopes - scope else visibilityScopes + scope
                            )
                        },
                        label = { Text(stringResource(scope.titleRes())) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
            Text(
                if (visibilityScopes.isEmpty()) stringResource(R.string.visibility_no_categories)
                else stringResource(R.string.visibility_active_targets, activeTargets.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.visibility_search_sources)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.visibility_show_system),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
                Switch(checked = showSystem, onCheckedChange = { showSystem = it })
            }
            Text(
                stringResource(R.string.visibility_summary, visible.size, apps.size, selected.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (loading) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp)) {
                    items(visible, key = { it.packageName }) { entry ->
                        val checked = entry.packageName in selected
                        AppEntryRow(
                            title = entry.label,
                            subtitle = entry.packageName,
                            icon = scopeAppIcon(entry.packageName),
                            checkState = if (checked) androidx.compose.ui.state.ToggleableState.On
                            else androidx.compose.ui.state.ToggleableState.Off,
                            onCheck = {
                                onSelectedChange(
                                    if (checked) selected - entry.packageName
                                    else selected + entry.packageName
                                )
                            },
                            onOpen = {
                                onSelectedChange(
                                    if (checked) selected - entry.packageName
                                    else selected + entry.packageName
                                )
                            }
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = dismiss) { Text(stringResource(R.string.common_done)) }
            }
        }
    }
}
