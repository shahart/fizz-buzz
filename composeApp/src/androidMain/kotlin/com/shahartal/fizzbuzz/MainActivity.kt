package com.shahartal.fizzbuzz

import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isWatch = packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
        setContent { App(compact = isWatch) }
    }
}
