package com.tiagomoura.leitoringles

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.tiagomoura.leitoringles.ui.AddBookScreen
import com.tiagomoura.leitoringles.ui.EinkTheme
import com.tiagomoura.leitoringles.ui.GrammarScreen
import com.tiagomoura.leitoringles.ui.LibraryScreen
import com.tiagomoura.leitoringles.ui.Paper
import com.tiagomoura.leitoringles.ui.ReaderScreen
import com.tiagomoura.leitoringles.ui.SettingsScreen
import com.tiagomoura.leitoringles.ui.VocabularyScreen

class MainActivity : ComponentActivity() {
    lateinit var app: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = AppState(applicationContext)
        app.refreshLibrary()
        setContent {
            EinkTheme {
                Box(Modifier.fillMaxSize().background(Paper).safeDrawingPadding()) {
                    when (val s = app.screen) {
                        Screen.Library -> LibraryScreen(app)
                        is Screen.Reader -> ReaderScreen(app, s)
                        Screen.Grammar -> GrammarScreen(app)
                        Screen.Vocabulary -> VocabularyScreen(app)
                        Screen.AddBook -> AddBookScreen(app)
                        Screen.Settings -> SettingsScreen(app)
                    }
                }
                LaunchedEffect(app.screen) {
                    if (app.screen !is Screen.Grammar) app.grammarFocus = null
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = app.keyHandler
        if (handler != null) {
            val code = event.keyCode
            val pageKey = code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_UP ||
                code == KeyEvent.KEYCODE_PAGE_DOWN || code == KeyEvent.KEYCODE_PAGE_UP ||
                code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT ||
                code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
            if (pageKey) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) handler(code)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
