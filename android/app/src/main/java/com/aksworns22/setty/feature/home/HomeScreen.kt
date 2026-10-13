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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onFurnitureClick: (Long) -> Unit,
    onProfileClick: () -> Unit,
    onRequireDong: () -> Unit,
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

                HomeEvent.LOAD_MORE_ERROR -> {
                    snackbarHostState.showSnackbar("가구를 더 불러오지 못했습니다")
                }

                HomeEvent.REQUIRE_DONG -> onRequireDong()
            }
        }
    }
    HomeContent(
        uiState = uiState.value,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::loadListings,
        onCategorySelected = viewModel::onCategorySelected,
        onMaxDeliveryFeeSelected = viewModel::onMaxDeliveryFeeSelected,
        onLoadMore = viewModel::loadMore,
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
    onMaxDeliveryFeeSelected: (Int?) -> Unit,
    onLoadMore: () -> Unit,
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
                        onMaxDeliveryFeeSelected = onMaxDeliveryFeeSelected,
                        onLoadMore = onLoadMore,
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
    onMaxDeliveryFeeSelected: (Int?) -> Unit,
    onLoadMore: () -> Unit,
    onListingClick: (Long) -> Unit,
) {
    val listings = uiState.visibleFurnitures
    val listState = rememberLazyListState()
    // 배송비로 거른 목록은 끝에 가까워지면 다음 페이지를 이어 받는다.
    // 받은 뒤에도 끝이 보이면 항목 수가 바뀌었으므로 다시 받고, 실패해 항목 수가 그대로면 다시 받지 않는다.
    LaunchedEffect(listState, uiState.hasMore) {
        if (!uiState.hasMore) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val isNearEnd = lastVisibleIndex >= layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
            if (isNearEnd) layoutInfo.totalItemsCount else null
        }
            .distinctUntilChanged()
            .filterNotNull()
            .collect { onLoadMore() }
    }
    LazyColumn(
        state = listState,
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
                maxDeliveryFee = uiState.maxDeliveryFee,
                onMaxDeliveryFeeSelected = onMaxDeliveryFeeSelected,
            )
        }
        if (listings.isEmpty() && !uiState.hasMore) {
            item(key = "empty") {
                if (uiState.maxDeliveryFee == null) {
                    ListingsMessage(
                        title = "아직 등록된 가구가 없어요",
                        description = "곧 새로운 가구가 올라올 거예요.",
                        modifier = Modifier.padding(top = 48.dp),
                    )
                } else {
                    ListingsMessage(
                        title = "배송비 예산 안의 가구가 없어요",
                        description = "예산을 늘리거나 필터를 해제해 보세요.",
                        modifier = Modifier.padding(top = 48.dp),
                    )
                }
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
        if (uiState.hasMore) {
            item(key = "loadMore") {
                LoadMoreFooter(
                    isLoadingMore = uiState.isLoadingMore,
                    onLoadMore = onLoadMore,
                )
            }
        }
    }
}

// 마지막에서 이만큼 앞의 항목이 보이면 다음 페이지를 받는다
private const val LOAD_MORE_THRESHOLD = 3

/** 다음 페이지를 받는 중이면 로딩을, 아니면 직접 이어 받을 버튼을 보여준다. 자동으로 받다 실패했을 때 다시 시도하는 용도다. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadMoreFooter(
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        if (isLoadingMore) {
            LoadingIndicator()
        } else {
            TextButton(onClick = onLoadMore) {
                Text("더 보기", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ListingHeader(
    selectedCategory: FurnitureCategory?,
    onCategorySelected: (FurnitureCategory?) -> Unit,
    maxDeliveryFee: Int?,
    onMaxDeliveryFeeSelected: (Int?) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
    ) {
        CategoryButtonGroup(
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected,
        )
        DeliveryFeeFilter(
            maxDeliveryFee = maxDeliveryFee,
            onMaxDeliveryFeeSelected = onMaxDeliveryFeeSelected,
        )
    }
}

/**
 * 배송비 예산 칩. "전체"는 필터를 해제하고, 정해 둔 예산 칩은 다시 누르면 해제한다.
 * 정해 둔 예산에 없는 값은 직접 입력해 고르고, 고른 값은 직접 입력 칩에 보여준다.
 */
