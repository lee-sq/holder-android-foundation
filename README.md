## holder-android-foundation

第一阶段目标是把旧仓里的基础框架拆到独立仓库，并完成结构重组、样例验线与硬件模块版本化。

当前已开始第二阶段，`core` 已收敛为“内部源码分层 + 对外聚合出口”的结构。

### 目录结构

```text
holder-android-foundation
  build-logic
  core
    build.gradle.kts
    ui
    network
    platform
  hardware
    base
    cabinet
    camera
    scale
    temperature
  samples
    sample-cabinet
```

### 关键约定

- `core` 当前对外新增聚合出口 `:core`，用于直接产出 `foundation-core-sdk`。
- `core` 内部源码层当前保留 `core/ui`、`core/network`、`core/platform` 三层目录。
- 原来的 `navigation` 已经收回到 `ui`，不再单独作为低收益细分模块维护。
- `ui` 承接原 `common + widget` 的源码与资源。
- `platform` 承接原 `utils + logger` 的源码。
- `hardware/base` 承接共享硬件契约与模型。
- 每个 `hardware/*` 模块都有自己的 `version.properties`，由 `com.holderzone.android.hardware.library` 统一读取。
- `sample-cabinet` 只验证基础框架接线，不承接业务流程。

### 常用命令

```powershell
.\gradlew.bat projects
.\gradlew.bat assembleCoreSdkRelease
.\gradlew.bat :samples:sample-cabinet:assembleDebug
.\gradlew.bat printHardwareVersions
.\gradlew.bat assembleHardwareRelease
```

### 接入文档

- [Foundation 接入指南](D:/dev/code/holder-android-foundation/docs/foundation-integration-guide.md)

### 硬件模块打包说明

Android Gradle Plugin 不允许 library 在打 AAR 时直接 `implementation(local.aar)`，否则会阻止 `bundleReleaseAar`。

当前第一阶段采用如下策略：

- `debugImplementation(local.aar)`：保证 sample / 调试链路可运行。
- `releaseCompileOnly(local.aar)`：保证 `hardware/*` 可以先产出版本化 wrapper AAR。

这意味着当前 release AAR 已完成模块拆分与版本管理，但厂商本地 AAR 仍然需要后续继续做第二阶段治理，推荐方向：

- 拆成独立 vendor module
- 或转为内部 Maven / flat repository 依赖

### 第二阶段落地方式

当前第二阶段已经落到更接近最终交付的目录形态：

- 对外出口为 `:core`
- 内部源码层为 `core/ui`、`core/network`、`core/platform`
- `common + widget` 已物理迁入 `core/ui`
- `utils + logger` 已物理迁入 `core/platform`
- `navigation` 已收回到 `core/ui`
