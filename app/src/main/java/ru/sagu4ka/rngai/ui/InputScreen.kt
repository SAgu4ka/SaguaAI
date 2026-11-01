package ru.sagu4ka.rngai.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import ru.sagu4ka.rngai.Analysis

@Composable
fun InputScreen(a: Analysis, onAdd: (Int) -> Unit, onUndo: () -> Unit) {
    val p = a.profile
    val k = p.sectorCount
    val haptic = LocalHapticFeedback.current
    val last = a.spins.lastOrNull()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        ScreenTitle("Ввод", "Раунд №${a.spins.size + 1} • результатов: ${a.spins.size}")

        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (last != null) {
                    SectorBall(a.names[last.sector], sectorColor(p, last.sector), 54.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Последний: ${a.names[last.sector]}", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("№${a.spins.size}", color = C.Muted, fontSize = 12.sp)
                    }
                } else {
                    Text("Нажмите на выпавший сектор ниже", color = C.Muted, modifier = Modifier.weight(1f))
                }
                FilledTonalIconButton(
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onUndo() },
                    enabled = last != null,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = C.Bg0, contentColor = C.Bad, disabledContentColor = C.Line, disabledContainerColor = C.Bg0),
                    modifier = Modifier.size(52.dp)
                ) { Icon(Icons.Rounded.Undo, "Отменить последнее") }
            }
            if (a.spins.size > 1) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    a.spins.takeLast(12).dropLast(1).reversed().forEach { SectorBall(a.names[it.sector], sectorColor(p, it.sector), 28.dp) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        // сетка внизу экрана — под большой палец
        val columns = if (k <= 12) 4 else 6
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = false
        ) {
            items((0 until k).toList()) { s ->
                SectorKey(a.names[s], sectorColor(p, s)) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAdd(s)
                }
            }
        }
    }
}

@Composable
private fun SectorKey(name: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "key")
    Box(
        Modifier.aspectRatio(1f).scale(sc).clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.92f))
            .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = if (pressed) 0.6f else 0.14f), RoundedCornerShape(18.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(name, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            fontSize = if (name.length > 3) 11.sp else 20.sp, maxLines = 2)
    }
}

@Suppress("unused") private val keepCircle = CircleShape
