package com.verlintas.baic2.device.api

import java.util.concurrent.CopyOnWriteArrayList

data class CachedNotification(
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
)

/** Recent notifications captured by the listener service while access is granted. */
object NotificationCache {
    private const val MAX = 60
    private val items = CopyOnWriteArrayList<CachedNotification>()

    fun record(notification: CachedNotification) {
        items.add(notification)
        while (items.size > MAX) items.removeAt(0)
    }

    fun snapshot(limit: Int, sinceMillis: Long, appFilter: String?): List<CachedNotification> =
        items.asReversed()
            .filter { it.postedAt >= sinceMillis }
            .filter { appFilter.isNullOrBlank() || it.packageName.contains(appFilter, ignoreCase = true) }
            .take(limit.coerceIn(1, MAX))
}
