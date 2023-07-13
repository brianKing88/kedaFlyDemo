package com.iflytek.aikitdemo.tool

import java.text.SimpleDateFormat
import java.util.Date

/**
 * @Desc:
 * @Author leon
 * @Date 2023/5/12-11:43
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */
fun Long.stampToDate(): String {
    val res: String
    val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    val date = Date(this)
    res = simpleDateFormat.format(date)
    return res
}