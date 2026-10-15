package com.aksworns22.setty.feature.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aksworns22.setty.R
import com.aksworns22.setty.core.model.FurnitureCategory
import com.aksworns22.setty.core.model.FurnitureSummary
import com.aksworns22.setty.feature.common.BackIconButton
import com.aksworns22.setty.feature.common.ErrorContent
import com.aksworns22.setty.feature.common.FurnitureThumbnail
import com.aksworns22.setty.feature.common.LoadUiState
import com.aksworns22.setty.feature.common.LoadingContent
import com.aksworns22.setty.feature.common.SettyMessage
import com.aksworns22.setty.model.categoryLabel
import com.aksworns22.setty.model.formatWon
import com.aksworns22.setty.ui.theme.Paperlogy
import com.aksworns22.setty.ui.theme.SettyTheme

@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onFurnitureClick: (Long) -> Unit,
    onRequireLogin: () -> Unit,
    viewModel: FavoritesViewModel = viewModel(factory = FavoritesViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // 뒤로 가기로 돌아온 화면은 목록을 유지한 채 갱신하고, 새로 연 화면은 처음부터 불러온다
    var isStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.load(reset = !isStarted)
        isStarted = true
        viewModel.event.collect { event ->
            when (event) {
                is FavoritesEvent.Message -> snackbarHostState.showSnackbar(event.message)
                FavoritesEvent.RequireLogin -> onRequireLogin()
            }
        }
    }
    FavoritesContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = { viewModel.load() },
        onRefresh = viewModel::refresh,
        onRequireLogin = onRequireLogin,
        onFurnitureClick = onFurnitureClick,
        onRemove = viewModel::remove,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FavoritesContent(
    uiState: FavoritesUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onRequireLogin: () -> Unit,
    onFurnitureClick: (Long) -> Unit,
    onRemove: (FurnitureSummary) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = "찜한 가구",
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
        when (val furnitures = uiState.furnitures) {
            LoadUiState.Loading -> LoadingContent(modifier = Modifier.padding(innerPadding))

            is LoadUiState.Error -> ErrorContent(
                message = furnitures.message,
                isUnauthorized = furnitures.isUnauthorized,
                onRetry = onRetry,
                onRequireLogin = onRequireLogin,
                modifier = Modifier.padding(innerPadding),
            )

            is LoadUiState.Success -> {
                val pullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    state = pullToRefreshState,
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
                    FavoriteList(
                        furnitures = furnitures.data,
                        onFurnitureClick = onFurnitureClick,
                        onRemove = onRemove,
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteList(
    furnitures: List<FurnitureSummary>,
    onFurnitureClick: (Long) -> Unit,
    onRemove: (FurnitureSummary) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (furnitures.isEmpty()) {
            item(key = "empty") {
                SettyMessage(
                    title = "아직 찜한 가구가 없어요",
                    description = "마음에 드는 가구의 하트를 눌러 모아보세요.",
                    modifier = Modifier.padding(top = 48.dp),
                )
            }
        }
        itemsIndexed(furnitures, key = { _, furniture -> furniture.id }) { index, furniture ->
            FavoriteItem(
                furniture = furniture,
                index = index,
                count = furnitures.size,
                onClick = { onFurnitureClick(furniture.id) },
                onRemove = { onRemove(furniture) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FavoriteItem(
    furniture: FurnitureSummary,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        leadingContent = {
            FurnitureThumbnail(
                imageUrl = furniture.thumbnailUrl,
                placeholder = furniture.categoryLabel,
                contentDescription = furniture.title,
            )
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
            IconToggleButton(
                checked = true,
                onCheckedChange = { onRemove() },
                shapes = IconButtonDefaults.toggleableShapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_favorite_24),
                    contentDescription = "찜 해제",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
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

@Preview
@Composable
private fun FavoritesContentPreview() {
    SettyTheme {
        FavoritesContent(
            uiState = FavoritesUiState(
                furnitures = LoadUiState.Success(
                    listOf(
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
                    )
                )
            ),
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onRetry = {},
            onRefresh = {},
            onRequireLogin = {},
            onFurnitureClick = {},
            onRemove = {},
        )
    }
}
