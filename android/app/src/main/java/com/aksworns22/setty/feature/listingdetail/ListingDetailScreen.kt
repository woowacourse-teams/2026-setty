package com.aksworns22.setty.feature.listingdetail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.aksworns22.setty.model.ListingCategory
import com.aksworns22.setty.model.ListingDetail
import com.aksworns22.setty.model.SaleStatus
import com.aksworns22.setty.model.formatWon
import com.aksworns22.setty.ui.theme.Paperlogy
import com.aksworns22.setty.ui.theme.SettyTheme

@Composable
fun ListingDetailScreen(
    listingId: Long,
    onBack: () -> Unit,
    onPurchaseClick: (Long) -> Unit,
    viewModel: ListingDetailViewModel = viewModel(
        key = "listing-detail-$listingId",
        factory = ListingDetailViewModel.factory(listingId),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ListingDetailContent(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::load,
        onPurchaseClick = onPurchaseClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ListingDetailContent(
    uiState: ListingDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPurchaseClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listing = (uiState as? ListingDetailUiState.Success)?.listing
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = listing?.title.orEmpty().keepAllWords(),
                            fontFamily = Paperlogy,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (listing != null) {
                            SaleStatusBadge(saleStatus = listing.saleStatus)
                        }
                    }
                },
                subtitle = listing?.let {
                    {
                        Text(
                            text = "${it.categoryLabel} · ${it.conditionGrade}급",
                            fontFamily = Paperlogy,
                        )
                    }
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_arrow_back_24),
                            contentDescription = "뒤로 가기",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (listing != null) {
                PurchaseBar(
                    listing = listing,
                    onPurchaseClick = { onPurchaseClick(listing.id) },
                )
            }
        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        val enterSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val exitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(
            targetState = uiState,
            contentKey = { it::class },
            transitionSpec = {
                fadeIn(enterSpec) togetherWith fadeOut(exitSpec)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "listingDetailState",
        ) { state ->
            when (state) {
                ListingDetailUiState.Loading -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LoadingIndicator()
                }

                is ListingDetailUiState.Error -> ListingDetailErrorContent(
                    error = state.reason,
                    onBack = onBack,
                    onRetry = onRetry,
                )

                is ListingDetailUiState.Success -> ListingDetailBody(listing = state.listing)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ListingDetailBody(
    listing: ListingDetail,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            ListingImageCarousel(
                imageUrls = listing.imageUrls,
                title = listing.title,
            )
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSpecHeight)
                    .padding(horizontal = 16.dp),
            ) {
                ConditionGradeCard(
                    grade = listing.conditionGrade,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                DimensionsCard(
                    widthCm = listing.widthCm,
                    depthCm = listing.depthCm,
                    heightCm = listing.heightCm,
                    modifier = Modifier
                        .weight(1.6f)
                        .fillMaxHeight(),
                )
            }
        }
        item {
            PriceSection(
                listing = listing,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            DescriptionSection(
                description = listing.description,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListingImageCarousel(
    imageUrls: List<String>,
    title: String,
    modifier: Modifier = Modifier,
) {
    if (imageUrls.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxWidth()
                .height(CarouselHeight)
                .padding(horizontal = CarouselHorizontalPadding)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Text(
                text = "등록된 사진이 없습니다",
                style = MaterialTheme.typography.bodyMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val carouselState = rememberCarouselState { imageUrls.size }
        HorizontalUncontainedCarousel(
            state = carouselState,
            itemWidth = maxWidth - CarouselHorizontalPadding * 2,
            itemSpacing = 8.dp,
            flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(state = carouselState),
            contentPadding = PaddingValues(horizontal = CarouselHorizontalPadding),
            modifier = Modifier
                .fillMaxWidth()
                .height(CarouselHeight),
        ) { index ->
            AsyncImage(
                model = imageUrls[index],
                contentDescription = "$title 사진 ${index + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .maskClip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
    }
}

@Composable
private fun SaleStatusBadge(
    saleStatus: SaleStatus,
    modifier: Modifier = Modifier,
) {
    val (containerColor, contentColor) = when (saleStatus) {
        SaleStatus.AVAILABLE -> MaterialTheme.colorScheme.primaryContainer to
                MaterialTheme.colorScheme.onPrimaryContainer

        SaleStatus.RESERVED -> MaterialTheme.colorScheme.tertiaryContainer to
                MaterialTheme.colorScheme.onTertiaryContainer

        SaleStatus.SOLD -> MaterialTheme.colorScheme.surfaceContainerHighest to
                MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier,
    ) {
        Text(
            text = saleStatus.label,
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConditionGradeCard(
    grade: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            modifier = Modifier.padding(16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.primary),
            ) {
                Text(
                    text = grade,
                    style = MaterialTheme.typography.headlineLargeEmphasized,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                text = "상태 등급",
                style = MaterialTheme.typography.bodyMediumEmphasized,
            )
        }
    }
}

@Composable
private fun DimensionsCard(
    widthCm: Int,
    depthCm: Int,
    heightCm: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "크기 (cm)",
                style = MaterialTheme.typography.bodyMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                DimensionValue(label = "W", value = widthCm)
                DimensionValue(label = "D", value = depthCm)
                DimensionValue(label = "H", value = heightCm)
            }
        }
    }
}

@Composable
private fun DimensionValue(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmallEmphasized,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PriceSection(
    listing: ListingDetail,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(
        "매물 가격" to listing.price,
        "예상 배송비" to listing.deliveryFee,
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = modifier,
    ) {
        SectionTitle(text = "가격 정보")
        rows.forEachIndexed { index, (label, amount) ->
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(
                    index = index,
                    count = rows.size + 1,
                ),
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
            shape = ListItemDefaults.segmentedShapes(
                index = rows.size,
                count = rows.size + 1,
            ).shape,
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
                Text(
                    text = "총 결제 예상액",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.padding(end = 12.dp),
                )
                val totalPriceStyle = MaterialTheme.typography.headlineLargeEmphasized
                Text(
                    text = formatWon(listing.totalPrice),
                    style = totalPriceStyle,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    autoSize = TextAutoSize.StepBased(maxFontSize = totalPriceStyle.fontSize),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DescriptionSection(
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        SectionTitle(text = "상세 설명")
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLargeEmphasized.copy(fontWeight = FontWeight.Normal),
        )
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMediumEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(bottom = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PurchaseBar(
    listing: ListingDetail,
    onPurchaseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Button(
            onClick = onPurchaseClick,
            enabled = listing.isPurchasable,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .height(ButtonDefaults.MediumContainerHeight),
        ) {
            Text(
                text = if (listing.isPurchasable) "결제하고 주문하기" else "구매할 수 없는 매물입니다",
                style = MaterialTheme.typography.bodyLargeEmphasized,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ListingDetailErrorContent(
    error: ListingDetailError,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(MaterialShapes.SoftBurst.toShape())
                .background(MaterialTheme.colorScheme.errorContainer),
        )
        Text(
            text = error.toMessage(),
            style = MaterialTheme.typography.titleMediumEmphasized,
            textAlign = TextAlign.Center,
        )
        if (error == ListingDetailError.NOT_FOUND) {
            Button(onClick = onBack, shapes = ButtonDefaults.shapes()) {
                Text("목록으로", style = MaterialTheme.typography.bodyLargeEmphasized)
            }
        } else {
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text("다시 시도", style = MaterialTheme.typography.bodyLargeEmphasized)
            }
        }
    }
}

private fun ListingDetailError.toMessage(): String = when (this) {
    ListingDetailError.NOT_FOUND -> "존재하지 않는 매물입니다"
    ListingDetailError.NETWORK_ERROR -> "네트워크 에러가 발생했습니다"
    ListingDetailError.UNKNOWN_ERROR -> "알 수 없는 에러가 발생했습니다"
}

/** 한글이 음절 단위로 줄바꿈되지 않도록 단어 안의 글자를 WORD JOINER로 묶는다. */
private fun String.keepAllWords(): String =
    split(' ').joinToString(" ") { word -> word.toList().joinToString(WORD_JOINER) }

private const val WORD_JOINER = "\u2060"

private val CarouselHeight = 320.dp
private val CarouselHorizontalPadding = 16.dp
private val IntrinsicSpecHeight = 148.dp

private val PreviewListing = ListingDetail(
    id = 1,
    title = "원목 4인 식탁",
    description = "2년 사용한 원목 식탁입니다.\n모서리 찍힘 없이 깨끗하게 사용했어요.",
    price = 120_000,
    deliveryFee = 20_000,
    totalPrice = 140_000,
    category = ListingCategory.TABLE,
    conditionGrade = "A",
    widthCm = 140,
    depthCm = 80,
    heightCm = 75,
    saleStatus = SaleStatus.AVAILABLE,
    imageUrls = emptyList(),
)

@Preview
@Composable
private fun ListingDetailContentPreview() {
    SettyTheme {
        ListingDetailContent(
            uiState = ListingDetailUiState.Success(PreviewListing),
            onBack = {},
            onRetry = {},
            onPurchaseClick = {},
        )
    }
}

@Preview
@Composable
private fun ListingDetailSoldPreview() {
    SettyTheme {
        ListingDetailContent(
            uiState = ListingDetailUiState.Success(PreviewListing.copy(saleStatus = SaleStatus.SOLD)),
            onBack = {},
            onRetry = {},
            onPurchaseClick = {},
        )
    }
}

@Preview
@Composable
private fun ListingDetailErrorPreview() {
    SettyTheme {
        ListingDetailContent(
            uiState = ListingDetailUiState.Error(ListingDetailError.NETWORK_ERROR),
            onBack = {},
            onRetry = {},
            onPurchaseClick = {},
        )
    }
}
