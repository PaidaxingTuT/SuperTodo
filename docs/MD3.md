# Material Design 3 界面

`dev-md3` 分支将现有原生 Web 界面应用到 MD3 的颜色、形状、排版和交互层级，继续复用本地图标和动效库。

## 设计依据

- [Google 官方主题设计指南](https://developer.android.com/codelabs/m3-design-theming?hl=en)：颜色角色与成对前景色、表面容器层级、文字层级、形状尺度与动效。
- [Material 3 Foundations](https://m3.material.io/foundations/)：设计令牌、布局与交互状态。
- [MD3 颜色角色](https://m3.material.io/styles/color/the-color-system/color-roles)。
- [MD3 按钮](https://m3.material.io/components/buttons/overview)。

## 实现

- 关于提供经典 / Material 3 切换。已有存储未指定风格时使用经典，新用户及演示默认 MD3。首次打开显示 UI 更新提示，显示后即记为已读，不再弹出。只有选中 MD3 时才挂载 `web/styles/md3.css` 覆盖样式；页面、逻辑及数据共用，风格随备份导出与恢复。
- `web/styles/md3.css` 定义低到高的色调表面，以及 primary、secondary、tertiary 容器与对应文字色；深浅主题独立映射。
- 主色继续由已有颜色设置控制。色阶采用轻量 HSL 衍生，未实现壁纸取色或 HCT 算法；日间主色限制亮度，以支持白色按钮文字。
- 24/28px 容器与对话框、胶囊导航与按钮、分段按钮选中指示、填充式文本框、MD3 开关、仅加号的 FAB、悬停及键盘焦点反馈。
- 以移动端单列清单为核心：64px 顶栏、抽屉导航、紧凑分组标题和 48px 主要触控区域。网页端仅限制内容宽度，不设置常驻侧栏或双列布局。
- 保留自定义背景、列表密度和系统减少动态效果偏好；分组入口与完成按钮支持键盘操作。
- 手机表单操作区固定在滚动内容之外，日期选择器限制在视口宽度内；子任务展开入口靠右，标题和标签预留按钮空间，展开列表占满正文宽度。
- 搜索框从右侧展开并淡入，关闭时反向收回后淡出；保持顶栏高度稳定，自动聚焦输入，支持返回键及点击框外收起和系统减少动画偏好。框外首次点击只收起搜索，避免误触清单操作。
- 事项使用分离的圆角色调表面、胶囊标签、统一的主任务与子任务勾选框；子任务展开为圆角色调面板，完成时播放短波纹与轻微回弹。

### Galaxy 动画参考

研究了 [Uiverse Galaxy](https://github.com/uiverse-io/galaxy) 的复选框组件，包含 [Shoh2008 的勾选波纹与描边示例](https://github.com/uiverse-io/galaxy/blob/main/Checkboxes/Shoh2008_perfect-mouse-3.html) 和 [adamgiebl 的缩放填充示例](https://github.com/uiverse-io/galaxy/blob/main/Checkboxes/adamgiebl_curly-lizard-40.html)。本项目借鉴短波纹与缩放反馈的交互思路，用浏览器原生动画、已有 Motion 和主题变量实现，勾号继续使用本地 Morphicons；子任务展开沿用 GSAP 高度过渡。无需加载新的组件库。
- 背景透明度和列表布局共用 48px 触控滑块，包含主题色进度、圆形手柄和数值胶囊；手柄与轨道同步平滑过渡，拖动时增加柔和光晕。预览面板淡入淡出，轨道与原位置对齐并限制在手机视口内，松手保存并返回设置；尊重系统减少动画设置。

## Web 演示

```powershell
python -m http.server 8765 --bind 127.0.0.1
```

打开 <http://localhost:8765/?demo=1>。演示使用 `listapp.demo.v2` 存储，和正常清单独立。演示修改可以保存；不带参数的页面继续使用原有数据。

```powershell
node --check web/js/app.js
git diff --check
```

仅作 Web 预览，本分支未更改发布版本或 Android 打包流程。
