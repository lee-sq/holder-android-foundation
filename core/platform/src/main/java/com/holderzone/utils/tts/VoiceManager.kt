package com.holderzone.utils.tts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.baidu.tts.chainofresponsibility.logger.LoggerProxy
import com.baidu.tts.client.SpeechError
import com.baidu.tts.client.SpeechSynthesizer
import com.baidu.tts.client.SpeechSynthesizerListener
import com.baidu.tts.client.TtsMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

/**
 * 百度语音服务类
 */
object VoiceManager {
    /**资源目录*/
    const val TTS_SOURCE_DIR = "baiduTTS"

    /**目标目录*/
    const val TTS_TARGET_DIR = "/baiduTTS"

    /**文字KEY*/
    const val VOICE_TEXT = "VOICE_TEXT"

    /**语音离线或者在线*/
    const val VOICE_LOCAL = "VOICE_TYPE"

    /**百度参数appId*/
    const val appId = "29249783"

    /**百度参数appKey*/
    const val appKey = "WFYDAwFv54H8AcvFHx2SspEl"

    /**百度参数secretKey*/
    const val secretKey = "ukIG1GjaGvzFCcrl0Gp5eONEXVqNnQCS"

    // ================选择TtsMode.ONLINE  不需要设置以下参数; 选择TtsMode.MIX 需要设置下面2个离线资源文件的路径
    private const val TEMP_DIR = "baiduTTS" // 重要！请手动将assets目录下的3个dat 文件复制到该目录

    // 请确保该PATH下有这个文件
    private const val TEXT_FILENAME =
        "$TEMP_DIR/bd_etts_common_text_txt_all_mand_eng_middle_big_v3.4.2_20210319.dat"

    //最符合在线语音的女声
    private const val MODEL_FILENAME =
        "$TEMP_DIR/bd_etts_common_speech_f7_mand_eng_high_am-mgc_v3.6.0_20190117.dat"
    // ===============初始化参数设置完毕，更多合成参数请至getParams()方法中设置 =================

    /**语音文本队列*/
    val textList: MutableList<String> = mutableListOf()

    /**当前是否在读*/
    var isSpeaking = false

    /**是否完成初始化*/
    var isInit = false

    /**只检查一次文件*/
    var isFirstCheckVoiceData = true

    /**模式*/
    private val ttsMode = TtsMode.ONLINE

    private var mSpeechSynthesizer: SpeechSynthesizer? = null

