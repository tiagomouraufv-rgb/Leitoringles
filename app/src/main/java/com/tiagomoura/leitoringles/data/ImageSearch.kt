package com.tiagomoura.leitoringles.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Finds a picture for a word or expression.
 * Order: Wikipedia (exact page, then search), Wikimedia Commons, and Brave image search if a key is set.
 */
class ImageSearch(private val cacheDir: File) {
    private val userAgent = "LeitorIngles/1.0 (personal e-ink reading app; github.com/tiagomouraufv-rgb/Leitoringles)"

    data class Result(val bitmap: Bitmap, val source: String)

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun get(url: String, headers: Map<String, String> = emptyMap()): String? {
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("User-Agent", userAgent)
            c.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (c.responseCode !in 200..299) {
                c.disconnect(); null
            } else c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
        } catch (e: Exception) {
            null
        }
    }

    private fun downloadBitmap(url: String): Bitmap? {
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 20000
            c.setRequestProperty("User-Agent", userAgent)
            if (c.responseCode !in 200..299) return null
            val bytes = c.inputStream.use { it.readBytes() }
            c.disconnect()
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            var sample = 1
            while (opts.outWidth / sample > 900 || opts.outHeight / sample > 900) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun wikipediaExact(q: String): String? {
        val title = q.trim().replace(' ', '_')
        val txt = get("https://en.wikipedia.org/api/rest_v1/page/summary/" + enc(title).replace("+", "%20")) ?: return null
        return try {
            val o = JSONObject(txt)
            if (o.optString("type") == "disambiguation") null
            else o.optJSONObject("thumbnail")?.optString("source")?.ifBlank { null }
                ?: o.optJSONObject("originalimage")?.optString("source")?.ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    private fun wikipediaSearch(q: String): String? {
        val url = "https://en.wikipedia.org/w/api.php?action=query&format=json&generator=search&gsrlimit=6" +
            "&prop=pageimages&piprop=thumbnail&pithumbsize=640&gsrsearch=" + enc(q)
        val txt = get(url) ?: return null
        return try {
            val pages = JSONObject(txt).optJSONObject("query")?.optJSONObject("pages") ?: return null
            val list = pages.keys().asSequence().map { pages.getJSONObject(it) }
                .sortedBy { it.optInt("index", 99) }.toList()
            list.firstNotNullOfOrNull { it.optJSONObject("thumbnail")?.optString("source")?.ifBlank { null } }
        } catch (e: Exception) {
            null
        }
    }

    private fun commonsSearch(q: String): String? {
        val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=6" +
            "&gsrlimit=6&prop=imageinfo&iiprop=url|mime&iiurlwidth=640&gsrsearch=" + enc("$q filetype:bitmap")
        val txt = get(url) ?: return null
        return try {
            val pages = JSONObject(txt).optJSONObject("query")?.optJSONObject("pages") ?: return null
            val list = pages.keys().asSequence().map { pages.getJSONObject(it) }
                .sortedBy { it.optInt("index", 99) }.toList()
            list.firstNotNullOfOrNull { p ->
                val info = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: return@firstNotNullOfOrNull null
                val mime = info.optString("mime")
                if (mime.startsWith("image/") && !mime.contains("svg")) info.optString("thumburl").ifBlank { null } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun braveSearch(q: String, key: String): String? {
        if (key.isBlank()) return null
        val txt = get(
            "https://api.search.brave.com/res/v1/images/search?count=5&safesearch=strict&q=" + enc(q),
            mapOf("X-Subscription-Token" to key.trim())
        ) ?: return null
        return try {
            val r = JSONObject(txt).optJSONArray("results") ?: return null
            (0 until r.length()).firstNotNullOfOrNull { i ->
                val o = r.getJSONObject(i)
                o.optJSONObject("thumbnail")?.optString("src")?.ifBlank { null }
                    ?: o.optJSONObject("properties")?.optString("url")?.ifBlank { null }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Searches for an image. [isName] marks proper names and real places, which are best found on Wikipedia.
     * Runs network calls: call it from a background thread.
     */
    fun find(query: String, isName: Boolean, braveKey: String, color: Boolean): Result? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val cacheFile = File(cacheDir, hash(q.lowercase()) + ".png")
        if (cacheFile.exists()) {
            BitmapFactory.decodeFile(cacheFile.absolutePath)?.let { return Result(prepare(it, color), "cache") }
        }
        val attempts: List<Pair<String, () -> String?>> = if (isName) listOf(
            "Wikipedia" to { wikipediaExact(q) },
            "Wikipedia" to { wikipediaSearch(q) },
            "Wikimedia Commons" to { commonsSearch(q) },
            "Brave" to { braveSearch(q, braveKey) },
        ) else listOf(
            "Brave" to { braveSearch(q, braveKey) },
            "Wikipedia" to { wikipediaExact(q) },
            "Wikimedia Commons" to { commonsSearch(q) },
            "Wikipedia" to { wikipediaSearch(q) },
        )
        for ((name, f) in attempts) {
            val url = f() ?: continue
            val bmp = downloadBitmap(url) ?: continue
            try {
                cacheFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
            } catch (e: Exception) {
            }
            return Result(prepare(bmp, color), name)
        }
        return null
    }

    /** On black-and-white screens, converts to grey with extra contrast so the picture is readable on e-ink. */
    private fun prepare(src: Bitmap, color: Boolean): Bitmap {
        if (color) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.25f
        val t = (1f - contrast) * 128f
        cm.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, t,
                    0f, contrast, 0f, 0f, t,
                    0f, 0f, contrast, 0f, t,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }
}
