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
    private final DataStore store;
    private final MessageService messages;
    private final LinkedHashMap<String, Category> categories = new LinkedHashMap<>();

    public record Category(String id, String name, String material, List<Long> thresholds) {}

    public AchievementService(MAGGuildsPlugin plugin, GuildService guilds, DataStore store, MessageService messages) {
        this.plugin = plugin; this.guilds = guilds; this.store = store; this.messages = messages; reload();
    }

    public void reload() {
        categories.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("achievements.categories");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            List<Long> thresholds = root.getLongList(id + ".thresholds");
            categories.put(id.toLowerCase(Locale.ROOT), new Category(id.toLowerCase(Locale.ROOT), root.getString(id + ".name", id), root.getString(id + ".material", "PAPER"), thresholds));
        }
    }

    public Collection<Category> categories() { return categories.values(); }
    public Category category(String id) { return id == null ? null : categories.get(id.toLowerCase(Locale.ROOT)); }

    public void add(Guild guild, String category, long amount) {
        if (guild == null || amount <= 0 || !categories.containsKey(category.toLowerCase(Locale.ROOT))) return;
        String key = category.toLowerCase(Locale.ROOT);
        guild.addStat(key, amount);
        Category definition = categories.get(key);
        for (int i = 0; i < definition.thresholds().size(); i++) {
            String achievement = key + ":" + (i + 1);
            if (guild.stat(key) >= definition.thresholds().get(i) && guild.unlockedAchievements().add(achievement)) {
                for (UUID member : guild.members()) {
                    Player player = Bukkit.getPlayer(member);
                    if (player != null) messages.send(player, "achievement-unlocked", "achievement", definition.name(), "level", i + 1, "target", definition.thresholds().get(i));
                }
            }
        }
    }

    public boolean claim(Player player, String categoryId, int level) {
        Guild guild = guilds.byPlayer(player.getUniqueId());
        if (guild == null) { messages.send(player, "no-guild"); return false; }
        if (!guild.canManage(player.getUniqueId())) { messages.send(player, "achievement-manage-only"); return false; }
        Category category = category(categoryId);
        if (category == null || level < 1 || level > category.thresholds().size()) return false;
        String key = category.id() + ":" + level;
        long target = category.thresholds().get(level - 1);
        if (guild.stat(category.id()) < target) { messages.send(player, "achievement-not-ready", "progress", guild.stat(category.id()), "target", target); return false; }
        if (guild.claimedAchievements().contains(key)) { messages.send(player, "achievement-already"); return false; }

        ConfigurationSection reward = plugin.getConfig().getConfigurationSection("achievements.rewards." + level);
        if (reward != null) {
            for (String command : reward.getStringList("commands")) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()).replace("%tag%", guild.tag()));
            guild.points(guild.points() + reward.getLong("guild-points", 0));
            guild.lives(guild.lives() + reward.getInt("guild-lives", 0));
        }
        guild.claimedAchievements().add(key);
        String description = reward == null ? "Nagroda" : reward.getString("description", "Nagroda");
        messages.send(player, "achievement-claimed", "achievement", category.name(), "level", level, "reward", description);
        guilds.save();
        return true;
    }

    public int claimAll(Player player, String category) {
        Category definition = category(category);
        if (definition == null) return 0;
        int count = 0;
        for (int level = 1; level <= definition.thresholds().size(); level++) if (claim(player, definition.id(), level)) count++;
        if (count == 0) messages.send(player, "achievement-none");
        return count;
    }

    public void tickMinute() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Guild guild = guilds.byPlayer(player.getUniqueId());
            if (guild != null) add(guild, "playtime", 1);
        }
    }
}
