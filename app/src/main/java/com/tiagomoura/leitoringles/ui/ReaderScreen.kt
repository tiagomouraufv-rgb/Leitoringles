package com.tiagomoura.leitoringles.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tiagomoura.leitoringles.AppState
import com.tiagomoura.leitoringles.Screen
import com.tiagomoura.leitoringles.data.Book
import com.tiagomoura.leitoringles.data.Chapter
import kotlinx.coroutines.delay

/** What the user tapped on a page. Offsets are inside the chapter text. */
data class TapTarget(val chapter: Int, val start: Int, val end: Int, val offset: Int)

private class Pages(val layout: TextLayoutResult, val firstLines: List<Int>, val base: Int) {
    val count get() = firstLines.size
    fun firstLine(page: Int) = firstLines[page.coerceIn(0, firstLines.size - 1)]
    fun lastLine(page: Int) =
        if (page + 1 < firstLines.size) firstLines[page + 1] - 1 else layout.lineCount - 1
    fun top(page: Int) = layout.getLineTop(firstLine(page))
    fun bottom(page: Int) = layout.getLineBottom(lastLine(page))
    fun startOffset(page: Int) = layout.getLineStart(firstLine(page))
    fun pageOf(offset: Int): Int {
        var p = 0
        for (i in firstLines.indices) if (layout.getLineStart(firstLines[i]) <= offset) p = i else break
        return p
    }
}

@Composable
fun ReaderScreen(app: AppState, target: Screen.Reader) {
    var book by remember(target.bookId) { mutableStateOf<Book?>(null) }
    var error by remember(target.bookId) { mutableStateOf<String?>(null) }
    LaunchedEffect(target.bookId) {
        try {
            book = app.loadBook(target.bookId) ?: run { error = "Livro não encontrado."; null }
        } catch (e: Exception) {
            error = "Não foi possível abrir o livro: ${e.message}"
        }
    }
    BackHandler { app.screen = Screen.Library }
    val b = book
    when {
        error != null -> Column(Modifier.fillMaxSize().background(Paper)) {
            TopBar("Erro", onBack = { app.screen = Screen.Library })
            Text(error!!, Modifier.padding(20.dp), fontSize = 18.sp)
        }
        b == null -> Box(Modifier.fillMaxSize().background(Paper), contentAlignment = Alignment.Center) {
            Text("Abrindo o livro...", fontSize = 20.sp)
        }
        else -> BookReader(app, b, target)
    }
}

