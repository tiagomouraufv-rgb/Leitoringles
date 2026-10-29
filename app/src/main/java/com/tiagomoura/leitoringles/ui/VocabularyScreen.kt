package com.tiagomoura.leitoringles.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiagomoura.leitoringles.AppState
import com.tiagomoura.leitoringles.Screen
import com.tiagomoura.leitoringles.data.VocabItem

private val TABS = listOf("verb" to "Verbos", "expression" to "Expressões", "word" to "Palavras")

@Composable
fun VocabularyScreen(app: AppState) {
    BackHandler { app.screen = Screen.Library }
    var tab by remember { mutableStateOf("verb") }
    var sortAlpha by remember { mutableStateOf(true) }
    var found by remember { mutableStateOf<List<VocabItem>>(emptyList()) }

    // expressions met while reading (not saved yet)
    LaunchedEffect(app.books, app.progress.toMap()) {
        val out = ArrayList<VocabItem>()
        for (m in app.books) {
            val p = app.progress[m.id] ?: continue
            if (p.updated == 0L) continue
            val book = try { app.loadBook(m.id) } catch (e: Exception) { null } ?: continue
            for (e in book.expressions) {
                val first = e.occ.firstOrNull() ?: continue
                val ch = book.chapters.getOrNull(first.chapter) ?: continue
                val off = ch.offsetOf(first.paragraph, first.start)
                if (!p.isBeyond(first.chapter, off)) continue
                val para = ch.paragraphs.getOrNull(first.paragraph)?.text ?: ""
                out.add(
                    VocabItem(
                        key = e.id, word = e.text, pt = e.pt, def = e.def, cat = "expression",
                        bookId = m.id, bookTitle = m.title,
                        example = TextTools.sentenceAround(para, first.start),
                        chapter = first.chapter, offset = off, added = 0L,
                    )
                )
            }
        }
        found = out.distinctBy { it.key }
    }

    Column(Modifier.fillMaxSize().background(Paper)) {
        TopBar("Vocabulário", onBack = { app.screen = Screen.Library }) {
            EinkButton(if (sortAlpha) "A–Z" else "Recentes", Modifier.padding(end = 6.dp)) { sortAlpha = !sortAlpha }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((id, label) in TABS) {
                val n = app.vocabulary.count { it.cat == id }
                EinkButton("$label ($n)", Modifier.weight(1f), filled = tab == id) { tab = id }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val saved = app.vocabulary.filter { it.cat == tab }.let { l ->
                if (sortAlpha) l.sortedBy { it.word.lowercase() } else l.sortedByDescending { it.added }
            }
            val extra = if (tab == "expression") found.filter { f -> app.vocabulary.none { it.key == f.key } }
                .sortedBy { it.word.lowercase() } else emptyList()
            if (saved.isEmpty() && extra.isEmpty()) {
                Text(
                    "Nada aqui ainda. Durante a leitura, toque numa palavra e escolha \"Enviar para Vocabulário\".",
                    Modifier.align(Alignment.Center).padding(24.dp), fontSize = 18.sp, textAlign = TextAlign.Center,
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    items(saved, key = { "s-" + it.bookId + it.key }) { v -> VocabCard(app, v, saved = true) }
                    if (extra.isNotEmpty()) {
                        item {
                            Text(
                                "Encontradas na leitura", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(extra, key = { "f-" + it.bookId + it.key }) { v -> VocabCard(app, v, saved = false) }
                    }
                }
            }
        }
        val last = app.lastBookId?.let { app.meta(it) }
        BottomBar("vocabulary", last != null, last?.title ?: "Nenhum livro",
            onGrammar = { app.screen = Screen.Grammar },
            onContinue = { last?.let { app.screen = Screen.Reader(it.id) } }, onVocabulary = {})
    }
}

@Composable
private fun VocabCard(app: AppState, v: VocabItem, saved: Boolean) {
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .border(1.dp, Ink, RoundedCornerShape(6.dp)).padding(12.dp)
    ) {
        Text(v.word, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
        Text(v.pt, fontSize = 17.sp)
        v.def?.let { Text(it, fontSize = 14.sp, fontStyle = FontStyle.Italic, color = Grey) }
        if (v.example.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text("“${v.example}”", fontSize = 15.sp, fontFamily = FontFamily.Serif)
        }
        Text(v.bookTitle, fontSize = 12.sp, color = Grey)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EinkButton("Ver no livro") { app.screen = Screen.Reader(v.bookId, v.chapter, v.offset) }
            if (saved) {
                if (!confirm) EinkButton("Remover") { confirm = true }
                else EinkButton("Confirmar", filled = true) { app.removeVocab(v.key, v.bookId) }
            } else {
                EinkButton("Salvar", filled = true) { app.addVocab(v.copy(added = System.currentTimeMillis())) }
            }
        }
    }
}
