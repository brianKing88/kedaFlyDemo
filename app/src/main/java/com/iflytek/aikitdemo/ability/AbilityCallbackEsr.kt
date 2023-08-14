package com.iflytek.aikitdemo.ability

/**
 * @Desc: 自定义能力回调
 * @Author leon
 * @Date 2023/3/7-20:36
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */
interface AbilityCallbackEsr {

    /**
     * 开始
     */
    fun onEsrAbilityBegin()

    /**
     * 能力结果输出
     * @param result 结果
     */
    fun onEsrAbilityResult(result: String)

    /**
     * 结束
     * @param code
     * @param error
     */
    fun onEsrAbilityError(code: Int, error: Throwable?)

    /**
     * 能力结束
     */
    fun onEsrAbilityEnd()
}