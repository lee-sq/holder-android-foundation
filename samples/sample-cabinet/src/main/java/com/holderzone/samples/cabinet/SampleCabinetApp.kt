package com.holderzone.samples.cabinet

import android.app.Application
import com.holderzone.logger.LoggerInitConfig
import com.holderzone.logger.logger
import com.holderzone.utils.storage.MMKVUtils
import com.holderzone.utils.storage.StringResHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SampleCabinetApp : Application() {

    override fun onCreate() {
        super.onCreate()
        MMKVUtils.init(this)
        StringResHelper.init(this)
        logger.init(
            application = this,
            config = LoggerInitConfig(
                fileDir = externalCacheDir?.path
            )
        )
        logger.i("sample-cabinet initialized")
    }
}
