package com.aksworns22.setty.feature.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aksworns22.setty.ui.theme.Paperlogy
import com.aksworns22.setty.ui.theme.SettyTheme

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onSignUpClick: () -> Unit,
    isSignUpCompleted: Boolean,
    onSignUpCompletedShown: () -> Unit,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory)
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(isSignUpCompleted) {
        if (isSignUpCompleted) {
            snackbarHostState.showSnackbar("회원가입이 완료되었습니다.")
            onSignUpCompletedShown()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                LoginEvent.SUCCESS -> {
                    onLoginSuccess()
                }

                LoginEvent.CREDENTIAL_ERROR -> {
                    snackbarHostState.showSnackbar("올바르지 않은 정보입니다")
                }

                LoginEvent.NETWORK_ERROR -> {
                    snackbarHostState.showSnackbar("네트워크 에러가 발생했습니다")
                }

                LoginEvent.UNKNOWN_ERROR -> {
                    snackbarHostState.showSnackbar("알 수 없는 에러가 발생했습니다")
                }
            }
        }
    }
    LoginContent(
        username = uiState.value.username,
        password = uiState.value.password,
        isLoading = uiState.value.isLoading,
        onUsernameChanged = viewModel::onUsernameChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onLogin = viewModel::login,
        onSignUpClick = onSignUpClick,
        snackbarHostState = snackbarHostState
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoginContent(
    username: String,
    password: String,
    isLoading: Boolean,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLogin: () -> Unit,
    onSignUpClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .offset(y = 16.dp)
                    .padding(horizontal = 20.dp),
            )
        },
        bottomBar = {
            LoginButton(
                isLoading = isLoading,
                onLogin = onLogin,
            )
        },
        modifier = modifier
            .imePadding()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = 32.dp, start = 32.dp, end = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = "SETTY 로그인", style = MaterialTheme.typography.headlineLargeEmphasized)
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontFamily = Paperlogy,
                                fontWeight = FontWeight.Black,
                            )
                        ) {
                            append("무거운 중고 가구")
                        }

                        append("를 더 ")

                        withStyle(
                            SpanStyle(
                                fontFamily = Paperlogy,
                                fontWeight = FontWeight.ExtraLight,
                            )
                        ) {
                            append("가볍게")
                        }

                        append("\n세티와 함께 ")

                        withStyle(
                            SpanStyle(
                                fontFamily = Paperlogy,
                                fontWeight = FontWeight.SemiBold,
                            )
                        ) {
                            append("중고 가구 거래")
                        }

                        append("를 시작해보세요.")
                    },
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChanged,
                    label = { Text("세티 아이디", style = MaterialTheme.typography.bodyMediumEmphasized) },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChanged,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("세티 비밀번호", style = MaterialTheme.typography.bodyMediumEmphasized) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            onLogin()
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        ) {
                            append("아직 계정이 없나요? ")
                        }

                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        ) {
                            append("계정 만들기")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable(onClick = onSignUpClick),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoginButton(
    isLoading: Boolean,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 16.dp, bottom = 16.dp, start = 32.dp, end = 32.dp),
    ) {
        if (!isLoading) {
            Button(
                onClick = onLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonDefaults.MediumContainerHeight),
            ) {
                Text("로그인", style = MaterialTheme.typography.bodyLargeEmphasized)
            }
        } else {
            LoadingIndicator()
        }
    }
}

@Preview
@Composable
private fun LoginContentPreview() {
    SettyTheme {
        LoginContent(
            username = "tars",
            password = "jaaars",
            isLoading = false,
            onUsernameChanged = {},
            onPasswordChanged = {},
            onLogin = {},
            onSignUpClick = {},
            snackbarHostState = SnackbarHostState()
        )
    }
}

@Preview
@Composable
private fun LoginContentLoadingPreview() {
    SettyTheme {
        LoginContent(
            username = "tars",
            password = "jaaars",
            isLoading = true,
            onUsernameChanged = {},
            onPasswordChanged = {},
            onLogin = {},
            onSignUpClick = {},
            snackbarHostState = SnackbarHostState()
        )
    }
}
