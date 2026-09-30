package com.aksworns22.setty.feature.signup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aksworns22.setty.ui.theme.SettyTheme

@Composable
fun SignUpScreen(
    onSignUpSuccess: () -> Unit,
    viewModel: SignUpViewModel = viewModel(factory = SignUpViewModel.Factory)
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                SignUpEvent.SUCCESS -> {
                    onSignUpSuccess()
                }

                SignUpEvent.DUPLICATE_LOGIN_ID -> {
                    snackbarHostState.showSnackbar("이미 사용 중인 아이디입니다")
                }

                SignUpEvent.INVALID_REQUEST -> {
                    snackbarHostState.showSnackbar("입력한 정보를 다시 확인해주세요")
                }

                SignUpEvent.NETWORK_ERROR -> {
                    snackbarHostState.showSnackbar("네트워크 에러가 발생했습니다")
                }

                SignUpEvent.UNKNOWN_ERROR -> {
                    snackbarHostState.showSnackbar("알 수 없는 에러가 발생했습니다")
                }
            }
        }
    }
    SignUpContent(
        uiState = uiState.value,
        onLoginIdChanged = viewModel::onLoginIdChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onPasswordConfirmChanged = viewModel::onPasswordConfirmChanged,
        onPhoneNumberChanged = viewModel::onPhoneNumberChanged,
        onAddressChanged = viewModel::onAddressChanged,
        onSignUp = viewModel::signUp,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SignUpContent(
    uiState: SignUpUiState,
    onLoginIdChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPasswordConfirmChanged: (String) -> Unit,
    onPhoneNumberChanged: (String) -> Unit,
    onAddressChanged: (String) -> Unit,
    onSignUp: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val phoneNumberState = rememberTextFieldState(uiState.phoneNumber)
    LaunchedEffect(phoneNumberState) {
        snapshotFlow { phoneNumberState.text.toString() }.collect(onPhoneNumberChanged)
    }
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
            SignUpButton(
                isLoading = uiState.isLoading,
                enabled = uiState.canSubmit,
                onSignUp = onSignUp,
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
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(text = "SETTY 회원가입", style = MaterialTheme.typography.headlineLargeEmphasized)
                Text(
                    text = "계정을 만들고\nSETTY에서 중고 가구 거래를 시작하세요.",
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                )
                SignUpSection(title = "계정 정보") {
                    SignUpTextField(
                        value = uiState.loginId,
                        onValueChange = onLoginIdChanged,
                        label = "세티 아이디",
                        error = uiState.loginIdError?.toMessage(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    SignUpTextField(
                        value = uiState.password,
                        onValueChange = onPasswordChanged,
                        label = "세티 비밀번호",
                        error = uiState.passwordError?.toMessage(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    SignUpTextField(
                        value = uiState.passwordConfirm,
                        onValueChange = onPasswordConfirmChanged,
                        label = "세티 비밀번호 확인",
                        error = uiState.passwordConfirmError?.toMessage(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                SignUpSection(title = "거래 정보") {
                    PhoneNumberTextField(
                        state = phoneNumberState,
                        label = "전화번호",
                        error = uiState.phoneNumberError?.toMessage(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    SignUpTextField(
                        value = uiState.address,
                        onValueChange = onAddressChanged,
                        label = "주소",
                        error = uiState.addressError?.toMessage(),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { onSignUp() }
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SignUpButton(
    isLoading: Boolean,
    enabled: Boolean,
    onSignUp: () -> Unit,
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
                onClick = onSignUp,
                enabled = enabled,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonDefaults.MediumContainerHeight),
            ) {
                Text("가입하기", style = MaterialTheme.typography.bodyLargeEmphasized)
            }
        } else {
            LoadingIndicator()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SignUpSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(top = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
private fun SignUpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodyMediumEmphasized) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun PhoneNumberTextField(
    state: TextFieldState,
    label: String,
    error: String?,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        state = state,
        label = { Text(label, style = MaterialTheme.typography.bodyMediumEmphasized) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        inputTransformation = PhoneNumberInputTransformation,
        outputTransformation = PhoneNumberOutputTransformation,
        keyboardOptions = keyboardOptions,
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = modifier.fillMaxWidth(),
    )
}

private val PhoneNumberInputTransformation = InputTransformation {
    val digits = asCharSequence().filter(Char::isDigit).take(SignUpViewModel.PHONE_NUMBER_MAX_DIGITS).toString()
    if (digits != asCharSequence().toString()) replace(0, length, digits)
}

private val PhoneNumberOutputTransformation = OutputTransformation {
    if (length > 3) insert(3, "-")
    if (length > 8) insert(8, "-")
}

private fun SignUpFieldError.toMessage(): String = when (this) {
    SignUpFieldError.INVALID_LOGIN_ID -> "아이디는 영문 소문자,숫자로 구성되며 4~20자입니다"
    SignUpFieldError.INVALID_PASSWORD_LENGTH -> "비밀번호는 8~64자입니다"
    SignUpFieldError.PASSWORD_MISMATCH -> "비밀번호가 일치하지 않습니다"
    SignUpFieldError.INVALID_PHONE_NUMBER -> "전화번호는 010-0000-0000 형식입니다"
    SignUpFieldError.ADDRESS_TOO_LONG -> "주소는 ${SignUpUiState.ADDRESS_MAX_LENGTH}자 이내입니다"
}

@Preview
@Composable
private fun SignUpContentPreview() {
    SettyTheme {
        SignUpContent(
            uiState = SignUpUiState(
                loginId = "setty01",
                password = "password1",
                passwordConfirm = "password1",
                phoneNumber = "01000000000",
                address = "서울시 가상구 테스트로 1",
            ),
            onLoginIdChanged = {},
            onPasswordChanged = {},
            onPasswordConfirmChanged = {},
            onPhoneNumberChanged = {},
            onAddressChanged = {},
            onSignUp = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun SignUpContentErrorPreview() {
    SettyTheme {
        SignUpContent(
            uiState = SignUpUiState(
                loginId = "SE",
                password = "short",
                passwordConfirm = "shorter",
                phoneNumber = "01000",
            ),
            onLoginIdChanged = {},
            onPasswordChanged = {},
            onPasswordConfirmChanged = {},
            onPhoneNumberChanged = {},
            onAddressChanged = {},
            onSignUp = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
