#!/usr/bin/env node
const fs = require('fs');
const path = require('path');

function injectWidgetManifest() {
  const manifestPath = path.join('android', 'app', 'src', 'main', 'AndroidManifest.xml');
  if (!fs.existsSync(manifestPath)) {
    console.error(`Error: ${manifestPath} not found`);
    process.exit(1);
  }

  let content = fs.readFileSync(manifestPath, 'utf8');

  if (content.includes('TodoWidget4x2Provider')) {
    console.log('Widget components already injected in AndroidManifest.xml');
    return;
  }

  // 1. 注入桌面快捷方式与小部件创建权限及安装包安装权限与振动反馈权限
  const perms = `
    <uses-permission android:name="com.android.launcher.permission.INSTALL_SHORTCUT" />
    <uses-permission android:name="com.miui.home.launcher.permission.INSTALL_SHORTCUT" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
    <uses-permission android:name="android.permission.VIBRATE" />
`;
  if (!content.includes('INSTALL_SHORTCUT')) {
    content = content.replace('<application', perms + '\n    <application');
  }

  const widgetEntries = `
        <!-- Android 原生桌面小部件 2x2 要事待办清单 -->
        <receiver
            android:name="com.dax.supertodo.widget.TodoWidget2x2Provider"
            android:exported="true"
            android:icon="@mipmap/ic_launcher"
            android:label="@string/widget_2x2_name">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
                <action android:name="com.dax.supertodo.ACTION_2X2_COMPLETE" />
                <action android:name="com.dax.supertodo.ACTION_REFRESH_WIDGET" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_2x2_info" />
        </receiver>

        <!-- Android 原生桌面小部件 4x2（全面兼容小米澎湃OS / OPPO ColorOS / vivo OriginOS / 华为 / 荣耀等全部安卓系统，免应用商店审核） -->
        <receiver
            android:name="com.dax.supertodo.widget.TodoWidget4x2Provider"
            android:exported="true"
            android:icon="@mipmap/ic_launcher"
            android:label="@string/widget_4x2_name">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
                <action android:name="com.dax.supertodo.ACTION_WIDGET_CLICK" />
                <action android:name="com.dax.supertodo.ACTION_REFRESH_WIDGET" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_4x2_info" />
        </receiver>

        <!-- Android 原生桌面小部件 4x4（全面兼容小米澎湃OS / OPPO ColorOS / vivo OriginOS / 华为 / 荣耀等全部安卓系统，免应用商店审核） -->
        <receiver
            android:name="com.dax.supertodo.widget.TodoWidget4x4Provider"
            android:exported="true"
            android:icon="@mipmap/ic_launcher"
            android:label="@string/widget_4x4_name">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
                <action android:name="com.dax.supertodo.ACTION_WIDGET_CLICK" />
                <action android:name="com.dax.supertodo.ACTION_REFRESH_WIDGET" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_4x4_info" />
        </receiver>

        <!-- Android 原生桌面小部件 四象限法则 4x4 -->
        <receiver
            android:name="com.dax.supertodo.widget.TodoWidgetQuadrantProvider"
            android:exported="true"
            android:icon="@mipmap/ic_launcher"
            android:label="@string/widget_quadrant_name">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
                <action android:name="com.dax.supertodo.ACTION_QUADRANT_CLICK" />
                <action android:name="com.dax.supertodo.ACTION_REFRESH_WIDGET" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_quadrant_info" />
        </receiver>


        <!-- 小部件列表远程服务 -->
        <service
            android:name="com.dax.supertodo.widget.TodoWidgetService"
            android:permission="android.permission.BIND_REMOTEVIEWS"
            android:exported="false" />

        <!-- 小部件配置页（长按编辑小部件或点击齿轮进入） -->
        <activity
            android:name="com.dax.supertodo.widget.WidgetConfigActivity"
            android:exported="true"
            android:theme="@style/Theme.SuperTodo.WidgetConfig">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" />
            </intent-filter>
        </activity>

        <!-- 2x2 小部件专属悬浮弹窗（展开清单与自定义排序设置） -->
        <activity
            android:name="com.dax.supertodo.widget.Widget2x2DialogActivity"
            android:exported="true"
            android:theme="@style/Theme.SuperTodo.FloatingDialog"
            android:launchMode="singleTop"
            android:excludeFromRecents="true"
            android:taskAffinity="com.dax.supertodo.widgetdialog"
            android:windowSoftInputMode="adjustResize" />
`;

  const appEnd = '</application>';
  if (!content.includes(appEnd)) {
    console.error('Error: </application> tag not found in AndroidManifest.xml');
    process.exit(1);
  }

  // 2. 注入外部应用打开 / 分享 JSON 备份文件的 Intent Filter 到 MainActivity
  const fileIntentFilters = `
            <!-- 响应从其他应用（微信、QQ、系统文件管理器等）中选择“打开方式 / 用其他应用打开”或“分享/发送” JSON 备份文件 -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="content" />
                <data android:scheme="file" />
                <data android:mimeType="application/json" />
                <data android:mimeType="text/json" />
                <data android:mimeType="text/plain" />
                <data android:mimeType="application/octet-stream" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="content" />
                <data android:scheme="file" />
                <data android:host="*" />
                <data android:pathPattern=".*\\.json" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="content" />
                <data android:scheme="file" />
                <data android:host="*" />
                <data android:mimeType="*/*" />
                <data android:pathPattern=".*\\.json" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="application/json" />
                <data android:mimeType="text/json" />
                <data android:mimeType="text/plain" />
                <data android:mimeType="*/*" />
            </intent-filter>`;

  if (!content.includes('android.intent.action.SEND') && content.includes('MainActivity')) {
    content = content.replace(/(<activity[^>]*MainActivity[^>]*>[\s\S]*?)(<\/activity>)/, `$1\n${fileIntentFilters}\n        $2`);
  }

  content = content.replace(appEnd, widgetEntries + '\n    ' + appEnd);
  fs.writeFileSync(manifestPath, content, 'utf8');
  console.log('Successfully injected widget components, file intent-filters and permissions into AndroidManifest.xml');

  // 3. 同步 widget_dialog.html 与 Sortable.min.js 至 android assets
  const assetsDir = path.join('android', 'app', 'src', 'main', 'assets');
  if (!fs.existsSync(assetsDir)) {
    fs.mkdirSync(assetsDir, { recursive: true });
  }
  ['widget_dialog.html', 'Sortable.min.js'].forEach(file => {
    if (fs.existsSync(file)) {
      fs.copyFileSync(file, path.join(assetsDir, file));
      console.log(`Copied ${file} to ${assetsDir}`);
    }
  });
}

if (require.main === module) {
  injectWidgetManifest();
}

module.exports = { injectWidgetManifest };