@Composable
private fun BookReader(app: AppState, book: Book, target: Screen.Reader) {
    val saved = app.progress[book.meta.id]
    var chapterIndex by remember(book) {
        mutableIntStateOf((target.chapter ?: saved?.chapter ?: 0).coerceIn(0, book.chapters.size - 1))
    }
    // offset (inside chapter text) that the current page must contain
    var anchor by remember(book) { mutableIntStateOf(target.offset ?: saved?.offset ?: 0) }
    var wantLastPage by remember(book) { mutableStateOf(false) }
    var page by remember(book) { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf("") } // "", "menu", "text", "parts"
    var tap by remember { mutableStateOf<TapTarget?>(null) }
    var flash by remember { mutableStateOf(false) }
    var turns by remember { mutableIntStateOf(0) }
    val settings = app.settings
    val chapter = book.chapters[chapterIndex]
    val measurer = rememberTextMeasurer(cacheSize = 2)
    val density = LocalDensity.current
    val color = app.colorScreen

    LaunchedEffect(flash) {
        if (flash) {
            delay(110)
            flash = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Paper)) {
        val marginPx = with(density) { settings.margin.dp.toPx() }
        val topPx = with(density) { 52.dp.toPx() }
        val bottomPx = with(density) { 52.dp.toPx() }
        val widthPx = constraints.maxWidth - 2 * marginPx
        val heightPx = constraints.maxHeight - topPx - bottomPx
        val screenW = constraints.maxWidth.toFloat()

        val pages = remember(chapterIndex, settings.fontSize, settings.lineSpacing, settings.showMarks, settings.margin, constraints.maxWidth, constraints.maxHeight, book) {
            val text = buildChapterText(book, chapterIndex, chapter, settings.showMarks, color)
            val style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = settings.fontSize.sp,
                lineHeight = (settings.fontSize * settings.lineSpacing).sp,
                color = Ink,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            )
            val layout = measurer.measure(text.first, style, constraints = Constraints(maxWidth = widthPx.toInt().coerceAtLeast(100)))
            val first = ArrayList<Int>()
            var line = 0
            while (line < layout.lineCount) {
                first.add(line)
                val top = layout.getLineTop(line)
                var l = line
                while (l + 1 < layout.lineCount && layout.getLineBottom(l + 1) - top <= heightPx) l++
                line = l + 1
            }
            if (first.isEmpty()) first.add(0)
            Pages(layout, first, text.second)
        }

        // keep the requested position visible after a new layout
        LaunchedEffect(pages) {
            page = if (wantLastPage) pages.count - 1 else pages.pageOf(pages.base + anchor)
            wantLastPage = false
            anchor = (pages.startOffset(page) - pages.base).coerceAtLeast(0)
            app.saveProgress(book.meta.id, chapterIndex, anchor)
        }

        fun registerTurn() {
            turns++
            val n = settings.fullRefreshEvery
            if (n > 0 && turns % n == 0) flash = true
        }

        fun goTo(newPage: Int) {
            page = newPage
            anchor = (pages.startOffset(page) - pages.base).coerceAtLeast(0)
            registerTurn()
            app.saveProgress(book.meta.id, chapterIndex, anchor)
        }

        fun next() {
            if (page + 1 < pages.count) goTo(page + 1)
            else if (chapterIndex + 1 < book.chapters.size) {
                chapterIndex += 1; anchor = 0; registerTurn()
            }
        }

        fun prev() {
            if (page > 0) goTo(page - 1)
            else if (chapterIndex > 0) {
                chapterIndex -= 1; wantLastPage = true; registerTurn()
            }
        }

        val pageNow = page.coerceIn(0, pages.count - 1)

        /** Finds the word under a point of the screen. */
        fun hitTest(pos: Offset): TapTarget? {
            val textY = pos.y - topPx
            if (textY < 0) return null
            val yIn = textY + pages.top(pageNow)
            if (yIn > pages.bottom(pageNow)) return null
            val xIn = pos.x - marginPx
            if (xIn < -8f || xIn > widthPx + 8f) return null
            val off = pages.layout.getOffsetForPosition(Offset(xIn.coerceIn(0f, widthPx), yIn))
            val inChapter = off - pages.base
            if (inChapter < 0) return null
            val txt = book.chapters[chapterIndex].text
            val r = TextTools.wordAt(txt, inChapter)
                ?: TextTools.wordAt(txt, (inChapter - 1).coerceAtLeast(0))
                ?: return null
            return TapTarget(chapterIndex, r.first, r.last + 1, inChapter)
        }

        fun handleTap(pos: Offset, long: Boolean) {
            if (panel.isNotEmpty() || tap != null) return
            if (settings.tapTurnsPage && !long) {
                if (pos.x < screenW * 0.35f) prev() else next()
                return
            }
            val hit = hitTest(pos)
            when {
                hit != null -> tap = hit
                long -> {}
                pos.x < screenW * 0.25f -> prev()
                pos.x > screenW * 0.75f -> next()
                else -> panel = "menu"
            }
        }

        val tapState = rememberUpdatedState(::handleTap)
        val nextState = rememberUpdatedState(::next)
        val prevState = rememberUpdatedState(::prev)
        DisposableEffect(Unit) {
            app.keyHandler = { code ->
                when (code) {
                    KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_SPACE -> { nextState.value(); true }
                    KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_UP -> { prevState.value(); true }
                    else -> false
                }
            }
            onDispose { app.keyHandler = null }
        }

        val swipePx = with(density) { 70.dp.toPx() }
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tapState.value(it, false) },
                        onLongPress = { tapState.value(it, true) },
                    )
                }
                .pointerInput(Unit) {
                    var acc = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { acc = 0f },
                        onHorizontalDrag = { change, amount -> acc += amount; change.consume() },
                        onDragEnd = {
                            if (acc < -swipePx) nextState.value() else if (acc > swipePx) prevState.value()
                            acc = 0f
                        },
                    )
                }
        ) {
            val top = pages.top(pageNow)
            val bottom = pages.bottom(pageNow)
            clipRect(left = 0f, top = topPx, right = size.width, bottom = topPx + (bottom - top) + 1f) {
                translate(left = marginPx, top = topPx - top) {
                    drawText(pages.layout)
                }
            }
        }

        // fixed top bar: always reachable, even if taps on the text fail
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Paper)) {
            Row(
                Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.height(50.dp).tap { app.screen = Screen.Library }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Text("‹ Biblioteca", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    book.meta.title, fontSize = 15.sp, color = Grey, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp), textAlign = TextAlign.Center,
                )
                Box(Modifier.height(50.dp).tap { panel = if (panel == "text") "" else "text" }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text("Aa", fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                }
                Box(Modifier.height(50.dp).tap { panel = if (panel == "menu") "" else "menu" }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text("☰", fontSize = 24.sp)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(LightGrey))
        }

        // footer: page buttons and progress
        val pct = book.meta.percent(chapterIndex, (pages.startOffset(pageNow) - pages.base).coerceAtLeast(0))
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Paper)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(LightGrey))
            Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(72.dp).height(50.dp).tap { prev() }, contentAlignment = Alignment.Center) {
                    Text("‹", fontSize = 30.sp)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(chapter.title, fontSize = 12.sp, color = Grey, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("página ${pageNow + 1} de ${pages.count} · $pct%", fontSize = 12.sp, color = Grey)
                }
                Box(Modifier.width(72.dp).height(50.dp).tap { next() }, contentAlignment = Alignment.Center) {
                    Text("›", fontSize = 30.sp)
                }
            }
        }

        when (panel) {
            "menu" -> Overlay(onClose = { panel = "" }) {
                ReaderMenu(app, book, chapterIndex, pageNow, pages.count, pct,
                    onParts = { panel = "parts" }, onText = { panel = "text" }, onClose = { panel = "" })
            }
            "text" -> Overlay(onClose = { panel = "" }) { TextSettings(app, onClose = { panel = "" }) }
            "parts" -> Overlay(onClose = { panel = "" }) {
                PartsList(book, chapterIndex, onClose = { panel = "" }) { i ->
                    chapterIndex = i; anchor = 0; panel = ""
                }
            }
        }
        tap?.let { t ->
            Overlay(onClose = { tap = null }) {
                WordPopup(app, book, t, onClose = { tap = null }, onGrammar = { id ->
                    tap = null
                    app.grammarFocus = id
                    app.screen = Screen.Grammar
                })
            }
        }
        if (flash) Box(Modifier.fillMaxSize().background(Ink))
    }
}

