package com.setty.android.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthInterceptorTest {
    @Test
    fun `only explicit invalid token response invalidates session`() {
        assertTrue(isInvalidTokenResponse(401, """{"code":"INVALID_TOKEN","message":"expired"}"""))
        assertFalse(isInvalidTokenResponse(403, """{"code":"INVALID_TOKEN"}"""))
        assertFalse(isInvalidTokenResponse(500, """{"code":"INVALID_TOKEN"}"""))
        assertFalse(isInvalidTokenResponse(401, """{"code":"OTHER_ERROR"}"""))
        assertFalse(isInvalidTokenResponse(401, "not json"))
        assertFalse(isInvalidTokenResponse(401, "{}"))
    }
}
