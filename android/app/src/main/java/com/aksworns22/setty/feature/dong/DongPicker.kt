package com.aksworns22.setty.feature.dong

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aksworns22.setty.R
import com.aksworns22.setty.feature.common.ErrorContent
import com.aksworns22.setty.feature.common.LoadUiState
import com.aksworns22.setty.feature.common.LoadingContent
import com.aksworns22.setty.model.Dong
import kotlinx.coroutines.launch

/**
 * 고른 행정동을 보여주고, 누르면 서울 행정동 목록에서 다시 고를 수 있는 입력 칸.
 * [selectedCode]가 목록에 없으면 비어 있는 칸으로 보여준다.
 * [catalog]는 화면에서 [DongPickerViewModel]로 불러와 넘긴다.
 */
@Composable
fun DongField(
    catalog: LoadUiState<DongCatalog>,
    selectedCode: String?,
    onDongSelected: (Dong) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var isPickerVisible by rememberSaveable { mutableStateOf(false) }
    val selectedDong = (catalog as? LoadUiState.Success)?.data?.find(selectedCode)
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedDong?.displayName.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("행정동", style = MaterialTheme.typography.bodyMediumEmphasized) },
            placeholder = { Text("배송비 계산에 쓰일 동네를 골라주세요") },
            supportingText = { Text("서울에서만 배송할 수 있어요") },
            trailingIcon = {
                Icon(painter = painterResource(R.drawable.outline_chevron_right_24), contentDescription = null)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // 읽기 전용 입력 칸은 눌림을 받지 않으므로 위에 눌림 영역을 덮는다
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(enabled = enabled) { isPickerVisible = true },
        )
    }
    if (isPickerVisible) {
        DongPickerSheet(
            catalog = catalog,
            onRetry = onRetry,
            onDismiss = { isPickerVisible = false },
            onDongSelected = { dong ->
                isPickerVisible = false
                onDongSelected(dong)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DongPickerSheet(
    catalog: LoadUiState<DongCatalog>,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onDongSelected: (Dong) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
        ) {
            Text(
                text = "행정동 선택",
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            when (catalog) {
                LoadUiState.Loading -> LoadingContent()

                is LoadUiState.Error -> ErrorContent(
                    message = catalog.message,
                    isUnauthorized = false,
                    onRetry = onRetry,
                    onRequireLogin = {},
                )

                is LoadUiState.Success -> DongPickerBody(
                    catalog = catalog.data,
                    onDongSelected = { dong ->
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDongSelected(dong) }
                    },
                )
            }
        }
    }
}

@Composable
private fun DongPickerBody(
    catalog: DongCatalog,
    onDongSelected: (Dong) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedDistrict by rememberSaveable { mutableStateOf<String?>(null) }
    // 동 목록을 보던 중 뒤로 가기를 누르면 시트를 닫지 않고 구 목록으로 돌아간다
    BackHandler(enabled = query.isBlank() && selectedDistrict != null) {
        selectedDistrict = null
    }
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("동 이름으로 검색") },
            leadingIcon = {
                Icon(painter = painterResource(R.drawable.outline_search_24), contentDescription = null)
            },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(painter = painterResource(R.drawable.outline_close_24), contentDescription = "검색어 지우기")
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
        val district = selectedDistrict
        when {
            query.isNotBlank() -> DongList(
                dongs = catalog.search(query),
                showDistrict = true,
                onDongSelected = onDongSelected,
            )

            district != null -> Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    IconButton(onClick = { selectedDistrict = null }) {
                        Icon(
                            painter = painterResource(R.drawable.outline_arrow_back_24),
                            contentDescription = "구 목록으로",
                        )
                    }
                    Text(text = district, style = MaterialTheme.typography.titleMedium)
                }
                DongList(
                    dongs = catalog.dongsIn(district),
                    showDistrict = false,
                    onDongSelected = onDongSelected,
                )
            }

            else -> LazyColumn {
                items(catalog.districts, key = { it }) { item ->
                    ListItem(
                        headlineContent = { Text(item) },
                        trailingContent = {
                            Icon(
                                painter = painterResource(R.drawable.outline_chevron_right_24),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable { selectedDistrict = item },
                    )
                }
            }
        }
    }
}

@Composable
private fun DongList(
    dongs: List<Dong>,
    showDistrict: Boolean,
    onDongSelected: (Dong) -> Unit,
) {
    if (dongs.isEmpty()) {
        Text(
            text = "찾는 동이 없어요. 서울 행정동 이름으로 검색해 주세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
        return
    }
    LazyColumn {
        items(dongs, key = { it.code }) { dong ->
            ListItem(
                headlineContent = { Text(dong.name) },
                supportingContent = if (showDistrict) {
                    { Text(dong.district) }
                } else {
                    null
                },
                modifier = Modifier.clickable { onDongSelected(dong) },
            )
        }
    }
}
