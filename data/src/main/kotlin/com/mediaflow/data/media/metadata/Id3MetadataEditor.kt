package com.mediaflow.data.media.metadata

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Pure Kotlin parser and writer for ID3v2.3 metadata in MP3 audio files.
 */
object Id3MetadataEditor {

    fun writeMetadata(sourceFile: File, targetFile: File, metadata: MediaMetadata) {
        val id3Bytes = buildId3v2Tag(metadata)

        FileInputStream(sourceFile).use { input ->
            FileOutputStream(targetFile).use { output ->
                // Write new ID3v2 header and frames
                output.write(id3Bytes)

                // Check if source file has an existing ID3v2 header to skip
                val header = ByteArray(10)
                val read = input.read(header)
                if (read == 10 && header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                    val size = ((header[6].toInt() and 0x7F) shl 21) or
                        ((header[7].toInt() and 0x7F) shl 14) or
                        ((header[8].toInt() and 0x7F) shl 7) or
                        (header[9].toInt() and 0x7F)
                    // Skip existing ID3 tag payload
                    var toSkip = size.toLong()
                    while (toSkip > 0) {
                        val skipped = input.skip(toSkip)
                        if (skipped <= 0) break
                        toSkip -= skipped
                    }
                } else if (read > 0) {
                    // Not ID3, write the read header bytes back
                    output.write(header, 0, read)
                }

                // Copy remaining audio stream bytes
                val buffer = ByteArray(65536)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
        }
    }

    private fun buildId3v2Tag(metadata: MediaMetadata): ByteArray {
        val framesStream = ByteArrayOutputStream()

        metadata.title?.let { writeTextFrame(framesStream, "TIT2", it) }
        metadata.artist?.let { writeTextFrame(framesStream, "TPE1", it) }
        metadata.albumArtist?.let { writeTextFrame(framesStream, "TPE2", it) }
        metadata.album?.let { writeTextFrame(framesStream, "TALB", it) }
        metadata.date?.let { writeTextFrame(framesStream, "TYER", it.take(4)) }
        metadata.description?.let { writeCommentFrame(framesStream, it) }

        val framesBytes = framesStream.toByteArray()
        val tagSize = framesBytes.size

        val id3Stream = ByteArrayOutputStream()
        // ID3 Header (10 bytes)
        id3Stream.write("ID3".toByteArray(Charsets.US_ASCII))
        id3Stream.write(3) // ID3v2.3
        id3Stream.write(0) // revision
        id3Stream.write(0) // flags
        // Syncsafe integer for tag size (excluding 10-byte header)
        id3Stream.write((tagSize shr 21) and 0x7F)
        id3Stream.write((tagSize shr 14) and 0x7F)
        id3Stream.write((tagSize shr 7) and 0x7F)
        id3Stream.write(tagSize and 0x7F)

        id3Stream.write(framesBytes)
        return id3Stream.toByteArray()
    }

    private fun writeTextFrame(out: ByteArrayOutputStream, frameId: String, text: String) {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val framePayload = ByteArrayOutputStream()
        framePayload.write(3) // 3 = UTF-8 encoding
        framePayload.write(textBytes)
        val payloadBytes = framePayload.toByteArray()

        out.write(frameId.toByteArray(Charsets.US_ASCII))
        out.writeInt(payloadBytes.size)
        out.write(0) // flags byte 1
        out.write(0) // flags byte 2
        out.write(payloadBytes)
    }

    private fun writeCommentFrame(out: ByteArrayOutputStream, comment: String) {
        val textBytes = comment.toByteArray(Charsets.UTF_8)
        val framePayload = ByteArrayOutputStream()
        framePayload.write(3) // 3 = UTF-8 encoding
        framePayload.write("eng".toByteArray(Charsets.US_ASCII))
        framePayload.write(0) // short description terminator
        framePayload.write(textBytes)
        val payloadBytes = framePayload.toByteArray()

        out.write("COMM".toByteArray(Charsets.US_ASCII))
        out.writeInt(payloadBytes.size)
        out.write(0)
        out.write(0)
        out.write(payloadBytes)
    }

    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write((value ushr 24) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }
}
