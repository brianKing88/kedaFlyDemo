package com.iflytek.aikitdemo.ability.wms_demo

import android.util.Log
import com.iflytek.aikit.core.AiEvent
import com.iflytek.aikit.core.AiHandle
import com.iflytek.aikit.core.AiHelper
import com.iflytek.aikit.core.AiListener
import com.iflytek.aikit.core.AiRequest
import com.iflytek.aikit.core.AiResponse
import com.iflytek.aikitdemo.MyApp
import com.iflytek.aikitdemo.ability.AbilityCallback
import com.iflytek.aikitdemo.ability.AbilityConstant
import com.iflytek.aikitdemo.media.PcmAudioPlayer
import com.iflytek.aikitdemo.tool.mainThread
import okio.BufferedSink
import okio.appendingSink
import okio.buffer
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * @Desc: 代码简单示例-语音合成辅助类
 * @Author leon
 * @Date 2023/2/27-14:40
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */

/**
 * @param isXtts 是否是增强版语音合成
 */
class TTSHelper(
    private val engineId: String,
    private val callBack: AbilityCallback,
    private val pcmPlayerCallback: PcmAudioPlayer.PcmPlayerListener
) : AiListener {
    companion object {
        private val TAG = this::class.java.simpleName
    }

    private val audioPlayer: PcmAudioPlayer by lazy { PcmAudioPlayer(MyApp.CONTEXT) }

    private var ttsParamsMap: MutableMap<String, Any> = mutableMapOf()

    //会话对象
    private var aiHandle: AiHandle? = null

    private val totalPercent = AtomicInteger(100)

    private var pcmFile: File

    private var cacheArray: ByteArray? = null
    private var lockArray = ByteArray(0)

    init {
        //创建合成的PCM文件
        pcmFile = createNewFile()
        //能力回调
        AiHelper.getInst().registerListener(engineId, this)
    }

    private fun createNewFile(): File {
        return File(MyApp.CONTEXT.externalCacheDir, "${System.currentTimeMillis()}.pcm").apply {
            delete()
        }
    }

    /**
     * 获取合成PCM数据缓存
     */
    private fun getCacheArray(): ByteArray? {
        synchronized(lockArray) { return cacheArray }
    }

    /**
     * 设置合成PCM数据缓存
     */
    private fun setCacheArray(cacheArray: ByteArray?) {
        synchronized(lockArray) { this.cacheArray = cacheArray }
    }

    /**
     * 设置发音人
     */
    fun setVCN(vcn: String) {
        Log.i(TAG, "设置发音人==>${vcn}")
        ttsParamsMap["vcn"] = vcn
        if (engineId == AbilityConstant.TTS_ID) return
        when (vcn) {
            "xiaoyan", "xiaofeng" -> {
                ttsParamsMap["language"] = 1
            }

            "catherine" -> {
                ttsParamsMap["language"] = 2
            }

            else -> {}
        }
    }

    /**
     * 设置发音人语速
     */
    fun setSpeed(speed: Int) {
        Log.i(TAG, "设置发音人语速==>${speed}")
        ttsParamsMap["speed"] = speed
    }

    /**
     * 设置发音人音调
     */
    fun setPitch(pitch: Int) {
        Log.i(TAG, "设置发音人音调==>${pitch}")
        ttsParamsMap["pitch"] = pitch
    }

    /**
     * 设置发音人音量
     */
    fun setVolume(volume: Int) {
        Log.i(TAG, "设置发音人音量==>${volume}")
        ttsParamsMap["volume"] = volume
    }


    /**
     * 开始合成并播放语音
     */
    fun startSpeaking(text: String) {
        var ret: Int = -1
        ret = AiHelper.getInst().engineInit(engineId)
        if (ret != AbilityConstant.ABILITY_SUCCESS_CODE) {
            Log.w(TAG, "open ivw error code ===> $ret")
            mainThread {
                callBack.onAbilityError(ret, Throwable("引擎初始化失败"))
            }
            return
        }
        setCacheArray(null)
        totalPercent.set(text.length)
        if (aiHandle != null) {
            AiHelper.getInst().end(aiHandle)
        }
        val paramBuilder = AiRequest.builder().param("rdn", 0)
            .param("reg", 0)
            .param("textEncoding", "UTF-8")  //可选参数，文本编码格式，默认为65001，UTF8格式

        for (mutableEntry in ttsParamsMap) {
            when (mutableEntry.value) {
                is Int -> {
                    paramBuilder.param(mutableEntry.key, mutableEntry.value as Int)
                }

                else -> {
                    paramBuilder.param(mutableEntry.key, "${mutableEntry.value}")
                }
            }
        }
        //启动会话
        aiHandle = AiHelper.getInst().start(engineId, paramBuilder.build(), null)
        if (aiHandle?.code != AbilityConstant.ABILITY_SUCCESS_CODE) {
            Log.w(TAG, "启动会话失败")
            callBack.onAbilityError(
                aiHandle?.code ?: AbilityConstant.ABILITY_CUSTOM_UNKNOWN_CODE,
                Throwable("启动会话失败")
            )
            return
        }
        // 构建写入数据
        val dataBuilder = AiRequest.builder().apply {
            text("text", text)
        }
        // 写入数据
        ret = AiHelper.getInst().write(dataBuilder.build(), aiHandle)
        if (ret != AbilityConstant.ABILITY_SUCCESS_CODE) {
            Log.w(TAG, "合成写入数据失败")
            callBack.onAbilityError(ret, Throwable("合成写入数据失败"))
            return
        }
        Log.w(TAG, "合成写入成功")
    }

    /**
     * 能力输出回调
     * @param handleID  会话ID
     * @param usrContext  用户自定义标识
     * @param responseData List<AiResponse> 是 能力执行结果
     */
    override fun onResult(
        handleID: Int,
        responseData: MutableList<AiResponse>?,
        usrContext: Any?
    ) {
        val list = responseData ?: mutableListOf()
        if (list.isEmpty()) return
        for (aiResponse in list) {
            val bytes = aiResponse.value ?: continue
//            savePcm(pcmFile, bytes)
            if (cacheArray == null) {
                cacheArray = bytes
            } else {
                val resBytes = ByteArray((cacheArray?.count() ?: 0) + bytes.count())
                cacheArray?.let {
                    System.arraycopy(it, 0, resBytes, 0, (cacheArray?.count() ?: 0))
                    System.arraycopy(
                        bytes,
                        0,
                        resBytes,
                        (cacheArray?.count() ?: 0),
                        bytes.count()
                    )
                }
                cacheArray = resBytes
//            audioPlayer.writeMemFile(bytes)
            }
        }
    }

    private fun savePcm(file: File?, data: ByteArray?): Boolean {
        if (file == null || data == null) {
            return false
        }
        try {
            if (!file.exists()) {
                file.createNewFile()
            }
            val sink: BufferedSink = file.appendingSink().buffer()
            sink.write(data)
            sink.flush()
            sink.close()
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * 能力输出回调
     * @param handleID 会话ID
     * @param event  0=未知;1=开始;2=结束;3=超时;4=进度
     * @param usrContext Object 用户自定义标识
     * @param eventData List<AiResponse>  事件消息数据
     */
    override fun onEvent(
        handleID: Int,
        event: Int,
        eventData: MutableList<AiResponse>?,
        usrContext: Any?
    ) {
        when (event) {
            AiEvent.EVENT_START.value -> {
                //引擎计算开始
                mainThread {
                    callBack.onAbilityBegin()
                }
                audioPlayer.prepareAudio {
                    Log.i(TAG, "开始播放")
                }
            }

            AiEvent.EVENT_PROGRESS.value -> {
                //引擎计算中
            }

            AiEvent.EVENT_END.value -> {
                //引擎计算结束
                mainThread {
                    callBack.onAbilityEnd()
                }
                getCacheArray()?.let { array ->
                    audioPlayer.writeMemFile(array)
                }
                audioPlayer.play(totalPercent.get(), pcmPlayerCallback)
            }

            AiEvent.EVENT_TIMEOUT.value -> {
                mainThread {
                    //引擎超时
                    callBack.onAbilityError(
                        AbilityConstant.ABILITY_CUSTOM_UNKNOWN_CODE,
                        Throwable("引擎超时")
                    )
                }
            }

            else -> {

            }
        }
    }

    fun pause() {
        audioPlayer.pause()
    }

    fun resume() {
        audioPlayer.resume()
    }

    fun stop() {
        aiHandle?.let {
            AiHelper.getInst().end(it)
        }
        aiHandle = null
        pcmFile = createNewFile()
        audioPlayer.stop()
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
        callBack.onAbilityError(err, Throwable(msg ?: "能力输出失败"))
    }


    /**
     * 所有能力在退出的时候需要手动去释放会话
     * AiHelper.getInst().end(aiHandle)
     */
    fun destroy() {
        stop()
        audioPlayer.release()
    }

}