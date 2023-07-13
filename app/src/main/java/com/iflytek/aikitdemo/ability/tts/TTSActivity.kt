package com.iflytek.aikitdemo.ability.tts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import com.google.android.material.button.MaterialButton
import com.iflytek.aikitdemo.R
import com.iflytek.aikitdemo.ability.AbilityCallback
import com.iflytek.aikitdemo.ability.AbilityConstant
import com.iflytek.aikitdemo.ability.abilityAuthStatus
import com.iflytek.aikitdemo.base.BaseActivity
import com.iflytek.aikitdemo.media.PcmAudioPlayer
import com.iflytek.aikitdemo.tool.MemoryStats
import com.iflytek.aikitdemo.tool.mainThread
import com.iflytek.aikitdemo.tool.toast
import com.iflytek.aikitdemo.widget.CustomSeekBar
import java.util.Timer
import kotlin.concurrent.fixedRateTimer


/**
 * @Desc: 语音合成-aisound & xtts 复用一个UI页面，页面逻辑通过 isXtts变量来区分
 * @Author leon
 * @Date 2023/2/23-14:18
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */
class TTSActivity : BaseActivity(), AbilityCallback {

    private lateinit var etTTS: AppCompatEditText
    private lateinit var tvResult: AppCompatTextView
    private lateinit var soundTone: CustomSeekBar
    private lateinit var soundVolume: CustomSeekBar
    private lateinit var speedSpeech: CustomSeekBar
    private lateinit var btnVCN: MaterialButton
    private lateinit var btnTTS: MaterialButton
    private lateinit var btnSoundPause: MaterialButton
    private lateinit var btnSoundEnd: MaterialButton

    private var aiSoundHelper: TTSHelper? = null

    private val TAG = "TTSActivity"

    private var memIntervalTimer: Timer? = null

    //用来区分XTTS和aisound能力
    private val isXtts: Boolean by lazy {
        intent.extras?.getBoolean(XTTS_PARAMS) ?: true
    }

    private var ttsBuilder: SpannableStringBuilder? = null

