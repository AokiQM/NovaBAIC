package com.verlintas.baic2.device.impl.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.verlintas.baic2.device.api.CachedNotification
import com.verlintas.baic2.device.api.NotificationCache

class BaicNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (notification.packageName == packageName) return
        val extras = notification.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        NotificationCache.record(
            CachedNotification(
                packageName = notification.packageName,
                title = title,
                text = text,
                postedAt = notification.postTime,
            ),
        )
    }
}
