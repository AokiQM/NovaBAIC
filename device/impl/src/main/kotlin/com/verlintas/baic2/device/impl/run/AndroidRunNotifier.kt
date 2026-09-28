package com.verlintas.baic2.device.impl.run

import android.content.Context
import com.verlintas.baic2.device.api.RunNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidRunNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : RunNotifier {

    override fun setStopHandler(handler: (() -> Unit)?) {
        RunControl.stopHandler = handler
    }

    override fun startRunning(title: String) {
        RunService.start(context, title)
    }

    override fun stopRunning() {
        RunService.stop(context)
    }
}
