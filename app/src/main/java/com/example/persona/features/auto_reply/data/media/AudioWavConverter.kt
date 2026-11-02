package com.example.persona.features.auto_reply.data.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Robust audio conversion utility that decodes various audio formats (including WhatsApp .ogg / Opus)
 * into a standard 16kHz mono 16-bit PCM WAV file required by speech-to-text endpoints like Meta's
 * `muse-voice-transcribe-1.0`.
 */
object AudioWavConverter {

    private const val TAG = "AudioWavConverter"
    const val TARGET_SAMPLE_RATE = 16000
    const val TARGET_CHANNELS = 1

    /**
     * Converts [inputFile] into a 16kHz mono 16-bit PCM WAV file.
     * If the file is already a valid WAV, returns [inputFile] directly.
     */
    fun convertTo16kHzMonoWav(
        inputFile: File,
        cacheDir: File? = null,
        targetSampleRate: Int = TARGET_SAMPLE_RATE
    ): File {
        if (!inputFile.exists() || inputFile.length() == 0L) {
            return inputFile
        }

        // Fast-path: if already .wav and starts with RIFF, return directly
        if (inputFile.extension.equals("wav", ignoreCase = true)) {
            val isRiff = runCatching {
                FileInputStream(inputFile).use { fis ->
                    val header = ByteArray(4)
                    fis.read(header) == 4 && String(header) == "RIFF"
                }
            }.getOrDefault(false)
            if (isRiff) return inputFile
        }

        val targetDir = cacheDir ?: inputFile.parentFile ?: File(".")
        val outputFile = File(targetDir, "transcoded_${System.currentTimeMillis()}_${System.nanoTime()}.wav")

        return try {
            decodeToWav(inputFile, outputFile, targetSampleRate, TARGET_CHANNELS)
            if (outputFile.exists() && outputFile.length() > 44L) {
                Log.i(TAG, "Successfully converted ${inputFile.name} (${inputFile.length()}B) to ${targetSampleRate}Hz WAV (${outputFile.length()}B)")
                outputFile
            } else {
                Log.w(TAG, "Decoded WAV file was empty or missing. Falling back to input file.")
                inputFile
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Audio decoding failed: ${e.message}. Falling back to input file.")
            if (outputFile.exists()) outputFile.delete()
            inputFile
        }
    }

    private fun decodeToWav(
        inputFile: File,
        outputFile: File,
        targetSampleRate: Int,
        targetChannels: Int
    ) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var fos: FileOutputStream? = null

        try {
            extractor.setDataSource(inputFile.absolutePath)
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || inputFormat == null) {
                throw IllegalStateException("No audio track found in file: ${inputFile.name}")
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: "audio/opus"
            var actualSampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 16000
            var actualChannelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 1

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            fos = FileOutputStream(outputFile)
            // Placeholder 44-byte WAV header
            val headerPlaceholder = ByteArray(44)
            fos.write(headerPlaceholder)

            val bufferInfo = MediaCodec.BufferInfo()
            var isInputEos = false
            var isOutputEos = false
            var totalPcmBytes = 0L

            val kTimeoutUs = 5000L

            while (!isOutputEos) {
                if (!isInputEos) {
                    val inIndex = codec.dequeueInputBuffer(kTimeoutUs)
                    if (inIndex >= 0) {
                        val inBuffer = codec.getInputBuffer(inIndex)
                        if (inBuffer != null) {
                            val sampleSize = extractor.readSampleData(inBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isInputEos = true
                            } else {
                                val sampleTime = extractor.sampleTime
                                codec.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, kTimeoutUs)
                when (outIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFormat = codec.outputFormat
                        if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            actualSampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            actualChannelCount = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        Log.d(TAG, "Decoder output format changed: sampleRate=$actualSampleRate, channels=$actualChannelCount")
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> { /* no-op */ }
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> { /* no-op (deprecated) */ }
                    else -> if (outIndex >= 0) {
                        val outBuffer = codec.getOutputBuffer(outIndex)
                        if (outBuffer != null && bufferInfo.size > 0) {
                            outBuffer.position(bufferInfo.offset)
                            outBuffer.limit(bufferInfo.offset + bufferInfo.size)

                            val chunk = ByteArray(bufferInfo.size)
                            outBuffer.get(chunk)

                            // Resample and downmix PCM using dynamic decoder output parameters
                            val processedChunk = resampleAndDownmixPcm(
                                inputPcm = chunk,
                                srcSampleRate = actualSampleRate,
                                srcChannels = actualChannelCount,
                                dstSampleRate = targetSampleRate,
                                dstChannels = targetChannels
                            )

                            fos.write(processedChunk)
                            totalPcmBytes += processedChunk.size
                        }

                        codec.releaseOutputBuffer(outIndex, false)

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isOutputEos = true
                        }
                    }
                }
            }

            fos.flush()
            fos.close()
            fos = null

            // Write real WAV header at beginning of file
            writeWavHeader(outputFile, totalPcmBytes, targetSampleRate, targetChannels)

        } finally {
            runCatching { fos?.close() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun resampleAndDownmixPcm(
        inputPcm: ByteArray,
        srcSampleRate: Int,
        srcChannels: Int,
        dstSampleRate: Int,
        dstChannels: Int
    ): ByteArray {
        if (srcSampleRate == dstSampleRate && srcChannels == dstChannels) {
            return inputPcm
        }

        val shortBuffer = ByteBuffer.wrap(inputPcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val numSamples = shortBuffer.remaining()
        val numFrames = numSamples / srcChannels

        // Step 1: Downmix to mono if srcChannels > 1
        val monoPcm = ShortArray(numFrames)
        for (i in 0 until numFrames) {
            var sum = 0
            for (ch in 0 until srcChannels) {
                sum += shortBuffer.get(i * srcChannels + ch)
            }
            monoPcm[i] = (sum / srcChannels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        // Step 2: Linear resampling if srcSampleRate != dstSampleRate
        val resampled: ShortArray
        if (srcSampleRate != dstSampleRate && srcSampleRate > 0) {
            val ratio = dstSampleRate.toDouble() / srcSampleRate.toDouble()
            val outFrames = (numFrames * ratio).toInt()
            resampled = ShortArray(outFrames)
            for (i in 0 until outFrames) {
                val srcIdx = i / ratio
                val idx0 = srcIdx.toInt().coerceIn(0, numFrames - 1)
                val idx1 = (idx0 + 1).coerceIn(0, numFrames - 1)
                val frac = srcIdx - idx0
                val s0 = monoPcm[idx0]
                val s1 = monoPcm[idx1]
                resampled[i] = (s0 + frac * (s1 - s0)).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        } else {
            resampled = monoPcm
        }

        val resultBytes = ByteArray(resampled.size * 2)
        val outBb = ByteBuffer.wrap(resultBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (s in resampled) {
            outBb.putShort(s)
        }
        return resultBytes
    }

    private fun writeWavHeader(
        file: File,
        pcmDataLength: Long,
        sampleRate: Int,
        channels: Int
    ) {
        val totalDataLen = pcmDataLength + 36
        val byteRate = (sampleRate * channels * 16 / 8).toLong()

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            val header = ByteArray(44)
            val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

            // Chunk ID: "RIFF"
            bb.put('R'.code.toByte())
            bb.put('I'.code.toByte())
            bb.put('F'.code.toByte())
            bb.put('F'.code.toByte())

            // Chunk Size
            bb.putInt(totalDataLen.toInt())

            // Format: "WAVE"
            bb.put('W'.code.toByte())
            bb.put('A'.code.toByte())
            bb.put('V'.code.toByte())
            bb.put('E'.code.toByte())

            // Subchunk 1 ID: "fmt "
            bb.put('f'.code.toByte())
            bb.put('m'.code.toByte())
            bb.put('t'.code.toByte())
            bb.put(' '.code.toByte())

            // Subchunk 1 Size: 16 for PCM
            bb.putInt(16)

            // Audio Format: 1 for PCM
            bb.putShort(1.toShort())

            // Num Channels
            bb.putShort(channels.toShort())

            // Sample Rate
            bb.putInt(sampleRate)

            // Byte Rate
            bb.putInt(byteRate.toInt())

            // Block Align: channels * bitsPerSample / 8
            bb.putShort((channels * 16 / 8).toShort())

            // Bits Per Sample: 16
            bb.putShort(16.toShort())

            // Subchunk 2 ID: "data"
            bb.put('d'.code.toByte())
            bb.put('a'.code.toByte())
            bb.put('t'.code.toByte())
            bb.put('a'.code.toByte())

            // Subchunk 2 Size
            bb.putInt(pcmDataLength.toInt())

            raf.write(header)
        }
    }
}

