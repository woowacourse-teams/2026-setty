package com.aksworns22.setty.feature.sellerpage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.aksworns22.setty.R
import com.aksworns22.setty.model.ListingCategory
import com.aksworns22.setty.model.MyListing
import com.aksworns22.setty.model.SaleStatus
import com.aksworns22.setty.model.formatWon
import com.aksworns22.setty.ui.component.SaleStatusBadge
import com.aksworns22.setty.ui.theme.Paperlogy
import com.aksworns22.setty.ui.theme.SettyTheme

@Composable
fun SellerPageScreen(
    onBack: () -> Unit,
    onListingClick: (Long) -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: SellerPageViewModel = viewModel(factory = SellerPageViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // ViewModel이 Activity 범위에 남으므로 새로 들어올 때만 다시 불러오고, 상세에서 돌아올 때는 유지한다
    var isLoaded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!isLoaded) {
            isLoaded = true
            viewModel.load()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                SellerPageEvent.LOGGED_OUT -> onLoggedOut()
            }
        }
    }
    SellerPageContent(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::load,
        onListingClick = onListingClick,
        onLogout = viewModel::logout,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SellerPageContent(
    uiState: SellerPageUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onListingClick: (Long) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var isLogoutDialogVisible by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "내 매물",
                            fontFamily = Paperlogy,
                            fontWeight = FontWeight.Black,
                        )
                        if (!uiState.isLoading && uiState.error == null) {
                            Text(
                                text = " ${uiState.listings.size}개",
                                fontFamily = Paperlogy,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                subtitle = {
                    Text(
                        text = "내가 올린 가구를 한눈에 확인해요",
                        fontFamily = Paperlogy,
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_arrow_back_24),
                            contentDescription = "뒤로 가기",
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { isLogoutDialogVisible = true },
                        enabled = !uiState.isLoggingOut,
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.SmallContentPadding,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_logout_24),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.SmallIconSize),
                        )
                        Text(
                            text = "로그아웃",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        val enterSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val exitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(
            targetState = uiState,
            contentKey = {
                when {
                    it.isLoading -> "loading"
                    it.error != null -> "error"
                    else -> "content"
                }
            },
            transitionSpec = {
                fadeIn(enterSpec) togetherWith fadeOut(exitSpec)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "sellerPageState",
        ) { state ->
            when {
                state.isLoading -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LoadingIndicator()
                }

                state.error != null -> SellerPageErrorContent(
                    error = state.error,
                    onRetry = onRetry,
                    onLogout = onLogout,
                )

                else -> SellerPageBody(
                    uiState = state,
                    onListingClick = onListingClick,
                )
            }
        }
    }
    if (isLogoutDialogVisible) {
        LogoutDialog(
            onConfirm = {
                isLogoutDialogVisible = false
                onLogout()
            },
            onDismiss = { isLogoutDialogVisible = false },
        )
    }
}

