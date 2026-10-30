package com.setty.android.devtools.update

import com.google.android.play.core.install.model.AppUpdateType

enum class UpdateScenario(val label: String, val updateType: Int?, val userAccepts: Boolean) {
    REAL_PLAY("실제 Play", null, true),
    FLEXIBLE_ACCEPT("Flexible: 수락하고 다운로드 완료", AppUpdateType.FLEXIBLE, true),
    FLEXIBLE_REJECT("Flexible: 거절", AppUpdateType.FLEXIBLE, false),
    IMMEDIATE_ACCEPT("Immediate: 수락", AppUpdateType.IMMEDIATE, true),
    IMMEDIATE_REJECT("Immediate: 거절", AppUpdateType.IMMEDIATE, false),
}
