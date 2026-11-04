package com.shahartal.fizzbuzz

import android.os.Bundle
import android.content.pm.PackageManager
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    var arrowKeyHandler: ((Int) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeAndroidSound(applicationContext)
        val isWatch = packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
        setContent { App(compact = isWatch, sessionFactory = sessionFactoryOverride) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && arrowKeyHandler?.invoke(event.keyCode) == true) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        @Volatile
        var sessionFactoryOverride: GameSessionFactory? = null
    }
}
