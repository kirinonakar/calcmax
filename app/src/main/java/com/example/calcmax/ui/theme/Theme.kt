package com.example.calcmax.ui.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity

data class InstrumentColors(val body: Color,val display: Color,val numeric: Color,val scientific: Color,val operator: Color,val ink: Color,val muted: Color,val accent: Color,val shift: Color,val alpha: Color,val danger: Color,val grid: Color,val curves: List<Color>)
private val Light = InstrumentColors(Color(0xFFE9EDE9),Color(0xFFF3F6EE),Color(0xFFFAFBF8),Color(0xFFD8E0DA),Color(0xFFC6D9D2),Color(0xFF162A26),Color(0xFF53635D),Color(0xFF006D5B),Color(0xFF785600),Color(0xFF754584),Color(0xFFAA3438),Color(0xFFD4DDD4),listOf(Color(0xFF006D5B),Color(0xFFB7521E),Color(0xFF7449B0),Color(0xFF225FB0),Color(0xFFB13365),Color(0xFF52650D)))
private val Dark = InstrumentColors(Color(0xFF111916),Color(0xFF09120F),Color(0xFF2B3731),Color(0xFF202D27),Color(0xFF29443A),Color(0xFFE6F0E7),Color(0xFF9EB3A6),Color(0xFF79DBB7),Color(0xFFE7C36E),Color(0xFFD6ACE9),Color(0xFFFFA29D),Color(0xFF253A2F),listOf(Color(0xFF79DBB7),Color(0xFFFFB37E),Color(0xFFC4A6FF),Color(0xFF89B9FF),Color(0xFFFFA0C8),Color(0xFFD7E383)))
val LocalInstrument = staticCompositionLocalOf { Light }
@Composable fun CalcmaxTheme(mode: String="System",content: @Composable ()->Unit) {
    val dark=mode=="Dark" || mode=="System" && isSystemInDarkTheme()
    val c=if(dark) Dark else Light
    val view=LocalView.current
    SideEffect { (view.context as? Activity)?.window?.let {window->WindowCompat.getInsetsController(window,view).apply {isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark}} }
    val scheme=if(dark) darkColorScheme(primary=c.accent,onPrimary=c.display,background=c.body,onBackground=c.ink,surface=c.body,onSurface=c.ink,surfaceVariant=c.scientific,onSurfaceVariant=c.muted,secondary=c.alpha,error=c.danger,outline=c.muted) else lightColorScheme(primary=c.accent,onPrimary=Color.White,background=c.body,onBackground=c.ink,surface=c.body,onSurface=c.ink,surfaceVariant=c.scientific,onSurfaceVariant=c.muted,secondary=c.alpha,error=c.danger,outline=c.muted)
    val complete=scheme.copy(primaryContainer=c.operator,onPrimaryContainer=c.ink,secondaryContainer=c.operator,onSecondaryContainer=c.ink,tertiaryContainer=c.scientific,onTertiaryContainer=c.ink,surfaceContainer=c.scientific,surfaceContainerHigh=c.scientific,surfaceContainerHighest=c.numeric,surfaceContainerLow=c.body,surfaceContainerLowest=c.display)
    CompositionLocalProvider(LocalInstrument provides c) { MaterialTheme(colorScheme=complete,typography=Typography,content=content) }
}
