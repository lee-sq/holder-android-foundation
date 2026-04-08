package com.holderzone.utils.tts

import android.content.Context
import android.content.Intent

class VoiceUtil {

    companion object {
        /**
         * 百度启动语音
         * localText 为true时，表示云端读取，false表示本地读取
         */
        fun baiduSpeakText(text: String, localVoice: Boolean = true) {
            VoiceManager.readText(text)
        }

        /**
         * 科大讯飞启动语音
         * localText 为true时，表示云端读取，false表示本地读取
         */
        fun speakText(context: Context, text: String) {
            val intentService = Intent(context, TTSService::class.java)
            intentService.putExtra(TTSService.VOICE_TEXT, text)
            context.startService(intentService)
        }
    }
}