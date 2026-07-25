package pl.maghub.guilds.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.maghub.guilds.MAGGuildsPlugin;
import pl.maghub.guilds.data.DataStore;
import pl.maghub.guilds.model.Guild;
import pl.maghub.guilds.service.CombatService;
import pl.maghub.guilds.service.GuildService;

public final class MAGGuildsExpansion extends PlaceholderExpansion {
    private final MAGGuildsPlugin plugin;
    private final GuildService guilds;
    private final CombatService combat;
    private final DataStore store;

    public MAGGuildsExpansion(MAGGuildsPlugin plugin, GuildService guilds, CombatService combat, DataStore store) {
        this.plugin = plugin; this.guilds = guilds; this.combat = combat; this.store = store;
    }
    @Override public @NotNull String getIdentifier() { return "magguilds"; }
    @Override public @NotNull String getAuthor() { return "MAGHUB"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        Guild guild = guilds.byPlayer(player.getUniqueId());
        DataStore.PlayerProfile profile = store.profile(player.getUniqueId(), player.getName());
        return switch (params.toLowerCase()) {
            case "tag" -> guild == null ? "" : guild.tag();
            case "name" -> guild == null ? "" : guild.name();
            case "display" -> guild == null ? "" : "[" + guild.tag() + "]";
            case "role" -> guild == null ? "" : guild.roleId(player.getUniqueId(), guilds.defaultRole());
            case "guild_points" -> guild == null ? "0" : String.valueOf(guild.points());
            case "guild_lives" -> guild == null ? "0" : String.valueOf(guild.lives());
            case "guild_kills" -> guild == null ? "0" : String.valueOf(guild.kills());
            case "guild_deaths" -> guild == null ? "0" : String.valueOf(guild.deaths());
            case "player_points" -> String.valueOf(profile.points());
            case "player_kills" -> String.valueOf(profile.kills());
            case "player_deaths" -> String.valueOf(profile.deaths());
            case "combat" -> combat.inCombat(player.getUniqueId()) ? "true" : "false";
            case "combat_time" -> String.valueOf(combat.remaining(player.getUniqueId()));
            default -> null;
        };
    }
}
