package com.setty.android.devtools.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri

/**
 * 외부 진입점 재현. 앱 밖에서 들어오는 것과 같은 VIEW 인텐트로 앱을 연다.
 * 앱이 떠 있을 때·꺼져 있을 때를 모두 확인할 수 있게 "앱을 끄고 열기"를 고를 수 있다.
 */
@Composable
internal fun DeepLinkPanel() {
    val context = LocalContext.current
    var uri by rememberSaveable { mutableStateOf(PRESETS.first().uri) }
    var isColdStart by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PRESETS.forEach { preset ->
            Text(
                text = "${preset.label}  ${preset.uri}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uri = preset.uri }
                    .padding(vertical = 4.dp),
            )
        }
        OutlinedTextField(
            value = uri,
            onValueChange = { uri = it },
            label = { Text("딥링크") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isColdStart = !isColdStart },
        ) {
            Checkbox(checked = isColdStart, onCheckedChange = null)
            Text("앱을 끄고 열기 (종료 상태에서 진입)")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { context.openDeepLink(uri, isColdStart) }) {
                Text("딥링크 실행")
            }
            OutlinedButton(onClick = { context.showTestNotification(uri) }) {
                Text("테스트 알림 보내기")
            }
        }
        Text(
            "알림은 받은 뒤 홈으로 나가거나 최근 앱에서 지운 뒤 눌러 백그라운드·종료 상태를 확인하세요.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private data class DeepLinkPreset(val label: String, val uri: String)

private val PRESETS = listOf(
    DeepLinkPreset("매물 상세", "setty://listings/1"),
    DeepLinkPreset("주문 상세", "setty://orders/1"),
    DeepLinkPreset("삭제된 매물", "setty://listings/999999999"),
    DeepLinkPreset("삭제된 주문", "setty://orders/999999999"),
    DeepLinkPreset("잘못된 id", "setty://listings/abc"),
    DeepLinkPreset("모르는 대상", "setty://unknown/1"),
)

private fun Context.deepLinkIntent(uri: String): Intent =
    Intent(Intent.ACTION_VIEW, uri.trim().toUri()).setPackage(packageName)

private fun Context.openDeepLink(uri: String, isColdStart: Boolean) {
    val intent = deepLinkIntent(uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent.resolveActivity(packageManager) == null) {
        Toast.makeText(this, "앱이 받지 않는 링크예요 (인텐트 필터 불일치)", Toast.LENGTH_SHORT).show()
        return
    }
    if (isColdStart) {
        // 개발자 도구와 앱은 같은 프로세스라 프로세스를 끝내면 앱도 종료 상태에서 링크를 받는다
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK))
        Runtime.getRuntime().exit(0)
    } else {
        startActivity(intent)
    }
}

private fun Context.showTestNotification(uri: String) {
    val manager = NotificationManagerCompat.from(this)
    if (!manager.areNotificationsEnabled()) {
        Toast.makeText(this, "알림 권한이 꺼져 있어요", Toast.LENGTH_SHORT).show()
        return
    }
    manager.createNotificationChannel(
        NotificationChannel(TEST_CHANNEL_ID, "개발자 도구 테스트 알림", NotificationManager.IMPORTANCE_HIGH),
    )
    // 실제 매물 알림과 같은 플래그로 연다
    val intent = deepLinkIntent(uri)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
    val pendingIntent = PendingIntent.getActivity(
        this,
        notificationId,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val notification = NotificationCompat.Builder(this, TEST_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("테스트 알림")
        .setContentText(uri)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()
    try {
        manager.notify(notificationId, notification)
    } catch (_: SecurityException) {
        Toast.makeText(this, "알림 권한이 꺼져 있어요", Toast.LENGTH_SHORT).show()
    }
}

private const val TEST_CHANNEL_ID = "devtools_test"
