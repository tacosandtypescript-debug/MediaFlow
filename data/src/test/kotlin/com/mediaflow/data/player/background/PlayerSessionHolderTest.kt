package com.mediaflow.data.player.background

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlayerSessionHolderTest {

    @Test
    fun `get returns same PlayerService instance across multiple calls`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val instance1 = PlayerSessionHolder.get(context)
        val instance2 = PlayerSessionHolder.get(context)

        assertNotNull(instance1)
        assertSame("PlayerSessionHolder must maintain a single persistent instance", instance1, instance2)
    }
}
