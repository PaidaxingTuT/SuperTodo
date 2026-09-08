package com.dax.supertodo.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import com.dax.supertodo.R;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class WidgetDataManager {
    private static final String FILE_NAME = "supertodo_widget_data.json";
    private static final String PREF_NAME = "supertodo_widget_prefs";

    public static final String ACTION_WIDGET_CLICK = "com.dax.supertodo.ACTION_WIDGET_CLICK";
    public static final String ACTION_REFRESH_WIDGET = "com.dax.supertodo.ACTION_REFRESH_WIDGET";

    public static final String GROUP_ALL = "all";
    public static final String GROUP_SCENE = "scene";
    public static final String GROUP_TIME = "time";

    public static final String SORT_DEFAULT = "default";
    public static final String SORT_STAR = "star";
    public static final String SORT_DUE = "due";
    public static final String SORT_COST = "cost";
    public static final String SORT_UNDONE_FIRST = "undone_first";

    private static volatile String sCachedJson = null;
    private static final java.util.concurrent.ExecutorService sIoExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    public static synchronized void saveWidgetData(Context context, String json) {
        if (context == null || json == null) return;
        sCachedJson = json;
        final Context appContext = context.getApplicationContext();
        sIoExecutor.execute(() -> {
            try {
                File dir = appContext.getFilesDir();
                File file = new File(dir, FILE_NAME);
                File tempFile = new File(dir, FILE_NAME + ".tmp");
                FileOutputStream fos = new FileOutputStream(tempFile);
                fos.write(json.getBytes(StandardCharsets.UTF_8));
                fos.flush();
                fos.close();
                if (tempFile.exists()) {
                    tempFile.renameTo(file);
                }
            } catch (Exception ignore) {}
        });
    }

    public static synchronized String getWidgetData(Context context) {
        if (sCachedJson != null && !sCachedJson.isEmpty()) {
            return sCachedJson;
        }
        if (context == null) return "";
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            sCachedJson = sb.toString();
        } catch (Exception ignore) {}
        return sCachedJson != null ? sCachedJson : "";
    }

    public static List<TodoItem> loadAllItems(Context context) {
        List<TodoItem> list = new ArrayList<>();
        String json = getWidgetData(context);
        if (json.isEmpty()) return list;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray items = root.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject obj = items.optJSONObject(i);
                    if (obj != null) {
                        list.add(TodoItem.fromJson(obj));
                    }
                }
            }
        } catch (Exception ignore) {}
        return list;
    }

    public static List<String> loadTags(Context context, String tagKey) {
        List<String> list = new ArrayList<>();
        String json = getWidgetData(context);
        if (!json.isEmpty()) {
            try {
                JSONObject root = new JSONObject(json);
                JSONArray arr = root.optJSONArray(tagKey);
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        String s = arr.optString(i, "");
                        if (!s.isEmpty()) list.add(s);
                    }
                }
            } catch (Exception ignore) {}
        }
        if (list.isEmpty()) {
            if ("scenes".equals(tagKey)) {
                Collections.addAll(list, "家里", "学校", "出差", "网上", "线下");
            } else if ("times".equals(tagKey)) {
                Collections.addAll(list, "今年", "明年", "以后再说");
            } else if ("types".equals(tagKey)) {
                Collections.addAll(list, "购物", "待办", "计划", "旅游", "愿望");
            }
        }
        return list;
    }

    public static List<String> loadAvailableScenes(Context context) {
        return loadTags(context, "scenes");
    }

    public static List<String> loadAvailableTimes(Context context) {
        return loadTags(context, "times");
    }

    public static List<String> loadAvailableTypes(Context context) {
        return loadTags(context, "types");
    }

    public static boolean isWidgetRemoveDone(Context context) {
        if (context == null) return false;
        String json = getWidgetData(context);
        if (json.isEmpty()) return false;
        try {
            JSONObject root = new JSONObject(json);
            return isWidgetRemoveDone(root);
        } catch (Exception ignore) {
            return false;
        }
    }

    public static boolean isWidgetRemoveDone(JSONObject root) {
        if (root == null) return false;
        return root.optBoolean("widgetRemoveDone", false);
    }

    public static synchronized boolean toggleItemDone(Context context, String itemId) {
        if (context == null || itemId == null || itemId.isEmpty()) return false;
        String json = getWidgetData(context);
        if (json.isEmpty()) return false;
        try {
            JSONObject root = new JSONObject(json);
            boolean removeDone = isWidgetRemoveDone(root);
            JSONArray items = root.optJSONArray("items");
            if (items == null) return false;
            boolean updated = false;
            boolean nextState = false;
            for (int i = 0; i < items.length(); i++) {
                JSONObject obj = items.optJSONObject(i);
                if (obj != null && itemId.equals(obj.optString("id"))) {
                    boolean cur = obj.optBoolean("done", false);
                    nextState = !cur;
                    obj.put("done", nextState);
                    updated = true;
                    break;
                }
            }
            // 同步更新四象限中包含的同 ID 事项状态
            JSONObject qw = root.optJSONObject("quadrantWidget");
            if (qw != null) {
                String[] qKeys = new String[] { "q1", "q2", "q3", "q4" };
                for (String qKey : qKeys) {
                    JSONArray arr = qw.optJSONArray(qKey);
                    if (arr == null) continue;
                    for (int j = arr.length() - 1; j >= 0; j--) {
                        JSONObject qObj = arr.optJSONObject(j);
                        if (qObj != null && itemId.equals(qObj.optString("id"))) {
                            if (removeDone && nextState) {
                                arr.remove(j);
                            } else {
                                qObj.put("done", nextState);
                            }
                            updated = true;
                        }
                    }
                }
            }
            // 同步更新 2x2 自定义清单中包含的同 ID 事项状态
            JSONArray w2 = root.optJSONArray("widget2x2");
            if (w2 != null) {
                for (int m = 0; m < w2.length(); m++) {
                    JSONObject wObj = w2.optJSONObject(m);
                    if (wObj != null && itemId.equals(wObj.optString("id"))) {
                        wObj.put("done", nextState);
                        updated = true;
                    }
                }
            }
            if (updated) {
                saveWidgetData(context, root.toString());
                return true;
            }
        } catch (Exception ignore) {}
        return false;
    }

    public static synchronized List<TodoItem> load2x2Items(Context context) {
        List<TodoItem> list = new ArrayList<>();
        if (context == null) return list;
        String json = getWidgetData(context);
        if (json.isEmpty()) return list;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("widget2x2");
            if (arr != null && arr.length() > 0) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj != null) {
                        list.add(TodoItem.fromJson(obj));
                    }
                }
                return list;
            }
            // 若尚无 widget2x2，默认从 items 取未完成事项初始化
            JSONArray items = root.optJSONArray("items");
            if (items != null) {
                JSONArray new2x2 = new JSONArray();
                for (int i = 0; i < items.length(); i++) {
                    JSONObject obj = items.optJSONObject(i);
                    if (obj != null && !obj.optBoolean("done", false)) {
                        TodoItem it = TodoItem.fromJson(obj);
                        list.add(it);
                        new2x2.put(obj);
                        if (list.size() >= 10) break;
                    }
                }
                if (new2x2.length() > 0) {
                    root.put("widget2x2", new2x2);
                    saveWidgetData(context, root.toString());
                }
            }
        } catch (Exception ignore) {}
        return list;
    }

    public static synchronized boolean completeNext2x2Item(Context context) {
        if (context == null) return false;
        String json = getWidgetData(context);
        if (json.isEmpty()) return false;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("widget2x2");
            if (arr == null || arr.length() == 0) {
                load2x2Items(context);
                json = getWidgetData(context);
                root = new JSONObject(json);
                arr = root.optJSONArray("widget2x2");
            }
            if (arr == null || arr.length() == 0) return false;

            String completedId = null;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null && !obj.optBoolean("done", false)) {
                    obj.put("done", true);
                    completedId = obj.optString("id");
                    break;
                }
            }
            if (completedId != null) {
                // 同步更新 items
                JSONArray items = root.optJSONArray("items");
                if (items != null) {
                    for (int j = 0; j < items.length(); j++) {
                        JSONObject it = items.optJSONObject(j);
                        if (it != null && completedId.equals(it.optString("id"))) {
                            it.put("done", true);
                            break;
                        }
                    }
                }
                // 同步更新四象限
                JSONObject qw = root.optJSONObject("quadrantWidget");
                if (qw != null) {
                    String[] qKeys = new String[] { "q1", "q2", "q3", "q4" };
                    for (String qKey : qKeys) {
                        JSONArray qArr = qw.optJSONArray(qKey);
                        if (qArr == null) continue;
                        for (int k = 0; k < qArr.length(); k++) {
                            JSONObject qObj = qArr.optJSONObject(k);
                            if (qObj != null && completedId.equals(qObj.optString("id"))) {
                                qObj.put("done", true);
                            }
                        }
                    }
                }
                saveWidgetData(context, root.toString());
                return true;
            }
        } catch (Exception ignore) {}
        return false;
    }

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void saveWidgetConfig(Context context, int widgetId, String groupBy, String category, String typeCategory, String sortKey, boolean sortAsc, boolean hideDone) {
        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.putString("config_group_by_" + widgetId, groupBy != null ? groupBy : GROUP_SCENE);
        editor.putString("config_category_" + widgetId, category != null ? category : "全部");
        editor.putString("config_type_category_" + widgetId, typeCategory != null ? typeCategory : "全部标签");
        editor.putString("config_sort_key_" + widgetId, sortKey != null ? sortKey : SORT_DEFAULT);
        editor.putBoolean("config_sort_asc_" + widgetId, sortAsc);
        editor.putBoolean("config_hide_done_" + widgetId, hideDone);
        editor.apply();
    }

    public static String getWidgetGroupBy(Context context, int widgetId) {
        return getPrefs(context).getString("config_group_by_" + widgetId, GROUP_SCENE);
    }

    public static String getWidgetCategory(Context context, int widgetId) {
        return getPrefs(context).getString("config_category_" + widgetId, "全部");
    }

    public static String getWidgetTypeCategory(Context context, int widgetId) {
        return getPrefs(context).getString("config_type_category_" + widgetId, "全部标签");
    }

    public static String getWidgetSortKey(Context context, int widgetId) {
        return getPrefs(context).getString("config_sort_key_" + widgetId, SORT_DEFAULT);
    }

    public static boolean getWidgetSortAsc(Context context, int widgetId) {
        return getPrefs(context).getBoolean("config_sort_asc_" + widgetId, true);
    }

    public static boolean getWidgetHideDone(Context context, int widgetId) {
        return getPrefs(context).getBoolean("config_hide_done_" + widgetId, false);
    }

    public static void removeWidgetConfig(Context context, int widgetId) {
        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.remove("config_group_by_" + widgetId);
        editor.remove("config_category_" + widgetId);
        editor.remove("config_type_category_" + widgetId);
        editor.remove("config_sort_key_" + widgetId);
        editor.remove("config_sort_asc_" + widgetId);
        editor.remove("config_hide_done_" + widgetId);
        editor.apply();
    }

    public static String getWidgetFilterTitle(Context context, int widgetId) {
        String groupBy = getWidgetGroupBy(context, widgetId);
        String category = getWidgetCategory(context, widgetId);
        String typeCategory = getWidgetTypeCategory(context, widgetId);
        StringBuilder title = new StringBuilder();
        if (GROUP_SCENE.equals(groupBy) && !"全部".equals(category) && category != null && !category.trim().isEmpty()) {
            title.append(category);
        } else if (GROUP_TIME.equals(groupBy) && !"全部".equals(category) && category != null && !category.trim().isEmpty()) {
            title.append(category);
        }
        if (!"全部标签".equals(typeCategory) && !"全部".equals(typeCategory) && typeCategory != null && !typeCategory.trim().isEmpty()) {
            if (title.length() > 0) title.append(" · ");
            title.append(typeCategory);
        }
        return title.length() > 0 ? title.toString() : "全部事项";
    }

    public static List<TodoItem> loadTasksForWidget(Context context, int widgetId) {
        List<TodoItem> all = loadAllItems(context);
        String groupBy = getWidgetGroupBy(context, widgetId);
        String category = getWidgetCategory(context, widgetId);
        String typeCategory = getWidgetTypeCategory(context, widgetId);
        String sortKey = getWidgetSortKey(context, widgetId);
        final boolean sortAsc = getWidgetSortAsc(context, widgetId);
        boolean removeDone = isWidgetRemoveDone(context);
        boolean hideDone = getWidgetHideDone(context, widgetId) || removeDone;

        List<TodoItem> filtered = new ArrayList<>();
        for (TodoItem it : all) {
            if (hideDone && it.done) continue;

            boolean matchesGroup = true;
            if (GROUP_SCENE.equals(groupBy)) {
                if ("未分组".equals(category)) {
                    matchesGroup = it.scene == null || it.scene.isEmpty();
                } else if (!"全部".equals(category)) {
                    matchesGroup = category.equals(it.scene);
                }
            } else if (GROUP_TIME.equals(groupBy)) {
                if ("未分组".equals(category)) {
                    matchesGroup = it.time == null || it.time.isEmpty();
                } else if (!"全部".equals(category)) {
                    matchesGroup = category.equals(it.time);
                }
            }
            boolean matchesType = "全部标签".equals(typeCategory) || "全部".equals(typeCategory)
                || typeCategory == null || typeCategory.trim().isEmpty() || it.types.contains(typeCategory);
            if (matchesGroup && matchesType) {
                filtered.add(it);
            }
        }

        Collections.sort(filtered, new Comparator<TodoItem>() {
            @Override
            public int compare(TodoItem a, TodoItem b) {
                if (SORT_UNDONE_FIRST.equals(sortKey)) {
                    if (a.done != b.done) {
                        return a.done ? 1 : -1;
                    }
                    return Long.compare(a.created, b.created);
                }
                if (SORT_STAR.equals(sortKey)) {
                    int cmp = Integer.compare(a.star, b.star);
                    if (cmp != 0) return sortAsc ? cmp : -cmp;
                } else if (SORT_DUE.equals(sortKey)) {
                    if (a.due.isEmpty() && b.due.isEmpty()) return 0;
                    if (a.due.isEmpty()) return 1;
                    if (b.due.isEmpty()) return -1;
                    int cmp = a.due.compareTo(b.due);
                    if (cmp != 0) return sortAsc ? cmp : -cmp;
                } else if (SORT_COST.equals(sortKey)) {
                    int cmp = Double.compare(a.cost, b.cost);
                    if (cmp != 0) return sortAsc ? cmp : -cmp;
                }
                int orderCmp = Integer.compare(a.order, b.order);
                if (orderCmp != 0) return sortAsc ? orderCmp : -orderCmp;
                return Long.compare(a.created, b.created);
            }
        });

        return filtered;
    }

    public static List<TodoItem> loadQuadrantItems(Context context, String qKey) {
        List<TodoItem> list = new ArrayList<>();
        if (context == null || qKey == null) return list;
        String json = getWidgetData(context);
        if (json.isEmpty()) return list;
        try {
            JSONObject root = new JSONObject(json);
            boolean removeDone = isWidgetRemoveDone(root);
            JSONObject qw = root.optJSONObject("quadrantWidget");
            if (qw != null) {
                JSONArray arr = qw.optJSONArray(qKey);
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.optJSONObject(i);
                        if (obj != null) {
                            TodoItem it = TodoItem.fromJson(obj);
                            if (removeDone && it.done) {
                                continue;
                            }
                            list.add(it);
                        }
                    }
                }
            }
        } catch (Exception ignore) {}
        return list;
    }

    public static synchronized boolean toggleQuadrantItemDone(Context context, String qKey, String itemId) {
        if (context == null || qKey == null || itemId == null) return false;
        String json = getWidgetData(context);
        if (json.isEmpty()) return false;
        try {
            JSONObject root = new JSONObject(json);
            boolean removeDone = isWidgetRemoveDone(root);
            JSONObject qw = root.optJSONObject("quadrantWidget");
            if (qw == null) return false;
            JSONArray arr = qw.optJSONArray(qKey);
            if (arr == null) return false;
            boolean updated = false;
            boolean nextState = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null && itemId.equals(obj.optString("id"))) {
                    boolean cur = obj.optBoolean("done", false);
                    nextState = !cur;
                    if (removeDone && nextState) {
                        arr.remove(i);
                    } else {
                        obj.put("done", nextState);
                    }
                    updated = true;
                    break;
                }
            }
            if (updated) {
                // 保持与主列表对应待办事项完成状态双向同步
                JSONArray rootItems = root.optJSONArray("items");
                if (rootItems != null) {
                    for (int j = 0; j < rootItems.length(); j++) {
                        JSONObject rootIt = rootItems.optJSONObject(j);
                        if (rootIt != null && itemId.equals(rootIt.optString("id"))) {
                            rootIt.put("done", nextState);
                            break;
                        }
                    }
                }
                // 同步更新其他象限中可能存在的同 ID 事项
                String[] qKeys = new String[] { "q1", "q2", "q3", "q4" };
                for (String otherKey : qKeys) {
                    if (otherKey.equals(qKey)) continue;
                    JSONArray otherArr = qw.optJSONArray(otherKey);
                    if (otherArr == null) continue;
                    for (int k = otherArr.length() - 1; k >= 0; k--) {
                        JSONObject oObj = otherArr.optJSONObject(k);
                        if (oObj != null && itemId.equals(oObj.optString("id"))) {
                            if (removeDone && nextState) {
                                otherArr.remove(k);
                            } else {
                                oObj.put("done", nextState);
                            }
                        }
                    }
                }
                saveWidgetData(context, root.toString());
                return true;
            }
        } catch (Exception ignore) {}
        return false;
    }

    public static void notifyAllWidgets(Context context) {
        if (context == null) return;
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            if (mgr == null) return;

            ComponentName cn4x2 = new ComponentName(context, TodoWidget4x2Provider.class);
            int[] ids4x2 = mgr.getAppWidgetIds(cn4x2);
            if (ids4x2 != null && ids4x2.length > 0) {
                mgr.notifyAppWidgetViewDataChanged(ids4x2, R.id.widget_list);
                for (int id : ids4x2) {
                    TodoWidget4x2Provider.updateAppWidget(context, mgr, id);
                }
            }

            ComponentName cn4x4 = new ComponentName(context, TodoWidget4x4Provider.class);
            int[] ids4x4 = mgr.getAppWidgetIds(cn4x4);
            if (ids4x4 != null && ids4x4.length > 0) {
                mgr.notifyAppWidgetViewDataChanged(ids4x4, R.id.widget_list);
                for (int id : ids4x4) {
                    TodoWidget4x4Provider.updateAppWidget(context, mgr, id);
                }
            }

            ComponentName cn2x2 = new ComponentName(context, TodoWidget2x2Provider.class);
            int[] ids2x2 = mgr.getAppWidgetIds(cn2x2);
            if (ids2x2 != null && ids2x2.length > 0) {
                for (int id : ids2x2) {
                    TodoWidget2x2Provider.updateAppWidget(context, mgr, id);
                }
            }

            ComponentName cnQuad = new ComponentName(context, TodoWidgetQuadrantProvider.class);
            int[] idsQuad = mgr.getAppWidgetIds(cnQuad);
            if (idsQuad != null && idsQuad.length > 0) {
                for (int id : idsQuad) {
                    TodoWidgetQuadrantProvider.updateAppWidget(context, mgr, id);
                }
            }

        } catch (Exception ignore) {}
    }

    public static int getWidgetWidth(Context context, AppWidgetManager mgr, int appWidgetId, int defaultWidth) {
        if (mgr == null || context == null) return defaultWidth;
        try {
            Bundle options = mgr.getAppWidgetOptions(appWidgetId);
            if (options == null) return defaultWidth;
            boolean isLandscape = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            int w = isLandscape ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
                                : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            if (w <= 0) {
                w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            }
            return w > 0 ? w : defaultWidth;
        } catch (Throwable t) {
            return defaultWidth;
        }
    }

    public static int getWidgetHeight(Context context, AppWidgetManager mgr, int appWidgetId, int defaultHeight) {
        if (mgr == null || context == null) return defaultHeight;
        try {
            Bundle options = mgr.getAppWidgetOptions(appWidgetId);
            if (options == null) return defaultHeight;
            boolean isLandscape = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            int h = isLandscape ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                                : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            if (h <= 0) {
                h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
            }
            return h > 0 ? h : defaultHeight;
        } catch (Throwable t) {
            return defaultHeight;
        }
    }
}
