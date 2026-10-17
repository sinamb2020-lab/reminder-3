package com.example.reminder

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

val Palette = listOf(
    Color(0xFF1E6FE8), Color(0xFF0F9D8A), Color(0xFF3B9B3B), Color(0xFFE59A00),
    Color(0xFFE8590C), Color(0xFFD6336C), Color(0xFF7048E8)
)

fun pal(i: Int): Color = Palette[i.coerceIn(0, Palette.size - 1)]

data class Settings(val accent: Int, val amoled: Boolean, val tf: Int) {
    companion object {
        fun read(c: Context) = Settings(Store.getInt(c, "accent", 0), Store.getBool(c, "amoled", false), Store.getInt(c, "tf", 0))
    }
}

fun formatTime(h: Int, m: Int, tf: Int): String {
    val loc = Locale.getDefault()
    val f = when (tf) {
        1 -> DateTimeFormatter.ofPattern("h:mm a", loc)
        2 -> DateTimeFormatter.ofPattern("HH:mm", loc)
        else -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(loc)
    }
    return LocalTime.of(h, m).format(f)
}

fun dayName(bit: Int): String =
    DayOfWeek.of(if (bit == 0) 7 else bit).getDisplayName(TextStyle.SHORT, Locale.getDefault())

fun weekOrder(): List<Int> {
    val first = Calendar.getInstance().firstDayOfWeek - 1
    return (0..6).map { (first + it) % 7 }
}

fun repeatText(c: Context, days: Int): String = when (days) {
    0 -> c.getString(R.string.once)
    127 -> c.getString(R.string.every_day)
    else -> weekOrder().filter { (days and (1 shl it)) != 0 }.joinToString(", ") { dayName(it) }
}

@Composable
fun AppTheme(s: Settings, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    var scheme = if (s.accent >= Palette.size && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        val a = pal(s.accent)
        if (dark) darkColorScheme(
            primary = lerp(a, Color.White, 0.35f), onPrimary = Color.Black,
            primaryContainer = lerp(a, Color.Black, 0.55f), onPrimaryContainer = Color.White,
            secondaryContainer = lerp(a, Color.Black, 0.6f), onSecondaryContainer = Color.White
        ) else lightColorScheme(
            primary = a, onPrimary = Color.White,
            primaryContainer = lerp(a, Color.White, 0.8f), onPrimaryContainer = lerp(a, Color.Black, 0.5f),
            secondaryContainer = lerp(a, Color.White, 0.8f), onSecondaryContainer = lerp(a, Color.Black, 0.5f)
        )
    }
    if (dark && s.amoled) {
        scheme = scheme.copy(
            background = Color.Black, surface = Color.Black,
            surfaceContainerHighest = Color(0xFF1C1C1E), surfaceContainerHigh = Color(0xFF161618),
            surfaceContainer = Color(0xFF111113), surfaceContainerLow = Color(0xFF0C0C0E)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
