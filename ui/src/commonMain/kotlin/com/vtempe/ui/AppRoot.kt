package com.vtempe.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.domain.account.AccountSession
import com.vtempe.shared.domain.repository.LanguagePreferences
import com.vtempe.ui.theme.VTempeTheme
import com.vtempe.ui.navigation.Destination
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal val NavigationSaver = Saver<List<Destination>, String>(
    save = { Json.encodeToString(it) },
    restore = { Json.decodeFromString<List<Destination>>(it) }
)

// Глобальные провайдеры для высот баров, чтобы использовать в экранах
val LocalTopBarHeight = compositionLocalOf { 0.dp }
val LocalBottomBarHeight = compositionLocalOf { 0.dp }

val LocalAppLocaleUpdater = compositionLocalOf<(String?) -> Unit> { {} }

internal val LocalAppRestart = compositionLocalOf<() -> Unit> { error("AppRoot is required") }

@Composable
fun AppRoot() {
    // Saveable so an Activity recreation (e.g. language change) keeps the same generation and its ViewModels.
    var restartGeneration by rememberSaveable { mutableStateOf(0) }

    // Device data cleared or replaced anywhere (sign-out, account switch, reset, deletion):
    // rebuild everything, whichever screen is open (VT-AUD-072).
    val accountSession = remember { KoinProvider.koin?.get<AccountSession>() }
    val dataGeneration = accountSession?.localDataGeneration?.collectAsState()?.value
    val restartGate = remember { LocalDataRestartGate(dataGeneration) }
    LaunchedEffect(dataGeneration) {
        if (restartGate.changed(dataGeneration)) restartGeneration++
    }
    key(restartGeneration) {
        RestartScopedViewModels(restartGeneration) {
            var backStack by rememberSaveable(stateSaver = NavigationSaver) {
                mutableStateOf(listOf<Destination>(Destination.Splash))
            }
            val savedTag = remember { KoinProvider.koin?.get<LanguagePreferences>()?.getLanguageTag() }
            var languageTag by remember { mutableStateOf(savedTag) }
            key(languageTag) {
                CompositionLocalProvider(
                    LocalAppLocaleUpdater provides { languageTag = it },
                    LocalAppRestart provides { restartGeneration++ }
                ) {
                    VTempeTheme { AppShell(backStack, { backStack = it }) }
                }
            }
        }
    }
}
