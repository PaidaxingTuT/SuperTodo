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

    widget_entries = """
        <!-- 小米澎湃OS / Android 桌面小部件 4x2 -->
        <receiver
            android:name="com.dax.supertodo.widget.TodoWidget4x2Provider"
            android:exported="true"
            android:label="@string/widget_4x2_name"
            android:process=":widgetProvider">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
                <action android:name="miui.appwidget.action.APPWIDGET_UPDATE" />
                <action android:name="com.dax.supertodo.ACTION_WIDGET_CLICK" />
                <action android:name="com.dax.supertodo.ACTION_REFRESH_WIDGET" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_4x2_info" />
            <meta-data
                android:name="miuiWidget"
                android:value="true" />
            <meta-data
                android:name="miuiWidgetRefresh"
                android:value="exposure" />
            <meta-data
                android:name="miuiWidgetRefreshMinInterval"
                android:value="10000" />
        </receiver>

        <!-- 小米澎湃OS / Android 桌面小部件 4x4 -->
        <receiver
            android:name="com.dax.supertodo.widget.TodoWidget4x4Provider"
            android:exported="true"
            android:label="@string/widget_4x4_name"
            android:process=":widgetProvider">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
                <action android:name="miui.appwidget.action.APPWIDGET_UPDATE" />
                <action android:name="com.dax.supertodo.ACTION_WIDGET_CLICK" />
                <action android:name="com.dax.supertodo.ACTION_REFRESH_WIDGET" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_4x4_info" />
            <meta-data
                android:name="miuiWidget"
                android:value="true" />
            <meta-data
                android:name="miuiWidgetRefresh"
                android:value="exposure" />
            <meta-data
                android:name="miuiWidgetRefreshMinInterval"
                android:value="10000" />
        </receiver>

        <!-- 小部件列表远程服务（独立进程） -->
        <service
            android:name="com.dax.supertodo.widget.TodoWidgetService"
            android:permission="android.permission.BIND_REMOTEVIEWS"
            android:exported="false"
            android:process=":widgetProvider" />

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
    print("Successfully injected widget components into AndroidManifest.xml")

if __name__ == "__main__":
    inject_widget_manifest()
