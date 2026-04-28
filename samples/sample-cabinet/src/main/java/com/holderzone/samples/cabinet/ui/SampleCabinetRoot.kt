package com.holderzone.samples.cabinet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.holderzone.hardware.cabinet.CabinetVendor
import com.holderzone.widget.dialog.AnyPopDialogProperties
import com.holderzone.widget.dialog.DirectionState
import com.holderzone.widget.dialog.HolderzoneDialog
import com.holderzone.widget.toast.CommonToastHost

/**
 * Cabinet SDK sample 根页面。
 *
 * 这个页面只展示最小接入链路，不引入任何真实业务流程。
 */
@Composable
fun SampleCabinetRoot(
    viewModel: MainViewModel = hiltViewModel(),
) {
    val cabinetState by viewModel.stateSummary.collectAsStateWithLifecycle()
    val selectedVendor by viewModel.selectedVendor.collectAsStateWithLifecycle()
    val lastAction by viewModel.lastAction.collectAsStateWithLifecycle()
    val lastDoorSnapshot by viewModel.lastDoorSnapshot.collectAsStateWithLifecycle()
    val lastWeightReading by viewModel.lastWeightReading.collectAsStateWithLifecycle()
    val lastTemperatureReading by viewModel.lastTemperatureReading.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF4F7FB),
                            Color(0xFFFFFFFF)
                        )
                    )
                )
        ) {
            // 页面主体只负责状态展示和动作分发，toast 统一交给 CommonToastHost。
            HomeScreen(
                cabinetState = cabinetState,
                selectedVendor = selectedVendor,
                lastAction = lastAction,
                lastDoorSnapshot = lastDoorSnapshot,
                lastWeightReading = lastWeightReading,
                lastTemperatureReading = lastTemperatureReading,
                capabilitySummary = buildList {
                    if (capabilities.openDoor) add("openDoor")
                    if (capabilities.readWeight) add("readWeight")
                    if (capabilities.zeroScale) add("zeroScale")
                    if (capabilities.calibrateScale) add("calibrateScale")
                    if (capabilities.printLabel) add("printLabel")
                    if (capabilities.printRawBytes) add("printRawBytes")
                    if (capabilities.readTemperature) add("readTemperature")
                    if (capabilities.setTargetTemperature) add("setTargetTemperature")
                }.ifEmpty { listOf("暂无") }.joinToString(),
                onStartAuto = viewModel::startAuto,
                onStartStar = { viewModel.startVendor(CabinetVendor.STAR) },
                onStartJwSdk = { viewModel.startVendor(CabinetVendor.JW_SDK) },
                onStartJwSerial = { viewModel.startVendor(CabinetVendor.JW_SERIAL) },
                onOpenDoor = viewModel::openDoorOne,
                onReadWeight = viewModel::readWeightOnce,
                onPrintLabel = viewModel::printSampleLabel,
                onStopCabinet = viewModel::stopCabinet,
            )

            CommonToastHost()
        }
    }
}

@Composable
private fun HomeScreen(
    cabinetState: String,
    selectedVendor: String,
    lastAction: String,
    lastDoorSnapshot: String,
    lastWeightReading: String,
    lastTemperatureReading: String,
    capabilitySummary: String,
    onStartAuto: () -> Unit,
    onStartStar: () -> Unit,
    onStartJwSdk: () -> Unit,
    onStartJwSerial: () -> Unit,
    onOpenDoor: () -> Unit,
    onReadWeight: () -> Unit,
    onPrintLabel: () -> Unit,
    onStopCabinet: () -> Unit,
) {
    var dialogDirection by remember { mutableStateOf<DirectionState?>(null) }
    var isActiveClose by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Cabinet SDK Sample",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "这个 sample 只演示 CabinetFacade 的自动探测、显式厂商选择、开门、读重、打印和关闭。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            // 集中展示 SDK 运行状态，方便验证当前驱动是否已经真正跑起来。
            InfoCard(
                title = "运行状态",
                values = listOf(
                    "State: $cabinetState",
                    "Selected Vendor: $selectedVendor",
                    "Capabilities: $capabilitySummary",
                    "Last Action: $lastAction",
                    "Door Snapshot: $lastDoorSnapshot",
                    "Weight: $lastWeightReading",
                    "Temperature: $lastTemperatureReading",
                )
            )
        }
        item {
            // 启动方式单独成卡片，便于验证自动探测和显式厂商指定。
            ActionCard(
                title = "启动方式",
                actions = listOf(
                    ActionItem("Auto Start", onStartAuto),
                    ActionItem("Force Star", onStartStar),
                    ActionItem("Force JW SDK", onStartJwSdk),
                    ActionItem("Force JW Serial", onStartJwSerial),
                )
            )
        }
        item {
            // 动作卡片只放最小能力验证：开门、读重、打印、停止。
            ActionCard(
                title = "设备动作",
                actions = listOf(
                    ActionItem("Open Door 1", onOpenDoor),
                    ActionItem("Read Weight", onReadWeight),
                    ActionItem("Print Label", onPrintLabel),
                    ActionItem("Stop SDK", onStopCabinet),
                )
            )
        }
        item {
            ActionCard(
                title = "Dialog 动画测试",
                actions = listOf(
                    ActionItem("Bottom Dialog") {
                        isActiveClose = false
                        dialogDirection = DirectionState.BOTTOM
                    },
                    ActionItem("Center Dialog") {
                        isActiveClose = false
                        dialogDirection = DirectionState.CENTER
                    },
                )
            )
        }
    }

    val currentDialogDirection = dialogDirection
    if (currentDialogDirection != null) {
        HolderzoneDialog(
            isActiveClose = isActiveClose,
            properties = AnyPopDialogProperties(
                direction = currentDialogDirection,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                backgroundDimEnabled = true,
                durationMillis = 250,
            ),
            onDismiss = {
                isActiveClose = false
                dialogDirection = null
            }
        ) {
            DialogAnimationPreview(
                direction = currentDialogDirection,
                onAnimatedClose = { isActiveClose = true }
            )
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    values: List<String>,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            values.forEach { value ->
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionCard(
    title: String,
    actions: List<ActionItem>,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                actions.forEach { action ->
                    Button(onClick = action.onClick) {
                        Text(action.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogAnimationPreview(
    direction: DirectionState,
    onAnimatedClose: () -> Unit,
) {
    val isBottomSheet = direction == DirectionState.BOTTOM
    val shape = if (isBottomSheet) {
        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    } else {
        RoundedCornerShape(28.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isBottomSheet) {
                    Modifier.navigationBarsPadding()
                } else {
                    Modifier.padding(horizontal = 20.dp)
                }
            )
    ) {
        Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (isBottomSheet) {
                        "Bottom HolderzoneDialog"
                    } else {
                        "Center HolderzoneDialog"
                    },
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "点击黑色遮罩、系统返回键，或者下方的“主动关闭”按钮，观察遮罩是否只做短暂的渐隐渐显。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "当前方向: ${direction.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = onAnimatedClose) {
                        Text("主动关闭")
                    }
                }
            }
    }
}

/**
 * sample 页面按钮模型。
 */
private data class ActionItem(
    val label: String,
    val onClick: () -> Unit,
)
