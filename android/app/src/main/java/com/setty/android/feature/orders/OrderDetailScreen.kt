package com.setty.android.feature.orders

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.setty.android.R
import com.setty.android.feature.common.BackIconButton
import com.setty.android.feature.common.ErrorContent
import com.setty.android.feature.common.FurnitureThumbnail
import com.setty.android.feature.common.LoadUiState
import com.setty.android.feature.common.LoadingContent
import com.setty.android.feature.common.SectionTitle
import com.setty.android.model.DeliveryStatus
import com.setty.android.model.DeliverySteps
import com.setty.android.model.Order
import com.setty.android.model.OrderStatus
import com.setty.android.model.OrderedFurniture
import com.setty.android.model.formatWon
import com.setty.android.model.isCancelFlow
import com.setty.android.model.label
import com.setty.android.model.progress
import com.setty.android.model.statusDescription
import com.setty.android.model.statusLabel
import com.setty.android.ui.theme.Paperlogy
import com.setty.android.ui.theme.SettyTheme

@Composable
fun OrderDetailScreen(
    orderId: Long,
    onBack: () -> Unit,
    onFurnitureClick: (Long) -> Unit,
    onRequireLogin: () -> Unit,
    viewModel: OrderDetailViewModel = viewModel(
        key = "order-detail-$orderId",
        factory = OrderDetailViewModel.factory(orderId),
    ),
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
                is OrderDetailEvent.Message -> snackbarHostState.showSnackbar(event.message)
                OrderDetailEvent.RequireLogin -> onRequireLogin()
            }
        }
    }
    OrderDetailContent(
        orderId = orderId,
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = { viewModel.load() },
        onRefresh = viewModel::refresh,
        onRequireLogin = onRequireLogin,
        onFurnitureClick = onFurnitureClick,
        onCancel = viewModel::cancel,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OrderDetailContent(
    orderId: Long,
    uiState: OrderDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onRequireLogin: () -> Unit,
    onFurnitureClick: (Long) -> Unit,
    onCancel: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var isCancelDialogVisible by rememberSaveable { mutableStateOf(false) }
    val order = (uiState.order as? LoadUiState.Success)?.data
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = "주문 상세",
                        fontFamily = Paperlogy,
                        fontWeight = FontWeight.Black,
                    )
                },
                subtitle = { Text(text = "주문 #$orderId", fontFamily = Paperlogy) },
                navigationIcon = { BackIconButton(onBack = onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (order != null && (order.canCancel || uiState.isCancelling)) {
                CancelBar(
                    isCancelling = uiState.isCancelling,
                    onCancelClick = { isCancelDialogVisible = true },
                )
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        when (val state = uiState.order) {
            LoadUiState.Loading -> LoadingContent(modifier = Modifier.padding(innerPadding))

            is LoadUiState.Error -> ErrorContent(
                message = state.message,
                isUnauthorized = state.isUnauthorized,
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
                    OrderDetailBody(order = state.data, onFurnitureClick = onFurnitureClick)
                }
            }
        }
    }
    if (isCancelDialogVisible) {
        AlertDialog(
            onDismissRequest = { isCancelDialogVisible = false },
            title = { Text("주문을 취소할까요?") },
            text = { Text("배송 기사가 이미 배정됐다면 취소가 거절될 수 있어요.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        isCancelDialogVisible = false
                        onCancel()
                    },
                ) {
                    Text("주문 취소")
                }
            },
            dismissButton = {
                TextButton(onClick = { isCancelDialogVisible = false }) {
                    Text("닫기")
                }
            },
        )
    }
}

@Composable
private fun OrderDetailBody(
    order: Order,
    onFurnitureClick: (Long) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "status") { OrderStatusCard(order = order) }
        val listing = order.listing
        if (listing != null) {
            item(key = "listing") {
                OrderedFurnitureSection(
                    listing = listing,
                    onClick = { onFurnitureClick(listing.id) },
                )
            }
            item(key = "payment") { PaymentSection(listing = listing) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OrderStatusCard(order: Order) {
    val isCancelFlow = order.isCancelFlow
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (isCancelFlow) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (isCancelFlow) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        modifier = Modifier.fillMaxWidth(),
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
                        .clip(
                            if (isCancelFlow) {
                                MaterialShapes.SoftBurst.toShape()
                            } else {
                                MaterialShapes.Cookie9Sided.toShape()
                            }
                        )
                        .background(
                            if (isCancelFlow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.outline_local_shipping_24),
                        contentDescription = null,
                        tint = if (isCancelFlow) {
                            MaterialTheme.colorScheme.onError
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        },
                        modifier = Modifier.size(28.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = order.statusLabel,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                    )
                    Text(
                        text = order.statusDescription,
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                    )
                }
            }
            if (!isCancelFlow && order.orderStatus != OrderStatus.PENDING) {
                DeliveryProgress(deliveryStatus = order.deliveryStatus)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeliveryProgress(deliveryStatus: DeliveryStatus) {
    val progress by animateFloatAsState(
        targetValue = deliveryStatus.progress,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "deliveryProgress",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            DeliverySteps.forEach { step ->
                val isReached = DeliverySteps.indexOf(step) <= DeliverySteps.indexOf(deliveryStatus)
                Text(
                    text = step.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (step == deliveryStatus) FontWeight.Black else FontWeight.Normal,
                    color = if (isReached) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OrderedFurnitureSection(
    listing: OrderedFurniture,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(text = "주문한 가구")
        SegmentedListItem(
            onClick = onClick,
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
            leadingContent = {
                FurnitureThumbnail(
                    imageUrl = listing.thumbnailUrl,
                    placeholder = "가구",
                    contentDescription = listing.name,
                )
            },
            trailingContent = {
                Icon(
                    painter = painterResource(R.drawable.outline_chevron_right_24),
                    contentDescription = null,
                )
            },
        ) {
            Text(
                text = listing.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PaymentSection(listing: OrderedFurniture) {
    val rows = listOf(
        "매물 가격" to listing.price,
        "배송비" to listing.deliveryFee,
    )
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        SectionTitle(text = "결제 정보")
        rows.forEachIndexed { index, (label, amount) ->
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index = index, count = rows.size + 1),
                trailingContent = {
                    Text(
                        text = formatWon(amount),
                        style = MaterialTheme.typography.bodyLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Surface(
            shape = ListItemDefaults.segmentedShapes(index = rows.size, count = rows.size + 1).shape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            ) {
                Text(text = "총 결제 금액", style = MaterialTheme.typography.titleMediumEmphasized)
                Text(
                    text = formatWon(listing.totalPrice),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CancelBar(
    isCancelling: Boolean,
    onCancelClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (isCancelling) {
            LoadingIndicator()
        } else {
            FilledTonalButton(
                onClick = onCancelClick,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonDefaults.MediumContainerHeight),
            ) {
                Text("주문 취소하기", style = MaterialTheme.typography.bodyLargeEmphasized)
            }
        }
    }
}

@Preview
@Composable
private fun OrderDetailContentPreview() {
    SettyTheme {
        OrderDetailContent(
            orderId = 100,
            uiState = OrderDetailUiState(
                order = LoadUiState.Success(
                    Order(
                        id = 100,
                        listing = OrderedFurniture(1, "원목 의자", null, 120000, 10000),
                        orderStatus = OrderStatus.CONFIRMED,
                        deliveryStatus = DeliveryStatus.ACCEPTED,
                    )
                )
            ),
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onRetry = {},
            onRefresh = {},
            onRequireLogin = {},
            onFurnitureClick = {},
            onCancel = {},
        )
    }
}
