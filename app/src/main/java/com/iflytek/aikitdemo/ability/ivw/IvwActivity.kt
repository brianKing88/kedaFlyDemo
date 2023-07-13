package com.iflytek.aikitdemo.ability.ivw

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.RadioGroup
import androidx.appcompat.widget.AppCompatEditText
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
import com.iflytek.aikitdemo.widget.CustomSeekBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.Charset


/**
 * @Desc: ivw唤醒
 * @Author leon
 * @Date 2023/2/23-17:14
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */
class IvwActivity : BaseActivity(), AbilityCallback {

    private val TAG = "IvwActivity"

    private lateinit var tvKeyword: AppCompatEditText
    private lateinit var audioGroup: RadioGroup
    private lateinit var radioAudioRecord: AppCompatRadioButton
    private lateinit var radioAudioFile: AppCompatRadioButton
    private lateinit var btnAudioRecord: MaterialButton
    private lateinit var tvAudioRecord: AppCompatTextView
    private lateinit var btnAudioFile: MaterialButton
    private lateinit var tvResult: AppCompatTextView
    private lateinit var progressThreshold: CustomSeekBar
    private var menuEdDecibel: MenuItem? = null


    private var ivwHelper: IvwHelper? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ivw)
        tvKeyword = findViewById(R.id.tvKeyword)
        audioGroup = findViewById(R.id.audioGroup)
        radioAudioRecord = findViewById(R.id.radioAudioRecord)
        radioAudioFile = findViewById(R.id.radioAudioFile)
        btnAudioRecord = findViewById(R.id.btnAudioRecord)
        tvAudioRecord = findViewById(R.id.tvAudioRecord)
        btnAudioFile = findViewById(R.id.btnAudioFile)
        tvResult = findViewById(R.id.tvResult)
        progressThreshold = findViewById(R.id.progressThreshold)
        progressThreshold.apply {
            setMaxProgress(3000)
            bindData("门限值", 900) {}
        }
        ivwHelper = IvwHelper(this).apply {
            setRecorderCallback(recorderCallback)
        }
        tvResult.append(AbilityConstant.IVW_ID.abilityAuthStatus())

        audioGroup.setOnCheckedChangeListener { _, i ->
            audioButtonVisible(i == R.id.radioAudioRecord)
        }
        btnAudioFile.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val fs = MyApp.CONTEXT.assets.open("ivw_xiaoduxiaodu.pcm")
                val filePath = createKeywordFile()
                kotlin.runCatching {
                    val keywordSize = tvKeyword.text.toString().trim().split(";").count()
                    ivwHelper?.writeStream(fs, filePath, keywordSize, progressThreshold.getProgress())
                }.onFailure {
                    it.printStackTrace()
                }
            }
        }
        audioRecordListener("录音", false)
    }

    private fun audioRecordListener(text: String, check: Boolean) {
        btnAudioRecord.clearOnCheckedChangeListeners()
        btnAudioRecord.text = text
        btnAudioRecord.isChecked = check
        btnAudioRecord.addOnCheckedChangeListener { button, isChecked ->
            lifecycleScope.launch(Dispatchers.IO) {
                if (isChecked) {
                    val filePath = createKeywordFile()
                    val keywordSize = tvKeyword.text.toString().trim().split(";").count()
                    ivwHelper?.startAudioRecord(filePath, keywordSize, progressThreshold.getProgress())
                } else {
                    ivwHelper?.stopAudioRecord()
                }
            }
            btnAudioRecord.text = if (isChecked) "停止录音" else "录音"
        }
    }


    private fun audioButtonVisible(audioVisible: Boolean) {
        btnAudioRecord.isVisible = audioVisible
//        tvAudioRecord.isVisible = audioVisible
        btnAudioFile.isVisible = !audioVisible
    }

    private fun audioButtonEnable(enable: Boolean) {
        audioGroup.setChildrenEnabled(enable)
        tvKeyword.isEnabled = enable
    }

    private fun createKeywordFile(): String {
        val file = File(MyApp.CONTEXT.externalCacheDir, "keyword.txt")
        if (file.exists()) {
            file.delete()
        }
        val binFile = File("${MyApp.CONTEXT.externalCacheDir}/process", "key_word.bin")
        if (binFile.exists()) {
            binFile.delete()
        }
        kotlin.runCatching {
            val keyword = tvKeyword.text.toString().trim()
                .replace("；", ";")
                .replace(";", ";\n")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
            val bufferedWriter =
                BufferedWriter(OutputStreamWriter(FileOutputStream(file), Charset.forName("GBK")))
//            val bufferedWriter = BufferedWriter(FileWriter(file))
            bufferedWriter.write(keyword)
            bufferedWriter.close()
        }.onFailure {
            Log.w(TAG, "唤醒词写入失败")
        }
        return file.absolutePath
    }
    override fun onAbilityBegin() {
        tvResult.append("语音唤醒开始\n")
        audioButtonEnable(false)
    }

    override fun onAbilityResult(result: String) {
        tvResult.append("${result}\n")
    }

    override fun onAbilityError(code: Int, error: Throwable?) {
        audioButtonEnable(true)
        audioRecordListener("录音", false)
        tvResult.append("语音唤醒error---$code, msg=${error?.message}")
    }

    override fun onAbilityEnd() {
        tvResult.append("语音唤醒结束---")
        audioButtonEnable(true)
        audioRecordListener("录音", false)
    }

    private val recorderCallback = object : RecorderCallback {

        override fun onStartRecord() {}

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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_esr_ed, menu)
        menu.findItem(R.id.ed_setting).isVisible = false
        menuEdDecibel = menu.findItem(R.id.ed_decibel)
        return true
    }

    override fun finish() {
        super.finish()
        ivwHelper?.destroy()
    }
}