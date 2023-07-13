package com.iflytek.aikitdemo.ability.esr

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.TextUtils
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.iflytek.aikitdemo.MyApp
import com.iflytek.aikitdemo.R
import com.iflytek.aikitdemo.ability.AbilityCallback
import com.iflytek.aikitdemo.ability.AbilityConstant
import com.iflytek.aikitdemo.ability.abilityAuthStatus
import com.iflytek.aikitdemo.base.BaseActivity
import com.iflytek.aikitdemo.media.audio.RecorderCallback
import com.iflytek.aikitdemo.tool.setChildrenEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * @Desc: 中英文命令词
 * @Author leon
 * @Date 2023/2/23-17:14
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */
class EsrActivity : BaseActivity(), AbilityCallback {

    private lateinit var btnLanguage: MaterialButton
    private lateinit var audioGroup: RadioGroup
    private lateinit var radioAudioRecord: AppCompatRadioButton
    private lateinit var radioAudioFile: AppCompatRadioButton
    private lateinit var btnAudioRecord: MaterialButton
    private lateinit var tvAudioRecord: AppCompatTextView
    private lateinit var btnAudioFile: MaterialButton
    private lateinit var tvResult: AppCompatTextView
    private lateinit var tvFsaContent: AppCompatTextView

    private var esrHelper: EsrHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_esr)
        btnLanguage = findViewById(R.id.btnLanguage)
        audioGroup = findViewById(R.id.audioGroup)
        radioAudioRecord = findViewById(R.id.radioAudioRecord)
        radioAudioFile = findViewById(R.id.radioAudioFile)
        btnAudioRecord = findViewById(R.id.btnAudioRecord)
        tvAudioRecord = findViewById(R.id.tvAudioRecord)
        btnAudioFile = findViewById(R.id.btnAudioFile)
        tvResult = findViewById(R.id.tvResult)
        tvFsaContent = findViewById(R.id.tvFsaContent)
        btnLanguage.apply {
            val lanArray = resources.getStringArray(R.array.esr_language)
            text = lanArray[0]
            setOnClickListener {
                val builder = AlertDialog.Builder(this@EsrActivity)
                builder.setSingleChoiceItems(lanArray, 0) { d, i ->
                    text = lanArray[i]
                    tvFsaContent.text = esrFsaList[i]
                    esrHelper?.changeEngineResource()
                    d.dismiss()
                }.create().show()
            }
        }
        tvFsaContent.text = esrFsaList[0]
        audioGroup.setOnCheckedChangeListener { _, i ->
            audioButtonVisible(i == R.id.radioAudioRecord)
        }
        btnAudioFile.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val isCn = TextUtils.equals(btnLanguage.text.toString().trim(), "中文-0")
                val fs =
                    MyApp.CONTEXT.assets.open(if (isCn) "esr_qingbangwodakaidaohang.pcm" else "esr_en.pcm")
                val language =
                    btnLanguage.text.toString().trim().split("-").getOrNull(1)?.toIntOrNull() ?: 0
                kotlin.runCatching {
                    esrHelper?.writeStream(language, fs)
                }.onFailure {
                    it.printStackTrace()
                }
            }
        }
        //命令词 中文：请帮我打开导航 ； 英文：
        audioRecordListener("录音", false)
        initEsr()
    }

    private fun initEsr() {
        esrHelper = EsrHelper(this)
        esrHelper?.apply {
            setRecorderCallback(recorderCallback)
        }
        tvResult.append(AbilityConstant.ESR_ID.abilityAuthStatus())
    }

    private fun audioRecordListener(text: String, check: Boolean) {
        btnAudioRecord.clearOnCheckedChangeListeners()
        btnAudioRecord.text = text
        btnAudioRecord.isChecked = check
        btnAudioRecord.addOnCheckedChangeListener { button, isChecked ->
            val language =
                btnLanguage.text.toString().trim().split("-").getOrNull(1)?.toIntOrNull() ?: 0
            if (isChecked) esrHelper?.startAudioRecord(language) else esrHelper?.stopAudioRecord()
            btnAudioRecord.text = if (isChecked) "停止录音" else "录音"
        }
    }

    private fun audioButtonVisible(audioVisible: Boolean) {
        btnAudioRecord.isVisible = audioVisible
//        tvAudioRecord.isVisible = audioVisible
        btnAudioFile.isVisible = !audioVisible
    }

    private fun audioButtonEnable(enable: Boolean) {
        btnLanguage.isEnabled = enable
        audioGroup.setChildrenEnabled(enable)
    }

    private val recorderCallback = object : RecorderCallback {

        override fun onStartRecord() {
            //计算时长
            audioButtonEnable(false)
        }

        override fun onPauseRecord() {
        }

        override fun onResumeRecord() {
        }

        override fun onRecordProgress(data: ByteArray, sampleSize: Int, volume: Int) {

        }

        override fun onStopRecord(output: File?) {
        }

    }

    @SuppressLint("MissingPermission")
    override fun onAbilityBegin() {
        tvResult.append("命令词识别开始\n")
    }

    /**
     * esr命令词识别内容回调
     */
    override fun onAbilityResult(result: String) {
        tvResult.append("${result}\n")
    }


    override fun onAbilityError(code: Int, error: Throwable?) {
        audioButtonEnable(true)
        tvResult.append("命令词识别error---$code, msg=${error?.message}")
    }

    override fun onAbilityEnd() {
        tvResult.append("命令词识别结束--")
        audioButtonEnable(true)
        audioRecordListener("录音", false)
    }

    override fun finish() {
        super.finish()
        esrHelper?.destroy()
    }
}