package pl.maghub.guilds.service;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import pl.maghub.guilds.MAGGuildsPlugin;
import pl.maghub.guilds.data.DataStore;
import pl.maghub.guilds.model.Guild;

import java.util.*;

public final class AchievementService {
    private final MAGGuildsPlugin plugin;
    private final GuildService guilds;
    private final DataStore dataStore;
    private final MessageService messages;
    private final LinkedHashMap<String, Category> categories = new LinkedHashMap<>();

    public record Category(String id, String name, String material, List<Long> thresholds) { }

    public AchievementService(MAGGuildsPlugin plugin, GuildService guilds, DataStore dataStore, MessageService messages) {
        this.plugin = plugin; this.guilds = guilds; this.dataStore = dataStore; this.messages = messages; reload();
    }

    public void reload() {
        categories.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("achievements.categories");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            List<Long> thresholds = new ArrayList<>();
            for (Object value : root.getList(id + ".thresholds", List.of())) if (value instanceof Number number) thresholds.add(number.longValue());
            categories.put(id, new Category(id, root.getString(id + ".name", id), root.getString(id + ".material", "PAPER"), thresholds));
        }
    }

    public Collection<Category> categories() { return Collections.unmodifiableCollection(categories.values()); }
    public Category category(String id) { return categories.get(id); }

    public void add(UUID player, String category, long amount) {
        Guild guild = guilds.byPlayer(player);
        if (guild == null || amount <= 0 || !categories.containsKey(category)) return;
        long old = guild.stat(category);
        guild.addStat(category, amount);
        Category data = categories.get(category);
        for (int i = 0; i < data.thresholds().size(); i++) {
            long target = data.thresholds().get(i);
            String key = category + ":" + (i + 1);
            if (old < target && guild.stat(category) >= target && guild.unlockedAchievements().add(key)) {
                for (UUID member : guild.members()) {
                    Player online = Bukkit.getPlayer(member);
                    if (online != null) messages.send(online, "achievement-unlocked", "achievement", data.name(), "level", i + 1, "target", target);
                }
            }
        }
    }

    public boolean claim(Player player, String category, int level) {
        Guild guild = guilds.byPlayer(player.getUniqueId());
        if (guild == null) { messages.send(player, "no-guild"); return false; }
        if (!guild.canManage(player.getUniqueId())) { messages.send(player, "achievement-manage-only"); return false; }
        Category data = categories.get(category);
        if (data == null || level < 1 || level > data.thresholds().size()) return false;
        String key = category + ":" + level;
        if (guild.claimedAchievements().contains(key)) { messages.send(player, "achievement-already"); return false; }
        long target = data.thresholds().get(level - 1);
        if (guild.stat(category) < target) { messages.send(player, "achievement-not-ready", "progress", guild.stat(category), "target", target); return false; }
        ConfigurationSection reward = plugin.getConfig().getConfigurationSection("achievements.rewards." + level);
        if (reward != null) {
            for (String command : reward.getStringList("commands")) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()).replace("%tag%", guild.tag()));
            guild.points(guild.points() + reward.getLong("guild-points", 0));
            guild.lives(guild.lives() + reward.getInt("guild-lives", 0));
        }
        guild.claimedAchievements().add(key);
        guilds.save();
        messages.send(player, "achievement-claimed", "achievement", data.name(), "level", level, "reward", reward == null ? "Nagroda" : reward.getString("description", "Nagroda"));
        return true;
    }

    public int claimAll(Player player, String category) {
        Category data = categories.get(category); if (data == null) return 0;
        int claimed = 0;
        for (int i = 1; i <= data.thresholds().size(); i++) if (claim(player, category, i)) claimed++;
        if (claimed == 0) messages.send(player, "achievement-none");
        return claimed;
    }

    public void tickMinute() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            add(player.getUniqueId(), "playtime", 1);
        }
        guilds.save();
    }
}
