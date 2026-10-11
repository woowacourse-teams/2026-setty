package com.aksworns22.setty.feature.mylistings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import com.aksworns22.setty.core.model.SaleStatus
import com.aksworns22.setty.feature.common.BackIconButton
import com.aksworns22.setty.feature.common.ErrorContent
import com.aksworns22.setty.feature.common.FurnitureThumbnail
import com.aksworns22.setty.feature.common.LoadUiState
import com.aksworns22.setty.feature.common.LoadingContent
import com.aksworns22.setty.feature.common.SaleStatusBadge
import com.aksworns22.setty.feature.common.SettyMessage
import com.aksworns22.setty.model.MyFurniture
import com.aksworns22.setty.model.categoryLabel
import com.aksworns22.setty.model.formatWon
import com.aksworns22.setty.model.registeredDateLabel
import com.aksworns22.setty.ui.theme.Paperlogy
import com.aksworns22.setty.ui.theme.SettyTheme

@Composable
fun MyListingsScreen(
    onBack: () -> Unit,
    onFurnitureClick: (Long) -> Unit,
    onCreateClick: () -> Unit,
    onEditClick: (Long) -> Unit,
    onRequireLogin: () -> Unit,
    viewModel: MyListingsViewModel = viewModel(factory = MyListingsViewModel.Factory),
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
                is MyListingsEvent.Message -> snackbarHostState.showSnackbar(event.message)
                MyListingsEvent.RequireLogin -> onRequireLogin()
            }
        }
    }
    MyListingsContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = { viewModel.load() },
        onRefresh = viewModel::refresh,
        onRequireLogin = onRequireLogin,
        onFurnitureClick = onFurnitureClick,
        onCreateClick = onCreateClick,
        onEditClick = onEditClick,
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MyListingsContent(
    uiState: MyListingsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onRequireLogin: () -> Unit,
    onFurnitureClick: (Long) -> Unit,
    onCreateClick: () -> Unit,
    onEditClick: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val isFabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    var deleteTarget by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = "내 판매 가구",
                        fontFamily = Paperlogy,
                        fontWeight = FontWeight.Black,
                    )
                },
                navigationIcon = { BackIconButton(onBack = onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            MediumExtendedFloatingActionButton(
                text = { Text("가구 등록", fontFamily = Paperlogy, fontWeight = FontWeight.Bold) },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.outline_add_24),
                        contentDescription = null,
                    )
                },
                onClick = onCreateClick,
                expanded = isFabExpanded,
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
                    MyFurnitureList(
                        furnitures = furnitures.data,
                        deletingId = uiState.deletingId,
                        listState = listState,
                        onFurnitureClick = onFurnitureClick,
                        onCreateClick = onCreateClick,
                        onEditClick = onEditClick,
                        onDeleteClick = { deleteTarget = it },
                    )
                }
            }
        }
    }
    deleteTarget?.let { listingId ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("가구를 삭제할까요?") },
            text = { Text("삭제한 가구는 목록에서 사라지고 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        onDelete(listingId)
                    },
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("취소")
                }
            },
        )
    }
}

@Composable
private fun MyFurnitureList(
    furnitures: List<MyFurniture>,
    deletingId: Long?,
    listState: LazyListState,
    onFurnitureClick: (Long) -> Unit,
    onCreateClick: () -> Unit,
    onEditClick: (Long) -> Unit,
    onDeleteClick: (Long) -> Unit,
) {
    LazyColumn(
        state = listState,
        // FAB에 마지막 항목이 가리지 않도록 아래 여백을 넉넉히 둔다
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (furnitures.isEmpty()) {
            item(key = "empty") {
                SettyMessage(
                    title = "아직 등록한 가구가 없어요",
                    description = "쓰지 않는 가구를 올리고 새 주인을 찾아보세요.",
                    modifier = Modifier.padding(top = 48.dp),
                    action = {
                        Button(onClick = onCreateClick, shapes = ButtonDefaults.shapes()) {
                            Text("가구 등록하기", style = MaterialTheme.typography.bodyLargeEmphasized)
                        }
                    },
                )
            }
        }
        itemsIndexed(furnitures, key = { _, furniture -> furniture.id }) { index, furniture ->
            MyFurnitureItem(
                furniture = furniture,
                index = index,
                count = furnitures.size,
                isDeleting = deletingId == furniture.id,
                onClick = { onFurnitureClick(furniture.id) },
                onEditClick = { onEditClick(furniture.id) },
                onDeleteClick = { onDeleteClick(furniture.id) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MyFurnitureItem(
    furniture: MyFurniture,
    index: Int,
    count: Int,
    isDeleting: Boolean,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
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
                text = "${furniture.categoryLabel} · ${furniture.conditionGrade}급 · ${furniture.registeredDateLabel}",
                style = MaterialTheme.typography.labelMedium,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = formatWon(furniture.price),
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SaleStatusBadge(saleStatus = furniture.saleStatus)
                    if (furniture.hasPurchaseRequest) {
                        Text(
                            text = "구매 신청 있음",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        },
        trailingContent = {
            if (isDeleting) {
                LoadingIndicator()
            } else {
                Column {
                    IconButton(
                        onClick = onEditClick,
                        enabled = furniture.canUpdate,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_edit_24),
                            contentDescription = "수정",
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        enabled = furniture.canDelete,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_delete_24),
                            contentDescription = "삭제",
                        )
                    }
                }
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
private fun MyListingsContentPreview() {
    SettyTheme {
        MyListingsContent(
            uiState = MyListingsUiState(
                furnitures = LoadUiState.Success(
                    listOf(
                        MyFurniture(
                            id = 1,
                            title = "원목 4인용 식탁",
                            thumbnailUrl = null,
                            price = 120000,
                            deliveryFee = 30000,
                            totalPrice = 150000,
                            category = FurnitureCategory.TABLE,
                            conditionGrade = "A",
                            saleStatus = SaleStatus.AVAILABLE,
                            hasPurchaseRequest = false,
                            canUpdate = true,
                            canDelete = true,
                            createdAt = "2026-09-30T05:03:41Z",
                        ),
                        MyFurniture(
                            id = 2,
                            title = "패브릭 3인용 소파",
                            thumbnailUrl = null,
                            price = 250000,
                            deliveryFee = 50000,
                            totalPrice = 300000,
                            category = FurnitureCategory.SOFA,
                            conditionGrade = "S",
                            saleStatus = SaleStatus.RESERVED,
                            hasPurchaseRequest = true,
                            canUpdate = false,
                            canDelete = false,
                            createdAt = "2026-09-28T05:03:41Z",
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
            onCreateClick = {},
            onEditClick = {},
            onDelete = {},
        )
    }
}
