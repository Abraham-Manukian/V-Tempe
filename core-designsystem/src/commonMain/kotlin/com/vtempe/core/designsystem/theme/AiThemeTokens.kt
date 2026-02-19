package com.vtempe.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

enum class AppThemeColor(val primary: Color, val deep: Color, val light: Color) {
    VIOLET(Color(0xFF7C4DFF), Color(0xFF4C1FD4), Color(0xFFEFE1FF)),
    OCEAN(Color(0xFF00B4DB), Color(0xFF0083B0), Color(0xFFE0F7FA)),
    SUNSET(Color(0xFFFF5F6D), Color(0xFFFFC371), Color(0xFFFFF3E0)),
    FOREST(Color(0xFF11998E), Color(0xFF38EF7D), Color(0xFFE8F5E9)),
    MIDNIGHT(Color(0xFF232526), Color(0xFF414345), Color(0xFFECEFF1)),
    
    LAVENDER(Color(0xFFB39DDB), Color(0xFF7E57C2), Color(0xFFF3E5F5)),
    MINT(Color(0xFFA5D6A7), Color(0xFF66BB6A), Color(0xFFE8F5E9)),
    PEACH(Color(0xFFFFCCBC), Color(0xFFFF8A65), Color(0xFFFBE9E7)),
    SKY(Color(0xFF90CAF9), Color(0xFF42A5F5), Color(0xFFE3F2FD)),
    ROSE(Color(0xFFF48FB1), Color(0xFFEC407A), Color(0xFFFCE4EC)),

    TALYK(Color(0xFFE8E2D9), Color(0xFFC4BDB1), Color(0xFFF5F2EE)),
    SAGE(Color(0xFFE6E9E4), Color(0xFFC7CDC3), Color(0xFFF4F6F3)),
    MIST(Color(0xFFDDE2E1), Color(0xFFB8C0BF), Color(0xFFF0F3F2)),
    SHADOW(Color(0xFFDDE1E7), Color(0xFFBCC2CC), Color(0xFFF2F4F7)),
    SNOW(Color(0xFFF4F1E8), Color(0xFFDCD8CB), Color(0xFFFAF9F5))
}

object AiPalette {
    var CurrentPrimary by mutableStateOf(AppThemeColor.VIOLET.primary)
    var CurrentDeep by mutableStateOf(AppThemeColor.VIOLET.deep)
    var CurrentLight by mutableStateOf(AppThemeColor.VIOLET.light)

    val Primary get() = CurrentPrimary
    val DeepAccent get() = CurrentDeep
    val LightAccent get() = CurrentLight

    val PrimaryBright get() = Primary
    
    val OnGradient get() = if (Primary.luminance() > 0.5f) Color(0xFF1A1A1A) else Color.White
    val OnLightAccent get() = if (LightAccent.luminance() > 0.5f) Color(0xFF1A1A1A) else Color.White
    val OnDeepAccent get() = if (DeepAccent.luminance() > 0.5f) Color(0xFF1A1A1A) else Color.White
    
    val Secondary = Color(0xFF2FDFB1)
    val Tertiary = Color(0xFFFF7D87)
    val Neutral900 = Color(0xFF080B16)
    val Neutral800 = Color(0xFF111428)
    val Neutral50 = Color(0xFFFFFFFF)
    
    val SurfaceLight get() = LightAccent
    val SurfaceVariant get() = DeepAccent.copy(alpha = 0.05f)
    val Outline get() = if (Primary.luminance() > 0.7f) Color.Black.copy(alpha = 0.12f) else Color.Transparent
    
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFF8C04B)
    val Danger = Color(0xFFFF5A5F)
}

object AiGradients {
    fun lavenderMist(): Brush = Brush.verticalGradient(
        colors = listOf(AiPalette.DeepAccent, AiPalette.Primary, AiPalette.LightAccent),
        startY = 0f, endY = 2200f
    )

    fun aurora(): Brush = Brush.linearGradient(
        colors = listOf(AiPalette.DeepAccent, AiPalette.Primary, AiPalette.Primary.copy(alpha = 0.8f)),
        start = Offset.Zero, end = Offset(x = 600f, y = 600f)
    )
}

@Composable
fun AiTypography(): Typography {
    val Manrope = manropeFontFamily()
    val shadowColor = if (AiPalette.Primary.luminance() > 0.5f) Color.Transparent else Color.Black.copy(alpha = 0.2f)
    val shadow = Shadow(color = shadowColor, offset = Offset(0f, 2f), blurRadius = 4f)
    
    return Typography(
        displayLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, shadow = shadow),
        headlineLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, shadow = shadow),
        titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp),
        titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp),
        bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp),
        bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp),
        labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    )
}

fun aiLightColorScheme(): ColorScheme = lightColorScheme(
    primary = AiPalette.Primary,
    onPrimary = if (AiPalette.Primary.luminance() > 0.5f) Color.Black else Color.White,
    background = AiPalette.SurfaceLight,
    onBackground = AiPalette.OnLightAccent,
    surface = AiPalette.Neutral50,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = AiPalette.SurfaceVariant,
    outline = AiPalette.DeepAccent.copy(alpha = 0.2f),
    error = AiPalette.Danger,
    onError = Color.White
)

fun aiDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = AiPalette.Primary,
    onPrimary = Color.Black,
    background = AiPalette.Neutral900,
    onBackground = Color.White,
    surface = AiPalette.Neutral800,
    onSurface = Color.White,
    error = AiPalette.Danger,
    onError = Color.White
)

val AiShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

data class AiSpacing(val xs: Dp = 4.dp, val sm: Dp = 8.dp, val md: Dp = 12.dp, val lg: Dp = 16.dp, val xl: Dp = 24.dp)
val LocalAiSpacing = staticCompositionLocalOf { AiSpacing() }
object AiThemeDefaults { val spacing: AiSpacing @Composable get() = LocalAiSpacing.current }
