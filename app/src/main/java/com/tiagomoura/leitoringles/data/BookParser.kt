package com.tiagomoura.leitoringles.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

class BookFormatException(message: String) : Exception(message)

object BookParser {
    const val FORMAT = "leitoringles-book"
    const val MAX_VERSION = 1

    /** Reads the whole stream as UTF-8 text, transparently handling gzip files. */
    fun readText(input: InputStream): String {
        val buffered = BufferedInputStream(input)
        buffered.mark(4)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        val stream: InputStream = if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(buffered) else buffered
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun parseJson(text: String): JSONObject {
        val trimmed = text.trimStart('﻿', ' ', '\n', '\r', '\t')
        val obj = try {
            JSONObject(trimmed)
        } catch (e: Exception) {
            throw BookFormatException("O arquivo não é um JSON válido: ${e.message}")
        }
        val format = obj.optString("format")
        if (format != FORMAT) throw BookFormatException("Formato desconhecido (esperado \"$FORMAT\").")
        val version = obj.optInt("version", 0)
        if (version < 1 || version > MAX_VERSION) {
            throw BookFormatException("Versão $version do formato não é suportada por este app (máximo $MAX_VERSION). Atualize o app.")
        }
        if (obj.optString("id").isBlank()) throw BookFormatException("O livro não tem \"id\".")
        if (obj.optString("title").isBlank()) throw BookFormatException("O livro não tem \"title\".")
        val level = obj.optString("level").uppercase()
        if (level !in LEVELS) throw BookFormatException("Nível \"$level\" inválido (use A1, A2, B1, B2, C1 ou C2).")
        val ch = obj.optJSONArray("chapters")
        if (ch == null || ch.length() == 0) throw BookFormatException("O livro não tem capítulos.")
        return obj
    }

    fun parseChapters(obj: JSONObject): List<Chapter> {
        val arr = obj.getJSONArray("chapters")
        val out = ArrayList<Chapter>(arr.length())
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            val ps = c.optJSONArray("paragraphs") ?: JSONArray()
            val paras = ArrayList<Paragraph>(ps.length())
            for (j in 0 until ps.length()) {
                val p = ps.get(j)
                if (p is JSONObject) paras.add(Paragraph(p.optString("t"), p.optString("s", "p")))
                else paras.add(Paragraph(p.toString(), "p"))
            }
            out.add(Chapter(c.optString("title"), paras))
        }
        return out
    }

    fun metaFrom(obj: JSONObject, chapters: List<Chapter>, source: String): BookMeta = BookMeta(
        id = obj.getString("id"),
        title = obj.getString("title"),
        author = obj.optString("author"),
        year = obj.optInt("year", 0),
        level = obj.optString("level").uppercase(),
        description = obj.optString("description"),
        chapterLengths = chapters.map { it.text.length },
        source = source,
    )

    private fun occList(arr: JSONArray?): List<Occurrence> {
        if (arr == null) return emptyList()
        val out = ArrayList<Occurrence>(arr.length())
        for (i in 0 until arr.length()) {
            val a = arr.optJSONArray(i) ?: continue
            if (a.length() < 4) continue
            out.add(
                Occurrence(
                    a.getInt(0), a.getInt(1), a.getInt(2), a.getInt(3),
                    if (a.length() > 4) a.getInt(4) else -1,
                    if (a.length() > 5) a.getInt(5) else -1,
                )
            )
        }
        return out
    }

    fun parseBook(obj: JSONObject, source: String): Book {
        val chapters = parseChapters(obj)
        val meta = metaFrom(obj, chapters, source)

        val lex = obj.optJSONObject("lexicon") ?: JSONObject()
        val formsObj = lex.optJSONObject("forms") ?: JSONObject()
        val forms = HashMap<String, String>(formsObj.length() * 2)
        formsObj.keys().forEach { k -> forms[k] = formsObj.getString(k) }
        val entObj = lex.optJSONObject("entries") ?: JSONObject()
        val entries = HashMap<String, Entry>(entObj.length() * 2)
        entObj.keys().forEach { k ->
            val e = entObj.getJSONObject(k)
            entries[k] = Entry(
                key = k,
                word = e.optString("w", k.substringAfter(':')),
                pt = e.optString("pt"),
                def = e.optString("def").ifBlank { null },
                pos = e.optString("pos", "other"),
                cat = e.optString("cat", "word"),
                img = e.optString("img").ifBlank { null },
            )
        }

        val exArr = obj.optJSONArray("expressions") ?: JSONArray()
        val expressions = ArrayList<Expression>(exArr.length())
        for (i in 0 until exArr.length()) {
            val e = exArr.getJSONObject(i)
            expressions.add(
                Expression(
                    id = e.optString("id", "E:" + e.optString("text")),
                    text = e.optString("text"),
                    pt = e.optString("pt"),
                    def = e.optString("def").ifBlank { null },
                    type = e.optString("type", "expression"),
                    occ = occList(e.optJSONArray("occ")),
                )
            )
        }

        val grArr = obj.optJSONArray("grammar") ?: JSONArray()
        val grammar = ArrayList<GrammarTopic>(grArr.length())
        for (i in 0 until grArr.length()) {
            val g = grArr.getJSONObject(i)
            grammar.add(
                GrammarTopic(
                    id = g.optString("id"),
                    order = g.optInt("order", 999),
                    level = g.optString("level"),
                    title = g.optString("title"),
                    titlePt = g.optString("titlePt", g.optString("title")),
                    form = g.optString("form"),
                    explanation = g.optString("explanation"),
                    occ = occList(g.optJSONArray("occ")),
                )
            )
        }
        return Book(meta, chapters, forms, entries, expressions, grammar)
    }
}
