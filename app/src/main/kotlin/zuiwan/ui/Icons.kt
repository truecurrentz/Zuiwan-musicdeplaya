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
    private fun stroke(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
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

    val Home: ImageVector = stroke("Home") {
        moveTo(3.5f, 10.5f)
        lineTo(12f, 3.5f)
        lineTo(20.5f, 10.5f)
        moveTo(5.5f, 9.2f)
        verticalLineTo(19.5f)
        curveTo(5.5f, 20.05f, 5.95f, 20.5f, 6.5f, 20.5f)
        horizontalLineTo(17.5f)
        curveTo(18.05f, 20.5f, 18.5f, 20.05f, 18.5f, 19.5f)
        verticalLineTo(9.2f)
        moveTo(9.8f, 20.5f)
        verticalLineTo(14.2f)
        horizontalLineTo(14.2f)
        verticalLineTo(20.5f)
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

    val Library: ImageVector = stroke("Library") {
        moveTo(4.5f, 5f)
        verticalLineTo(19f)
        moveTo(9.5f, 5f)
        verticalLineTo(19f)
        moveTo(14.2f, 5.5f)
        lineTo(19.5f, 19f)
    }

    val Play: ImageVector = solid("Play") {
        moveTo(7.5f, 5.1f)
        curveTo(7.5f, 4.25f, 8.42f, 3.73f, 9.15f, 4.17f)
        lineTo(19.3f, 10.97f)
        curveTo(19.98f, 11.43f, 19.98f, 12.57f, 19.3f, 13.03f)
        lineTo(9.15f, 19.83f)
        curveTo(8.42f, 20.27f, 7.5f, 19.75f, 7.5f, 18.9f)
        close()
    }

    val Pause: ImageVector = solid("Pause") {
        moveTo(6.5f, 5f)
        horizontalLineTo(10.2f)
        verticalLineTo(19f)
        horizontalLineTo(6.5f)
        close()
        moveTo(13.8f, 5f)
        horizontalLineTo(17.5f)
        verticalLineTo(19f)
        horizontalLineTo(13.8f)
        close()
    }

    val Next: ImageVector = solid("Next") {
        moveTo(5.5f, 5.4f)
        lineTo(14.8f, 12f)
        lineTo(5.5f, 18.6f)
        close()
        moveTo(16.5f, 5.5f)
        horizontalLineTo(18.8f)
        verticalLineTo(18.5f)
        horizontalLineTo(16.5f)
        close()
    }

    val Prev: ImageVector = solid("Prev") {
        moveTo(18.5f, 5.4f)
        lineTo(9.2f, 12f)
        lineTo(18.5f, 18.6f)
        close()
        moveTo(5.2f, 5.5f)
        horizontalLineTo(7.5f)
        verticalLineTo(18.5f)
        horizontalLineTo(5.2f)
        close()
    }

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
        moveTo(4f, 7f)
        horizontalLineTo(15f)
        moveTo(4f, 12f)
        horizontalLineTo(15f)
        moveTo(4f, 17f)
        horizontalLineTo(11f)
        moveTo(17f, 12f)
        verticalLineTo(18.5f)
        moveTo(17f, 12f)
        lineTo(20f, 13.2f)
        moveTo(15.5f, 18.5f)
        arcTo(1.5f, 1.5f, 0f, true, true, 15.49f, 18.5f)
    }

    val Lyrics: ImageVector = stroke("Lyrics") {
        moveTo(5f, 5.5f)
        horizontalLineTo(19f)
        curveTo(19.8f, 5.5f, 20.5f, 6.2f, 20.5f, 7f)
        verticalLineTo(15.5f)
        curveTo(20.5f, 16.3f, 19.8f, 17f, 19f, 17f)
        horizontalLineTo(10f)
        lineTo(5.5f, 20f)
        verticalLineTo(17f)
        horizontalLineTo(5f)
        curveTo(4.2f, 17f, 3.5f, 16.3f, 3.5f, 15.5f)
        verticalLineTo(7f)
        curveTo(3.5f, 6.2f, 4.2f, 5.5f, 5f, 5.5f)
        close()
        moveTo(8f, 9.5f)
        horizontalLineTo(16f)
        moveTo(8f, 13f)
        horizontalLineTo(13.5f)
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

    val Add: ImageVector = stroke("Add") {
        moveTo(12f, 5f)
        verticalLineTo(19f)
        moveTo(5f, 12f)
        horizontalLineTo(19f)
    }

    val Close: ImageVector = stroke("Close") {
        moveTo(6f, 6f)
        lineTo(18f, 18f)
        moveTo(18f, 6f)
        lineTo(6f, 18f)
    }

    val Back: ImageVector = stroke("Back") {
        moveTo(15f, 5f)
        lineTo(8f, 12f)
        lineTo(15f, 19f)
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
        moveTo(4.5f, 8.5f)
        verticalLineTo(4.5f)
        horizontalLineTo(8.5f)
        moveTo(5.2f, 7.8f)
        arcTo(8.2f, 8.2f, 0f, true, true, 4.3f, 13.5f)
        moveTo(12f, 8f)
        verticalLineTo(12.4f)
        lineTo(15f, 14.2f)
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

    val Settings: ImageVector = stroke("Settings") {
        moveTo(12f, 8.8f)
        arcTo(3.2f, 3.2f, 0f, true, true, 11.99f, 8.8f)
        close()
        moveTo(12f, 3.5f)
        verticalLineTo(5.5f)
        moveTo(12f, 18.5f)
        verticalLineTo(20.5f)
        moveTo(3.5f, 12f)
        horizontalLineTo(5.5f)
        moveTo(18.5f, 12f)
        horizontalLineTo(20.5f)
        moveTo(6f, 6f)
        lineTo(7.5f, 7.5f)
        moveTo(16.5f, 16.5f)
        lineTo(18f, 18f)
        moveTo(18f, 6f)
        lineTo(16.5f, 7.5f)
        moveTo(7.5f, 16.5f)
        lineTo(6f, 18f)
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
        moveTo(16f, 5f)
        arcTo(2.2f, 2.2f, 0f, true, true, 15.99f, 5f)
        close()
        moveTo(6f, 9.8f)
        arcTo(2.2f, 2.2f, 0f, true, true, 5.99f, 9.8f)
        close()
        moveTo(16f, 14.6f)
        arcTo(2.2f, 2.2f, 0f, true, true, 15.99f, 14.6f)
        close()
        moveTo(8f, 11f)
        lineTo(14f, 8f)
        moveTo(8f, 13f)
        lineTo(14f, 16f)
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
}
