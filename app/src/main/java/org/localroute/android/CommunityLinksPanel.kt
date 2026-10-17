package org.localroute.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

@Composable
internal fun CommunityLinksPanel() {
    val uri = LocalUriHandler.current
    OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(4.dp)) {
            listOf("ТГ" to "https://t.me/TheLocalRoute", "МАКС" to "https://max.ru/channel_LocalRoute", "GitHub" to "https://github.com/iKokTot/local-route").forEach { (name, address) ->
                TextButton(onClick = { uri.openUri(address) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(name, maxLines = 1) }
            }
        }
    }
}
