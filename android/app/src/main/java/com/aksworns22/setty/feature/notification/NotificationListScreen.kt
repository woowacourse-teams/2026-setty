package com.aksworns22.setty.feature.notification

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
import com.aksworns22.setty.feature.common.BackIconButton
import com.aksworns22.setty.feature.common.ErrorContent
import com.aksworns22.setty.feature.common.LoadUiState
import com.aksworns22.setty.feature.common.LoadingContent
import com.aksworns22.setty.feature.common.SettyMessage
import com.aksworns22.setty.feature.common.StatusBadge
import com.aksworns22.setty.model.ListingNotification
import com.aksworns22.setty.model.NotificationStatus
import com.aksworns22.setty.ui.theme.Paperlogy
import com.aksworns22.setty.ui.theme.SettyTheme

@Composable
fun NotificationListScreen(
    onBack: () -> Unit,
    onFurnitureClick: (Long) -> Unit,
    onRequireLogin: () -> Unit,
    viewModel: NotificationListViewModel = viewModel(factory = NotificationListViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var isStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.load(reset = !isStarted)
        isStarted = true
        viewModel.event.collect { event ->
            when (event) {
                is NotificationListEvent.Message -> snackbarHostState.showSnackbar(event.message)
                NotificationListEvent.RequireLogin -> onRequireLogin()
            }
        }
    }
    NotificationListContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = { viewModel.load() },
        onRefresh = viewModel::refresh,
        onRequireLogin = onRequireLogin,
        onFurnitureClick = onFurnitureClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NotificationListContent(
    uiState: NotificationListUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onRequireLogin: () -> Unit,
    onFurnitureClick: (Long) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = "알림함",
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
        when (val notifications = uiState.notifications) {
            LoadUiState.Loading -> LoadingContent(modifier = Modifier.padding(innerPadding))

            is LoadUiState.Error -> ErrorContent(
                message = notifications.message,
                isUnauthorized = notifications.isUnauthorized,
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
                    NotificationList(
                        notifications = notifications.data,
                        onFurnitureClick = onFurnitureClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationList(
    notifications: List<ListingNotification>,
    onFurnitureClick: (Long) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (notifications.isEmpty()) {
            item(key = "empty") {
                SettyMessage(
                    title = "받은 알림이 없어요",
                    description = "키워드를 등록하면 새 매물 알림이 여기에 쌓여요.",
                    modifier = Modifier.padding(top = 48.dp),
                )
            }
        }
        itemsIndexed(notifications, key = { _, notification -> notification.id }) { index, notification ->
            NotificationItem(
                notification = notification,
                index = index,
                count = notifications.size,
                onClick = { onFurnitureClick(notification.listingId) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NotificationItem(
    notification: ListingNotification,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        overlineContent = {
            Text(text = notification.createdAtLabel, style = MaterialTheme.typography.labelMedium)
        },
        trailingContent = { NotificationStatusBadge(status = notification.status) },
    ) {
        Text(
            text = notification.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NotificationStatusBadge(status: NotificationStatus) {
    val (containerColor, contentColor) = when (status) {
        NotificationStatus.SENT -> MaterialTheme.colorScheme.primaryContainer to
                MaterialTheme.colorScheme.onPrimaryContainer

        NotificationStatus.PENDING -> MaterialTheme.colorScheme.tertiaryContainer to
                MaterialTheme.colorScheme.onTertiaryContainer

        NotificationStatus.FAILED -> MaterialTheme.colorScheme.errorContainer to
                MaterialTheme.colorScheme.onErrorContainer
    }
    StatusBadge(text = status.label, containerColor = containerColor, contentColor = contentColor)
}

@Preview
@Composable
private fun NotificationListContentPreview() {
    SettyTheme {
        NotificationListContent(
            uiState = NotificationListUiState(
                notifications = LoadUiState.Success(
                    listOf(
                        ListingNotification(1, 10, "원목 4인용 식탁", NotificationStatus.SENT, "2026-10-11T12:00:00Z"),
                        ListingNotification(2, 11, "패브릭 소파", NotificationStatus.PENDING, "2026-10-11T11:00:00Z"),
                    )
                )
            ),
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onRetry = {},
            onRefresh = {},
            onRequireLogin = {},
            onFurnitureClick = {},
        )
    }
}
