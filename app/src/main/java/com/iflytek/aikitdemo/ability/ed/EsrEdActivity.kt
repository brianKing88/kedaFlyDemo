package com.iflytek.aikitdemo.ability.ed

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
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
 * @Desc: 语音识别-多语种
 * @Author leon
 * @Date 2023/2/23-17:14
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */
class EsrEdActivity : BaseActivity(), AbilityCallback {

    companion object {
        private const val TAG = "EsrEdActivity"
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

    private var esrEdHelper: EdHelper? = null

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
            val lanArray = resources.getStringArray(R.array.ed_multi_language)
            text = lanArray[0]
            setOnClickListener {
                val builder = AlertDialog.Builder(this@EsrEdActivity)
                builder.setSingleChoiceItems(lanArray, 0) { d, i ->
                    text = lanArray[i]
                    initEd()
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

    //0:中英, 20:日语, 21:韩语, 22:俄语, 23:法语, 24:西班牙语, 25:德语, 27:泰语, 28:阿拉伯语, 50:维吾尔语, 51:藏语, 26:越南语
    private fun languageAssetsFormat(): String {
        val language =
            btnLanguage.text.toString().trim().split("-").getOrNull(1)?.toIntOrNull() ?: 0
        return when (language) {
            0 -> "ed_multi_cn.pcm"
            25 -> "ed_multi_de.pcm"
            24 -> "ed_multi_es.pcm"
            23 -> "ed_multi_fr.pcm"
            20 -> "ed_multi_ja.pcm"
            21 -> "ed_multi_ko.pcm"
            22 -> "ed_multi_ru.pcm"
            else -> "ed_multi_en.pcm"
        }
    }

    private fun initEd() {
        val language =
            btnLanguage.text.toString().trim().split("-").getOrNull(1)?.toIntOrNull() ?: 0
        esrEdHelper = EdHelper(this, language)
        esrEdHelper?.apply {
            setRecorderCallback(recorderCallback)
        }
        tvResult.append(AbilityConstant.ED_ID.abilityAuthStatus())
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_esr_ed, menu)
        menuEdDecibel = menu.findItem(R.id.ed_decibel)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.ed_setting -> {
                startActivity(Intent(this, EdSettingsActivity::class.java))
                true
            }

            R.id.ed_decibel -> {
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }


//    private val activityFileResultLauncher =
//        registerForActivityResult(
//            ActivityResultContracts.StartActivityForResult()
//        ) {
//            if (it.resultCode == Activity.RESULT_OK) {
//                val data = it.data
//                val fileUri = data?.data
//                kotlin.runCatching {
//                    esrEdHelper?.writeFile(fileUri)
//                }.onFailure {
//                    it.printStackTrace()
//                }
//            }
//        }

    override fun finish() {
        super.finish()
        esrEdHelper?.destroy()
    }

}