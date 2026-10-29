package com.tiagomoura.leitoringles.data

import android.content.Context
import android.net.Uri
import java.io.File

/** Finds, indexes, loads and imports books. */
class Library(private val context: Context, private val storage: Storage) {
    private val cache = LinkedHashMap<String, Book>()

    private fun assetNames(): List<String> =
        (context.assets.list("books") ?: emptyArray()).filter { it.endsWith(".json") || it.endsWith(".gz") }.sorted()

    private fun fileBooks(): List<File> =
        (storage.booksDir.listFiles() ?: emptyArray()).filter { it.isFile && (it.name.endsWith(".json") || it.name.endsWith(".gz")) }
            .sortedBy { it.name }

    private fun signature(): String {
        val version = try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            pi.lastUpdateTime.toString()
        } catch (e: Exception) {
            "0"
        }
        val a = assetNames().joinToString(",")
        val f = fileBooks().joinToString(",") { it.name + ":" + it.length() + ":" + it.lastModified() }
        return "$version|$a|$f"
    }

    private fun openSource(source: String): String = when {
        source.startsWith("asset:") -> context.assets.open("books/" + source.removePrefix("asset:")).use { BookParser.readText(it) }
        source.startsWith("file:") -> File(source.removePrefix("file:")).inputStream().use { BookParser.readText(it) }
        else -> throw BookFormatException("Origem desconhecida: $source")
    }

    /** Returns all books. Imported books replace bundled ones with the same id. */
    fun index(): List<BookMeta> {
        val sig = signature()
        if (storage.indexSignature() == sig) {
            val cached = storage.loadIndex()
            if (cached.isNotEmpty() || (assetNames().isEmpty() && fileBooks().isEmpty())) return cached
        }
        val byId = LinkedHashMap<String, BookMeta>()
        for (name in assetNames()) {
            try {
                val obj = BookParser.parseJson(openSource("asset:$name"))
                val chapters = BookParser.parseChapters(obj)
                val m = BookParser.metaFrom(obj, chapters, "asset:$name")
                byId[m.id] = m
            } catch (e: Exception) {
                // a broken bundled book is skipped
            }
        }
        for (f in fileBooks()) {
            try {
                val obj = BookParser.parseJson(openSource("file:" + f.absolutePath))
                val chapters = BookParser.parseChapters(obj)
                val m = BookParser.metaFrom(obj, chapters, "file:" + f.absolutePath)
                byId[m.id] = m
            } catch (e: Exception) {
                // skip
            }
        }
        val list = byId.values.toList()
        storage.saveIndex(list, sig)
        return list
    }

    @Synchronized
    fun load(meta: BookMeta): Book {
        cache[meta.id]?.let { if (it.meta.source == meta.source) return it }
        val obj = BookParser.parseJson(openSource(meta.source))
        val book = BookParser.parseBook(obj, meta.source)
        cache[meta.id] = book
        while (cache.size > 4) cache.remove(cache.keys.first())
        return book
    }

    /** Imports a book file chosen by the user. Returns the book's information. */
    fun import(uri: Uri): BookMeta {
        val text = context.contentResolver.openInputStream(uri)?.use { BookParser.readText(it) }
            ?: throw BookFormatException("Não foi possível abrir o arquivo.")
        val obj = BookParser.parseJson(text)
        val book = BookParser.parseBook(obj, "file:")
        if (book.chapters.all { it.text.isBlank() }) throw BookFormatException("Os capítulos estão vazios.")
        val safeId = book.meta.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val target = File(storage.booksDir, "$safeId.lib.json")
        target.writeText(text, Charsets.UTF_8)
        cache.remove(book.meta.id)
        return book.meta.copy(source = "file:" + target.absolutePath)
    }

    fun remove(meta: BookMeta): Boolean {
        if (!meta.source.startsWith("file:")) return false
        cache.remove(meta.id)
        return File(meta.source.removePrefix("file:")).delete()
    }
}
