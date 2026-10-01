package com.aksworns22.setty.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.aksworns22.setty.model.Listing
import com.aksworns22.setty.model.ListingCategory
import com.aksworns22.setty.model.formatWon
import com.aksworns22.setty.ui.theme.Paperlogy
import com.aksworns22.setty.ui.theme.SettyTheme

@Composable
fun HomeScreen(
    onListingClick: (Long) -> Unit,
    onProfileClick: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                HomeEvent.REFRESH_ERROR -> {
                    snackbarHostState.showSnackbar("가구 목록을 새로고침하지 못했습니다")
                }
            }
        }
    }
    HomeContent(
        uiState = uiState.value,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::loadListings,
        onCategorySelected = viewModel::onCategorySelected,
        onListingClick = onListingClick,
        onProfileClick = onProfileClick,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onCategorySelected: (ListingCategory?) -> Unit,
    onListingClick: (Long) -> Unit,
    onProfileClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val textFieldState = rememberTextFieldState()
    val searchBarState = rememberSearchBarState()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppBarWithSearch(
                state = searchBarState,
                inputField = {
                    SearchBarDefaults.InputField(
                        textFieldState = textFieldState,
                        searchBarState = searchBarState,
                        onSearch = {},
                        placeholder = {
                            Text(
                                text = "중고 가구 검색",
                                fontFamily = Paperlogy,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {},
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_menu_24),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = { IconButton(
                    onClick = onProfileClick,
                ) {
                    Image(
                        painter = painterResource(R.drawable.profile),
                        contentDescription = "내 매물",
                    )
                } },
            )

            ExpandedFullScreenSearchBar(
                state = searchBarState,
                inputField = {
                    SearchBarDefaults.InputField(
                        textFieldState = textFieldState,
                        searchBarState = searchBarState,
                        onSearch = {},
                        placeholder = {
                            Text(
                                text = "중고 가구 검색",
                                fontFamily = Paperlogy,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    )
                },
            ) {

            }
        },
    ) { innerPadding ->
        val pullToRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            state = pullToRefreshState,
            enabled = !uiState.isLoading,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> {
                    LoadingIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.hasError -> {
                    ListingsMessage(
                        title = "가구 목록을 불러오지 못했어요",
                        description = "네트워크 상태를 확인한 뒤 다시 시도해주세요.",
                        modifier = Modifier.align(Alignment.Center),
                        action = {
                            Button(onClick = onRetry) {
                                Text("다시 시도", style = MaterialTheme.typography.bodyLargeEmphasized)
                            }
                        },
                    )
                }

                else -> {
                    ListingList(
                        uiState = uiState,
                        onCategorySelected = onCategorySelected,
                        onListingClick = onListingClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun ListingList(
    uiState: HomeUiState,
    onCategorySelected: (ListingCategory?) -> Unit,
    onListingClick: (Long) -> Unit,
) {
    val listings = uiState.visibleListings
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(
            ListItemDefaults.SegmentedGap
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            ListingHeader(
                count = listings.size,
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = onCategorySelected,
            )
        }
        if (listings.isEmpty()) {
            item(key = "empty") {
                ListingsMessage(
                    title = "아직 등록된 가구가 없어요",
                    description = "곧 새로운 가구가 올라올 거예요.",
                    modifier = Modifier.padding(top = 48.dp),
                )
            }
        }
        itemsIndexed(listings, key = { _, listing -> listing.id }) { index, listing ->
            ListingItem(
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
private fun ListingHeader(
    count: Int,
    selectedCategory: ListingCategory?,
    onCategorySelected: (ListingCategory?) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "지금 판매 중인 가구",
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${count}개",
                style = MaterialTheme.typography.bodyLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        CategoryButtonGroup(
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategoryButtonGroup(
    selectedCategory: ListingCategory?,
    onCategorySelected: (ListingCategory?) -> Unit,
) {
    val categories: List<ListingCategory?> = listOf(null) + ListingCategory.entries
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        categories.forEachIndexed { index, category ->
            ToggleButton(
                checked = selectedCategory == category,
                onCheckedChange = { onCategorySelected(category) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    categories.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(
                    text = category?.label ?: "전체",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ListingItem(
    listing: Listing,
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
            ListingThumbnail(listing = listing)
        },
        overlineContent = {
            Text(
                text = "${listing.categoryLabel} · ${listing.conditionGrade}급",
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
                Text(
                    text = "배송비 ${formatWon(listing.deliveryFee)} 포함",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        trailingContent = {
            Text(
                text = listing.registeredDateLabel,
                style = MaterialTheme.typography.labelMedium,
            )
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
private fun ListingThumbnail(listing: Listing) {
    val shape = MaterialShapes.Square.toShape()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .clip(shape)
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
private fun ListingsMessage(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.tertiaryContainer),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

private val previewListings = listOf(
    Listing(
        id = 1,
        title = "원목 4인용 식탁",
        thumbnailUrl = null,
        price = 120000,
        deliveryFee = 30000,
        totalPrice = 150000,
        category = ListingCategory.TABLE,
        conditionGrade = "A",
        createdAt = "2026-09-30T05:03:41Z",
    ),
    Listing(
        id = 2,
        title = "패브릭 3인용 소파",
        thumbnailUrl = null,
        price = 250000,
        deliveryFee = 50000,
        totalPrice = 300000,
        category = ListingCategory.SOFA,
        conditionGrade = "S",
        createdAt = "2026-09-28T05:03:41Z",
    ),
    Listing(
        id = 3,
        title = "화이트 5단 수납장",
        thumbnailUrl = null,
        price = 45000,
        deliveryFee = 20000,
        totalPrice = 65000,
        category = ListingCategory.STORAGE,
        conditionGrade = "B",
        createdAt = "2026-09-21T05:03:41Z",
    ),
)

@Preview
@Composable
private fun HomeContentPreview() {
    SettyTheme {
        HomeContent(
            uiState = HomeUiState(listings = previewListings, isLoading = false),
            onRefresh = {},
            onRetry = {},
            onCategorySelected = {},
            onListingClick = {},
            onProfileClick = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun HomeContentErrorPreview() {
    SettyTheme {
        HomeContent(
            uiState = HomeUiState(isLoading = false, hasError = true),
            onRefresh = {},
            onRetry = {},
            onCategorySelected = {},
            onListingClick = {},
            onProfileClick = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
