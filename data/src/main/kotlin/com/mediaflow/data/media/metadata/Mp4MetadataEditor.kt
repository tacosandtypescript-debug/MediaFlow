package com.mediaflow.data.media.metadata

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure Kotlin parser and writer for ISO Base Media (MP4/M4A) metadata atoms.
 * Safely inserts or updates `moov.udta.meta.ilst` metadata tags and updates
 * chunk offsets (stco/co64) when moov size changes.
 */
object Mp4MetadataEditor {

    private val TYPE_FTYP = byteArrayOf('f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
    private val TYPE_MOOV = byteArrayOf('m'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'v'.code.toByte())
    private val TYPE_MDAT = byteArrayOf('m'.code.toByte(), 'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte())
    private val TYPE_UDTA = byteArrayOf('u'.code.toByte(), 'd'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte())
    private val TYPE_META = byteArrayOf('m'.code.toByte(), 'e'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte())
    private val TYPE_ILST = byteArrayOf('i'.code.toByte(), 'l'.code.toByte(), 's'.code.toByte(), 't'.code.toByte())
    private val TYPE_DATA = byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte())
    private val TYPE_HDLR = byteArrayOf('h'.code.toByte(), 'd'.code.toByte(), 'l'.code.toByte(), 'r'.code.toByte())
    private val TYPE_STCO = byteArrayOf('s'.code.toByte(), 't'.code.toByte(), 'c'.code.toByte(), 'o'.code.toByte())
    private val TYPE_CO64 = byteArrayOf('c'.code.toByte(), 'o'.code.toByte(), '6'.code.toByte(), '4'.code.toByte())

    // Standard iTunes metadata fourcc codes
    private val TAG_TITLE = byteArrayOf(0xa9.toByte(), 'n'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte())
    private val TAG_ARTIST = byteArrayOf(0xa9.toByte(), 'A'.code.toByte(), 'R'.code.toByte(), 'T'.code.toByte())
    private val TAG_ALBUM_ARTIST = byteArrayOf('a'.code.toByte(), 'A'.code.toByte(), 'R'.code.toByte(), 'T'.code.toByte())
    private val TAG_ALBUM = byteArrayOf(0xa9.toByte(), 'a'.code.toByte(), 'l'.code.toByte(), 'b'.code.toByte())
    private val TAG_DATE = byteArrayOf(0xa9.toByte(), 'd'.code.toByte(), 'a'.code.toByte(), 'y'.code.toByte())
    private val TAG_COMMENT = byteArrayOf(0xa9.toByte(), 'c'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte())
    private val TAG_DESC = byteArrayOf('d'.code.toByte(), 'e'.code.toByte(), 's'.code.toByte(), 'c'.code.toByte())

    fun writeMetadata(sourceFile: File, targetFile: File, metadata: MediaMetadata) {
        RandomAccessFile(sourceFile, "r").use { raf ->
            val fileLength = raf.length()
            require(fileLength >= 8) { "Archivo MP4 demasiado pequeño o inválido" }

            val topLevelBoxes = parseTopLevelBoxes(raf, fileLength)
            val moovBoxInfo = topLevelBoxes.firstOrNull { it.type.contentEquals(TYPE_MOOV) }
                ?: throw IllegalArgumentException("No se encontró el átomo moov en el archivo MP4")

            val mdatBoxInfo = topLevelBoxes.firstOrNull { it.type.contentEquals(TYPE_MDAT) }

            // Read existing moov payload
            raf.seek(moovBoxInfo.offset + moovBoxInfo.headerSize)
            val oldMoovPayload = ByteArray((moovBoxInfo.size - moovBoxInfo.headerSize).toInt())
            raf.readFully(oldMoovPayload)

            // Construct new udta box containing metadata
            val udtaBox = buildUdtaBox(metadata)

            // Replace or append udta inside moov payload
            val newMoovPayload = replaceOrAppendUdta(oldMoovPayload, udtaBox)

            val newMoovTotalSize = (8 + newMoovPayload.size).toLong()
            val oldMoovTotalSize = moovBoxInfo.size
            val delta = newMoovTotalSize - oldMoovTotalSize

            // If moov is before mdat, sample chunk offsets in mdat need adjustment by delta
            val moovBeforeMdat = mdatBoxInfo != null && moovBoxInfo.offset < mdatBoxInfo.offset
            if (moovBeforeMdat && delta != 0L) {
                adjustChunkOffsets(newMoovPayload, delta)
            }

            // Write output to targetFile
            RandomAccessFile(targetFile, "rw").use { out ->
                out.setLength(0L)

                for (box in topLevelBoxes) {
                    if (box.type.contentEquals(TYPE_MOOV)) {
                        // Write new moov box
                        out.writeInt(newMoovTotalSize.toInt())
                        out.write(TYPE_MOOV)
                        out.write(newMoovPayload)
                    } else {
                        // Copy existing box as is
                        raf.seek(box.offset)
                        val buffer = ByteArray(65536)
                        var remaining = box.size
                        while (remaining > 0) {
                            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                            raf.readFully(buffer, 0, toRead)
                            out.write(buffer, 0, toRead)
                            remaining -= toRead
                        }
                    }
                }
            }
        }
    }

    private data class BoxInfo(
        val offset: Long,
        val size: Long,
        val headerSize: Int,
        val type: ByteArray,
    )

    private fun parseTopLevelBoxes(raf: RandomAccessFile, fileLength: Long): List<BoxInfo> {
        val boxes = mutableListOf<BoxInfo>()
        var offset = 0L
        while (offset < fileLength - 8) {
            raf.seek(offset)
            val size32 = raf.readInt().toLong() and 0xFFFFFFFFL
            val type = ByteArray(4)
            raf.readFully(type)

            val (boxSize, headerSize) = when (size32) {
                1L -> {
                    val size64 = raf.readLong()
                    Pair(size64, 16)
                }
                0L -> Pair(fileLength - offset, 8)
                else -> Pair(size32, 8)
            }

            if (boxSize < headerSize || offset + boxSize > fileLength) {
                // If box size is invalid, treat remaining file as single box
                boxes.add(BoxInfo(offset, fileLength - offset, headerSize, type))
                break
            }

            boxes.add(BoxInfo(offset, boxSize, headerSize, type))
            offset += boxSize
        }
        return boxes
    }

    private fun replaceOrAppendUdta(moovPayload: ByteArray, newUdtaBox: ByteArray): ByteArray {
        val stream = ByteArrayOutputStream()
        val buffer = ByteBuffer.wrap(moovPayload).order(ByteOrder.BIG_ENDIAN)
        var udtaReplaced = false

        while (buffer.remaining() >= 8) {
            val boxStart = buffer.position()
            val size = buffer.getInt().toLong() and 0xFFFFFFFFL
            val type = ByteArray(4)
            buffer.get(type)

            val boxSize = if (size == 1L) {
                buffer.getLong()
            } else if (size == 0L) {
                (moovPayload.size - boxStart).toLong()
            } else {
                size
            }

            if (type.contentEquals(TYPE_UDTA)) {
                // Replace udta with new udta
                stream.write(newUdtaBox)
                udtaReplaced = true
            } else {
                // Copy original sub-box
                val currentPos = buffer.position()
                val payloadSize = (boxSize - (currentPos - boxStart)).toInt()
                val subBoxBytes = ByteArray((currentPos - boxStart) + payloadSize)
                System.arraycopy(moovPayload, boxStart, subBoxBytes, 0, subBoxBytes.size)
                stream.write(subBoxBytes)
            }

            val nextPos = boxStart + boxSize.toInt()
            if (nextPos > moovPayload.size || nextPos < 0) break
            buffer.position(nextPos)
        }

        if (!udtaReplaced) {
            stream.write(newUdtaBox)
        }

        return stream.toByteArray()
    }

    private fun buildUdtaBox(metadata: MediaMetadata): ByteArray {
        val ilstContent = ByteArrayOutputStream()

        metadata.title?.let { writeTextItem(ilstContent, TAG_TITLE, it) }
        metadata.artist?.let { writeTextItem(ilstContent, TAG_ARTIST, it) }
        metadata.albumArtist?.let { writeTextItem(ilstContent, TAG_ALBUM_ARTIST, it) }
        metadata.album?.let { writeTextItem(ilstContent, TAG_ALBUM, it) }
        metadata.date?.let { writeTextItem(ilstContent, TAG_DATE, it) }
        metadata.description?.let {
            writeTextItem(ilstContent, TAG_COMMENT, it)
            writeTextItem(ilstContent, TAG_DESC, it)
        }

        val ilstBytes = ilstContent.toByteArray()
        val ilstBox = ByteArrayOutputStream()
        ilstBox.writeInt(8 + ilstBytes.size)
        ilstBox.write(TYPE_ILST)
        ilstBox.write(ilstBytes)

        // hdlr box inside meta
        val hdlrBox = ByteArrayOutputStream()
        hdlrBox.writeInt(33) // Size of standard metadata hdlr
        hdlrBox.write(TYPE_HDLR)
        hdlrBox.writeInt(0) // version + flags
        hdlrBox.writeInt(0) // pre-defined
        hdlrBox.write("mdir".toByteArray(Charsets.US_ASCII)) // handler type
        hdlrBox.write("appl".toByteArray(Charsets.US_ASCII)) // mfg
        hdlrBox.writeInt(0) // reserved
        hdlrBox.writeInt(0) // reserved
        hdlrBox.write(0) // string null-terminator

        // meta box (FullBox: size, 'meta', version+flags=0, hdlr, ilst)
        val metaContent = ByteArrayOutputStream()
        metaContent.write(hdlrBox.toByteArray())
        metaContent.write(ilstBox.toByteArray())
        val metaBytes = metaContent.toByteArray()

        val metaBox = ByteArrayOutputStream()
        metaBox.writeInt(8 + 4 + metaBytes.size) // 8 header + 4 version/flags + content
        metaBox.write(TYPE_META)
        metaBox.writeInt(0) // FullBox version and flags
        metaBox.write(metaBytes)

        // udta box
        val udtaBox = ByteArrayOutputStream()
        val metaBoxBytes = metaBox.toByteArray()
        udtaBox.writeInt(8 + metaBoxBytes.size)
        udtaBox.write(TYPE_UDTA)
        udtaBox.write(metaBoxBytes)

        return udtaBox.toByteArray()
    }

    private fun writeTextItem(out: ByteArrayOutputStream, tagFourcc: ByteArray, text: String) {
        val textBytes = text.toByteArray(Charsets.UTF_8)

        // data box: size, 'data', type (1 = utf-8), locale (0), payload
        val dataBox = ByteArrayOutputStream()
        dataBox.writeInt(16 + textBytes.size)
        dataBox.write(TYPE_DATA)
        dataBox.writeInt(1) // type indicator: 1 = UTF-8 text
        dataBox.writeInt(0) // locale
        dataBox.write(textBytes)

        val dataBoxBytes = dataBox.toByteArray()

        // item box
        out.writeInt(8 + dataBoxBytes.size)
        out.write(tagFourcc)
        out.write(dataBoxBytes)
    }

    private fun adjustChunkOffsets(moovPayload: ByteArray, delta: Long) {
        val buffer = ByteBuffer.wrap(moovPayload).order(ByteOrder.BIG_ENDIAN)

        while (buffer.remaining() >= 8) {
            val pos = buffer.position()
            val size = buffer.getInt().toLong() and 0xFFFFFFFFL
            val type = ByteArray(4)
            buffer.get(type)

            if (type.contentEquals(TYPE_STCO)) {
                // stco: 4 bytes version+flags, 4 bytes count, count * 4 bytes offsets
                buffer.getInt() // skip version and flags
                val count = buffer.getInt()
                for (i in 0 until count) {
                    val offsetPos = buffer.position()
                    val oldOffset = buffer.getInt().toLong() and 0xFFFFFFFFL
                    val newOffset = (oldOffset + delta) and 0xFFFFFFFFL
                    buffer.putInt(offsetPos, newOffset.toInt())
                }
            } else if (type.contentEquals(TYPE_CO64)) {
                // co64: 4 bytes version+flags, 4 bytes count, count * 8 bytes offsets
                buffer.getInt() // skip version and flags
                val count = buffer.getInt()
                for (i in 0 until count) {
                    val offsetPos = buffer.position()
                    val oldOffset = buffer.getLong()
                    val newOffset = oldOffset + delta
                    buffer.putLong(offsetPos, newOffset)
                }
            }

            // Continue scanning inner boxes recursively/linearly
            val nextPos = pos + 1
            if (nextPos >= moovPayload.size - 8) break
            buffer.position(nextPos)
        }
    }

    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write((value ushr 24) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }
}
