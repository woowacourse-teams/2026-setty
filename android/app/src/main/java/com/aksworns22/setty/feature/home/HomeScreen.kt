package com.aksworns22.setty.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aksworns22.setty.R
import com.aksworns22.setty.ui.theme.Paperlogy
import com.aksworns22.setty.ui.theme.SettyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val textFieldState = rememberTextFieldState()
    val searchBarState = rememberSearchBarState()
    Scaffold(
        topBar = {
            AppBarWithSearch(
                state = searchBarState,
                inputField = {
                    SearchBarDefaults.InputField(
                        textFieldState = textFieldState,
                        searchBarState = searchBarState,
                        onSearch = {},
                        placeholder = {
                            Text(
                                text = "중고 가구 검색",
                                fontFamily = Paperlogy,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {},
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_menu_24),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = { IconButton(
                    onClick = {},
                ) {
                    Image(
                        painter = painterResource(R.drawable.profile),
                        contentDescription = null,
                    )
                } },
            )

            ExpandedFullScreenSearchBar(
                state = searchBarState,
                inputField = {
                    SearchBarDefaults.InputField(
                        textFieldState = textFieldState,
                        searchBarState = searchBarState,
                        onSearch = {},
                        placeholder = {
                            Text(
                                text = "중고 가구 검색",
                                fontFamily = Paperlogy,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    )
                },
            ) {

            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            val titles = listOf(
                "A Year in Ecuador",
                "Wizards & Winona",
                "Gamer Course",
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(
                    ListItemDefaults.SegmentedGap
                ),
            ) {
                itemsIndexed(titles) { index, title ->
                    SegmentedListItem(
                        onClick = { },
                        onLongClick = {},
                        shapes = ListItemDefaults.segmentedShapes(
                            index = index,
                            count = titles.size,
                        ),
                        supportingContent = { Text(text = "10 episodes", fontFamily = Paperlogy) },
                        trailingContent = { Text(text = "68 min", fontFamily = Paperlogy) },
                    ) {
                        Text(text = title, fontFamily = Paperlogy,)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    SettyTheme {
        HomeScreen()
    }
}
