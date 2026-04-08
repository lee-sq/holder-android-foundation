# Core Module Layout

`holder-android-foundation` 的 `core` 现在分成两层：

- 对外聚合出口：`:core`
- 内部源码分层目录：`core/ui`、`core/network`、`core/platform`

其中：

- `:core` 负责聚合 `ui + network + platform`，产出一个可直接分发的 `foundation-core-sdk` AAR
- `navigation` 已经回收到 `ui`，不再单独维护模块

目录结构如下：

```text
core
  build.gradle.kts
  ui
    src/main
    src/test
    src/androidTest
  network
  platform
    libs
    src/main
    src/test
    src/androidTest
```

常用命令：

```powershell
.\gradlew.bat :core:assembleRelease
.\gradlew.bat printCoreSdkVersion
.\gradlew.bat assembleCoreSdkRelease
```

默认产物位置：

```text
core/build/outputs/aar/foundation-core-sdk-<version>-release.aar
```
