package com.sarotech.lthisab

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sarotech.lthisab.data.AppDatabase
import com.sarotech.lthisab.data.SettingsRepository
import com.sarotech.lthisab.i18n.LocalStrings
import com.sarotech.lthisab.i18n.StringsRepository
import com.sarotech.lthisab.ui.screens.CashBookScreen
import com.sarotech.lthisab.ui.screens.SettingsScreen
import com.sarotech.lthisab.ui.security.AppLockScreen
import com.sarotech.lthisab.ui.theme.LTHisabTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.get(this)
        val settingsRepo = SettingsRepository(this)

        setContent {
            val loadedSettings by settingsRepo.settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val scope = rememberCoroutineScope()

            val settings = loadedSettings
            if (settings == null) {
                LTHisabTheme(darkTheme = false) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                return@setContent
            }

            LaunchedEffect(settings.keepScreenOn) {
                if (settings.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            var unlocked by rememberSaveable { mutableStateOf(!settings.pinEnabled || settings.pinHash.isBlank()) }
            var showSettings by rememberSaveable { mutableStateOf(false) }

            // If user disables PIN inside settings, keep unlocked state synced
            LaunchedEffect(settings.pinEnabled) {
                if (!settings.pinEnabled) unlocked = true
            }

            val strings = remember(settings.language) {
                StringsRepository.forLanguage(settings.language)
            }

            LTHisabTheme(darkTheme = settings.darkTheme) {
                CompositionLocalProvider(LocalStrings provides strings) {
                    if (settings.pinEnabled && settings.pinHash.isNotBlank() && !unlocked) {
                        AppLockScreen(
                            settings = settings,
                            onUnlocked = { unlocked = true },
                            onResetPin = { newPin, question, answer ->
                                scope.launch { settingsRepo.setPin(newPin, question, answer) }
                            }
                        )
                    } else if (showSettings) {
                        BackHandler { showSettings = false }
                        SettingsScreen(
                            db = db,
                            settings = settings,
                            settingsRepo = settingsRepo,
                            onBack = { showSettings = false }
                        )
                    } else {
                        CashBookScreen(
                            db = db,
                            settings = settings,
                            onOpenSettings = { showSettings = true }
                        )
                    }
                }
            }
        }
    }
}
