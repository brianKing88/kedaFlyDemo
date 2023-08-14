package com.iflytek.aikitdemo.ability.wms_demo

/**
 * @Desc:
 * @Author leon
 * @Date 2023/3/28-10:12
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */

enum class DemoFsaEnum(val path: String) {
//    CALL("Call.txt"),
//    ALBUM("Album.txt"),
//    VIDEO("Video.txt"),
    APP("My.txt")
}

//命令词语法文件示例，对应资源里面的命令词文件App.txt
val wmsDemoFsaList = mutableListOf(
    "#FSA 1.0;\n" +
            "0\t1\t<appwant>\n" +
            ";\n" +
            "\n" +
            "<appwant>:好的|确认|确定|好|到达\n" +
            ";"
)