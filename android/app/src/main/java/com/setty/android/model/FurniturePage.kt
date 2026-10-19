package com.setty.android.model

import com.setty.android.core.model.FurnitureSummary

/** 커서로 나눠 받은 매물 목록. [nextCursor]가 null이면 마지막 페이지다. */
data class FurniturePage(
    val items: List<FurnitureSummary>,
    val nextCursor: String?,
)
