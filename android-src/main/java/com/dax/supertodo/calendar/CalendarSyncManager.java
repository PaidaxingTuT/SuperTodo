package com.dax.supertodo.calendar;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * 系统日历同步管理器。
 *
 * <p>负责将 Web 端带截止日期的未完成待办通过 Android CalendarContract 写入本地系统日历，
 * 并维护 itemId 到系统 eventId 的映射，确保改期、完成或删除时系统日程同步更新。</p>
 */
public final class CalendarSyncManager {

    private static final String PREF_NAME = "supertodo_calendar_prefs";
    private static final String KEY_ENABLED = "calendar_sync_enabled";
    private static final String KEY_EVENT_MAP = "calendar_event_map";
    private static final String DUE_PATTERN = "yyyy-MM-dd";
    private static final String UTC_TIMEZONE = "UTC";
    private static final long ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L;
    private static final int DEFAULT_REMINDER_MINUTES = 0;

    private CalendarSyncManager() {
    }

    /**
     * 读取系统日程同步专用的偏好设置。
     *
     * @param context 任意上下文
     * @return SharedPreferences 实例
     */
    public static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 判断系统日程同步是否已开启（默认关闭，避免未经授权写入用户日历）。
     *
     * @param context 任意上下文
     * @return true 表示已开启
     */
    public static boolean isEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_ENABLED, false);
    }

    /**
     * 设置系统日程同步开关状态。
     *
     * @param context 任意上下文
     * @param enabled 是否开启
     */
    public static void setEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /**
     * 检查是否同时具备系统日历的读写权限。
     *
     * @param context 任意上下文
     * @return true 表示权限齐全
     */
    public static boolean hasCalendarPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 根据 Web 端传来的状态 JSON 全量同步未完成待办到系统日历。
     *
     * <p>未完成且带有有效 due 日期的事项会创建或更新为全天日程；
     * 已完成、被删除或移除截止日期的事项会从系统日历清理，避免残留过期日程。</p>
     *
     * @param context 任意上下文
     * @param stateJson Web 端完整 state JSON 字符串
     */
    public static synchronized void syncFromStateJson(Context context, String stateJson) {
        if (!isEnabled(context) || !hasCalendarPermission(context) || stateJson == null || stateJson.isEmpty()) {
            return;
        }
        try {
            JSONObject stateObj = new JSONObject(stateJson);
            JSONArray items = stateObj.optJSONArray("items");
            if (items == null) {
                return;
            }
            long calendarId = findWritableCalendarId(context);
            if (calendarId <= 0L) {
                return;
            }

            Map<String, Long> eventMap = loadEventMap(context);
            Set<String> activeIds = new HashSet<>();
            ContentResolver resolver = context.getContentResolver();

            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String id = item.optString("id", "").trim();
                boolean done = item.optBoolean("done", false);
                String due = item.optString("due", "").trim();
                String title = item.optString("title", "").trim();
                if (id.isEmpty() || done || due.isEmpty() || title.isEmpty()) {
                    continue;
                }
                long startUtc = parseDueToUtcMidnight(due);
                if (startUtc <= 0L) {
                    continue;
                }

                activeIds.add(id);
                String note = item.optString("note", "").trim();
                Long existingEventId = eventMap.get(id);
                if (existingEventId != null && eventExists(resolver, existingEventId)) {
                    updateAllDayEvent(resolver, existingEventId, title, note, startUtc);
                } else {
                    long newEventId = insertAllDayEvent(resolver, calendarId, title, note, startUtc);
                    if (newEventId > 0L) {
                        eventMap.put(id, newEventId);
                        insertReminder(resolver, newEventId);
                    }
                }
            }

            Iterator<Map.Entry<String, Long>> iterator = eventMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Long> entry = iterator.next();
                if (!activeIds.contains(entry.getKey())) {
                    deleteEvent(resolver, entry.getValue());
                    iterator.remove();
                }
            }
            saveEventMap(context, eventMap);
        } catch (Exception ignore) {
        }
    }

    private static long findWritableCalendarId(Context context) {
        ContentResolver resolver = context.getContentResolver();
        String[] projection = new String[] {
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY
        };
        String selection = CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= ?";
        String[] selectionArgs = new String[] {
            String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)
        };
        Cursor cursor = null;
        try {
            cursor = resolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    CalendarContract.Calendars.IS_PRIMARY + " DESC"
            );
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } catch (Exception ignore) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return -1L;
    }

    private static long parseDueToUtcMidnight(String due) {
        SimpleDateFormat sdf = new SimpleDateFormat(DUE_PATTERN, Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone(UTC_TIMEZONE));
        sdf.setLenient(false);
        try {
            Date date = sdf.parse(due);
            if (date == null) {
                return -1L;
            }
            return date.getTime();
        } catch (ParseException ignore) {
            return -1L;
        }
    }

    private static boolean eventExists(ContentResolver resolver, long eventId) {
        Cursor cursor = null;
        try {
            Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
            cursor = resolver.query(
                    uri,
                    new String[] { CalendarContract.Events._ID, CalendarContract.Events.DELETED },
                    null,
                    null,
                    null
            );
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(1) == 0;
            }
        } catch (Exception ignore) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return false;
    }

    private static long insertAllDayEvent(
            ContentResolver resolver,
            long calendarId,
            String title,
            String note,
            long startUtc
    ) {
        ContentValues values = buildEventValues(title, note, startUtc);
        values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        Uri inserted = resolver.insert(CalendarContract.Events.CONTENT_URI, values);
        if (inserted == null) {
            return -1L;
        }
        return ContentUris.parseId(inserted);
    }

    private static void updateAllDayEvent(
            ContentResolver resolver,
            long eventId,
            String title,
            String note,
            long startUtc
    ) {
        Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
        ContentValues values = buildEventValues(title, note, startUtc);
        resolver.update(uri, values, null, null);
    }

    private static ContentValues buildEventValues(String title, String note, long startUtc) {
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.TITLE, title);
        values.put(CalendarContract.Events.DESCRIPTION, note);
        values.put(CalendarContract.Events.DTSTART, startUtc);
        values.put(CalendarContract.Events.DTEND, startUtc + ONE_DAY_MILLIS);
        values.put(CalendarContract.Events.ALL_DAY, 1);
        values.put(CalendarContract.Events.EVENT_TIMEZONE, UTC_TIMEZONE);
        return values;
    }

    private static void insertReminder(ContentResolver resolver, long eventId) {
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Reminders.EVENT_ID, eventId);
        values.put(CalendarContract.Reminders.MINUTES, DEFAULT_REMINDER_MINUTES);
        values.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
        resolver.insert(CalendarContract.Reminders.CONTENT_URI, values);
    }

    private static void deleteEvent(ContentResolver resolver, long eventId) {
        try {
            Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
            resolver.delete(uri, null, null);
        } catch (Exception ignore) {
        }
    }

    private static Map<String, Long> loadEventMap(Context context) {
        Map<String, Long> map = new HashMap<>();
        String raw = getPrefs(context).getString(KEY_EVENT_MAP, "{}");
        try {
            JSONObject obj = new JSONObject(raw);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                long val = obj.optLong(key, -1L);
                if (val > 0L) {
                    map.put(key, val);
                }
            }
        } catch (Exception ignore) {
        }
        return map;
    }

    private static void saveEventMap(Context context, Map<String, Long> map) {
        JSONObject obj = new JSONObject();
        try {
            for (Map.Entry<String, Long> entry : map.entrySet()) {
                obj.put(entry.getKey(), entry.getValue());
            }
        } catch (Exception ignore) {
        }
        getPrefs(context).edit().putString(KEY_EVENT_MAP, obj.toString()).apply();
    }
}
