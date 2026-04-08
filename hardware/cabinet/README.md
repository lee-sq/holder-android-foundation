# Cabinet SDK

`hardware-cabinet` 是面向留样柜一体机的统一 SDK。它对外保留“柜门 + 称重 + 打印 + 温度”一体语义，通过一个 `CabinetFacade` 抹平 Star、JW SDK、JW Serial 三套厂商实现差异。

## 目标形态

- 对外只交付一个主 AAR: `hardware-cabinet`
- 对外根包固定为 `com.holderzone.hardware.cabinet`
- 每次调用 `CabinetFacadeFactory.create(context)` 都返回一个新的 facade 实例
- 默认支持 `Auto` 自动探测，也支持显式指定厂商
- 对外门号统一使用 `1-based`
- SDK 内部不依赖 `MMKVUtils`、`HardwareManager`、`hardware:base`

## 目录结构

```text
hardware/cabinet
  src/main/java/com/holderzone/hardware/cabinet
    core
    driver/jw/serial
    driver/jw/sdk
    driver/star
    internal
    print
  src/main/AndroidManifest.xml
  libs/jw-sdk
  libs/star
```

说明:

- `src/main/java/com/holderzone/hardware/cabinet`:
  现在是单 module 收口后的唯一源码入口
- `core`:
  `DefaultCabinetFacade`、自动探测、显式厂商选择、心跳恢复、控制器桥接
- `driver/jw/*`、`driver/star`:
  厂商驱动实现，源码都已经回收到当前 module
- `print`:
  标签渲染和串口打印适配
- `libs/jw-sdk`、`libs/star`:
  当前 module 自己持有的厂商二进制
- 共享串口底层二进制:
  已收口到仓库内部模块 `:hardware:vendor`，对业务侧不需要单独感知

## 对外 API

核心入口:

```kotlin
val facade = CabinetFacadeFactory.create(context)

val result = facade.start(
    CabinetConfig(
        vendorPreference = CabinetVendorPreference.Auto,
        calibrationStore = InMemoryCalibrationStore(),
        logger = CabinetLogger.None,
    )
)
```

门面能力:

```kotlin
interface CabinetFacade : Closeable {
    val state: StateFlow<CabinetState>
    val events: Flow<CabinetEvent>
    val capabilities: StateFlow<CabinetCapabilities>

    val door: DoorController
    val scale: ScaleController
    val printer: PrinterController
    val temperature: TemperatureController

    suspend fun start(config: CabinetConfig): CabinetResult<Unit>
    suspend fun stop(): CabinetResult<Unit>
    override fun close()
}
```

关键模型:

- `CabinetConfig`
- `CabinetVendor`
- `CabinetVendorPreference`
- `CabinetPortOverrides`
- `HeartbeatConfig`
- `CabinetCapabilities`
- `CabinetState`
- `CabinetEvent`
- `CabinetResult`
- `CabinetError`
- `DoorOpenRequest`
- `DoorStateSnapshot`
- `WeightReading`
- `PrintRequest`
- `PrintResultInfo`
- `TemperatureReading`
- `CalibrationStore`
- `CabinetLogger`

## 厂商支持矩阵

| Vendor | Door | Scale | Print | Temperature | Calibration | Set Target Temp |
| --- | --- | --- | --- | --- | --- | --- |
| `STAR` | Yes | Yes | Yes | Yes | No | No |
| `JW_SDK` | Yes | Yes | Yes | Yes | Yes | No |
| `JW_SERIAL` | Yes | Yes | Yes | Yes | Yes | Yes |

说明:

- `Auto` 探测顺序固定为 `STAR -> JW_SDK -> JW_SERIAL`
- `UVC`、`Camera1` 这类概念与 cabinet SDK 无关，不在本模块内
- 目前 `STAR` 不支持称重标定和目标温度设置

## 自动探测与显式指定

自动探测:

```kotlin
val result = facade.start(
    CabinetConfig(
        vendorPreference = CabinetVendorPreference.Auto,
    )
)
```

显式指定厂商:

```kotlin
val result = facade.start(
    CabinetConfig(
        vendorPreference = CabinetVendorPreference.Explicit(CabinetVendor.JW_SDK),
    )
)
```

端口覆盖:

