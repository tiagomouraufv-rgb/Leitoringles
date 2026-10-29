package com.tiagomoura.leitoringles.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Everything the app saves lives in plain JSON files in the app's folder:
 * reading positions, vocabulary, settings and the library index.
 */
class Storage(context: Context) {
    val root: File = (context.getExternalFilesDir(null) ?: context.filesDir).also { it.mkdirs() }
    val booksDir = File(root, "books").also { it.mkdirs() }
    val imageCacheDir = File(root, "imgcache").also { it.mkdirs() }
    private val progressDir = File(root, "progress").also { it.mkdirs() }
    private val vocabFile = File(root, "vocabulary.json")
    private val settingsFile = File(root, "settings.json")
    private val stateFile = File(root, "state.json")
    val indexFile = File(root, "library_index.json")

    private fun writeAtomic(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.writeText(text, Charsets.UTF_8)
            tmp.delete()
        }
    }

    private fun readJson(file: File): JSONObject? = try {
        if (file.exists()) JSONObject(file.readText(Charsets.UTF_8)) else null
    } catch (e: Exception) {
        null
    }

    // ---------- progress ----------
    private fun progressFile(bookId: String) = File(progressDir, bookId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    fun loadProgress(bookId: String): Progress {
        val o = readJson(progressFile(bookId)) ?: return Progress()
        return Progress(
            chapter = o.optInt("chapter"),
            offset = o.optInt("offset"),
            maxChapter = o.optInt("maxChapter"),
            maxOffset = o.optInt("maxOffset"),
            updated = o.optLong("updated"),
        )
    }

    fun saveProgress(bookId: String, p: Progress) {
        val o = JSONObject()
            .put("chapter", p.chapter).put("offset", p.offset)
            .put("maxChapter", p.maxChapter).put("maxOffset", p.maxOffset)
            .put("updated", p.updated)
        writeAtomic(progressFile(bookId), o.toString())
    }

    fun deleteProgress(bookId: String) {
        progressFile(bookId).delete()
    }

    // ---------- state ----------
    fun lastBookId(): String? = readJson(stateFile)?.optString("lastBook")?.ifBlank { null }

    fun setLastBookId(id: String?) {
        writeAtomic(stateFile, JSONObject().put("lastBook", id ?: "").toString())
    }

    // ---------- settings ----------
    fun loadSettings(): Settings {
        val o = readJson(settingsFile) ?: return Settings()
        val d = Settings()
        return Settings(
            fontSize = o.optInt("fontSize", d.fontSize),
            lineSpacing = o.optDouble("lineSpacing", d.lineSpacing.toDouble()).toFloat(),
            showMarks = o.optBoolean("showMarks", d.showMarks),
            fullRefreshEvery = o.optInt("fullRefreshEvery", d.fullRefreshEvery),
            colorMode = o.optString("colorMode", d.colorMode),
            braveKey = o.optString("braveKey", ""),
            margin = o.optInt("margin", d.margin),
            tapTurnsPage = o.optBoolean("tapTurnsPage", d.tapTurnsPage),
        )
    }

    fun saveSettings(s: Settings) {
        val o = JSONObject()
            .put("fontSize", s.fontSize).put("lineSpacing", s.lineSpacing.toDouble())
            .put("showMarks", s.showMarks).put("fullRefreshEvery", s.fullRefreshEvery)
            .put("colorMode", s.colorMode).put("braveKey", s.braveKey).put("margin", s.margin)
            .put("tapTurnsPage", s.tapTurnsPage)
        writeAtomic(settingsFile, o.toString(2))
    }

    // ---------- vocabulary ----------
    fun loadVocabulary(): List<VocabItem> {
        if (!vocabFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(vocabFile.readText(Charsets.UTF_8))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                VocabItem(
                    key = o.optString("key"),
                    word = o.optString("word"),
                    pt = o.optString("pt"),
                    def = o.optString("def").ifBlank { null },
                    cat = o.optString("cat", "word"),
                    bookId = o.optString("bookId"),
                    bookTitle = o.optString("bookTitle"),
                    example = o.optString("example"),
                    chapter = o.optInt("chapter"),
                    offset = o.optInt("offset"),
                    added = o.optLong("added"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveVocabulary(items: List<VocabItem>) {
        val arr = JSONArray()
        for (v in items) {
            arr.put(
                JSONObject()
                    .put("key", v.key).put("word", v.word).put("pt", v.pt).put("def", v.def ?: "")
                    .put("cat", v.cat).put("bookId", v.bookId).put("bookTitle", v.bookTitle)
                    .put("example", v.example).put("chapter", v.chapter).put("offset", v.offset)
                    .put("added", v.added)
            )
        }
        writeAtomic(vocabFile, arr.toString(1))
    }

    // ---------- library index ----------
    fun loadIndex(): List<BookMeta> {
        val o = readJson(indexFile) ?: return emptyList()
        val arr = o.optJSONArray("books") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val b = arr.getJSONObject(i)
            val lens = b.optJSONArray("chapterLengths") ?: JSONArray()
            BookMeta(
                id = b.getString("id"),
                title = b.getString("title"),
                author = b.optString("author"),
                year = b.optInt("year"),
                level = b.optString("level"),
                description = b.optString("description"),
                chapterLengths = (0 until lens.length()).map { lens.getInt(it) },
                source = b.getString("source"),
            )
        }
    }

    fun saveIndex(books: List<BookMeta>, signature: String) {
        val arr = JSONArray()
        for (b in books) {
            arr.put(
                JSONObject().put("id", b.id).put("title", b.title).put("author", b.author)
                    .put("year", b.year).put("level", b.level).put("description", b.description)
                    .put("chapterLengths", JSONArray(b.chapterLengths)).put("source", b.source)
            )
        }
        writeAtomic(indexFile, JSONObject().put("signature", signature).put("books", arr).toString())
    }

    fun indexSignature(): String? = readJson(indexFile)?.optString("signature")
}
