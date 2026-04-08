package com.holderzone.hardware.cabinet.print

import com.bjw.bean.ComBean
import com.bjw.utils.FuncUtil
import com.bjw.utils.SerialHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 通用 TSPL 串口打印适配层。
 *
 * 负责维护打印串口连接、发送打印命令、监听打印机状态反馈，
 * 并把底层回包转换成 SDK 内部统一的 [PrinterState] / [PrintResult]。
 */
open class CommonTSPLPrintProvide : SerialHelper() {

    private val isConnected = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(PrinterState.UNKNOWN)

    val state: StateFlow<PrinterState> = mutableState.asStateFlow()

    fun connect(port: String, baudRate: Int): Boolean {
        if (isConnected.get()) {
            return true
        }
        return try {
            this.port = port
            this.baudRate = baudRate
            open()
            isConnected.set(true)
            true
        } catch (_: Throwable) {
            isConnected.set(false)
            false
        }
    }

    fun disconnect() {
        try {
            close()
        } catch (_: IOException) {
        } finally {
            isConnected.set(false)
        }
    }

    protected fun sendCommand(command: ByteArray): Boolean {
        if (!isConnected.get()) {
            return false
        }
        // 打印口严格按串口发送，不在这里做重试，交由上层根据结果决定是否重打。
        send(command)
        return true
    }

    suspend fun print(command: ByteArray): PrintResult {
        return if (sendCommand(command)) {
            PrintResult.Success
        } else {
            PrintResult.Failed(PrintFailure.SendFailed)
        }
    }

    suspend fun printAndAwaitCompletion(
        command: ByteArray,
        timeoutMs: Long = 10_000L,
    ): PrintResult = coroutineScope {
        if (!isConnected.get()) {
            return@coroutineScope PrintResult.Failed(PrintFailure.NotConnected)
        }
        if (!sendCommand(command)) {
            return@coroutineScope PrintResult.Failed(PrintFailure.SendFailed)
        }
        val result = CompletableDeferred<PrintResult>()
        val job: Job = launch {
            // 这里监听打印机状态变化，等待 ready / 缺纸 / 开盖等明确反馈。
            state.collect { current ->
                when (current) {
                    PrinterState.READY -> if (!result.isCompleted) result.complete(PrintResult.Success)
                    PrinterState.PAPER_OUT -> if (!result.isCompleted) {
                        result.complete(PrintResult.Failed(PrintFailure.PaperOut))
                    }

                    PrinterState.HEAD_OPEN -> if (!result.isCompleted) {
                        result.complete(PrintResult.Failed(PrintFailure.HeadOpen))
                    }

                    PrinterState.ERROR -> if (!result.isCompleted) {
                        result.complete(PrintResult.Failed(PrintFailure.Error))
                    }

                    else -> Unit
                }
            }
        }
        try {
            withTimeout(timeoutMs) {
                result.await()
            }
        } catch (_: Throwable) {
            PrintResult.Failed(PrintFailure.Timeout)
        } finally {
            job.cancel()
        }
    }

    override fun onDataReceived(data: ComBean?) {
        val bytes = data?.bRec ?: return
        mutableState.value = analysisByteArray(bytes)
    }

    /**
     * 解析打印机状态字节。
     *
     * 默认实现兼容普通 TSPL 状态码，Star 打印机会在子类里覆盖为自己的协议。
     */
    protected open fun analysisByteArray(byteArray: ByteArray): PrinterState {
        return when (FuncUtil.ByteArrToHex(byteArray)) {
            "00" -> PrinterState.READY
            "04" -> PrinterState.BUSY
            "20" -> PrinterState.PAPER_OUT
            "05" -> PrinterState.ERROR
            "12" -> PrinterState.HEAD_OPEN
            else -> PrinterState.UNKNOWN
        }
    }
}

/**
 * 打印机状态枚举。
 */
enum class PrinterState {
    READY,
    BUSY,
    PAPER_OUT,
    ERROR,
    HEAD_OPEN,
    UNKNOWN,
}

/**
 * 打印执行结果。
 */
sealed class PrintResult {
    data object Success : PrintResult()

    data class Failed(val reason: PrintFailure) : PrintResult()
}

/**
 * 打印失败原因。
 */
enum class PrintFailure(val message: String) {
    NotConnected("Printer is not connected."),
    SendFailed("Failed to send printer command."),
    Timeout("Printer response timed out."),
    PaperOut("Printer is out of paper."),
    HeadOpen("Printer head is open."),
    Error("Printer reported an error."),
}
