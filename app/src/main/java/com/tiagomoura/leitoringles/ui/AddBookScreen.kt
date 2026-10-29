package com.tiagomoura.leitoringles.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiagomoura.leitoringles.AppState
import com.tiagomoura.leitoringles.Screen
import com.tiagomoura.leitoringles.data.LEVELS
import com.tiagomoura.leitoringles.data.Prompt

@Composable
fun EinkField(label: String, value: String, onChange: (String) -> Unit, singleLine: Boolean = true) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, fontSize = 14.sp, color = Grey)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = singleLine,
            textStyle = TextStyle(fontSize = 18.sp, color = Ink),
            cursorBrush = SolidColor(Ink),
            modifier = Modifier.fillMaxWidth().border(2.dp, Ink, RoundedCornerShape(6.dp)).padding(12.dp),
        )
    }
}

@Composable
fun AddBookScreen(app: AppState) {
    BackHandler { app.screen = Screen.Library }
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("B1") }
    var prompt by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            status = "Importando..."
            app.importBook(uri) { msg -> busy = false; status = msg }
        }
    }

    Column(Modifier.fillMaxSize().background(Paper)) {
        TopBar("Adicionar livro", onBack = { app.screen = Screen.Library })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("1. Peça o livro ao Claude", fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            Spacer(Modifier.height(4.dp))
            Text(
                "Digite o nome do livro e escolha o nível. O app monta o pedido completo, com todas as regras do formato. " +
                    "Copie e cole numa conversa com o Claude.",
                fontSize = 15.sp,
            )
            EinkField("Nome do livro", title, { title = it })
            EinkField("Autor (opcional)", author, { author = it })
            Text("Nível", fontSize = 14.sp, color = Grey)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (l in LEVELS) EinkButton(l, Modifier.weight(1f), filled = level == l) { level = l }
            }
            Spacer(Modifier.height(6.dp))
            EinkButton("Gerar pedido", filled = true, enabled = title.isNotBlank()) {
                prompt = Prompt.build(title.trim(), author.trim(), level)
            }
            prompt?.let { p ->
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 320.dp).border(1.dp, Ink, RoundedCornerShape(6.dp))
                        .verticalScroll(rememberScrollState()).padding(10.dp)
                ) {
                    Text(p, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(8.dp))
                EinkButton("Copiar pedido", filled = true) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Pedido para o Claude", p))
                    status = "Pedido copiado. Cole numa conversa com o Claude."
                }
            }

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
            Text("2. Importe o arquivo", fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            Spacer(Modifier.height(4.dp))
            Text(
                "Quando o Claude entregar o arquivo (termina em .lib.json), baixe-o para este aparelho e toque no botão abaixo. " +
                    "O livro vai para a prateleira do seu nível.",
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(10.dp))
            EinkButton(if (busy) "Importando..." else "Escolher arquivo", filled = true, enabled = !busy) {
                picker.launch(arrayOf("application/json", "application/gzip", "application/octet-stream", "text/plain", "*/*"))
            }
            status?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
