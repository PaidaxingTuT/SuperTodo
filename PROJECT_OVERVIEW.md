# 项目总览 · PROJECT OVERVIEW

> 超级清单 SuperTodo —— 一份同时充当「任务说明 + 需求 + 实现细节 + 规范」的单文件速查。
> 更新：2026-09-02 ｜ 当前版本：**v1.6.6** ｜ 仓库：`PaidaxingTuT/SuperTodo`

---

## 一、一句话

个人专用的超级清单工具：把购物 / 待办 / 计划 / 旅游 / 愿望统一管理，本地存储、界面清爽、可分类可排序、带 AI 云端增强。

## 二、交付形态与技术栈（红线）

| 项 | 约定 |
|----|------|
| 交付形态 | **Android APK**（本地 WebView 壳封装），**不是 PWA / 本地网页应用**，宣传文案不得出现 PWA 字眼 |
| 前端 | 纯原生 HTML + CSS + JavaScript，**无框架**，Material 风格可换主题色 |
| 拖拽 | 本地引入 `Sortable.min.js`（分组内手动排序） |
| 存储 | 浏览器 `localStorage`（key `listapp.data.v2`），导出 / 导入 / 清空备份 |
| AI | 云端 OpenAI 兼容接口（设置里填 base/key/model，带总开关）；速记 + 智能整理，启用才显示入口 |
| 构建 | 本地用浏览器开 `index.html` 测试；APK 走 **GitHub Actions**（Capacitor 封装） |
| App 名 | 中文「超级清单」，桌面/关于弹窗显示；品牌 SuperTodo |
| 包名 | `com.dax.supertodo` |
| 显示作者 | 派大星TuT |

## 三、功能需求总览

### 1. 三层信息架构（核心）
| 层级 | 是什么 | 在哪 |
|------|--------|------|
| 类型 | 购物/待办/计划/旅游/愿望…可增删改 | 左上角 ☰ 汉堡菜单 |
| 分组 | **按场景** 或 **按时间** 二选一 | 主页大区块卡片 |
| 事项 | 具体事项 | 点进分组区块后看到 |

### 2. 记录与编辑
- 右下角 **＋** 一键添加；**✨** AI 一句话速记（需开启 AI 增强）
- 完整表单：标题 / 备注 / 类型 / 场景 / 时间 / 预估花费 ¥ / 截止日期 / ★重要程度 1–5
- 点事项可编辑 / 删除；完成事项自动沉底

### 3. 分类与排序
- 类型 / 场景 / 时间标签均可自定义增删改（汉堡长按类型重命名；场景时间在设置里）
- 排序：默认（手动顺序）/ 花费升降 / 重要度 / 截止 / 创建
- 「默认」排序下按行右侧 **≡ 把手** 拖拽调序，落位即保存

### 4. AI 增强（云端，需联网）
- 一句话速记：一句话自动解析类型/场景/时间/花费/截止/重要度，填好表单，保存前可改
- 智能整理：为未分类事项建议补场景 / 时间，逐条预览 + 一键采纳
- 建议式新建标签：解析到不存在标签时提示，默认勾选可取消

### 5. 自定义与数据
- 主题色：预设 10 色 + 自定义取色器，配色整体跟随
- 数据：导出备份 / 导入备份 / 清空全部
- 关于弹窗：作者 / 版本 / 检查更新 / 开源仓库按钮

### 6. 原生体验（APK 专属）
- 全部弹窗替换为 **App 内自定义对话框**（提示/询问/删除图标区分），无系统原生弹窗
- Android **返回键**适配：先关抽屉 / 弹窗 / 分组页，无层可关才退出（不误退）
- 抽屉顶部 **避让系统状态栏**
- 启动自动**检查更新**；发现新版本在 App 内**原生下载管理器直接下载**（不跳浏览器），网络受限时走 ghfast 加速
- 安装包显示的版本号 = 真实 Android 版本（修复恒 1.0 的 bug）

## 四、界面 / 关键代码文件地图

| 文件 | 职责 |
|------|------|
| `index.html` | 界面结构；`#infoVer` 里写版本展示文案 |
| `style.css` | 样式；主题变量 `--primary/-soft/-faint/-deep` 由 JS 动态生成 |
| `app.js` | 全部逻辑与常量（见下） |
| `Sortable.min.js` | 拖拽库 |
| `icon.svg` / `app-icon.png` | 图标（Workflow 会按分辨率转桌面图标） |
| `manifest.webmanifest` / `sw.js` | PWA 残留，非交付形态，sw 保持禁用 |
| `.github/workflows/deploy.yml` | 云端打包 + 发布 Release |
| `CHANGELOG.md` | 更新记录（Release 正文来源） |
| `debug.keystore` | **固定签名密钥**（保证版本间可覆盖安装） |
| `README.md` / `requirements.md` / `PROJECT_STATUS.md` | 对外功能说明 / 需求 / 进度 |

