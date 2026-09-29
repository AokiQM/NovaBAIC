/*
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of BetterAIChat2.
 *
 * BetterAIChat2 is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * BetterAIChat2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * BetterAIChat2. If not, see <https://www.gnu.org/licenses/>.
 */

package com.verlintas.baic2.device.impl.shell

import android.content.Context
import android.content.pm.PackageManager
import com.verlintas.baic2.device.api.ShellBridge
import com.verlintas.baic2.device.api.ShellResult
import com.verlintas.baic2.device.api.ShellState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

@Singleton
class ShizukuShellBridge @Inject constructor(
    @ApplicationContext private val context: Context,
) : ShellBridge {

    private val _state = MutableStateFlow(readState())
    override val state: StateFlow<ShellState> = _state.asStateFlow()

    init {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky { _state.value = readState() }
            Shizuku.addBinderDeadListener { _state.value = readState() }
            Shizuku.addRequestPermissionResultListener { _, _ -> _state.value = readState() }
        }
    }

    private fun readState(): ShellState = runCatching {
        when {
            !Shizuku.pingBinder() -> ShellState.Unavailable
            Shizuku.isPreV11() -> ShellState.Unavailable
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShellState.Ready
            else -> ShellState.PermissionRequired
        }
    }.getOrDefault(ShellState.Unavailable)

    override fun requestPermission() {
        runCatching {
            if (Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
            ) {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        }
    }

    override suspend fun exec(
        command: String,
        timeoutMs: Long,
        maxOutputChars: Int,
    ): ShellResult = withContext(Dispatchers.IO) {
        if (!Shizuku.pingBinder()) {
            return@withContext ShellResult.Unavailable(
                "Shizuku is not running. Install/open Shizuku and start its service first.",
            )
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return@withContext ShellResult.Unavailable(
                "Shizuku permission not granted. Open Settings → Permissions → Shizuku and allow access.",
            )
        }
        try {
            val service = rikka.shizuku.Shizuku.getBinder()?.let { binder ->
                moe.shizuku.server.IShizukuService.Stub.asInterface(binder)
            } ?: return@withContext ShellResult.Unavailable("Shizuku service is not available")

            val remote = service.newProcess(arrayOf("sh", "-c", command), null, null)
            val process = wrapRemoteProcess(remote)
            var truncated = false
            val stdoutBuffer = StringBuffer()
            val stderrBuffer = StringBuffer()
            val stdoutThread = Thread {
                runCatching {
                    process.inputStream.bufferedReader().use { reader ->
                        stdoutBuffer.append(reader.readText())
                    }
                }
            }
            val stderrThread = Thread {
                runCatching {
                    process.errorStream.bufferedReader().use { reader ->
                        stderrBuffer.append(reader.readText())
                    }
                }
            }
            stdoutThread.start()
            stderrThread.start()

            val finished = process.waitForTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                runCatching { process.destroy() }
                stdoutThread.join(500)
                return@withContext ShellResult.Timeout(stdoutBuffer.toString().trim())
            }
            stdoutThread.join(1_000)
            stderrThread.join(1_000)
            var stdout = stdoutBuffer.toString().trim()
            var stderr = stderrBuffer.toString().trim()
            if (stdout.length > maxOutputChars) {
                stdout = stdout.take(maxOutputChars)
                truncated = true
            }
            if (stderr.length > maxOutputChars / 2) {
                stderr = stderr.take(maxOutputChars / 2)
                truncated = true
            }
            val exitCode = runCatching { process.exitValue() }.getOrDefault(-1)
            ShellResult.Output(
                stdout = stdout,
                stderr = stderr,
                exitCode = exitCode,
                truncated = truncated,
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ShellResult.Unavailable(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * The AIDL hands back an [moe.shizuku.server.IRemoteProcess]; wrapping it
     * into ShizukuRemoteProcess (via its Parcelable contract) gives the
     * Process-compatible streams/timeout API.
     */
    private fun wrapRemoteProcess(
        remote: moe.shizuku.server.IRemoteProcess,
    ): rikka.shizuku.ShizukuRemoteProcess {
        val parcel = android.os.Parcel.obtain()
        return try {
            parcel.writeStrongBinder(remote.asBinder())
            parcel.setDataPosition(0)
            rikka.shizuku.ShizukuRemoteProcess.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }

    private companion object {
        const val REQUEST_CODE = 4211
    }
}
