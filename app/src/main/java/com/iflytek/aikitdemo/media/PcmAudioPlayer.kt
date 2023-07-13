package com.iflytek.aikitdemo.media

import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.AudioTrack
import android.os.Build
import android.os.MemoryFile
import android.util.Log
import com.iflytek.aikit.core.media.player.PcmPlayer
import com.iflytek.aikitdemo.tool.mainThread
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

/**
 * @Desc: 简单的PCM播放器
 * @Author leon
 * @Date 2023/2/23-17:05
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */
class PcmAudioPlayer @JvmOverloads constructor(val context: Context) {

    companion object {
        private const val TAG = "PcmAudioPlayer"

        /**
         * 采样率
         * 采样率为44100，目前为常用的采样率，官方文档表示这个值可以兼容所有的设置
         */
        private const val AudioPlayerSampleRate = 16000

        /**
         * 编码格式
         * 通常我们选择[AudioFormat.ENCODING_PCM_16BIT]和[AudioFormat.ENCODING_PCM_8BIT]
         * PCM代表的是脉冲编码调制，它实际上是原始音频样本。
         * 因此可以设置每个样本的分辨率为16位或者8位，16位将占用更多的空间和处理能力,表示的音频也更加接近真实。
         */
        private const val AudioPlayerFormat = AudioFormat.ENCODING_PCM_16BIT

        /**
         * 指定捕获音频的声道数目
         * 使用单声道
         */
        private const val AudioPlayerChannelConfig = AudioFormat.CHANNEL_OUT_MONO

        /**
         * 音频流类型
         * STREAM是由用户通过write方式把数据一次一次写到 [AudioTrack]中
         * 例如通过编解码得到PCM数据，然后write到 [AudioTrack]。
         */
        private const val AudioPlayerMode = AudioTrack.MODE_STREAM

        private const val BUFFER_CAPITAL = 10

        private const val INIT = 0x100
        private const val BUFFING = 0x101
        private const val PLAYING = 0x102
        private const val PAUSED = 0x103
        private const val STOP = 0x104

        private const val MIN_SLEEP = 5L
    }

    /**
     * audioTrack状态
     */
    private var playState = INIT


    private val syncObj = this

    /**
     * 指定缓冲区大小
     * 具体通过[AudioTrack.getMinBufferSize]获得
     */
    private var minBufferSize: Int = 0

    private var bufferCount: Long = 0L

    private var audioTrack: AudioTrack? = null

    private var memFile: MemoryFile? = null

    private var mEndLock = ReentrantLock()
    private var mEndCondition = mEndLock.newCondition()

    private val isExit = AtomicBoolean(true)

    private var previousAudioMode: Int? = null

    private var audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * 是否会话过程中暂停后台音乐播放
     */
    private val requestFocus = true

    private var previousMicrophoneMute: Boolean = false

    private var audioFocusRequest: AudioFocusRequest? = null

    private var previousVolumeControlStream: Int = 0

    /**
     * 用来区分音频焦点是被抢占还是手动
     */
    private var changeListenerFlag = false

    /**
     * 音频播放回调
     */
    private var pcmAudioPlayerListener: PcmAudioPlayer.PcmPlayerListener? = null

    @Volatile
    private var readOffset = 0

    @Volatile
    private var palyPercent: Long = 0L

    @Volatile
    private var totalSize: Long = 0L
    private var bufOffset = 0
    private var bufLength = 0

    private var pcmThread: PcmThread? = null

    init {
        initAudioTrack()
    }

