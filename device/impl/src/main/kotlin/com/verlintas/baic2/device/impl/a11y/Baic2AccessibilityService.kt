package com.verlintas.baic2.device.impl.a11y

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class Baic2AccessibilityService : AccessibilityService() {

    @Inject
    lateinit var bridge: AndroidAccessibilityBridge

    override fun onServiceConnected() {
        super.onServiceConnected()
        bridge.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        bridge.detach(this)
        super.onDestroy()
    }
}
