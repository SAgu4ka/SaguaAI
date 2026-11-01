package ru.sagu4ka.rngai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.sagu4ka.rngai.ui.*

private class Tab(val title: String, val icon: ImageVector)

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SagukaTheme { Root(vm) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Root(vm: AppViewModel) {
    val tabs = remember {
        listOf(
            Tab("Главная", Icons.Rounded.AutoAwesome), Tab("Ввод", Icons.Rounded.AddCircle),
            Tab("Статистика", Icons.Rounded.BarChart), Tab("История", Icons.Rounded.History),
            Tab("Модели", Icons.Rounded.Memory), Tab("Настройки", Icons.Rounded.Settings)
        )
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }

    Scaffold(
        containerColor = C.Bg0,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar(containerColor = C.Bg1, tonalElevation = 0.dp) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(t.icon, t.title) },
                        label = { Text(t.title, fontSize = 9.sp, maxLines = 1) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = C.Violet, selectedTextColor = C.Violet,
                            indicatorColor = C.Violet.copy(alpha = 0.18f),
                            unselectedIconColor = C.Muted, unselectedTextColor = C.Muted
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(C.Bg0)) {
            val a = analysis
            if (a == null || busy && a.spins.isEmpty() && profiles.isEmpty()) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = C.Violet)
                    Spacer(Modifier.height(12.dp))
                    Text("Восстанавливаю обучение модели…", color = C.Muted)
                }
            } else {
                Crossfade(tab, animationSpec = tween(220), label = "tabs") { t ->
                    when (t) {
                        0 -> HomeScreen(a, profiles, vm::selectProfile) { tab = 1 }
                        1 -> InputScreen(a, vm::addSpin, vm::undoLast)
                        2 -> StatsScreen(a)
                        3 -> HistoryScreen(a, vm::setSector, vm::deleteSpin)
                        4 -> ModelsScreen(a)
                        else -> SettingsScreen(
                            a, profiles, vm::selectProfile, vm::addProfile, vm::saveProfile, vm::deleteProfile,
                            vm::resetStats, vm::exportTo, vm::importFrom
                        )
                    }
                }
            }
        }
    }
}

