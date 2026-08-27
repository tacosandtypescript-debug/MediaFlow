package com.mediaflow.data.player.background

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WakeLockManagerTest {

    @Test
    fun `acquireLocks and releaseLocks execute cleanly`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val manager = WakeLockManager(context)

        manager.acquireLocks(isLive = true)
        manager.releaseLocks()
        // Double release should also be completely safe
        manager.releaseLocks()
    }
}
