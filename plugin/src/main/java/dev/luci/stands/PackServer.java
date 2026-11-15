package dev.luci.stands;

import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Serves two resource packs over HTTP and sends them on join:
 *   /sounds.zip — the stand sound clips bundled in this plugin
 *   /models.zip — BetterModel's generated pack (stand models + skins)
 */
public final class PackServer implements Listener {
    private final StandsPlugin plugin;
    private HttpServer http;
    private Path soundsZip;

    public PackServer(StandsPlugin plugin) { this.plugin = plugin; }

    private Path modelsZip() { return plugin.getDataFolder().toPath().getParent().resolve("BetterModel/build.zip"); }

    public void start() {
        var c = plugin.getConfig();
        if (!c.getBoolean("pack.enabled", true)) return;
        try {
            soundsZip = plugin.getDataFolder().toPath().resolve("sounds.zip");
            buildSoundsZip(soundsZip);
            http = HttpServer.create(new InetSocketAddress(c.getInt("pack.port", 8765)), 0);
            http.createContext("/", ex -> {
                String path = ex.getRequestURI().getPath();
                Path file = path.equals("/sounds.zip") ? soundsZip : path.equals("/models.zip") ? modelsZip() : null;
                if (file == null || !Files.exists(file)) { ex.sendResponseHeaders(404, -1); ex.close(); return; }
                byte[] body = Files.readAllBytes(file);
                ex.getResponseHeaders().add("Content-Type", "application/zip");
                ex.sendResponseHeaders(200, body.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(body); }
            });
            http.start();
            plugin.getLogger().info("Resource packs served on port " + c.getInt("pack.port", 8765));
            if (c.getString("pack.public-url", "").contains("localhost"))
                plugin.getLogger().warning("pack.public-url is still localhost: other players won't get the stand models or sounds. Set it to http://<your server address>:" + c.getInt("pack.port", 8765) + " in config.yml.");
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't start the pack server: " + e.getMessage());
        }
    }

    public void stop() { if (http != null) http.stop(0); }

    private void buildSoundsZip(Path out) throws IOException {
        Files.createDirectories(out.getParent());
        File jar = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
        try (JarFile jf = new JarFile(jar); var zos = new ZipOutputStream(Files.newOutputStream(out))) {
            var entries = jf.entries();
            while (entries.hasMoreElements()) {
                var en = entries.nextElement();
                if (en.isDirectory() || !en.getName().startsWith("pack/")) continue;
                zos.putNextEntry(new ZipEntry(en.getName().substring("pack/".length())));
                try (InputStream in = jf.getInputStream(en)) { in.transferTo(zos); }
                zos.closeEntry();
            }
        }
    }

    private static String sha1(Path p) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(p)));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (http == null) return;
        var c = plugin.getConfig();
        String base = c.getString("pack.public-url", "http://localhost:8765").replaceAll("/+$", "");
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                var req = ResourcePackRequest.resourcePackRequest().replace(false)
                        .required(c.getBoolean("pack.required", false))
                        .prompt(Component.text("Stand models + sounds", NamedTextColor.GOLD));
                var packs = new java.util.ArrayList<ResourcePackInfo>();
                for (String name : new String[]{"models", "sounds"}) {
                    Path f = name.equals("models") ? modelsZip() : soundsZip;
                    if (!Files.exists(f)) continue;
                    String hash = sha1(f);
                    packs.add(ResourcePackInfo.resourcePackInfo(UUID.nameUUIDFromBytes(("stands-" + name).getBytes()),
                            URI.create(base + "/" + name + ".zip?h=" + hash), hash));
                }
                if (!packs.isEmpty()) e.getPlayer().sendResourcePacks(req.packs(packs).build());
            } catch (Exception ex) {
                plugin.getLogger().warning("Couldn't send packs: " + ex.getMessage());
            }
        }, 20L);
    }
}
