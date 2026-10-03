package com.helios.browser.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object HeliosIcons {

    val Shield: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosShield",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White)
            ) {
                moveTo(12f, 2f)
                lineTo(4f, 5.5f)
                verticalLineTo(11.5f)
                curveTo(4f, 16.5f, 7.5f, 21.1f, 12f, 22.3f)
                curveTo(16.5f, 21.1f, 20f, 16.5f, 20f, 11.5f)
                verticalLineTo(5.5f)
                lineTo(12f, 2f)
                close()
                moveTo(12f, 4.3f)
                lineTo(18f, 7f)
                verticalLineTo(11.5f)
                curveTo(18f, 15.3f, 15.4f, 18.9f, 12f, 20f)
                curveTo(8.6f, 18.9f, 6f, 15.3f, 6f, 11.5f)
                verticalLineTo(7f)
                lineTo(12f, 4.3f)
                close()
                moveTo(10.5f, 15f)
                lineTo(7.5f, 12f)
                lineTo(8.9f, 10.6f)
                lineTo(10.5f, 12.2f)
                lineTo(15.1f, 7.6f)
                lineTo(16.5f, 9f)
                lineTo(10.5f, 15f)
                close()
            }
        }.build()
    }

    val Tabs: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosTabs",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(3f, 5f)
                curveTo(3f, 3.9f, 3.9f, 3f, 5f, 3f)
                horizontalLineTo(15f)
                curveTo(16.1f, 3f, 17f, 3.9f, 17f, 5f)
                verticalLineTo(15f)
                curveTo(17f, 16.1f, 16.1f, 17f, 15f, 17f)
                horizontalLineTo(5f)
                curveTo(3.9f, 17f, 3f, 16.1f, 3f, 15f)
                verticalLineTo(5f)
                close()
                moveTo(5f, 5f)
                verticalLineTo(15f)
                horizontalLineTo(15f)
                verticalLineTo(5f)
                horizontalLineTo(5f)
                close()
                moveTo(19f, 7f)
                horizontalLineTo(17f)
                verticalLineTo(9f)
                horizontalLineTo(19f)
                verticalLineTo(19f)
                horizontalLineTo(9f)
                verticalLineTo(17f)
                horizontalLineTo(7f)
                verticalLineTo(19f)
                curveTo(7f, 20.1f, 7.9f, 21f, 9f, 21f)
                horizontalLineTo(19f)
                curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
                verticalLineTo(9f)
                curveTo(21f, 7.9f, 20.1f, 7f, 19f, 7f)
                close()
            }
        }.build()
    }

    val Lock: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosLock",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(18f, 8f)
                horizontalLineTo(17f)
                verticalLineTo(6f)
                curveTo(17f, 3.2f, 14.8f, 1f, 12f, 1f)
                curveTo(9.2f, 1f, 7f, 3.2f, 7f, 6f)
                verticalLineTo(8f)
                horizontalLineTo(6f)
                curveTo(4.9f, 8f, 4f, 8.9f, 4f, 10f)
                verticalLineTo(20f)
                curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
                horizontalLineTo(18f)
                curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
                verticalLineTo(10f)
                curveTo(20f, 8.9f, 19.1f, 8f, 18f, 8f)
                close()
                moveTo(9f, 6f)
                curveTo(9f, 4.3f, 10.3f, 3f, 12f, 3f)
                curveTo(13.7f, 3f, 15f, 4.3f, 15f, 6f)
                verticalLineTo(8f)
                horizontalLineTo(9f)
                verticalLineTo(6f)
                close()
                moveTo(18f, 20f)
                horizontalLineTo(6f)
                verticalLineTo(10f)
                horizontalLineTo(18f)
                verticalLineTo(20f)
                close()
            }
        }.build()
    }

    val Refresh: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosRefresh",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(17.65f, 6.35f)
                curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
                curveTo(7.58f, 4f, 4.01f, 7.58f, 4.01f, 12f)
                curveTo(4.01f, 16.42f, 7.58f, 20f, 12f, 20f)
                curveTo(15.73f, 20f, 18.84f, 17.45f, 19.73f, 14f)
                horizontalLineTo(17.65f)
                curveTo(16.83f, 16.33f, 14.61f, 18f, 12f, 18f)
                curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
                curveTo(6f, 8.69f, 8.69f, 6f, 12f, 6f)
                curveTo(13.66f, 6f, 15.14f, 6.69f, 16.22f, 7.78f)
                lineTo(13f, 11f)
                horizontalLineTo(20f)
                verticalLineTo(4f)
                lineTo(17.65f, 6.35f)
                close()
            }
        }.build()
    }

    val Plus: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosPlus",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(19f, 13f)
                horizontalLineTo(13f)
                verticalLineTo(19f)
                horizontalLineTo(11f)
                verticalLineTo(13f)
                horizontalLineTo(5f)
                verticalLineTo(11f)
                horizontalLineTo(11f)
                verticalLineTo(5f)
                horizontalLineTo(13f)
                verticalLineTo(11f)
                horizontalLineTo(19f)
                verticalLineTo(13f)
                close()
            }
        }.build()
    }

    val Close: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosClose",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(19f, 6.41f)
                lineTo(17.59f, 5f)
                lineTo(12f, 10.59f)
                lineTo(6.41f, 5f)
                lineTo(5f, 6.41f)
                lineTo(10.59f, 12f)
                lineTo(5f, 17.59f)
                lineTo(6.41f, 19f)
                lineTo(12f, 13.41f)
                lineTo(17.59f, 19f)
                lineTo(19f, 17.59f)
                lineTo(13.41f, 12f)
                close()
            }
        }.build()
    }

    val ArrowBack: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosArrowBack",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(15.41f, 7.41f)
                lineTo(14f, 6f)
                lineTo(8f, 12f)
                lineTo(14f, 18f)
                lineTo(15.41f, 16.59f)
                lineTo(10.83f, 12f)
                close()
            }
        }.build()
    }

    val ArrowForward: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosArrowForward",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(8.59f, 7.41f)
                lineTo(10f, 6f)
                lineTo(16f, 12f)
                lineTo(10f, 18f)
                lineTo(8.59f, 16.59f)
                lineTo(13.17f, 12f)
                close()
            }
        }.build()
    }

    val More: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosMore",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 10f)
                curveTo(4.9f, 10f, 4f, 10.9f, 4f, 12f)
                curveTo(4f, 13.1f, 4.9f, 14f, 6f, 14f)
                curveTo(7.1f, 14f, 8f, 13.1f, 8f, 12f)
                curveTo(8f, 10.9f, 7.1f, 10f, 6f, 10f)
                close()
                moveTo(12f, 10f)
                curveTo(10.9f, 10f, 10f, 10.9f, 10f, 12f)
                curveTo(10f, 13.1f, 10.9f, 14f, 12f, 14f)
                curveTo(13.1f, 14f, 14f, 13.1f, 14f, 12f)
                curveTo(14f, 10.9f, 13.1f, 10f, 12f, 10f)
                close()
                moveTo(18f, 10f)
                curveTo(16.9f, 10f, 16f, 10.9f, 16f, 12f)
                curveTo(16f, 13.1f, 16.9f, 14f, 18f, 14f)
                curveTo(19.1f, 14f, 20f, 13.1f, 20f, 12f)
                curveTo(20f, 10.9f, 19.1f, 10f, 18f, 10f)
                close()
            }
        }.build()
    }

    val Search: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosSearch",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(15.5f, 14f)
                horizontalLineTo(14.71f)
                lineTo(14.43f, 13.73f)
                curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f)
                curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f)
                curveTo(5.91f, 3f, 3f, 5.91f, 3f, 9.5f)
                curveTo(3f, 13.09f, 5.91f, 16f, 9.5f, 16f)
                curveTo(11.11f, 16f, 12.59f, 15.41f, 13.73f, 14.43f)
                lineTo(14f, 14.71f)
                verticalLineTo(15.5f)
                lineTo(19f, 20.49f)
                lineTo(20.49f, 19f)
                lineTo(15.5f, 14f)
                close()
                moveTo(9.5f, 14f)
                curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
                curveTo(5f, 7.01f, 7.01f, 5f, 9.5f, 5f)
                curveTo(11.99f, 5f, 14f, 7.01f, 14f, 9.5f)
                curveTo(14f, 11.99f, 11.99f, 14f, 9.5f, 14f)
                close()
            }
        }.build()
    }

    val Sparkle: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosSparkle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 2f)
                curveTo(12f, 7.52f, 7.52f, 12f, 2f, 12f)
                curveTo(7.52f, 12f, 12f, 16.48f, 12f, 22f)
                curveTo(12f, 16.48f, 16.48f, 12f, 22f, 12f)
                curveTo(16.48f, 12f, 12f, 7.52f, 12f, 2f)
                close()
            }
        }.build()
    }

    val Incognito: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosIncognito",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 3.5f)
                curveTo(7.8f, 3.5f, 4.3f, 5.7f, 3f, 8.5f)
                horizontalLineTo(21f)
                curveTo(19.7f, 5.7f, 16.2f, 3.5f, 12f, 3.5f)
                close()
                moveTo(2f, 10f)
                verticalLineTo(11f)
                horizontalLineTo(22f)
                verticalLineTo(10f)
                horizontalLineTo(2f)
                close()
                moveTo(7.5f, 13f)
                curveTo(5.6f, 13f, 4f, 14.6f, 4f, 16.5f)
                curveTo(4f, 18.4f, 5.6f, 20f, 7.5f, 20f)
                curveTo(9.4f, 20f, 11f, 18.4f, 11f, 16.5f)
                curveTo(11f, 14.6f, 9.4f, 13f, 7.5f, 13f)
                close()
                moveTo(16.5f, 13f)
                curveTo(14.6f, 13f, 13f, 14.6f, 13f, 16.5f)
                curveTo(13f, 18.4f, 14.6f, 20f, 16.5f, 20f)
                curveTo(18.4f, 20f, 20f, 18.4f, 20f, 16.5f)
                curveTo(20f, 14.6f, 18.4f, 13f, 16.5f, 13f)
                close()
            }
        }.build()
    }

    val Desktop: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosDesktop",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(21f, 2f)
                horizontalLineTo(3f)
                curveTo(1.9f, 2f, 1f, 2.9f, 1f, 4f)
                verticalLineTo(16f)
                curveTo(1f, 17.1f, 1.9f, 18f, 3f, 18f)
                horizontalLineTo(10f)
                verticalLineTo(20f)
                horizontalLineTo(8f)
                verticalLineTo(22f)
                horizontalLineTo(16f)
                verticalLineTo(20f)
                horizontalLineTo(14f)
                verticalLineTo(18f)
                horizontalLineTo(21f)
                curveTo(22.1f, 18f, 23f, 17.1f, 23f, 16f)
                verticalLineTo(4f)
                curveTo(23f, 2.9f, 22.1f, 2f, 21f, 2f)
                close()
                moveTo(21f, 16f)
                horizontalLineTo(3f)
                verticalLineTo(4f)
                horizontalLineTo(21f)
                verticalLineTo(16f)
                close()
            }
        }.build()
    }

    val Trash: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeliosTrash",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 19f)
                curveTo(6f, 20.1f, 6.9f, 21f, 8f, 21f)
                horizontalLineTo(16f)
                curveTo(17.1f, 21f, 18f, 20.1f, 18f, 19f)
                verticalLineTo(7f)
                horizontalLineTo(6f)
                verticalLineTo(19f)
                close()
                moveTo(19f, 4f)
                horizontalLineTo(15.5f)
                lineTo(14.5f, 3f)
                horizontalLineTo(9.5f)
                lineTo(8.5f, 4f)
                horizontalLineTo(5f)
                verticalLineTo(6f)
                horizontalLineTo(19f)
                verticalLineTo(4f)
                close()
            }
        }.build()
    }
}
