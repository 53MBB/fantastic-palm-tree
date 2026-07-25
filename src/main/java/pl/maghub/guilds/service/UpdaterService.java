package pl.maghub.guilds.service;

import org.bukkit.Bukkit;
import pl.maghub.guilds.MAGGuildsPlugin;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdaterService {
    private static final Pattern TAG = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"v?([^\\\"]+)\\\"");
    private static final Pattern ASSET = Pattern.compile("\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]*MAGGuilds-([^\\\"]+)\\.jar)\\\"");
    private static final Pattern HASH_ASSET = Pattern.compile("\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]*MAGGuilds-([^\\\"]+)\\.jar\\.sha256)\\\"");

    private final MAGGuildsPlugin plugin;

    public UpdaterService(MAGGuildsPlugin plugin) {
        this.plugin = plugin;
    }

    public void checkAsync() {
        if (!plugin.getConfig().getBoolean("auto-update.enabled", true)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::check);
    }

    private void check() {
        try {
            String api = plugin.getConfig().getString("auto-update.github-api",
                    "https://api.github.com/repos/53MBB/fantastic-palm-tree/releases/latest");
            String json = getText(api);
            Matcher tagMatcher = TAG.matcher(json);
            if (!tagMatcher.find()) {
                plugin.getLogger().warning("Auto-update: brak tag_name w odpowiedzi GitHub.");
                return;
            }
            String newest = tagMatcher.group(1);
            String current = plugin.getDescription().getVersion();
            if (compareVersions(newest, current) <= 0) {
                plugin.getLogger().info("Auto-update: uzywasz najnowszej wersji " + current + '.');
                return;
            }

            String jarUrl = null;
            Matcher assetMatcher = ASSET.matcher(json);
            while (assetMatcher.find()) {
                if (assetMatcher.group(2).equalsIgnoreCase(newest)) {
                    jarUrl = assetMatcher.group(1);
                    break;
                }
            }
            if (jarUrl == null) {
                plugin.getLogger().warning("Auto-update: wydanie " + newest + " nie zawiera MAGGuilds-" + newest + ".jar.");
                return;
            }

            String expectedHash = null;
            Matcher hashMatcher = HASH_ASSET.matcher(json);
            while (hashMatcher.find()) {
                if (hashMatcher.group(2).equalsIgnoreCase(newest)) {
                    expectedHash = getText(hashMatcher.group(1)).trim().split("\\s+")[0];
                    break;
                }
            }
            if (plugin.getConfig().getBoolean("auto-update.require-sha256", true) && (expectedHash == null || expectedHash.length() != 64)) {
                plugin.getLogger().warning("Auto-update: brak poprawnej sumy SHA-256. Aktualizacja anulowana.");
                return;
            }

            File updateDirectory = new File(plugin.getDataFolder().getParentFile(), "update");
            if (!updateDirectory.exists() && !updateDirectory.mkdirs()) {
                plugin.getLogger().warning("Auto-update: nie mozna utworzyc folderu plugins/update.");
                return;
            }
            File destination = new File(updateDirectory, "MAGGuilds-" + newest + ".jar");
            download(jarUrl, destination);
            if (expectedHash != null && !sha256(destination).equalsIgnoreCase(expectedHash)) {
                if (!destination.delete()) destination.deleteOnExit();
                plugin.getLogger().severe("Auto-update: suma SHA-256 nie pasuje. Pobrany plik zostal usuniety.");
                return;
            }
            plugin.getLogger().info("Auto-update: pobrano MAGGuilds " + newest + " do plugins/update. Wykonaj pelny restart serwera.");
        } catch (Exception exception) {
            plugin.getLogger().warning("Auto-update nie powiodl sie: " + exception.getMessage());
        }
    }

    private String getText(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(address).toURL().openConnection();
        connection.setConnectTimeout(plugin.getConfig().getInt("auto-update.connect-timeout-ms", 8000));
        connection.setReadTimeout(plugin.getConfig().getInt("auto-update.read-timeout-ms", 15000));
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "MAGGuilds-Updater/" + plugin.getDescription().getVersion());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append('\n');
            return output.toString();
        } finally {
            connection.disconnect();
        }
    }

    private void download(String address, File destination) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(address).toURL().openConnection();
        connection.setConnectTimeout(plugin.getConfig().getInt("auto-update.connect-timeout-ms", 8000));
        connection.setReadTimeout(plugin.getConfig().getInt("auto-update.read-timeout-ms", 15000));
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "MAGGuilds-Updater/" + plugin.getDescription().getVersion());
        try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
        } finally {
            connection.disconnect();
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value));
        return result.toString();
    }

    static int compareVersions(String left, String right) {
        String[] a = left.replaceAll("[^0-9.]", "").split("\\.");
        String[] b = right.replaceAll("[^0-9.]", "").split("\\.");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int av = i < a.length && !a[i].isBlank() ? Integer.parseInt(a[i]) : 0;
            int bv = i < b.length && !b[i].isBlank() ? Integer.parseInt(b[i]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }
}
