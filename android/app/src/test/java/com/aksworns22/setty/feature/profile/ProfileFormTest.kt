package com.aksworns22.setty.feature.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileFormTest {

    private val validForm = ProfileForm(
        loginId = "settyuser1",
        phoneNumber = "01012345678",
        address = "서울시 세티구 세티로 1",
    )

    @Test
    fun `전화번호와 주소가 유효하면 저장할 수 있다`() {
        assertTrue(validForm.canSubmit)
    }

    @Test
    fun `010으로 시작하는 11자리가 아니면 저장할 수 없다`() {
        assertFalse(validForm.copy(phoneNumber = "0101234567").canSubmit)
        assertFalse(validForm.copy(phoneNumber = "01112345678").canSubmit)
    }

    @Test
    fun `주소가 비었거나 200자를 넘으면 저장할 수 없다`() {
        assertFalse(validForm.copy(address = " ").canSubmit)
        assertFalse(validForm.copy(address = "가".repeat(201)).canSubmit)
    }
}
