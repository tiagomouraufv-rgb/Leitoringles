package com.tiagomoura.leitoringles.data

/** Levels shown as shelves in the library, in this order. */
val LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")

val LEVEL_NAMES = mapOf(
    "A1" to "Iniciante",
    "A2" to "Básico",
    "B1" to "Intermediário",
    "B2" to "Intermediário superior",
    "C1" to "Avançado",
    "C2" to "Proficiente",
)

data class Paragraph(val text: String, val style: String)

data class Chapter(val title: String, val paragraphs: List<Paragraph>) {
    /** Text of the whole chapter, paragraphs separated by a blank line. */
    val text: String
    /** Start offset of each paragraph inside [text]. */
    val paragraphStarts: IntArray

    init {
        val sb = StringBuilder()
        val starts = IntArray(paragraphs.size)
        paragraphs.forEachIndexed { i, p ->
            if (i > 0) sb.append("\n\n")
            starts[i] = sb.length
            sb.append(p.text)
        }
        text = sb.toString()
        paragraphStarts = starts
    }

    /** Converts a chapter offset into (paragraph index, offset inside paragraph). */
    fun locate(offset: Int): Pair<Int, Int> {
        var p = 0
        for (i in paragraphStarts.indices) {
            if (paragraphStarts[i] <= offset) p = i else break
        }
        return p to (offset - paragraphStarts[p]).coerceAtLeast(0)
    }

    fun offsetOf(paragraph: Int, inPara: Int): Int =
        if (paragraph in paragraphStarts.indices) paragraphStarts[paragraph] + inPara else 0
}

data class Entry(
    val key: String,
    val word: String,
    val pt: String,
    val def: String?,
    val pos: String,
    val cat: String,
    val img: String?,
)

/** An occurrence inside a book: chapter, paragraph and character span inside the paragraph. */
data class Occurrence(
    val chapter: Int,
    val paragraph: Int,
    val start: Int,
    val end: Int,
    val sentStart: Int = -1,
    val sentEnd: Int = -1,
)

data class Expression(
    val id: String,
    val text: String,
    val pt: String,
    val def: String?,
    val type: String,
    val occ: List<Occurrence>,
)

data class GrammarTopic(
    val id: String,
    val order: Int,
    val level: String,
    val title: String,
    val titlePt: String,
    val form: String,
    val explanation: String,
    val occ: List<Occurrence>,
)

/** Basic information about a book, used by the library without loading the whole text. */
data class BookMeta(
    val id: String,
    val title: String,
    val author: String,
    val year: Int,
    val level: String,
    val description: String,
    val chapterLengths: List<Int>,
    val source: String, // "asset:<name>" or "file:<path>"
) {
    val totalChars: Int get() = chapterLengths.sum().coerceAtLeast(1)

    fun globalOffset(chapter: Int, offset: Int): Int {
        var g = 0
        for (i in 0 until chapter.coerceAtMost(chapterLengths.size)) g += chapterLengths[i]
        return g + offset
    }

    fun percent(chapter: Int, offset: Int): Int =
        ((globalOffset(chapter, offset).toDouble() / totalChars) * 100).toInt().coerceIn(0, 100)
}

class Book(
    val meta: BookMeta,
    val chapters: List<Chapter>,
    val forms: Map<String, String>,
    val entries: Map<String, Entry>,
    val expressions: List<Expression>,
    val grammar: List<GrammarTopic>,
) {
    /** Expressions indexed by chapter, for quick lookup in the reader. */
    val expressionsByChapter: Map<Int, List<Pair<Expression, Occurrence>>> by lazy {
        val m = HashMap<Int, MutableList<Pair<Expression, Occurrence>>>()
        for (e in expressions) for (o in e.occ) m.getOrPut(o.chapter) { mutableListOf() }.add(e to o)
        m
    }

    val grammarByChapter: Map<Int, List<Pair<GrammarTopic, Occurrence>>> by lazy {
        val m = HashMap<Int, MutableList<Pair<GrammarTopic, Occurrence>>>()
        for (g in grammar) for (o in g.occ) m.getOrPut(o.chapter) { mutableListOf() }.add(g to o)
        m
    }

    fun lookup(surface: String): Entry? {
        val key = forms[surface] ?: forms[surface.lowercase()]
            ?: forms[surface.replace('’', '\'')]
            ?: forms[surface.replaceFirstChar { it.uppercase() }]
        if (key != null) entries[key]?.let { return it }
        val l = surface.lowercase()
        return entries["W:$l"] ?: entries["N:$surface"]
            ?: (if (l.endsWith("s")) entries["W:${l.dropLast(1)}"] else null)
    }
}

/** Saved state of reading for one book. */
data class Progress(
    val chapter: Int = 0,
    val offset: Int = 0,
    val maxChapter: Int = 0,
    val maxOffset: Int = 0,
    val updated: Long = 0L,
) {
    fun isBeyond(chapter: Int, offset: Int): Boolean =
        maxChapter > chapter || (maxChapter == chapter && maxOffset >= offset)
}

/** A word or expression the reader sent to the vocabulary tab. */
data class VocabItem(
    val key: String,
    val word: String,
    val pt: String,
    val def: String?,
    val cat: String, // "verb", "expression", "word"
    val bookId: String,
    val bookTitle: String,
    val example: String,
    val chapter: Int,
    val offset: Int,
    val added: Long,
)

data class Settings(
    val fontSize: Int = 20,
    val lineSpacing: Float = 1.45f,
    val showMarks: Boolean = true,
    val fullRefreshEvery: Int = 8,
    val colorMode: String = "auto", // auto, color, mono
    val braveKey: String = "",
    val margin: Int = 24,
)
