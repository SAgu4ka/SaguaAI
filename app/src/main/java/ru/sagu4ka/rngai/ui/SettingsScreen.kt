package ru.sagu4ka.rngai.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import ru.sagu4ka.rngai.Analysis
import ru.sagu4ka.rngai.data.ProfileEntity

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.Violet, unfocusedBorderColor = C.Line,
    focusedTextColor = C.Text, unfocusedTextColor = C.Text,
    focusedLabelColor = C.Violet, unfocusedLabelColor = C.Muted, cursorColor = C.Violet
)

@Composable
fun SettingsScreen(
    a: Analysis,
    profiles: List<ProfileEntity>,
    onSelect: (Long) -> Unit,
    onAddProfile: (String, Int, String) -> Unit,
    onSaveProfile: (ProfileEntity) -> Unit,
    onDeleteProfile: (Long) -> Unit,
    onReset: () -> Unit,
    onExport: (Uri, String) -> Unit,
    onImport: (Uri, String) -> Unit
) {
    val p = a.profile
    var name by remember(p.id, p.name) { mutableStateOf(p.name) }
    var count by remember(p.id, p.sectorCount) { mutableStateOf(p.sectorCount.toString()) }
    var names by remember(p.id, p.sectorNames) { mutableStateOf(p.sectorNames) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }

    var pendingKind by remember { mutableStateOf("json") }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { u -> onExport(u, "csv") } }
    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { u -> onExport(u, pendingKind) } }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> onImport(u, pendingKind) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        ScreenTitle("Настройки", "Профили, секторы, данные")

        SectionLabel("Профили казино")
        GlassCard(Modifier.fillMaxWidth()) {
            profiles.forEach { pr ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(pr.id) }.padding(vertical = 10.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(pr.id == p.id, { onSelect(pr.id) }, colors = RadioButtonDefaults.colors(selectedColor = C.Violet, unselectedColor = C.Muted))
                    Column(Modifier.weight(1f)) {
                        Text(pr.name, color = C.Text, fontWeight = FontWeight.Medium)
                        Text("секторов: ${pr.sectorCount}", color = C.Muted, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            GhostButton("＋ Новый профиль", Modifier.fillMaxWidth()) { showNew = true }
        }

        SectionLabel("Текущий профиль")
        GlassCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(count, { count = it.filter(Char::isDigit).take(3) }, label = { Text("Количество секторов (2–100)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(names, { names = it }, label = { Text("Названия секторов — по одному на строку (пусто = номера)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), colors = fieldColors())
            val newK = count.toIntOrNull()?.coerceIn(2, 100) ?: p.sectorCount
            if (newK < p.sectorCount) {
                Text("Внимание: результаты с номерами сектора ≥ $newK будут удалены.", color = C.Warn, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Сохранить", Modifier.fillMaxWidth()) {
                onSaveProfile(p.copy(name = name.ifBlank { p.name }, sectorCount = newK, sectorNames = names))
            }
        }

        SectionLabel("Данные")
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Экспорт текущего профиля", color = C.Muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("CSV", Modifier.weight(1f)) { saveCsv.launch("saguka_${p.name}.csv") }
                GhostButton("JSON", Modifier.weight(1f)) { pendingKind = "json"; saveJson.launch("saguka_${p.name}.json") }
            }
            Text("Импорт", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("CSV в профиль", Modifier.weight(1f)) { pendingKind = "csv"; open.launch(arrayOf("*/*")) }
                GhostButton("JSON как новый", Modifier.weight(1f)) { pendingKind = "json"; open.launch(arrayOf("*/*")) }
            }
            HorizontalDivider(color = C.Line, modifier = Modifier.padding(vertical = 10.dp))
            Text("Резервная копия всех профилей", color = C.Muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Создать копию", Modifier.weight(1f)) { pendingKind = "backup"; saveJson.launch("saguka_backup.json") }
                GhostButton("Восстановить", Modifier.weight(1f)) { pendingKind = "json"; open.launch(arrayOf("*/*")) }
            }
            Text("Восстановление добавляет профили из файла как новые и ничего не затирает.", color = C.Muted, fontSize = 11.sp)
        }

        SectionLabel("Опасная зона")
        GlassCard(Modifier.fillMaxWidth()) {
            GhostButton("Сбросить статистику (профиль останется)", Modifier.fillMaxWidth(), C.Warn) { confirmReset = true }
            Spacer(Modifier.height(8.dp))
            GhostButton("Удалить профиль", Modifier.fillMaxWidth(), C.Bad) { confirmDelete = true }
        }
        Spacer(Modifier.height(10.dp))
        Text("Saguka AI 1.0 • всё считается на устройстве, интернет и регистрация не нужны.", color = C.Muted, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false }, containerColor = C.Bg2,
        title = { Text("Сбросить статистику?", color = C.Text) },
        text = { Text("Все ${a.spins.size} результатов профиля «${p.name}» будут удалены, а модели начнут обучение с нуля. Профиль и настройки секторов сохранятся.", color = C.Muted) },
        confirmButton = { TextButton({ onReset(); confirmReset = false }) { Text("Сбросить", color = C.Warn) } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Отмена", color = C.Muted) } }
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = C.Bg2,
        title = { Text("Удалить профиль?", color = C.Text) },
        text = { Text("Профиль «${p.name}» и вся его история будут удалены без возможности восстановления.", color = C.Muted) },
        confirmButton = { TextButton({ onDeleteProfile(p.id); confirmDelete = false }) { Text("Удалить", color = C.Bad) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Отмена", color = C.Muted) } }
    )
    if (showNew) {
        var nn by remember { mutableStateOf("") }
        var nk by remember { mutableStateOf("37") }
        AlertDialog(
            onDismissRequest = { showNew = false }, containerColor = C.Bg2,
            title = { Text("Новый профиль", color = C.Text) },
            text = {
                Column {
                    OutlinedTextField(nn, { nn = it }, label = { Text("Название") }, singleLine = true, colors = fieldColors())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(nk, { nk = it.filter(Char::isDigit).take(3) }, label = { Text("Секторов") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = fieldColors())
                }
            },
            confirmButton = { TextButton({ onAddProfile(nn, nk.toIntOrNull() ?: 37, ""); showNew = false }) { Text("Создать", color = C.Violet) } },
            dismissButton = { TextButton({ showNew = false }) { Text("Отмена", color = C.Muted) } }
        )
    }
}
