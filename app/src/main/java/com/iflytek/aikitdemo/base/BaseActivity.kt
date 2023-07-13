package com.iflytek.aikitdemo.base

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.iflytek.aikitdemo.R

open class BaseActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBarNavigation()
    }

    fun actionBarNavigation(show: Boolean = true){
        supportActionBar?.apply {
            setHomeAsUpIndicator(R.drawable.baseline_arrow_back_24)
            setDisplayHomeAsUpEnabled(show)
        }
    }

    fun setTitleBar(title: CharSequence){
        actionBarNavigation(true)
        supportActionBar?.apply {
            setTitle(title)
        }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home-> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}