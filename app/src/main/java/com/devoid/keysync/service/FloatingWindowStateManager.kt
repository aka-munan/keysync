package com.devoid.keysync.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.hardware.input.InputManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.core.view.WindowInsetsCompat
import androidx.datastore.preferences.core.Preferences
import com.devoid.keysync.IJniInputCallback
import com.devoid.keysync.data.local.DataStoreManager
import com.devoid.keysync.model.AppConfig
import com.devoid.keysync.model.DraggableItem
import com.devoid.keysync.model.DraggableItemType
import com.devoid.keysync.domain.EventHandler
import com.devoid.keysync.data.external.ShizukuSystemServerAPi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

class FloatingWindowStateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStoreManager: DataStoreManager
) {

    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val shizukuSystemServerAPi =
        ShizukuSystemServerAPi(context.getSystemService(Context.INPUT_SERVICE) as InputManager)
    private val eventHandler: EventHandler by lazy { shizukuSystemServerAPi.getEventHandler() }
    val windowManager: WindowManager by lazy { context.getSystemService(WindowManager::class.java) }
    private val displayMetrics = context.resources.displayMetrics

    val pointerSensitivity = MutableStateFlow(0.5f)
    val overlayOpacity = MutableStateFlow(0.5f)

    private var buttonConfigKey: Preferences.Key<String>? = null

    private val _appConfig = MutableStateFlow(AppConfig.Default)
    val keysConfig = _appConfig.asStateFlow()

    val sensitivity = (10f * pointerSensitivity.value)//at max: 10x the original offset

    private val _isBubbleExpanded = MutableStateFlow(false)
    val isBubbleExpanded = _isBubbleExpanded.asStateFlow()

    private val _mousePointerOffset =
        MutableStateFlow(Offset(displayMetrics.widthPixels / 2f, displayMetrics.heightPixels / 2f))
    val pointerOffset = _mousePointerOffset.asStateFlow()

    private val _containerItems = MutableStateFlow(listOf<DraggableItem>())
    val containerItems = _containerItems.asStateFlow()

    val isShootingMode = eventHandler.shootingModeFlow
    private val inputCallback: IJniInputCallback.Stub = object : IJniInputCallback.Stub() {
        override fun onKeyDown(code: Int) {
            if (_isBubbleExpanded.value)
                return
            val keyEvent = KeyEvent(
                KeyEvent.ACTION_DOWN,
                code,
            )
            eventHandler.handleKeyEvent(keyEvent)
        }

        override fun onKeyUp(code: Int) {//occupy keyboard so windowmanager dosent receive events
            if (_isBubbleExpanded.value)
                return
            val keyEvent = KeyEvent(
                KeyEvent.ACTION_UP,
                code,
            )
            eventHandler.handleKeyEvent(keyEvent)
        }

    }


    init {
        shizukuSystemServerAPi.startInputReaderService(inputCallback)
        scope.launch {
            isBubbleExpanded.drop(1).collect { isExpanded ->
                if (isExpanded)
                    return@collect
                buttonConfigKey?.let {
                    dataStoreManager.save(DataStoreManager.OVERLAY_OPACITY, overlayOpacity.value)
                    dataStoreManager.save(
                        DataStoreManager.POINTER_SENSITIVITY,
                        pointerSensitivity.value
                    )
                    dataStoreManager.save(it, containerItems.value)
                }
                _appConfig.value =
                    dataStoreManager.getKeyConfig(DataStoreManager.KEYS_CONFIG).first()
            }
        }
        scope.launch {
            _appConfig.value = dataStoreManager.getKeyConfig(DataStoreManager.KEYS_CONFIG).first()
            eventHandler.setCancelableTouchMode(_appConfig.value.cancellableTouchMode)
            eventHandler.setNormalBtnTouchMode(_appConfig.value.normalBtnTouchMode)
            eventHandler.appConfig = _appConfig.value
        }

    }

    fun loadButtonsConfig(packageName: String) {
        buttonConfigKey = DataStoreManager.getButtonsConfigKey(packageName)
        scope.launch {
            _containerItems.value = dataStoreManager.getButtons(buttonConfigKey!!).first()
            eventHandler.updateKeyMapping(containerItems.value)
            dataStoreManager.getFloat(DataStoreManager.OVERLAY_OPACITY).first()?.let {
                overlayOpacity.value = it
            }
            dataStoreManager.getFloat(DataStoreManager.POINTER_SENSITIVITY).first()?.let {
                pointerSensitivity.value = it
            }
        }
    }

    fun addNewItem(itemType: DraggableItemType) {
        val itemID = containerItems.value.sumOf { it.id } + 1
        val offset = Offset(0f, 200f)
        val item = when (itemType) {
            DraggableItemType.KEY -> {
                DraggableItem.VariableKey(itemID, position = offset, size = 0)
            }

            DraggableItemType.WASD_KEY -> {
                DraggableItem.WASDGroup(itemID, offset)
            }

            DraggableItemType.SHOOTING_MODE -> {
                DraggableItem.FixedKey(
                    itemID,
                    offset,
                    size = 0,
                    type = itemType,
                    keyCode = keysConfig.value.shootingModeKeyCode
                )

            }

            DraggableItemType.FIRE -> {
                DraggableItem.FixedKey(
                    itemID,
                    offset,
                    size = 0,
                    type = itemType,
                    keyCode = keysConfig.value.fireKeyCode
                )
            }

            DraggableItemType.BAG_MAP -> {
                DraggableItem.CancelableKey(
                    itemID,
                    offset,
                    cancelPosition = offset,
                    size = 0,
                    type = itemType
                )
            }

            DraggableItemType.SCOPE -> {
                DraggableItem.FixedKey(
                    itemID,
                    offset,
                    size = 0,
                    type = itemType,
                    keyCode = keysConfig.value.scopeKeyCode
                )
            }
        }
        _containerItems.value = containerItems.value.plus(item)
    }

    fun removeItem(id: Int) {
        _containerItems.value = _containerItems.value.filterNot { it.id == id }
    }

    fun onFloatingBubbleClick() {
        _isBubbleExpanded.value = !_isBubbleExpanded.value
        if (!_isBubbleExpanded.value) {
            eventHandler.updateKeyMapping(containerItems.value)
            shizukuSystemServerAPi.grabActiveDevice()//grab device so that android system dosent receive events
        }
        shizukuSystemServerAPi.releaseActiveDevice()//grab device so that android system  receive events
    }

    @Deprecated(
        "Deprecated, now uses native /dev/input/eventx instead",
    )
    fun onKeyEvent(keyEvent: KeyEvent): Boolean {
        if (_isBubbleExpanded.value)
            return false
        return eventHandler.handleKeyEvent(keyEvent)
    }

    fun onMouseEvent(motionEvent: MotionEvent): Boolean {
        if (isBubbleExpanded.value)
            return false
        when (motionEvent.action) {

            MotionEvent.ACTION_BUTTON_PRESS -> {
                eventHandler.mousePointerPosition = pointerOffset.value
                eventHandler.handleMouseButton(motionEvent.actionButton, true)
            }

            MotionEvent.ACTION_BUTTON_RELEASE -> {
                eventHandler.handleMouseButton(motionEvent.actionButton, false)
            }

            MotionEvent.ACTION_MOVE -> {
                val offset = Offset(
                    motionEvent.rawX,
                    motionEvent.rawY
                ) * sensitivity
                if (!isShootingMode.value) {
                    val position = _mousePointerOffset.value + offset
                    _mousePointerOffset.value = Offset(
                        position.x.coerceIn(0f, displayMetrics.widthPixels.toFloat()),
                        position.y.coerceIn(0f, displayMetrics.heightPixels.toFloat())
                    )
                }
                return eventHandler.handlePointerMove(offset)

            }
        }
        return true
    }

    fun clearActivePointers() {
        eventHandler.clear()
    }

    fun onDestroy() {
        _isBubbleExpanded.value = false
        eventHandler.clear()
    }

    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    private fun getStatusBrHeight(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.currentWindowMetrics.windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        } else {
            val resourceId =
                Resources.getSystem().getIdentifier("status_bar_height", "dimen", "android")
            return if (resourceId > 0) Resources.getSystem()
                .getDimensionPixelSize(resourceId) else 0

        }
    }
}