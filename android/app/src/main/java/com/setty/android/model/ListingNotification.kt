package com.setty.android.model

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class ListingNotification(
    val id: Long,
    val listingId: Long,
    val title: String,
    val status: NotificationStatus,
    val createdAt: String,
) {
    val createdAtLabel: String
        get() = formatNotificationTime(createdAt)
}

enum class NotificationStatus(val label: String) {
    PENDING("발송 중"),
    SENT("발송됨"),
    FAILED("발송 실패"),
}

fun notificationStatusOf(value: String): NotificationStatus =
    NotificationStatus.entries.find { it.name == value } ?: NotificationStatus.PENDING

/** ISO-8601 UTC 문자열(예: 2026-10-11T12:34:56.123Z)을 기기 시간대의 "10.11 21:34"로 만든다. */
fun formatNotificationTime(createdAt: String): String {
    if (createdAt.length < 19) return ""
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val date = runCatching { parser.parse(createdAt.substring(0, 19)) }.getOrNull() ?: return ""
    return SimpleDateFormat("M.d HH:mm", Locale.KOREA).format(date)
}
