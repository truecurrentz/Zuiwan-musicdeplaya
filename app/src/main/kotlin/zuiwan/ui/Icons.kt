package zuiwan.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object ZIcons {
    private fun stroke(
        name: String,
        width: Float = 2f,
        block: PathBuilder.() -> Unit,
    ): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        ).build()

    private fun solid(
        name: String,
        fillType: PathFillType = PathFillType.NonZero,
        block: PathBuilder.() -> Unit,
    ): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            fill = SolidColor(Color.White),
            pathFillType = fillType,
            pathBuilder = block,
        ).build()

    val Brand: ImageVector = stroke("Brand", width = 2.6f) {
        moveTo(5.2f, 6.2f)
        horizontalLineTo(18.8f)
        lineTo(6.4f, 17.8f)
        horizontalLineTo(18.8f)
    }

    val Home: ImageVector = stroke("Home") {
        moveTo(12f, 3f)
        arcTo(9f, 9f, 0f, true, true, 11.99f, 3f)
        close()
        moveTo(15.8f, 8.2f)
        lineTo(13.8f, 13.8f)
        lineTo(8.2f, 15.8f)
        lineTo(10.2f, 10.2f)
        close()
    }

    val Explore: ImageVector = stroke("Explore") {
        moveTo(12f, 3f)
        arcTo(9f, 9f, 0f, true, true, 11.99f, 3f)
        close()
        moveTo(15.8f, 8.2f)
        lineTo(13.8f, 13.8f)
        lineTo(8.2f, 15.8f)
        lineTo(10.2f, 10.2f)
        close()
    }

    val Search: ImageVector = stroke("Search") {
        moveTo(11f, 4f)
        arcTo(7f, 7f, 0f, true, true, 10.99f, 4f)
        close()
        moveTo(16.2f, 16.2f)
        lineTo(20.5f, 20.5f)
    }

    val Grid: ImageVector = stroke("Grid") {
        moveTo(4.5f, 4.5f)
        horizontalLineTo(10.2f)
        verticalLineTo(10.2f)
        horizontalLineTo(4.5f)
        close()
        moveTo(13.8f, 4.5f)
        horizontalLineTo(19.5f)
        verticalLineTo(10.2f)
        horizontalLineTo(13.8f)
        close()
        moveTo(4.5f, 13.8f)
        horizontalLineTo(10.2f)
        verticalLineTo(19.5f)
        horizontalLineTo(4.5f)
        close()
        moveTo(13.8f, 13.8f)
        horizontalLineTo(19.5f)
        verticalLineTo(19.5f)
        horizontalLineTo(13.8f)
        close()
    }

    val List: ImageVector = stroke("List") {
        moveTo(4.5f, 6.5f)
        horizontalLineTo(4.8f)
        moveTo(8.8f, 6.5f)
        horizontalLineTo(19.5f)
        moveTo(4.5f, 12f)
        horizontalLineTo(4.8f)
        moveTo(8.8f, 12f)
        horizontalLineTo(19.5f)
        moveTo(4.5f, 17.5f)
        horizontalLineTo(4.8f)
        moveTo(8.8f, 17.5f)
        horizontalLineTo(19.5f)
    }

    val Sort: ImageVector = stroke("Sort") {
        moveTo(4f, 7f)
        horizontalLineTo(20f)
        moveTo(4f, 12f)
        horizontalLineTo(15f)
        moveTo(4f, 17f)
        horizontalLineTo(10f)
    }

    val Library: ImageVector = Grid

    val Sparkles: ImageVector = stroke("Sparkles", width = 1.9f) {
        moveTo(11f, 3.5f)
        lineTo(12.6f, 8.4f)
        lineTo(17.5f, 10f)
        lineTo(12.6f, 11.6f)
        lineTo(11f, 16.5f)
        lineTo(9.4f, 11.6f)
        lineTo(4.5f, 10f)
        lineTo(9.4f, 8.4f)
        close()
        moveTo(18.2f, 14.5f)
        lineTo(19f, 17f)
        lineTo(21.5f, 17.8f)
        lineTo(19f, 18.6f)
        lineTo(18.2f, 21f)
        lineTo(17.4f, 18.6f)
        lineTo(15f, 17.8f)
        lineTo(17.4f, 17f)
        close()
    }

    val Globe: ImageVector = stroke("Globe") {
        moveTo(12f, 3.5f)
        arcTo(8.5f, 8.5f, 0f, true, true, 11.99f, 3.5f)
        close()
        moveTo(3.8f, 12f)
        horizontalLineTo(20.2f)
        moveTo(12f, 3.6f)
        curveTo(14.8f, 6.2f, 14.8f, 17.8f, 12f, 20.4f)
        moveTo(12f, 3.6f)
        curveTo(9.2f, 6.2f, 9.2f, 17.8f, 12f, 20.4f)
    }

    val Waveform: ImageVector = stroke("Waveform", width = 2.2f) {
        moveTo(4.5f, 10f)
        verticalLineTo(14f)
        moveTo(8.2f, 6.5f)
        verticalLineTo(17.5f)
        moveTo(12f, 4f)
        verticalLineTo(20f)
        moveTo(15.8f, 7.5f)
        verticalLineTo(16.5f)
        moveTo(19.5f, 10f)
        verticalLineTo(14f)
    }

    val Star: ImageVector = stroke("Star", width = 1.9f) {
        moveTo(12f, 3.6f)
        lineTo(14.5f, 8.8f)
        lineTo(20.2f, 9.6f)
        lineTo(16.1f, 13.6f)
        lineTo(17.1f, 19.3f)
        lineTo(12f, 16.6f)
        lineTo(6.9f, 19.3f)
        lineTo(7.9f, 13.6f)
        lineTo(3.8f, 9.6f)
        lineTo(9.5f, 8.8f)
        close()
    }

    val Offline: ImageVector = stroke("Offline") {
        moveTo(12f, 3.5f)
        arcTo(8.5f, 8.5f, 0f, true, true, 11.99f, 3.5f)
        close()
        moveTo(8.6f, 12.2f)
        lineTo(11f, 14.6f)
        lineTo(15.6f, 9.8f)
    }

    val Trend: ImageVector = stroke("Trend") {
        moveTo(4f, 16.5f)
        lineTo(9.5f, 11f)
        lineTo(13.2f, 14.7f)
        lineTo(20f, 7.8f)
        moveTo(15f, 7.8f)
        horizontalLineTo(20f)
        verticalLineTo(12.8f)
    }

    val Note: ImageVector = stroke("Note") {
        moveTo(9f, 17.5f)
        arcTo(2.4f, 2.4f, 0f, true, true, 8.99f, 17.5f)
        close()
        moveTo(17f, 15.5f)
        arcTo(2.4f, 2.4f, 0f, true, true, 16.99f, 15.5f)
        close()
        moveTo(11.4f, 17.5f)
        verticalLineTo(6.5f)
        lineTo(19.4f, 4.8f)
        verticalLineTo(15.5f)
    }

    val Play: ImageVector = solid("Play") {
        moveTo(7.2f, 4.9f)
        curveTo(7.2f, 4.05f, 8.15f, 3.53f, 8.88f, 3.98f)
        lineTo(19.4f, 10.95f)
        curveTo(20.08f, 11.41f, 20.08f, 12.59f, 19.4f, 13.05f)
        lineTo(8.88f, 20.02f)
        curveTo(8.15f, 20.47f, 7.2f, 19.95f, 7.2f, 19.1f)
        close()
    }

    val Pause: ImageVector = solid("Pause") {
        moveTo(6.2f, 4.6f)
        curveTo(6.2f, 4f, 6.7f, 3.5f, 7.3f, 3.5f)
        horizontalLineTo(9.7f)
        curveTo(10.3f, 3.5f, 10.8f, 4f, 10.8f, 4.6f)
        verticalLineTo(19.4f)
        curveTo(10.8f, 20f, 10.3f, 20.5f, 9.7f, 20.5f)
        horizontalLineTo(7.3f)
        curveTo(6.7f, 20.5f, 6.2f, 20f, 6.2f, 19.4f)
        close()
        moveTo(13.2f, 4.6f)
        curveTo(13.2f, 4f, 13.7f, 3.5f, 14.3f, 3.5f)
        horizontalLineTo(16.7f)
        curveTo(17.3f, 3.5f, 17.8f, 4f, 17.8f, 4.6f)
        verticalLineTo(19.4f)
        curveTo(17.8f, 20f, 17.3f, 20.5f, 16.7f, 20.5f)
        horizontalLineTo(14.3f)
        curveTo(13.7f, 20.5f, 13.2f, 20f, 13.2f, 19.4f)
        close()
    }

    val FastForward: ImageVector = solid("FastForward") {
        moveTo(3.2f, 6.4f)
        curveTo(3.2f, 5.7f, 4f, 5.3f, 4.6f, 5.7f)
        lineTo(12.2f, 11.2f)
        curveTo(12.7f, 11.6f, 12.7f, 12.4f, 12.2f, 12.8f)
        lineTo(4.6f, 18.3f)
        curveTo(4f, 18.7f, 3.2f, 18.3f, 3.2f, 17.6f)
        close()
        moveTo(12.4f, 6.4f)
        curveTo(12.4f, 5.7f, 13.2f, 5.3f, 13.8f, 5.7f)
        lineTo(21.4f, 11.2f)
        curveTo(21.9f, 11.6f, 21.9f, 12.4f, 21.4f, 12.8f)
        lineTo(13.8f, 18.3f)
        curveTo(13.2f, 18.7f, 12.4f, 18.3f, 12.4f, 17.6f)
        close()
    }

    val FastRewind: ImageVector = solid("FastRewind") {
        moveTo(20.8f, 6.4f)
        curveTo(20.8f, 5.7f, 20f, 5.3f, 19.4f, 5.7f)
        lineTo(11.8f, 11.2f)
        curveTo(11.3f, 11.6f, 11.3f, 12.4f, 11.8f, 12.8f)
        lineTo(19.4f, 18.3f)
        curveTo(20f, 18.7f, 20.8f, 18.3f, 20.8f, 17.6f)
        close()
        moveTo(11.6f, 6.4f)
        curveTo(11.6f, 5.7f, 10.8f, 5.3f, 10.2f, 5.7f)
        lineTo(2.6f, 11.2f)
        curveTo(2.1f, 11.6f, 2.1f, 12.4f, 2.6f, 12.8f)
        lineTo(10.2f, 18.3f)
        curveTo(10.8f, 18.7f, 11.6f, 18.3f, 11.6f, 17.6f)
        close()
    }

    val Next: ImageVector = FastForward

    val Prev: ImageVector = FastRewind

    val Shuffle: ImageVector = stroke("Shuffle") {
        moveTo(4f, 7f)
        horizontalLineTo(7.5f)
        lineTo(16.5f, 17f)
        horizontalLineTo(20f)
        moveTo(17.5f, 14.5f)
        lineTo(20f, 17f)
        lineTo(17.5f, 19.5f)
        moveTo(4f, 17f)
        horizontalLineTo(7.5f)
        lineTo(16.5f, 7f)
        horizontalLineTo(20f)
        moveTo(17.5f, 4.5f)
        lineTo(20f, 7f)
        lineTo(17.5f, 9.5f)
    }

    val Repeat: ImageVector = stroke("Repeat") {
        moveTo(17f, 4f)
        lineTo(20f, 7f)
        lineTo(17f, 10f)
        moveTo(4f, 11f)
        verticalLineTo(9f)
        curveTo(4f, 7.9f, 4.9f, 7f, 6f, 7f)
        horizontalLineTo(20f)
        moveTo(7f, 20f)
        lineTo(4f, 17f)
        lineTo(7f, 14f)
        moveTo(20f, 13f)
        verticalLineTo(15f)
        curveTo(20f, 16.1f, 19.1f, 17f, 18f, 17f)
        horizontalLineTo(4f)
    }

    val RepeatOne: ImageVector = stroke("RepeatOne") {
        moveTo(17f, 4f)
        lineTo(20f, 7f)
        lineTo(17f, 10f)
        moveTo(4f, 11f)
        verticalLineTo(9f)
        curveTo(4f, 7.9f, 4.9f, 7f, 6f, 7f)
        horizontalLineTo(20f)
        moveTo(7f, 20f)
        lineTo(4f, 17f)
        lineTo(7f, 14f)
        moveTo(20f, 13f)
        verticalLineTo(15f)
        curveTo(20f, 16.1f, 19.1f, 17f, 18f, 17f)
        horizontalLineTo(4f)
        moveTo(11f, 10.5f)
        lineTo(12.3f, 9.5f)
        verticalLineTo(14.5f)
    }

    val Heart: ImageVector = stroke("Heart") {
        moveTo(12f, 20.2f)
        curveTo(12f, 20.2f, 4f, 15f, 4f, 9.2f)
        curveTo(4f, 6.5f, 6.1f, 4.5f, 8.7f, 4.5f)
        curveTo(10.3f, 4.5f, 11.4f, 5.3f, 12f, 6.4f)
        curveTo(12.6f, 5.3f, 13.7f, 4.5f, 15.3f, 4.5f)
        curveTo(17.9f, 4.5f, 20f, 6.5f, 20f, 9.2f)
        curveTo(20f, 15f, 12f, 20.2f, 12f, 20.2f)
        close()
    }

    val HeartFilled: ImageVector = solid("HeartFilled") {
        moveTo(12f, 20.4f)
        curveTo(12f, 20.4f, 3.6f, 15.1f, 3.6f, 9.2f)
        curveTo(3.6f, 6.3f, 5.9f, 4.1f, 8.7f, 4.1f)
        curveTo(10.4f, 4.1f, 11.5f, 5f, 12f, 6.1f)
        curveTo(12.5f, 5f, 13.6f, 4.1f, 15.3f, 4.1f)
        curveTo(18.1f, 4.1f, 20.4f, 6.3f, 20.4f, 9.2f)
        curveTo(20.4f, 15.1f, 12f, 20.4f, 12f, 20.4f)
        close()
    }

    val Queue: ImageVector = stroke("Queue") {
        moveTo(4f, 6.5f)
        horizontalLineTo(4.2f)
        moveTo(8f, 6.5f)
        horizontalLineTo(20f)
        moveTo(4f, 12f)
        horizontalLineTo(4.2f)
        moveTo(8f, 12f)
        horizontalLineTo(20f)
        moveTo(4f, 17.5f)
        horizontalLineTo(4.2f)
        moveTo(8f, 17.5f)
        horizontalLineTo(20f)
    }

    val Lyrics: ImageVector = stroke("Lyrics") {
        moveTo(5f, 5f)
        horizontalLineTo(19f)
        curveTo(19.9f, 5f, 20.6f, 5.7f, 20.6f, 6.6f)
        verticalLineTo(15.4f)
        curveTo(20.6f, 16.3f, 19.9f, 17f, 19f, 17f)
        horizontalLineTo(10.2f)
        lineTo(5.6f, 20.2f)
        verticalLineTo(17f)
        horizontalLineTo(5f)
        curveTo(4.1f, 17f, 3.4f, 16.3f, 3.4f, 15.4f)
        verticalLineTo(6.6f)
        curveTo(3.4f, 5.7f, 4.1f, 5f, 5f, 5f)
        close()
        moveTo(9.2f, 9.8f)
        verticalLineTo(12.2f)
        moveTo(14.8f, 9.8f)
        verticalLineTo(12.2f)
    }

    val VolumeMute: ImageVector = stroke("VolumeMute", width = 1.9f) {
        moveTo(5f, 9.5f)
        horizontalLineTo(8.2f)
        lineTo(12.8f, 5.8f)
        verticalLineTo(18.2f)
        lineTo(8.2f, 14.5f)
        horizontalLineTo(5f)
        close()
        moveTo(16.2f, 9.8f)
        lineTo(20f, 14.2f)
        moveTo(20f, 9.8f)
        lineTo(16.2f, 14.2f)
    }

    val VolumeHigh: ImageVector = stroke("VolumeHigh", width = 1.9f) {
        moveTo(4.5f, 9.5f)
        horizontalLineTo(7.8f)
        lineTo(12.5f, 5.8f)
        verticalLineTo(18.2f)
        lineTo(7.8f, 14.5f)
        horizontalLineTo(4.5f)
        close()
        moveTo(15.8f, 9.2f)
        arcTo(4f, 4f, 0f, false, true, 15.8f, 14.8f)
        moveTo(18.2f, 6.8f)
        arcTo(7.2f, 7.2f, 0f, false, true, 18.2f, 17.2f)
    }

    val Speaker: ImageVector = stroke("Speaker", width = 1.9f) {
        moveTo(7f, 4f)
        horizontalLineTo(17f)
        curveTo(18.1f, 4f, 19f, 4.9f, 19f, 6f)
        verticalLineTo(18f)
        curveTo(19f, 19.1f, 18.1f, 20f, 17f, 20f)
        horizontalLineTo(7f)
        curveTo(5.9f, 20f, 5f, 19.1f, 5f, 18f)
        verticalLineTo(6f)
        curveTo(5f, 4.9f, 5.9f, 4f, 7f, 4f)
        close()
        moveTo(12f, 11.5f)
        arcTo(3.2f, 3.2f, 0f, true, true, 11.99f, 11.5f)
        close()
        moveTo(12f, 7.6f)
        verticalLineTo(7.8f)
    }

    val Equalizer: ImageVector = stroke("Equalizer") {
        moveTo(6f, 4f)
        verticalLineTo(20f)
        moveTo(12f, 4f)
        verticalLineTo(20f)
        moveTo(18f, 4f)
        verticalLineTo(20f)
        moveTo(4f, 9f)
        horizontalLineTo(8f)
        moveTo(10f, 15f)
        horizontalLineTo(14f)
        moveTo(16f, 10.5f)
        horizontalLineTo(20f)
    }

    val Timer: ImageVector = stroke("Timer") {
        moveTo(9.5f, 3f)
        horizontalLineTo(14.5f)
        moveTo(12f, 6f)
        arcTo(7.5f, 7.5f, 0f, true, true, 11.99f, 6f)
        close()
        moveTo(12f, 10f)
        verticalLineTo(13.8f)
        lineTo(14.6f, 15.3f)
    }

    val Speed: ImageVector = stroke("Speed") {
        moveTo(4.5f, 17.5f)
        arcTo(8.5f, 8.5f, 0f, true, true, 19.5f, 17.5f)
        moveTo(12f, 14f)
        lineTo(16.2f, 9.8f)
    }

    val Download: ImageVector = stroke("Download") {
        moveTo(12f, 4f)
        verticalLineTo(15f)
        moveTo(8f, 11.2f)
        lineTo(12f, 15.2f)
        lineTo(16f, 11.2f)
        moveTo(5f, 19.5f)
        horizontalLineTo(19f)
    }

    val Check: ImageVector = stroke("Check") {
        moveTo(5f, 12.5f)
        lineTo(9.8f, 17.3f)
        lineTo(19f, 7.2f)
    }

    val More: ImageVector = solid("More") {
        moveTo(12f, 4.5f)
        arcTo(1.7f, 1.7f, 0f, true, true, 11.99f, 4.5f)
        close()
        moveTo(12f, 10.3f)
        arcTo(1.7f, 1.7f, 0f, true, true, 11.99f, 10.3f)
        close()
        moveTo(12f, 16.1f)
        arcTo(1.7f, 1.7f, 0f, true, true, 11.99f, 16.1f)
        close()
    }

    val MoreHoriz: ImageVector = solid("MoreHoriz") {
        moveTo(6.2f, 10.3f)
        arcTo(1.7f, 1.7f, 0f, true, true, 6.19f, 10.3f)
        close()
        moveTo(12f, 10.3f)
        arcTo(1.7f, 1.7f, 0f, true, true, 11.99f, 10.3f)
        close()
        moveTo(17.8f, 10.3f)
        arcTo(1.7f, 1.7f, 0f, true, true, 17.79f, 10.3f)
        close()
    }

    val Add: ImageVector = stroke("Add") {
        moveTo(12f, 5f)
        verticalLineTo(19f)
        moveTo(5f, 12f)
        horizontalLineTo(19f)
    }

    val Close: ImageVector = stroke("Close") {
        moveTo(6.5f, 6.5f)
        lineTo(17.5f, 17.5f)
        moveTo(17.5f, 6.5f)
        lineTo(6.5f, 17.5f)
    }

    val Back: ImageVector = stroke("Back") {
        moveTo(19f, 12f)
        horizontalLineTo(5f)
        moveTo(11f, 6f)
        lineTo(5f, 12f)
        lineTo(11f, 18f)
    }

    val ArrowForward: ImageVector = stroke("ArrowForward") {
        moveTo(5f, 12f)
        horizontalLineTo(19f)
        moveTo(13f, 6f)
        lineTo(19f, 12f)
        lineTo(13f, 18f)
    }

    val ArrowUp: ImageVector = stroke("ArrowUp") {
        moveTo(12f, 18.5f)
        verticalLineTo(5.5f)
        moveTo(7f, 10.5f)
        lineTo(12f, 5.5f)
        lineTo(17f, 10.5f)
    }

    val ArrowDown: ImageVector = stroke("ArrowDown") {
        moveTo(12f, 5.5f)
        verticalLineTo(18.5f)
        moveTo(7f, 13.5f)
        lineTo(12f, 18.5f)
        lineTo(17f, 13.5f)
    }

    val ChevronDown: ImageVector = stroke("ChevronDown") {
        moveTo(6f, 9.5f)
        lineTo(12f, 15.5f)
        lineTo(18f, 9.5f)
    }

    val ChevronRight: ImageVector = stroke("ChevronRight") {
        moveTo(9.5f, 6f)
        lineTo(15.5f, 12f)
        lineTo(9.5f, 18f)
    }

    val Info: ImageVector = stroke("Info") {
        moveTo(12f, 3.5f)
        arcTo(8.5f, 8.5f, 0f, true, true, 11.99f, 3.5f)
        close()
        moveTo(12f, 11f)
        verticalLineTo(16f)
        moveTo(12f, 8f)
        verticalLineTo(8.1f)
    }

    val History: ImageVector = stroke("History") {
        moveTo(12f, 3.5f)
        arcTo(8.5f, 8.5f, 0f, true, true, 11.99f, 3.5f)
        close()
        moveTo(12f, 7.8f)
        verticalLineTo(12.4f)
        lineTo(15.2f, 14.3f)
    }

    val Stats: ImageVector = stroke("Stats") {
        moveTo(5.5f, 19f)
        verticalLineTo(13f)
        moveTo(10f, 19f)
        verticalLineTo(8.5f)
        moveTo(14.5f, 19f)
        verticalLineTo(11f)
        moveTo(19f, 19f)
        verticalLineTo(5f)
    }

    val Settings: ImageVector = stroke("Settings", width = 1.85f) {
        moveTo(10.35f, 5.4f)
        lineTo(10.63f, 2.8f)
        lineTo(13.37f, 2.8f)
        lineTo(13.65f, 5.4f)
        lineTo(15.5f, 6.17f)
        lineTo(17.53f, 4.52f)
        lineTo(19.48f, 6.47f)
        lineTo(17.83f, 8.5f)
        lineTo(18.6f, 10.35f)
        lineTo(21.2f, 10.63f)
        lineTo(21.2f, 13.37f)
        lineTo(18.6f, 13.65f)
        lineTo(17.83f, 15.5f)
        lineTo(19.48f, 17.53f)
        lineTo(17.53f, 19.48f)
        lineTo(15.5f, 17.83f)
        lineTo(13.65f, 18.6f)
        lineTo(13.37f, 21.2f)
        lineTo(10.63f, 21.2f)
        lineTo(10.35f, 18.6f)
        lineTo(8.5f, 17.83f)
        lineTo(6.47f, 19.48f)
        lineTo(4.52f, 17.53f)
        lineTo(6.17f, 15.5f)
        lineTo(5.4f, 13.65f)
        lineTo(2.8f, 13.37f)
        lineTo(2.8f, 10.63f)
        lineTo(5.4f, 10.35f)
        lineTo(6.17f, 8.5f)
        lineTo(4.52f, 6.47f)
        lineTo(6.47f, 4.52f)
        lineTo(8.5f, 6.17f)
        close()
        moveTo(12f, 8.9f)
        arcTo(3.1f, 3.1f, 0f, true, true, 11.99f, 8.9f)
        close()
    }

    val Mic: ImageVector = stroke("Mic") {
        moveTo(9.2f, 6.3f)
        arcTo(2.8f, 2.8f, 0f, false, true, 14.8f, 6.3f)
        verticalLineTo(11.5f)
        arcTo(2.8f, 2.8f, 0f, false, true, 9.2f, 11.5f)
        close()
        moveTo(6f, 11.2f)
        arcTo(6f, 6f, 0f, false, false, 18f, 11.2f)
        moveTo(12f, 17.2f)
        verticalLineTo(20.5f)
        moveTo(9f, 20.5f)
        horizontalLineTo(15f)
    }

    val Sync: ImageVector = stroke("Sync") {
        moveTo(20f, 5.5f)
        verticalLineTo(10f)
        horizontalLineTo(15.5f)
        moveTo(19.3f, 9.8f)
        arcTo(7.4f, 7.4f, 0f, false, false, 5.4f, 8.6f)
        moveTo(4f, 18.5f)
        verticalLineTo(14f)
        horizontalLineTo(8.5f)
        moveTo(4.7f, 14.2f)
        arcTo(7.4f, 7.4f, 0f, false, false, 18.6f, 15.4f)
    }

    val Cloud: ImageVector = stroke("Cloud") {
        moveTo(6.8f, 18.2f)
        horizontalLineTo(17.4f)
        arcTo(3.6f, 3.6f, 0f, false, false, 17.9f, 11.1f)
        arcTo(5.3f, 5.3f, 0f, false, false, 7.5f, 9.7f)
        arcTo(4.2f, 4.2f, 0f, false, false, 6.8f, 18.2f)
        close()
    }

    val Export: ImageVector = stroke("Export") {
        moveTo(12f, 15f)
        verticalLineTo(4f)
        moveTo(8f, 8f)
        lineTo(12f, 4f)
        lineTo(16f, 8f)
        moveTo(5f, 13.5f)
        verticalLineTo(18.2f)
        curveTo(5f, 19.2f, 5.8f, 20f, 6.8f, 20f)
        horizontalLineTo(17.2f)
        curveTo(18.2f, 20f, 19f, 19.2f, 19f, 18.2f)
        verticalLineTo(13.5f)
    }

    val Import: ImageVector = stroke("Import") {
        moveTo(12f, 4f)
        verticalLineTo(15f)
        moveTo(8f, 11f)
        lineTo(12f, 15f)
        lineTo(16f, 11f)
        moveTo(5f, 13.5f)
        verticalLineTo(18.2f)
        curveTo(5f, 19.2f, 5.8f, 20f, 6.8f, 20f)
        horizontalLineTo(17.2f)
        curveTo(18.2f, 20f, 19f, 19.2f, 19f, 18.2f)
        verticalLineTo(13.5f)
    }

    val Alert: ImageVector = stroke("Alert") {
        moveTo(12f, 3.5f)
        arcTo(8.5f, 8.5f, 0f, true, true, 11.99f, 3.5f)
        close()
        moveTo(12f, 7.8f)
        verticalLineTo(12.8f)
        moveTo(12f, 16.2f)
        verticalLineTo(16.4f)
    }

    val Folder: ImageVector = stroke("Folder") {
        moveTo(3.5f, 7.2f)
        curveTo(3.5f, 6f, 4.4f, 5f, 5.6f, 5f)
        horizontalLineTo(9.4f)
        lineTo(11.4f, 7.3f)
        horizontalLineTo(18.4f)
        curveTo(19.6f, 7.3f, 20.5f, 8.2f, 20.5f, 9.4f)
        verticalLineTo(17.4f)
        curveTo(20.5f, 18.6f, 19.6f, 19.5f, 18.4f, 19.5f)
        horizontalLineTo(5.6f)
        curveTo(4.4f, 19.5f, 3.5f, 18.6f, 3.5f, 17.4f)
        close()
    }

    val Sun: ImageVector = stroke("Sun") {
        moveTo(12f, 7.5f)
        arcTo(4.5f, 4.5f, 0f, true, true, 11.99f, 7.5f)
        close()
        moveTo(12f, 3f)
        verticalLineTo(4.8f)
        moveTo(12f, 19.2f)
        verticalLineTo(21f)
        moveTo(3f, 12f)
        horizontalLineTo(4.8f)
        moveTo(19.2f, 12f)
        horizontalLineTo(21f)
        moveTo(5.6f, 5.6f)
        lineTo(6.9f, 6.9f)
        moveTo(17.1f, 17.1f)
        lineTo(18.4f, 18.4f)
        moveTo(18.4f, 5.6f)
        lineTo(17.1f, 6.9f)
        moveTo(6.9f, 17.1f)
        lineTo(5.6f, 18.4f)
    }

    val Moon: ImageVector = stroke("Moon") {
        moveTo(19.5f, 14.5f)
        arcTo(7.8f, 7.8f, 0f, true, true, 9.5f, 4.5f)
        arcTo(6.2f, 6.2f, 0f, false, false, 19.5f, 14.5f)
        close()
    }

    val Album: ImageVector = stroke("Album") {
        moveTo(12f, 3.5f)
        arcTo(8.5f, 8.5f, 0f, true, true, 11.99f, 3.5f)
        close()
        moveTo(12f, 9.5f)
        arcTo(2.5f, 2.5f, 0f, true, true, 11.99f, 9.5f)
        close()
    }

    val Artist: ImageVector = stroke("Artist") {
        moveTo(12f, 4.5f)
        arcTo(3.8f, 3.8f, 0f, true, true, 11.99f, 4.5f)
        close()
        moveTo(5f, 19.5f)
        curveTo(5f, 15.8f, 8.1f, 13.5f, 12f, 13.5f)
        curveTo(15.9f, 13.5f, 19f, 15.8f, 19f, 19.5f)
    }

    val Playlist: ImageVector = stroke("Playlist") {
        moveTo(4f, 6.5f)
        horizontalLineTo(15f)
        moveTo(4f, 11.5f)
        horizontalLineTo(15f)
        moveTo(4f, 16.5f)
        horizontalLineTo(11f)
        moveTo(15.5f, 13f)
        lineTo(20f, 15.8f)
        lineTo(15.5f, 18.6f)
        close()
    }

    val Radio: ImageVector = stroke("Radio") {
        moveTo(12f, 10f)
        arcTo(2f, 2f, 0f, true, true, 11.99f, 10f)
        close()
        moveTo(8.5f, 8.5f)
        arcTo(5f, 5f, 0f, false, false, 8.5f, 15.5f)
        moveTo(15.5f, 8.5f)
        arcTo(5f, 5f, 0f, false, true, 15.5f, 15.5f)
        moveTo(5.5f, 5.5f)
        arcTo(9.2f, 9.2f, 0f, false, false, 5.5f, 18.5f)
        moveTo(18.5f, 5.5f)
        arcTo(9.2f, 9.2f, 0f, false, true, 18.5f, 18.5f)
    }

    val Share: ImageVector = stroke("Share") {
        moveTo(14f, 6f)
        lineTo(20f, 11.5f)
        lineTo(14f, 17f)
        verticalLineTo(13.5f)
        curveTo(9f, 13.5f, 5.8f, 15.2f, 4f, 18.5f)
        curveTo(4.8f, 12.8f, 8.2f, 9.2f, 14f, 9.2f)
        close()
    }

    val Trash: ImageVector = stroke("Trash") {
        moveTo(4.5f, 7f)
        horizontalLineTo(19.5f)
        moveTo(9.5f, 7f)
        verticalLineTo(4.8f)
        horizontalLineTo(14.5f)
        verticalLineTo(7f)
        moveTo(6.8f, 7f)
        lineTo(7.6f, 19.5f)
        horizontalLineTo(16.4f)
        lineTo(17.2f, 7f)
    }

    val Drag: ImageVector = stroke("Drag") {
        moveTo(6f, 9.5f)
        horizontalLineTo(18f)
        moveTo(6f, 14.5f)
        horizontalLineTo(18f)
    }

    val Lock: ImageVector = stroke("Lock") {
        moveTo(6.5f, 11f)
        horizontalLineTo(17.5f)
        curveTo(18.3f, 11f, 19f, 11.7f, 19f, 12.5f)
        verticalLineTo(18.5f)
        curveTo(19f, 19.3f, 18.3f, 20f, 17.5f, 20f)
        horizontalLineTo(6.5f)
        curveTo(5.7f, 20f, 5f, 19.3f, 5f, 18.5f)
        verticalLineTo(12.5f)
        curveTo(5f, 11.7f, 5.7f, 11f, 6.5f, 11f)
        close()
        moveTo(8.5f, 11f)
        verticalLineTo(7.8f)
        arcTo(3.5f, 3.5f, 0f, false, true, 15.5f, 7.8f)
        verticalLineTo(11f)
    }

    val Unlock: ImageVector = stroke("Unlock") {
        moveTo(6.5f, 11f)
        horizontalLineTo(17.5f)
        curveTo(18.3f, 11f, 19f, 11.7f, 19f, 12.5f)
        verticalLineTo(18.5f)
        curveTo(19f, 19.3f, 18.3f, 20f, 17.5f, 20f)
        horizontalLineTo(6.5f)
        curveTo(5.7f, 20f, 5f, 19.3f, 5f, 18.5f)
        verticalLineTo(12.5f)
        curveTo(5f, 11.7f, 5.7f, 11f, 6.5f, 11f)
        close()
        moveTo(8.5f, 11f)
        verticalLineTo(7.8f)
        arcTo(3.5f, 3.5f, 0f, false, true, 15.2f, 6.4f)
    }

    val Edit: ImageVector = stroke("Edit") {
        moveTo(15.5f, 4.8f)
        lineTo(19.2f, 8.5f)
        lineTo(9.2f, 18.5f)
        horizontalLineTo(5.5f)
        verticalLineTo(14.8f)
        close()
    }

    val Palette: ImageVector = stroke("Palette") {
        moveTo(12f, 3.5f)
        curveTo(7.2f, 3.5f, 3.5f, 7.1f, 3.5f, 11.7f)
        curveTo(3.5f, 16.2f, 7.2f, 19.8f, 11.5f, 19.8f)
        curveTo(13.1f, 19.8f, 14.2f, 18.7f, 14.2f, 17.3f)
        curveTo(14.2f, 16.5f, 13.8f, 15.9f, 13.8f, 15.2f)
        curveTo(13.8f, 14.3f, 14.6f, 13.6f, 15.6f, 13.6f)
        horizontalLineTo(17.2f)
        curveTo(19.1f, 13.6f, 20.5f, 12.1f, 20.5f, 10.2f)
        curveTo(20.5f, 6.4f, 16.7f, 3.5f, 12f, 3.5f)
        close()
        moveTo(8f, 9.5f)
        verticalLineTo(9.7f)
        moveTo(11.5f, 7.5f)
        verticalLineTo(7.7f)
        moveTo(15.2f, 8.8f)
        verticalLineTo(9.0f)
    }

    val Storage: ImageVector = stroke("Storage") {
        moveTo(5f, 6f)
        horizontalLineTo(19f)
        verticalLineTo(10.5f)
        horizontalLineTo(5f)
        close()
        moveTo(5f, 13.5f)
        horizontalLineTo(19f)
        verticalLineTo(18f)
        horizontalLineTo(5f)
        close()
        moveTo(8f, 8.2f)
        horizontalLineTo(8.3f)
        moveTo(8f, 15.7f)
        horizontalLineTo(8.3f)
    }

    val Backup: ImageVector = stroke("Backup") {
        moveTo(7f, 18f)
        horizontalLineTo(17.2f)
        arcTo(3.5f, 3.5f, 0f, false, false, 17.8f, 11.1f)
        arcTo(5.2f, 5.2f, 0f, false, false, 7.6f, 9.8f)
        arcTo(4.1f, 4.1f, 0f, false, false, 7f, 18f)
        close()
        moveTo(12f, 15.5f)
        verticalLineTo(10.5f)
        moveTo(9.8f, 12.6f)
        lineTo(12f, 10.4f)
        lineTo(14.2f, 12.6f)
    }

    val Language: ImageVector = stroke("Language") {
        moveTo(4f, 6.5f)
        horizontalLineTo(13f)
        moveTo(8.5f, 4.5f)
        verticalLineTo(6.5f)
        moveTo(6f, 6.5f)
        curveTo(6.5f, 10.8f, 8.8f, 13.8f, 12.2f, 15.5f)
        moveTo(11f, 9f)
        curveTo(10f, 12.2f, 7.8f, 14.5f, 4.8f, 15.8f)
        moveTo(13.2f, 19.5f)
        lineTo(16.6f, 10.8f)
        lineTo(20f, 19.5f)
        moveTo(14.4f, 16.8f)
        horizontalLineTo(18.8f)
    }

    val User: ImageVector = stroke("User") {
        moveTo(12f, 3.5f)
        arcTo(8.5f, 8.5f, 0f, true, true, 11.99f, 3.5f)
        close()
        moveTo(12f, 7.5f)
        arcTo(2.7f, 2.7f, 0f, true, true, 11.99f, 7.5f)
        close()
        moveTo(7.2f, 18.2f)
        curveTo(8.1f, 15.8f, 9.9f, 14.6f, 12f, 14.6f)
        curveTo(14.1f, 14.6f, 15.9f, 15.8f, 16.8f, 18.2f)
    }

    val Refresh: ImageVector = stroke("Refresh") {
        moveTo(19.5f, 5.5f)
        verticalLineTo(10f)
        horizontalLineTo(15f)
        moveTo(19f, 9.8f)
        arcTo(7.5f, 7.5f, 0f, true, false, 18.5f, 16.2f)
    }

    val Bell: ImageVector = stroke("Bell") {
        moveTo(18f, 9.5f)
        arcTo(6f, 6f, 0f, false, false, 6f, 9.5f)
        curveTo(6f, 14f, 4.2f, 15.8f, 4.2f, 16.8f)
        horizontalLineTo(19.8f)
        curveTo(19.8f, 15.8f, 18f, 14f, 18f, 9.5f)
        close()
        moveTo(10f, 17.2f)
        arcTo(2.2f, 2.2f, 0f, false, false, 14f, 17.2f)
    }

    val Update: ImageVector = stroke("Update") {
        moveTo(12f, 3.8f)
        arcTo(8.2f, 8.2f, 0f, true, true, 11.99f, 3.8f)
        close()
        moveTo(12f, 8.2f)
        verticalLineTo(15.2f)
        moveTo(9.2f, 12.6f)
        lineTo(12f, 15.5f)
        lineTo(14.8f, 12.6f)
    }

    val Notes: ImageVector = stroke("Notes") {
        moveTo(6f, 4.2f)
        horizontalLineTo(15.2f)
        lineTo(19f, 8f)
        verticalLineTo(19.8f)
        horizontalLineTo(6f)
        close()
        moveTo(14.8f, 4.2f)
        verticalLineTo(8.4f)
        horizontalLineTo(19f)
        moveTo(9f, 12f)
        horizontalLineTo(16f)
        moveTo(9f, 15.5f)
        horizontalLineTo(14f)
    }

    val External: ImageVector = stroke("External") {
        moveTo(14f, 4.5f)
        horizontalLineTo(19.5f)
        verticalLineTo(10f)
        moveTo(19.2f, 4.8f)
        lineTo(10.5f, 13.5f)
        moveTo(19f, 13.8f)
        verticalLineTo(18.5f)
        curveTo(19f, 19.1f, 18.6f, 19.5f, 18f, 19.5f)
        horizontalLineTo(5.5f)
        curveTo(4.9f, 19.5f, 4.5f, 19.1f, 4.5f, 18.5f)
        verticalLineTo(6f)
        curveTo(4.5f, 5.4f, 4.9f, 5f, 5.5f, 5f)
        horizontalLineTo(10.2f)
    }
}
