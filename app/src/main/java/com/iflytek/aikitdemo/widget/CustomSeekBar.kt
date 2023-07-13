package com.iflytek.aikitdemo.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.appcompat.widget.AppCompatTextView
import com.iflytek.aikitdemo.R

/**
 * @Desc:
 * @Author leon
 * @Date 2023/2/24-15:36
 * Copyright 2023 iFLYTEK Inc. All Rights Reserved.
 */
class CustomSeekBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var tvLabel: AppCompatTextView
    private var seekBar: AppCompatSeekBar
    private var tvProgress: AppCompatTextView

    init {
        LayoutInflater.from(context).inflate(R.layout.custom_seekbar_layout, this)
        tvLabel = findViewById(R.id.tvLabel)
        seekBar = findViewById(R.id.seekBar)
        tvProgress = findViewById(R.id.tvProgress)
    }

    fun bindData(
        label: String,
        progress: Int,
        callback: (Int) -> Unit
    ) {
        tvLabel.text = label
        tvProgress.text = "$progress"
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                tvProgress.text = "$p1"
                callback.invoke(p1)
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }
        })
        seekBar.progress = progress
    }

    override fun setEnabled(enabled: Boolean) {
        seekBar.isEnabled = enabled
        super.setEnabled(enabled)
    }

    fun setMaxProgress(progress: Int) {
        this.seekBar.max = progress
    }

    fun getProgress(): Int {
        return seekBar.progress
    }
}