package com.dax.supertodo.widget;

import org.json.JSONObject;

public class TodoItem {
    public String id = "";
    public String title = "";
    public String note = "";
    public String type = "";
    public String scene = "";
    public String time = "";
    public boolean done = false;
    public boolean hasCost = false;
    public double cost = 0;
    public int star = 0;
    public String due = "";
    public long created = 0;
    public int order = 0;

    public static TodoItem fromJson(JSONObject obj) {
        TodoItem item = new TodoItem();
        if (obj == null) return item;
        item.id = obj.optString("id", "");
        item.title = obj.optString("title", "");
        item.note = obj.optString("note", "");
        item.type = obj.optString("type", "");
        item.scene = obj.optString("scene", "");
        item.time = obj.optString("time", "");
        item.done = obj.optBoolean("done", false);
        if (obj.has("cost") && !obj.isNull("cost")) {
            try {
                item.cost = obj.getDouble("cost");
                item.hasCost = true;
            } catch (Exception ignore) {}
        }
        item.star = obj.optInt("star", 0);
        item.due = obj.optString("due", "");
        item.created = obj.optLong("created", 0);
        item.order = obj.optInt("order", 0);
        return item;
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("title", title);
            obj.put("note", note);
            obj.put("type", type);
            obj.put("scene", scene);
            obj.put("time", time);
            obj.put("done", done);
            if (cost > 0) obj.put("cost", cost);
            if (star > 0) obj.put("star", star);
            if (!due.isEmpty()) obj.put("due", due);
            obj.put("created", created);
            obj.put("order", order);
        } catch (Exception ignore) {}
        return obj;
    }
}
