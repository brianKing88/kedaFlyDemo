package com.iflytek.aikitdemo.tool

import android.content.ContentResolver
import android.net.Uri
import com.iflytek.aikitdemo.MyApp
import java.io.InputStream
import java.io.OutputStream

inline val contentResolver: ContentResolver get() = MyApp.CONTEXT.contentResolver

inline fun <R> Uri.openInputStream(crossinline block: (InputStream) -> R): R? =
    contentResolver.openInputStream(this)?.use(block)

inline fun <R> Uri.openOutputStream(crossinline block: (OutputStream) -> R): R? =
    contentResolver.openOutputStream(this)?.use(block)

