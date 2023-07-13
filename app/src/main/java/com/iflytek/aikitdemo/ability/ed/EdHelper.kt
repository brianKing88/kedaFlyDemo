package com.iflytek.aikitdemo.ability.ed

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.iflytek.aikit.core.AiHandle
import com.iflytek.aikit.core.AiHelper
import com.iflytek.aikit.core.AiListener
import com.iflytek.aikit.core.AiRequest
import com.iflytek.aikit.core.AiResponse
import com.iflytek.aikit.core.AiStatus
import com.iflytek.aikit.core.DataStatus
import com.iflytek.aikitdemo.MyApp
import com.iflytek.aikitdemo.ability.AbilityCallback
import com.iflytek.aikitdemo.ability.AbilityConstant
import com.iflytek.aikitdemo.media.audio.AudioRecorder
import com.iflytek.aikitdemo.media.audio.RecorderCallback
import com.iflytek.aikitdemo.tool.mainThread
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @Desc: 语音识别
 * @Author leon
 * @Date 2023/3/7-19:59
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */
class EdHelper(
    private val callBack: AbilityCallback,
    private val language: Int
) : RecorderCallback, AiListener {

    companion object {
        private val TAG = this::class.java.simpleName
    }

    init {
        //能力回调
        AiHelper.getInst().registerListener(AbilityConstant.ED_ID, this)
    }


    private val preferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(MyApp.CONTEXT)
    }

    private val recorder: AudioRecorder by lazy {
        AudioRecorder.instance.apply {
            init()
            setRecorderCallback(this@EdHelper)
        }
    }

    private var recordCallback: RecorderCallback? = null

    private val lockArray = ByteArray(0)

    //会话对象
    private var aiHandle: AiHandle? = null

    private val audioBegin: AtomicBoolean = AtomicBoolean(false)


    fun setRecorderCallback(callback: RecorderCallback?) {
        recordCallback = callback
    }

    fun switchAsr(boolean: Boolean) {
        if (boolean) startEsr()
        else stopAsr()
    }

    /**
     * 开始语音识别
     */
    @SuppressLint("MissingPermission")
    fun startEsr() {
        if (initParams()) return
        recorder.startRecording()
        mainThread {
            callBack.onAbilityBegin()
        }
    }

    private fun initParams(): Boolean {
        //能力逆初始化， 部分能力，比如语种切换的时候 需要逆初始化
        AiHelper.getInst().engineUnInit(AbilityConstant.ED_ID)
        var ret: Int = -1
        ret = AiHelper.getInst().engineInit(AbilityConstant.ED_ID)
        if (ret != AbilityConstant.ABILITY_SUCCESS_CODE) {
            Log.w(TAG, "open ivw error code ===> $ret")
            mainThread {
                callBack.onAbilityError(ret,Throwable("open ivw engineInit error"))
            }
            return true
        }
        val paramBuilder = AiRequest.builder().apply {
            param("languageType", language) //语种类型
            param("vadOn", preferences.getBoolean("vadOn", true)) //vad功能开关，建议true
            param("rltSep", "") //输出词之间的分隔符，默认填写blank 最小长度:0, 最大长度:6
            param("vadThreshold", 0.1332) //vad模型阈值，建议0.1332 最小值:0, 最大值:1
            param("vadEnergyThreshold", 9) //vad的能量门限值，建议9 	最小值:0, 最大值:12
            param(
                "vadLinkOn",
                preferences.getBoolean("vadLinkOn", false)
            )//vad是否开启vadLink功能，开启则将多个vad子句拼在一起解码，建议false
            param("puncCache", preferences.getBoolean("puncCache", true)) //句尾标点是否为缓存模式
            param(
                "vadSpeechEnd",
                preferences.getString("vadSpeechEnd", "120000")?.toIntOrNull() ?: 120000
            ) //vad尾端点，最小值:100000, 最大值:150000, 建议120000
            param(
                "vadResponsetime",
                preferences.getString("vadResponsetime", "150000")?.toIntOrNull() ?: 150000
            )//vad结束参数，建议150000,最小值:100000, 最大值:170000
        }
        aiHandle = AiHelper.getInst().start(AbilityConstant.ED_ID, paramBuilder.build(), null)
        if (aiHandle?.code != AbilityConstant.ABILITY_SUCCESS_CODE) {
            Log.w(TAG, "open esr start error code ===> ${aiHandle?.code}")
            mainThread {
                callBack.onAbilityError(
                    aiHandle?.code ?: AbilityConstant.ABILITY_CUSTOM_UNKNOWN_CODE,
                    Throwable("open esr start error")
                )
            }
            return true
        }
        return false
    }

    /**
     * 停止语音识别
     */
    private fun stopAsr() {
        recorder.stopRecording()
        endAiHandle()
    }

    /**
     * 手动结束会话
     */
    private fun endAiHandle() {
        val ret = AiHelper.getInst().end(aiHandle)
        mainThread {
            if (ret == AbilityConstant.ABILITY_SUCCESS_CODE) {
                callBack.onAbilityEnd()
            } else {
                callBack.onAbilityError(ret, Throwable("aiHandle end error"))
            }
        }
        aiHandle = null
    }

    /**
     * 写入音频文件
     */
    fun writeStream(stream: InputStream) {
        if (initParams()) return
        val buffer = ByteArray(320)
        var status = DataStatus.BEGIN
        while (-1 != stream.read(buffer)) {
            writeData(buffer, status)
            status = DataStatus.CONTINUE
        }
        //补个尾帧，表示结束
        writeData(ByteArray(100), DataStatus.END)
        stream.close()
    }

    /**
     * 写入音频数据
     * @param status 送入的数据的状态，告诉引擎送入的首帧数据、中间数据、还是尾帧
     */
    private fun writeData(audio: ByteArray, status: DataStatus) {
        if (aiHandle == null) {
            return
        }
        synchronized(lockArray) {
            val dataBuilder = AiRequest.builder()
            dataBuilder.dataStatus(status)
            dataBuilder.audio("input", audio)
            var ret = AiHelper.getInst().write(dataBuilder.build(), aiHandle)
            if (ret != AbilityConstant.ABILITY_SUCCESS_CODE) {
                endAiHandle()
                Log.w(TAG, "writeData is error => $ret")
            } else {
                ret = AiHelper.getInst().read(AbilityConstant.ED_ID, aiHandle)
                if (ret != AbilityConstant.ABILITY_SUCCESS_CODE) {
                    Log.w(TAG, "read error code => $ret")
                    endAiHandle()
                } else {
                    Log.w(TAG, "read success code => $ret")
                }
            }
        }
    }

    /**
     * audioRecorder 回调
     */
    override fun onStartRecord() {
        Log.i(TAG, "onStartRecord===>")
        recordCallback?.onStartRecord()
        audioBegin.set(true)
    }

    override fun onPauseRecord() {
        Log.i(TAG, "onPauseRecord===>")
        recordCallback?.onPauseRecord()
    }

    override fun onResumeRecord() {
        Log.i(TAG, "onResumeRecord===>")
        recordCallback?.onResumeRecord()
        audioBegin.set(true)
    }

    override fun onRecordProgress(data: ByteArray, sampleSize: Int, volume: Int) {
        Log.i(TAG, "onRecordProgress===>$sampleSize=$volume")
        recordCallback?.onRecordProgress(data, sampleSize, volume)
        var status = DataStatus.CONTINUE
        if (audioBegin.get()) {
            status = DataStatus.BEGIN
            audioBegin.set(false)
        }
        writeData(data, status)
    }

    override fun onStopRecord(output: File?) {
        Log.i(TAG, "onStopRecord===>${output?.absolutePath}")
        recordCallback?.onStopRecord(output)
    }

    /**
     * 能力输出回调结果
     * @param handleID  会话ID
     * @param usrContext  用户自定义标识
     * @param responseData List<AiResponse> 是 能力执行结果
     */
    override fun onResult(
        handleID: Int,
        responseData: MutableList<AiResponse>?,
        usrContext: Any?
    ) {
        val esrData = responseData ?: return
        if (esrData.isNotEmpty().not()) return
        Log.i(
            TAG,
            "onResult:handleID:${handleID} : ${esrData.count()} usrContext: ${usrContext}"
        )
        for (item in esrData) {
            val tempKey = item.key
            val tempValue =
                item.value.toString(Charset.forName(if (language == 0) "GBK" else "utf-8"))
            if (tempKey.contains("plain") || tempKey.contains("pgs")) {
                mainThread {
                    callBack.onAbilityResult("$tempKey: \n$tempValue")
                }
                if (tempKey.contains("plain")) {
                    stopAsr()
                    return
                }
            }
        }
        if (esrData[0].status == AiStatus.END.value) {
            stopAsr()
        }
    }

    /**
     * 能力输出事件回调
     * @param ability ability 能力标识ID
     * @param handleID 会话ID
     * @param event  0=未知;1=开始;2=结束;3=超时;4=进度
     * @param usrContext Object 用户自定义标识
     * @param eventData List<AiResponse>  事件消息数据
     *
     * >>>> 注意啦：这里语音识别、ivw唤醒不会执行该事件回调 <<<<
     */
    override fun onEvent(
        handleID: Int,
        event: Int,
        eventData: MutableList<AiResponse>?,
        usrContext: Any?
    ) {
    }

    /**
     * 能力输出失败回调
     * @param handleID 会话ID
     * @param err  错误码
     * @param usrContext Object 用户自定义标识
     * @param msg 错误描述
     */
    override fun onError(
        handleID: Int,
        err: Int,
        msg: String?,
        usrContext: Any?
    ) {
        val tips = "onError==>,ERROR::$msg,err code:$err"
        mainThread {
            callBack.onAbilityError(err, Throwable(tips))
        }
        Log.e(TAG, tips)
    }

    /**
     * 所有能力在退出的时候需要手动去释放会话
     * AiHelper.getInst().end(aiHandle)
     */
    fun destroy() {
        stopAsr()
    }
}