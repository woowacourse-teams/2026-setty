package com.aksworns22.setty.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.aksworns22.setty.feature.home.HomeScreen
import com.aksworns22.setty.feature.login.LoginScreen
import kotlinx.serialization.Serializable


sealed interface SettyScreen : NavKey {
    @Serializable
    data object Login : SettyScreen

    @Serializable
    data object Home : SettyScreen
}

@Composable
fun SettyNavHost() {
    val backStack = rememberNavBackStack(SettyScreen.Login)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<SettyScreen.Login> {
                LoginScreen(
                    onLoginSuccess = {
                        backStack.clear()
                        backStack.add(SettyScreen.Home)
                    }
                )
            }
            entry<SettyScreen.Home> {
                HomeScreen()
            }
        }
    )
}
