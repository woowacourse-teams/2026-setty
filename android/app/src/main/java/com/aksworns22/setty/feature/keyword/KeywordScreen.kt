package com.aksworns22.setty.feature.keyword

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aksworns22.setty.R
import com.aksworns22.setty.feature.common.BackIconButton
import com.aksworns22.setty.feature.common.ErrorContent
import com.aksworns22.setty.feature.common.LoadUiState
import com.aksworns22.setty.feature.common.LoadingContent
import com.aksworns22.setty.feature.common.SettyMessage
import com.aksworns22.setty.model.Keyword
import com.aksworns22.setty.ui.theme.Paperlogy
import com.aksworns22.setty.ui.theme.SettyTheme

@Composable
fun KeywordScreen(
    onBack: () -> Unit,
    onRequireLogin: () -> Unit,
    viewModel: KeywordViewModel = viewModel(factory = KeywordViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var isStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.load(reset = !isStarted)
        isStarted = true
        viewModel.event.collect { event ->
            when (event) {
                is KeywordEvent.Message -> snackbarHostState.showSnackbar(event.message)
                KeywordEvent.RequireLogin -> onRequireLogin()
            }
        }
    }
    KeywordContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = { viewModel.load() },
        onRequireLogin = onRequireLogin,
        onInputChanged = viewModel::onInputChanged,
        onAdd = viewModel::add,
        onRemove = viewModel::remove,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KeywordContent(
    uiState: KeywordUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRequireLogin: () -> Unit,
    onInputChanged: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Keyword) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = "키워드 알림",
                        fontFamily = Paperlogy,
                        fontWeight = FontWeight.Black,
                    )
                },
                navigationIcon = { BackIconButton(onBack = onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            KeywordInput(
                value = uiState.input,
                isSubmitting = uiState.isSubmitting,
                onValueChange = onInputChanged,
                onAdd = onAdd,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        when (val keywords = uiState.keywords) {
            LoadUiState.Loading -> LoadingContent(modifier = Modifier.padding(innerPadding))

            is LoadUiState.Error -> ErrorContent(
                message = keywords.message,
                isUnauthorized = keywords.isUnauthorized,
                onRetry = onRetry,
                onRequireLogin = onRequireLogin,
                modifier = Modifier.padding(innerPadding),
            )

            is LoadUiState.Success -> KeywordList(
                keywords = keywords.data,
                onRemove = onRemove,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun KeywordList(
    keywords: List<Keyword>,
    onRemove: (Keyword) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = modifier.fillMaxSize(),
    ) {
        if (keywords.isEmpty()) {
            item(key = "empty") {
                SettyMessage(
                    title = "등록한 키워드가 없어요",
                    description = "키워드를 등록하면 새 매물이 올라올 때 알려드려요.",
                    modifier = Modifier.padding(top = 48.dp),
                )
            }
        }
        itemsIndexed(keywords, key = { _, keyword -> keyword.id }) { index, keyword ->
            KeywordItem(
                keyword = keyword,
                index = index,
                count = keywords.size,
                onRemove = { onRemove(keyword) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KeywordItem(
    keyword: Keyword,
    index: Int,
    count: Int,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(
                    painter = painterResource(R.drawable.outline_delete_24),
                    contentDescription = "키워드 삭제",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
        modifier = modifier,
    ) {
        Text(text = keyword.keyword, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KeywordInput(
    value: String,
    isSubmitting: Boolean,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Surface {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("예: 식탁") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onAdd,
                enabled = value.isNotBlank() && !isSubmitting,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text("등록")
            }
        }
    }
}

@Preview
@Composable
private fun KeywordContentPreview() {
    SettyTheme {
        KeywordContent(
            uiState = KeywordUiState(
                keywords = LoadUiState.Success(
                    listOf(Keyword(1, "식탁"), Keyword(2, "소파")),
                ),
                input = "책상",
            ),
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onRetry = {},
            onRequireLogin = {},
            onInputChanged = {},
            onAdd = {},
            onRemove = {},
        )
    }
}
