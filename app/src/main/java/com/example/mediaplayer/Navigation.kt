package com.example.mediaplayer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.mediaplayer.data.SecurityDatabaseHelper
import com.example.mediaplayer.ui.legal.AboutScreen
import com.example.mediaplayer.ui.legal.EulaScreen
import com.example.mediaplayer.ui.legal.TermAndPrivacyScreen
import com.example.mediaplayer.ui.main.MainScreen
import com.example.mediaplayer.ui.player.PlayerScreen
import com.example.mediaplayer.ui.security.LockScreen
import com.example.mediaplayer.ui.settings.SettingsScreen

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val dbHelper = remember { SecurityDatabaseHelper(context) }
    
    // Choose start destination based on whether a PIN is already set
    val startDestination = remember {
        if (dbHelper.isPasswordSet()) Lock(isSettingUp = false) else Main
    }
    
    val backStack = rememberNavBackStack(startDestination)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            // 1. Lock Screen
            entry<Lock> { lock ->
                LockScreen(
                    isSettingUp = lock.isSettingUp,
                    onSuccess = {
                        if (lock.isSettingUp) {
                            // Go back to Settings
                            backStack.removeLastOrNull()
                        } else {
                            // Unlocked successfully: go to Main and clear Lock screen from backstack
                            backStack.add(Main)
                            backStack.remove(lock)
                        }
                    },
                    onCancelSetup = if (lock.isSettingUp) {
                        { backStack.removeLastOrNull() }
                    } else null
                )
            }

            // 2. Main Screen
            entry<Main> {
                MainScreen(
                    onNavigate = { key -> backStack.add(key) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 3. Custom Player Screen
            entry<Player> { playerKey ->
                PlayerScreen(
                    videoPath = playerKey.videoPath,
                    videoTitle = playerKey.videoTitle,
                    isOnline = playerKey.isOnline,
                    onBack = { backStack.removeLastOrNull() }
                )
            }

            // 4. Settings Screen
            entry<Settings> {
                SettingsScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToSetupPin = { backStack.add(Lock(isSettingUp = true)) },
                    onNavigateToAbout = { backStack.add(About) },
                    onNavigateToTerms = { backStack.add(Terms) },
                    onNavigateToEula = { backStack.add(Eula) }
                )
            }

            // 5. About Screen
            entry<About> {
                AboutScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToEula = { backStack.add(Eula) },
                    onNavigateToTerms = { backStack.add(Terms) }
                )
            }

            // 6. Terms & Privacy Screen
            entry<Terms> {
                TermAndPrivacyScreen(
                    onBack = { backStack.removeLastOrNull() }
                )
            }

            // 7. EULA Screen
            entry<Eula> {
                EulaScreen(
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
