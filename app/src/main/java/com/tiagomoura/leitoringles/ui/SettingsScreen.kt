package com.tiagomoura.leitoringles.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiagomoura.leitoringles.AppState
import com.tiagomoura.leitoringles.Screen

@Composable
fun SettingsScreen(app: AppState) {
    BackHandler { app.screen = Screen.Library }
    val s = app.settings
    var key by remember { mutableStateOf(s.braveKey) }
    Column(Modifier.fillMaxSize().background(Paper)) {
        TopBar("Configurações", onBack = { app.screen = Screen.Library })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Section("Leitura")
            StepRow("Tamanho da letra", "${s.fontSize}",
                onMinus = { app.updateSettings(s.copy(fontSize = (s.fontSize - 2).coerceAtLeast(14))) },
                onPlus = { app.updateSettings(s.copy(fontSize = (s.fontSize + 2).coerceAtMost(48))) })
            StepRow("Espaço entre linhas", String.format("%.2f", s.lineSpacing),
                onMinus = { app.updateSettings(s.copy(lineSpacing = (s.lineSpacing - 0.1f).coerceAtLeast(1.1f))) },
                onPlus = { app.updateSettings(s.copy(lineSpacing = (s.lineSpacing + 0.1f).coerceAtMost(2.2f))) })
            StepRow("Margens", "${s.margin}",
                onMinus = { app.updateSettings(s.copy(margin = (s.margin - 4).coerceAtLeast(8))) },
                onPlus = { app.updateSettings(s.copy(margin = (s.margin + 4).coerceAtMost(56))) })
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("Marcar expressões e gramática", fontSize = 16.sp, modifier = Modifier.weight(1f))
                EinkButton(if (s.showMarks) "Ligado" else "Desligado", filled = s.showMarks) {
                    app.updateSettings(s.copy(showMarks = !s.showMarks))
                }
            }

            Section("Tela e-ink")
            Text(
                "Atualização completa da tela (pisca em preto para limpar fantasmas) a cada quantas páginas. 0 = nunca.",
                fontSize = 14.sp, color = Grey,
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (n in listOf(0, 4, 8, 12, 20)) {
                    EinkButton("$n", Modifier.weight(1f), filled = s.fullRefreshEvery == n) {
                        app.updateSettings(s.copy(fullRefreshEvery = n))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Cores (detectado: ${Build.MANUFACTURER} ${Build.MODEL})", fontSize = 14.sp, color = Grey)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((id, label) in listOf("auto" to "Automático", "color" to "Colorida", "mono" to "Preto e branco")) {
                    EinkButton(label, Modifier.weight(1f), filled = s.colorMode == id) { app.updateSettings(s.copy(colorMode = id)) }
                }
            }
            Text(
                "Dica: no Boox, abra a Central E-ink (puxando a barra superior) e escolha o modo \"Normal\" ou \"HD\" para este app.",
                fontSize = 14.sp, color = Grey,
            )

            Section("Imagens")
            Text(
                "As imagens vêm da Wikipédia e do Wikimedia Commons, sem cadastro. Para palavras comuns, você pode " +
                    "colar aqui uma chave da Brave Search API (opcional).",
                fontSize = 14.sp, color = Grey,
            )
            EinkField("Chave da Brave Search API", key, { key = it })
            EinkButton("Salvar chave", filled = true) { app.updateSettings(s.copy(braveKey = key.trim())) }

            Section("Sobre")
            Text("Leitor Inglês — graded readers para e-ink.", fontSize = 15.sp)
            Text("Os dados (posição de leitura, vocabulário e configurações) ficam em:", fontSize = 14.sp, color = Grey)
            Text(app.storage.root.absolutePath, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(18.dp))
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
    Spacer(Modifier.height(4.dp))
    Divider()
    Spacer(Modifier.height(6.dp))
}
