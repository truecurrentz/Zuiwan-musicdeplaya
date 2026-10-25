package zuiwan.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import zuiwan.model.EqBand
import zuiwan.model.EqProfile
import zuiwan.model.FilterKind
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.glass
import zuiwan.ui.chip
import zuiwan.ui.topbar
import zuiwan.ui.scrollhost
import zuiwan.ui.headerscroll
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

private val studioPresets: List<EqProfile> = listOf(
    EqProfile(id = "flat", name = "Flat", enabled = true, preampDb = 0.0),
    EqProfile(
        id = "bass",
        name = "Bass Boost",
        enabled = true,
        preampDb = -2.5,
        bands = listOf(
            EqBand(31.0, 5.5, 0.71, FilterKind.LSC),
            EqBand(62.0, 4.5, 1.0, FilterKind.PK),
            EqBand(125.0, 2.5, 1.1, FilterKind.PK),
            EqBand(250.0, 0.5, 1.2, FilterKind.PK),
            EqBand(500.0, 0.0, 1.2, FilterKind.PK),
            EqBand(1000.0, 0.0, 1.2, FilterKind.PK),
            EqBand(2000.0, 0.5, 1.2, FilterKind.PK),
            EqBand(4000.0, 1.0, 1.2, FilterKind.PK),
            EqBand(8000.0, 1.0, 1.2, FilterKind.PK),
            EqBand(16000.0, 0.5, 0.71, FilterKind.HSC),
        ),
    ),
    EqProfile(
        id = "harman",
        name = "Harman Curve",
        enabled = true,
        preampDb = -2.0,
        bands = listOf(
            EqBand(31.0, 4.2, 0.71, FilterKind.LSC),
            EqBand(62.0, 3.4, 1.0, FilterKind.PK),
            EqBand(125.0, 1.2, 1.1, FilterKind.PK),
            EqBand(250.0, -0.8, 1.2, FilterKind.PK),
            EqBand(500.0, -0.4, 1.2, FilterKind.PK),
            EqBand(1000.0, 0.4, 1.2, FilterKind.PK),
            EqBand(2000.0, 2.2, 1.2, FilterKind.PK),
            EqBand(4000.0, 3.0, 1.2, FilterKind.PK),
            EqBand(8000.0, 1.6, 1.2, FilterKind.PK),
            EqBand(16000.0, 1.2, 0.71, FilterKind.HSC),
        ),
    ),
    EqProfile(
        id = "vocal",
        name = "Vocal Clarity",
        enabled = true,
        preampDb = -1.5,
        bands = listOf(
            EqBand(31.0, -2.0, 0.71, FilterKind.LSC),
            EqBand(62.0, -1.5, 1.0, FilterKind.PK),
            EqBand(125.0, -0.5, 1.1, FilterKind.PK),
            EqBand(250.0, 1.0, 1.2, FilterKind.PK),
            EqBand(500.0, 2.2, 1.2, FilterKind.PK),
            EqBand(1000.0, 3.5, 1.1, FilterKind.PK),
            EqBand(2000.0, 3.8, 1.1, FilterKind.PK),
            EqBand(4000.0, 2.4, 1.2, FilterKind.PK),
            EqBand(8000.0, 1.2, 1.2, FilterKind.PK),
            EqBand(16000.0, 0.5, 0.71, FilterKind.HSC),
        ),
    ),
    EqProfile(
        id = "sparkle",
        name = "Air & Sparkle",
        enabled = true,
        preampDb = -2.0,
        bands = listOf(
            EqBand(31.0, 1.5, 0.71, FilterKind.LSC),
            EqBand(62.0, 1.5, 1.0, FilterKind.PK),
            EqBand(125.0, 0.5, 1.1, FilterKind.PK),
            EqBand(250.0, 0.0, 1.2, FilterKind.PK),
            EqBand(500.0, 0.0, 1.2, FilterKind.PK),
            EqBand(1000.0, 0.5, 1.2, FilterKind.PK),
            EqBand(2000.0, 1.5, 1.2, FilterKind.PK),
            EqBand(4000.0, 3.2, 1.2, FilterKind.PK),
            EqBand(8000.0, 4.5, 1.1, FilterKind.PK),
            EqBand(16000.0, 5.0, 0.71, FilterKind.HSC),
        ),
    ),
)

