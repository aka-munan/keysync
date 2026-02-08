package com.devoid.keysync.domain.animators

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.math.atan

object CoroutineValueAnimator{
    suspend inline fun animateOffsetFrame(
        from: Offset,
        to: Offset,
        durationMs: Long=150,
        randomness: Int=3,
        onStart:suspend () -> Unit,
        onEnd:suspend () -> Unit,
        onUpdate:suspend (Offset) -> Unit,
    ) {
        val atanX = atan(to.x - from.x)
        val atanY = atan(to.y - from.y)

        val evaluator = OffsetRandomEvaluator(randomness, atanX, atanY)

        val durationNs = durationMs * 1_000_000L
        val start = System.nanoTime()
        onStart()
        while (true) {
            val now = System.nanoTime()
            val fraction =
                ((now - start).toFloat() / durationNs).coerceIn(0f, 1f)

            onUpdate(evaluator.evaluate(fraction, from, to))
            if (fraction >= 1f) break
            delay(16)
        }
        onEnd()
    }

}

class CoroutineOffsetRandomEvaluator(
    private val limit: Int,
    private val atanX: Float,
    private val atanY: Float
) {
    fun evaluate(fraction: Float, p1: Offset, p2: Offset): Offset {
        val random = (-limit..limit).random()

        val x = p1.x + ((p2.x - p1.x) * fraction) - random * atanY
        val y = p1.y + ((p2.y - p1.y) * fraction) + random * atanX

        return Offset(x, y)
    }
}