@Composable
private fun DeliveryFeeFilter(
    maxDeliveryFee: Int?,
    onMaxDeliveryFeeSelected: (Int?) -> Unit,
) {
    var isBudgetSheetVisible by rememberSaveable { mutableStateOf(false) }
    val customBudget = maxDeliveryFee?.takeIf { it !in DeliveryFeeBudget.PRESETS }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        FilterChip(
            selected = maxDeliveryFee == null,
            onClick = { onMaxDeliveryFeeSelected(null) },
            label = { Text("배송비 전체", style = MaterialTheme.typography.labelLarge) },
        )
        DeliveryFeeBudget.PRESETS.forEach { budget ->
            val isSelected = maxDeliveryFee == budget
            FilterChip(
                selected = isSelected,
                onClick = { onMaxDeliveryFeeSelected(if (isSelected) null else budget) },
                label = {
                    Text(
                        text = "${DeliveryFeeBudget.format(budget)} 이하",
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            )
        }
        FilterChip(
            selected = customBudget != null,
            onClick = { isBudgetSheetVisible = true },
            label = {
                Text(
                    text = customBudget?.let { "${DeliveryFeeBudget.format(it)} 이하" } ?: "직접 설정",
                    style = MaterialTheme.typography.labelLarge,
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.outline_edit_24),
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
        )
    }
    if (isBudgetSheetVisible) {
        DeliveryFeeBudgetSheet(
            initialBudget = maxDeliveryFee ?: DeliveryFeeBudget.DEFAULT,
            onDismiss = { isBudgetSheetVisible = false },
            onSelect = { budget ->
                isBudgetSheetVisible = false
                onMaxDeliveryFeeSelected(budget)
            },
        )
    }
}

/** 슬라이더로 배송비 예산을 고르는 시트. [onSelect]에 null을 넘기면 필터를 해제한다. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeliveryFeeBudgetSheet(
    initialBudget: Int,
    onDismiss: () -> Unit,
    onSelect: (Int?) -> Unit,
) {
    var budget by rememberSaveable { mutableIntStateOf(initialBudget.coerceIn(DeliveryFeeBudget.MIN, DeliveryFeeBudget.MAX)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Text(
                text = "배송비 예산",
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            Text(
                text = "내 행정동까지 이 금액 안으로 배송되는 가구만 보여드려요.",
                style = MaterialTheme.typography.bodyMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "${DeliveryFeeBudget.format(budget)} 이하",
                style = MaterialTheme.typography.displaySmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 24.dp),
            )
            Slider(
                value = budget.toFloat(),
                onValueChange = { budget = DeliveryFeeBudget.snap(it) },
                valueRange = DeliveryFeeBudget.MIN.toFloat()..DeliveryFeeBudget.MAX.toFloat(),
                steps = DeliveryFeeBudget.SLIDER_STEPS,
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = DeliveryFeeBudget.format(DeliveryFeeBudget.MIN),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = DeliveryFeeBudget.format(DeliveryFeeBudget.MAX),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
            ) {
                OutlinedButton(
                    onClick = { onSelect(null) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .weight(1f)
                        .height(ButtonDefaults.MediumContainerHeight),
                ) {
                    Text("필터 해제", style = MaterialTheme.typography.bodyLargeEmphasized)
                }
                Button(
                    onClick = { onSelect(budget) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .weight(1f)
                        .height(ButtonDefaults.MediumContainerHeight),
                ) {
                    Text("적용", style = MaterialTheme.typography.bodyLargeEmphasized)
                }
            }
        }
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
                text = listOfNotNull(
                    furniture.categoryLabel,
                    "${furniture.conditionGrade}급",
                    furniture.dongName,
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
        dongName = "마포구 서교동",
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
            onMaxDeliveryFeeSelected = {},
            onLoadMore = {},
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
            onMaxDeliveryFeeSelected = {},
            onLoadMore = {},
            onListingClick = {},
            onProfileClick = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
