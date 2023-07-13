package com.iflytek.aikitdemo

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatButton
import com.iflytek.aikitdemo.ability.IFlytekAbilityManager
import com.iflytek.aikitdemo.ability.ed.EsrEdActivity
import com.iflytek.aikitdemo.ability.ed.encn.EsrEdEnCnActivity
import com.iflytek.aikitdemo.ability.esr.EsrActivity
import com.iflytek.aikitdemo.ability.ivw.IvwActivity
import com.iflytek.aikitdemo.ability.tts.TTSActivity
import com.iflytek.aikitdemo.ability.wms_demo.WmsDemoActivity
import com.iflytek.aikitdemo.base.BaseActivity
import com.iflytek.aikitdemo.tool.toast

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBarNavigation(false)
        setContentView(R.layout.activity_main)
        activityResultLauncher.launch(
            arrayListOf(Manifest.permission.RECORD_AUDIO).apply {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                } else {
                    add(Manifest.permission.READ_MEDIA_IMAGES)
                    add(Manifest.permission.READ_MEDIA_VIDEO)
                    add(Manifest.permission.READ_MEDIA_AUDIO)
                }
            }.toTypedArray()
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
        findViewById<AppCompatButton>(R.id.btnAISound).setOnClickListener {
            TTSActivity.openTtsActivity(this, false)
        }

        findViewById<AppCompatButton>(R.id.btnXtts).setOnClickListener {
            TTSActivity.openTtsActivity(this, true)
        }

        findViewById<AppCompatButton>(R.id.btnEd).setOnClickListener {
            startActivity(Intent(this, EsrEdActivity::class.java))
        }

        findViewById<AppCompatButton>(R.id.btnEdCnen).setOnClickListener {
            startActivity(Intent(this, EsrEdEnCnActivity::class.java))
        }

        findViewById<AppCompatButton>(R.id.btnEsr).setOnClickListener {
            startActivity(Intent(this, EsrActivity::class.java))
        }

        findViewById<AppCompatButton>(R.id.btnIvw).setOnClickListener {
            startActivity(Intent(this, IvwActivity::class.java))
        }
        findViewById<AppCompatButton>(R.id.btnDemo).setOnClickListener {
            startActivity(Intent(this, WmsDemoActivity::class.java))
        }
    }

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        { permissions ->
            val allGranted = permissions.all { it.value }
            if (allGranted){
                IFlytekAbilityManager.getInstance().initializeSdk(this)
            }
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value
                if (isGranted) {
                    // Permission is granted
                } else {
                    // Permission is denied
                    toast("${permissionName}被拒绝了，请在应用设置里打开权限")
                }
            }
        }
}