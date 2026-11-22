package com.setty.android.navigation

import java.net.URI
import java.net.URISyntaxException

/**
 * 앱 밖(알림, 딥링크)에서 들어온 진입 대상.
 * 들어온 값은 그대로 믿지 않고 [parseDeepLink]로 형식과 id를 검증한 뒤에만 화면으로 바꾼다.
 */
sealed interface DeepLink {
    /** 로그인이 필요한 대상인지 여부. 지금 진입 대상은 모두 회원 화면이라 로그인이 필요하다. */
    val requiresLogin: Boolean get() = true

    data class Listing(val listingId: Long) : DeepLink

    data class Order(val orderId: Long) : DeepLink

    /** 형식이 잘못됐거나 앱이 모르는 링크. 진입하지 않고 안내만 한다. */
    data object Invalid : DeepLink {
        override val requiresLogin: Boolean get() = false
    }

    companion object {
        const val SCHEME = "setty"
        const val HOST_LISTINGS = "listings"
        const val HOST_ORDERS = "orders"

        fun listingUri(listingId: Long): String = "$SCHEME://$HOST_LISTINGS/$listingId"

        fun orderUri(orderId: Long): String = "$SCHEME://$HOST_ORDERS/$orderId"
    }
}

/**
 * `setty://listings/{id}`, `setty://orders/{id}`를 [DeepLink]로 바꾼다.
 * 링크가 없으면 null, 형식·대상·id가 하나라도 맞지 않으면 [DeepLink.Invalid]를 돌려준다.
 */
fun parseDeepLink(uriString: String?): DeepLink? {
    if (uriString.isNullOrBlank()) return null
    val uri = try {
        URI(uriString)
    } catch (_: URISyntaxException) {
        return DeepLink.Invalid
    }
    if (uri.scheme != DeepLink.SCHEME) return DeepLink.Invalid
    val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
    val id = segments.singleOrNull()?.toLongOrNull()?.takeIf { it > 0 } ?: return DeepLink.Invalid
    return when (uri.host) {
        DeepLink.HOST_LISTINGS -> DeepLink.Listing(id)
        DeepLink.HOST_ORDERS -> DeepLink.Order(id)
        else -> DeepLink.Invalid
    }
}

/**
 * 앱이 꺼져 있을 때 진입하면 쓸 뒤로가기 스택.
 * 앱 안에서 대상 화면까지 가는 경로를 그대로 쌓아서 뒤로가기가 앱 안 흐름과 같게 동작한다.
 */
fun DeepLink.toBackStack(): List<SettyScreen> = when (this) {
    is DeepLink.Listing -> listOf(SettyScreen.Home, SettyScreen.FurnitureDetail(listingId))
    is DeepLink.Order -> listOf(SettyScreen.Home, SettyScreen.Orders, SettyScreen.OrderDetail(orderId))
    DeepLink.Invalid -> listOf(SettyScreen.Home)
}

/** 앱이 실행 중일 때 진입하면 보던 화면 위에 쌓을 대상 화면. */
fun DeepLink.target(): SettyScreen? = when (this) {
    is DeepLink.Listing -> SettyScreen.FurnitureDetail(listingId)
    is DeepLink.Order -> SettyScreen.OrderDetail(orderId)
    DeepLink.Invalid -> null
}

/** 저장(rememberSaveable)하거나 알림에 실을 수 있게 링크 문자열로 되돌린다. */
fun DeepLink.toUri(): String? = when (this) {
    is DeepLink.Listing -> DeepLink.listingUri(listingId)
    is DeepLink.Order -> DeepLink.orderUri(orderId)
    DeepLink.Invalid -> null
}
