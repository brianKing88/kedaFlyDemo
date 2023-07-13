package com.iflytek.aikitdemo.tool

import android.content.Context
import android.widget.Toast
/**
 * @Desc:
 * @Author leon
 * @Date 2023/2/27-14:46
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */

fun Context.toast(text: CharSequence){
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

/**
 * 分贝
 */
fun ByteArray.calculateVolume(): Int {
    var sumVolume = 0.0
    var avgVolume = 0.0
    var volume = 0
    var i = 0
    while (i < this.size) {
        val v1 = this[i].toInt() and 0xFF
        val v2 = this[i + 1].toInt() and 0xFF
        var temp = v1 + (v2 shl 8) // 小端
        if (temp >= 0x8000) {
            temp = 0xffff - temp
        }
        sumVolume += Math.abs(temp).toDouble()
        i += 2
    }
    avgVolume = sumVolume / this.size / 2
    volume = (Math.log10(1 + avgVolume) * 10).toInt()
    return volume
}