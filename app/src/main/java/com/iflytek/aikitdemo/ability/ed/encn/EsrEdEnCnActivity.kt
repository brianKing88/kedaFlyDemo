package com.iflytek.aikitdemo.ability.ed.encn

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MenuItem
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
import com.iflytek.aikitdemo.tool.calculateVolume
import com.iflytek.aikitdemo.tool.setChildrenEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * @Desc: 语音识别-中英文
 * @Author leon
 * @Date 2023/04/07-15:14
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */
class EsrEdEnCnActivity : BaseActivity(), AbilityCallback {

    companion object {
        private const val TAG = "EsrEdEnCnActivity"
    }

    private lateinit var btnLanguage: MaterialButton
    private lateinit var audioGroup: RadioGroup
    private lateinit var radioAudioRecord: AppCompatRadioButton
    private lateinit var radioAudioFile: AppCompatRadioButton
    private lateinit var btnAudioRecord: MaterialButton
    private lateinit var tvAudioRecord: AppCompatTextView
    private lateinit var btnAudioFile: MaterialButton
    private lateinit var tvResult: AppCompatTextView
    private var menuEdDecibel: MenuItem? = null

    private var esrEdHelper: EdEnCnHelper? = null

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_esr_ed)
        btnLanguage = findViewById(R.id.btnLanguage)
        audioGroup = findViewById(R.id.audioGroup)
        radioAudioRecord = findViewById(R.id.radioAudioRecord)
        radioAudioFile = findViewById(R.id.radioAudioFile)
        btnAudioRecord = findViewById(R.id.btnAudioRecord)
        tvAudioRecord = findViewById(R.id.tvAudioRecord)
        btnAudioFile = findViewById(R.id.btnAudioFile)
        tvResult = findViewById(R.id.tvResult)
        btnLanguage.apply {
            val lanArray = resources.getStringArray(R.array.esr_language)
            text = lanArray[0]
            setOnClickListener {
                val builder = AlertDialog.Builder(this@EsrEdEnCnActivity)
                builder.setSingleChoiceItems(lanArray, 0) { d, i ->
                    text = lanArray[i]
                    d.dismiss()
                }.create().show()
            }
        }
        audioGroup.setOnCheckedChangeListener { _, i ->
            audioButtonVisible(i == R.id.radioAudioRecord)
        }
        audioRecordListener("录音", false)
        initEd()
        btnAudioFile.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val fs = MyApp.CONTEXT.assets.open(languageAssetsFormat())
                kotlin.runCatching {
                    esrEdHelper?.writeStream(fs)
                }.onFailure {
                    it.printStackTrace()
                }
            }
        }
    }

    private fun languageAssetsFormat(): String {
        val language =
            btnLanguage.text.toString().trim().split("-").getOrNull(1)?.toIntOrNull() ?: 0
        return when (language) {
            0 -> "ed_multi_cn.pcm"
            1 -> "ed_multi_en.pcm"
            else -> "ed_multi_en.pcm"
        }
    }

    private fun initEd() {
        esrEdHelper = EdEnCnHelper(this)
        esrEdHelper?.apply {
            setRecorderCallback(recorderCallback)
            esrEdHelper?.loadCustomParams()
        }
        tvResult.append(AbilityConstant.ED_ENCN_ID.abilityAuthStatus())
    }

    private fun audioRecordListener(text: String, check: Boolean) {
        btnAudioRecord.clearOnCheckedChangeListeners()
        btnAudioRecord.text = text
        btnAudioRecord.isChecked = check
        btnAudioRecord.addOnCheckedChangeListener { button, isChecked ->
            esrEdHelper?.switchAsr(isChecked)
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
            val calculateVolume = data.calculateVolume()
            menuEdDecibel?.title = "当前分贝:$calculateVolume"
        }

        override fun onStopRecord(output: File?) {
        }

    }

    @SuppressLint("MissingPermission")
    override fun onAbilityBegin() {
        tvResult.append("语音识别开始\n")
    }

    override fun onAbilityResult(result: String) {
        tvResult.append("${result}\n")
    }

    override fun onAbilityError(code: Int, error: Throwable?) {
        audioButtonEnable(true)
        audioRecordListener("录音", false)
        tvResult.append("语音识别error---$code, msg=${error?.message}")
    }

    override fun onAbilityEnd() {
        tvResult.append("语音识别结束---")
        audioButtonEnable(true)
        audioRecordListener("录音", false)
    }


    override fun finish() {
        super.finish()
        esrEdHelper?.destroy()
    }

}