package com.iflytek.aikitdemo

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import com.iflytek.aikitdemo.tool.connectMemoryStatsPipe
import kotlin.properties.Delegates

/**
 * @Desc:
 * @Author leon
 * @Date 2023/2/24-11:01
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */
class MyApp: Application() {

    companion object {
        var CONTEXT: Context by Delegates.notNull()
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
    override fun onCreate() {
        super.onCreate()
        CONTEXT = applicationContext
        //仅仅作为测试性能用
        connectMemoryStatsPipe()
    }
}