package com.mediaflow.data.provider.x.recording

import com.mediaflow.core.model.XSpaceState
import com.mediaflow.data.provider.x.dvr.DvrWindowMinutes
import com.mediaflow.data.provider.x.dvr.RollingDvrBuffer
import java.io.File

/**
 * Local Space recorder. LIVE playback pause does not pause recording.
 * Record OFF keeps a temporary DVR window and never emits a library item.
 */
class SpaceRecorder(
    private val workDir: File,
    private val library: RecordedSpaceLibrary,
    window: DvrWindowMinutes = DvrWindowMinutes.FIFTEEN,
    val spaceId: String,
    val originalUrl: String,
) {
    var phase: RecordingPhase = RecordingPhase.OFF
        private set

    var recordEnabled: Boolean = false
        private set

    var elapsedMs: Long = 0L
        private set

    var playbackPaused: Boolean = false

    val dvr = RollingDvrBuffer(window)
    val backoff = ReconnectBackoff()
    val markers = mutableListOf<RecordingMarker>()

    private val checkpointStore = RecordingCheckpointStore(File(workDir, "session"))
    private val committed = mutableListOf<ByteArray>()

    fun setRecordEnabled(enabled: Boolean) {
        if (enabled == recordEnabled) return
        recordEnabled = enabled
        if (enabled && phase == RecordingPhase.OFF) {
            phase = RecordingPhase.STARTING
            phase = RecordingPhase.RECORDING
        }
    }

    fun acceptRecorderTick(payload: ByteArray) {
        dvr.acceptTick(payload)
        if (!recordEnabled || phase == RecordingPhase.OFF || phase == RecordingPhase.SAVED) {
            return
        }
        if (phase == RecordingPhase.STARTING) {
            phase = RecordingPhase.RECORDING
        }
        if (phase != RecordingPhase.RECORDING) return
        elapsedMs += payload.size
        committed.add(payload.copyOf())
        checkpointStore.writeSegment(committed.lastIndex, payload)
        persistCheckpoint(RecordingPhase.RECORDING)
        backoff.reset()
    }

    fun mark(label: String? = null): RecordingMarker {
        val marker = RecordingMarker(relativeTimestampMs = elapsedMs, label = label)
        markers.add(marker)
        if (recordEnabled) persistCheckpoint(phase)
        return marker
    }

    fun onDisconnect(): Long? = backoff.nextDelayMs()

    fun onLiveEnded(spaceState: XSpaceState): RecordedSpace? {
        if (spaceState != XSpaceState.ENDED && spaceState != XSpaceState.TIMED_OUT) return null
        if (!recordEnabled || phase != RecordingPhase.RECORDING) {
            phase = RecordingPhase.OFF
            committed.clear()
            dvr.clear()
            return null
        }
        return finalizeRecording()
    }

    fun recoverPartialThenFinalize(): RecordedSpace? {
        val checkpoint = checkpointStore.loadCheckpoint() ?: return null
        if (checkpoint.phase == RecordingPhase.SAVED) {
            return library.items().firstOrNull { it.spaceId == checkpoint.spaceId }
        }
        val recovered = checkpointStore.recoverBytes()
        if (recovered.isEmpty() && checkpoint.segmentCount == 0) return null
        elapsedMs = checkpoint.elapsedMs
        markers.clear()
        markers.addAll(checkpoint.markers)
        recordEnabled = true
        committed.clear()
        committed.add(recovered)
        return finalizeRecording()
    }

    private fun finalizeRecording(): RecordedSpace {
        phase = RecordingPhase.FINALIZING
        persistCheckpoint(RecordingPhase.FINALIZING)
        val output = File(workDir, "recorded_${spaceId}.bin")
        checkpointStore.finalizeTo(output)
        require(output.isFile && output.length() > 0L) { "empty recording" }
        val item = RecordedSpace(
            spaceId = spaceId,
            originalUrl = originalUrl,
            filePath = output.absolutePath,
            elapsedMs = elapsedMs,
            markers = markers.toList(),
        )
        library.add(item)
        phase = RecordingPhase.SAVED
        persistCheckpoint(RecordingPhase.SAVED)
        return item
    }

    private fun persistCheckpoint(phase: RecordingPhase) {
        checkpointStore.writeCheckpoint(
            RecordingCheckpoint(
                spaceId = spaceId,
                originalUrl = originalUrl,
                phase = phase,
                elapsedMs = elapsedMs,
                segmentCount = committed.size,
                markers = markers.toList(),
            ),
        )
    }
}
