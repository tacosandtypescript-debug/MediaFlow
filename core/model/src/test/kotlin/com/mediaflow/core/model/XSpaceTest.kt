package com.mediaflow.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XSpaceTest {

    @Test
    fun `formats duration correctly`() {
        val host = XParticipant("Test Host", "testhost", "1", role = ParticipantRole.HOST)
        val space1 = XSpace(
            id = "s1",
            url = "https://x.com/i/spaces/s1",
            title = "Sample Space",
            host = host,
            durationSeconds = 5413L, // 1h 30m 13s
        )
        assertEquals("1 h 30 min", space1.formattedDuration)

        val space2 = space1.copy(durationSeconds = 2700L) // 45m
        assertEquals("45 min", space2.formattedDuration)

        val space3 = space1.copy(durationSeconds = 45L) // 45s
        assertEquals("45 seg", space3.formattedDuration)
    }

    @Test
    fun `combines and deduplicates all speakers preserving roles`() {
        val host = XParticipant("Host User", "@host", "u1", role = ParticipantRole.HOST)
        val cohost = XParticipant("Cohost User", "@cohost", "u2", role = ParticipantRole.COHOST)
        val speaker1 = XParticipant("Speaker 1", "@spk1", "u3", role = ParticipantRole.SPEAKER)
        val speakerDuplicate = XParticipant("Host Duplicate", "@HOST", "u1", role = ParticipantRole.SPEAKER)

        val space = XSpace(
            id = "s1",
            url = "https://x.com/i/spaces/s1",
            title = "Space with Speakers",
            host = host,
            cohosts = listOf(cohost),
            speakers = listOf(speaker1, speakerDuplicate),
            participants = listOf(host, cohost, speaker1),
        )

        assertEquals(3, space.allSpeakers.size)
        assertEquals("@host", space.allSpeakers[0].formattedHandle)
        assertEquals(ParticipantRole.HOST, space.allSpeakers[0].role)
        assertEquals("@cohost", space.allSpeakers[1].formattedHandle)
        assertEquals(ParticipantRole.COHOST, space.allSpeakers[1].role)
        assertEquals("@spk1", space.allSpeakers[2].formattedHandle)
        assertEquals(ParticipantRole.SPEAKER, space.allSpeakers[2].role)
    }

    @Test
    fun `parses roles and space states safely from strings`() {
        assertEquals(ParticipantRole.HOST, ParticipantRole.fromString("admin"))
        assertEquals(ParticipantRole.HOST, ParticipantRole.fromString("HOST"))
        assertEquals(ParticipantRole.COHOST, ParticipantRole.fromString("cohost"))
        assertEquals(ParticipantRole.SPEAKER, ParticipantRole.fromString("speaker"))
        assertEquals(ParticipantRole.LISTENER, ParticipantRole.fromString("listener"))
        assertEquals(ParticipantRole.UNKNOWN, ParticipantRole.fromString("invalid"))

        assertEquals(XSpaceState.ENDED, XSpaceState.fromString("Ended"))
        assertEquals(XSpaceState.LIVE, XSpaceState.fromString("running"))
        assertEquals(XSpaceState.UPCOMING, XSpaceState.fromString("notStarted"))
        assertEquals(XSpaceState.TIMED_OUT, XSpaceState.fromString("timedout"))
        assertEquals(XSpaceState.UNKNOWN, XSpaceState.fromString("unknown_val"))
    }
}
