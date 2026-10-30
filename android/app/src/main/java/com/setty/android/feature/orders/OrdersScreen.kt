package com.setty.android.feature.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.setty.android.feature.common.BackIconButton
import com.setty.android.feature.common.ErrorContent
import com.setty.android.feature.common.FurnitureThumbnail
import com.setty.android.feature.common.LoadUiState
import com.setty.android.feature.common.LoadingContent
import com.setty.android.feature.common.SettyMessage
import com.setty.android.feature.common.StatusBadge
import com.setty.android.model.DeliveryStatus
import com.setty.android.model.Order
import com.setty.android.model.OrderStatus
import com.setty.android.model.OrderedFurniture
import com.setty.android.model.formatWon
import com.setty.android.model.isCancelFlow
import com.setty.android.model.statusLabel
import com.setty.android.ui.theme.Paperlogy
import com.setty.android.ui.theme.SettyTheme

@Composable
fun OrdersScreen(
    onBack: () -> Unit,
    onOrderClick: (Long) -> Unit,
    onRequireLogin: () -> Unit,
    viewModel: OrdersViewModel = viewModel(factory = OrdersViewModel.Factory),
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
                is OrdersEvent.Message -> snackbarHostState.showSnackbar(event.message)
                OrdersEvent.RequireLogin -> onRequireLogin()
            }
        }
    }
    OrdersContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = { viewModel.load() },
        onRefresh = viewModel::refresh,
        onRequireLogin = onRequireLogin,
        onOrderClick = onOrderClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OrdersContent(
    uiState: OrdersUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onRequireLogin: () -> Unit,
    onOrderClick: (Long) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = "주문 내역",
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
        when (val orders = uiState.orders) {
            LoadUiState.Loading -> LoadingContent(modifier = Modifier.padding(innerPadding))

            is LoadUiState.Error -> ErrorContent(
                message = orders.message,
                isUnauthorized = orders.isUnauthorized,
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
                    OrderList(orders = orders.data, onOrderClick = onOrderClick)
                }
            }
        }
    }
}

@Composable
private fun OrderList(
    orders: List<Order>,
    onOrderClick: (Long) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (orders.isEmpty()) {
            item(key = "empty") {
                SettyMessage(
                    title = "아직 주문한 가구가 없어요",
                    description = "마음에 드는 가구를 찾아 주문해 보세요.",
                    modifier = Modifier.padding(top = 48.dp),
                )
            }
        }
        itemsIndexed(orders, key = { _, order -> order.id }) { index, order ->
            OrderItem(
                order = order,
                index = index,
                count = orders.size,
                onClick = { onOrderClick(order.id) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OrderItem(
    order: Order,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listing = order.listing
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        leadingContent = {
            FurnitureThumbnail(
                imageUrl = listing?.thumbnailUrl,
                placeholder = "가구",
                contentDescription = listing?.name,
            )
        },
        overlineContent = {
            Text(text = "주문 #${order.id}", style = MaterialTheme.typography.labelMedium)
        },
        supportingContent = listing?.let {
            {
                Text(
                    text = formatWon(it.totalPrice),
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        trailingContent = { OrderStatusBadge(order = order) },
        modifier = modifier,
    ) {
        Text(
            text = listing?.name ?: "삭제된 가구",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun OrderStatusBadge(
    order: Order,
    modifier: Modifier = Modifier,
) {
    val (containerColor, contentColor) = when {
        order.isCancelFlow -> MaterialTheme.colorScheme.errorContainer to
                MaterialTheme.colorScheme.onErrorContainer

        order.deliveryStatus == DeliveryStatus.DELIVERED ->
            MaterialTheme.colorScheme.surfaceContainerHighest to
                    MaterialTheme.colorScheme.onSurfaceVariant

        else -> MaterialTheme.colorScheme.primaryContainer to
                MaterialTheme.colorScheme.onPrimaryContainer
    }
    StatusBadge(
        text = order.statusLabel,
        containerColor = containerColor,
        contentColor = contentColor,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun OrdersContentPreview() {
    SettyTheme {
        OrdersContent(
            uiState = OrdersUiState(
                orders = LoadUiState.Success(
                    listOf(
                        Order(
                            id = 100,
                            listing = OrderedFurniture(1, "원목 의자", null, 120000, 10000),
                            orderStatus = OrderStatus.CONFIRMED,
                            deliveryStatus = DeliveryStatus.ACCEPTED,
                        ),
                        Order(
                            id = 99,
                            listing = OrderedFurniture(2, "패브릭 소파", null, 250000, 50000),
                            orderStatus = OrderStatus.CANCELLED,
                            deliveryStatus = DeliveryStatus.REQUESTED,
                        ),
                    )
                )
            ),
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onRetry = {},
            onRefresh = {},
            onRequireLogin = {},
            onOrderClick = {},
        )
    }
}
