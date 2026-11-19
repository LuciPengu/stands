package dev.luci.stands;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;

/** Read-only view of a config section. Dotted paths ("power.vs-mobs") walk into nested sections. Never throws. */
public final class Cfg {
    public static final Cfg EMPTY = new Cfg(new JsonObject());
    private final JsonObject root;

    public Cfg(JsonObject root) { this.root = root == null ? new JsonObject() : root; }

    private JsonElement find(String path) {
        JsonElement cur = root;
        for (String part : path.split("\\.")) {
            if (!(cur instanceof JsonObject o) || !o.has(part)) return null;
            cur = o.get(part);
        }
        return cur;
    }

    public Cfg section(String path) {
        JsonElement e = find(path);
        return e instanceof JsonObject o ? new Cfg(o) : EMPTY;
    }

    public boolean has(String path) { return find(path) != null; }

    public double getDouble(String path, double def) {
        JsonElement e = find(path);
        return e instanceof JsonPrimitive p && p.isNumber() ? p.getAsDouble() : def;
    }

    public double getDouble(String path) { return getDouble(path, 0); }

    public int getInt(String path, int def) {
        JsonElement e = find(path);
        return e instanceof JsonPrimitive p && p.isNumber() ? (int) Math.round(p.getAsDouble()) : def;
    }

    public int getInt(String path) { return getInt(path, 0); }

    public boolean getBoolean(String path, boolean def) {
        JsonElement e = find(path);
        return e instanceof JsonPrimitive p && p.isBoolean() ? p.getAsBoolean() : def;
    }

    public String getString(String path, String def) {
        JsonElement e = find(path);
        return e instanceof JsonPrimitive p ? p.getAsString() : def;
    }

    public String getString(String path) { return getString(path, null); }

    public List<String> getStringList(String path) {
        List<String> out = new ArrayList<>();
        if (find(path) instanceof JsonArray a) for (JsonElement e : a) if (e instanceof JsonPrimitive p) out.add(p.getAsString());
        return out;
    }
}