    companion object {
        //用来区分XTTS和aisound能力
        private const val XTTS_PARAMS = "xtts_params"

        fun openTtsActivity(context: Context, isXtts: Boolean) {
            val intent = Intent(context, TTSActivity::class.java).apply {
                putExtras(bundleOf(XTTS_PARAMS to isXtts))
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aisound)
        setTitleBar(getString(if (isXtts) R.string.xtts_title else R.string.aisound_title))
        tvResult = findViewById(R.id.tvResult)
        val engineId = if (isXtts) AbilityConstant.XTTS_ID else AbilityConstant.TTS_ID
        aiSoundHelper = TTSHelper(engineId, this, pcmPlayerListener)
        tvResult.append(engineId.abilityAuthStatus())
        etTTS = findViewById<AppCompatEditText>(R.id.etTTS).apply {
            movementMethod = ScrollingMovementMethod.getInstance()
        }
        btnVCN = findViewById(R.id.btnVCN)
        btnTTS = findViewById(R.id.btnTTS)
        btnSoundPause = findViewById<MaterialButton?>(R.id.btnSoundPause).apply {
            isEnabled = false
        }
        btnSoundEnd = findViewById<MaterialButton?>(R.id.btnSoundEnd).apply {
            isEnabled = false
        }
        soundTone = findViewById(R.id.soundTone)
        soundTone.bindData("音调", 50) {
            aiSoundHelper?.setPitch(it)
        }
        soundVolume = findViewById(R.id.soundVolume)
        soundVolume.bindData("音量", 50) {
            aiSoundHelper?.setVolume(it)
        }
        speedSpeech = findViewById(R.id.speedSpeech)
        speedSpeech.bindData("语速", 50) {
            aiSoundHelper?.setSpeed(it)
        }

        //切换发音人
        btnVCN.apply {
            val vcnArray = resources.getStringArray(if (isXtts) R.array.xtts_vcn else R.array.aisound_vcn)
            val ttsTextArray = resources.getStringArray(R.array.aisound_text_array)
            text = vcnArray[0]
            aiSoundHelper?.setVCN(text.toString().trim())
            setOnClickListener {
                val builder = AlertDialog.Builder(this@TTSActivity)
                builder.setSingleChoiceItems(vcnArray, 0) { d, i ->
                    text = vcnArray[i]
                    //切换发音人朗读的文本
//                    etTTS.setText(ttsTextArray[i])
                    aiSoundHelper?.setVCN(text.toString().trim())
                    d.dismiss()
                }.create().show()
            }
        }
        //合成按钮
        btnTTS.setOnClickListener {
            btnSoundPause.text = "暂停"
            val ttsText = etTTS.text?.toString()?.trim()
            if (TextUtils.isEmpty(ttsText)) {
                toast("合成文本不能为空")
                return@setOnClickListener
            }
            aiSoundHelper?.startSpeaking(ttsText ?: "")
        }
        //暂停、播放
        btnSoundPause.addOnCheckedChangeListener { _, checked ->
            tvResult.append("点击了 ===>${btnSoundPause.text.toString()}\n")
            if (!checked) {
                btnSoundPause.text = "暂停"
                aiSoundHelper?.resume()
            } else {
                btnSoundPause.text = "继续播放"
                aiSoundHelper?.pause()
            }
        }
        //结束播放
        btnSoundEnd.setOnClickListener {
            tvResult.append("点击了 ===>${btnSoundEnd.text.toString()}\n")
            aiSoundHelper?.stop()
            enableOperationButton(true)
        }
        //内存检测 测试工具
        val tvMemory = findViewById<AppCompatTextView>(R.id.tvMemory)
        memIntervalTimer = fixedRateTimer("timer", false, 1L, 5 * 1000) {
            MemoryStats.snapshot().run {
                mainThread {
                    tvMemory.text =
                        "内存占用：\n${map { (k, v) -> "${k.name}: ${v} KB" }.joinToString("\n|  - ")}"
                }
            }
        }
        //合成文本结果显示
        val ttsContent = etTTS.text?.toString() ?: ""
        ttsBuilder = SpannableStringBuilder(ttsContent)
    }

    private fun enableOperationButton(boolean: Boolean) {
        btnTTS.isEnabled = boolean
        btnVCN.isEnabled = boolean
        soundTone.isEnabled = boolean
        soundVolume.isEnabled = boolean
        speedSpeech.isEnabled = boolean
        btnSoundPause.isEnabled = !boolean
        btnSoundEnd.isEnabled = !boolean
    }

    override fun onAbilityBegin() {
        Log.d(TAG, "开始合成数据")
        enableOperationButton(false)
        tvResult.append("开始合成数据\n")
    }

    override fun onAbilityResult(result: String) {
    }

    override fun onAbilityError(code: Int, error: Throwable?) {
        tvResult.append("合成失败: ${error?.message}\n")
    }

    override fun onAbilityEnd() {
        tvResult.append("合成结束=====\n")
        tvResult.append("开始播放...\n")
    }

    //pcm播放器结果回调
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
            val ttsLength = etTTS.text?.toString()?.length ?: 0
            if (ttsLength != ttsBuilder?.length) {
                ttsBuilder = SpannableStringBuilder(etTTS.text?.toString())
            }
            if (percent > ttsLength) return
            ttsBuilder?.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this@TTSActivity, R.color.color_E91E63)),
                0,
                if (ttsLength - percent < 5) ttsLength else percent,
                Spannable.SPAN_INCLUSIVE_EXCLUSIVE
            )
            etTTS.setText(ttsBuilder ?: "")
            (etTTS.text as? Spannable)?.apply {
                Selection.setSelection(this, percent)
            }
        }

        override fun onStoped() {
            tvResult.append("播放停止===>\n")
            aiSoundHelper?.stop()
            enableOperationButton(true)
            ttsBuilder?.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this@TTSActivity, R.color.black)),
                0,
                ttsBuilder?.length ?: 0,
                Spannable.SPAN_INCLUSIVE_EXCLUSIVE
            )
            etTTS.setText(ttsBuilder ?: "")
        }
    }

    override fun finish() {
        super.finish()
        memIntervalTimer?.cancel()
        aiSoundHelper?.destroy()
    }
}