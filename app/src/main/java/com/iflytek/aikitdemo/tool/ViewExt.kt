package com.iflytek.aikitdemo.tool

import android.widget.RadioGroup

/**
 * @Desc:
 * @Author leon
 * @Date 2023/3/17-10:17
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */
fun RadioGroup.setChildrenEnabled(enabled: Boolean) {
    for (i in 0 until childCount) {
        getChildAt(i).isEnabled = enabled
    }
}