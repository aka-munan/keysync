package com.devoid.keysync.service

import android.content.Context
import android.system.Os
import android.util.Log
import com.devoid.keysync.IInputReaderService
import com.devoid.keysync.IJniInputCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.system.exitProcess


class InputReaderService() : IInputReaderService.Stub() {

    //features
    /*users can now type in game/app using external keyboard through specific key combination*/
    var callback: IJniInputCallback? = null
    private val TAG = "InputReaderService"
    private var context: Context? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var ioJob: Job? = null

    constructor(context: Context) : this() {
        Log.i(TAG, "Launched with context: $context")
        this.context = context
    }

    init {
        try {
            System.loadLibrary("keysync")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }

    override fun destroy() {
        Log.i(TAG, "destroy");
        exitProcess(0)
    }

    override fun exit() {
        Log.i(TAG, "exit");
        destroy()
    }

    override fun run(): String? {
        ioJob?.cancel()
        ioJob = scope.launch {
            releaseActiveDevice()
            val nativeKeyboardPath = getKeyboardDevicePath()
            if (nativeKeyboardPath == null) {
                Log.e(TAG, "nativeKeyboardPath is null")
                return@launch
            }
            Log.i(TAG, "nativeKeyboardPath: listening to $nativeKeyboardPath")
            callback?.let {
                attachCallbackToNative(it)
            }
            val nativeFd = readInput(nativeKeyboardPath)
            Log.i(TAG, "finished reading, fd: $nativeFd")
            if (nativeFd < 0) {
                Log.e(TAG, "failed to open file")
                return@launch
            }
        }
        return "pid=" + Os.getpid() + ", uid=" + Os.getuid() + ", started";
    }

    override fun registerCallback(callback: IJniInputCallback?) {
        Log.i(TAG, "registerCallback: callback registered $callback")
        this.callback = callback
    }

    override fun releaseActiveDevice(): Int {
        return nativeReleaseActiveDevice()
    }

    override fun grabActiveDevice(): Int {
        return nativeGrabActiveDevice()

    }

    //external functions should be called from same thread
    external fun nativeGrabActiveDevice(): Int
    external fun nativeReleaseActiveDevice(): Int

    external fun readInput(openNativeFilepath: String): Int

    external fun attachCallbackToNative(callback: IJniInputCallback): Int
    external fun getKeyboardDevicePath(): String?
}