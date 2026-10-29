package com.tiagomoura.leitoringles.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tiagomoura.leitoringles.AppState
import com.tiagomoura.leitoringles.Screen
import com.tiagomoura.leitoringles.data.BookMeta
import com.tiagomoura.leitoringles.data.LEVELS
import com.tiagomoura.leitoringles.data.LEVEL_NAMES
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(app: AppState) {
    var info by remember { mutableStateOf<BookMeta?>(null) }
    Column(Modifier.fillMaxSize().background(Paper)) {
        TopBar("Biblioteca") {
            EinkButton("+ Adicionar livro", Modifier.padding(end = 6.dp)) { app.screen = Screen.AddBook }
            Box(Modifier.size(48.dp).tap { app.screen = Screen.Settings }, contentAlignment = Alignment.Center) {
                Text("⚙", fontSize = 24.sp, color = Ink)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (app.loadingLibrary && app.books.isEmpty()) {
                Text("Abrindo a biblioteca...", Modifier.align(Alignment.Center), fontSize = 18.sp)
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    for (level in LEVELS) {
                        val shelf = app.books.filter { it.level == level }.sortedBy { it.title }
                        item(key = "shelf-$level") {
                            Shelf(app, level, shelf, onOpen = { app.screen = Screen.Reader(it.id) }, onInfo = { info = it })
                        }
                    }
                    val other = app.books.filter { it.level !in LEVELS }
                    if (other.isNotEmpty()) item {
                        Shelf(app, "?", other, onOpen = { app.screen = Screen.Reader(it.id) }, onInfo = { info = it })
                    }
                }
            }
        }
        val last = app.lastBookId?.let { app.meta(it) }
        BottomBar(
            current = "library",
            canContinue = last != null,
            continueLabel = last?.title ?: "Nenhum livro",
            onGrammar = { app.screen = Screen.Grammar },
            onContinue = { last?.let { app.screen = Screen.Reader(it.id) } },
            onVocabulary = { app.screen = Screen.Vocabulary },
        )
    }
    info?.let { m -> BookInfoDialog(app, m, onClose = { info = null }) }
}

@Composable
private fun Shelf(app: AppState, level: String, books: List<BookMeta>, onOpen: (BookMeta) -> Unit, onInfo: (BookMeta) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            LevelBadge(level, app.colorScreen)
            Spacer(Modifier.width(10.dp))
            Text(LEVEL_NAMES[level] ?: "Outros", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            Spacer(Modifier.width(8.dp))
            Text("· ${books.size} ${if (books.size == 1) "livro" else "livros"}", fontSize = 14.sp, color = Grey)
        }
        Spacer(Modifier.height(8.dp))
        if (books.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                Text("Nenhum livro ainda", fontSize = 15.sp, color = Grey, fontStyle = FontStyle.Italic)
            }
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(books, key = { it.id }) { b -> BookCard(app, b, onOpen, onInfo) }
            }
        }
        // the shelf board
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(6.dp).background(Ink, RoundedCornerShape(2.dp)))
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(3.dp).background(LightGrey))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(app: AppState, b: BookMeta, onOpen: (BookMeta) -> Unit, onInfo: (BookMeta) -> Unit) {
    val p = app.progress[b.id]
    val pct = p?.let { b.percent(it.chapter, it.offset) } ?: 0
    val src = remember { MutableInteractionSource() }
    Column(
        Modifier.width(120.dp).combinedClickable(
            interactionSource = src, indication = null,
            onClick = { onOpen(b) }, onLongClick = { onInfo(b) },
        )
    ) {
        Column(
            Modifier.width(120.dp).height(170.dp)
                .border(2.dp, Ink, RoundedCornerShape(4.dp))
                .background(levelColor(b.level, app.colorScreen), RoundedCornerShape(4.dp))
        ) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(10.dp).fillMaxHeight().background(Ink))
                Column(Modifier.weight(1f).padding(8.dp)) {
                    LevelBadge(b.level, app.colorScreen)
                    Spacer(Modifier.weight(1f))
                    Text(
                        b.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif,
                        maxLines = 4, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(b.author, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).border(1.dp, Ink)) {
            Box(Modifier.fillMaxWidth(pct / 100f).height(8.dp).background(Ink))
        }
        Text(if (pct == 0 && p?.updated == 0L || p == null) "Não iniciado" else "$pct% lido", fontSize = 12.sp, color = Ink)
    }
}

@Composable
private fun BookInfoDialog(app: AppState, m: BookMeta, onClose: () -> Unit) {
    val p = app.progress[m.id]
    val saved = app.vocabulary.count { it.bookId == m.id }
    var confirm by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth().border(2.dp, Ink, RoundedCornerShape(8.dp)).background(Paper, RoundedCornerShape(8.dp))
                .padding(20.dp).verticalScroll(rememberScrollState())
        ) {
            Text(m.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            Text(listOfNotNull(m.author, if (m.year > 0) m.year.toString() else null).joinToString(" · "), fontSize = 15.sp, color = Grey)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(m.level, app.colorScreen)
                Spacer(Modifier.width(8.dp))
                Text("${m.chapterLengths.size} partes", fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(m.description, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            val pct = p?.let { m.percent(it.chapter, it.offset) } ?: 0
            Text("Progresso: $pct%", fontSize = 15.sp)
            Text("Palavras salvas: $saved", fontSize = 15.sp)
            if (p != null && p.updated > 0) {
                val d = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(p.updated))
                Text("Última leitura: $d", fontSize = 15.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EinkButton("Ler", filled = true) { onClose(); app.screen = Screen.Reader(m.id) }
                EinkButton("Fechar") { onClose() }
            }
            if (m.source.startsWith("file:")) {
                Spacer(Modifier.height(12.dp))
                if (!confirm) EinkButton("Remover livro") { confirm = true }
                else Column {
                    Text("Remover este livro e seu progresso? As palavras salvas ficam.", fontSize = 14.sp, textAlign = TextAlign.Start)
                    Spacer(Modifier.height(6.dp))
                    EinkButton("Sim, remover", filled = true) { app.removeBook(m); onClose() }
                }
            }
        }
    }
}