@Composable
fun viewequalizer(
    onBack: () -> Unit,
    bottomPadding: PaddingValues,
) {
    val hub = LocalHub.current
    val glassTokens = LocalGlass.current
    val profile by hub.eq.collectAsState()

    val headerScroll = headerscroll()
    val listState = rememberLazyListState()
    val lifted by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 12
        }
    }

    scrollhost(
        scroll = headerScroll,
        lifted = lifted,
        header = {
            Column(modifier = Modifier.fillMaxWidth()) {
                topbar(title = "Parametric EQ", onBack = onBack) {
                    Switch(
                        checked = profile.enabled,
                        onCheckedChange = { enabled ->
                            hub.pilot.seteq(profile.copy(enabled = enabled))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = glassTokens.onAccent,
                            checkedTrackColor = glassTokens.accent,
                        ),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
                ) {
                    studioPresets.forEach { preset ->
                        chip(
                            label = preset.name,
                            selected = profile.id == preset.id,
                            onClick = {
                                hub.pilot.seteq(preset.copy(enabled = true))
                            },
                        )
                    }
                }
            }
        },
    ) { topPad ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = topPad + 6.dp,
                bottom = bottomPadding.calculateBottomPadding() + 28.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "curve") {
                glass(
                    shape = RoundedCornerShape(22.dp),
                    elevated = true,
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    eqcanvas(profile = profile)
                }
            }

            item(key = "preamp") {
                glass(
                    shape = RoundedCornerShape(18.dp),
                    elevated = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Preamp Gain", style = MaterialTheme.typography.titleMedium, color = glassTokens.ink)
                            Text(
                                text = "%+.1f dB".format(profile.preampDb),
                                style = MaterialTheme.typography.labelLarge,
                                color = glassTokens.accent,
                            )
                        }
                        Slider(
                            value = profile.preampDb.toFloat(),
                            onValueChange = { next ->
                                val snapped = (next * 2f).roundToInt() / 2.0
                                hub.pilot.seteq(profile.copy(id = "custom", name = "Custom", enabled = true, preampDb = snapped))
                            },
                            valueRange = -12f..12f,
                            colors = SliderDefaults.colors(
                                thumbColor = glassTokens.accent,
                                activeTrackColor = glassTokens.accent,
                                inactiveTrackColor = glassTokens.glassBorder,
                            ),
                        )
                    }
                }
            }

            itemsIndexed(profile.bands) { idx, band ->
                val freqLabel = if (band.frequency >= 1000.0) {
                    "${(band.frequency / 1000.0).toInt()} kHz"
                } else {
                    "${band.frequency.toInt()} Hz"
                }
                glass(
                    shape = RoundedCornerShape(16.dp),
                    elevated = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.width(64.dp)) {
                            Text(freqLabel, style = MaterialTheme.typography.titleSmall, color = glassTokens.ink)
                            Text(band.kind.name, style = MaterialTheme.typography.labelSmall, color = glassTokens.inkMuted)
                        }
                        Slider(
                            value = band.gainDb.toFloat(),
                            onValueChange = { raw ->
                                val snapped = (raw * 2f).roundToInt() / 2.0
                                val updated = profile.bands.toMutableList()
                                updated[idx] = band.copy(gainDb = snapped)
                                hub.pilot.seteq(
                                    profile.copy(
                                        id = "custom",
                                        name = "Custom",
                                        enabled = true,
                                        bands = updated,
                                    ),
                                )
                            },
                            valueRange = -12f..12f,
                            colors = SliderDefaults.colors(
                                thumbColor = glassTokens.accent,
                                activeTrackColor = glassTokens.accent,
                                inactiveTrackColor = glassTokens.glassBorder,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "%+.1f".format(band.gainDb),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (band.gainDb != 0.0) glassTokens.accent else glassTokens.inkSecondary,
                            modifier = Modifier.width(46.dp).padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun eqcanvas(profile: EqProfile) {
    val glassTokens = LocalGlass.current
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(136.dp),
    ) {
        val midY = size.height / 2f
        drawLine(
            color = glassTokens.glassBorder,
            start = Offset(0f, midY),
            end = Offset(size.width, midY),
            strokeWidth = 1.dp.toPx(),
        )

        val steps = 96
        val minLog = ln(20.0)
        val maxLog = ln(20_000.0)
        val linePath = Path()
        val fillPath = Path()

        for (i in 0..steps) {
            val t = i.toDouble() / steps.toDouble()
            val freq = exp(minLog + (maxLog - minLog) * t)
            var db = if (profile.enabled) profile.preampDb * 0.35 else 0.0
            if (profile.enabled) {
                profile.bands.forEach { b ->
                    if (b.enabled) {
                        val octDist = ln(freq / b.frequency) / ln(2.0)
                        val bw = (1.0 / b.q.coerceAtLeast(0.25))
                        db += b.gainDb * exp(-(octDist / bw).pow(2.0) * 1.8)
                    }
                }
            }
            val clamped = db.coerceIn(-14.0, 14.0)
            val x = (t * size.width).toFloat()
            val y = (midY - (clamped / 14.0) * (size.height * 0.44f)).toFloat()
            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            if (i == steps) {
                fillPath.lineTo(x, size.height)
                fillPath.close()
            }
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(glassTokens.accent.copy(alpha = 0.28f), Color.Transparent),
            ),
        )
        drawPath(
            path = linePath,
            color = glassTokens.accent,
            style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
