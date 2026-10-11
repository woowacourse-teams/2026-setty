package com.aksworns22.setty.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aksworns22.setty.feature.common.BackIconButton
import com.aksworns22.setty.feature.common.ErrorContent
import com.aksworns22.setty.feature.common.LoadUiState
import com.aksworns22.setty.feature.common.LoadingContent
import com.aksworns22.setty.feature.common.SectionTitle
import com.aksworns22.setty.feature.signup.PhoneNumberInputTransformation
import com.aksworns22.setty.feature.signup.PhoneNumberOutputTransformation
import com.aksworns22.setty.ui.theme.Paperlogy
import com.aksworns22.setty.ui.theme.SettyTheme

@Composable
fun ProfileEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onRequireLogin: () -> Unit,
    viewModel: ProfileEditViewModel = viewModel(factory = ProfileEditViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // 화면에 처음 들어올 때만 서버 값으로 폼을 채운다. 회전 등으로 다시 그려질 때는 입력을 유지한다.
    var isLoaded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!isLoaded) {
            isLoaded = true
            viewModel.load()
        }
        viewModel.event.collect { event ->
            when (event) {
                ProfileEditEvent.Saved -> onSaved()
                ProfileEditEvent.RequireLogin -> onRequireLogin()
                is ProfileEditEvent.Failed -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }
    ProfileEditContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = viewModel::load,
        onRequireLogin = onRequireLogin,
        onPhoneNumberChanged = viewModel::onPhoneNumberChanged,
        onAddressChanged = viewModel::onAddressChanged,
        onSave = viewModel::save,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileEditContent(
    uiState: ProfileEditUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRequireLogin: () -> Unit,
    onPhoneNumberChanged: (String) -> Unit,
    onAddressChanged: (String) -> Unit,
    onSave: () -> Unit,
) {
    val form = (uiState.form as? LoadUiState.Success)?.data
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        text = "정보 수정",
                        fontFamily = Paperlogy,
                        fontWeight = FontWeight.Black,
                    )
                },
                navigationIcon = { BackIconButton(onBack = onBack) },
            )
        },
        bottomBar = {
            if (form != null) {
                SaveButton(
                    isSaving = uiState.isSaving,
                    enabled = form.canSubmit,
                    onSave = onSave,
                )
            }
        },
        modifier = Modifier.imePadding(),
    ) { innerPadding ->
        when (val state = uiState.form) {
            LoadUiState.Loading -> LoadingContent(modifier = Modifier.padding(innerPadding))

            is LoadUiState.Error -> ErrorContent(
                message = state.message,
                isUnauthorized = state.isUnauthorized,
                onRetry = onRetry,
                onRequireLogin = onRequireLogin,
                modifier = Modifier.padding(innerPadding),
            )

            is LoadUiState.Success -> ProfileFormFields(
                form = state.data,
                onPhoneNumberChanged = onPhoneNumberChanged,
                onAddressChanged = onAddressChanged,
                onSave = onSave,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileFormFields(
    form: ProfileForm,
    onPhoneNumberChanged: (String) -> Unit,
    onAddressChanged: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val phoneNumberState = rememberTextFieldState(form.phoneNumber)
    LaunchedEffect(phoneNumberState) {
        snapshotFlow { phoneNumberState.text.toString() }.collect(onPhoneNumberChanged)
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 16.dp),
    ) {
        Text(
            text = "배송에 쓰일 연락처와 주소를 최신으로 유지해 주세요.\n이미 생성된 주문의 배송 정보는 바뀌지 않아요.",
            style = MaterialTheme.typography.bodyLargeEmphasized,
        )
        SectionTitle(text = "계정", modifier = Modifier.padding(top = 12.dp))
        OutlinedTextField(
            value = form.loginId,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text("세티 아이디", style = MaterialTheme.typography.bodyMediumEmphasized) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SectionTitle(text = "거래 정보", modifier = Modifier.padding(top = 12.dp))
        val isPhoneNumberError = form.phoneNumber.isNotEmpty() && !form.isPhoneNumberValid
        OutlinedTextField(
            state = phoneNumberState,
            label = { Text("전화번호", style = MaterialTheme.typography.bodyMediumEmphasized) },
            isError = isPhoneNumberError,
            supportingText = if (isPhoneNumberError) {
                { Text("전화번호는 010-0000-0000 형식입니다") }
            } else {
                null
            },
            inputTransformation = PhoneNumberInputTransformation,
            outputTransformation = PhoneNumberOutputTransformation,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next,
            ),
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.address,
            onValueChange = onAddressChanged,
            label = { Text("주소", style = MaterialTheme.typography.bodyMediumEmphasized) },
            isError = form.isAddressTooLong,
            supportingText = if (form.isAddressTooLong) {
                { Text("주소는 ${ProfileForm.ADDRESS_MAX_LENGTH}자 이내입니다") }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SaveButton(
    isSaving: Boolean,
    enabled: Boolean,
    onSave: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 16.dp, bottom = 16.dp, start = 32.dp, end = 32.dp),
    ) {
        if (!isSaving) {
            Button(
                onClick = onSave,
                enabled = enabled,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonDefaults.MediumContainerHeight),
            ) {
                Text("저장하기", style = MaterialTheme.typography.bodyLargeEmphasized)
            }
        } else {
            LoadingIndicator()
        }
    }
}

@Preview
@Composable
private fun ProfileEditContentPreview() {
    SettyTheme {
        ProfileEditContent(
            uiState = ProfileEditUiState(
                form = LoadUiState.Success(
                    ProfileForm(
                        loginId = "settyuser1",
                        phoneNumber = "01012345678",
                        address = "서울시 세티구 세티로 1",
                    )
                )
            ),
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onRetry = {},
            onRequireLogin = {},
            onPhoneNumberChanged = {},
            onAddressChanged = {},
            onSave = {},
        )
    }
}
