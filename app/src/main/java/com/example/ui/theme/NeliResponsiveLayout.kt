package com.example.ui.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Adaptive screen size categories covering all Android devices:
 * - [COMPACT_SMALL]: Compact entry-level phones (< 360dp width, e.g., Itel A-series, Tecno Pop, Infinix Smart)
 * - [COMPACT_STANDARD]: Standard phones (360dp..411dp width, e.g., Tecno Spark/Camon, Infinix Hot/Note, Samsung Galaxy A)
 * - [COMPACT_LARGE]: Large phones / phablets (412dp..599dp width, e.g., Samsung Galaxy S Ultra, Infinix Zero, Tecno Phantom)
 * - [MEDIUM_TABLET]: Compact tablets & unfolded foldables (600dp..839dp width)
 * - [EXPANDED_TABLET]: Large tablets & landscape tablets (>= 840dp width, e.g., Samsung Galaxy Tab)
 */
enum class NeliScreenSizeClass {
    COMPACT_SMALL,
    COMPACT_STANDARD,
    COMPACT_LARGE,
    MEDIUM_TABLET,
    EXPANDED_TABLET
}

@Immutable
data class NeliScreenProfile(
    val widthDp: Int,
    val heightDp: Int,
    val sizeClass: NeliScreenSizeClass,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val cardSpacing: Dp,
    val liveChannelColumns: Int,
    val discoveryGridColumns: Int,
    val posterCardWidth: Dp,
    val adultCardWidth: Dp,
    val heroBannerHeight: Dp,
    val bottomBarIconSize: Dp,
    val bottomBarFontSize: TextUnit,
    val bottomBarHorizontalItemPadding: Dp,
    val headerHeight: Dp,
    val qrPreviewSize: Dp,
    val maxFormWidth: Dp,
    val isSmallPhone: Boolean,
    val isTablet: Boolean,
    val deviceBrandLabel: String
) {
    val liveChannelGridColumns: Int
        get() = liveChannelColumns

    val isTabletOrFoldable: Boolean
        get() = isTablet

    val horizontalChannelCardWidth: Dp
        get() = posterCardWidth

    val brandOptimizationLabel: String
        get() = "$deviceBrandLabel • ${widthDp}dp"
}

object NeliResponsiveLayout {

    fun detectDeviceBrandLabel(
        manufacturer: String = Build.MANUFACTURER.orEmpty(),
        model: String = Build.MODEL.orEmpty()
    ): String {
        val raw = "$manufacturer $model".trim()
        val lower = raw.lowercase()
        return when {
            lower.contains("samsung") || lower.contains("sm-") -> "Samsung"
            lower.contains("tecno") -> "Tecno"
            lower.contains("infinix") -> "Infinix"
            lower.contains("itel") -> "Itel"
            lower.contains("xiaomi") || lower.contains("redmi") || lower.contains("poco") -> "Xiaomi"
            lower.contains("oppo") || lower.contains("realme") -> "Oppo"
            lower.contains("vivo") -> "Vivo"
            lower.contains("huawei") || lower.contains("honor") -> "Huawei"
            lower.contains("google") || lower.contains("pixel") -> "Google Pixel"
            else -> manufacturer.replaceFirstChar { it.uppercase() }.ifBlank { "Android Device" }
        }
    }