    /**协程作用域，用于管理协程生命周期*/
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)


    public fun init(context: Context) {
        initPermission(context)
        initTTsInIOThread(context)
    }


    private fun initTTsInIOThread(context: Context) {
        coroutineScope.launch {
            initTTs(context)  //初始化完成
            launch(Dispatchers.Main) {
                isInit = true
                //若当前有值，且未读，则开始
                if (!isSpeaking && textList.isNotEmpty()) readNext()
            }
        }
    }

    fun readText(content: String) {
        textList.add(content)
        //若当前有值，且已经初始化完成，则开始读
        if (!isSpeaking && isInit) readNext()
    }


    /**
     *该方法一定在新线程中调用，并且该线程不能结束。具体可以参考NonBlockSyntherizer的写法
     */
    private fun initTTs(context: Context) {
        Log.d("Voice", "voiceUtils,开始设置语音，当前线程：" + Thread.currentThread().name)
        LoggerProxy.printable(true) // 日志打印在logcat中
        val isMix = ttsMode == TtsMode.MIX
        var isSuccess: Boolean
        if (isMix) {
            // 检查2个离线资源是否可读
            isSuccess = checkOfflineResources(context)
            if (!isSuccess) {
                Log.d("Voice", "voiceUtils,线离不可用！直接退出")
                //手动再导入一次 成功后再次调用initTTs
                if (isFirstCheckVoiceData) initVoice(context)
                return
            } else {
                print("离线资源存在并且可读, 目录：$TEMP_DIR")
            }
        }
        // 日志更新在UI中，可以换成MessageListener，在logcat中查看日志
        val listener = mTtsListener

        // 1. 获取实例
        mSpeechSynthesizer = SpeechSynthesizer.getInstance()
        mSpeechSynthesizer?.setContext(context)

        // 2. 设置listener
        mSpeechSynthesizer?.setSpeechSynthesizerListener(listener)

        // 3. 设置appId，appKey.secretKey
        var result = mSpeechSynthesizer?.setAppId(appId)
        result?.let { checkResult(it, "setAppId") }
        result = mSpeechSynthesizer?.setApiKey(appKey, secretKey)
        result?.let { checkResult(it, "setApiKey") }

        // 4. 支持离线的话，需要设置离线模型
        if (isMix) {
            // 检查离线授权文件是否下载成功，离线授权文件联网时SDK自动下载管理，有效期3年，3年后的最后一个月自动更新。
            isSuccess = checkAuth()
            if (!isSuccess) {
                return
            }
            // 文本模型文件路径 (离线引擎使用)， 注意TEXT_FILENAME必须存在并且可读
            mSpeechSynthesizer?.setParam(SpeechSynthesizer.PARAM_TTS_TEXT_MODEL_FILE, TEXT_FILENAME)
            // 声学模型文件路径 (离线引擎使用)， 注意TEXT_FILENAME必须存在并且可读
            mSpeechSynthesizer?.setParam(
                SpeechSynthesizer.PARAM_TTS_SPEECH_MODEL_FILE,
                MODEL_FILENAME
            )

        }

        // 5. 以下setParam 参数选填。不填写则默认值生效
        // 设置在线发声音人： 0 普通女声（默认） 1 普通男声 2 特别男声 3 情感男声<度逍遥> 4 情感儿童声<度丫丫>
        mSpeechSynthesizer?.setParam(SpeechSynthesizer.PARAM_SPEAKER, "0")
        // 设置合成的音量，0-9 ，默认 5
        mSpeechSynthesizer?.setParam(SpeechSynthesizer.PARAM_VOLUME, "9")
        // 设置合成的语速，0-9 ，默认 5
        mSpeechSynthesizer?.setParam(SpeechSynthesizer.PARAM_SPEED, "5")
        // 设置合成的语调，0-9 ，默认 5
        mSpeechSynthesizer?.setParam(SpeechSynthesizer.PARAM_PITCH, "5")

        mSpeechSynthesizer?.setParam(
            SpeechSynthesizer.PARAM_MIX_MODE,
            SpeechSynthesizer.MIX_MODE_HIGH_SPEED_SYNTHESIZE_WIFI
        )

        // 该参数设置为TtsMode.MIX生效。即纯在线模式不生效。
        // MIX_MODE_DEFAULT 默认 ，wifi状态下使用在线，非wifi离线。在线状态下，请求超时6s自动转离线
        // MIX_MODE_HIGH_SPEED_SYNTHESIZE_WIFI wifi状态下使用在线，非wifi离线。在线状态下， 请求超时1.2s自动转离线
        // MIX_MODE_HIGH_SPEED_NETWORK ， 3G 4G wifi状态下使用在线，其它状态离线。在线状态下，请求超时1.2s自动转离线
        // MIX_MODE_HIGH_SPEED_SYNTHESIZE, 2G 3G 4G wifi状态下使用在线，其它状态离线。在线状态下，请求超时1.2s自动转离线

        mSpeechSynthesizer?.setAudioStreamType(AudioManager.MODE_IN_CALL)

        // x. 额外 ： 自动so文件是否复制正确及上面设置的参数
        val params = HashMap<String, String>()
        // 复制下上面的 mSpeechSynthesizer.setParam参数
        // 上线时请删除AutoCheck的调用
        if (isMix) {
            params[SpeechSynthesizer.PARAM_TTS_TEXT_MODEL_FILE] = TEXT_FILENAME
            params[SpeechSynthesizer.PARAM_TTS_SPEECH_MODEL_FILE] = MODEL_FILENAME
        }

        // 6. 初始化
        result = mSpeechSynthesizer?.initTts(ttsMode)
        result?.let { checkResult(it, "initTts") }

    }

    /**
     * 检查appId ak sk 是否填写正确，另外检查官网应用内设置的包名是否与运行时的包名一致。本demo的包名定义在build.gradle文件中
     *
     * @return
     */
    private fun checkAuth(): Boolean {
        val authInfo = mSpeechSynthesizer?.auth(ttsMode)
        if (authInfo != null) {
            return if (!authInfo.isSuccess) {
                // 离线授权需要网站上的应用填写包名。定义在build.gradle中
                val errorMsg = authInfo.ttsError?.detailMessage
                print("voiceUtils 【error】鉴权失败 errorMsg=$errorMsg")
                false
            } else {
                print("voiceUtils 验证通过，离线正式授权文件存在。")
                true
            }
        }
        return false
    }

    private fun checkResult(result: Int, method: String) {
        if (result != 0) {
            Log.d("Voice", "voiceUtils,语音错误：$method")
            //  print("error code :$result method:$method, 错误码文档:http://yuyin.baidu.com/docs/tts/122 ")
        }
    }

    /**
     * 检查 TEXT_FILENAME, MODEL_FILENAME 这2个文件是否存在，不存在请自行从assets目录里手动复制
     *
     * @return
     */
    private fun checkOfflineResources(context: Context): Boolean {
        val filenames = arrayOf(
            context.getExternalFilesDir("")?.path + File.separator + TEXT_FILENAME,
            context.getExternalFilesDir("")?.path + File.separator + MODEL_FILENAME
        )
        for (path in filenames) {
            val f = File(path)
            if (!f.canRead()) {
                Log.d("Voice", "voiceUtils,文件不存在或者不可读取，请从assets目录复制同名文件到：$path")
                print("[ERROR] 文件不存在或者不可读取，请从assets目录复制同名文件到：$path")
                print("[ERROR] 初始化失败！！！")
                return false
            }
        }
        return true
    }


    /**
     * android 6.0 以上需要动态申请权限
     */
    private fun initPermission(context: Context) {
        val permissions = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_WIFI_STATE,
        )

        val toApplyList = ArrayList<String>()

        for (perm in permissions) {
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(
                    context,
                    perm
                )
            ) {
                toApplyList.add(perm)
                // 进入到这里代表没有权限.
                Log.d("Voice", "voiceUtils,没有语音权限:$perm")
            }
        }
    }

    /**
     * 读取语音
     */
    private fun readNext() {
        if (textList.isNotEmpty()) {
            val text = textList.removeAt(0)
            Log.d("Voice", "voiceUtils,执行了readNext")
            val result = mSpeechSynthesizer?.speak(text)
            result?.let { checkResult(it, "speak") }
        }
    }

    fun onDestroy() {
        // 取消所有协程
        coroutineScope.cancel()
        mSpeechSynthesizer?.let {
            it.stop()
            it.release()
            mSpeechSynthesizer = null
            print("释放资源成功")
        }
    }

    private val mTtsListener = object : SpeechSynthesizerListener {

        /**
         * 播放开始，每句播放开始都会回调
         *
         * @param utteranceId
         */
        override fun onSynthesizeStart(utteranceId: String) {
        }

        /**
         * 语音流 16K采样率 16bits编码 单声道 。
         *
         * @param utteranceId
         * @param bytes       二进制语音 ，注意可能有空data的情况，可以忽略
         * @param progress    如合成“百度语音问题”这6个字， progress肯定是从0开始，到6结束。 但progress无法和合成到第几个字对应。
         */
        fun onSynthesizeDataArrived(utteranceId: String, bytes: ByteArray, progress: Int) {
        }

        /** engineType 下版本提供。1:音频数据由离线引擎合成； 0：音频数据由在线引擎（百度服务器）合成。*/
        override fun onSynthesizeDataArrived(
            utteranceId: String?,
            bytes: ByteArray?,
            progress: Int,
            engineType: Int
        ) {
            onSynthesizeDataArrived(utteranceId!!, bytes!!, progress)
        }

        /**
         * 合成正常结束，每句合成正常结束都会回调，如果过程中出错，则回调onError，不再回调此接口
         *
         * @param utteranceId
         */
        override fun onSynthesizeFinish(utteranceId: String) {
        }

        override fun onSpeechStart(utteranceId: String) {
            isSpeaking = true
            Log.d("Voice", "voiceUtils,开始播放")
        }

        /**
         * 播放进度回调接口，分多次回调
         *
         * @param utteranceId
         * @param progress    如合成“百度语音问题”这6个字， progress肯定是从0开始，到6结束。 但progress无法保证和合成到第几个字对应。
         */
        override fun onSpeechProgressChanged(utteranceId: String, progress: Int) {
            //  Log.i(TAG, "播放进度回调, progress：" + progress + ";序列号:" + utteranceId );
        }

        /**
         * 播放正常结束，每句播放正常结束都会回调，如果过程中出错，则回调onError,不再回调此接口
         *
         * @param utteranceId
         */
        override fun onSpeechFinish(utteranceId: String) {
            if (textList.isNotEmpty()) {
                readNext()
            } else {
                isSpeaking = false
            }
            Log.d("Voice", "voiceUtils,播放结束")
        }

        /**
         * 当合成或者播放过程中出错时回调此接口
         *
         * @param utteranceId
         * @param speechError 包含错误码和错误信息
         */
        override fun onError(utteranceId: String, speechError: SpeechError) {
            Log.d("Voice", "voiceUtils,播放出错，原因：" + speechError.description)
            isSpeaking = false
        }

    }

    /**
     * 为防止用户删除了离线文件，当发现无文件时，这里再进行一次导入
     */
    private fun initVoice(context: Context) {
        isFirstCheckVoiceData = false
        FileUtils.getInstance(context).copyAssetsToSD(TTS_SOURCE_DIR, TTS_TARGET_DIR)
            .setFileOperateCallback(object : FileUtils.FileOperateCallback {
                override fun onSuccess() {
                    Log.d("Voice", "voiceUtils,导入文件成功")
                    initTTs(context)
                }

                override fun onFailed(error: String?) {
                    Log.d("Voice", "voiceUtils,导入文件失败")
                }
            })
    }

}
