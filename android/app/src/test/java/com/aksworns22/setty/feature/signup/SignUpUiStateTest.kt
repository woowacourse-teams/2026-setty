package com.aksworns22.setty.feature.signup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignUpUiStateTest {

    private val validState = SignUpUiState(
        loginId = "setty01",
        password = "password1",
        passwordConfirm = "password1",
        phoneNumber = "01000000000",
        address = "서울시 가상구 테스트로 1",
    )

    @Test
    fun `모든 입력이 유효하면 가입할 수 있다`() {
        assertTrue(validState.canSubmit)
    }

    @Test
    fun `빈 입력에는 오류를 표시하지 않지만 가입할 수 없다`() {
        val state = SignUpUiState()

        assertNull(state.loginIdError)
        assertNull(state.passwordError)
        assertNull(state.phoneNumberError)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `아이디는 영문 소문자와 숫자 4~20자만 허용한다`() {
        assertEquals(SignUpFieldError.INVALID_LOGIN_ID, validState.copy(loginId = "abc").loginIdError)
        assertEquals(SignUpFieldError.INVALID_LOGIN_ID, validState.copy(loginId = "Setty01").loginIdError)
        assertEquals(SignUpFieldError.INVALID_LOGIN_ID, validState.copy(loginId = "a".repeat(21)).loginIdError)
        assertNull(validState.copy(loginId = "a".repeat(20)).loginIdError)
    }

    @Test
    fun `비밀번호는 8~64자만 허용한다`() {
        assertEquals(SignUpFieldError.INVALID_PASSWORD_LENGTH, validState.copy(password = "1234567").passwordError)
        assertEquals(SignUpFieldError.INVALID_PASSWORD_LENGTH, validState.copy(password = "a".repeat(65)).passwordError)
        assertNull(validState.copy(password = "12345678").passwordError)
    }

    @Test
    fun `비밀번호 확인이 다르면 가입할 수 없다`() {
        val state = validState.copy(passwordConfirm = "password2")

        assertEquals(SignUpFieldError.PASSWORD_MISMATCH, state.passwordConfirmError)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `전화번호는 010으로 시작하는 11자리 숫자만 허용한다`() {
        assertEquals(SignUpFieldError.INVALID_PHONE_NUMBER, validState.copy(phoneNumber = "0100000000").phoneNumberError)
        assertEquals(SignUpFieldError.INVALID_PHONE_NUMBER, validState.copy(phoneNumber = "01100000000").phoneNumberError)
        assertEquals(SignUpFieldError.INVALID_PHONE_NUMBER, validState.copy(phoneNumber = "010-0000-0000").phoneNumberError)
    }

    @Test
    fun `주소가 공백이거나 200자를 넘으면 가입할 수 없다`() {
        assertFalse(validState.copy(address = "   ").canSubmit)
        assertEquals(SignUpFieldError.ADDRESS_TOO_LONG, validState.copy(address = "가".repeat(201)).addressError)
        assertFalse(validState.copy(address = "가".repeat(201)).canSubmit)
    }

    @Test
    fun `요청 중에는 가입할 수 없다`() {
        assertFalse(validState.copy(isLoading = true).canSubmit)
    }

    @Test
    fun `전화번호 입력에 하이픈을 자동으로 넣는다`() {
        assertEquals("010", SignUpViewModel.formatPhoneNumber("010"))
        assertEquals("010-0000", SignUpViewModel.formatPhoneNumber("0100000"))
        assertEquals("010-0000-0000", SignUpViewModel.formatPhoneNumber("01000000000"))
        assertEquals("010-0000-0000", SignUpViewModel.formatPhoneNumber("010-0000-00001"))
    }
}