**app.js 内需随版本更新的常量**
```js
const APP_VERSION='v1.6.6';
const REPO_URL='https://github.com/PaidaxingTuT/SuperTodo';
const REPO_API='https://api.github.com/repos/PaidaxingTuT/SuperTodo';
```
- `index.html` 的 `#infoVer` 文案 `SuperTodo · 版本 1.6.6`（由 app.js `openInfo()` 动态改写，改不改显示上都对，但保持同步）
- 打包时 Workflow 用 `sed` 把 `APP_VERSION` 注入 dist 副本（本地手动改了 app.js 即可）

## 五、版本与发布规范（每次 release 必须遵守）

### 版本递增规则
- 从小版本开始递增，**由用户指定**
- 小改动：+0.0.1；大改动：+0.1；末位清零、满十进位
- 版本范围保持稳定增长，不做大跨度跳版本

### Tag / 文件 / 标题约定
| 项 | 规则 |
|----|------|
| Git tag | `v` 前缀，如 `v1.6.5` |
| APK 文件名 | `SuperTodo-1.6.5.apk`（**去掉 v**） |
| Release 标题 | **只写版本号** `v1.6.5`（不带日期、不带「大更新/小更新」字样） |
| Release 正文 | 更新内容放正文；由 Workflow 从 CHANGELOG 对应条目自动提取（去掉 `## vX.Y.Z` 标题行，只留 bullet） |
| CHANGELOG | 每发一版在文件顶部加一条 `## vX.Y.Z（YYYY-MM-DD）`，只列 bullet |

### versionName / versionCode
- Workflow 用 `sed` 把 `versionName "x.y.z"` / `versionCode N` 写进 `android/app/build.gradle`
- versionCode 计算：各段 ×100 合并（如 1.6.5 → 10605），保证单调递增

### Release 触发
- `push` 打 `v*` 标签即自动触发
- 也可在 Actions 页 `workflow_dispatch` 手动触发（未打标签时按最新 tag 递增一版）

### 签名
- 仓库根固定 `debug.keystore`（storepass/keypass=`android`，alias=`androiddebugkey`），Workflow 拷到 `~/.android/` 后统一签名
- **作用**：跨版本直接覆盖安装，不报签名冲突

## 六、更新检测与下载链路（v1.6.3+）

1. 启动 `setTimeout(()=>checkUpdate(true),900)` 静默检测（`about` 弹窗里可点「检查更新」主动触发）
2. `fetch(REPO_API+'/releases/latest')` 拿最新 Release → `verGt()` 与本地 `APP_VERSION` 比较
3. 有新版本：原生平台走 `window.Capacitor` 分支 → `DownloadManager` 下载 APK（MainActivity 里 patch 的 DownloadListener）；失败或网络受限时改用 `https://ghfast.top/<github下载地址>` 前缀
4. 桌面浏览器预览时仍回退 `window.open` 打开 Releases 页

## 七、提交与署名约定（红线，不可违反）

- Git 提交**只署名用户本人**（`user.name=AmaneKanata` / `user.email=paidaxing_tut@outlook.com`）
- commit / PR / README / 任何仓库内容中**禁止**出现协作者署名、`Co-Authored-By`、以及任何第三方 AI 工具痕迹
- 上传只用用户身份；本地 git 与 GitHub 同步推进
- README 只写软件功能，不写开发过程

## 八、发布历史

| 版本 | 要点 |
|------|------|
| v1.5.3 | **SuperTodo 首发**：三层架构 + AI 速记/整理 + 拖拽 + 本地存储 |
| v1.6.0 | **大更新**：原生弹窗→App 内自定义对话框；返回键适配；状态栏避让；包名/图标统一；检查更新 |
| v1.6.2 | 抽屉头部微调；仓库地址统一；LOGO 去黑边 |
| v1.6.3 | 更新改为 App 内直接下载；修复安装显示版本恒 1.0；美化自定义对话框 |
| v1.6.4 | About 开源仓库改为图标+文字按钮，不换行 |
| v1.6.5 | 开源仓库按钮改主色实心胶囊；固定签名（换钥，旧版需先卸载）；原生下载 + ghfast 加速 |
| v1.6.6 | 简化分类文案；调整仓库按钮尺寸；增强 AI 速记解析并支持关闭时取消请求 |

## 九、现状与注意事项

- Git：工作树干净，与 `origin/main` 同步；tag 已到 `v1.6.6`
- ⚠️ v1.6.5 起换用固定签名：**从 ≤1.6.4 升级需先卸载一次**；此后的版本间可直接覆盖安装
- `sw.js` 保持禁用（开发期防旧缓存；APK 经 Capacitor 读取本地文件，不受影响）
- 已知可选增强（按需再做）：跨分组拖拽移动事项、深浅色模式、字体/列表密度、数据云同步
