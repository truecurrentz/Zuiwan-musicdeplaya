package zuiwan.sheet

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import zuiwan.model.Track
import zuiwan.model.artistline
import zuiwan.model.fallbackart
import zuiwan.model.formatclock
import zuiwan.model.islocal
import zuiwan.model.trackart
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.ZIcons
import zuiwan.ui.artimage
import zuiwan.ui.pillbtn
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun editorsheet(
    track: Track,
    onDismiss: () -> Unit,
    onOpenEffects: () -> Unit = {},
) {
    val hub = LocalHub.current
    val glassTokens = LocalGlass.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artistline()) }
    var album by remember(track.id) { mutableStateOf(track.album?.name.orEmpty()) }
    var pickedCover by remember(track.id) { mutableStateOf<Uri?>(null) }

    val fullMs = (track.duration.coerceAtLeast(15) * 1_000L).toFloat()
    var trimStart by remember(track.id) {
        mutableFloatStateOf(track.trimStartMs.toFloat().coerceIn(0f, fullMs))
    }
    var trimEnd by remember(track.id) {
        val initEnd = if (track.trimEndMs > track.trimStartMs) track.trimEndMs.toFloat() else fullMs
        mutableFloatStateOf(initEnd.coerceIn(0f, fullMs))
    }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pickedCover = uri
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = glassTokens.bgSecondary,
        contentColor = glassTokens.ink,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Audio & Metadata Editor",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = glassTokens.ink,
            )
            Spacer(Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                artimage(
                    url = pickedCover?.toString() ?: track.trackart(),
                    fallbackUrl = track.fallbackart(),
                    shape = RoundedCornerShape(16.dp),
                    fallbackIcon = ZIcons.Note,
                    modifier = Modifier.size(76.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pillbtn(
                        text = "Change Cover Art",
                        icon = ZIcons.Image,
                        onClick = { coverPicker.launch(arrayOf("image/*")) },
                    )
                    pillbtn(
                        text = "Audio Effects & EQ",
                        icon = ZIcons.Sliders,
                        onClick = {
                            onDismiss()
                            onOpenEffects()
                        },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                singleLine = true,
                label = { Text("Artist") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = album,
                onValueChange = { album = it },
                singleLine = true,
                label = { Text("Album") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(18.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Cue Trim Range",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = glassTokens.ink,
                )
                Text(
                    text = "${formatclock(trimStart.roundToLong())} — ${formatclock(trimEnd.roundToLong())}",
                    style = MaterialTheme.typography.labelLarge,
                    color = glassTokens.accent,
                )
            }

            RangeSlider(
                value = trimStart..trimEnd,
                onValueChange = { range ->
                    trimStart = range.start
                    trimEnd = range.endInclusive.coerceAtLeast(range.start + 1_000f)
                },
                valueRange = 0f..fullMs,
                colors = SliderDefaults.colors(
                    thumbColor = glassTokens.accent,
                    activeTrackColor = glassTokens.accent,
                    inactiveTrackColor = glassTokens.glassElevated,
                ),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                pillbtn(
                    text = "Reset Trim",
                    icon = ZIcons.Refresh,
                    onClick = {
                        trimStart = 0f
                        trimEnd = fullMs
                    },
                )
                if (track.islocal()) {
                    pillbtn(
                        text = "Export Clip",
                        icon = ZIcons.Scissors,
                        onClick = {
                            hub.exportclip(
                                track = track,
                                startMs = trimStart.roundToLong(),
                                endMs = trimEnd.roundToLong(),
                            )
                            onDismiss()
                        },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                pillbtn(
                    text = "Cancel",
                    icon = ZIcons.Close,
                    onClick = onDismiss,
                )
                Spacer(Modifier.width(10.dp))
                pillbtn(
                    text = "Save Changes",
                    icon = ZIcons.Check,
                    primary = true,
                    onClick = {
                        val sMs = trimStart.roundToLong()
                        val eMs = if (trimEnd >= fullMs - 500f) 0L else trimEnd.roundToLong()
                        hub.saveedited(
                            track = track,
                            title = title,
                            artist = artist,
                            album = album,
                            artworkUri = pickedCover,
                            trimStartMs = sMs,
                            trimEndMs = eMs,
                        )
                        onDismiss()
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
