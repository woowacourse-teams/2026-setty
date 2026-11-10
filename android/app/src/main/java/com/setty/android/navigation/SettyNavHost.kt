package com.setty.android.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.setty.android.feature.favorites.FavoritesScreen
import com.setty.android.feature.home.HomeScreen
import com.setty.android.feature.keyword.KeywordScreen
import com.setty.android.feature.listingdetail.FurnitureDetailScreen
import com.setty.android.feature.listingeditor.ListingEditorScreen
import com.setty.android.feature.login.LoginScreen
import com.setty.android.feature.mylistings.MyListingsScreen
import com.setty.android.feature.mypage.MyPageScreen
import com.setty.android.feature.notification.NotificationListScreen
import com.setty.android.feature.orders.OrderDetailScreen
import com.setty.android.feature.orders.OrdersScreen
import com.setty.android.feature.profile.ProfileEditScreen
import com.setty.android.feature.signup.SignUpScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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

    @Serializable
    data object Keywords : SettyScreen

    @Serializable
    data object Notifications : SettyScreen
}

@Composable
fun SettyNavHost(
    startDeepLink: DeepLink? = null,
    isLoggedInAtStart: Boolean = false,
    newDeepLinks: Flow<DeepLink> = emptyFlow(),
    isLoggedIn: suspend () -> Boolean = { false },
) {
    val backStack = rememberNavBackStack(*startKeys(startDeepLink, isLoggedInAtStart))
    var isSignUpCompleted by rememberSaveable { mutableStateOf(false) }
    // 로그인하지 않은 채 들어온 링크. 로그인하면 이 화면으로 보낸다
    var pendingDeepLinkUri by rememberSaveable {
        mutableStateOf(startDeepLink?.takeIf { it.requiresLogin && !isLoggedInAtStart }?.toUri())
    }
    // 잘못된 링크 안내는 처음 한 번만 띄운다
    var isInvalidLinkNoticePending by rememberSaveable { mutableStateOf(startDeepLink == DeepLink.Invalid) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 로그아웃하거나 토큰이 만료되면 쌓인 화면을 모두 버리고 로그인으로 보낸다
    val navigateToLogin: () -> Unit = {
        backStack.clear()
        backStack.add(SettyScreen.Login)
    }
    val navigateBack: () -> Unit = { backStack.removeLastOrNull() }

    LaunchedEffect(isInvalidLinkNoticePending) {
        if (isInvalidLinkNoticePending) {
            // key를 먼저 바꾸면 이 effect가 취소돼 안내가 뜨지 않으므로 띄운 뒤에 내린다
            snackbarHostState.showSnackbar(INVALID_LINK_MESSAGE)
            isInvalidLinkNoticePending = false
        }
    }
    // 앱이 떠 있을 때 들어온 링크는 보던 화면 위에 쌓아서 뒤로가기로 보던 화면에 돌아온다
    LaunchedEffect(newDeepLinks) {
        newDeepLinks.collect { deepLink ->
            val target = deepLink.target()
            when {
                target == null -> isInvalidLinkNoticePending = true
                deepLink.requiresLogin && !isLoggedIn() -> {
                    pendingDeepLinkUri = deepLink.toUri()
                    navigateToLogin()
                }
                // 로그인 화면 위에 쌓으면 뒤로가기가 로그인으로 가므로 처음 연 것처럼 스택을 새로 만든다
                SettyScreen.Login in backStack -> {
                    backStack.clear()
                    backStack.addAll(deepLink.toBackStack())
                }
                backStack.lastOrNull() != target -> backStack.add(target)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<SettyScreen.Login> {
                    LoginScreen(
                        isSignUpCompleted = isSignUpCompleted,
                        onLoginSuccess = {
                            val pendingDeepLink = parseDeepLink(pendingDeepLinkUri)
                            pendingDeepLinkUri = null
                            backStack.clear()
                            backStack.addAll(pendingDeepLink?.toBackStack() ?: listOf(SettyScreen.Home))
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
                        onRequireDong = {
                            backStack.add(SettyScreen.ProfileEdit)
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
                        onKeywordsClick = { backStack.add(SettyScreen.Keywords) },
                        onNotificationsClick = { backStack.add(SettyScreen.Notifications) },
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
                        onRequireDong = { backStack.add(SettyScreen.ProfileEdit) },
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
                entry<SettyScreen.Keywords> {
                    KeywordScreen(
                        onBack = navigateBack,
                        onRequireLogin = navigateToLogin,
                    )
                }
                entry<SettyScreen.Notifications> {
                    NotificationListScreen(
                        onBack = navigateBack,
                        onFurnitureClick = { furnitureId ->
                            backStack.add(SettyScreen.FurnitureDetail(furnitureId))
                        },
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
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
}

/**
 * 처음 띄울 백스택.
 * 링크 없이 열면 지금처럼 로그인부터, 로그인한 채 링크로 열면 대상 화면까지 앱 안 경로대로 쌓는다.
 * 로그인하지 않았으면 로그인부터 보여 주고 로그인 뒤에 대상 화면으로 보낸다.
 */
private fun startKeys(startDeepLink: DeepLink?, isLoggedIn: Boolean): Array<SettyScreen> = when {
    startDeepLink == null -> arrayOf(SettyScreen.Login)
    startDeepLink == DeepLink.Invalid -> arrayOf(if (isLoggedIn) SettyScreen.Home else SettyScreen.Login)
    startDeepLink.requiresLogin && !isLoggedIn -> arrayOf(SettyScreen.Login)
    else -> startDeepLink.toBackStack().toTypedArray()
}

private const val INVALID_LINK_MESSAGE = "열 수 없는 링크예요"
