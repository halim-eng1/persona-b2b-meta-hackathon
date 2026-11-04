package com.example.persona.features.knowledge.presentation.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Manages live audio recording directly to 16kHz 16-bit mono PCM WAV for the Bot Simulator.
 * Emits recording state, elapsed duration in seconds, and normalized audio amplitude (0f..1f) for waveform animation.
 */
class LiveAudioRecorder(private val context: Context) {

    companion object {
        private const val TAG = "LiveAudioRecorder"
        private const val STALE_RECORDING_MS = 60L * 60L * 1000L
        const val SAMPLE_RATE = 16000
        const val CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
    }

    sealed interface RecordingState {
        object Idle : RecordingState
        data class Recording(val durationSeconds: Int, val amplitude: Float) : RecordingState
        data class Completed(val file: File, val durationSeconds: Int) : RecordingState
        data class Error(val message: String) : RecordingState
    }

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var currentOutputFile: File? = null

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (_state.value is RecordingState.Recording) return false

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            _state.value = RecordingState.Error("Unsupported audio configuration")
            return false
        }

        val bufferSize = maxOf(minBufferSize * 2, 4096)

        return try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                _state.value = RecordingState.Error("AudioRecord initialization failed")
                return false
            }

            audioRecord = record
            pruneStaleRecordings()
            val outputFile = File(context.cacheDir, "sim_live_rec_${System.currentTimeMillis()}.wav")
            currentOutputFile = outputFile

            record.startRecording()

            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ShortArray(bufferSize / 2)
                val fos = FileOutputStream(outputFile)
                // Write placeholder 44-byte WAV header
                fos.write(ByteArray(44))

                var totalAudioBytes = 0
                val startTime = System.currentTimeMillis()
                var lastStateUpdate = 0L

                try {
                    while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val readShorts = record.read(buffer, 0, buffer.size)
                        if (readShorts > 0) {
                            var maxAmp = 0
                            val byteBuffer = ByteBuffer.allocate(readShorts * 2).order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until readShorts) {
                                val s = buffer[i]
                                byteBuffer.putShort(s)
                                val abs = kotlin.math.abs(s.toInt())
                                if (abs > maxAmp) maxAmp = abs
                            }
                            val pcmBytes = byteBuffer.array()
                            fos.write(pcmBytes)
                            totalAudioBytes += pcmBytes.size

                            val now = System.currentTimeMillis()
                            if (now - lastStateUpdate >= 100) {
                                lastStateUpdate = now
                                val durationSec = ((now - startTime) / 1000).toInt()
                                val normalizedAmp = (maxAmp / 32767.0f).coerceIn(0f, 1f)
                                _state.value = RecordingState.Recording(durationSec, normalizedAmp)
                            }
                        }
                    }
                } finally {
                    runCatching { fos.flush() }
                    runCatching { fos.close() }
                    // Update WAV header with accurate sizes
                    if (outputFile.exists() && totalAudioBytes > 0) {
                        writeWavHeader(outputFile, totalAudioBytes, SAMPLE_RATE, CHANNELS, BITS_PER_SAMPLE)
                    }
                }
            }

            _state.value = RecordingState.Recording(0, 0f)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            _state.value = RecordingState.Error(e.message ?: "Failed to start recording")
            false
        }
    }

    fun stopRecording(): File? {
        val file = currentOutputFile
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            recordingJob?.cancel()
            recordingJob = null

            val currentState = _state.value
            val duration = (currentState as? RecordingState.Recording)?.durationSeconds ?: 0

            if (file != null && file.exists() && file.length() > 44L) {
                _state.value = RecordingState.Completed(file, duration)
                return file
            } else {
                _state.value = RecordingState.Idle
                file?.delete()
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}", e)
            _state.value = RecordingState.Error(e.message ?: "Error stopping recording")
            file?.delete()
            return null
        }
    }

    fun cancelRecording() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            recordingJob?.cancel()
            recordingJob = null

            currentOutputFile?.let {
                if (it.exists()) it.delete()
            }
            currentOutputFile = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling recording: ${e.message}", e)
        } finally {
            _state.value = RecordingState.Idle
        }
    }

    fun reset() {
        cancelRecording()
    }

    /**
     * Removes recordings older than [STALE_RECORDING_MS] so the cache directory does not grow
     * without bound across simulator sessions. The most recent clip is always preserved so the
     * user can still replay the previous turn.
     */
    private fun pruneStaleRecordings() {
        runCatching {
            val cutoff = System.currentTimeMillis() - STALE_RECORDING_MS
            context.cacheDir.listFiles { f -> f.name.startsWith("sim_live_rec_") }
                ?.filter { it.lastModified() < cutoff }
                ?.forEach { it.delete() }
        }
    }

    private fun writeWavHeader(
        file: File,
        totalAudioLen: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        val header = ByteArray(44)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        buf.put("RIFF".toByteArray())
        buf.putInt(totalDataLen)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16) // Subchunk1Size for PCM
        buf.putShort(1) // AudioFormat (1 = PCM)
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort(blockAlign.toShort())
        buf.putShort(bitsPerSample.toShort())
        buf.put("data".toByteArray())
        buf.putInt(totalAudioLen)

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header)
        }
    }
}
