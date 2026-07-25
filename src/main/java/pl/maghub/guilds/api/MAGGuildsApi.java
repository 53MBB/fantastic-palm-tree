package pl.maghub.guilds.api;

import org.bukkit.entity.Player;
import pl.maghub.guilds.MAGGuildsPlugin;
import pl.maghub.guilds.model.Guild;
import pl.maghub.guilds.service.AchievementService;
import pl.maghub.guilds.service.CombatService;
import pl.maghub.guilds.service.GuildService;

import java.util.Optional;
import java.util.UUID;

public final class MAGGuildsApi {
    private static MAGGuildsPlugin plugin;
    private static GuildService guilds;
    private static CombatService combat;
    private static AchievementService achievements;
    private MAGGuildsApi() {}

    public static void initialize(MAGGuildsPlugin owner, GuildService guildService, CombatService combatService, AchievementService achievementService) {
        plugin = owner; guilds = guildService; combat = combatService; achievements = achievementService;
    }
    public static void shutdown() { plugin = null; guilds = null; combat = null; achievements = null; }
    public static boolean available() { return plugin != null; }
    public static Optional<String> guildTag(UUID player) { Guild guild = guilds == null ? null : guilds.byPlayer(player); return guild == null ? Optional.empty() : Optional.of(guild.tag()); }
    public static long playerPoints(Player player) { return plugin == null ? 0 : plugin.guilds() == null ? 0 : plugin.getServer() == null ? 0 : new pl.maghub.guilds.data.DataStore(plugin).profile(player.getUniqueId(), player.getName()).points(); }
    public static boolean inCombat(UUID player) { return combat != null && combat.inCombat(player); }
    public static void addAchievement(UUID player, String category, long amount) { if (guilds == null || achievements == null) return; Guild guild = guilds.byPlayer(player); if (guild != null) achievements.add(guild, category, amount); }
}
