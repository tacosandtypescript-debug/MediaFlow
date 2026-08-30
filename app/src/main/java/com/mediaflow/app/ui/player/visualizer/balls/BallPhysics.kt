package com.mediaflow.app.ui.player.visualizer.balls

import com.mediaflow.app.ui.player.visualizer.audio.AudioReactiveState
import kotlin.math.abs
import kotlin.math.max

/** One simulation step. Deterministic given inputs — no Random. */
object BallPhysics {
    const val Gravity = 980f
    const val Floor = 1f
    const val Damping = 0.992f
    const val Restitution = 0.62f

    fun step(
        balls: List<BallParticle>,
        dt: Float,
        width: Float,
        height: Float,
        signal: AudioReactiveState,
        intensity: Float,
        motion: Float,
    ): List<BallParticle> {
        if (width <= 1f || height <= 1f || dt <= 0f) return balls
        val g = Gravity * (0.55f + 0.45f * motion)
        val impulse = if (!signal.isPlaying) {
            0f
        } else {
            val beatKick = if (signal.beat) 420f * intensity else 0f
            val bassKick = signal.bass * 280f * intensity
            beatKick + bassKick
        }
        return balls.mapIndexed { index, b ->
            var vx = b.vx * Damping
            var vy = b.vy * Damping + g * dt * b.mass
            val scale = 0.65f + (index % 5) * 0.09f
            if (impulse > 0f) {
                vy -= impulse * scale * dt * 18f
                vx += (if (index % 2 == 0) 1f else -1f) * impulse * 0.08f * dt * 12f
            }
            var x = b.x + vx * dt
            var y = b.y + vy * dt
            val r = b.radius
            if (x < r) { x = r; vx = abs(vx) * Restitution }
            if (x > width - r) { x = width - r; vx = -abs(vx) * Restitution }
            if (y < r) { y = r; vy = abs(vy) * Restitution * 0.4f }
            val floor = height * Floor - r
            if (y > floor) {
                y = floor
                vy = -abs(vy) * Restitution
                if (abs(vy) < 30f) vy = 0f
            }
            b.copy(x = x, y = y, vx = vx, vy = vy)
        }
    }

    fun seed(count: Int, width: Float, height: Float): List<BallParticle> {
        val n = count.coerceIn(4, 28)
        return List(n) { i ->
            val t = i / n.toFloat()
            BallParticle(
                x = width * (0.08f + t * 0.84f),
                y = height * (0.72f + (i % 4) * 0.05f),
                vx = (i % 3 - 1) * 40f,
                vy = 0f,
                radius = 10f + (i % 5) * 3.5f,
                colorIndex = i % 5,
                mass = 0.85f + (i % 4) * 0.08f,
            )
        }
    }
}
