package com.aksworns22.setty.feature.listingeditor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.aksworns22.setty.R
import com.aksworns22.setty.core.model.FurnitureCategory
import com.aksworns22.setty.feature.common.BackIconButton
import com.aksworns22.setty.feature.common.ErrorContent
import com.aksworns22.setty.feature.common.LoadUiState
import com.aksworns22.setty.feature.common.LoadingContent
import com.aksworns22.setty.feature.common.SectionTitle
import com.aksworns22.setty.feature.listingeditor.ListingEditorUiState.Companion.DESCRIPTION_MAX_LENGTH
import com.aksworns22.setty.feature.listingeditor.ListingEditorUiState.Companion.MAX_IMAGE_COUNT
import com.aksworns22.setty.feature.listingeditor.ListingEditorUiState.Companion.TITLE_MAX_LENGTH
import com.aksworns22.setty.model.ConditionGrades
import com.aksworns22.setty.model.label
import com.aksworns22.setty.ui.theme.Paperlogy
import com.aksworns22.setty.ui.theme.SettyTheme

/**
 * 매물 등록·수정 화면. [listingId]가 null이면 새 매물을 등록한다.
 */
@Composable
fun ListingEditorScreen(
    listingId: Long?,
    onBack: () -> Unit,
    onSaved: (listingId: Long) -> Unit,
    onRequireLogin: () -> Unit,
    viewModel: ListingEditorViewModel = viewModel(factory = ListingEditorViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // 화면에 처음 들어올 때만 폼을 초기화한다. 회전 등으로 다시 그려질 때는 입력을 유지한다.
    var isStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!isStarted) {
            isStarted = true
            viewModel.start(listingId)
        }
        viewModel.event.collect { event ->
            when (event) {
                is ListingEditorEvent.Saved -> onSaved(event.listingId)
                ListingEditorEvent.RequireLogin -> onRequireLogin()
                is ListingEditorEvent.Failed -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGE_COUNT)
    ) { uris -> viewModel.onImagesPicked(uris) }
    ListingEditorContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        actions = ListingEditorActions(
            onBack = onBack,
            onRetry = viewModel::retry,
            onRequireLogin = onRequireLogin,
            onAddImagesClick = {
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRetainedImageRemoved = viewModel::onRetainedImageRemoved,
            onNewImageRemoved = viewModel::onNewImageRemoved,
            onTitleChanged = viewModel::onTitleChanged,
            onDescriptionChanged = viewModel::onDescriptionChanged,
            onPriceChanged = viewModel::onPriceChanged,
            onCategorySelected = viewModel::onCategorySelected,
            onConditionGradeSelected = viewModel::onConditionGradeSelected,
            onWidthChanged = viewModel::onWidthChanged,
            onDepthChanged = viewModel::onDepthChanged,
            onHeightChanged = viewModel::onHeightChanged,
            onSubmit = viewModel::submit,
        ),
    )
}

