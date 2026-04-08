package com.holderzone.samples.camerasdk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.holderzone.samples.camerasdk.ui.SampleCameraSdkRoot

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleCameraSdkRoot()
        }
    }
}
