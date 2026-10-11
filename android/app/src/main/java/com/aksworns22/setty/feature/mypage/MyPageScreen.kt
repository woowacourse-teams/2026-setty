package com.aksworns22.setty.feature.mypage

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aksworns22.setty.R
import com.aksworns22.setty.feature.common.BackIconButton
import com.aksworns22.setty.feature.common.ErrorContent
import com.aksworns22.setty.feature.common.LoadUiState
import com.aksworns22.setty.feature.common.LoadingContent
import com.aksworns22.setty.feature.common.SectionTitle
import com.aksworns22.setty.model.Member
import com.aksworns22.setty.ui.theme.Paperlogy
import com.aksworns22.setty.ui.theme.SettyTheme

@Composable
fun MyPageScreen(
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onMyListingsClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onOrdersClick: () -> Unit,
    onKeywordsClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: MyPageViewModel = viewModel(factory = MyPageViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // 뒤로 가기로 돌아온 화면은 목록을 유지한 채 갱신하고, 새로 연 화면은 처음부터 불러온다
    var isStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.load(reset = !isStarted)
        isStarted = true
        viewModel.event.collect { event ->
            when (event) {
                MyPageEvent.LOGGED_OUT -> onLoggedOut()
            }
        }
    }
    MyPageContent(
        uiState = uiState,
        onBack = onBack,
        onRetry = { viewModel.load() },
        onEditProfile = onEditProfile,
        onMyListingsClick = onMyListingsClick,
        onFavoritesClick = onFavoritesClick,
        onOrdersClick = onOrdersClick,
        onKeywordsClick = onKeywordsClick,
        onNotificationsClick = onNotificationsClick,
        onLogout = viewModel::logout,
        onRequireLogin = onLoggedOut,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MyPageContent(
    uiState: MyPageUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEditProfile: () -> Unit,
    onMyListingsClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onOrdersClick: () -> Unit,
    onKeywordsClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onLogout: () -> Unit,
    onRequireLogin: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var isLogoutDialogVisible by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = "내 정보",
                        fontFamily = Paperlogy,
                        fontWeight = FontWeight.Black,
                    )
                },
                navigationIcon = { BackIconButton(onBack = onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        when (val member = uiState.member) {
            LoadUiState.Loading -> LoadingContent(modifier = Modifier.padding(innerPadding))

            is LoadUiState.Error -> ErrorContent(
                message = member.message,
                isUnauthorized = member.isUnauthorized,
                onRetry = onRetry,
                onRequireLogin = onRequireLogin,
                modifier = Modifier.padding(innerPadding),
            )

            is LoadUiState.Success -> MyPageBody(
                member = member.data,
                contentPadding = innerPadding,
                onEditProfile = onEditProfile,
                onMyListingsClick = onMyListingsClick,
                onFavoritesClick = onFavoritesClick,
                onOrdersClick = onOrdersClick,
                onKeywordsClick = onKeywordsClick,
                onNotificationsClick = onNotificationsClick,
                onLogoutClick = { isLogoutDialogVisible = true },
            )
        }
    }
    if (isLogoutDialogVisible) {
        AlertDialog(
            onDismissRequest = { isLogoutDialogVisible = false },
            title = { Text("로그아웃할까요?") },
            text = {
                Text(
                    text = "다시 이용하려면 로그인해야 합니다.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isLogoutDialogVisible = false
                        onLogout()
                    },
                    enabled = !uiState.isLoggingOut,
                ) {
                    Text("로그아웃")
                }
            },
            dismissButton = {
                TextButton(onClick = { isLogoutDialogVisible = false }) {
                    Text("취소")
                }
            },
        )
    }
}

@Composable
private fun MyPageBody(
    member: Member,
    contentPadding: PaddingValues,
    onEditProfile: () -> Unit,
    onMyListingsClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onOrdersClick: () -> Unit,
    onKeywordsClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    val tradeMenus = listOf(
        MyPageMenu("내 판매 가구", "등록한 가구를 관리해요", R.drawable.outline_sell_24, onMyListingsClick),
        MyPageMenu("찜한 가구", "마음에 둔 가구를 모아봐요", R.drawable.outline_favorite_24, onFavoritesClick),
        MyPageMenu("주문 내역", "구매한 가구의 배송을 확인해요", R.drawable.outline_local_shipping_24, onOrdersClick),
    )
    val notificationMenus = listOf(
        MyPageMenu("키워드 알림", "관심 키워드의 새 매물을 알려드려요", R.drawable.outline_notifications_24, onKeywordsClick),
        MyPageMenu("알림함", "받은 매물 알림을 모아봐요", R.drawable.outline_search_24, onNotificationsClick),
    )
    val accountMenus = listOf(
        MyPageMenu("로그아웃", null, R.drawable.outline_logout_24, onLogoutClick),
    )
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "profile") {
            ProfileCard(
                member = member,
                onEditProfile = onEditProfile,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
        item(key = "tradeTitle") { SectionTitle(text = "거래") }
        menuItems(tradeMenus)
        item(key = "notificationTitle") {
            SectionTitle(text = "알림", modifier = Modifier.padding(top = 20.dp))
        }
        menuItems(notificationMenus)
        item(key = "accountTitle") {
            SectionTitle(text = "계정", modifier = Modifier.padding(top = 20.dp))
        }
        menuItems(accountMenus)
    }
}

private data class MyPageMenu(
    val title: String,
    val description: String?,
    @param:DrawableRes val icon: Int,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.menuItems(menus: List<MyPageMenu>) {
    menus.forEachIndexed { index, menu ->
        item(key = menu.title) {
            SegmentedListItem(
                onClick = menu.onClick,
                shapes = ListItemDefaults.segmentedShapes(index = index, count = menus.size),
                leadingContent = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialShapes.Cookie6Sided.toShape())
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Icon(
                            painter = painterResource(menu.icon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                supportingContent = menu.description?.let {
                    { Text(text = it, style = MaterialTheme.typography.labelMedium) }
                },
                trailingContent = {
                    Icon(
                        painter = painterResource(R.drawable.outline_chevron_right_24),
                        contentDescription = null,
                    )
                },
            ) {
                Text(text = menu.title, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileCard(
    member: Member,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Text(
                        text = member.loginId.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineLargeEmphasized,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = member.loginId,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                    )
                    Text(
                        text = member.phoneNumber,
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                    )
                }
            }
            Text(
                text = member.address,
                style = MaterialTheme.typography.bodyLargeEmphasized.copy(fontWeight = FontWeight.Normal),
            )
            FilledTonalButton(
                onClick = onEditProfile,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("정보 수정", style = MaterialTheme.typography.bodyLargeEmphasized)
            }
        }
    }
}

@Preview
@Composable
private fun MyPageContentPreview() {
    SettyTheme {
        MyPageContent(
            uiState = MyPageUiState(
                member = LoadUiState.Success(
                    Member(
                        id = 1,
                        loginId = "settyuser1",
                        phoneNumber = "010-1234-5678",
                        address = "서울시 세티구 세티로 1",
                    )
                )
            ),
            onBack = {},
            onRetry = {},
            onEditProfile = {},
            onMyListingsClick = {},
            onFavoritesClick = {},
            onOrdersClick = {},
            onKeywordsClick = {},
            onNotificationsClick = {},
            onLogout = {},
            onRequireLogin = {},
        )
    }
}
