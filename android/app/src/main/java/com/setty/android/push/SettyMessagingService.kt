package com.setty.android.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.setty.android.MainActivity
import com.setty.android.R
import com.setty.android.SettyApp
import com.setty.android.navigation.DeepLink
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch

class SettyMessagingService : FirebaseMessagingService() {

    private val appContainer get() = (application as SettyApp).appContainer

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onNewToken(token: String) {
        appContainer.applicationScope.launch {
            runCatching { appContainer.deviceTokenRepository.register(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notificationId = message.data["notificationId"]?.toLongOrNull() ?: return
        val listingId = message.data["listingId"]?.toLongOrNull() ?: return
        val title = message.data["title"] ?: return

        appContainer.applicationScope.launch {
            runCatching { appContainer.notificationRepository.ack(notificationId) }
        }
        showNotification(notificationId, listingId, title)
    }

    private fun showNotification(notificationId: Long, listingId: Long, title: String) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return

        // 외부 딥링크와 같은 경로로 들어가게 해서 로그인 확인과 뒤로가기 스택을 한 곳에서 처리한다.
        // 앱이 떠 있으면 CLEAR_TOP + singleTop으로 기존 화면을 살린 채 onNewIntent로 받는다
        val intent = Intent(Intent.ACTION_VIEW, DeepLink.listingUri(listingId).toUri(), this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.outline_notifications_24)
            .setContentTitle("관심 키워드의 새 매물")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(notificationId.toInt(), notification)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "매물 알림",
            NotificationManager.IMPORTANCE_HIGH,
        )
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "listing_alerts"
    }
}
