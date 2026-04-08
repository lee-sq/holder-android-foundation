package com.holderzone.logger

import android.app.Application
import com.yuu.android.component.logbook.Logbook
import com.yuu.android.component.logbook.config.LogbookConfig
import com.yuu.android.component.logbook.config.LoggerConfig
import com.yuu.android.component.logbook.strategy.LogbookStrategyFile
import com.yuu.android.component.logbook.strategy.LogbookStrategyServer

object logger {

    var hasInit = false

    fun init(application: Application, config: LoggerInitConfig) {
        val builder = LogbookConfig.Builder()
            .setLoggerConfig(
                LoggerConfig(
                    isShowThreadInfo = config.showThreadInfo,
                    methodCount = config.methodCount,
                )
            )

        if (config.enableFileStrategy && !config.fileDir.isNullOrBlank()) {
            builder.addLogbookStrategy(LogbookStrategyFile(config.fileDir))
        }

        if (config.enableServerStrategy && !config.serverUrl.isNullOrBlank()) {
            builder.addLogbookStrategy(LogbookStrategyServer(config.serverUrl))
        }

        val logbookConfig = builder.build()
        Logbook.init(application, logbookConfig)
        hasInit = true
    }

    fun t(tag: String?): logger {
        Logbook.t(tag)
        return this
    }

    fun label(label: String?): logger {
        Logbook.label(label)
        return this
    }

    fun d(message: String, vararg args: Any?) {
        Logbook.d(message, args)
    }

    fun i(message: String, vararg args: Any?) {
        Logbook.i(message, args)
    }

    fun w(message: String, vararg args: Any?) {
        Logbook.w(message, args)
    }

    fun e(message: String, vararg args: Any?) {
        Logbook.e(message, args)
    }

    fun e(message: String, throwable: Throwable, vararg args: Any?) {
        Logbook.e(message, throwable, args)
    }

    fun v(message: String, vararg args: Any?) {
        Logbook.e(message, args)
    }

    fun json(json: String) {
        Logbook.json(json)
    }

    fun xml(xml: String) {
        Logbook.xml(xml)
    }

}