@Composable
private fun SellerPageBody(
    uiState: SellerPageUiState,
    onListingClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listings = uiState.listings
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "summary") {
            SellerSummary(
                uiState = uiState,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
        if (listings.isEmpty()) {
            item(key = "empty") {
                EmptyListings(modifier = Modifier.padding(top = 24.dp))
            }
        }
        itemsIndexed(listings, key = { _, listing -> listing.id }) { index, listing ->
            MyListingItem(
                listing = listing,
                index = index,
                count = listings.size,
                onClick = { onListingClick(listing.id) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SellerSummary(
    uiState: SellerPageUiState,
    modifier: Modifier = Modifier,
) {
    val purchaseRequestCount = uiState.listings.count { it.hasPurchaseRequest }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "등록한 가구",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    Text(
                        text = if (purchaseRequestCount > 0) {
                            "구매 요청이 들어온 가구가 ${purchaseRequestCount}개 있어요"
                        } else {
                            "아직 들어온 구매 요청이 없어요"
                        },
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(MaterialShapes.Cookie12Sided.toShape())
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Text(
                        text = uiState.listings.size.toString(),
                        style = MaterialTheme.typography.headlineLargeEmphasized,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaleStatus.entries.forEach { saleStatus ->
                SaleStatusCountCard(
                    saleStatus = saleStatus,
                    count = uiState.countOf(saleStatus),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SaleStatusCountCard(
    saleStatus: SaleStatus,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val (containerColor: Color, contentColor: Color) = when (saleStatus) {
        SaleStatus.AVAILABLE -> MaterialTheme.colorScheme.secondaryContainer to
                MaterialTheme.colorScheme.onSecondaryContainer

        SaleStatus.RESERVED -> MaterialTheme.colorScheme.tertiaryContainer to
                MaterialTheme.colorScheme.onTertiaryContainer

        SaleStatus.SOLD -> MaterialTheme.colorScheme.surfaceContainerHighest to
                MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 16.dp),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            Text(
                text = saleStatus.label,
                style = MaterialTheme.typography.bodyMediumEmphasized,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MyListingItem(
    listing: MyListing,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(
            index = index,
            count = count,
        ),
        leadingContent = {
            MyListingThumbnail(listing = listing)
        },
        overlineContent = {
            Text(
                text = "${listing.categoryLabel} · ${listing.conditionGrade}급 · ${listing.registeredDateLabel}",
                style = MaterialTheme.typography.labelMedium,
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = formatWon(listing.totalPrice),
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (listing.hasPurchaseRequest) {
                    Text(
                        text = "구매 요청이 들어왔어요",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        },
        trailingContent = {
            SaleStatusBadge(saleStatus = listing.saleStatus)
        },
        modifier = modifier,
    ) {
        Text(
            text = listing.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MyListingThumbnail(listing: MyListing) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .clip(MaterialShapes.Square.toShape())
            .background(MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(
            text = listing.categoryLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        if (listing.thumbnailUrl != null) {
            AsyncImage(
                model = listing.thumbnailUrl,
                contentDescription = listing.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyListings(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.tertiaryContainer),
        )
        Text(
            text = "아직 등록한 가구가 없어요",
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "가구를 등록하면 이곳에서 판매 상태를 확인할 수 있어요.",
            style = MaterialTheme.typography.bodyMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SellerPageErrorContent(
    error: SellerPageError,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(MaterialShapes.SoftBurst.toShape())
                .background(MaterialTheme.colorScheme.errorContainer),
        )
        Text(
            text = error.toMessage(),
            style = MaterialTheme.typography.titleMediumEmphasized,
            textAlign = TextAlign.Center,
        )
        if (error == SellerPageError.UNAUTHORIZED) {
            Button(onClick = onLogout, shapes = ButtonDefaults.shapes()) {
                Text("다시 로그인", style = MaterialTheme.typography.bodyLargeEmphasized)
            }
        } else {
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text("다시 시도", style = MaterialTheme.typography.bodyLargeEmphasized)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LogoutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.outline_logout_24),
                contentDescription = null,
            )
        },
        title = {
            Text(
                text = "로그아웃할까요?",
                fontFamily = Paperlogy,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "다시 이용하려면 로그인해야 해요.",
                style = MaterialTheme.typography.bodyMediumEmphasized,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, shapes = ButtonDefaults.shapes()) {
                Text("로그아웃", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text("취소", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

private fun SellerPageError.toMessage(): String = when (this) {
    SellerPageError.UNAUTHORIZED -> "로그인이 만료되었습니다"
    SellerPageError.NETWORK_ERROR -> "네트워크 에러가 발생했습니다"
    SellerPageError.UNKNOWN_ERROR -> "알 수 없는 에러가 발생했습니다"
}

private val previewListings = listOf(
    MyListing(
        id = 1,
        title = "원목 4인용 식탁",
        thumbnailUrl = null,
        totalPrice = 150000,
        category = ListingCategory.TABLE,
        conditionGrade = "A",
        saleStatus = SaleStatus.AVAILABLE,
        hasPurchaseRequest = true,
        createdAt = "2026-09-30T05:03:41Z",
    ),
    MyListing(
        id = 2,
        title = "패브릭 3인용 소파",
        thumbnailUrl = null,
        totalPrice = 300000,
        category = ListingCategory.SOFA,
        conditionGrade = "S",
        saleStatus = SaleStatus.RESERVED,
        hasPurchaseRequest = false,
        createdAt = "2026-09-28T05:03:41Z",
    ),
    MyListing(
        id = 3,
        title = "화이트 5단 수납장",
        thumbnailUrl = null,
        totalPrice = 65000,
        category = ListingCategory.STORAGE,
        conditionGrade = "B",
        saleStatus = SaleStatus.SOLD,
        hasPurchaseRequest = false,
        createdAt = "2026-09-21T05:03:41Z",
    ),
)

@Preview
@Composable
private fun SellerPageContentPreview() {
    SettyTheme {
        SellerPageContent(
            uiState = SellerPageUiState(listings = previewListings, isLoading = false),
            onBack = {},
            onRetry = {},
            onListingClick = {},
            onLogout = {},
        )
    }
}

@Preview
@Composable
private fun SellerPageEmptyPreview() {
    SettyTheme {
        SellerPageContent(
            uiState = SellerPageUiState(isLoading = false),
            onBack = {},
            onRetry = {},
            onListingClick = {},
            onLogout = {},
        )
    }
}

@Preview
@Composable
private fun SellerPageErrorPreview() {
    SettyTheme {
        SellerPageContent(
            uiState = SellerPageUiState(isLoading = false, error = SellerPageError.NETWORK_ERROR),
            onBack = {},
            onRetry = {},
            onListingClick = {},
            onLogout = {},
        )
    }
}
