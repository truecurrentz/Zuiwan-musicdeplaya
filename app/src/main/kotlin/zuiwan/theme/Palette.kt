package zuiwan.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zuiwan.model.upscaleart

@Composable
fun artcolors(
    artworkUrl: String?,
    enabled: Boolean,
    dark: Boolean,
): Pair<Color, Color> {
    val defaultPrimary = if (dark) Color(0xFFF2A7B5) else Color(0xFF007AFF)
    val defaultSecondary = if (dark) Color(0xFF8FB8FF) else Color(0xFF5856D6)

    var targetPrimary by remember(dark) { mutableStateOf(defaultPrimary) }
    var targetSecondary by remember(dark) { mutableStateOf(defaultSecondary) }
    val context = LocalContext.current

    LaunchedEffect(artworkUrl, enabled, dark) {
        val cleanUrl = artworkUrl?.let { upscaleart(it, 256) }.orEmpty()
        if (!enabled || cleanUrl.isBlank()) {
            targetPrimary = defaultPrimary
            targetSecondary = defaultSecondary
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(context)
                    .data(cleanUrl)
                    .size(Size(128, 128))
                    .allowHardware(false)
                    .build()
                val res = SingletonImageLoader.get(context).execute(req)
                val bmp = res.image?.toBitmap() ?: return@runCatching
                val palette = Palette.from(bmp).maximumColorCount(20).generate()
                val primarySwatch =
                    (if (dark) palette.lightVibrantSwatch else palette.vibrantSwatch)
                        ?: palette.vibrantSwatch
                        ?: palette.mutedSwatch
                        ?: palette.dominantSwatch
                val secondarySwatch =
                    palette.mutedSwatch
                        ?: palette.darkVibrantSwatch
                        ?: palette.dominantSwatch
                targetPrimary = primarySwatch?.let { Color(it.rgb) } ?: defaultPrimary
                targetSecondary = secondarySwatch?.let { Color(it.rgb) } ?: defaultSecondary
            }
        }
    }

    val animatedPrimary by animateColorAsState(
        targetValue = targetPrimary,
        animationSpec = tween(550),
        label = "accentPrimary",
    )
    val animatedSecondary by animateColorAsState(
        targetValue = targetSecondary,
        animationSpec = tween(550),
        label = "accentSecondary",
    )
    return animatedPrimary to animatedSecondary
}
