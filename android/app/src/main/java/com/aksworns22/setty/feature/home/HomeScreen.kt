package com.aksworns22.setty.feature.home

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.aksworns22.setty.core.model.FurnitureCategory
import com.aksworns22.setty.core.model.FurnitureSummary
import com.aksworns22.setty.model.categoryLabel
import com.aksworns22.setty.model.formatWon
import com.aksworns22.setty.model.label
import com.aksworns22.setty.model.registeredDateLabel
import com.aksworns22.setty.ui.theme.Paperlogy
import com.aksworns22.setty.ui.theme.SettyTheme
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onFurnitureClick: (Long) -> Unit,
    onProfileClick: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.onEnter()
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
        onListingClick = onFurnitureClick,
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
    onCategorySelected: (FurnitureCategory?) -> Unit,
    onListingClick: (Long) -> Unit,
    onProfileClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val textFieldState = rememberTextFieldState()
    val searchBarState = rememberSearchBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val coroutineScope = rememberCoroutineScope()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // 브랜드 제목을 액션 버튼과 같은 줄에 두고, 스크롤해도 높이가 바뀌지 않게 고정한다
            TopAppBar(
                title = {
                    Text(
                        text = "SETTY",
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                    )
                },
                subtitle = {
                    Text(
                        text = "무거운 중고 가구를 더 가볍게",
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                expandedHeight = HomeTopAppBarHeight,
                actions = {
                    IconButton(
                        onClick = { coroutineScope.launch { searchBarState.animateToExpanded() } },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_search_24),
                            contentDescription = "가구 검색",
                        )
                    }
                    ProfileButton(
                        profileInitial = uiState.profileInitial,
                        onClick = onProfileClick,
                    )
                },
                scrollBehavior = scrollBehavior,
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
                        },
                        leadingIcon = {
                            IconButton(
                                onClick = { coroutineScope.launch { searchBarState.animateToCollapsed() } },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.outline_arrow_back_24),
                                    contentDescription = "검색 닫기",
                                )
                            }
                        },
                    )
                },
            ) {
                SearchResults(
                    query = textFieldState.text.toString(),
                    results = uiState.search(textFieldState.text.toString()),
                    onListingClick = { listingId ->
                        // 상세에서 돌아왔을 때 검색창이 덮고 있지 않도록 먼저 닫는다
                        coroutineScope.launch {
                            searchBarState.snapTo(0f)
                            onListingClick(listingId)
                        }
                    },
                )
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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

private val HomeTopAppBarHeight = 72.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchResults(
    query: String,
    results: List<FurnitureSummary>,
    onListingClick: (Long) -> Unit,
) {
    when {
        query.isBlank() -> ListingsMessage(
            title = "어떤 가구를 찾으세요?",
            description = "판매 중인 가구의 제목으로 찾아드려요.",
            modifier = Modifier.padding(top = 48.dp),
        )

        results.isEmpty() -> ListingsMessage(
            title = "검색 결과가 없어요",
            description = "다른 단어로 다시 찾아보세요.",
            modifier = Modifier.padding(top = 48.dp),
        )

        else -> LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(results, key = { _, listing -> listing.id }) { index, listing ->
                ListingItem(
                    furniture = listing,
                    index = index,
                    count = results.size,
                    onClick = { onListingClick(listing.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/** 로그인한 회원 아이디의 첫 글자를 Expressive 도형 안에 보여준다. 아직 모르면 계정 아이콘을 보여준다. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileButton(
    profileInitial: String?,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier
            .padding(end = 8.dp)
            .size(48.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            if (profileInitial != null) {
                Text(
                    text = profileInitial,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.outline_account_circle_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ListingList(
    uiState: HomeUiState,
    onCategorySelected: (FurnitureCategory?) -> Unit,
    onListingClick: (Long) -> Unit,
) {
    val listings = uiState.visibleFurnitures
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(
            ListItemDefaults.SegmentedGap
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            ListingHeader(
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
                furniture = listing,
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
    selectedCategory: FurnitureCategory?,
    onCategorySelected: (FurnitureCategory?) -> Unit,
) {
    Box(modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
        CategoryButtonGroup(
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategoryButtonGroup(
    selectedCategory: FurnitureCategory?,
    onCategorySelected: (FurnitureCategory?) -> Unit,
) {
    val categories: List<FurnitureCategory?> = listOf(null) + (FurnitureCategory.entries - FurnitureCategory.ETC)
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
    furniture: FurnitureSummary,
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
            ListingThumbnail(furniture = furniture)
        },
        overlineContent = {
            Text(
                text = "${furniture.categoryLabel} · ${furniture.conditionGrade}급",
                style = MaterialTheme.typography.labelMedium,
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = formatWon(furniture.totalPrice),
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "배송비 ${formatWon(furniture.deliveryFee)} 포함",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        trailingContent = {
            Text(
                text = furniture.registeredDateLabel,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        modifier = modifier,
    ) {
        Text(
            text = furniture.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ListingThumbnail(furniture: FurnitureSummary) {
    val shape = MaterialShapes.Square.toShape()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(
            text = furniture.categoryLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        if (furniture.thumbnailUrl != null) {
            AsyncImage(
                model = furniture.thumbnailUrl,
                contentDescription = furniture.title,
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

private val previewFurnitures = listOf(
    FurnitureSummary(
        id = 1,
        title = "원목 4인용 식탁",
        thumbnailUrl = null,
        price = 120000,
        deliveryFee = 30000,
        totalPrice = 150000,
        category = FurnitureCategory.TABLE,
        conditionGrade = "A",
        createdAt = "2026-09-30T05:03:41Z",
    ),
    FurnitureSummary(
        id = 2,
        title = "패브릭 3인용 소파",
        thumbnailUrl = null,
        price = 250000,
        deliveryFee = 50000,
        totalPrice = 300000,
        category = FurnitureCategory.SOFA,
        conditionGrade = "S",
        createdAt = "2026-09-28T05:03:41Z",
    ),
    FurnitureSummary(
        id = 3,
        title = "화이트 5단 수납장",
        thumbnailUrl = null,
        price = 45000,
        deliveryFee = 20000,
        totalPrice = 65000,
        category = FurnitureCategory.STORAGE,
        conditionGrade = "B",
        createdAt = "2026-09-21T05:03:41Z",
    ),
)

@Preview
@Composable
private fun HomeContentPreview() {
    SettyTheme {
        HomeContent(
            uiState = HomeUiState(furnitures = previewFurnitures, profileInitial = "S", isLoading = false),
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
