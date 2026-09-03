#!/usr/bin/env python3
import os
import sys

def inject_widget_manifest():
    manifest_path = os.path.join("android", "app", "src", "main", "AndroidManifest.xml")
    if not os.path.exists(manifest_path):
        print(f"Error: {manifest_path} not found")
        sys.exit(1)

    with open(manifest_path, "r", encoding="utf-8") as f:
        content = f.read()

    if "TodoWidget4x2Provider" in content:
        print("Widget components already injected in AndroidManifest.xml")
        return

    # 1. 注入桌面快捷方式与小部件创建权限（小米澎湃OS / ColorOS 必备）
    perms = """
    <uses-permission android:name="com.android.launcher.permission.INSTALL_SHORTCUT" />
    <uses-permission android:name="com.miui.home.launcher.permission.INSTALL_SHORTCUT" />
"""
    if "INSTALL_SHORTCUT" not in content:
        content = content.replace("<application", perms + "\n    <application")

    widget_entries = """
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
"""
    app_end = "</application>"
    if app_end not in content:
        print("Error: </application> tag not found in AndroidManifest.xml")
        sys.exit(1)

    content = content.replace(app_end, widget_entries + "\n    " + app_end)
    with open(manifest_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("Successfully injected widget components and permissions into AndroidManifest.xml")

if __name__ == "__main__":
    inject_widget_manifest()
