package com.iflytek.aikitdemo.ability.ivw

import android.annotation.SuppressLint
import android.util.Log
import com.iflytek.aikit.core.AiHandle
import com.iflytek.aikit.core.AiHelper
import com.iflytek.aikit.core.AiListener
import com.iflytek.aikit.core.AiRequest
import com.iflytek.aikit.core.AiResponse
import com.iflytek.aikit.core.AiStatus
import com.iflytek.aikitdemo.ability.AbilityCallback
import com.iflytek.aikitdemo.ability.AbilityConstant
import com.iflytek.aikitdemo.media.audio.AudioRecorder
import com.iflytek.aikitdemo.media.audio.RecorderCallback
import com.iflytek.aikitdemo.tool.mainThread
import java.io.File
import java.io.InputStream
import java.lang.StringBuilder
import java.nio.charset.Charset

/**
 * @Desc: ivw唤醒
 * @Author leon
 * @Date 2023/3/22-14:08
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */
class IvwHelper(
    private val callback: AbilityCallback,
) : RecorderCallback, AiListener {

    companion object {
        private val TAG = this::class.java.simpleName
    }

    init {
        //能力回调
        AiHelper.getInst().registerListener(AbilityConstant.IVW_ID, this)
    }

    private val recorder: AudioRecorder by lazy {
        AudioRecorder.instance.apply {
            init()
            setRecorderCallback(this@IvwHelper)
        }
    }

    private var recordCallback: RecorderCallback? = null

    private val lockArray = ByteArray(0)

    //会话对象
    private var aiHandle: AiHandle? = null

    fun setRecorderCallback(callback: RecorderCallback?) {
        recordCallback = callback
    }

    /**
     * 开始命令词识别
     */
    @SuppressLint("MissingPermission")
    fun startAudioRecord(
        keyword: String,
        keywordSize: Int,
        nCmThreshold: Int
    ) {
        if (initParams(keyword,keywordSize, nCmThreshold)) return
        recorder.startRecording()
        mainThread {
            callback.onAbilityBegin()
        }
    }

    /**
     * @param keywordPath 唤醒词
     * @param nCmThreshold 门限值, 最小长度:0, 最大长度:3000
     * @param keywordSize 唤醒词个数
     */
    private fun initParams(
        keywordPath: String,
        keywordSize: Int,
        nCmThreshold: Int
    ): Boolean {
        var ret: Int = -1
        ret = AiHelper.getInst().engineInit(AbilityConstant.IVW_ID)
        if (ret != AbilityConstant.ABILITY_SUCCESS_CODE) {
            Log.w(TAG, "open ivw error code ===> $ret")
            mainThread {
                callback.onAbilityError(ret, Throwable("引擎初始化失败"))
            }
            return true
        }
        val customBuilder = AiRequest.builder()
        //资源可以设置多个，通过index来区别
        customBuilder.customText("key_word", keywordPath, 0)
        ret = AiHelper.getInst().loadData(AbilityConstant.IVW_ID, customBuilder.build())
        if (ret != AbilityConstant.ABILITY_SUCCESS_CODE) {
            Log.w(TAG, "open ivw error code ===> $ret")
            mainThread {
                callback.onAbilityError(ret, Throwable("open ivw error code"))
            }
            return true
        }
        //指定加载设置的唤醒词，这里array中的index需要在上面设置的FSA语法文件中的index对应上
        //这里暂时只指定加载index == 0 的
        ret = AiHelper.getInst().specifyDataSet(
            AbilityConstant.IVW_ID,
            "key_word",
            intArrayOf(0)
        )
        if (ret != AbilityConstant.ABILITY_SUCCESS_CODE) {
            Log.w(TAG, "open ivw specifyDataSet error code ===> $ret")
            mainThread {
                callback.onAbilityError(ret, Throwable("open ivw specifyDataSet error"))
            }
            return true
        }
        //门限值就是唤醒得分  如果你设置了这个门限值是1000  那么你唤醒得分如果超过这个1000的话  是不会被唤醒的
        //0 1:100  => 0 为第一个唤醒词文件 1 为第二个唤醒词  100 为门限值， 多个唤醒词情况下用 | 分割，如'0 0:100|0 1:200'
        //demo这里就一个唤醒词文件，所以第一个参数为0
        //demo这里为了方便测试，所以唤醒词的门限值为一致的，可以根据业务需要设置不同的门限值
        val nCmThresholdBuilder = StringBuilder()
        for (i in 0..keywordSize) {
            nCmThresholdBuilder.append("0 ${i}:$nCmThreshold|")
        }
        val params = AiRequest.builder().apply {
            param("wdec_param_nCmThreshold", nCmThresholdBuilder.toString()) //门限值	, 最小长度:0, 最大长度:1024
            param("gramLoad", true)
        }.build()
        aiHandle = AiHelper.getInst().start(AbilityConstant.IVW_ID, params, null)
        if (aiHandle?.code != AbilityConstant.ABILITY_SUCCESS_CODE) {
            ret = aiHandle?.code ?: AbilityConstant.ABILITY_CUSTOM_UNKNOWN_CODE
            Log.w(TAG, "open ivw start error code ===> $ret")
            mainThread {
                callback.onAbilityError(ret, Throwable("open ivw start error"))
            }
            return true
        }
        return false
    }


    /**
     * 停止命令词识别
     */
    fun stopAudioRecord() {
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
                callback.onAbilityEnd()
            } else {
                callback.onAbilityError(ret, Throwable("aiHandle end error"))
            }
        }
        aiHandle = null
    }

    /**
     * 写入音频文件
     */
    fun writeStream(
        stream: InputStream,
        filePath: String,
        keywordSize: Int,
        nCmThreshold: Int
    ) {
        if (initParams(filePath,keywordSize, nCmThreshold)) return
        val buffer = ByteArray(320)
        while (-1 != stream.read(buffer)) {
            writeData(buffer)
        }
        //补个尾帧，表示结束
        writeData(ByteArray(100))
        stream.close()
        endAiHandle()
    }

    /**
     * 写入音频数据
     */
    private fun writeData(audio: ByteArray) {
        if (aiHandle == null) {
            return
        }
        synchronized(lockArray) {
            val dataBuilder = AiRequest.builder()
            dataBuilder.audio("wav", audio)
            var ret = AiHelper.getInst().write(dataBuilder.build(), aiHandle)
            if (ret != AbilityConstant.ABILITY_SUCCESS_CODE) {
                //写入失败，暂停录音
                endAiHandle()
                Log.w(TAG, "writeData is error => $ret")
            } else {
                ret = AiHelper.getInst().read(AbilityConstant.IVW_ID, aiHandle)
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
    }

    override fun onPauseRecord() {
        Log.i(TAG, "onPauseRecord===>")
        recordCallback?.onPauseRecord()
    }

    override fun onResumeRecord() {
        Log.i(TAG, "onResumeRecord===>")
        recordCallback?.onResumeRecord()
    }

    override fun onRecordProgress(data: ByteArray, sampleSize: Int, volume: Int) {
        Log.i(TAG, "onRecordProgress===>$sampleSize=$volume")
        recordCallback?.onRecordProgress(data, sampleSize, volume)
        writeData(data)
    }

    override fun onStopRecord(output: File?) {
        Log.i(TAG, "onStopRecord===>${output?.absolutePath}")
        recordCallback?.onStopRecord(output)
    }

    /**
     * 能力输出回调结果
     * @param ability 能力标识ID
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
            val tempValue = item.value.toString(Charset.forName("utf-8"))
            if (tempKey.contains("func_wake_up") || tempKey.contains("func_pre_wakeup")) {
                mainThread {
                    callback.onAbilityResult("$tempKey: \n$tempValue")
                }
            }
        }
        if (esrData[0].status == AiStatus.END.value) {
            //停止recorder播放
            stopAudioRecord()
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
     * @param ability ability 能力标识ID
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
            callback.onAbilityError(err, Throwable(tips))
        }
        Log.e(TAG, tips)
    }


    /**
     * 所有能力在退出的时候需要手动去释放会话
     * AiHelper.getInst().end(aiHandle)
     */
    fun destroy() {
        stopAudioRecord()
    }
}