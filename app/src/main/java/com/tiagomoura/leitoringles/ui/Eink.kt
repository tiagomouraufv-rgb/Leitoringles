package com.tiagomoura.leitoringles.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF000000)
val Paper = Color(0xFFFFFFFF)
val Grey = Color(0xFF6E6E6E)
val LightGrey = Color(0xFFD9D9D9)

/** Soft colours for each level, used only on colour screens. */
fun levelColor(level: String, color: Boolean): Color = if (!color) Paper else when (level) {
    "A1" -> Color(0xFFE3F2D9)
    "A2" -> Color(0xFFD7EEF2)
    "B1" -> Color(0xFFFFF0C9)
    "B2" -> Color(0xFFFBDCCB)
    "C1" -> Color(0xFFE9DDF5)
    "C2" -> Color(0xFFDADDF2)
    else -> Paper
}

@Composable
fun EinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink, onPrimary = Paper, secondary = Ink, onSecondary = Paper,
            background = Paper, onBackground = Ink, surface = Paper, onSurface = Ink,
            surfaceVariant = Paper, onSurfaceVariant = Ink, outline = Ink,
        ),
        content = content,
    )
}

/** A click without ripple animations (they look bad on e-ink). */
@Composable
fun Modifier.tap(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = src, indication = null, onClick = onClick)
}

@Composable
fun EinkButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val fg = if (!enabled) Grey else if (filled) Paper else Ink
    val bg = if (filled && enabled) Ink else Paper
    Box(
        modifier
            .border(BorderStroke(2.dp, if (enabled) Ink else Grey), RoundedCornerShape(6.dp))
            .background(bg, RoundedCornerShape(6.dp))
            .then(if (enabled) Modifier.tap(onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Column {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Box(Modifier.size(48.dp).tap(onBack), contentAlignment = Alignment.Center) {
                    Text("←", fontSize = 26.sp, color = Ink)
                }
            } else Spacer(Modifier.width(8.dp))
            Text(
                title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Serif,
            )
            actions()
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Ink))
    }
}

/** Book-shaped icon for the central "continue reading" button. */
@Composable
fun BookIcon(modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = if (enabled) Ink else Grey
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.06f)
        // left page
        drawRoundRect(c, Offset(w * 0.06f, h * 0.12f), Size(w * 0.42f, h * 0.76f), CornerRadius(w * 0.04f), style = stroke)
        // right page
        drawRoundRect(c, Offset(w * 0.52f, h * 0.12f), Size(w * 0.42f, h * 0.76f), CornerRadius(w * 0.04f), style = stroke)
        // text lines
        for (i in 0..2) {
            val y = h * (0.32f + i * 0.16f)
            drawLine(c, Offset(w * 0.14f, y), Offset(w * 0.40f, y), strokeWidth = w * 0.04f)
            drawLine(c, Offset(w * 0.60f, y), Offset(w * 0.86f, y), strokeWidth = w * 0.04f)
        }
    }
}

@Composable
fun BottomBar(
    current: String,
    canContinue: Boolean,
    continueLabel: String,
    onGrammar: () -> Unit,
    onContinue: () -> Unit,
    onVocabulary: () -> Unit,
) {
    Column {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Ink))
        Row(
            Modifier.fillMaxWidth().height(76.dp).background(Paper),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BarItem("Gramática", "Aa", current == "grammar", Modifier.weight(1f), onGrammar)
            Column(
                Modifier.weight(1f).tap { if (canContinue) onContinue() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BookIcon(Modifier.size(width = 52.dp, height = 40.dp), enabled = canContinue)
                Text(
                    continueLabel, fontSize = 12.sp, color = if (canContinue) Ink else Grey,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            BarItem("Vocabulário", "≡", current == "vocabulary", Modifier.weight(1f), onVocabulary)
        }
    }
}

@Composable
private fun BarItem(label: String, symbol: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.tap(onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(width = 52.dp, height = 36.dp)
                .then(if (selected) Modifier.background(Ink, RoundedCornerShape(6.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (selected) Paper else Ink, fontFamily = FontFamily.Serif)
        }
        Text(label, fontSize = 13.sp, color = Ink, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun LevelBadge(level: String, color: Boolean) {
    Box(
        Modifier.border(1.5.dp, Ink, RoundedCornerShape(4.dp))
            .background(levelColor(level, color), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(level, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LightGrey))
}