/** Builds the styled text of a chapter. Returns the text and the offset where the chapter body starts. */
private fun buildChapterText(book: Book, index: Int, chapter: Chapter, marks: Boolean, color: Boolean): Pair<AnnotatedString, Int> {
    val exprBg = if (color) Color(0xFFFFF1B8) else Color(0xFFE4E4E4)
    var base = 0
    val s = buildAnnotatedString {
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 1.25.em))
        append(chapter.title)
        pop()
        append("\n\n")
        base = length
        chapter.paragraphs.forEachIndexed { i, p ->
            if (i > 0) append("\n\n")
            val start = length
            append(p.text)
            val end = length
            when (p.style) {
                "h" -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                "q" -> {
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                    addStyle(ParagraphStyle(textIndent = TextIndent(18.sp, 18.sp)), start, end)
                }
                "li" -> addStyle(ParagraphStyle(textIndent = TextIndent(0.sp, 14.sp)), start, end)
            }
        }
        if (marks) {
            book.expressionsByChapter[index]?.forEach { (_, o) ->
                val a = base + chapter.offsetOf(o.paragraph, o.start)
                val b = base + chapter.offsetOf(o.paragraph, o.end)
                if (a in 0 until b && b <= length) addStyle(SpanStyle(background = exprBg), a, b)
            }
            book.grammarByChapter[index]?.forEach { (g, o) ->
                if (g.id == "be_present" || g.id == "be_past" || g.id == "present_simple") return@forEach
                val a = base + chapter.offsetOf(o.paragraph, o.start)
                val b = base + chapter.offsetOf(o.paragraph, o.end)
                if (a in 0 until b && b <= length) addStyle(SpanStyle(textDecoration = TextDecoration.Underline), a, b)
            }
        }
    }
    return s to base
}


