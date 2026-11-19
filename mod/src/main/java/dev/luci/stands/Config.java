package dev.luci.stands;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * config/stands.json. Written from the bundled defaults on first run. Keys missing from the user's file
 * (for example after an update adds new ones) fall back to the bundled defaults.
 */
public final class Config {
    private static Cfg current = Cfg.EMPTY;

    private Config() { }

    public static Cfg get() { return current; }

    public static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("stands.json"); }

    public static void load() {
        JsonObject defaults = bundled();
        JsonObject user = null;
        Path f = file();
        try {
            if (Files.exists(f)) {
                JsonElement e = JsonParser.parseString(Files.readString(f));
                if (e instanceof JsonObject o) user = o;
            } else {
                Files.createDirectories(f.getParent());
                Files.writeString(f, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(defaults));
            }
        } catch (Exception ex) {
            StandsMod.LOG.warn("Couldn't read {}: {} (using defaults)", f, ex.getMessage());
        }
        current = new Cfg(user == null ? defaults : merge(defaults, user));
    }

    private static JsonObject bundled() {
        try (InputStream in = Config.class.getResourceAsStream("/stands-default.json")) {
            if (in == null) return new JsonObject();
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException ex) {
            return new JsonObject();
        }
    }

    /** User values win; nested sections merge so a partial file still works. */
    private static JsonObject merge(JsonObject base, JsonObject over) {
        JsonObject out = base.deepCopy();
        for (Map.Entry<String, JsonElement> en : over.entrySet()) {
            JsonElement b = out.get(en.getKey());
            if (b instanceof JsonObject bo && en.getValue() instanceof JsonObject oo) out.add(en.getKey(), merge(bo, oo));
            else out.add(en.getKey(), en.getValue().deepCopy());
        }
        return out;
    }
}
