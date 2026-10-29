package com.tiagomoura.leitoringles.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tiagomoura.leitoringles.AppState
import com.tiagomoura.leitoringles.data.Book
import com.tiagomoura.leitoringles.data.Entry
import com.tiagomoura.leitoringles.data.Expression
import com.tiagomoura.leitoringles.data.GrammarTopic
import com.tiagomoura.leitoringles.data.VocabItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed class ImgState {
    data object None : ImgState()
    data object Loading : ImgState()
    data class Shown(val bitmap: Bitmap, val source: String) : ImgState()
    data object NotFound : ImgState()
}

@Composable
fun WordPopup(app: AppState, book: Book, t: TapTarget, onClose: () -> Unit, onGrammar: (String) -> Unit) {
    val chapter = book.chapters[t.chapter]
    val text = chapter.text
    val surface = text.substring(t.start, t.end)
    val entry: Entry? = remember(t) { book.lookup(surface) }
    val (para, inPara) = chapter.locate(t.offset)
    val expressions: List<Expression> = remember(t) {
        book.expressionsByChapter[t.chapter].orEmpty()
            .filter { (_, o) -> o.paragraph == para && inPara >= o.start && inPara < o.end }
            .map { it.first }.distinctBy { it.id }
    }
    val grammar: List<GrammarTopic> = remember(t) {
        book.grammarByChapter[t.chapter].orEmpty()
            .filter { (_, o) -> o.paragraph == para && inPara >= o.start && inPara < o.end }
            .map { it.first }.distinctBy { it.id }
            .filter { it.id != "be_present" && it.id != "present_simple" && it.id != "be_past" }
    }
    val sentence = remember(t) { TextTools.sentenceAround(text, t.offset) }
    var img by remember(t) { mutableStateOf<ImgState>(ImgState.None) }
    val scope = rememberCoroutineScope()

    fun searchImage(query: String, isName: Boolean) {
        img = ImgState.Loading
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                app.images.find(query, isName, app.settings.braveKey, app.colorScreen)
            }
            img = if (r != null) ImgState.Shown(r.bitmap, r.source) else ImgState.NotFound
        }
    }

    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 640.dp)
                .border(2.dp, Ink, RoundedCornerShape(8.dp)).background(Paper, RoundedCornerShape(8.dp))
                .verticalScroll(rememberScrollState()).padding(18.dp)
        ) {
            for (e in expressions) {
                Text(if (e.type == "phrasal") "Phrasal verb" else "Expressão", fontSize = 13.sp, color = Grey)
                Text(e.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                Text(e.pt, fontSize = 18.sp)
                e.def?.let { Text(it, fontSize = 15.sp, fontStyle = FontStyle.Italic, color = Grey) }
                Spacer(Modifier.height(8.dp))
                val savedE = app.isSaved(e.id, book.meta.id)
                EinkButton(if (savedE) "✓ No vocabulário (remover)" else "Enviar para Vocabulário", filled = !savedE) {
                    if (savedE) app.removeVocab(e.id, book.meta.id)
                    else app.addVocab(
                        VocabItem(
                            key = e.id, word = e.text, pt = e.pt, def = e.def, cat = "expression",
                            bookId = book.meta.id, bookTitle = book.meta.title, example = sentence,
                            chapter = t.chapter, offset = t.offset, added = System.currentTimeMillis(),
                        )
                    )
                }
                Spacer(Modifier.height(14.dp))
                Divider()
                Spacer(Modifier.height(14.dp))
            }

            Text(surface, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            if (entry != null) {
                val posPt = TextTools.POS_PT[entry.pos].orEmpty()
                val lemmaInfo = if (!entry.word.equals(surface, ignoreCase = true) && entry.pos != "name") "forma de: ${entry.word}" else ""
                val sub = listOf(posPt, lemmaInfo).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, fontSize = 14.sp, color = Grey)
                Spacer(Modifier.height(6.dp))
                Text(entry.pt.ifBlank { "(sem tradução)" }, fontSize = 20.sp)
                entry.def?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, fontSize = 16.sp, fontStyle = FontStyle.Italic, color = Grey)
                }
            } else {
                Text("Palavra não encontrada no dicionário deste livro.", fontSize = 16.sp, color = Grey)
            }
            if (sentence.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text("“$sentence”", fontSize = 15.sp, fontFamily = FontFamily.Serif)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (entry != null) {
                    val savedW = app.isSaved(entry.key, book.meta.id)
                    EinkButton(if (savedW) "✓ Salva (remover)" else "Enviar para Vocabulário", filled = !savedW) {
                        if (savedW) app.removeVocab(entry.key, book.meta.id)
                        else app.addVocab(
                            VocabItem(
                                key = entry.key, word = entry.word, pt = entry.pt, def = entry.def,
                                cat = if (entry.cat == "verb" || entry.pos == "verb") "verb" else "word",
                                bookId = book.meta.id, bookTitle = book.meta.title, example = sentence,
                                chapter = t.chapter, offset = t.offset, added = System.currentTimeMillis(),
                            )
                        )
                    }
                }
                EinkButton("Ver imagem") {
                    val q = entry?.img ?: expressions.firstOrNull()?.text ?: entry?.word ?: surface
                    searchImage(q, entry?.pos == "name" || entry?.img != null)
                }
            }
            when (val st = img) {
                ImgState.None -> {}
                ImgState.Loading -> {
                    Spacer(Modifier.height(10.dp)); Text("Procurando imagem...", fontSize = 15.sp)
                }
                ImgState.NotFound -> {
                    Spacer(Modifier.height(10.dp))
                    Text("Nenhuma imagem encontrada. Verifique o Wi-Fi ou tente outra palavra.", fontSize = 15.sp, color = Grey)
                }
                is ImgState.Shown -> {
                    Spacer(Modifier.height(10.dp))
                    Image(
                        st.bitmap.asImageBitmap(), contentDescription = surface,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).border(1.dp, Ink),
                        contentScale = ContentScale.Fit,
                    )
                    Text("Fonte: ${st.source}", fontSize = 12.sp, color = Grey)
                }
            }

            for (g in grammar) {
                Spacer(Modifier.height(14.dp))
                Divider()
                Spacer(Modifier.height(10.dp))
                Text("Gramática · ${g.level}", fontSize = 13.sp, color = Grey)
                Text(g.titlePt, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(g.form, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(g.explanation, fontSize = 15.sp, maxLines = 4)
                Spacer(Modifier.height(6.dp))
                EinkButton("Ver em Gramática") { onGrammar(g.id) }
            }

            Spacer(Modifier.height(16.dp))
            EinkButton("Fechar", Modifier.fillMaxWidth()) { onClose() }
        }
    }
}