    /**
     * 初始化AudioTrack
     */
    private fun initAudioTrack() {
        //根据采样率，采样精度，单双声道来得到frame的大小
        minBufferSize = AudioTrack.getMinBufferSize(
            AudioPlayerSampleRate,
            AudioPlayerChannelConfig,
            AudioPlayerFormat
        )
        Log.i(TAG, "minBufferSize===> ${minBufferSize * BUFFER_CAPITAL}")
        // 初始化AudioTrack播放器
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(AudioPlayerSampleRate)
                        .setChannelMask(AudioPlayerChannelConfig)
                        .setEncoding(AudioPlayerFormat)
                        .build()
                )
                .setTransferMode(AudioPlayerMode)
                .setBufferSizeInBytes(minBufferSize * BUFFER_CAPITAL)
                .build();
        } else {
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                AudioPlayerSampleRate,
                AudioPlayerChannelConfig,
                AudioPlayerFormat,
                minBufferSize * BUFFER_CAPITAL, //计算最小缓冲区 *10
                AudioPlayerMode
            )
        }
    }

    /**
     * 预加载 [AudioTrack]
     */
    fun prepareAudio(start: () -> Unit) {
        bufferCount = 0
        Log.i(TAG, "prepareAudio: ========>  ")
        if (audioTrack == null) return
        if (audioTrack?.state == AudioTrack.STATE_UNINITIALIZED) {
            initAudioTrack()
        }
        audioTrack?.play()
        Log.i(TAG, "========start playing audio========")
        start.invoke()
    }

    fun resume(): Boolean {
        Log.w(TAG, "player resume..")
        val ret = setState(
            PAUSED,
            PLAYING
        )
        kotlin.runCatching {
            if (ret) {
                Log.i(TAG, "resume start fade in")
                //TODO 再次播放回调
            }
            audioTrack?.play()
        }.onFailure { it.printStackTrace() }
        return ret
    }

    fun pause(): Boolean {
        Log.w(TAG, "player pause...")
        if (playState == STOP || playState == PAUSED) return false
        runCatching {
            playState = PAUSED
        }.onFailure { it.printStackTrace() }
        return true
    }

    fun stop() {
        Log.w(TAG, "player stop...")
        runCatching {
            synchronized(syncObj) {
                playState = STOP
                resetAudio()
            }
        }.onFailure { it.printStackTrace() }
    }

    fun release() {
        Log.w(TAG, "player release...")
        stop()
        runCatching {
            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack?.release()
                audioTrack = null
            }
        }.onFailure { it.printStackTrace() }
    }


    private fun setState(srcState: Int, dstState: Int): Boolean {
        var ret = false
        synchronized(syncObj) {
            if (srcState == playState) {
                playState = dstState
                ret = true
            }
        }
        return ret
    }


    inner class PcmThread : Thread() {

        private var mAudioBuf: ByteArray? = null

        @Throws(IOException::class)
        private fun readAudio(minSize: Int) {
            if (null == this.mAudioBuf) {
                this.mAudioBuf = ByteArray(10 * minSize)
            }
            var dataSize: Int = this.mAudioBuf!!.size
            val bufLen: Int = (totalSize - readOffset).toInt()
            var readLen = dataSize
            if (bufLen < dataSize) {
                dataSize = bufLen
                readLen = bufLen
            }
            memFile?.readBytes(mAudioBuf, readOffset, 0, readLen)
            readOffset += readLen
            bufOffset = 0
            this@PcmAudioPlayer.bufLength = dataSize
            Log.i(TAG, "readAudio leave, dataSize=$dataSize, bufLen=$bufLen")
        }

        override fun run() {
            kotlin.runCatching {
                Log.i(TAG, "start player")
                readOffset = 0
                synchronized(syncObj) {
                    if (playState != STOP && playState != PAUSED) playState = PLAYING
                }
                while (!isExit.get()) {
                    when (playState) {
                        PLAYING, BUFFING -> {
                            if (playAble()) {
                                if (setState(BUFFING, PLAYING)) {
                                    mainThread {
                                        pcmAudioPlayerListener?.onResume()
                                    }
                                    Log.i(TAG, "BUFFERING to PLAYING  fading ")
                                }
                                mainThread {
                                    pcmAudioPlayerListener?.onPercent(getPlayPercent())
                                }
                                if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                                    audioTrack?.play()
                                }
//                                val blockSize = 16000
                                val blockSize = minBufferSize
                                if (bufOffset >= bufLength) {
                                    readAudio(blockSize)
                                }
                                var size = blockSize
                                if (2 * blockSize > bufLength - bufOffset) {
                                    size = bufLength - bufOffset
                                }
                                if (mAudioBuf != null) {
//                                    audioTrack?.let { initAudioTrack() }
                                    audioTrack?.write(mAudioBuf!!, bufOffset, size)
                                    bufOffset += size
                                }

                            } else if (isOver()) {
                                val pos: Int = audioTrack?.playbackHeadPosition ?: 0
                                val total = (totalSize / 2).toInt()
                                if (total > pos) {
                                    if (mEndLock.tryLock()) {
                                        audioTrack?.notificationMarkerPosition = total
                                        audioTrack?.setPlaybackPositionUpdateListener(object :
                                            AudioTrack.OnPlaybackPositionUpdateListener {
                                            override fun onMarkerReached(p0: AudioTrack?) {
                                                mEndLock.lock()
                                                try {
                                                    mEndCondition.signalAll()
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                } finally {
                                                    mEndLock.unlock()
                                                }
                                            }

                                            override fun onPeriodicNotification(p0: AudioTrack?) {
                                            }
                                        })
                                        try {
                                            mEndCondition.await(1000, TimeUnit.MILLISECONDS)
                                        } catch (ie: InterruptedException) {
                                            Log.i(TAG, "pcmplayer interrupted")
                                            ie.printStackTrace()
                                        } finally {
                                            mEndLock.unlock()
                                        }
                                    }
                                }
                                synchronized(syncObj) {
                                    Log.i(TAG, "play isover stop:$playState")
                                    if (playState != STOP) {
                                        this@PcmAudioPlayer.stop()
                                        audioTrack?.stop()
                                        mainThread {
                                            pcmAudioPlayerListener?.onStoped()
                                        }
                                    }
                                }
                            } else {
                                if (setState(PLAYING, BUFFING)) {
                                    Log.i(TAG, "play onpaused!")
                                    mainThread {
                                        pcmAudioPlayerListener?.onPaused()
                                    }
                                }
                                sleep(MIN_SLEEP)
                            }
                        }

                        PAUSED -> {
                            if (AudioTrack.PLAYSTATE_PAUSED != audioTrack?.playState) {
                                audioTrack?.pause()
                                Log.i(TAG, "pause done")
                                mainThread {
                                    pcmAudioPlayerListener?.onPaused()
                                }
                            }
                            sleep(MIN_SLEEP)
                        }

                        else -> {}
                    }
                }
                if (null != audioTrack && audioTrack?.state != AudioTrack.PLAYSTATE_STOPPED) {
                    audioTrack?.stop()
                }
            }.onFailure {
                Log.e(TAG, "PcmThread error", it)
                it.printStackTrace()
            }
            synchronized(syncObj) {
                playState = STOP
            }
            if (audioTrack != null) {
                audioTrack?.release()
                audioTrack = null
            }
            setAudioFocus(false)
            pcmThread = null
            Log.i(TAG, "player stopped")
        }
    }

    fun play(textLength: Int, listener: PcmPlayerListener) {
        Log.i(TAG, "play playState= $playState")
        synchronized(syncObj) {
            palyPercent = textLength.toLong()
            if (playState != STOP && playState != INIT && playState != PAUSED && null != pcmThread)
                return@synchronized
            if (pcmThread == null) {
                pcmThread = PcmThread()
            }
            if (audioTrack == null) {
                initAudioTrack()
            }
            playState = INIT
            pcmAudioPlayerListener = listener
            isExit.set(false)
            pcmThread?.start()
        }
    }


    /**
     * 播放器抢占、获取监听。
     */
    private var focusChangeListener =
        OnAudioFocusChangeListener { focusChange ->
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS
            ) {
                Log.i(TAG, "pause start")
                // 播放器被抢占，暂停时回调出去
                if (pause()) {
                    Log.i(TAG, "pause success")
                    // 是被抢占
                    changeListenerFlag = true
                    mainThread {
                        pcmAudioPlayerListener?.onPaused()
                    }
                }
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                Log.i(TAG, "resume start")
                // 被抢占后重新获得播放器，继续播放回调出去
                if (changeListenerFlag) {
                    changeListenerFlag = false
                    if (resume()) {
                        Log.i(TAG, "resume success")
                        mainThread {
                            pcmAudioPlayerListener?.onResume()
                        }
                    }
                }
            }
        }

    fun isOver(): Boolean {
        return readOffset >= totalSize && bufOffset >= bufLength
    }

    fun playAble(): Boolean {
        return (readOffset < totalSize
                || bufOffset < bufLength)
    }

    fun getPlayPercent(): Int {
        return if (totalSize <= 0) 0 else ((readOffset - (bufLength - bufOffset)) * palyPercent / totalSize).toInt()
    }

    fun writeMemFile(data: ByteArray?) {
        if (data == null || data.isEmpty()) return
        if (memFile == null) {
            val filePath = createNewFile().absolutePath
            memFile = MemoryFile(filePath, data.size)
            memFile?.allowPurging(false)
        }
        memFile?.writeBytes(data, 0, totalSize.toInt(), data.size)
        totalSize += data.size
        Log.i(TAG, "mTotalSize : $totalSize")
    }

    private fun deleteMemFile() {
        Log.i(TAG, "MemFile deleteFile")
        kotlin.runCatching {
            memFile?.let { it.close() }
            memFile = null
        }.onFailure {
            it.printStackTrace()
        }
    }

    private fun createNewFile(): File {
        return File(context.externalCacheDir, "${System.currentTimeMillis()}.pcm").apply {
            delete()
        }
    }


    private fun resetAudio() {
        isExit.set(true)
        palyPercent = 0
        totalSize = 0
        readOffset = 0
        bufOffset = 0
        bufLength = 0
        deleteMemFile()
    }

    /**
     * 音频焦点处理
     * @see <a href="https://developer.android.com/guide/topics/media-apps/audio-focus?hl=zh-cn#kotlin">audioFocusRequest</a>
     */
    internal fun setAudioFocus(focus: Boolean): Boolean {
        if (focus) {
            if (previousAudioMode == null) {
                previousAudioMode = audioManager.mode
            }
            if (context is Activity) {
                previousVolumeControlStream = context.volumeControlStream
            }
            previousMicrophoneMute = audioManager.isMicrophoneMute
            val requestResult: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                audioFocusRequest =
                    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(false)
                        .setOnAudioFocusChangeListener {
                            Log.i(TAG, "onAudioFocusChange => focusChange: $it")
                        }
                        .build()
                Log.i(
                    TAG,
                    "setAudioFocus =>" +
                            "\n\tfocus: $focus," +
                            "\n\taudioFocusRequest: $audioFocusRequest" +
                            "\n\tpreviousAudioMode: $previousAudioMode"
                )
                requestResult = audioManager.requestAudioFocus(audioFocusRequest!!)
            } else {
                requestResult = audioManager.requestAudioFocus(
                    null, AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }

            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isMicrophoneMute = false
            if (context is Activity) {
                context.volumeControlStream = AudioManager.STREAM_VOICE_CALL
            }
            val requestGranted = requestResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            Log.i(TAG, "requestAudioFocus => requestGranted: $requestGranted")
            return requestGranted
        } else {
            Log.i(
                TAG, "setAudioFocus =>" +
                        "\tfocus: $focus," +
                        "\taudioFocusRequest: $audioFocusRequest" +
                        "\tpreviousAudioMode: $previousAudioMode"
            )
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocus(null)
            } else if (audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
            }
            audioManager.isSpeakerphoneOn = false
            if (previousAudioMode != null) {
                audioManager.mode = previousAudioMode!!
                previousAudioMode = null
            }
            audioManager.isMicrophoneMute = previousMicrophoneMute
            if (context is Activity) {
                context.volumeControlStream = previousVolumeControlStream
            }
            return true
        }
    }

    interface PcmPlayerListener {

        fun onError(error: Throwable?)

        fun onPaused()

        fun onResume()

        fun onPercent(percent: Int)

        fun onStoped()
    }
}