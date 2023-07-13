package com.iflytek.aikitdemo.ability.esr

/**
 * @Desc:
 * @Author leon
 * @Date 2023/3/28-10:12
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */

enum class EsrFsaEnum(val path: String) {
    CALL("Call.txt"),
    ALBUM("Album.txt"),
    VIDEO("Video.txt"),
    APP("App.txt")
}

//命令词语法文件示例，对应资源里面的命令词文件App.txt
val esrFsaList = mutableListOf(
    "#FSA 1.0;\n" +
            "0\t1\t<appwant>\n" +
            "0\t1\t-\n" +
            "1\t2\t<applaunch>\n" +
            "1\t2\t-\n" +
            "2\t3\t<applist>\n" +
            ";\n" +
            "\n" +
            "<appwant>:我想|我要|我想要|请|帮我|请帮我|给我|请给我;\n" +
            "<applaunch>:打开|启动|用|看|听|播放|设置|玩|关闭|退出;\n" +
            "\n" +
            "<applist>:帮助|\n" +
            "保险|\n" +
            "车况|\n" +
            "唱吧|\n" +
            "倒车影像|\n" +
            "导航|\n" +
            "电话|\n" +
            "电台|\n" +
            "个人中心|\n" +
            "快捷方式|\n" +
            "设置|\n" +
            "生活资讯|\n" +
            "视频|\n" +
            "手机互联|\n" +
            "输入法|\n" +
            "通知|\n" +
            "推送|\n" +
            "违章查询|\n" +
            "新闻|\n" +
            "音乐|\n" +
            "语音功能|\n" +
            "账户|\n" +
            "帮助|\n" +
            "应用名称|\n" +
            "桌面;",
    "#FSA 1.0;\n" +
            "0\t2\t<preSettings>\n" +
            "2\t3\t<sPrep>\n" +
            "2\t3\t-\n" +
            "3\t1\t<sSettings>\n" +
            "0\t4\t<preHelp>\n" +
            "0\t4\t-\n" +
            "4\t5\t<sThe>\n" +
            "4\t5\t-\n" +
            "5\t6\t<helpType>\n" +
            "6\t1\t<helpTail>\n" +
            "6\t1\t-\n" +
            "0\t7\t<helpStable>\n" +
            "7\t1\t-\n" +
            "0\t8\t<sWant>\n" +
            "0\t8\t-\n" +
            "8\t9\t<sControl>\n" +
            "8\t9\t-\n" +
            "9\t10\t<sThe>\n" +
            "9\t10\t-\n" +
            "10\t11\t<applist>\n" +
            "11\t1\t<sControl>\n" +
            "11\t1\t-\n" +
            "0\t12\t<sControl>\n" +
            "12\t13\t<applist>\n" +
            "13\t1\t<sControl>\n" +
            ";\n" +
            "<sWant>:\n" +
            "i want|\n" +
            "i want to|\n" +
            "i wanna|\n" +
            "help me|\n" +
            "can|\n" +
            "please|\n" +
            "can you|\n" +
            "i want my;\n" +
            "<sControl>:close|turn off|shut down|exit|leave|start|turn on|open|enter|launch|off|exits|down|mute|unmute|cancel|sound|mute to;\n" +
            "<applist>:app name|phone|navigation|navigation yps;\n" +
            "<preSettings>:\n" +
            "take me to|\n" +
            "open;\n" +
            "\n" +
            "<sPrep>:to;\n" +
            "\n" +
            "<sSettings>:\n" +
            "settings;\n" +
            "\n" +
            "<preHelp>:\n" +
            "how to use|\n" +
            "how can i use|\n" +
            "display how to|\n" +
            "use|\n" +
            "instructions of|\n" +
            "instructions about|\n" +
            "instructions for|\n" +
            "teach me how to check|\n" +
            "how to open;\n" +
            "\n" +
            "<sThe>:\n" +
            "the|\n" +
            "this;\n" +
            "\n" +
            "<helpType>:\n" +
            "help|\n" +
            "help type;\n" +
            "\n" +
            "<helpTail>:\n" +
            "function|\n" +
            "system;\n" +
            "\n" +
            "<helpStable>:\n" +
            "what can you do for me|\n" +
            "what should i say|\n" +
            "what can i say;"
)