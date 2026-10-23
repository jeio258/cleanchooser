package com.eio258.cleanchooser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.domain.ComponentCandidate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComponentTitleDialog(
    item: ComponentCandidate,
    currentTitle: String?,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(item.rule.id) { mutableStateOf(currentTitle.orEmpty()) }
    LaunchedEffect(currentTitle, item.rule.id) { text = currentTitle.orEmpty() }
    val trimmed = text.trim()
    val normalizedCurrent = currentTitle.orEmpty()
    val changed = trimmed != normalizedCurrent
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.component_title_dialog_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.component_title_original, item.activityLabel),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(R.string.component_title_help, stringResource(item.rule.kind.titleRes())),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                item.rule.className,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 64 && it.none(Char::isISOControl)) text = it },
                label = { Text(stringResource(R.string.component_title_field)) },
                supportingText = { Text(stringResource(R.string.component_title_support, text.length)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                TextButton(
                    onClick = { onSave(trimmed.ifEmpty { null }); onDismiss() },
                    enabled = changed
                ) {
                    Text(stringResource(R.string.common_save))
                }
            }
        }
    }
}
