package com.tiagomoura.leitoringles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tiagomoura.leitoringles.ui.ReaderProbe
import com.tiagomoura.leitoringles.ui.TextTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Taps on real words of the built-in books and checks that the reader opens exactly that word.
 * The position of each word on the screen is taken from the text as Compose draws it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34, 35], qualifiers = "w411dp-h891dp-xxhdpi")
open class ReaderTouchTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun openBook(id: String, chapter: Int) {
        rule.waitUntil(60_000) { rule.activity.app.books.isNotEmpty() }
        ReaderProbe.holder = null
        rule.runOnUiThread { rule.activity.app.screen = Screen.Reader(id, chapter, 0) }
        rule.waitUntil(60_000) {
            val h = ReaderProbe.holder
            h?.layout != null && h.text?.isAttached == true && h.outer?.isAttached == true
        }
        rule.waitForIdle()
    }

    private fun pageText(): String = ReaderProbe.holder!!.layout!!.layoutInput.text.text

    /** Words of the current page (offsets inside the page text), only from the chapter body. */
    private fun pageWords(): List<IntRange> {
        val h = ReaderProbe.holder!!
        val bodyStart = (h.base - h.start).coerceAtLeast(0)
        return TextTools.WORD.findAll(pageText()).map { it.range }.filter { it.first >= bodyStart }.toList()
    }

    /** Centre of the word in root coordinates (where a finger would touch it). */
    private fun centreOf(r: IntRange): Offset {
        val h = ReaderProbe.holder!!
        val lay = h.layout!!
        val a = lay.getBoundingBox(r.first)
        val b = lay.getBoundingBox(r.last)
        val local = Offset((a.left + b.right) / 2f, (a.top + a.bottom) / 2f)
        return h.text!!.localToRoot(local)
    }

    private fun tappedWord(): String? {
        val t = ReaderProbe.lastTap ?: return null
        val h = ReaderProbe.holder!!
        val s = t.start + h.base - h.start
        val e = t.end + h.base - h.start
        return pageText().substring(s, e)
    }

    private fun checkTapsOnPage(label: String) {
        val words = pageWords()
        assertTrue("$label: page has words", words.size > 20)
        // up to 40 words spread over the whole page: first lines, middle and last lines
        val step = (words.size / 40).coerceAtLeast(1)
        val sample = words.filterIndexed { i, _ -> i % step == 0 } + listOf(words.last())
        var ok = 0
        for (r in sample) {
            val expected = pageText().substring(r.first, r.last + 1)
            ReaderProbe.lastTap = null
            rule.onRoot().performTouchInput { click(centreOf(r)) }
            rule.waitForIdle()
            val got = tappedWord()
            println("$label: touched '$expected' at ${centreOf(r)} -> '$got'")
            assertEquals("$label: touched '$expected'", expected, got)
            ok++
            rule.onNodeWithText("Fechar").performClick()
            rule.waitForIdle()
        }
        println("$label: $ok/${sample.size} taps hit the right word")
    }

    @Test
    fun tapsHitTheTouchedWord_redAndBlack() {
        openBook("red-and-black", 1)
        checkTapsOnPage("Red and Black part 1, page 1")
    }

    @Test
    fun tapsHitTheTouchedWord_afterTurningPages() {
        openBook("dracula", 4)
        val width = rule.onRoot().fetchSemanticsNode().size.width.toFloat()
        val height = rule.onRoot().fetchSemanticsNode().size.height.toFloat()
        // tap the right edge twice: two pages forward
        repeat(2) {
            rule.onRoot().performTouchInput { click(Offset(width - 4f, height / 2f)) }
            rule.waitForIdle()
        }
        assertEquals("page after two taps on the right edge", 2, ReaderProbe.page)
        checkTapsOnPage("Dracula chapter 4, page 3")
    }

    @Test
    fun longPressSelectsTheSentence() {
        openBook("moby-dick", 1)
        val words = pageWords()
        val r = words[words.size / 2]
        rule.onRoot().performTouchInput { longClick(centreOf(r)) }
        rule.waitForIdle()
        val h = ReaderProbe.holder!!
        val sel = ReaderProbe.selection
        assertTrue("selection panel is open", ReaderProbe.selectionDone)
        val wordInChapter = r.first + h.start - h.base
        assertTrue("sentence contains the pressed word: $sel vs $wordInChapter", sel != null && wordInChapter in sel)
        rule.onNodeWithText("Fechar").performClick()
    }

    @Test
    fun dragSelectsFromFirstToLastWord() {
        openBook("moby-dick", 2)
        val words = pageWords()
        val from = words[5]
        val to = words[17]
        rule.onRoot().performTouchInput {
            down(centreOf(from))
            advanceEventTime(900)
            moveTo(centreOf(words[11]), 100)
            moveTo(centreOf(to), 100)
            up()
        }
        rule.waitForIdle()
        val h = ReaderProbe.holder!!
        val sel = ReaderProbe.selection
        val a = from.first + h.start - h.base
        val b = to.last + h.start - h.base
        println("drag selection: $sel expected $a..$b")
        assertTrue("selection panel is open", ReaderProbe.selectionDone)
        assertEquals(a, sel!!.first)
        assertEquals(b, sel.last)
    }
}

/** Same checks on a narrower phone with a large system font, like the phone in the bug report. */
@Config(sdk = [34, 35], qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 1.3f)
class ReaderTouchLargeFontTest : ReaderTouchTest()
