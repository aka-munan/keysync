package com.devoid.keysync.domain

import android.view.InputEvent
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import com.devoid.keysync.domain.animators.CoroutineValueAnimator
import com.devoid.keysync.domain.animators.ObjectRandomAnimator
import com.devoid.keysync.model.EventInjector
import com.devoid.keysync.model.EventManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class EventInjectorWithRandImpl(private val eventManager: EventManager = EventManagerImpl()) :
    EventInjector {
    private val animators = mutableMapOf<Int, ObjectRandomAnimator>()
    private val animatorJobs = mutableMapOf<Int, Job>()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val taskQueue = Channel<suspend () -> Unit>()
    abstract fun inject(event: InputEvent)

    init {
        scope.launch {
            for (task in taskQueue) {
                task.invoke()
            }
        }
    }

    override fun injectGesture(pointerID: Int, o1: Offset, o2: Offset) {

//        animators[pointerID]?.let {
//            it.cancel()
//            animators.remove(pointerID)
//        }
        animatorJobs[pointerID]?.let {
            it.cancel()
            animatorJobs.remove(pointerID)
        }
//        val animator = ObjectRandomAnimator()
        val animatorJob = scope.launch {
            CoroutineValueAnimator.animateOffsetFrame(
                o1, o2, durationMs = 80,
                onStart = {
                    if (!injected(pointerID)) {
                        addEventDown(pointerID, o1.x, o1.y)
                    }
                }, onUpdate = {offset->
                    taskQueue.send {
                        if (!isActive)
                            return@send
                        eventManager.updatePointer(pointerID, offset.x, offset.y)
                        val event = eventManager.createMotionEvent(MotionEvent.ACTION_MOVE)
                        event?.let {
                            inject(it)
                            it.recycle()
                        }
                    }
                },
                onEnd = {
                    animators.remove(pointerID)
                })
        }
//        animator.init(o1, o2, duration = 80)
//        animator.addUpdateListener { animation ->
//            val offset = animation.animatedValue as Offset
//            scope.launch {
//                taskQueue.send {
//                    if (!animator.isRunning)
//                        return@send
//                    eventManager.updatePointer(pointerID, offset.x, offset.y)
//                    val event = eventManager.createMotionEvent(MotionEvent.ACTION_MOVE)
//                    event?.let {
//                        inject(it)
//                        it.recycle()
//                    }
//                }
//            }
//        }
//        animator.doOnEnd {
//            animators.remove(pointerID)
//        }
//        animator.doOnStart {//inject action down once at $o1 if not injected
//            scope.launch {
//                if (!injected(pointerID)) {
//                    addEventDown(pointerID, o1.x, o1.y)
//                }
//            }
//        }
//        animator.start()
//        animators[pointerID] = animator
        animatorJobs[pointerID] = animatorJob
    }

    override fun transFormGesture(pointerID: Int, position: Offset) {
        scope.launch {
            taskQueue.send {
                eventManager.getPointerLocation(pointerID)?.let {
                    withContext(Dispatchers.Main) {
                        injectGesture(pointerID, it, position)
                    }
                }
            }
        }
    }

    override fun releaseGesture(pointerID: Int) {
        animators[pointerID]?.let {
            it.cancel()
            animators.remove(pointerID)
        }
        releasePointer(pointerID)
    }

    override fun injectPointer(pointerID: Int, o1: Offset, o2: Offset) {
        val x = (o1.x.toInt()..o2.x.toInt()).random().toFloat()
        val y = (o1.y.toInt()..o2.y.toInt()).random().toFloat()
        addEventDown(pointerID, x, y)
    }

    override fun injectPointer(pointerID: Int, position: Offset) {
        addEventDown(pointerID, position.x, position.y)
    }

    override fun updatePointerPosition(pointerID: Int, position: Offset) {
        scope.launch {
            taskQueue.send {
                eventManager.offsetPointer(pointerID, position)
                val event = eventManager.createMotionEvent(MotionEvent.ACTION_MOVE)
                event?.let {
                    inject(it)
                    it.recycle()
                }
            }
        }
    }


    private fun addEventDown(pointerID: Int, x: Float, y: Float) {
        scope.launch {
            taskQueue.send {
                eventManager.addPointer(pointerID, x, y)
                val action = eventManager.getPointerAction(pointerID, false)
                val event = eventManager.createMotionEvent(action)
                event?.let {
                    inject(it)
                    it.recycle()
                }
            }
        }
    }

    override fun releasePointer(pointerID: Int) {
        scope.launch {
            taskQueue.send {
                val action = eventManager.getPointerAction(pointerID, true)
                if (action == -1)
                    return@send
                val event = eventManager.createMotionEvent(action)
                event?.let {
                    inject(it)
                    it.recycle()
                }
                eventManager.removePointer(pointerID)
            }
        }
    }

    override fun clear() {
        scope.launch {
            taskQueue.send {
                val event = eventManager.createMotionEvent(MotionEvent.ACTION_CANCEL)
                event?.let {
                    inject(it)
                    it.recycle()
                }
                eventManager.clear()
            }
        }
    }

    override suspend fun injected(pointerID: Int): Boolean {
        return eventManager.getPointerAction(pointerID, false) != -1
    }

}