    fun resolveProfileForDimensions(
        widthDp: Int,
        heightDp: Int = 800,
        manufacturer: String = Build.MANUFACTURER.orEmpty(),
        model: String = Build.MODEL.orEmpty()
    ): NeliScreenProfile {
        val safeWidth = widthDp.coerceAtLeast(280)
        val safeHeight = heightDp.coerceAtLeast(480)
        val sizeClass = when {
            safeWidth < 360 -> NeliScreenSizeClass.COMPACT_SMALL
            safeWidth < 412 -> NeliScreenSizeClass.COMPACT_STANDARD
            safeWidth < 600 -> NeliScreenSizeClass.COMPACT_LARGE
            safeWidth < 840 -> NeliScreenSizeClass.MEDIUM_TABLET
            else -> NeliScreenSizeClass.EXPANDED_TABLET
        }
        val isSmallPhone = sizeClass == NeliScreenSizeClass.COMPACT_SMALL
        val isTablet = sizeClass == NeliScreenSizeClass.MEDIUM_TABLET ||
            sizeClass == NeliScreenSizeClass.EXPANDED_TABLET
        val brand = detectDeviceBrandLabel(manufacturer, model)

        return when (sizeClass) {
            NeliScreenSizeClass.COMPACT_SMALL -> NeliScreenProfile(
                widthDp = safeWidth,
                heightDp = safeHeight,
                sizeClass = sizeClass,
                horizontalPadding = 10.dp,
                verticalPadding = 10.dp,
                cardSpacing = 8.dp,
                liveChannelColumns = 2,
                discoveryGridColumns = 2,
                posterCardWidth = 132.dp,
                adultCardWidth = 196.dp,
                heroBannerHeight = 182.dp,
                bottomBarIconSize = 20.dp,
                bottomBarFontSize = 9.sp,
                bottomBarHorizontalItemPadding = 4.dp,
                headerHeight = 48.dp,
                qrPreviewSize = 104.dp,
                maxFormWidth = 560.dp,
                isSmallPhone = true,
                isTablet = false,
                deviceBrandLabel = brand
            )
            NeliScreenSizeClass.COMPACT_STANDARD -> NeliScreenProfile(
                widthDp = safeWidth,
                heightDp = safeHeight,
                sizeClass = sizeClass,
                horizontalPadding = 14.dp,
                verticalPadding = 12.dp,
                cardSpacing = 10.dp,
                liveChannelColumns = 2,
                discoveryGridColumns = 2,
                posterCardWidth = 148.dp,
                adultCardWidth = 220.dp,
                heroBannerHeight = 200.dp,
                bottomBarIconSize = 22.dp,
                bottomBarFontSize = 10.sp,
                bottomBarHorizontalItemPadding = 8.dp,
                headerHeight = 52.dp,
                qrPreviewSize = 120.dp,
                maxFormWidth = 600.dp,
                isSmallPhone = false,
                isTablet = false,
                deviceBrandLabel = brand
            )
            NeliScreenSizeClass.COMPACT_LARGE -> NeliScreenProfile(
                widthDp = safeWidth,
                heightDp = safeHeight,
                sizeClass = sizeClass,
                horizontalPadding = 16.dp,
                verticalPadding = 14.dp,
                cardSpacing = 12.dp,
                liveChannelColumns = 2,
                discoveryGridColumns = 3,
                posterCardWidth = 156.dp,
                adultCardWidth = 232.dp,
                heroBannerHeight = 210.dp,
                bottomBarIconSize = 24.dp,
                bottomBarFontSize = 10.sp,
                bottomBarHorizontalItemPadding = 10.dp,
                headerHeight = 54.dp,
                qrPreviewSize = 128.dp,
                maxFormWidth = 640.dp,
                isSmallPhone = false,
                isTablet = false,
                deviceBrandLabel = brand
            )
            NeliScreenSizeClass.MEDIUM_TABLET -> NeliScreenProfile(
                widthDp = safeWidth,
                heightDp = safeHeight,
                sizeClass = sizeClass,
                horizontalPadding = 22.dp,
                verticalPadding = 16.dp,
                cardSpacing = 14.dp,
                liveChannelColumns = 3,
                discoveryGridColumns = 4,
                posterCardWidth = 172.dp,
                adultCardWidth = 256.dp,
                heroBannerHeight = 240.dp,
                bottomBarIconSize = 25.dp,
                bottomBarFontSize = 11.sp,
                bottomBarHorizontalItemPadding = 14.dp,
                headerHeight = 58.dp,
                qrPreviewSize = 144.dp,
                maxFormWidth = 720.dp,
                isSmallPhone = false,
                isTablet = true,
                deviceBrandLabel = brand
            )
            NeliScreenSizeClass.EXPANDED_TABLET -> NeliScreenProfile(
                widthDp = safeWidth,
                heightDp = safeHeight,
                sizeClass = sizeClass,
                horizontalPadding = 28.dp,
                verticalPadding = 18.dp,
                cardSpacing = 16.dp,
                liveChannelColumns = 4,
                discoveryGridColumns = 5,
                posterCardWidth = 186.dp,
                adultCardWidth = 272.dp,
                heroBannerHeight = 264.dp,
                bottomBarIconSize = 26.dp,
                bottomBarFontSize = 12.sp,
                bottomBarHorizontalItemPadding = 18.dp,
                headerHeight = 60.dp,
                qrPreviewSize = 156.dp,
                maxFormWidth = 840.dp,
                isSmallPhone = false,
                isTablet = true,
                deviceBrandLabel = brand
            )
        }
    }
}

@Composable
fun rememberNeliScreenProfile(): NeliScreenProfile {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        NeliResponsiveLayout.resolveProfileForDimensions(
            widthDp = configuration.screenWidthDp,
            heightDp = configuration.screenHeightDp
        )
    }
}
