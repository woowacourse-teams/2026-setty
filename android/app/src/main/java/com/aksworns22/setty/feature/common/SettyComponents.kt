package com.aksworns22.setty.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aksworns22.setty.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackIconButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalIconButton(
        onClick = onBack,
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(),
        modifier = modifier.padding(start = 8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.outline_arrow_back_24),
            contentDescription = "뒤로 가기",
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        LoadingIndicator()
    }
}

/** 빈 목록·에러처럼 화면 가운데에 보여주는 안내. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettyMessage(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    shape: Shape = MaterialShapes.Cookie9Sided.toShape(),
    shapeColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
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
                .clip(shape)
                .background(shapeColor),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        action?.invoke()
    }
}

/** 불러오기 실패 화면. 로그인이 풀린 경우 로그인 화면으로 보낸다. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ErrorContent(
    message: String,
    isUnauthorized: Boolean,
    onRetry: () -> Unit,
    onRequireLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        SettyMessage(
            title = message,
            shape = MaterialShapes.SoftBurst.toShape(),
            shapeColor = MaterialTheme.colorScheme.errorContainer,
            action = {
                Button(
                    onClick = if (isUnauthorized) onRequireLogin else onRetry,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(
                        text = if (isUnauthorized) "로그인하기" else "다시 시도",
                        style = MaterialTheme.typography.bodyLargeEmphasized,
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FurnitureThumbnail(
    imageUrl: String?,
    placeholder: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(MaterialShapes.Square.toShape())
            .background(MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(
            text = placeholder,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun SectionTitle(
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
