package com.holderzone.samples.cabinet.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.holderzone.common.toast.Toaster
import com.holderzone.hardware.cabinet.CabinetConfig
import com.holderzone.hardware.cabinet.CabinetEvent
import com.holderzone.hardware.cabinet.CabinetFacadeFactory
import com.holderzone.hardware.cabinet.CabinetLogger
import com.holderzone.hardware.cabinet.CabinetResult
import com.holderzone.hardware.cabinet.CabinetState
import com.holderzone.hardware.cabinet.CabinetVendor
import com.holderzone.hardware.cabinet.CabinetVendorPreference
import com.holderzone.hardware.cabinet.CalibrationStore
import com.holderzone.hardware.cabinet.DoorOpenRequest
import com.holderzone.hardware.cabinet.PrintRequest
import com.holderzone.logger.logger
import com.holderzone.utils.storage.MMKVUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Cabinet SDK 最小示例页面的 ViewModel。
 *
 * 这里不承载业务逻辑，只负责演示如何启动 SDK、监听状态、执行开门/读重/打印，
 * 以及如何把宿主侧的日志和标定存储桥接进 SDK。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    // sample 里每个页面只持有一个 facade 实例，页面销毁时统一 close。
    private val facade = CabinetFacadeFactory.create(appContext)
    private val calibrationStore = SampleMmkvCalibrationStore()

    val cabinetState: StateFlow<CabinetState> = facade.state
    val capabilities = facade.capabilities

    private val _lastAction = MutableStateFlow("未启动")
    val lastAction: StateFlow<String> = _lastAction.asStateFlow()

    private val _selectedVendor = MutableStateFlow("未选择")
    val selectedVendor: StateFlow<String> = _selectedVendor.asStateFlow()

    private val _lastDoorSnapshot = MutableStateFlow("暂无")
    val lastDoorSnapshot: StateFlow<String> = _lastDoorSnapshot.asStateFlow()

    private val _lastWeightReading = MutableStateFlow("暂无")
    val lastWeightReading: StateFlow<String> = _lastWeightReading.asStateFlow()

    private val _lastTemperatureReading = MutableStateFlow("暂无")
    val lastTemperatureReading: StateFlow<String> = _lastTemperatureReading.asStateFlow()

    private val _stateSummary = MutableStateFlow(CabinetState.Idle.describe())
    val stateSummary: StateFlow<String> = _stateSummary.asStateFlow()

    init {
        viewModelScope.launch {
            facade.events.collect { event ->
                when (event) {
                    is CabinetEvent.VendorSelected -> {
                        _selectedVendor.value = event.vendor.name
                        _lastAction.value = "已选中厂商 ${event.vendor.name}"
                    }

                    is CabinetEvent.DoorOpened -> {
                        _lastDoorSnapshot.value = event.snapshot.render()
                        _lastAction.value = "开门成功"
                    }

                    is CabinetEvent.DoorSnapshotChanged -> {
                        _lastDoorSnapshot.value = event.snapshot.render()
                    }

                    is CabinetEvent.WeightUpdated -> {
                        _lastWeightReading.value = "${event.reading.grams.format(2)} g"
                    }

                    is CabinetEvent.TemperatureUpdated -> {
                        _lastTemperatureReading.value = "${event.reading.celsius.format(1)} °C"
                    }

                    is CabinetEvent.PrintCompleted -> {
                        _lastAction.value = "打印完成 ${event.result.byteCount} bytes"
                    }

                    is CabinetEvent.Info -> {
                        _lastAction.value = event.message
                    }

                    is CabinetEvent.Error -> {
                        _lastAction.value = event.error.message
                    }
                }
            }
        }
        viewModelScope.launch {
            facade.state.collect { state ->
                _stateSummary.value = state.describe()
                when (state) {
                    is CabinetState.Running -> _selectedVendor.value = state.vendor.name
                    is CabinetState.Recovering -> _selectedVendor.value = state.vendor.name
                    is CabinetState.Error -> _selectedVendor.value = state.vendor?.name ?: "未选择"
                    else -> Unit
                }
            }
        }
    }

    fun startAuto() {
        startCabinet(CabinetVendorPreference.Auto)
    }

    fun startVendor(vendor: CabinetVendor) {
        startCabinet(CabinetVendorPreference.Explicit(vendor))
    }

    fun openDoorOne() {
        viewModelScope.launch(Dispatchers.IO) {
            handleResult(
                facade.door.open(DoorOpenRequest(listOf(1))),
                successMessage = { "Door 1 已打开: ${it.render()}" },
            )
        }
    }

    fun readWeightOnce() {
        viewModelScope.launch(Dispatchers.IO) {
            handleResult(
                facade.scale.read(),
                successMessage = {
                    _lastWeightReading.value = "${it.grams.format(2)} g"
                    "重量读取成功 ${it.grams.format(2)} g"
                },
            )
        }
    }

    fun printSampleLabel() {
        viewModelScope.launch(Dispatchers.IO) {
            val request = PrintRequest.Label(
                title = "Cabinet SDK",
                lines = listOf(
                    "Vendor: ${selectedVendor.value}",
                    "Time: ${System.currentTimeMillis()}",
                    "Door: 1",
                ),
                qrCode = "holder-cabinet-sdk",
            )
            handleResult(
                facade.printer.print(request),
                successMessage = { "标签打印完成 ${it.byteCount} bytes" },
            )
        }
    }

    fun stopCabinet() {
        viewModelScope.launch(Dispatchers.IO) {
            handleResult(
                facade.stop(),
                successMessage = { "Cabinet SDK 已关闭" },
            )
        }
    }

    private fun startCabinet(preference: CabinetVendorPreference) {
        viewModelScope.launch(Dispatchers.IO) {
            // SDK 内部只认 CabinetLogger，这里把它桥接到宿主现有 logger。
            val loggerBridge = object : CabinetLogger {
                override fun debug(tag: String, message: String) {
                    logger.t(tag).d(message)
                }

                override fun info(tag: String, message: String) {
                    logger.t(tag).i(message)
                }

                override fun warn(tag: String, message: String) {
                    logger.t(tag).w(message)
                }

                override fun error(tag: String, message: String, throwable: Throwable?) {
                    if (throwable != null) {
                        logger.t(tag).e(message, throwable)
                    } else {
                        logger.t(tag).e(message)
                    }
                }
            }
            handleResult(
                facade.start(
                    CabinetConfig(
                        vendorPreference = preference,
                        // sample 用 MMKV 持久化标定参数，方便重启后继续沿用。
                        calibrationStore = calibrationStore,
                        logger = loggerBridge,
                    )
                ),
                successMessage = {
                    when (preference) {
                        is CabinetVendorPreference.Auto -> "Auto 模式启动成功"
                        is CabinetVendorPreference.Explicit -> "显式启动 ${preference.vendor.name} 成功"
                    }
                },
            )
        }
    }

    private suspend fun <T> handleResult(
        result: CabinetResult<T>,
        successMessage: (T) -> String,
    ) {
        when (result) {
            is CabinetResult.Ok -> {
                val message = successMessage(result.value)
                _lastAction.value = message
                Toaster.showSuccess(message)
            }

            is CabinetResult.Err -> {
                val message = result.error.message
                _lastAction.value = message
                Toaster.showError(message)
            }
        }
    }

    override fun onCleared() {
        // facade 持有串口、监听器和驱动资源，页面销毁时必须显式释放。
        facade.close()
        super.onCleared()
    }
}

