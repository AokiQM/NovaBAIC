package com.verlintas.baic2.device.api

/**
 * Foreground-service progress for an active agent run: keeps the process
 * alive in the background and offers a stop action in the notification.
 */
interface RunNotifier {
    /** Handler invoked when the user taps stop in the notification. */
    fun setStopHandler(handler: (() -> Unit)?)

    fun startRunning(title: String)

    fun stopRunning()
}
