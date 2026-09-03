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

  // 1. 注入桌面快捷方式与小部件创建权限（小米澎湃OS / ColorOS 必备）
  const perms = `
    <uses-permission android:name="com.android.launcher.permission.INSTALL_SHORTCUT" />
    <uses-permission android:name="com.miui.home.launcher.permission.INSTALL_SHORTCUT" />
`;
  if (!content.includes('INSTALL_SHORTCUT')) {
    content = content.replace('<application', perms + '\n    <application');
  }

  const widgetEntries = `
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
`;

  const appEnd = '</application>';
  if (!content.includes(appEnd)) {
    console.error('Error: </application> tag not found in AndroidManifest.xml');
    process.exit(1);
  }

  content = content.replace(appEnd, widgetEntries + '\n    ' + appEnd);
  fs.writeFileSync(manifestPath, content, 'utf8');
  console.log('Successfully injected widget components and permissions into AndroidManifest.xml');
}

if (require.main === module) {
  injectWidgetManifest();
}

module.exports = { injectWidgetManifest };
