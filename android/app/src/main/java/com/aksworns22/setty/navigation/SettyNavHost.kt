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
import com.aksworns22.setty.feature.favorites.FavoritesScreen
import com.aksworns22.setty.feature.home.HomeScreen
import com.aksworns22.setty.feature.listingdetail.FurnitureDetailScreen
import com.aksworns22.setty.feature.listingeditor.ListingEditorScreen
import com.aksworns22.setty.feature.login.LoginScreen
import com.aksworns22.setty.feature.mylistings.MyListingsScreen
import com.aksworns22.setty.feature.mypage.MyPageScreen
import com.aksworns22.setty.feature.orders.OrderDetailScreen
import com.aksworns22.setty.feature.orders.OrdersScreen
import com.aksworns22.setty.feature.profile.ProfileEditScreen
import com.aksworns22.setty.feature.signup.SignUpScreen
import kotlinx.serialization.Serializable


sealed interface SettyScreen : NavKey {
    @Serializable
    data object Login : SettyScreen

    @Serializable
    data object SignUp : SettyScreen

    @Serializable
    data object Home : SettyScreen

    @Serializable
    data class FurnitureDetail(val furnitureId: Long) : SettyScreen

    @Serializable
    data object MyPage : SettyScreen

    @Serializable
    data object ProfileEdit : SettyScreen

    @Serializable
    data object MyListings : SettyScreen

    /** [listingId]가 null이면 새 매물 등록, 있으면 해당 매물 수정. */
    @Serializable
    data class ListingEditor(val listingId: Long? = null) : SettyScreen

    @Serializable
    data object Favorites : SettyScreen

    @Serializable
    data object Orders : SettyScreen

    @Serializable
    data class OrderDetail(val orderId: Long) : SettyScreen
}

@Composable
fun SettyNavHost() {
    val backStack = rememberNavBackStack(SettyScreen.Login)
    var isSignUpCompleted by rememberSaveable { mutableStateOf(false) }

    // 로그아웃하거나 토큰이 만료되면 쌓인 화면을 모두 버리고 로그인으로 보낸다
    val navigateToLogin: () -> Unit = {
        backStack.clear()
        backStack.add(SettyScreen.Login)
    }
    val navigateBack: () -> Unit = { backStack.removeLastOrNull() }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<SettyScreen.Login> {
                LoginScreen(
                    isSignUpCompleted = isSignUpCompleted,
                    onLoginSuccess = {
                        backStack.clear()
                        backStack.add(SettyScreen.Home)
                    },
                    onSignUpClick = {
                        backStack.add(SettyScreen.SignUp)
                    },
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
                    onFurnitureClick = { furnitureId ->
                        backStack.add(SettyScreen.FurnitureDetail(furnitureId))
                    },
                    onProfileClick = {
                        backStack.add(SettyScreen.MyPage)
                    },
                )
            }
            entry<SettyScreen.FurnitureDetail> { key ->
                FurnitureDetailScreen(
                    furnitureId = key.furnitureId,
                    onBack = navigateBack,
                    onOrdersClick = { backStack.add(SettyScreen.Orders) },
                    onRequireLogin = navigateToLogin,
                )
            }
            entry<SettyScreen.MyPage> {
                MyPageScreen(
                    onBack = navigateBack,
                    onEditProfile = { backStack.add(SettyScreen.ProfileEdit) },
                    onMyListingsClick = { backStack.add(SettyScreen.MyListings) },
                    onFavoritesClick = { backStack.add(SettyScreen.Favorites) },
                    onOrdersClick = { backStack.add(SettyScreen.Orders) },
                    onLoggedOut = navigateToLogin,
                )
            }
            entry<SettyScreen.ProfileEdit> {
                ProfileEditScreen(
                    onBack = navigateBack,
                    onSaved = navigateBack,
                    onRequireLogin = navigateToLogin,
                )
            }
            entry<SettyScreen.MyListings> {
                MyListingsScreen(
                    onBack = navigateBack,
                    onFurnitureClick = { furnitureId ->
                        backStack.add(SettyScreen.FurnitureDetail(furnitureId))
                    },
                    onCreateClick = { backStack.add(SettyScreen.ListingEditor()) },
                    onEditClick = { listingId -> backStack.add(SettyScreen.ListingEditor(listingId)) },
                    onRequireLogin = navigateToLogin,
                )
            }
            entry<SettyScreen.ListingEditor> { key ->
                ListingEditorScreen(
                    listingId = key.listingId,
                    onBack = navigateBack,
                    onSaved = { listingId ->
                        backStack.removeLastOrNull()
                        // 새로 등록한 매물은 상세 화면으로 이어서 보여준다
                        if (key.listingId == null) {
                            backStack.add(SettyScreen.FurnitureDetail(listingId))
                        }
                    },
                    onRequireLogin = navigateToLogin,
                )
            }
            entry<SettyScreen.Favorites> {
                FavoritesScreen(
                    onBack = navigateBack,
                    onFurnitureClick = { furnitureId ->
                        backStack.add(SettyScreen.FurnitureDetail(furnitureId))
                    },
                    onRequireLogin = navigateToLogin,
                )
            }
            entry<SettyScreen.Orders> {
                OrdersScreen(
                    onBack = navigateBack,
                    onOrderClick = { orderId -> backStack.add(SettyScreen.OrderDetail(orderId)) },
                    onRequireLogin = navigateToLogin,
                )
            }
            entry<SettyScreen.OrderDetail> { key ->
                OrderDetailScreen(
                    orderId = key.orderId,
                    onBack = navigateBack,
                    onFurnitureClick = { furnitureId ->
                        backStack.add(SettyScreen.FurnitureDetail(furnitureId))
                    },
                    onRequireLogin = navigateToLogin,
                )
            }
        }
    )
}
