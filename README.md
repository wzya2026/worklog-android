# 工作随记 · Android

一个本地保存的中文工作记录 APP，可记录工作内容、后续举措、分类及跟进提醒，并导出 Excel/CSV。**这是独立的安卓项目，与 Transmission 下载界面无关。**

**[下载 APK](https://github.com/wzya2026/worklog-android/releases)** · [1.70 使用说明](docs/v1.70-原版说明.txt) · [构建与维护](docs/构建与维护.md)

## 下载安装

当前版本 **1.70**，包名 `com.example.worklog`，最低 Android **8.0（API 26）**。三个安装包使用相同包名和签名，按设备布局选择其中一个：

| 安装包 | 适用方式 |
| --- | --- |
| `WorkLog-v1.70-FullScreen.apk` | 手势导航手机，底部“录入新工作”按钮。 |
| `WorkLog-v1.70-NavKeys.apk` | 使用三键/虚拟导航键的手机，录入入口位于顶部。 |
| `WorkLog-v1.70-TabletAdaptive.apk` | 平板，横屏左右分栏、竖屏手机布局，尊重系统自动旋转。 |

Releases 中的 APK 来自原有 1.70 成品，保留原签名，没有重新签名。已有用户安装前建议先导出表格备份；不要先卸载旧 APP，卸载会清除本地数据。

## 功能

- 录入日期、时间、工作类型、分类、内容和后续举措；使用输入法的语音转文字即可快速登记。
- 编辑、删除、批量删除记录；管理工作类型和分类。
- 按月份、日期范围、类型、分类组合浏览，按关键词检索。
- 待跟进列表、完成跟进、每天/每周/每月提醒及每日补录提醒。
- 导出 Excel 或 CSV；可选入口密码。
- 数据保存在设备本地，本版没有联网权限或云同步。入口密码不等于数据库加密。

## 源码与构建

`src/`、`res/`、`AndroidManifest.xml` 来自原项目，文件 SHA256 记录在 `source-manifest.json`。使用 Android 原生 Java API，无需第三方业务依赖。Windows 构建入口为 `build.ps1`，需要 JDK 17 和 Android SDK Platform/Build Tools 35，详见[构建与维护](docs/构建与维护.md)。

签名密钥、数据库、导出工作记录和本机工具链不在仓库中。源码编译出来的新签名包不能自动替代原签名包；维护既有安装时应使用自己保管的原签名密钥。

## 本次发布检查

三个 APK 的 ZIP 完整性、签名、版本、最低系统版本及布局标记均已检查。它们的应用代码 DEX 相同，三个布局开关不同；校验记录见 `docs/apk-verification.json`，下载附件见 `SHA256SUMS.txt`。

本次是整理已有 1.70 项目并独立发布，没有增加 APP 功能，也没有新增手机或平板的安装运行验收。

MIT License。
