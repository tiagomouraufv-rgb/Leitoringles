package com.tiagomoura.leitoringles.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiagomoura.leitoringles.AppState
import com.tiagomoura.leitoringles.data.Book
import com.tiagomoura.leitoringles.data.VocabItem

/** Panel shown after the reader selects a passage: the passage and the translation of its sentences. */
@Composable
fun SelectionPanel(app: AppState, book: Book, chapterIndex: Int, range: IntRange, onClose: () -> Unit) {
    val chapter = book.chapters[chapterIndex]
    val text = chapter.text
    val a = range.first.coerceIn(0, text.length)
    val b = (range.last + 1).coerceIn(a, text.length)
    val passage = text.substring(a, b).replace(Regex("\\s*\\n\\s*"), " ")
    val sentences = remember(chapterIndex, a, b) { chapter.sentencesIn(a, b) }
    val translated = sentences.mapNotNull { it.pt }
    val words = passage.split(Regex("\\s+")).count { it.isNotBlank() }
    val key = "S:" + passage.lowercase().trim().trim('.', ',', ';', ':', '!', '?', '"', '“', '”')
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Trecho selecionado", fontSize = 13.sp, color = Grey)
        Text(
            if (passage.length > 700) passage.take(700) + "…" else passage,
            fontSize = 19.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Divider()
        Spacer(Modifier.height(10.dp))
        if (translated.isNotEmpty()) {
            Text(if (sentences.size > 1) "Tradução das frases" else "Tradução da frase", fontSize = 13.sp, color = Grey)
            for (t in translated) {
                Text(t, fontSize = 18.sp, modifier = Modifier.padding(top = 4.dp))
            }
            if (words < sentences.sumOf { s -> text.substring(s.start, s.end).split(Regex("\\s+")).size }) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "A tradução mostra a frase inteira em que o trecho aparece.",
                    fontSize = 13.sp, fontStyle = FontStyle.Italic, color = Grey,
                )
            }
        } else {
            Text(
                "Este livro ainda não tem a tradução das frases. Os livros novos feitos pelo Claude já vêm com ela.",
                fontSize = 15.sp, color = Grey,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (words in 1..12 && translated.isNotEmpty()) {
                val saved = app.isSaved(key, book.meta.id)
                EinkButton(if (saved) "✓ No vocabulário" else "Enviar para Vocabulário", filled = !saved) {
                    if (saved) app.removeVocab(key, book.meta.id)
                    else app.addVocab(
                        VocabItem(
                            key = key, word = passage, pt = translated.joinToString(" "), def = null,
                            cat = "expression", bookId = book.meta.id, bookTitle = book.meta.title,
                            example = sentences.firstOrNull()?.let { text.substring(it.start, it.end) } ?: passage,
                            chapter = chapterIndex, offset = a, added = System.currentTimeMillis(),
                        )
                    )
                }
            }
            EinkButton("Fechar") { onClose() }
        }
    }
}

/** Full-screen image with pinch zoom. Double tap resets the zoom. */
@Composable
fun FullImage(bitmap: Bitmap, source: String, onClose: () -> Unit) {
    BackHandler { onClose() }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().background(Paper).tap { }) {
        Image(
            bitmap.asImageBitmap(), contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(top = 56.dp, bottom = 28.dp)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero })
                }
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offset.x; translationY = offset.y
                },
        )
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Paper).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Dois dedos para ampliar · toque duplo volta ao normal", fontSize = 13.sp, color = Grey, modifier = Modifier.weight(1f))
            EinkButton("Fechar", filled = true) { onClose() }
        }
        Text(
            "Fonte: $source", fontSize = 12.sp, color = Grey,
            modifier = Modifier.align(Alignment.BottomCenter).background(Paper).padding(6.dp),
        )
    }
}
