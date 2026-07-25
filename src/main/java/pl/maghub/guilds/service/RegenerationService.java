package pl.maghub.guilds.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pl.maghub.guilds.MAGGuildsPlugin;
import pl.maghub.guilds.model.Guild;
import pl.maghub.guilds.util.Items;
import pl.maghub.guilds.util.Text;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class RegenerationService {
    private record Key(String world, int x, int y, int z) {}
    private record Saved(String blockData) {}
    private static final class Session {
        final Guild guild;
        final ArrayDeque<Key> queue;
        final int total;
        final long startedAt;
        final BossBar bar;
        int restored;
        Session(Guild guild, ArrayDeque<Key> queue, BossBar bar) {
            this.guild = guild; this.queue = queue; this.total = queue.size(); this.startedAt = System.currentTimeMillis(); this.bar = bar;
        }
    }

    private final MAGGuildsPlugin plugin;
    private final GuildService guilds;
    private final MessageService messages;
    private final File folder;
    private final HashMap<String, LinkedHashMap<Key, Saved>> damage = new HashMap<>();
    private final HashMap<String, Session> sessions = new HashMap<>();
    private final HashSet<String> dirty = new HashSet<>();

    public RegenerationService(MAGGuildsPlugin plugin, GuildService guilds, MessageService messages) {
        this.plugin = plugin; this.guilds = guilds; this.messages = messages;
        this.folder = new File(plugin.getDataFolder(), "regeneration");
        if (!folder.exists()) folder.mkdirs();
    }

    public int damaged(Guild guild) { return changes(guild).size(); }
    public boolean active(Guild guild) { return sessions.containsKey(guild.tag()); }

    public void record(Block block, Guild guild) {
        if (!plugin.getConfig().getBoolean("regeneration.enabled", true) || block == null || guild == null) return;
        LinkedHashMap<Key, Saved> map = changes(guild);
        int limit = plugin.getConfig().getInt("regeneration.maximum-saved-blocks-per-guild", 200000);
        if (map.size() >= limit) return;
        Key key = new Key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (map.putIfAbsent(key, new Saved(block.getBlockData().getAsString())) == null) dirty.add(guild.tag());
    }

    public void start(Player player) {
        Guild guild = guilds.byPlayer(player.getUniqueId());
        if (guild == null) { messages.send(player, "no-guild"); return; }
        if (!guild.hasPermission(player.getUniqueId(), Guild.Permission.START_REGEN, guilds.defaultRole())) { messages.send(player, "no-permission"); return; }
        if (!plugin.getConfig().getBoolean("regeneration.enabled", true)) { messages.send(player, "regeneration-disabled"); return; }
        if (active(guild)) { messages.send(player, "regeneration-active", "time", Text.duration(estimatedSeconds(guild))); return; }
        LinkedHashMap<Key, Saved> map = changes(guild);
        if (map.isEmpty()) { messages.send(player, "regeneration-empty"); return; }

        int blocksPerItem = Math.max(1, plugin.getConfig().getInt("regeneration.cost.blocks-per-item", 16));
        int minimum = Math.max(0, plugin.getConfig().getInt("regeneration.cost.minimum", 1));
        int amount = Math.max(minimum, (int) Math.ceil(map.size() / (double) blocksPerItem));
        Material material = Material.matchMaterial(plugin.getConfig().getString("regeneration.cost.material", "EMERALD_BLOCK"));
        if (material == null) material = Material.EMERALD_BLOCK;
        String display = plugin.getConfig().getString("regeneration.cost.material-display", Text.plainMaterial(material.name()));
        long seconds = Math.max(1, (long) Math.ceil(map.size() / (double) Math.max(1, plugin.getConfig().getInt("regeneration.blocks-per-tick", 4)) / 20.0));
        if (!player.hasPermission("magguilds.regeneration.bypass-cost") && Items.count(player.getInventory(), material) < amount) {
            messages.send(player, "regeneration-missing-cost", "blocks", map.size(), "time", Text.duration(seconds), "amount", amount, "material", display);
            return;
        }
        if (!player.hasPermission("magguilds.regeneration.bypass-cost")) Items.remove(player.getInventory(), material, amount);

        BossBar bar = Bukkit.createBossBar("", color(), style());
        Session session = new Session(guild, new ArrayDeque<>(map.keySet()), bar);
        sessions.put(guild.tag(), session);
        updateBar(session);
        messages.send(player, "regeneration-started", "blocks", map.size(), "time", Text.duration(seconds), "amount", amount, "material", display);
    }

    public void tick() {
        int perTick = Math.max(1, plugin.getConfig().getInt("regeneration.blocks-per-tick", 4));
        Iterator<Map.Entry<String, Session>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Session> entry = iterator.next();
            Session session = entry.getValue();
            LinkedHashMap<Key, Saved> map = changes(session.guild);
            for (int i = 0; i < perTick && !session.queue.isEmpty(); i++) {
                Key key = session.queue.poll();
                Saved saved = map.remove(key);
                if (saved == null) continue;
                World world = Bukkit.getWorld(key.world());
                if (world != null && key.y() >= world.getMinHeight() && key.y() < world.getMaxHeight()) {
                    try {
                        BlockData data = Bukkit.createBlockData(saved.blockData());
                        world.getBlockAt(key.x(), key.y(), key.z()).setBlockData(data, false);
                    } catch (IllegalArgumentException ignored) { }
                }
                session.restored++;
            }
            dirty.add(session.guild.tag());
            updateBar(session);
            if (session.queue.isEmpty()) {
                session.bar.removeAll();
                iterator.remove();
                dirty.add(session.guild.tag());
                for (UUID member : session.guild.members()) {
                    Player player = Bukkit.getPlayer(member);
                    if (player != null) messages.send(player, "regeneration-completed", "tag", session.guild.tag(), "blocks", session.total);
                }
            }
        }
    }

    private void updateBar(Session session) {
        double progress = session.total == 0 ? 1.0 : session.restored / (double) session.total;
        int remaining = Math.max(0, session.total - session.restored);
        int perTick = Math.max(1, plugin.getConfig().getInt("regeneration.blocks-per-tick", 4));
        long seconds = Math.max(0, (long) Math.ceil(remaining / (double) perTick / 20.0));
        String raw = plugin.getConfig().getString("regeneration.bossbar.title", "Regeneracja %tag% %percent%% %remaining% %time%");
        String title = Text.smallCapsPreservingTokens(raw)
                .replace("%tag%", session.guild.tag())
                .replace("%percent%", String.format(Locale.US, "%.1f", progress * 100.0))
                .replace("%remaining%", String.valueOf(remaining))
                .replace("%restored%", String.valueOf(session.restored))
                .replace("%blocks%", String.valueOf(session.total))
                .replace("%time%", Text.duration(seconds));
        session.bar.setTitle(Text.color(title));
        session.bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        for (UUID member : session.guild.members()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null && !session.bar.getPlayers().contains(player)) session.bar.addPlayer(player);
        }
        session.bar.getPlayers().removeIf(player -> !session.guild.isMember(player.getUniqueId()));
    }

    private long estimatedSeconds(Guild guild) {
        Session session = sessions.get(guild.tag());
        if (session == null) return 0;
        return (long) Math.ceil(session.queue.size() / (double) Math.max(1, plugin.getConfig().getInt("regeneration.blocks-per-tick", 4)) / 20.0);
    }

    private LinkedHashMap<Key, Saved> changes(Guild guild) {
        return damage.computeIfAbsent(guild.tag(), tag -> load(tag));
    }

    private LinkedHashMap<Key, Saved> load(String tag) {
        LinkedHashMap<Key, Saved> map = new LinkedHashMap<>();
        File file = new File(folder, tag + ".yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("blocks");
        if (section == null) return map;
        for (String id : section.getKeys(false)) {
            String world = section.getString(id + ".world");
            int x = section.getInt(id + ".x"), y = section.getInt(id + ".y"), z = section.getInt(id + ".z");
            String data = section.getString(id + ".data");
            if (world != null && data != null) map.put(new Key(world, x, y, z), new Saved(data));
        }
        return map;
    }

    public void flushDirty() {
        for (String tag : new HashSet<>(dirty)) {
            LinkedHashMap<Key, Saved> map = damage.get(tag);
            if (map == null) continue;
            YamlConfiguration yaml = new YamlConfiguration();
            int index = 0;
            for (Map.Entry<Key, Saved> entry : map.entrySet()) {
                String path = "blocks." + (++index);
                yaml.set(path + ".world", entry.getKey().world());
                yaml.set(path + ".x", entry.getKey().x());
                yaml.set(path + ".y", entry.getKey().y());
                yaml.set(path + ".z", entry.getKey().z());
                yaml.set(path + ".data", entry.getValue().blockData());
            }
            try { yaml.save(new File(folder, tag + ".yml")); dirty.remove(tag); }
            catch (IOException exception) { plugin.getLogger().severe("Nie mozna zapisac regeneracji " + tag + ": " + exception.getMessage()); }
        }
    }

    public void shutdown() {
        for (Session session : sessions.values()) session.bar.removeAll();
        sessions.clear(); flushDirty();
    }

    private BarColor color() {
        try { return BarColor.valueOf(plugin.getConfig().getString("regeneration.bossbar.color", "PURPLE")); }
        catch (IllegalArgumentException exception) { return BarColor.PURPLE; }
    }
    private BarStyle style() {
        try { return BarStyle.valueOf(plugin.getConfig().getString("regeneration.bossbar.style", "SEGMENTED_10")); }
        catch (IllegalArgumentException exception) { return BarStyle.SEGMENTED_10; }
    }
}
