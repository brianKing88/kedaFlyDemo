package com.iflytek.aikitdemo.ability

import android.util.Log
import com.iflytek.aikit.core.AiHelper
import com.iflytek.aikitdemo.tool.stampToDate


/**
 * @Desc: 能力扩展
 * @Author leon
 * @Date 2023/5/12-11:38
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */

/**
 * 能力授权状态查询
 */
fun String.abilityAuthStatus(): String {
    val time = AiHelper.getInst().getAuthLeftTime(this)
    var authTips = ""
    authTips = if (time.code == 0) {
        if (time.leftTime == 0L) {
            "$this ===> 授权状态：永久授权"
        } else if (time.leftTime < 0) {
            "$this ===> 授权状态：已过期 剩余${time.leftTime} 秒 \n===> 授权结束时间: ${(time.expireTime * 1000).stampToDate()}"
        } else {
            "$this ===> 授权状态：授权中 剩余${time.leftTime} 秒 \n===> 授权结束时间: ${(time.expireTime * 1000).stampToDate()}"
        }
    } else {
        "$this ===> 查询失败 ret : ${time.code}"
    }
    Log.i("能力授权", "$authTips\n")
    return authTips
}