/**
 * sample 专用 MMKV 标定存储实现。
 *
 * 生产环境可以替换成 DataStore、Room 或任意其他持久化方案，
 * SDK 本身不关心底层存储技术。
 */
private class SampleMmkvCalibrationStore : CalibrationStore {
    override suspend fun readWeightSlope(vendor: CabinetVendor): Double? {
        val key = key(vendor)
        return if (MMKVUtils.containsKey(key)) MMKVUtils.getDouble(key, 1.0) else null
    }

    override suspend fun writeWeightSlope(vendor: CabinetVendor, slope: Double) {
        MMKVUtils.putDouble(key(vendor), slope)
    }

    override suspend fun clear(vendor: CabinetVendor) {
        MMKVUtils.remove(key(vendor))
    }

    private fun key(vendor: CabinetVendor): String = "sample:cabinet:calibration:${vendor.name}"
}

/**
 * 把 SDK 状态转换成更适合 sample 页面展示的字符串。
 */
private fun CabinetState.describe(): String {
    return when (this) {
        CabinetState.Idle -> "Idle"
        is CabinetState.Starting -> "Starting(${config.vendorPreference})"
        is CabinetState.Running -> "Running(${vendor.name})"
        is CabinetState.Recovering -> "Recovering(${vendor.name}, attempt=$attempt)"
        is CabinetState.Stopped -> "Stopped(lastVendor=${lastVendor?.name ?: "none"})"
        is CabinetState.Error -> "Error(${vendor?.name ?: "none"}: ${error.message})"
        CabinetState.Closed -> "Closed"
    }
}

/**
 * 简单格式化浮点数，避免 sample 页面直接显示过长小数。
 */
private fun Double.format(scale: Int): String = "%.${scale}f".format(this)

/**
 * 把柜门快照转成人类可读文本。
 */
private fun com.holderzone.hardware.cabinet.DoorStateSnapshot.render(): String {
    return "requested=${requestedDoors.sorted()} opened=${openedDoors.sorted()}"
}
