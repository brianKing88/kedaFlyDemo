package com.iflytek.aikitdemo.ability.ed.encn

import android.annotation.SuppressLint
import android.util.Log
import com.iflytek.aikit.core.AiAudio
import com.iflytek.aikit.core.AiHandle
import com.iflytek.aikit.core.AiHelper
import com.iflytek.aikit.core.AiListener
import com.iflytek.aikit.core.AiRequest
import com.iflytek.aikit.core.AiResponse
import com.iflytek.aikit.core.AiStatus
import com.iflytek.aikit.core.DataStatus
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
 * @Desc: 语音识别 - 中英文
 * @Author leon
 * @Date 2023/04/07-15:29
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */
class EdEnCnHelper(
    private val callBack: AbilityCallback
) : AiListener, RecorderCallback {

    companion object {
        private val TAG = this::class.java.simpleName
    }

    init {
        //能力回调
        AiHelper.getInst().registerListener(AbilityConstant.ED_ENCN_ID, this)
    }

    private val recorder: AudioRecorder by lazy {
        AudioRecorder.instance.apply {
            init()
            setRecorderCallback(this@EdEnCnHelper)
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

    /**
     * ed资源存放目录
     */
    private fun workDir(): String {
        return "/sdcard/iflytekAikit/ed/encn"
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

    /**
     * 初始化挂载个性化参数
     */
    fun loadCustomParams() {
        val ret: Int
        val customBuilder = AiRequest.builder()
        //不替换列表, 开启数字规整功能时，汉字数字会被识别为阿拉伯数字，对于不想转换的数字，可通过不替换列表配置实现
        customBuilder.customText(
            "PPROC_NOT_REP", "${workDir()}/num_not_change_list", 0
        )
        //替换列表 , 识别出结果后，后处理阶段可将替换列表中词自定义替换
        customBuilder.customText("PPROC_REPLACE", "${workDir()}/replace_list", 1)
        ret = AiHelper.getInst().loadData(
            AbilityConstant.ED_ENCN_ID,
            customBuilder.build()
        )
        if (ret != 0) {
            mainThread {
                callBack.onAbilityError(ret, Throwable("open esr loadData 失败：$ret"))
            }
        }
    }

    private fun initParams(): Boolean {
        //能力逆初始化， 部分能力，比如语种切换的时候 需要逆初始化
        AiHelper.getInst().engineUnInit(AbilityConstant.ED_ENCN_ID)
        var ret: Int = -1
        ret = AiHelper.getInst().engineInit(AbilityConstant.ED_ENCN_ID)
        if (ret != AbilityConstant.ABILITY_SUCCESS_CODE) {
            Log.w(TAG, "open ivw error code ===> $ret")
            mainThread {
                callBack.onAbilityError(ret, Throwable("引擎初始化失败：$ret"))
            }
            return true
        }
        ret =
            AiHelper.getInst()
                .specifyDataSet(AbilityConstant.ED_ENCN_ID, "PPROC_NOT_REP", intArrayOf(0))
        if (ret != 0) {
            mainThread {
                callBack.onAbilityError(ret, Throwable("open esr specifyDataSet 失败：$ret"))
            }
            return true
        }
        ret = AiHelper.getInst()
            .specifyDataSet(AbilityConstant.ED_ENCN_ID, "PPROC_REPLACE", intArrayOf(1))
        if (ret != 0) {
            mainThread {
                callBack.onAbilityError(ret, Throwable("open esr specifyDataSet 失败：$ret"))
            }
            return true
        }
        val paramBuilder = AiRequest.builder().apply {
            param("lmLoad", true)
            param("vadLoad", true)
            param("puncLoad", true)
            param("numLoad", true)
            param("postprocOn", true)
            param("lmOn", true)
            param("vadOn", true)
            param("vadLinkOn", false)
        }
        aiHandle = AiHelper.getInst().start(AbilityConstant.ED_ENCN_ID, paramBuilder.build(), null)
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
     * 写入音频文件
     */
    fun writeStream(stream: InputStream) {
        if (initParams()) return
        val buffer = ByteArray(320)
        var status = AiStatus.BEGIN
        while (-1 != stream.read(buffer)) {
            writeData(buffer, status)
            status = AiStatus.CONTINUE
        }
        //补个尾帧，表示结束
        writeData(ByteArray(100), AiStatus.END)
        stream.close()
    }

    /**
     * 写入音频数据
     * @param status 送入的数据的状态，告诉引擎送入的首帧数据、中间数据、还是尾帧
     */
    private fun writeData(audio: ByteArray, status: AiStatus) {
        if (aiHandle == null) {
            return
        }
        synchronized(lockArray) {
            val dataBuilder = AiRequest.builder()
            val holder: AiAudio.Holder = AiAudio.get("PCM").data(audio)
            holder.status(status)
            dataBuilder.payload(holder.valid())
            var ret = AiHelper.getInst().write(dataBuilder.build(), aiHandle)
            if (ret != AbilityConstant.ABILITY_SUCCESS_CODE) {
                endAiHandle()
                Log.w(TAG, "writeData is error => $ret")
            } else {
                ret = AiHelper.getInst().read(AbilityConstant.ED_ENCN_ID, aiHandle)
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
        var status = AiStatus.CONTINUE
        if (audioBegin.get()) {
            status = AiStatus.BEGIN
            audioBegin.set(false)
        }
        writeData(data, status)
    }

    override fun onStopRecord(output: File?) {
        Log.i(TAG, "onStopRecord===>${output?.absolutePath}")
        recordCallback?.onStopRecord(output)
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
                item.value.toString(Charset.forName("GBK"))
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
        if (esrData[0].status == DataStatus.END.value) {
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