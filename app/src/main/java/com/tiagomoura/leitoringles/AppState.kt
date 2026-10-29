package com.tiagomoura.leitoringles

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tiagomoura.leitoringles.data.Book
import com.tiagomoura.leitoringles.data.BookMeta
import com.tiagomoura.leitoringles.data.ImageSearch
import com.tiagomoura.leitoringles.data.Library
import com.tiagomoura.leitoringles.data.Progress
import com.tiagomoura.leitoringles.data.Settings
import com.tiagomoura.leitoringles.data.Storage
import com.tiagomoura.leitoringles.data.VocabItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Screen {
    data object Library : Screen()
    data class Reader(val bookId: String, val chapter: Int? = null, val offset: Int? = null) : Screen()
    data object Grammar : Screen()
    data object Vocabulary : Screen()
    data object AddBook : Screen()
    data object Settings : Screen()
}

/** Holds the whole state of the app. There is only one instance, created by MainActivity. */
class AppState(context: Context) {
    val storage = Storage(context)
    val library = Library(context, storage)
    val images = ImageSearch(storage.imageCacheDir)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var screen by mutableStateOf<Screen>(Screen.Library)
    var books by mutableStateOf<List<BookMeta>>(emptyList())
    var loadingLibrary by mutableStateOf(true)
    var settings by mutableStateOf(storage.loadSettings())
    var lastBookId by mutableStateOf(storage.lastBookId())
    val vocabulary = mutableStateListOf<VocabItem>().also { it.addAll(storage.loadVocabulary()) }
    val progress = mutableStateMapOf<String, Progress>()
    var message by mutableStateOf<String?>(null)
    var grammarFocus by mutableStateOf<String?>(null)

    /** Set by the reader to receive the page-turn keys (volume and page buttons). */
    var keyHandler: ((Int) -> Boolean)? = null

    /** True on screens that can show colour (Boox Go Color, Tab, phones), unless the user chose otherwise. */
    val colorScreen: Boolean
        get() = when (settings.colorMode) {
            "color" -> true
            "mono" -> false
            else -> {
                val maker = (Build.MANUFACTURER ?: "").lowercase()
                val model = (Build.MODEL ?: "").lowercase()
                val isOnyx = maker.contains("onyx") || Build.BRAND.lowercase().contains("onyx") || model.contains("boox")
                if (isOnyx) {
                    model.contains("color") || model.contains("colour") || model.endsWith("c") ||
                        Regex("(^|[^a-z])c( |$|pro)").containsMatchIn(model)
                } else true
            }
        }

    fun refreshLibrary() {
        loadingLibrary = true
        scope.launch {
            val list = withContext(Dispatchers.IO) { library.index() }
            books = list
            for (b in list) progress[b.id] = withContext(Dispatchers.IO) { storage.loadProgress(b.id) }
            loadingLibrary = false
        }
    }

    fun meta(id: String): BookMeta? = books.firstOrNull { it.id == id }

    suspend fun loadBook(id: String): Book? {
        val m = meta(id) ?: return null
        return withContext(Dispatchers.IO) { library.load(m) }
    }

    fun updateSettings(s: Settings) {
        settings = s
        scope.launch(Dispatchers.IO) { storage.saveSettings(s) }
    }

    fun saveProgress(bookId: String, chapter: Int, offset: Int) {
        val old = progress[bookId] ?: Progress()
        val beyond = old.isBeyond(chapter, offset)
        val p = Progress(
            chapter = chapter,
            offset = offset,
            maxChapter = if (beyond) old.maxChapter else chapter,
            maxOffset = if (beyond) old.maxOffset else offset,
            updated = System.currentTimeMillis(),
        )
        progress[bookId] = p
        if (lastBookId != bookId) {
            lastBookId = bookId
            scope.launch(Dispatchers.IO) { storage.setLastBookId(bookId) }
        }
        scope.launch(Dispatchers.IO) { storage.saveProgress(bookId, p) }
    }

    fun isSaved(key: String, bookId: String): Boolean = vocabulary.any { it.key == key && it.bookId == bookId }

    fun addVocab(item: VocabItem) {
        if (vocabulary.any { it.key == item.key && it.bookId == item.bookId }) return
        vocabulary.add(item)
        val copy = vocabulary.toList()
        scope.launch(Dispatchers.IO) { storage.saveVocabulary(copy) }
    }

    fun removeVocab(key: String, bookId: String) {
        vocabulary.removeAll { it.key == key && it.bookId == bookId }
        val copy = vocabulary.toList()
        scope.launch(Dispatchers.IO) { storage.saveVocabulary(copy) }
    }

    fun importBook(uri: Uri, onDone: (String?) -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val m = library.import(uri)
                    Result.success(m)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            result.onSuccess { m ->
                refreshLibrary()
                onDone("\"${m.title}\" foi adicionado à prateleira ${m.level}.")
            }.onFailure { e ->
                onDone("Não foi possível importar: ${e.message}")
            }
        }
    }

    fun removeBook(meta: BookMeta) {
        scope.launch {
            withContext(Dispatchers.IO) {
                library.remove(meta)
                storage.deleteProgress(meta.id)
            }
            if (lastBookId == meta.id) {
                lastBookId = null
                withContext(Dispatchers.IO) { storage.setLastBookId(null) }
            }
            refreshLibrary()
        }
    }
}
