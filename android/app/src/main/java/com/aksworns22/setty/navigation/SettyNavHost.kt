package com.aksworns22.setty.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.aksworns22.setty.feature.home.HomeScreen
import com.aksworns22.setty.feature.login.LoginScreen
import com.aksworns22.setty.feature.signup.SignUpScreen
import kotlinx.serialization.Serializable


sealed interface SettyScreen : NavKey {
    @Serializable
    data object Login : SettyScreen

    @Serializable
    data object SignUp : SettyScreen

    @Serializable
    data object Home : SettyScreen
}

@Composable
fun SettyNavHost() {
    val backStack = rememberNavBackStack(SettyScreen.Login)
    var isSignUpCompleted by rememberSaveable { mutableStateOf(false) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<SettyScreen.Login> {
                LoginScreen(
                    onLoginSuccess = {
                        backStack.clear()
                        backStack.add(SettyScreen.Home)
                    },
                    onSignUpClick = {
                        backStack.add(SettyScreen.SignUp)
                    },
                    isSignUpCompleted = isSignUpCompleted,
                    onSignUpCompletedShown = {
                        isSignUpCompleted = false
                    }
                )
            }
            entry<SettyScreen.SignUp> {
                SignUpScreen(
                    onSignUpSuccess = {
                        isSignUpCompleted = true
                        backStack.removeLastOrNull()
                    }
                )
            }
            entry<SettyScreen.Home> {
                HomeScreen(
                    onListingClick = {}
                )
            }
        }
    )
}