```kotlin
val result = facade.start(
    CabinetConfig(
        vendorPreference = CabinetVendorPreference.Auto,
        ports = CabinetPortOverrides(
            cabinet = SerialPortEndpoint("/dev/ttyS2", 9600),
            printer = SerialPortEndpoint("/dev/ttyS3", 9600),
        ),
        doorCount = 8,
    )
)
```

建议:

- `JW` 设备优先显式传入 `doorCount`
- 现场串口不稳定时优先使用 `ports` 覆盖默认端口

## 控制器使用示例

开门:

```kotlin
when (val result = facade.door.open(DoorOpenRequest(listOf(1)))) {
    is CabinetResult.Ok -> {
        val snapshot = result.value
    }
    is CabinetResult.Err -> {
        val error = result.error
    }
}
```

读取重量:

```kotlin
when (val result = facade.scale.read()) {
    is CabinetResult.Ok -> {
        val weight = result.value.grams
    }
    is CabinetResult.Err -> Unit
}
```

打印标签:

```kotlin
val request = PrintRequest.Label(
    title = "Cabinet SDK",
    lines = listOf("Door: 1", "Operator: Sample"),
    qrCode = "holder-cabinet-sdk",
)

val result = facade.printer.print(request)
```

关闭:

```kotlin
facade.stop()
facade.close()
```

## 标定存储

SDK 默认使用内存版 `InMemoryCalibrationStore`。如果业务需要持久化标定系数，可以自己实现 `CalibrationStore`。

MMKV 示例:

```kotlin
class MmkvCalibrationStore : CalibrationStore {
    override suspend fun readWeightSlope(vendor: CabinetVendor): Double? {
        val key = "cabinet:calibration:${vendor.name}"
        return if (MMKVUtils.containsKey(key)) MMKVUtils.getDouble(key, 1.0) else null
    }

    override suspend fun writeWeightSlope(vendor: CabinetVendor, slope: Double) {
        MMKVUtils.putDouble("cabinet:calibration:${vendor.name}", slope)
    }

    override suspend fun clear(vendor: CabinetVendor) {
        MMKVUtils.remove("cabinet:calibration:${vendor.name}")
    }
}
```

## AAR 打包命令

在仓库根目录执行:

```powershell
.\gradlew.bat :hardware:cabinet:assembleRelease
.\gradlew.bat assembleCabinetSdkRelease
.\gradlew.bat printCabinetSdkVersion
```

默认产物位置:

```text
hardware/cabinet/build/outputs/aar/hardware-cabinet-<version>-release.aar
```

## Sample 接入示例

仓库内最小样例:

- `samples/sample-cabinet`

这个 sample 展示了:

- `Auto` 启动
- 显式指定 `STAR / JW_SDK / JW_SERIAL`
- 读取当前 `CabinetState`
- 展示已选厂商与能力矩阵
- 开门
- 读重
- 标签打印
- 关闭 SDK

## 外部工程接入

如果外部工程只拿到了 AAR，可以直接引入:

```kotlin
dependencies {
    implementation(files("libs/hardware-cabinet-1.0.1-release.aar"))
}
```

如果外部工程还需要样例里的持久化标定策略或日志桥接，可以在宿主工程自行实现 `CalibrationStore` 与 `CabinetLogger`。

## 从旧 HardwareManager 迁移

旧入口与新入口映射:

| Old | New |
| --- | --- |
| `HardwareManager.init(context, config)` | `CabinetFacadeFactory.create(context).start(config)` |
| `HardwareManager.shutdown()` | `facade.stop()` / `facade.close()` |
| `HardwareManager.current?.cabinetDoor?.open(...)` | `facade.door.open(...)` |
| `HardwareManager.current?.scale?.read()` | `facade.scale.read()` |
| `HardwareManager.current?.printer?.print(...)` | `facade.printer.print(...)` |
| `HardwareManager.current?.temperature?.read()` | `facade.temperature.read()` |
| `HardwareConfig.vendorId` | `CabinetVendorPreference.Explicit(...)` |
| `HardwareConfig.serialPorts` | `CabinetPortOverrides` |
| `MMKVUtils` 直写标定系数 | `CalibrationStore` |

注意:

- 新 SDK 不再暴露 `HardwareManager` 单例
- 新 SDK 不再返回 nullable controller
- 新 SDK 所有门号都以 `1-based` 解释
- 新 SDK 把“自动探测”和“显式厂商”都收口到 `CabinetConfig`