private class ListingEditorActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onRequireLogin: () -> Unit,
    val onAddImagesClick: () -> Unit,
    val onRetainedImageRemoved: (Long) -> Unit,
    val onNewImageRemoved: (Uri) -> Unit,
    val onTitleChanged: (String) -> Unit,
    val onDescriptionChanged: (String) -> Unit,
    val onPriceChanged: (String) -> Unit,
    val onCategorySelected: (FurnitureCategory) -> Unit,
    val onConditionGradeSelected: (String) -> Unit,
    val onWidthChanged: (String) -> Unit,
    val onDepthChanged: (String) -> Unit,
    val onHeightChanged: (String) -> Unit,
    val onSubmit: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ListingEditorContent(
    uiState: ListingEditorUiState,
    snackbarHostState: SnackbarHostState,
    actions: ListingEditorActions,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        text = if (uiState.isEditing) "가구 수정" else "가구 등록",
                        fontFamily = Paperlogy,
                        fontWeight = FontWeight.Black,
                    )
                },
                subtitle = {
                    Text(
                        text = "배송비는 가구 크기로 자동 계산돼요",
                        fontFamily = Paperlogy,
                    )
                },
                navigationIcon = {
                    BackIconButton(onBack = actions.onBack, enabled = !uiState.isSubmitting)
                },
            )
        },
        bottomBar = {
            if (uiState.loadState is LoadUiState.Success) {
                SubmitButton(
                    text = if (uiState.isEditing) "수정 완료" else "등록하기",
                    isSubmitting = uiState.isSubmitting,
                    enabled = uiState.canSubmit,
                    onSubmit = actions.onSubmit,
                )
            }
        },
        modifier = Modifier.imePadding(),
    ) { innerPadding ->
        when (val loadState = uiState.loadState) {
            LoadUiState.Loading -> LoadingContent(modifier = Modifier.padding(innerPadding))

            is LoadUiState.Error -> ErrorContent(
                message = loadState.message,
                isUnauthorized = loadState.isUnauthorized,
                onRetry = actions.onRetry,
                onRequireLogin = actions.onRequireLogin,
                modifier = Modifier.padding(innerPadding),
            )

            is LoadUiState.Success -> ListingEditorForm(
                uiState = uiState,
                actions = actions,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun ListingEditorForm(
    uiState: ListingEditorUiState,
    actions: ListingEditorActions,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(28.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        ImageSection(
            uiState = uiState,
            onAddImagesClick = actions.onAddImagesClick,
            onRetainedImageRemoved = actions.onRetainedImageRemoved,
            onNewImageRemoved = actions.onNewImageRemoved,
        )
        FormSection(title = "기본 정보") {
            OutlinedTextField(
                value = uiState.title,
                onValueChange = actions.onTitleChanged,
                label = { FieldLabel("제목") },
                isError = uiState.titleError,
                supportingText = { Text("${uiState.title.length}/$TITLE_MAX_LENGTH") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.description,
                onValueChange = actions.onDescriptionChanged,
                label = { FieldLabel("상세 설명") },
                placeholder = { Text("사용 기간, 하자 여부 등을 알려주세요") },
                isError = uiState.descriptionError,
                supportingText = { Text("${uiState.description.length}/$DESCRIPTION_MAX_LENGTH") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FormSection(title = "가격") {
            OutlinedTextField(
                value = uiState.price,
                onValueChange = actions.onPriceChanged,
                label = { FieldLabel("판매 가격") },
                suffix = { Text("원") },
                isError = uiState.priceError,
                supportingText = if (uiState.priceError) {
                    { Text("가격은 1억 원 이하로 입력해 주세요") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FormSection(title = "카테고리", horizontalPadding = false) {
            CategoryButtonGroup(
                selectedCategory = uiState.category,
                onCategorySelected = actions.onCategorySelected,
            )
        }
        FormSection(title = "상태 등급") {
            ConditionGradeButtonGroup(
                selectedGrade = uiState.conditionGrade,
                onGradeSelected = actions.onConditionGradeSelected,
            )
        }
        FormSection(title = "크기") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DimensionField(
                    label = "가로",
                    value = uiState.widthCm,
                    isError = uiState.widthError,
                    onValueChange = actions.onWidthChanged,
                    modifier = Modifier.weight(1f),
                )
                DimensionField(
                    label = "깊이",
                    value = uiState.depthCm,
                    isError = uiState.depthError,
                    onValueChange = actions.onDepthChanged,
                    modifier = Modifier.weight(1f),
                )
                DimensionField(
                    label = "높이",
                    value = uiState.heightCm,
                    isError = uiState.heightError,
                    onValueChange = actions.onHeightChanged,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "각 변은 1~1,000cm 사이로 입력해 주세요",
                style = MaterialTheme.typography.labelMedium,
                color = if (uiState.widthError || uiState.depthError || uiState.heightError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        SectionTitle(text = title, modifier = Modifier.padding(horizontal = 16.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = if (horizontalPadding) Modifier.padding(horizontal = 16.dp) else Modifier,
        ) {
            content()
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMediumEmphasized)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ImageSection(
    uiState: ListingEditorUiState,
    onAddImagesClick: () -> Unit,
    onRetainedImageRemoved: (Long) -> Unit,
    onNewImageRemoved: (Uri) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(
            text = "사진 ${uiState.imageCount}/$MAX_IMAGE_COUNT",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            item(key = "add") {
                AddImageTile(
                    enabled = uiState.remainingImageSlots > 0 && !uiState.isSubmitting,
                    onClick = onAddImagesClick,
                )
            }
            // 기존 이미지 뒤에 새 이미지가 붙으므로 같은 순서로 보여준다
            items(uiState.retainedImages, key = { "retained-${it.id}" }) { image ->
                ImageTile(
                    model = image.url,
                    isCover = uiState.retainedImages.first() == image,
                    onRemove = { onRetainedImageRemoved(image.id) },
                    modifier = Modifier.animateItem(),
                )
            }
            items(uiState.newImages, key = { "new-$it" }) { uri ->
                ImageTile(
                    model = uri,
                    isCover = uiState.retainedImages.isEmpty() && uiState.newImages.first() == uri,
                    onRemove = { onNewImageRemoved(uri) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        Text(
            text = "첫 번째 사진이 대표 사진이 돼요. JPEG·PNG·WebP, 전체 25MB까지 올릴 수 있어요.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun AddImageTile(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(ImageTileSize),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Icon(
                painter = painterResource(R.drawable.outline_add_photo_24),
                contentDescription = "사진 추가",
            )
            Text(text = "사진 추가", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ImageTile(
    model: Any,
    isCover: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(ImageTileSize)) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        if (isCover) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            ) {
                Text(
                    text = "대표",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        FilledTonalIconButton(
            onClick = onRemove,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(28.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.outline_close_24),
                contentDescription = "사진 삭제",
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategoryButtonGroup(
    selectedCategory: FurnitureCategory?,
    onCategorySelected: (FurnitureCategory) -> Unit,
) {
    // 서버가 받는 카테고리만 고를 수 있다
    val categories = FurnitureCategory.entries - FurnitureCategory.ETC
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
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
                Text(text = category.label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConditionGradeButtonGroup(
    selectedGrade: String?,
    onGradeSelected: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ConditionGrades.forEachIndexed { index, grade ->
            ToggleButton(
                checked = selectedGrade == grade,
                onCheckedChange = { onGradeSelected(grade) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    ConditionGrades.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(text = "${grade}급", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DimensionField(
    label: String,
    value: String,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { FieldLabel(label) },
        suffix = { Text("cm") },
        isError = isError,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = imeAction,
        ),
        singleLine = true,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubmitButton(
    text: String,
    isSubmitting: Boolean,
    enabled: Boolean,
    onSubmit: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (!isSubmitting) {
            Button(
                onClick = onSubmit,
                enabled = enabled,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonDefaults.MediumContainerHeight),
            ) {
                Text(text, style = MaterialTheme.typography.bodyLargeEmphasized)
            }
        } else {
            LoadingIndicator()
        }
    }
}

private val ImageTileSize = 104.dp

@Preview
@Composable
private fun ListingEditorContentPreview() {
    SettyTheme {
        ListingEditorContent(
            uiState = ListingEditorUiState(
                title = "원목 4인용 식탁",
                description = "2년 사용했습니다.",
                price = "120000",
                category = FurnitureCategory.TABLE,
                conditionGrade = "A",
                widthCm = "140",
                depthCm = "80",
                heightCm = "75",
            ),
            snackbarHostState = SnackbarHostState(),
            actions = ListingEditorActions(
                onBack = {},
                onRetry = {},
                onRequireLogin = {},
                onAddImagesClick = {},
                onRetainedImageRemoved = {},
                onNewImageRemoved = {},
                onTitleChanged = {},
                onDescriptionChanged = {},
                onPriceChanged = {},
                onCategorySelected = {},
                onConditionGradeSelected = {},
                onWidthChanged = {},
                onDepthChanged = {},
                onHeightChanged = {},
                onSubmit = {},
            ),
        )
    }
}
