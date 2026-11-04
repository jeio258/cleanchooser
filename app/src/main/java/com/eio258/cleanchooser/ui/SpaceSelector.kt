package com.eio258.cleanchooser.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.data.UserSpace

/**
 * 轻量空间选择器：当前空间名 + 下拉切换。只有一个空间（无 root / 单用户设备）时
 * 整体不渲染。由 WorkbenchScreen 顶部接线使用。
 *
 * 接线方式（不改 WorkbenchScreen 也可用，示例由集成方放入 Column 顶部、
 * SegmentedTabs 之前）：
 * ```
 * val spaces by vm.spaceManager.spaces.collectAsState()
 * val activeSpace by vm.repository.activeSpace.collectAsState()
 * SpaceSelectorBar(
 *     spaces = spaces,
 *     selectedUserId = activeSpace,
 *     onSelect = { scope.launch { vm.switchSpace(it) } }
 * )
 * ```
 */
@Composable
fun SpaceSelectorBar(
    spaces: List<UserSpace>,
    selectedUserId: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (spaces.size <= 1) return
    var expanded by remember { mutableStateOf(false) }
    val current = spaces.firstOrNull { it.userId == selectedUserId }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { expanded = true }) {
            Icon(
                Icons.Rounded.Layers,
                contentDescription = stringResource(R.string.space_switch),
                Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = current?.title
                    ?: stringResource(R.string.space_user_title, selectedUserId),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            spaces.forEach { space ->
                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.space_menu_item, space.title, space.userId))
                    },
                    trailingIcon = if (space.userId == selectedUserId) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        expanded = false
                        if (space.userId != selectedUserId) onSelect(space.userId)
                    }
                )
            }
        }
    }
}
