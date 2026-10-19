package cloud.setty.setty.feature.common

import cloud.setty.setty.data.ListingImageTooLargeException
import cloud.setty.setty.network.ApiException
import java.io.IOException

/** 사용자에게 보여줄 에러 문구. 서버 에러는 서버가 내려준 문구를 그대로 쓴다. */
fun Throwable.toUserMessage(fallback: String = "알 수 없는 에러가 발생했습니다"): String = when (this) {
    is ApiException -> if (isUnauthorized) "로그인이 필요합니다" else message
    is ListingImageTooLargeException -> message.orEmpty()
    is IOException -> "네트워크 에러가 발생했습니다"
    else -> fallback
}

/** 토큰이 없거나 만료돼 다시 로그인해야 하는 에러인지 여부. */
val Throwable.isUnauthorized: Boolean
    get() = this is ApiException && code == INVALID_TOKEN

/** 회원 행정동을 정하지 않아 행정동 선택 화면으로 보내야 하는 에러인지 여부. */
val Throwable.isMemberDongRequired: Boolean
    get() = this is ApiException && code == MEMBER_DONG_REQUIRED

private const val INVALID_TOKEN = "INVALID_TOKEN"
private const val MEMBER_DONG_REQUIRED = "MEMBER_DONG_REQUIRED"
