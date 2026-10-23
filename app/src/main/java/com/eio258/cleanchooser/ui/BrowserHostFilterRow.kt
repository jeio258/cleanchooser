package com.eio258.cleanchooser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.domain.BrowserLinkConfig
import com.eio258.cleanchooser.domain.normalizeBrowserHost

/** Compact BROWSER-domain selector shown inside the shared top filter row. */
@Composable
fun BrowserHostFilterMenu(
    selected: String?,
    config: BrowserLinkConfig,
    availableHosts: Set<String> = config.hosts,
    onSelected: (String?) -> Unit,
    onManage: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var hostQuery by remember { mutableStateOf("") }
    var anchorWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val menuWidth = 296.dp
    val menuHorizontalOffset = with(density) {
        ((anchorWidthPx.toDp() - menuWidth) / 2)
    }
    val menuScroll = rememberScrollState()
    val selectedTitle = selected ?: stringResource(R.string.common_all)
    val visibleHosts = remember(availableHosts, hostQuery) {
        val query = hostQuery.trim()
        availableHosts.asSequence()
            .filter { query.isEmpty() || it.contains(query, ignoreCase = true) }
            .sorted()
            .toList()
    }

    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.onSizeChanged { anchorWidthPx = it.width },
            contentPadding = PaddingValues(horizontal = 6.dp)
        ) {
            Text(
                stringResource(
                    R.string.compact_filter_format,
                    stringResource(R.string.browser_domain_filter),
                    selectedTitle
                ),
                modifier = Modifier.widthIn(max = 170.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Rounded.ExpandMore, null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { hostQuery = ""; expanded = false },
            modifier = Modifier
                .width(menuWidth)
                .heightIn(max = 420.dp),
            offset = DpOffset(menuHorizontalOffset, 0.dp),
            scrollState = menuScroll
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clickable {
                        hostQuery = ""
                        expanded = false
                        onSelected(null)
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.width(24.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (selected == null) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(stringResource(R.string.common_all))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clickable {
                        hostQuery = ""
                        expanded = false
                        onManage()
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(24.dp))
                Text(stringResource(R.string.browser_hosts_manage))
            }
            HorizontalDivider()
            OutlinedTextField(
                value = hostQuery,
                onValueChange = { hostQuery = it },
                modifier = Modifier
                    .width(280.dp)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(24.dp)) },
                placeholder = {
                    Text(
                        stringResource(R.string.browser_domain_search),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            )
            if (visibleHosts.isEmpty()) {
                Text(
                    stringResource(R.string.browser_domain_search_empty),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                visibleHosts.forEach { host ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clickable {
                                hostQuery = ""
                                expanded = false
                                onSelected(host)
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.width(24.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (selected == host) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            host,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserHostDialog(
    config: BrowserLinkConfig,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var hosts by remember(config.hosts) { mutableStateOf(config.hosts.sorted()) }
    var input by remember { mutableStateOf("") }
    val normalized = normalizeBrowserHost(input)
    val canAdd = normalized != null && normalized !in hosts && hosts.size < BrowserLinkConfig.MAX_HOSTS

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.browser_hosts_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.browser_hosts_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.browser_host_input)) },
                    placeholder = { Text(stringResource(R.string.browser_host_example)) },
                    isError = input.isNotBlank() && normalized == null
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = canAdd,
                    onClick = {
                        val host = normalized ?: return@Button
                        hosts = (hosts + host).distinct().sorted()
                        input = ""
                    }
                ) { Text(stringResource(R.string.common_add)) }
            }
            if (input.isNotBlank() && normalized == null) {
                Text(
                    stringResource(R.string.browser_host_invalid),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (hosts.isEmpty()) {
                Text(
                    stringResource(R.string.browser_hosts_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(top = 8.dp)) {
                    items(hosts, key = { it }) { host ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(host, Modifier.weight(1f))
                            IconButton(onClick = { hosts = hosts - host }) {
                                Icon(Icons.Rounded.Delete, stringResource(R.string.common_delete))
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                TextButton(onClick = { onSave(hosts.toSet()); onDismiss() }) {
                    Text(stringResource(R.string.common_save))
                }
            }
        }
    }
}
