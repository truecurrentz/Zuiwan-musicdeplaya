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
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.chip
import zuiwan.ui.glass
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun temposheet(onDismiss: () -> Unit) {
    val hub = LocalHub.current
    val glassTokens = LocalGlass.current
    val state by hub.pilot.state.collectAsState()
    var speed by remember(state.speed) { mutableFloatStateOf(state.speed) }
    var pitch by remember(state.pitch) { mutableFloatStateOf(state.pitch) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = glassTokens.glassStrong,
        contentColor = glassTokens.ink,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 22.dp),
        ) {
            Text(
                text = "Tempo & Pitch",
                style = MaterialTheme.typography.headlineSmall,
                color = glassTokens.ink,
            )
            Spacer(Modifier.height(14.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    Triple("Normal", 1.0f, 1.0f),
                    Triple("Nightcore", 1.22f, 1.22f),
                    Triple("Slowed", 0.85f, 0.88f),
                    Triple("1.25x", 1.25f, 1.0f),
                    Triple("1.5x", 1.5f, 1.0f),
                ).forEach { (label, s, p) ->
                    val selected = (speed * 100).roundToInt() == (s * 100).roundToInt() &&
                        (pitch * 100).roundToInt() == (p * 100).roundToInt()
                    chip(
                        label = label,
                        selected = selected,
                        onClick = {
                            speed = s
                            pitch = p
                            hub.pilot.settempo(s, p)
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            glass(
                shape = RoundedCornerShape(18.dp),
                elevated = true,
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Speed", style = MaterialTheme.typography.titleMedium, color = glassTokens.ink)
                        Text("%.2fx".format(speed), style = MaterialTheme.typography.labelLarge, color = glassTokens.accent)
                    }
                    Slider(
                        value = speed,
                        onValueChange = {
                            speed = (it * 20f).roundToInt() / 20f
                            hub.pilot.settempo(speed, pitch)
                        },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = glassTokens.accent,
                            activeTrackColor = glassTokens.accent,
                            inactiveTrackColor = glassTokens.glassBorder,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Pitch", style = MaterialTheme.typography.titleMedium, color = glassTokens.ink)
                        Text("%.2fx".format(pitch), style = MaterialTheme.typography.labelLarge, color = glassTokens.accent)
                    }
                    Slider(
                        value = pitch,
                        onValueChange = {
                            pitch = (it * 20f).roundToInt() / 20f
                            hub.pilot.settempo(speed, pitch)
                        },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = glassTokens.accent,
                            activeTrackColor = glassTokens.accent,
                            inactiveTrackColor = glassTokens.glassBorder,
                        ),
                    )
                }
            }
        }
    }
}
