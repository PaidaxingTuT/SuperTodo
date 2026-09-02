# SuperTodo 发布规范

## 构建原则

- 本地只执行：修改文件、检查、提交、打轻量标签、推送。
- APK 构建与 GitHub Release 全部交给 `.github/workflows/deploy.yml`。
- 不在本地安装或使用 Android SDK、Java、Gradle 构建 APK。

## 发布前版本同步

发布 `vX.Y.Z` 前必须保持以下三处版本一致：

1. `app.js`：`const APP_VERSION='vX.Y.Z'`。
2. `CHANGELOG.md` 顶部：`## vX.Y.Z（YYYY-MM-DD）`，正文只列本次更新 bullet，不写“大更新”或“小更新”。Release 正文由此自动提取。
3. `index.html`：About 弹窗 `#infoVer` 的版本文案。

`APP_VERSION` 必须与 Git tag 完全一致，否则应用可能错误提示更新或显示错误版本。

## 提交与推送

- 只使用仓库现有用户身份提交：`AmaneKanata <paidaxing_tut@outlook.com>`。
- 禁止添加 `Co-Authored-By`、协作者署名或 AI 工具署名。
- 按实际改动暂存文件，提交信息格式：`发布 vX.Y.Z`。
- Tag 必须带 `v` 前缀并使用轻量标签。
- 不跳版本；CI 根据版本各段乘 100 合并生成严格递增的 `versionCode`，例如 `1.6.6 -> 10606`。

```powershell
git tag vX.Y.Z
git push origin main vX.Y.Z
```

推送 `v*` 标签会自动触发 GitHub Actions。流水线负责 Capacitor 封装、原生下载监听、Java 21 编译、ImageMagick 图标生成、固定签名、APK 重命名和 Release 发布。产物名为 `SuperTodo-X.Y.Z.apk`，Release 标题只写 `vX.Y.Z`。

## 结果确认

- 优先查看公开的 [Actions 页面](https://github.com/PaidaxingTuT/SuperTodo/actions)：黄为构建中，绿为成功，红为失败。
- 再查看公开的 [Releases 页面](https://github.com/PaidaxingTuT/SuperTodo/releases)，确认版本、更新说明及 `SuperTodo-X.Y.Z.apk` 附件均存在。
- 本机有已认证的 `gh` 时可使用 `gh run watch`。
- 不使用匿名 GitHub REST API 轮询；共享出口容易触发限流。使用 API 时必须通过安全方式提供认证，禁止把 PAT 写入仓库、命令输出或日志。

## 常见失败点

- Workflow heredoc 缩进异常会导致瞬时失败或没有 jobs。
- ImageMagick 必须安装；Java 必须为 21。
- 覆盖安装失败时检查签名是否一致、`versionCode` 是否递增。

## 签名（铁律，勿回退）

完整事故报告见 `SIGNING_REPORT.md`。

- **禁止回归**「只复制 keystore 到 `~/.android/debug.keystore`」的旧写法——实测在 GitHub runner 上 AGP 不会采用，会静默现生成随机调试钥，导致每次构建签名都不同、覆盖安装必报「签名冲突」。
- 签名必须**显式**写在 `android/app/build.gradle`：`signingConfigs.debug` 的 `storeFile` 指向仓库根 `debug.keystore`，`storePassword`/`keyPassword`=`android`，`keyAlias`=`androiddebugkey`，并让 `buildTypes.debug` 使用它。`deploy.yml` 已注入该配置，并在打包后校验「APK 证书 = 固定钥匙」（apksigner 比对 sha256，不一致构建失败）。
- **事实更正（重要）**：不是「v1.6.5 起同钥」。已发布的 v1.6.5 / v1.6.6 APK 均为随机签名（证书 `4B:C9:36:F3…`、`F6:00:DE:CC…`），互不兼容。固定钥匙证书 SHA-256 为 `C5:3E:3A:82:F0:4F:75:1D:4C:4F:8A:36:7B:3F:73:82:26:27:00:F5:21:54:D5:1E:F2:BC:B3:C8:A2:42:64:97`。
- **自 v1.6.7（显式签名修复后的首个版本）起才真正共用固定钥匙**。
- 手机端只需**最后一次**卸载重装来切到固定钥：先导出备份 → 卸载 → 安装 v1.6.7 → 导入备份；此后所有版本可直接覆盖安装。
