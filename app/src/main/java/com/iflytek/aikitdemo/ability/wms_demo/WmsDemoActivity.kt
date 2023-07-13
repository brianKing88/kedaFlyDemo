package com.iflytek.aikitdemo.ability.wms_demo

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.iflytek.aikitdemo.MyApp
import com.iflytek.aikitdemo.R
import com.iflytek.aikitdemo.ability.AbilityCallback
import com.iflytek.aikitdemo.ability.AbilityConstant
import com.iflytek.aikitdemo.ability.abilityAuthStatus
import com.iflytek.aikitdemo.ability.wms_demo.TTSHelper
import com.iflytek.aikitdemo.base.BaseActivity
import com.iflytek.aikitdemo.media.PcmAudioPlayer
import com.iflytek.aikitdemo.media.audio.RecorderCallback
import com.iflytek.aikitdemo.tool.setChildrenEnabled
import com.iflytek.aikitdemo.tool.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream




/**
 * @Desc: 中英文命令词
 * @Author leon
 * @Date 2023/2/23-17:14
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */
class WmsDemoActivity : BaseActivity(), AbilityCallback {

    private val TAG = "WsmDemoActivity"
    private var aiSoundHelper: TTSHelper? = null
    private lateinit var tvResult: AppCompatTextView
    private lateinit var btnTTS: MaterialButton // 发送语音指令按钮
    private lateinit var btnAudioFile: MaterialButton // 语音流

    private var isSpeaking = false
    private var isListening = true

    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize
    )
    private val buffer = ByteArray(bufferSize)



    private var esrHelper: WmsDemoHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wms_demo)

        tvResult = findViewById(R.id.tvResult)
        btnTTS = findViewById(R.id.btnTTS)
        btnAudioFile = findViewById(R.id.btnAudioFile) // 语音流
        initEsr()
        initTts()
    }

    private fun initEsr() {
        esrHelper = WmsDemoHelper(this)
        esrHelper?.apply {
            setRecorderCallback(recorderCallback)
        }
        tvResult.append( "语音识别：" + AbilityConstant.ESR_ID.abilityAuthStatus() + "\n")
        btnAudioFile.setOnClickListener {
            startListening()
        }
    }
    private fun initTts() {
        val engineId = AbilityConstant.XTTS_ID // 应用ID
        // 语音合成
        aiSoundHelper = TTSHelper(engineId, this, pcmPlayerListener)
        // 设置初始化  ["vcn","language","textEncoding"]
        aiSoundHelper?.setVCN("xiaoyan")

        tvResult.append("语音合成：" + engineId.abilityAuthStatus() + "\n") // 查看能力状态
        //点击 合成按钮
        btnTTS.setOnClickListener {
            startSpeaking()
            val ttsText = "你好！"
            if (TextUtils.isEmpty(ttsText)) {
                toast("合成文本不能为空")
                return@setOnClickListener
            }
            aiSoundHelper?.startSpeaking(ttsText ?: "")
        }
    }


    // 合成 pcm播放器结果回调
    private val pcmPlayerListener = object : PcmAudioPlayer.PcmPlayerListener {

        override fun onError(error: Throwable?) {
            tvResult.append("播放出错了===>\n")
        }

        override fun onPaused() {
            tvResult.append("暂停播放===>\n")
        }

        override fun onResume() {
            tvResult.append("继续播放===>\n")
        }

        override fun onPercent(percent: Int) {
            Log.d(TAG,"onPercent")
        }

        override fun onStoped() {
            tvResult.append("播放停止===>\n")
            aiSoundHelper?.stop()
            enableOperationButton(true)
            stopSpeaking()

        }
    }

    override fun finish() {
        super.finish()
        aiSoundHelper?.destroy()
    }
    // 合成
    @SuppressLint("MissingPermission")
    override fun onAbilityBegin() {
        Log.d(TAG, "开始合成数据")
        enableOperationButton(false)
        tvResult.append("开始合成数据\n")
    }
    override fun onAbilityResult(result: String) {
        tvResult.append("${result}\n")
    }
    override fun onAbilityError(code: Int, error: Throwable?) {
        tvResult.append("合成失败:${code} ${error?.message}\n")
    }
    override fun onAbilityEnd() {
        tvResult.append("合成结束=====\n")
        tvResult.append("开始播放...\n")
    }
    private fun enableOperationButton(boolean: Boolean) {
        btnTTS.isEnabled = boolean
    }
    // ----- 识别 -------
    private val recorderCallback = object : RecorderCallback {

        override fun onStartRecord() {
            //计算时长
            Log.d(TAG, "onStartRecord")
//            audioButtonEnable(false)
        }

        override fun onPauseRecord() {
            Log.d(TAG, "onPauseRecord")
        }

        override fun onResumeRecord() {
            Log.d(TAG, "onResumeRecord")

        }

        override fun onRecordProgress(data: ByteArray, sampleSize: Int, volume: Int) {
            Log.d(TAG, "onRecordProgress")
        }

        override fun onStopRecord(output: File?) {
            Log.d(TAG, "onStopRecord")
        }

    }
    private fun startListening(){
        lifecycleScope.launch(Dispatchers.IO) {
//            esrHelper?.startAudioRecord(0) // Start the ESR helper
            audioRecord.startRecording()
            Log.d(TAG, "startListening")
            while (isListening) {
                if (!isSpeaking) {
                    val bytes = audioRecord.read(buffer, 0, buffer.size)
                    if (bytes > 0) {
                        val inputStream = ByteArrayInputStream(buffer)
                        kotlin.runCatching {
                            esrHelper?.writeStream(0, inputStream) // Assuming language is defined somewhere in your class
                        }.onFailure {
                            it.printStackTrace()
                        }
                    }
                }
            }
            audioRecord.stop()
            esrHelper?.stopAudioRecord()
        }
    }
    private val stopListening = {
        isListening = false
    }

    private val startSpeaking = {
        stopListening()
        isSpeaking = true
        // TODO: Start speech synthesis here
    }

    private val stopSpeaking = {
        // TODO: Stop speech synthesis here
        isSpeaking = false
        isListening = true
        startListening()
    }
    // ----- 识别 -------
}