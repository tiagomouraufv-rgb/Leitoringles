package com.tiagomoura.leitoringles.ui

/** Helpers to find words and sentences in a text. The same word pattern is used to build the books. */
object TextTools {
    val WORD = Regex("[A-Za-zÀ-ÖØ-öø-ÿ]+(?:['’][A-Za-z]+)?")

    /** Returns the start and end of the word at [offset], or null if there is no word there. */
    fun wordAt(text: String, offset: Int): IntRange? {
        if (text.isEmpty()) return null
        val from = (offset - 40).coerceAtLeast(0)
        val to = (offset + 40).coerceAtMost(text.length)
        for (m in WORD.findAll(text.substring(from, to))) {
            val s = from + m.range.first
            val e = from + m.range.last + 1
            if (offset in s until e) return s until e
            if (s > offset) break
        }
        return null
    }

    /** Returns the sentence that contains [offset]. */
    fun sentenceAround(text: String, offset: Int): String {
        if (text.isEmpty()) return ""
        val o = offset.coerceIn(0, text.length - 1)
        var s = o
        while (s > 0) {
            val c = text[s - 1]
            if (c == '\n') break
            if ((c == '.' || c == '!' || c == '?') && s < text.length && text[s].isWhitespace()) break
            s--
        }
        var e = o
        while (e < text.length) {
            val c = text[e]
            if (c == '\n') break
            e++
            if ((c == '.' || c == '!' || c == '?') && (e >= text.length || text[e].isWhitespace() || text[e] == '"' || text[e] == '”')) {
                if (e < text.length && (text[e] == '"' || text[e] == '”')) e++
                break
            }
        }
        return text.substring(s, e).trim()
    }

    val POS_PT = mapOf(
        "noun" to "substantivo", "verb" to "verbo", "adj" to "adjetivo", "adv" to "advérbio",
        "name" to "nome próprio", "pron" to "pronome", "prep" to "preposição", "conj" to "conjunção",
        "det" to "determinante", "interj" to "interjeição", "part" to "partícula", "other" to "",
    )
}
