package com.mediaflow.app.ui.player.visualizer.balls

import com.mediaflow.app.ui.player.visualizer.audio.AudioReactiveState
import org.junit.Assert.assertTrue
import org.junit.Test

class BallPhysicsTest {
    @Test
    fun gravityDropsThenFloorBouncesAndBeatChangesVelocity() {
        val rest = BallPhysics.seed(6, 400f, 800f)
        val radii = rest.map { it.radius }.toSet()
        assertTrue(radii.size > 1)
        val afterFall = BallPhysics.step(
            rest, 0.05f, 400f, 800f,
            AudioReactiveState(isPlaying = true),
            intensity = 0.5f, motion = 0.5f,
        )
        val beat = AudioReactiveState(bass = 0.9f, beat = true, energy = 0.8f, isPlaying = true)
        val afterBeat = BallPhysics.step(afterFall, 0.05f, 400f, 800f, beat, 1f, 1f)
        val vyChanged = afterBeat.zip(afterFall).any { (a, b) -> a.vy != b.vy }
        assertTrue(vyChanged)
        assertTrue(afterBeat.all { it.y <= 800f && it.y >= it.radius })
    }
}
