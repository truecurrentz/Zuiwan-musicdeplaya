package zuiwan.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zuiwan.model.formatclock
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.GlassBox
import zuiwan.ui.GlassChip
import zuiwan.ui.GlassPillButton
import zuiwan.ui.ZIcons

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepSheet(onDismiss: () -> Unit) {
    val hub = LocalHub.current
    val glass = LocalGlass.current
    val sleepState by hub.pilot.sleep.state.collectAsState()
    var customMin by remember { mutableFloatStateOf(25f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = glass.glassStrong,
        contentColor = glass.ink,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = "Sleep Timer",
                style = MaterialTheme.typography.headlineSmall,
                color = glass.ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (sleepState.active) {
                    if (sleepState.finishTrack && sleepState.remainingMs <= 0L) {
                        "Stopping after current track"
                    } else {
                        "Time remaining: ${formatclock(sleepState.remainingMs)}"
                    }
                } else {
                    "Gently pause playback automatically"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (sleepState.active) glass.accent else glass.inkSecondary,
            )

            Spacer(Modifier.height(18.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(5, 15, 30, 45, 60, 90).forEach { mins ->
                    GlassChip(
                        label = "${mins}m",
                        selected = false,
                        onClick = {
                            hub.pilot.sleep.startsleep(mins)
                            onDismiss()
                        },
                    )
                }
                GlassChip(
                    label = "End of track",
                    selected = sleepState.active && sleepState.finishTrack,
                    onClick = {
                        hub.pilot.sleep.startsleep(0, finishCurrentTrack = true)
                        onDismiss()
                    },
                )
            }

            Spacer(Modifier.height(18.dp))

            GlassBox(
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Custom duration", style = MaterialTheme.typography.titleMedium, color = glass.ink)
                        Text("${customMin.toInt()} min", style = MaterialTheme.typography.labelLarge, color = glass.accent)
                    }
                    Slider(
                        value = customMin,
                        onValueChange = { customMin = it },
                        valueRange = 5f..180f,
                        colors = SliderDefaults.colors(
                            thumbColor = glass.accent,
                            activeTrackColor = glass.accent,
                            inactiveTrackColor = glass.glassBorder,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (sleepState.active) {
                    GlassPillButton(
                        text = "Cancel",
                        icon = ZIcons.Close,
                        onClick = {
                            hub.pilot.sleep.cancelsleep()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                GlassPillButton(
                    text = "Start ${customMin.toInt()}m",
                    icon = ZIcons.Timer,
                    primary = true,
                    onClick = {
                        hub.pilot.sleep.startsleep(customMin.toInt())
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