@Composable
private fun ReaderMenu(
    app: AppState, book: Book, chapter: Int, page: Int, pageCount: Int, pct: Int,
    onParts: () -> Unit, onText: () -> Unit, onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text(book.meta.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
        Text(
            "Parte ${chapter + 1} de ${book.chapters.size} · página ${page + 1} de $pageCount · $pct% do livro",
            fontSize = 15.sp, color = Grey,
        )
        Spacer(Modifier.height(14.dp))
        MenuRow("Partes do livro") { onParts() }
        MenuRow("Texto: tamanho, espaço e margens") { onText() }
        MenuRow(if (app.settings.showMarks) "Marcas de expressões e gramática: ligadas" else "Marcas de expressões e gramática: desligadas") {
            app.updateSettings(app.settings.copy(showMarks = !app.settings.showMarks))
        }
        MenuRow("Gramática") { app.screen = Screen.Grammar }
        MenuRow("Vocabulário") { app.screen = Screen.Vocabulary }
        MenuRow("Biblioteca") { app.screen = Screen.Library }
        Spacer(Modifier.height(10.dp))
        Text(
            "Toque numa palavra para ver a tradução. Para virar a página: toque nas bordas da tela, deslize, use as setas ‹ › embaixo ou os botões do aparelho. " +
                "Fundo cinza = expressão · sublinhado = ponto de gramática.",
            fontSize = 14.sp, color = Grey,
        )
        Spacer(Modifier.height(12.dp))
        EinkButton("Fechar", Modifier.fillMaxWidth()) { onClose() }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().tap(onClick)) {
        Text(label, fontSize = 18.sp, modifier = Modifier.padding(vertical = 14.dp))
        Divider()
    }
}

@Composable
private fun PartsList(book: Book, current: Int, onClose: () -> Unit, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("Partes do livro", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
        LazyColumn(state = state, modifier = Modifier.weight(1f, fill = false)) {
            itemsIndexed(book.chapters) { i, c ->
                Text(
                    c.title.ifBlank { "Parte ${i + 1}" },
                    Modifier.fillMaxWidth().tap { onPick(i) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    fontSize = 18.sp,
                    fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal,
                )
                Divider()
            }
        }
        Spacer(Modifier.height(8.dp))
        EinkButton("Fechar", Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { onClose() }
    }
}

/** Text settings shown inside the book. Changes apply at once, so the reader sees the result behind the panel. */
@Composable
fun TextSettings(app: AppState, onClose: () -> Unit) {
    val s = app.settings
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Texto", fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
        Spacer(Modifier.height(4.dp))
        Text("Exemplo de texto: Call me Ishmael.", fontSize = s.fontSize.sp, fontFamily = FontFamily.Serif, lineHeight = (s.fontSize * s.lineSpacing).sp)
        Spacer(Modifier.height(10.dp))
        StepRow("Tamanho da letra", "${s.fontSize}",
            onMinus = { app.updateSettings(s.copy(fontSize = (s.fontSize - 2).coerceAtLeast(14))) },
            onPlus = { app.updateSettings(s.copy(fontSize = (s.fontSize + 2).coerceAtMost(48))) })
        StepRow("Espaço entre linhas", String.format("%.1f", s.lineSpacing),
            onMinus = { app.updateSettings(s.copy(lineSpacing = (s.lineSpacing - 0.1f).coerceAtLeast(1.1f))) },
            onPlus = { app.updateSettings(s.copy(lineSpacing = (s.lineSpacing + 0.1f).coerceAtMost(2.4f))) })
        StepRow("Margens", "${s.margin}",
            onMinus = { app.updateSettings(s.copy(margin = (s.margin - 4).coerceAtLeast(8))) },
            onPlus = { app.updateSettings(s.copy(margin = (s.margin + 4).coerceAtMost(64))) })
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Marcas no texto", fontSize = 17.sp, modifier = Modifier.weight(1f))
            EinkButton(if (s.showMarks) "Ligadas" else "Desligadas", filled = s.showMarks) {
                app.updateSettings(s.copy(showMarks = !s.showMarks))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Toque no texto", fontSize = 17.sp, modifier = Modifier.weight(1f))
            EinkButton(if (s.tapTurnsPage) "Vira a página" else "Traduz a palavra", filled = true) {
                app.updateSettings(s.copy(tapTurnsPage = !s.tapTurnsPage))
            }
        }
        if (s.tapTurnsPage) Text("Com \"Vira a página\", toque e segure numa palavra para traduzi-la.", fontSize = 14.sp, color = Grey)
        Text("Atualização completa da tela a cada (páginas):", fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (n in listOf(0, 4, 8, 12, 20)) {
                EinkButton(if (n == 0) "nunca" else "$n", Modifier.weight(1f), filled = s.fullRefreshEvery == n) {
                    app.updateSettings(s.copy(fullRefreshEvery = n))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        EinkButton("Fechar", Modifier.fillMaxWidth(), filled = true) { onClose() }
    }
}

@Composable
fun StepRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 17.sp, modifier = Modifier.weight(1f))
        EinkButton("  −  ") { onMinus() }
        Text(value, fontSize = 17.sp, modifier = Modifier.width(56.dp).padding(horizontal = 8.dp), maxLines = 1, textAlign = TextAlign.Center)
        EinkButton("  +  ") { onPlus() }
    }
}
