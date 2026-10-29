package com.tiagomoura.leitoringles.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiagomoura.leitoringles.AppState
import com.tiagomoura.leitoringles.Screen
import com.tiagomoura.leitoringles.data.Book
import com.tiagomoura.leitoringles.data.GrammarTopic
import com.tiagomoura.leitoringles.data.Occurrence
import com.tiagomoura.leitoringles.data.SYLLABUS

private data class Example(val book: Book, val occ: Occurrence)

private data class TopicView(val topic: GrammarTopic, val examples: List<Example>)

@Composable
fun GrammarScreen(app: AppState) {
    BackHandler { app.screen = Screen.Library }
    var topics by remember { mutableStateOf<List<TopicView>?>(null) }
    var locked by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf(app.grammarFocus) }

    LaunchedEffect(app.books, app.progress.toMap()) {
        val all = HashMap<String, Pair<GrammarTopic, MutableList<Example>>>()
        val seenIds = HashSet<String>()
        for (m in app.books) {
            val p = app.progress[m.id] ?: continue
            if (p.updated == 0L) continue
            val book = try { app.loadBook(m.id) } catch (e: Exception) { null } ?: continue
            for (g in book.grammar) {
                seenIds.add(g.id)
                val read = g.occ.filter { p.isBeyond(it.chapter, book.chapters[it.chapter.coerceIn(0, book.chapters.size - 1)].offsetOf(it.paragraph, it.end)) }
                if (read.isEmpty()) continue
                val slot = all.getOrPut(g.id) { g to mutableListOf() }
                slot.second.addAll(read.map { Example(book, it) })
            }
        }
        val list = all.values.map { (g, ex) -> TopicView(g, ex) }
            .sortedWith(compareBy({ SYLLABUS.indexOf(it.topic.id).let { i -> if (i < 0) 1000 + it.topic.order else i } }, { it.topic.title }))
        locked = (SYLLABUS.toSet() + seenIds).size - list.size
        topics = list
    }

    Column(Modifier.fillMaxSize().background(Paper)) {
        TopBar("Gramática", onBack = { app.screen = Screen.Library })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val list = topics
            when {
                list == null -> Text("Carregando...", Modifier.align(Alignment.Center), fontSize = 18.sp)
                list.isEmpty() -> Text(
                    "Os pontos de gramática aparecem aqui conforme você lê. Abra um livro e comece a leitura.",
                    Modifier.align(Alignment.Center).padding(24.dp), fontSize = 18.sp,
                )
                else -> {
                    val focusIndex = list.indexOfFirst { it.topic.id == open }.coerceAtLeast(0)
                    val state = rememberLazyListState(initialFirstVisibleItemIndex = focusIndex)
                    LazyColumn(state = state, contentPadding = PaddingValues(12.dp)) {
                        item {
                            Text(
                                "Em ordem de aprendizagem, do mais simples ao mais avançado. Toque num tópico para ver a explicação e os exemplos que você já leu.",
                                fontSize = 14.sp, color = Grey, modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                        items(list, key = { it.topic.id }) { tv ->
                            TopicCard(app, tv, expanded = open == tv.topic.id) {
                                open = if (open == tv.topic.id) null else tv.topic.id
                            }
                        }
                        if (locked > 0) item {
                            Text(
                                "Mais $locked tópicos serão desbloqueados com a leitura.",
                                Modifier.padding(16.dp), fontSize = 15.sp, fontStyle = FontStyle.Italic, color = Grey,
                            )
                        }
                    }
                }
            }
        }
        val last = app.lastBookId?.let { app.meta(it) }
        BottomBar("grammar", last != null, last?.title ?: "Nenhum livro",
            onGrammar = {}, onContinue = { last?.let { app.screen = Screen.Reader(it.id) } },
            onVocabulary = { app.screen = Screen.Vocabulary })
    }
}

@Composable
private fun TopicCard(app: AppState, tv: TopicView, expanded: Boolean, onToggle: () -> Unit) {
    val g = tv.topic
    Column(
        Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .border(if (expanded) 2.dp else 1.dp, Ink, RoundedCornerShape(6.dp)).padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().tap(onToggle), verticalAlignment = Alignment.CenterVertically) {
            LevelBadge(g.level, app.colorScreen)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(g.titlePt, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(g.title, fontSize = 13.sp, color = Grey)
            }
            Text("${tv.examples.size}", fontSize = 14.sp, color = Grey)
            Spacer(Modifier.width(6.dp))
            Text(if (expanded) "▴" else "▾", fontSize = 18.sp)
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Text("Forma", fontSize = 13.sp, color = Grey)
            Text(g.form, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text("Uso", fontSize = 13.sp, color = Grey)
            Text(g.explanation, fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))
            Text("Exemplos que você já leu", fontSize = 13.sp, color = Grey)
            val shown = tv.examples.takeLast(10).reversed()
            for (ex in shown) {
                val chapter = ex.book.chapters.getOrNull(ex.occ.chapter) ?: continue
                val para = chapter.paragraphs.getOrNull(ex.occ.paragraph)?.text ?: continue
                val ss = if (ex.occ.sentStart >= 0) ex.occ.sentStart else 0
                val se = if (ex.occ.sentEnd > 0) ex.occ.sentEnd.coerceAtMost(para.length) else para.length
                val s = ex.occ.start.coerceIn(ss, se)
                val e = ex.occ.end.coerceIn(s, se)
                val annotated = buildAnnotatedString {
                    append(para.substring(ss, s))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
                        append(para.substring(s, e))
                    }
                    append(para.substring(e, se))
                }
                Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp).tap {
                        app.screen = Screen.Reader(ex.book.meta.id, ex.occ.chapter, chapter.offsetOf(ex.occ.paragraph, ss))
                    }
                ) {
                    Text(annotated, fontSize = 16.sp, fontFamily = FontFamily.Serif)
                    Text("${ex.book.meta.title} · ${chapter.title}", fontSize = 12.sp, color = Grey)
                }
            }
        }
    }
}
