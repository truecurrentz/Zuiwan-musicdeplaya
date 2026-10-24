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

@Composable
fun rememberartcolors(
    artworkUrl: String?,
    enabled: Boolean,
): Pair<Color, Color> {
    val defaultPrimary = Color(0xFF38BDF8)
    val defaultSecondary = Color(0xFF818CF8)

    var targetPrimary by remember { mutableStateOf(defaultPrimary) }
    var targetSecondary by remember { mutableStateOf(defaultSecondary) }
    val context = LocalContext.current

    LaunchedEffect(artworkUrl, enabled) {
        if (!enabled || artworkUrl.isNullOrBlank()) {
            targetPrimary = defaultPrimary
            targetSecondary = defaultSecondary
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(Size(128, 128))
                    .allowHardware(false)
                    .build()
                val res = SingletonImageLoader.get(context).execute(req)
                val bmp = res.image?.toBitmap() ?: return@runCatching
                val palette = Palette.from(bmp).maximumColorCount(16).generate()
                val primaryInt = palette.getVibrantColor(
                    palette.getLightVibrantColor(
                        palette.getDominantColor(0xFF38BDF8.toInt()),
                    ),
                )
                val secondaryInt = palette.getMutedColor(
                    palette.getDarkVibrantColor(0xFF818CF8.toInt()),
                )
                targetPrimary = Color(primaryInt)
                targetSecondary = Color(secondaryInt)
            }
        }
    }

    val animatedPrimary by animateColorAsState(
        targetValue = targetPrimary,
        animationSpec = tween(650),
        label = "accentPrimary",
    )
    val animatedSecondary by animateColorAsState(
        targetValue = targetSecondary,
        animationSpec = tween(650),
        label = "accentSecondary",
    )
    return animatedPrimary to animatedSecondary
}
