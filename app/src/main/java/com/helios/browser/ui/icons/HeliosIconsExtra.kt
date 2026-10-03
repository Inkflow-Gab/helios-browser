package com.helios.browser.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Additional hand-drawn symbols for the first-run flow and browser menu.
 *
 * Same rules as [HeliosIcons]: 24dp viewport, paths filled white and tinted at the call site.
 * Zero emoji in the UI, so every glyph has to exist here as a vector.
 */
object HeliosGlyphs {

    val Bookmark: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosBookmark",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 3f)
                horizontalLineTo(18f)
                curveTo(19.1f, 3f, 20f, 3.9f, 20f, 5f)
                verticalLineTo(21f)
                lineTo(12f, 18f)
                lineTo(4f, 21f)
                verticalLineTo(5f)
                curveTo(4f, 3.9f, 4.9f, 3f, 6f, 3f)
                close()
            }
        }.build()
    }

    val BookmarkFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosBookmarkFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 3f)
                horizontalLineTo(18f)
                curveTo(19.1f, 3f, 20f, 3.9f, 20f, 5f)
                verticalLineTo(21f)
                lineTo(12f, 18f)
                lineTo(4f, 21f)
                verticalLineTo(5f)
                curveTo(4f, 3.9f, 4.9f, 3f, 6f, 3f)
                close()
            }
        }.build()
    }

    val History: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosHistory",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 3f)
                curveTo(7f, 3f, 3f, 7f, 3f, 12f)
                curveTo(3f, 17f, 7f, 21f, 12f, 21f)
                curveTo(17f, 21f, 21f, 17f, 21f, 12f)
                curveTo(21f, 7f, 17f, 3f, 12f, 3f)
                close()
                moveTo(12f, 5f)
                curveTo(15.9f, 5f, 19f, 8.1f, 19f, 12f)
                curveTo(19f, 15.9f, 15.9f, 19f, 12f, 19f)
                curveTo(8.1f, 19f, 5f, 15.9f, 5f, 12f)
                curveTo(5f, 8.1f, 8.1f, 5f, 12f, 5f)
                close()
                moveTo(11f, 8f)
                horizontalLineTo(13f)
                verticalLineTo(12.6f)
                lineTo(16.5f, 14.8f)
                lineTo(15.2f, 16.6f)
                lineTo(11f, 14f)
                close()
            }
        }.build()
    }

    val Download: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosDownload",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(11f, 3f)
                horizontalLineTo(13f)
                verticalLineTo(10.6f)
                lineTo(17.4f, 15f)
                horizontalLineTo(14f)
                verticalLineTo(17f)
                horizontalLineTo(10f)
                verticalLineTo(15f)
                horizontalLineTo(6.6f)
                lineTo(11f, 10.6f)
                close()
                moveTo(5f, 19f)
                horizontalLineTo(19f)
                curveTo(19.6f, 19f, 20f, 19.4f, 20f, 20f)
                verticalLineTo(21f)
                curveTo(20f, 21.6f, 19.6f, 22f, 19f, 22f)
                horizontalLineTo(5f)
                curveTo(4.4f, 22f, 4f, 21.6f, 4f, 21f)
                verticalLineTo(20f)
                curveTo(4f, 19.4f, 4.4f, 19f, 5f, 19f)
                close()
            }
        }.build()
    }

    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosCheck",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(9.6f, 16.2f)
                lineTo(5.4f, 12f)
                lineTo(4f, 13.4f)
                lineTo(9.6f, 19f)
                lineTo(20f, 8.6f)
                lineTo(18.6f, 7.2f)
                close()
            }
        }.build()
    }

    val Globe: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosGlobe",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 2f)
                curveTo(6.5f, 2f, 2f, 6.5f, 2f, 12f)
                curveTo(2f, 17.5f, 6.5f, 22f, 12f, 22f)
                curveTo(17.5f, 22f, 22f, 17.5f, 22f, 12f)
                curveTo(22f, 6.5f, 17.5f, 2f, 12f, 2f)
                close()
                moveTo(12f, 4f)
                curveTo(13.8f, 4f, 15.6f, 6.6f, 16.3f, 10f)
                horizontalLineTo(7.7f)
                curveTo(8.4f, 6.6f, 10.2f, 4f, 12f, 4f)
                close()
                moveTo(4.3f, 12f)
                horizontalLineTo(8f)
                curveTo(8.1f, 14.4f, 8.7f, 16.6f, 9.8f, 18.3f)
                curveTo(6.6f, 17.5f, 4.4f, 15f, 4.3f, 12f)
                close()
                moveTo(12f, 20f)
                curveTo(10.2f, 20f, 8.4f, 17.4f, 7.7f, 14f)
                horizontalLineTo(16.3f)
                curveTo(15.6f, 17.4f, 13.8f, 20f, 12f, 20f)
                close()
                moveTo(14.2f, 18.3f)
                curveTo(15.3f, 16.6f, 15.9f, 14.4f, 16f, 12f)
                horizontalLineTo(19.7f)
                curveTo(19.6f, 15f, 17.4f, 17.5f, 14.2f, 18.3f)
                close()
            }
        }.build()
    }

    val Radiance: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosRadiance",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 7f)
                curveTo(9.2f, 7f, 7f, 9.2f, 7f, 12f)
                curveTo(7f, 14.8f, 9.2f, 17f, 12f, 17f)
                curveTo(14.8f, 17f, 17f, 14.8f, 17f, 12f)
                curveTo(17f, 9.2f, 14.8f, 7f, 12f, 7f)
                close()
                moveTo(11f, 1f)
                horizontalLineTo(13f)
                verticalLineTo(4f)
                lineTo(11f, 4f)
                close()
                moveTo(11f, 20f)
                horizontalLineTo(13f)
                verticalLineTo(23f)
                lineTo(11f, 23f)
                close()
                moveTo(1f, 11f)
                verticalLineTo(13f)
                horizontalLineTo(4f)
                lineTo(1f, 11f)
                close()
                moveTo(20f, 11f)
                verticalLineTo(13f)
                horizontalLineTo(23f)
                lineTo(20f, 11f)
                close()
            }
        }.build()
    }

    val Camera: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosCamera",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(9f, 3f)
                lineTo(8.2f, 5f)
                horizontalLineTo(4.5f)
                curveTo(3.4f, 5f, 2.5f, 5.9f, 2.5f, 7f)
                verticalLineTo(19f)
                curveTo(2.5f, 20.1f, 3.4f, 21f, 4.5f, 21f)
                horizontalLineTo(19.5f)
                curveTo(20.6f, 21f, 21.5f, 20.1f, 21.5f, 19f)
                verticalLineTo(7f)
                curveTo(21.5f, 5.9f, 20.6f, 5f, 19.5f, 5f)
                horizontalLineTo(15.8f)
                lineTo(15f, 3f)
                close()
                moveTo(12f, 9f)
                curveTo(14.2f, 9f, 16f, 10.8f, 16f, 13f)
                curveTo(16f, 15.2f, 14.2f, 17f, 12f, 17f)
                curveTo(9.8f, 17f, 8f, 15.2f, 8f, 13f)
                curveTo(8f, 10.8f, 9.8f, 9f, 12f, 9f)
                close()
            }
        }.build()
    }

    val Microphone: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosMicrophone",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 2f)
                curveTo(10.3f, 2f, 9f, 3.3f, 9f, 5f)
                verticalLineTo(12f)
                curveTo(13.7f, 12f, 15f, 10.7f, 15f, 9f)
                verticalLineTo(5f)
                curveTo(15f, 3.3f, 13.7f, 2f, 12f, 2f)
                close()
                moveTo(5f, 10f)
                horizontalLineTo(7f)
                verticalLineTo(11f)
                curveTo(7f, 14.3f, 9.3f, 17f, 12f, 17f)
                curveTo(14.7f, 17f, 17f, 14.3f, 17f, 11f)
                horizontalLineTo(19f)
                verticalLineTo(11f)
                curveTo(19f, 15.1f, 16.1f, 18.5f, 12.2f, 19f)
                verticalLineTo(22f)
                horizontalLineTo(20f)
                verticalLineTo(19f)
                horizontalLineTo(12f)
                curveTo(7.6f, 19f, 5f, 15.4f, 5f, 10f)
                close()
            }
        }.build()
    }

    val Location: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosLocation",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 2f)
                curveTo(8.1f, 2f, 5f, 5.1f, 5f, 9f)
                curveTo(5f, 14.3f, 12f, 22f, 12f, 22f)
                curveTo(12f, 22f, 19f, 14.3f, 19f, 9f)
                curveTo(19f, 5.1f, 15.9f, 2f, 12f, 2f)
                close()
                moveTo(12f, 6.5f)
                curveTo(13.9f, 6.5f, 15.5f, 8.1f, 15.5f, 10f)
                curveTo(15.5f, 11.9f, 13.9f, 13.5f, 12f, 13.5f)
                curveTo(10.1f, 13.5f, 8.5f, 11.9f, 8.5f, 10f)
                curveTo(8.5f, 8.1f, 10.1f, 6.5f, 12f, 6.5f)
                close()
            }
        }.build()
    }

    val Bell: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosBell",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 22f)
                curveTo(13.1f, 22f, 14f, 21.1f, 14f, 20f)
                horizontalLineTo(10f)
                curveTo(10f, 21.1f, 10.9f, 22f, 12f, 22f)
                close()
                moveTo(18f, 16f)
                verticalLineTo(11f)
                curveTo(18f, 7.9f, 15.4f, 5.5f, 12.3f, 5.1f)
                verticalLineTo(4f)
                curveTo(7.4f, 4.5f, 10f, 2.5f, 10f, 0f)
                horizontalLineTo(14f)
                curveTo(14f, 2.5f, 16.6f, 4.5f, 20f, 4f)
                verticalLineTo(5.1f)
                curveTo(16.6f, 5.5f, 14f, 7.9f, 14f, 11f)
                verticalLineTo(16f)
                horizontalLineTo(20f)
                verticalLineTo(18f)
                close()
            }
        }.build()
    }

    val Info: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosInfo",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 2f)
                curveTo(6.5f, 2f, 2f, 6.5f, 2f, 12f)
                curveTo(2f, 17.5f, 6.5f, 22f, 12f, 22f)
                curveTo(17.5f, 22f, 22f, 17.5f, 22f, 12f)
                curveTo(22f, 6.5f, 17.5f, 2f, 12f, 2f)
                close()
                moveTo(11f, 10f)
                horizontalLineTo(13f)
                verticalLineTo(17f)
                horizontalLineTo(11f)
                close()
                moveTo(11f, 7f)
                horizontalLineTo(13f)
                verticalLineTo(9f)
                horizontalLineTo(11f)
                close()
            }
        }.build()
    }

    val Eye: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosEye",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 4.5f)
                curveTo(7f, 4.5f, 2.7f, 7.6f, 1f, 12f)
                curveTo(2.7f, 16.4f, 7f, 19.5f, 12f, 19.5f)
                curveTo(17f, 19.5f, 21.3f, 16.4f, 23f, 12f)
                curveTo(21.3f, 7.6f, 17f, 4.5f, 12f, 4.5f)
                close()
                moveTo(12f, 17f)
                curveTo(9.2f, 17f, 7f, 14.8f, 7f, 12f)
                curveTo(7f, 9.2f, 9.2f, 7f, 12f, 7f)
                curveTo(14.8f, 7f, 17f, 9.2f, 17f, 12f)
                curveTo(17f, 14.8f, 14.8f, 17f, 12f, 17f)
                close()
            }
        }.build()
    }
}