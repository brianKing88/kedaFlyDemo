package com.iflytek.aikitdemo.tool

import android.os.Build
import android.os.Debug
import androidx.annotation.RequiresApi


/**
 * 定义内存状态枚举
 */
enum class MemoryIdentifier(val desc: String) {
    TOTAL("Total memory usage in KB"),
    JAVA_HEAP("The private Java Heap usage in KB"),
    NATIVE_HEAP("The private Native Heap usage in KB"),
    CODE_STATIC("The memory usage for static code and resources in KB"),
    STACK("The stack memory usage in KB"),
    GRAPHICS("The graphics memory usage in KB"),
    SYSTEM("Shared and system memory usage in KB"),
    OTHER("Other private memory usage in KB"),
}

/**
 * Room内存状态检测工具
 *
 * 使用:
 *   a) 获取整个内存信息:
 *     for ((id, usage) in MemoryStats.snapshot()) {
 *       ....
 *     }
 *   b) 指定内存信息:
 *     val usage = MemoryStats.stat(TOTAL, JAVA_HEAP, NATIVE_HEAP)
 *     ....
 */
object MemoryStats {
    var pipe: () -> Map<MemoryIdentifier, Long> = DEFAULT_PIPE

    fun snapshot(): Map<MemoryIdentifier, Long> = pipe()

    fun stat(vararg ids: MemoryIdentifier): List<Long> =
        snapshot().run { ids.map { this[it] ?: 0L } }
}

private val DEFAULT_PIPE = { emptyMap<MemoryIdentifier, Long>() }

@RequiresApi(Build.VERSION_CODES.M)
fun Debug.MemoryInfo.getMemoryStatLong(name: String): Long =
    this.getMemoryStat(name)?.toLongOrNull() ?: 0L

fun connectMemoryStatsPipe() {
    MemoryStats.pipe = {
        with(Debug.MemoryInfo().apply { Debug.getMemoryInfo(this) }) {
            mapOf(
                MemoryIdentifier.TOTAL to getMemoryStatLong("summary.total-pss"),
                MemoryIdentifier.JAVA_HEAP to getMemoryStatLong("summary.java-heap"),
                MemoryIdentifier.NATIVE_HEAP to getMemoryStatLong("summary.native-heap"),
                MemoryIdentifier.CODE_STATIC to getMemoryStatLong("summary.code"),
                MemoryIdentifier.STACK to getMemoryStatLong("summary.stack"),
                MemoryIdentifier.GRAPHICS to getMemoryStatLong("summary.graphics"),
                MemoryIdentifier.SYSTEM to getMemoryStatLong("summary.system"),
                MemoryIdentifier.OTHER to getMemoryStatLong("summary.private-other")
            )
        }
    }
}