package com.iflytek.aikitdemo.media.audio

/**
 * @Desc:
 * @Author leon
 * @Date 2023/3/8-15:53
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */

class AudioRecordException(detailMessage: String) : Exception(detailMessage) {

    companion object {
        private const val serialVersionUID = -1494092412387923456L
    }
}

interface RecorderCallback {
    fun onStartRecord()
    fun onPauseRecord()
    fun onResumeRecord()
    fun onRecordProgress(data: ByteArray, sampleSize: Int, volume: Int)
    fun onStopRecord(output: java.io.File?)
}

interface Recorder {

    fun setRecorderCallback(callback: RecorderCallback?)
    fun startRecording()
    fun resumeRecording()
    fun pauseRecording()
    fun stopRecording()
    fun isRecording(): Boolean
    fun isPaused(): Boolean
}