# 签名修复技术报告 · SIGNING REPORT

> 写给接手修复/发布的 Agent（GPT/Codex）看。读完请按「四、请执行」操作，并把「五、铁律」记入 AGENTS.md。
> 日期：2026-09-02 ｜ 仓库：`PaidaxingTuT/SuperTodo` ｜ 本地路径：`D:\Code\project\SuperTodo`

---

## 一、现象

用户反馈：从已安装版本覆盖升级到最新（v1.6.6）时，Android 报**「安装失败，已安装了签名冲突的应用」**（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`）。

## 二、实测证据（勿跳过）

对 Releases 上已发布的两个 APK 与仓库根的固定 keystore 做了证书校验（下载 APK → 提取 v2 签名证书 → openssl 指纹）：

| 对象 | 证书 SHA-256 |
|------|--------------|
| 仓库固定 `debug.keystore`（`androiddebugkey`） | `C5:3E:3A:82:F0:4F:75:1D:4C:4F:8A:36:7B:3F:73:82:26:27:00:F5:21:54:D5:1E:F2:BC:B3:C8:A2:42:64:97` |
| 已发布 `SuperTodo-1.6.5.apk` | `4B:C9:36:F3:5E:8B:8A:0A:7A:59:B8:F9:60:28:D5:27:B6:9C:D4:C6:5D:AE:F5:21:E6:AC:45:FA:70:DE:15:41` |
| 已发布 `SuperTodo-1.6.6.apk` | `F6:00:DE:CC:BA:8A:BC:03:57:13:36:FE:3A:16:7F:D0:7D:FC:A3:50:DD:22:0B:73:55:13:38:B7:4E:E4:ED:DF` |

**三个证书互不相同。** 结论：v1.6.5、v1.6.6 两次 CI 构建都没有用仓库固定 keystore，而是 gradle 每次现生成的一把随机调试钥。

## 三、根因

原 `deploy.yml` 的签名做法是：

```yaml
- name: 固定签名密钥（保证版本间可覆盖安装）
  run: |
    mkdir -p ~/.android
    cp debug.keystore ~/.android/debug.keystore
```

它把 keystore 放进 `~/.android`，**赌 AGP 默认 debug 签名配置会去自动发现它**。实测证明该机制在 GitHub runner 上**不生效**（文件没被采用时 AGP 就静默现生成随机钥，且不报错）。

已排除的干扰项：
- v1.6.5 与 v1.6.6 两版的 `.github/workflows/deploy.yml`、`debug.keystore` **逐字节相同**，签名步骤都在、顺序也对（复制在 `assembleDebug` 之前）。所以不是版本差异，是机制本身失效。
- APK 为 **v2-only 签名（无 v1）**：`keytool -printcert -jarfile` 读不到，验签必须用 `apksigner`（或解析 APK Signing Block）。

## 四、v1.6.7 修复方案

改了两处（可打开 `.github/workflows/deploy.yml` 对照）：

1. **签名步骤改为「复制 + 向 build.gradle 显式注入签名配置」**，不再依赖自动发现：

```yaml
- name: 固定签名密钥（显式写入 build.gradle，指向仓库内 debug.keystore）
  run: |
    mkdir -p ~/.android
    cp debug.keystore ~/.android/debug.keystore
    cat >> android/app/build.gradle <<'GRADLEEOF'

    android {
        signingConfigs {
            debug {
                storeFile file('../../debug.keystore')
                storePassword 'android'
                keyAlias 'androiddebugkey'
                keyPassword 'android'
            }
        }
        buildTypes {
            debug {
                signingConfig signingConfigs.debug
            }
        }
    }
    GRADLEEOF
    echo "已将 debug 签名显式指向仓库内 debug.keystore"
```

   `android/app/build.gradle` 相对仓库根是 `android/app/`，故 `../../debug.keystore` 精确指向仓库根的固定钥匙。`storePassword/keyPassword=android`、`keyAlias=androiddebugkey` 与 keystore 一致。追加第二个 `android {}` 配置块在 Gradle 中合法（配置同一扩展）。

2. **新增「校验 APK 签名 = 仓库固定钥匙」步骤**，在 `构建 APK` 之后：用 `apksigner verify --print-certs` 取 APK 证书 sha256，与 `keytool -exportcert` 导出的固定钥匙证书 sha256 比对，不一致 `exit 1` 让构建失败——防止再无声发布错误签名包。

修改后 workflow 共 14 步，已用 YAML 解析器验证结构合法。

## 五、请执行（GPT）

1. **提交**以上 `deploy.yml` 改动（连同更新的 `AGENTS.md`、本报告，按你的判断一并纳入）。作者只用仓库既有身份 `AmaneKanata <paidaxing_tut@outlook.com>`，**禁止任何 `Co-Authored-By`/协作者/AI 署名**。提交信息建议：`发布流程：显式固定签名并增加签名校验`。
2. **不要重发 v1.6.6**（已发布 APK 为错误签名；删 release/tag 重发代价高）。直接进 **v1.6.7**：
   - 同步三处版本：`app.js` 的 `APP_VERSION`、`CHANGELOG.md` 顶部 `## v1.6.7（2026-09-02）`、`index.html` 的 `#infoVer`。
   - `git tag v1.6.7` → `git push origin main v1.6.7`。
   - 等 Actions 变绿，去 [Releases](https://github.com/PaidaxingTuT/SuperTodo/releases) 确认 `SuperTodo-1.6.7.apk` 存在，且「校验 APK 签名」步骤日志显示 APK 证书 = 固定钥匙 `C5:3E:3A:82…`。
3. **引导用户手机端最后做一次卸载重装**（切到固定钥，数据会清，务必先备份）：
   设置 → 数据 → **导出备份** → 卸载当前 App → 安装 v1.6.7 → 导入备份。
   这是**最后一次**卸载重装。自 v1.6.7 起所有版本同钥，之后可无限直接覆盖安装。

## 六、铁律（请写入 AGENTS.md，长期记住）

- **禁止回归**「只复制 keystore 到 `~/.android`」的写法——已证实无效。
- 签名必须**显式**写在 `android/app/build.gradle`（由 workflow 注入），并用签名校验步骤兜底。
- **事实更正**：不是「v1.6.5 起同钥」。已发布 v1.6.5 / v1.6.6 都是随机签名；**只有自 v1.6.7（本次显式签名修复）起才真正共用固定钥匙**。
- 固定钥匙指纹 `C5:3E:3A:82…`；下次覆盖安装再报「签名冲突」时，先核对该 APK 是否同指纹。
