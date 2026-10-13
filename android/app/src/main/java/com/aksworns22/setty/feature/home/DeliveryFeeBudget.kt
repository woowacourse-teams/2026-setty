package com.aksworns22.setty.feature.home

import com.aksworns22.setty.model.formatWon

/** 배송비 예산 필터에서 고를 수 있는 값과 표시 문구. */
object DeliveryFeeBudget {
    val PRESETS = listOf(10_000)

    // 기본 배송비보다 작은 예산은 서버가 항상 빈 목록을 주므로 슬라이더는 기본 배송비부터 시작한다
    const val MIN = 10_000
    const val MAX = 100_000
    const val STEP = 5_000

    // 직접 고를 때 처음 보여줄 예산
    const val DEFAULT = 20_000

    // 양 끝을 뺀 슬라이더 눈금 수
    val SLIDER_STEPS = (MAX - MIN) / STEP - 1

    /** 슬라이더 값을 가장 가까운 [STEP] 단위 예산으로 맞춘다. */
    fun snap(value: Float): Int {
        val steps = ((value - MIN) / STEP).let(Math::round)
        return (MIN + steps * STEP).coerceIn(MIN, MAX)
    }

    /** 15000 → "1.5만 원", 20000 → "2만 원", 만·천 단위로 떨어지지 않으면 "23,500원" */
    fun format(value: Int): String {
        if (value % 1_000 != 0) return formatWon(value)
        val man = value / 10_000
        val thousand = value % 10_000 / 1_000
        return if (thousand == 0) "${man}만 원" else "$man.${thousand}만 원"
    }
}
