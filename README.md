# DeepPilot for Android

DeepPilot 是 [DeepSeek Harness（DSH）](https://www.npmjs.com/package/dsh-deeppilot) 的原生移动客户端。
本仓库是开源的 Android 版，与 iOS 版共用同一套桥接协议：会话、审批和提问都直连你自己 Mac 上的
[dsh-deeppilot](https://github.com/Mars-Sea/dsh-deeppilot) 插件，不经过第三方服务器。

开发中，尚未发布正式版本。

## 一期功能

- 扫码或粘贴 `deeppilot://pair?…` 链接配对；局域网自签证书按 SPKI 指纹固定，设备私钥存在 Android Keystore（有 StrongBox 时用 StrongBox）。
- 会话列表、会话详情（流式回复、思考过程、工具调用）、发送消息、停止当前回合。
- 助手回复按 Markdown 显示，公式（`$…$`、`\(…\)`、`$$…$$`、`\[…\]`、```` ```math ````）用内置 KaTeX 排版，与 iOS 版和网页版一致；渲染页面不联网。
- 在手机上处理审批和提问。
- 通知：一期没有远程推送。打开“保持连接”后，前台服务一直连着 Mac，回复完成、审批、提问和出错都会弹本地通知。
  国行手机还需要在系统设置里允许自启动、后台活动和不限制电池。厂商推送通道放在二期。
- 配对后引导：配对成功后一次性展示 4 页导览（已连接、实时会话、审批与提问、通知），通知权限改由末页申请；设置页「关于」可重看。升级前已配对的用户不展示。范围与验证见 [决策记录](docs/决策记录.md) 和 [配对后引导验收](docs/配对后引导验收.md)。

## 技能提示

输入 `/` 展示当前会话的技能，输入名称筛选，点选后继续编辑并手动发送。需要支持 `skills` 能力的 DeepPilot 插件；旧插件仍可普通输入。范围与验证见 [决策记录](docs/决策记录.md) 和 [技能提示验收](docs/技能提示验收.md)。

## 下载

正式版会以 APK 形式发布在 [Releases](https://github.com/Mars-Sea/deeppilot-android/releases)。
每个 PR 的 CI 也会产出一个 debug APK（Actions 里的 `deeppilot-debug-apk`）。

## 构建

- JDK 17+，Android SDK（compileSdk 36），最低支持 Android 10（API 29）。
- `./gradlew :app:assembleDebug` 产出 debug APK。
- `core` 模块是纯 Kotlin/JVM（协议、配对、签名、TLS 固定、连接客户端），没有 Android SDK 也能 `./gradlew :core:test`。

正式签名只在打 `v*` tag 时由 GitHub Actions 完成，密钥放在仓库 Secrets 里，不进仓库。

## 第三方组件

- [KaTeX](https://github.com/KaTeX/KaTeX) 0.18.7（MIT）和 [marked](https://github.com/markedjs/marked) 18.0.13（MIT），放在 `app/src/main/assets/markdown/`，许可证随文件附带。
- [ZXing Android Embedded](https://github.com/journeyapps/zxing-android-embedded)（Apache-2.0），用于扫码。
- [Lobe Icons](https://github.com/lobehub/lobe-icons)（MIT）的单色模型品牌图标，与 iOS 版相同，转成矢量图放在 `app/src/main/res/drawable/model_icon_*.xml`。

## 许可证

[MIT](LICENSE)

## 系统指令与目录缓存

系统指令和原生选择器支持范围见[系统指令验收](docs/系统指令验收.md)。技能、指令和模型目录增加五分钟内存缓存与磁盘恢复，支持手动刷新、后台更新、请求合并和身份隔离；当前模型选择独立实时更新。策略与本地证据见[目录缓存验收](docs/目录缓存验收.md)及[决策记录](docs/决策记录.md)。需要安装本次新构建；新模型变更推送需要更新并重载插件。未完成真机/真实主机业务验收，未发布。
