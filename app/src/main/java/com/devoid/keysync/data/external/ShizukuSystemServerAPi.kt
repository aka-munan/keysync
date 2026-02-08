package com.devoid.keysync.data.external

import android.content.ComponentName
import android.content.ServiceConnection
import android.hardware.input.InputManager
import android.os.IBinder
import android.util.Log
import android.view.InputEvent
import com.devoid.keysync.BuildConfig
import com.devoid.keysync.IInputReaderService
import com.devoid.keysync.IJniInputCallback
import com.devoid.keysync.domain.EventHandler
import com.devoid.keysync.domain.EventInjectorWithRandImpl
import com.devoid.keysync.service.InputReaderService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method


class ShizukuSystemServerAPi(private val inputManager: InputManager) {
    private val TAG = "ShizukuSystemServerApi"
    private var injectInputEventFunction: Method? = null
    private var inputManagerInstance: Any? = null
    private var isBinderReceived: Boolean = false
    private var inputReaderService: IInputReaderService? = null
    private var callback: IJniInputCallback? = null

    init {
        injectInputEventFunction = getInputApi()
        Shizuku.addBinderReceivedListenerSticky { isBinderReceived = true }
        inputManager.registerInputDeviceListener(object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(p0: Int) {
                Log.i(TAG, "onInputDeviceAdded: device added$p0")
                if (isBinderReceived) {
                    callback?.let {
                        inputReaderService?.registerCallback(it)
                    }
                    inputReaderService?.run()
                }
            }

            override fun onInputDeviceRemoved(p0: Int) {
                Log.i(TAG, "onInputDeviceRemoved: device removed$p0")
                if (isBinderReceived) {
                    callback?.let {
                        inputReaderService?.registerCallback(it)
                    }
                    inputReaderService?.run()
                }
            }

            override fun onInputDeviceChanged(p0: Int) {
            }
        }, null)
    }

    private val inputReaderServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            p0: ComponentName?,
            binder: IBinder?
        ) {
            if (binder != null && binder.pingBinder()) {
                val inputReaderService = IInputReaderService.Stub.asInterface(binder)
                try {
                    callback?.let {
                        inputReaderService.registerCallback(it)
                    }
                    val output = inputReaderService.run()
                    Log.i(TAG, "onServiceConnected: output: $output")
                } catch (e: Exception) {
                    Log.e(TAG, "onServiceConnected: ", e)
                }
                this@ShizukuSystemServerAPi.inputReaderService = inputReaderService
            } else {
                Log.e(TAG, "onServiceConnected: binder is null or not pingable")
            }
            Log.i(TAG, "onServiceConnected: $p0")
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            this@ShizukuSystemServerAPi.inputReaderService = null
            Log.i(TAG, "onServiceDisconnected: $p0")
        }
    }

    private fun getInputApi(): Method? {
        val inputManagerBinder: IBinder =
            ShizukuBinderWrapper(SystemServiceHelper.getSystemService("input"))
        val inputManagerStub = Class.forName("android.hardware.input.IInputManager\$Stub")
        val inputManagerClass = Class.forName("android.hardware.input.IInputManager")
        inputManagerInstance =
            inputManagerStub.getMethod("asInterface", IBinder::class.java)
                .invoke(null, inputManagerBinder)
        val injectInputEvent = inputManagerClass.getMethod(
            "injectInputEvent",
            InputEvent::class.java,
            Int::class.java
        )
        return injectInputEvent
    }

    fun startInputReaderService(callback: IJniInputCallback) {
        try {
            this.callback = callback
            val serviceArgs = Shizuku.UserServiceArgs(
                ComponentName(
                    BuildConfig.APPLICATION_ID,
                    InputReaderService::class.java.name
                )
            ).tag("KeySyncInputReaderService")
                .daemon(false)
                .debuggable(BuildConfig.DEBUG)
                .processNameSuffix("service")
                .version(BuildConfig.VERSION_CODE)
            CoroutineScope(Dispatchers.Default).launch {
                while (!isBinderReceived) {
                    delay(100)
                }
                delay(1000)
                Shizuku.bindUserService(serviceArgs, inputReaderServiceConnection)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getInputApi: ", e)
        }
    }

    fun grabActiveDevice() {
        val result = inputReaderService?.grabActiveDevice()
        if (result == null) {
            Log.e(TAG, "grabActiveDevice: InputReaderService is null")
            return
        }
        Log.i(TAG, "grabActiveDevice: $result")
    }

    fun releaseActiveDevice() {
        val result = inputReaderService?.releaseActiveDevice()
        if (result == null) {
            Log.e(TAG, "releaseActiveDevice: InputReaderService is null")
            return
        }
        Log.i(TAG, "releaseActiveDevice: $result")
    }

    fun getEventHandler(): EventHandler {
        if (injectInputEventFunction == null || inputManagerInstance == null) {
            throw IllegalStateException("Input manager Service not connected")
        }
        return EventHandler(object : EventInjectorWithRandImpl() {
            override fun inject(event: InputEvent) {
//               Log.d(TAG, "inject: injecting: $event")
                injectInputEventFunction?.invoke(inputManagerInstance, event, 1)
            }
        })
    }


}