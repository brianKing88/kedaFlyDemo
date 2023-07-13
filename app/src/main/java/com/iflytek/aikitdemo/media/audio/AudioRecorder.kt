package com.iflytek.aikitdemo.media.audio

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import com.iflytek.aikitdemo.MyApp
import com.iflytek.aikitdemo.tool.mainThread
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * @Desc: 简单的音频播放器
 * @Author leon
 * @Date 2023/3/8-15:49
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */
class AudioRecorder private constructor() : Recorder {

    private val TAG = "AudioRecorder"

    private var audioRecord: AudioRecord? = null

    private var recordFile: File? = null
    private var recordingThread: Thread? = null
    private var bufferSize = 0
    private var recordCallback: RecorderCallback? = null

    private val isRecording = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    companion object {

        private const val SAMPLE_RATE_IN_HZ = 16000
        private const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC
        private const val CHANNEL_CONFIGURATION = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        @JvmStatic
        val instance by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AudioRecorder() }
    }

    @SuppressLint("MissingPermission")
    fun init() {
        if (null != audioRecord) {
            audioRecord?.release()
        }
        try {
            bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_IN_HZ, CHANNEL_CONFIGURATION, AUDIO_FORMAT
            )
            audioRecord = AudioRecord(
                AUDIO_SOURCE, SAMPLE_RATE_IN_HZ,
                CHANNEL_CONFIGURATION, AUDIO_FORMAT, bufferSize
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw AudioRecordException("初始化录音失败")
        }
    }

    override fun setRecorderCallback(callback: RecorderCallback?) {
        recordCallback = callback
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun startRecording() {
        when (audioRecord?.state) {
            AudioRecord.STATE_INITIALIZED -> {
                try {
                    audioRecord?.startRecording()
                } catch (e: Exception) {
                    throw AudioRecordException("录音失败")
                }
            }
            AudioRecord.STATE_UNINITIALIZED -> {
                init()
                audioRecord?.startRecording()
            }
            else -> {
                throw AudioRecordException("录音失败")
            }
        }
        isRecording.set(true)
        isPaused.set(false)
        recordFile = File(
            MyApp.CONTEXT.externalCacheDir?.absolutePath ?: "",
            "${System.currentTimeMillis()}.pcm"
        )
        recordingThread = Thread(RecordThread(), "RecordThread")
        try {
            recordingThread?.start()
            mainThread {
                recordCallback?.onStartRecord()
            }
        } catch (e: Exception) {
            throw AudioRecordException("录音失败")
        }
    }


    override fun resumeRecording() {
        if (audioRecord != null && audioRecord!!.state == AudioRecord.STATE_INITIALIZED) {
            if (isPaused.get()) {
                audioRecord?.startRecording()
                mainThread {
                    recordCallback?.onResumeRecord()
                }
                isPaused.set(false)
            }
        }
    }

    override fun pauseRecording() {
        if (audioRecord != null && isRecording.get()) {
            audioRecord?.stop()
            isPaused.set(true)
            mainThread {
                recordCallback?.onPauseRecord()
            }
        }
    }

    override fun stopRecording() {
        if (audioRecord != null) {
            isRecording.set(false)
            isPaused.set(false)
            if (audioRecord!!.state == AudioRecord.STATE_INITIALIZED) {
                try {
                    audioRecord?.stop()
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "stopRecording() problems", e)
                }
            }
            audioRecord?.release()
            recordingThread?.interrupt()
            mainThread {
                recordCallback?.onStopRecord(recordFile)
            }
        }
    }

    override fun isRecording(): Boolean {
        return isRecording.get()
    }

    override fun isPaused(): Boolean {
        return isPaused.get()
    }

    inner class RecordThread : Runnable {

        override fun run() {
            val fos: FileOutputStream? = try {
                FileOutputStream(recordFile)
            } catch (e: FileNotFoundException) {
                Log.e(TAG, "", e)
                null
            }
            val scoringBufferMaxSize = bufferSize
            val audioData = ByteArray(scoringBufferMaxSize)
            while (isRecording()) {
                val localPaused = isPaused()
                if (localPaused) {
                    continue
                }
                val audioSampleSize = getAudioRecordBuffer(
                    scoringBufferMaxSize, audioData
                )
                if (audioSampleSize > 0) {
                    val x =
                        abs(audioData[0].toInt()).toFloat() / Short.MAX_VALUE
                    val recordVolume = ((2 * x - x * x) * 9).roundToInt()
                    if (audioSampleSize == scoringBufferMaxSize) {
                        mainThread {
                            recordCallback?.onRecordProgress(audioData, audioSampleSize, recordVolume)
                        }
                        writeToFile(fos, audioData)
                    } else {
                        val copy = ByteArray(audioSampleSize)
                        System.arraycopy(audioData, 0, copy, 0, audioSampleSize)
                        mainThread {
                            recordCallback?.onRecordProgress(copy, audioSampleSize, recordVolume)
                        }
                        writeToFile(fos, copy)
                    }
                }
            }
            try {
                fos?.flush()
                fos?.close()
            } catch (e: IOException) {
                Log.e(TAG, "", e)
            }
        }
    }

    private fun writeToFile(fos: FileOutputStream?, data: ByteArray) {
        if (fos == null) return
        try {
            fos.write(data)
        } catch (e: IOException) {
            Log.e(TAG, "", e)
        }
    }

    private fun getAudioRecordBuffer(
        scoringBufferMaxSize: Int,
        audioSamples: ByteArray
    ): Int {
        return audioRecord?.read(
            audioSamples,
            0,
            scoringBufferMaxSize
        ) ?: 0
    }

}