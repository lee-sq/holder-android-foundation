package com.holderzone.utils.tts

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import com.yuu.android.component.voice.tts.EngineService
import java.util.*

class TTSService : Service() {
    private var tts: TextToSpeech? = null
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.e("TextToSpeech", "TTService success")
        tts = TextToSpeech(this, initListener, EngineService.VOICE_SERVICE_PACKAGE_NAME)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        intent?.getStringExtra(VOICE_TEXT)?.let {
            tts?.speak(it, TextToSpeech.QUEUE_ADD, null, System.currentTimeMillis().toString())
        }
        return super.onStartCommand(intent, flags, startId)

    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
    }

    private val initListener = TextToSpeech.OnInitListener { status: Int ->
        tts?.let { tts ->
            if (status == TextToSpeech.SUCCESS) {
                if (tts.isLanguageAvailable(Locale.CHINESE) == TextToSpeech.LANG_AVAILABLE) {
                    tts.language = Locale.CHINESE
                    tts.setSpeechRate(SPEECH_RATE)
                    tts.setPitch(SPEECH_PITCH)
                } else {
                    Log.e("TextToSpeech", "current area not support chinese")
                }
            } else {
                Log.e("TTSService", "TTS init fail")
            }
        } ?: run {
            Log.e("TTSService", "TTS not Init")
        }
    }


    companion object {
        const val VOICE_TEXT = "VOICE_TEXT"  //文字KEY
        const val SPEECH_RATE = 0.9F
        const val SPEECH_PITCH = 0.9F

        public fun init(
            context: Context,
            startService: (context: Context, clazz: Class<TTSService>) -> Unit
        ) {
            EngineService.checkVoiceService(context) {
                startService(context, TTSService::class.java)
                VoiceManager.init(context)
            }
        }
    }
}