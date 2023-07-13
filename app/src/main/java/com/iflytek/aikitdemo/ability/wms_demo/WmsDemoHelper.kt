package com.iflytek.aikitdemo.ability.wms_demo

import android.annotation.SuppressLint
import android.util.Log
import com.iflytek.aikit.core.AiHandle
import com.iflytek.aikit.core.AiHelper
import com.iflytek.aikit.core.AiListener
import com.iflytek.aikit.core.AiRequest
import com.iflytek.aikit.core.AiResponse
import com.iflytek.aikit.core.AiStatus
import com.iflytek.aikit.utils.log.LogUtil.init
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
 * @Desc: 命令词识别
 * @Author leon
 * @Date 2023/3/20-19:30
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */
class WmsDemoHelper(
    private val callBack: AbilityCallback
) : RecorderCallback, AiListener {

    companion object {
        private val TAG = this::class.java.simpleName
    }

    private val recorder: AudioRecorder by lazy {
        AudioRecorder.instance.apply {
            init()
            setRecorderCallback(this@WmsDemoHelper)
        }
    }

    private var recordCallback: RecorderCallback? = null

    private val lockArray = ByteArray(0)

    //会话对象
    private var aiHandle: AiHandle? = null

    private val isInit  = AtomicBoolean(false)

    init {
        //能力回调
        AiHelper.getInst().registerListener(AbilityConstant.ESR_ID, this)
    }

    private fun initEngine(language: Int): Boolean {
        val engineBuilder = AiRequest.builder()
        //解码类型 fsa:命令词, wfst:wfst解码, wfst_fsa:混合解 是 码
        engineBuilder.param("decNetType", "fsa")
        //fsa惩罚分数 最小值:0, 最大值:10
        engineBuilder.param("punishCoefficient", 0.0)
        //选择加载wfst资源 0中文，1英文
        engineBuilder.param("wfst_addType", language)
        //初始化引擎
        val ret = AiHelper.getInst().engineInit(AbilityConstant.ESR_ID, engineBuilder.build())
        Log.i(TAG, "引擎初始化结果：$ret")
        if (ret != AbilityConstant.ABILITY_SUCCESS_CODE) {
            mainThread {
                callBack.onAbilityError(ret, Throwable("引擎初始化结果 ===> $ret"))
            }
            return false
        }
        return true
    }

    /**
     * 切换资源的时候需要做引擎的逆初始化操作
     */
    fun changeEngineResource(){
        isInit.set(false)
        unloadResource()
        engineUnInit()
    }


    /**
     * 逆初始化
     */
    private fun engineUnInit(){
        //这里考虑到语种切换，所以每次初始化时候 ，先逆初始化引擎id
        AiHelper.getInst().engineUnInit(AbilityConstant.ESR_ID)
    }

    fun setRecorderCallback(callback: RecorderCallback?) {
        recordCallback = callback
    }

    /**
     * esr资源存放目录
     */
    private fun workDir(): String {
        return "/sdcard/iflytekAikit/esr"
    }

    /**
     * 开始命令词识别
     */
    @SuppressLint("MissingPermission")
    fun startAudioRecord(language: Int) {
        if (initParams(language)) return
        recorder.startRecording()
        mainThread {
            callBack.onAbilityBegin()
        }
    }

    private fun initParams(language: Int): Boolean {
        if (!isInit.get()){
            initEngine(language)
        }
        isInit.set(true)
//        //在不切换语种的情况下，如何需要重新设置命令词，需要卸载之前的命令词，再去加载新的命令词
        unloadResource()
        val customBuilder = AiRequest.builder()
        //设置FSA语法资源
        val fsaDir = "${workDir()}/${if (language == 0) "cn_fsa" else "en_fsa"}"
        //语法资源可以设置多个，通过index来区别
        for (withIndex in DemoFsaEnum.values().withIndex()) {
            customBuilder.customText(
                "FSA",
                "${fsaDir}/${withIndex.value.path}",
                withIndex.index
            )
        }
        var ret: Int = -1
        ret = AiHelper.getInst().loadData(AbilityConstant.ESR_ID, customBuilder.build())
        if (ret != AbilityConstant.ABILITY_SUCCESS_CODE) {
            Log.w(TAG, "open esr error code ===> $ret")
            mainThread {
                callBack.onAbilityError(ret, Throwable("open esr error code"))
            }
            return true
        }
        //指定加载设置的FSA语法文件，这里array中的index需要在上面设置的FSA语法文件中的index对应上
        //这里暂时只指定加载index == 0 和 index == 1的， index= 2暂时不加载
        ret = AiHelper.getInst().specifyDataSet(
            AbilityConstant.ESR_ID,
            "FSA",
            intArrayOf(0, 1, 3)
        )
        if (ret != AbilityConstant.ABILITY_SUCCESS_CODE) {
            Log.w(TAG, "open esr specifyDataSet error code ===> $ret")
            mainThread {
                callBack.onAbilityError(ret, Throwable("open esr specifyDataSet error"))
            }
            return true
        }
        val paramBuilder = AiRequest.builder().apply {
            param("languageType", language)//0:中文, 1:英文
            param("vadEndGap", if (language == 0) 60 else 75) //子句分割时间间隔，中文建议60，英文建议75
            param("vadOn", true)
            param("beamThreshold", if (language == 0) 20 else 25) //解码控制beam的阈值，中文建议20，英文建议25
            param("hisGramThreshold", 3000)  //解码Gram阈值，建议值3000
            param("vadLinkOn", false)
            param("vadSpeechEnd", 80)
            param("vadResponsetime", 1000)
            param("postprocOn", false) //后处理开关
        }
        aiHandle = AiHelper.getInst().start(AbilityConstant.ESR_ID, paramBuilder.build(), null)
        if (aiHandle?.code != AbilityConstant.ABILITY_SUCCESS_CODE) {
            ret = aiHandle?.code ?: AbilityConstant.ABILITY_CUSTOM_UNKNOWN_CODE
            Log.w(TAG, "open esr start error code ===> $ret")
            mainThread {
                callBack.onAbilityError(ret, Throwable("open esr start error"))
            }
            return true
        }
        return false
    }


    /**
     * 切换资源(比如发音人等等的情况下)需要卸载之前加载的资源
     */
    private fun unloadResource() {
        for (withIndex in DemoFsaEnum.values().withIndex()) {
            AiHelper.getInst().unLoadData(AbilityConstant.ESR_ID, "FSA", withIndex.index)
        }
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
    fun writeStream(language: Int, stream: InputStream) {
        if (initParams(language)) return
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
            dataBuilder.status(status)
            dataBuilder.audio("audio", audio)
            var ret = AiHelper.getInst().write(dataBuilder.build(), aiHandle)
            if (ret != AbilityConstant.ABILITY_SUCCESS_CODE) {
                //写入失败，暂停录音
                endAiHandle()
                Log.w(TAG, "writeData is error => $ret")
            } else {
                ret = AiHelper.getInst().read(AbilityConstant.ESR_ID, aiHandle)
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
        val status = AiStatus.BEGIN
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
            val tempValue = item.value.toString(Charset.forName("GBK"))
            if (tempKey.contains("plain") || tempKey.contains("pgs")) {
                mainThread {
                    callBack.onAbilityResult("$tempKey: \n$tempValue")
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
     * 先结束会话 AiHelper.getInst().end(aiHandle)
     * 再去卸载资源  AiHelper.getInst().unLoadData(xxx)
     * 最后再去 逆初始化引擎 AiHelper.getInst().engineUnInit(AbilityConstant.ESR_ID)
     */
    fun destroy() {
        stopAudioRecord()
        unloadResource()
        engineUnInit()
    }
}