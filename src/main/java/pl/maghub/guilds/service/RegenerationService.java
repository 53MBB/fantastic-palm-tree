package pl.maghub.guilds.service;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.maghub.guilds.MAGGuildsPlugin;
import pl.maghub.guilds.model.Guild;
import pl.maghub.guilds.util.Items;
import pl.maghub.guilds.util.Text;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class RegenerationService {
    public record Snapshot(String world, int x, int y, int z, String material, String data) { }
    private static final class Session {
        private final Guild guild; private final ArrayDeque<Snapshot> queue; private final int total; private final long started; private final long estimatedEnd; private final BossBar bar;
        private Session(Guild guild, Collection<Snapshot> snapshots, long estimatedSeconds, BossBar bar) { this.guild = guild; this.queue = new ArrayDeque<>(snapshots); this.total = snapshots.size(); this.started = System.currentTimeMillis(); this.estimatedEnd = started + estimatedSeconds * 1000L; this.bar = bar; }
    }

    private final MAGGuildsPlugin plugin;
    private final GuildService guilds;
    private final MessageService messages;
    private final HashMap<String, LinkedHashMap<String, Snapshot>> damage = new HashMap<>();
    private final HashMap<String, Session> sessions = new HashMap<>();
    private final HashSet<String> dirty = new HashSet<>();
    private final File directory;

    public RegenerationService(MAGGuildsPlugin plugin, GuildService guilds, MessageService messages) {
        this.plugin = plugin; this.guilds = guilds; this.messages = messages;
        this.directory = new File(plugin.getDataFolder(), "regeneration");
        if (!directory.exists()) directory.mkdirs();
        for (Guild guild : guilds.all()) load(guild.tag());
    }

    public boolean active(String tag) { return sessions.containsKey(tag.toUpperCase(Locale.ROOT)); }
    public int damaged(String tag) { return damage.getOrDefault(tag.toUpperCase(Locale.ROOT), new LinkedHashMap<>()).size(); }

    public void record(Block block, Guild guild) {
        if (!plugin.getConfig().getBoolean("regeneration.enabled", true) || guild == null || active(guild.tag())) return;
        LinkedHashMap<String, Snapshot> map = damage.computeIfAbsent(guild.tag(), k -> new LinkedHashMap<>());
        int max = plugin.getConfig().getInt("regeneration.maximum-saved-blocks-per-guild", 200000);
        if (map.size() >= max) return;
        String key = key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        map.putIfAbsent(key, new Snapshot(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), block.getType().name(), block.getBlockData().getAsString()));
        dirty.add(guild.tag());
    }

    public boolean start(Player player, Guild guild) {
        if (!plugin.getConfig().getBoolean("regeneration.enabled", true)) { messages.send(player, "regeneration-disabled"); return false; }
        if (active(guild.tag())) { Session session = sessions.get(guild.tag()); messages.send(player, "regeneration-active", "time", Text.duration(Math.max(0, (session.estimatedEnd - System.currentTimeMillis()) / 1000))); return false; }
        LinkedHashMap<String, Snapshot> map = damage.get(guild.tag());
        if (map == null || map.isEmpty()) { messages.send(player, "regeneration-empty"); return false; }
        int blocks = map.size();
        int perItem = Math.max(1, plugin.getConfig().getInt("regeneration.cost.blocks-per-item", 16));
        int amount = Math.max(plugin.getConfig().getInt("regeneration.cost.minimum", 1), (int) Math.ceil(blocks / (double) perItem));
        Material material = Material.matchMaterial(plugin.getConfig().getString("regeneration.cost.material", "EMERALD_BLOCK"));
        if (material == null) material = Material.EMERALD_BLOCK;
        int perTick = Math.max(1, plugin.getConfig().getInt("regeneration.blocks-per-tick", 4));
        long seconds = Math.max(1, (long) Math.ceil(blocks / (perTick * 20.0)));
        String display = plugin.getConfig().getString("regeneration.cost.material-display", Text.plainMaterial(material.name()));
        if (!player.hasPermission("magguilds.regeneration.bypass-cost") && Items.count(player.getInventory(), material) < amount) {
            messages.send(player, "regeneration-missing-cost", "blocks", blocks, "time", Text.duration(seconds), "amount", amount, "material", display); return false;
        }
        if (!player.hasPermission("magguilds.regeneration.bypass-cost")) Items.remove(player.getInventory(), material, amount);
        BarColor color; BarStyle style;
        try { color = BarColor.valueOf(plugin.getConfig().getString("regeneration.bossbar.color", "PURPLE")); } catch (Exception ignored) { color = BarColor.PURPLE; }
        try { style = BarStyle.valueOf(plugin.getConfig().getString("regeneration.bossbar.style", "SEGMENTED_10")); } catch (Exception ignored) { style = BarStyle.SEGMENTED_10; }
        BossBar bar = Bukkit.createBossBar("", color, style);
        Session session = new Session(guild, map.values(), seconds, bar);
        sessions.put(guild.tag(), session);
        messages.send(player, "regeneration-started", "blocks", blocks, "time", Text.duration(seconds), "amount", amount, "material", display);
        updateBar(session);
        return true;
    }

    public void tick() {
        int perTick = Math.max(1, plugin.getConfig().getInt("regeneration.blocks-per-tick", 4));
        Iterator<Map.Entry<String, Session>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Session> entry = iterator.next(); Session session = entry.getValue();
            for (int i = 0; i < perTick && !session.queue.isEmpty(); i++) restore(session.queue.poll());
            updateBar(session);
            if (session.queue.isEmpty()) {
                session.bar.removeAll(); iterator.remove(); damage.remove(session.guild.tag()); dirty.add(session.guild.tag()); save(session.guild.tag());
                for (UUID member : session.guild.members()) { Player online = Bukkit.getPlayer(member); if (online != null) messages.send(online, "regeneration-completed", "tag", session.guild.tag(), "blocks", session.total); }
            }
        }
    }

    private void restore(Snapshot snapshot) {
        World world = Bukkit.getWorld(snapshot.world()); if (world == null) return;
        Block block = world.getBlockAt(snapshot.x(), snapshot.y(), snapshot.z());
        try { block.setBlockData(Bukkit.createBlockData(snapshot.data()), false); }
        catch (Exception exception) { Material material = Material.matchMaterial(snapshot.material()); block.setType(material == null ? Material.AIR : material, false); }
    }

    private void updateBar(Session session) {
        int remaining = session.queue.size(); int restored = session.total - remaining;
        double progress = session.total == 0 ? 1.0 : restored / (double) session.total;
        long seconds = Math.max(0, (session.estimatedEnd - System.currentTimeMillis()) / 1000L);
        String raw = plugin.getConfig().getString("regeneration.bossbar.title", "Regeneracja %tag% %percent%% %remaining% %time%");
        String title = Text.smallCapsPreservingTokens(raw)
                .replace("%tag%", session.guild.tag())
                .replace("%percent%", String.format(Locale.US, "%.1f", progress * 100.0))
                .replace("%remaining%", String.valueOf(remaining))
                .replace("%restored%", String.valueOf(restored))
                .replace("%blocks%", String.valueOf(session.total))
                .replace("%time%", Text.duration(seconds));
        session.bar.setTitle(Text.color(title)); session.bar.setProgress(Math.max(0, Math.min(1, progress)));
        for (UUID member : session.guild.members()) { Player player = Bukkit.getPlayer(member); if (player != null && !session.bar.getPlayers().contains(player)) session.bar.addPlayer(player); }
        session.bar.getPlayers().removeIf(player -> !session.guild.isMember(player.getUniqueId()) || !player.isOnline());
    }

    public void onJoin(Player player) { Guild guild = guilds.byPlayer(player.getUniqueId()); if (guild != null && sessions.containsKey(guild.tag())) sessions.get(guild.tag()).bar.addPlayer(player); }
    public void onQuit(Player player) { for (Session session : sessions.values()) session.bar.removePlayer(player); }

    public void flushDirty() { for (String tag : new HashSet<>(dirty)) save(tag); dirty.clear(); }
    private void load(String tag) {
        File file = new File(directory, tag + ".yml"); YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        LinkedHashMap<String, Snapshot> map = new LinkedHashMap<>();
        for (Map<?, ?> raw : yaml.getMapList("blocks")) {
            try {
                Snapshot snapshot = new Snapshot(String.valueOf(raw.get("world")), ((Number) raw.get("x")).intValue(), ((Number) raw.get("y")).intValue(), ((Number) raw.get("z")).intValue(), String.valueOf(raw.get("material")), String.valueOf(raw.get("data")));
                map.put(key(snapshot.world(), snapshot.x(), snapshot.y(), snapshot.z()), snapshot);
            } catch (Exception ignored) { }
        }
        if (!map.isEmpty()) damage.put(tag, map);
    }
    private void save(String tag) {
        File file = new File(directory, tag + ".yml"); LinkedHashMap<String, Snapshot> map = damage.get(tag);
        if (map == null || map.isEmpty()) { if (file.exists()) file.delete(); return; }
        YamlConfiguration yaml = new YamlConfiguration(); List<Map<String, Object>> list = new ArrayList<>();
        for (Snapshot s : map.values()) { Map<String, Object> row = new LinkedHashMap<>(); row.put("world", s.world()); row.put("x", s.x()); row.put("y", s.y()); row.put("z", s.z()); row.put("material", s.material()); row.put("data", s.data()); list.add(row); }
        yaml.set("blocks", list); try { yaml.save(file); } catch (IOException e) { plugin.getLogger().severe("Nie mozna zapisac regeneracji " + tag + ": " + e.getMessage()); }
    }
    private static String key(String world, int x, int y, int z) { return world + ';' + x + ';' + y + ';' + z; }
    public void shutdown() { for (Session session : sessions.values()) session.bar.removeAll(); flushDirty(); sessions.clear(); }
}
