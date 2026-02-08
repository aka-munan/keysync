package com.devoid.keysync.domain.animators

import android.animation.TypeEvaluator
import android.animation.ValueAnimator
import androidx.compose.ui.geometry.Offset
import kotlin.math.atan

class ObjectRandomAnimator : ValueAnimator() {
    fun init(p1: Offset, p2: Offset, randomness:Int=3, duration: Long=150) {
        val atanX = atan(p2.x-p1.x)
        val atanY= atan(p2.y - p1.y)
        val evaluator= OffsetRandomEvaluator(randomness,atanX,atanY)
        setObjectValues(p1,p2)
        setEvaluator(evaluator)
        setDuration(duration)
    }
}
class OffsetRandomEvaluator(private val limit:Int,private val atanX:Float,private val atanY: Float) : TypeEvaluator<Offset>{
    override fun evaluate(fraction: Float, p1: Offset?, p2: Offset?): Offset {
        requireNotNull(p1)
        requireNotNull(p2)
        val random = (-limit..limit).random()
        val x = p1.x+((p2.x-p1.x)*fraction)-random*(atanY)
        val y = p1.y+((p2.y-p1.y)*fraction)+random*(atanX)
        return Offset(x,y)
    }
}