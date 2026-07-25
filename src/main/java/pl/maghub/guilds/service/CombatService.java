package pl.maghub.guilds.service;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.maghub.guilds.MAGGuildsPlugin;
import pl.maghub.guilds.data.DataStore;
import pl.maghub.guilds.model.Guild;
import pl.maghub.guilds.util.Text;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class CombatService {
    private record Tag(UUID opponent, long expiresAt) { }

    private final MAGGuildsPlugin plugin;
    private final GuildService guilds;
    private final DataStore dataStore;
    private final MessageService messages;
    private final AchievementService achievements;
    private final HashMap<UUID, Tag> tags = new HashMap<>();
    private final HashMap<String, Long> recentKills = new HashMap<>();

    public CombatService(
            MAGGuildsPlugin plugin,
            GuildService guilds,
            DataStore dataStore,
            MessageService messages,
            AchievementService achievements
    ) {
        this.plugin = plugin;
        this.guilds = guilds;
        this.dataStore = dataStore;
        this.messages = messages;
        this.achievements = achievements;
    }

    public void tag(Player first, Player second) {
        if (!plugin.getConfig().getBoolean("anti-logout.enabled", true)) return;
        if (bypass(first) || bypass(second)) return;

        long expires = System.currentTimeMillis()
                + plugin.getConfig().getLong("anti-logout.combat-seconds", 30L) * 1000L;
        boolean firstNew = !inCombat(first.getUniqueId());
        boolean secondNew = !inCombat(second.getUniqueId());

        tags.put(first.getUniqueId(), new Tag(second.getUniqueId(), expires));
        tags.put(second.getUniqueId(), new Tag(first.getUniqueId(), expires));

        if (firstNew) {
            messages.send(first, "combat-started",
                    "player", second.getName(),
                    "seconds", plugin.getConfig().getLong("anti-logout.combat-seconds", 30L));
        }
        if (secondNew) {
            messages.send(second, "combat-started",
                    "player", first.getName(),
                    "seconds", plugin.getConfig().getLong("anti-logout.combat-seconds", 30L));
        }
    }

    private boolean bypass(Player player) {
        return plugin.getConfig().getBoolean("anti-logout.bypass-permission-enabled", false)
                && player.hasPermission("magguilds.antylogout.bypass");
    }

    public boolean inCombat(UUID uuid) {
        Tag tag = tags.get(uuid);
        if (tag == null) return false;
        if (tag.expiresAt() <= System.currentTimeMillis()) {
            tags.remove(uuid);
            return false;
        }
        return true;
    }

    public long remaining(UUID uuid) {
        Tag tag = tags.get(uuid);
        return tag == null ? 0L : Math.max(0L, (tag.expiresAt() - System.currentTimeMillis() + 999L) / 1000L);
    }

    public UUID opponent(UUID uuid) {
        Tag tag = tags.get(uuid);
        return tag == null ? null : tag.opponent();
    }

    public void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Tag>> iterator = tags.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Tag> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());

            if (entry.getValue().expiresAt() <= now) {
                iterator.remove();
                if (player != null) {
                    sendActionBar(player, "");
                    messages.send(player, "combat-ended");
                }
                continue;
            }

            if (player != null && plugin.getConfig().getBoolean("anti-logout.actionbar-enabled", true)) {
                String raw = plugin.getConfig().getString(
                        "anti-logout.actionbar-format",
                        "&#FB7185&lᴀɴᴛʏʟᴏɢᴏᴜᴛ &#F8FAFC%time%s"
                );
                String formatted = Text.color(
                        Text.smallCapsPreservingTokens(raw)
                                .replace("%time%", String.valueOf(remaining(entry.getKey())))
                );
                sendActionBar(player, formatted);
            }
        }
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(message == null ? "" : message)
        );
    }

    public void clear(UUID uuid) {
        tags.remove(uuid);
    }

    public void onDeath(Player victim, Player killer) {
        UUID victimId = victim.getUniqueId();
        clear(victimId);
        if (killer == null || killer.equals(victim)) return;
        clear(killer.getUniqueId());

        DataStore.PlayerProfile victimProfile = dataStore.profile(victimId, victim.getName());
        DataStore.PlayerProfile killerProfile = dataStore.profile(killer.getUniqueId(), killer.getName());
        Guild victimGuild = guilds.byPlayer(victimId);
        Guild killerGuild = guilds.byPlayer(killer.getUniqueId());

        int samePenalty = plugin.getConfig().getInt("ranking.same-guild-penalty", 25);
        if (victimGuild != null && victimGuild == killerGuild) {
            killerProfile.points(killerProfile.points() - samePenalty);
            victimProfile.points(victimProfile.points() - samePenalty);
            killerProfile.kills(killerProfile.kills() + 1L);
            victimProfile.deaths(victimProfile.deaths() + 1L);
            messages.send(killer, "ranking-same-killer",
                    "points", samePenalty,
                    "total", killerProfile.points());
            messages.send(victim, "ranking-same-victim",
                    "points", samePenalty,
                    "total", victimProfile.points());
            dataStore.saveProfiles();
            return;
        }

        String farmKey = killer.getUniqueId() + ":" + victimId;
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("ranking.anti-farm-seconds", 300L) * 1000L;
        if (recentKills.getOrDefault(farmKey, 0L) + cooldown > now) {
            messages.send(killer, "ranking-farm");
            return;
        }
        recentKills.put(farmKey, now);

        int base = plugin.getConfig().getInt("ranking.base-kill-points", 25);
        int gain = Math.max(
                plugin.getConfig().getInt("ranking.minimum-kill-points", 5),
                Math.min(
                        plugin.getConfig().getInt("ranking.maximum-kill-points", 50),
                        base + (int) ((victimProfile.points() - killerProfile.points()) / 100L)
                )
        );

        killerProfile.points(killerProfile.points() + gain);
        victimProfile.points(victimProfile.points() - gain);
        killerProfile.kills(killerProfile.kills() + 1L);
        victimProfile.deaths(victimProfile.deaths() + 1L);

        if (killerGuild != null) {
            killerGuild.kills(killerGuild.kills() + 1L);
            killerGuild.points(killerGuild.points() + gain);
            achievements.add(killer.getUniqueId(), "kills", 1L);
            achievements.add(killer.getUniqueId(), "points", gain);
        }
        if (victimGuild != null) {
            victimGuild.deaths(victimGuild.deaths() + 1L);
        }

        messages.send(killer, "ranking-kill",
                "points", gain,
                "victim", victim.getName(),
                "total", killerProfile.points());
        messages.send(victim, "ranking-death",
                "points", gain,
                "killer", killer.getName(),
                "total", victimProfile.points());

        guilds.save();
        dataStore.saveProfiles();
    }

    public void onQuit(Player victim) {
        if (!inCombat(victim.getUniqueId())) return;

        UUID killerId = opponent(victim.getUniqueId());
        Player killer = killerId == null ? null : Bukkit.getPlayer(killerId);
        DataStore.PlayerProfile profile = dataStore.profile(victim.getUniqueId(), victim.getName());
        profile.pendingCombatDeath(true);

        if (killer != null) onDeath(victim, killer);
        if (plugin.getConfig().getBoolean("anti-logout.broadcast-enabled", true)) {
            Bukkit.broadcastMessage(messages.format(
                    "combat-logout",
                    "player", victim.getName(),
                    "killer", killer == null ? "brak" : killer.getName()
            ));
        }
        dataStore.saveProfiles();
    }

    public void onJoin(Player player) {
        DataStore.PlayerProfile profile = dataStore.profile(player.getUniqueId(), player.getName());
        profile.lastName(player.getName());

        if (profile.pendingCombatDeath()
                && plugin.getConfig().getBoolean("anti-logout.force-death-on-rejoin", true)) {
            profile.pendingCombatDeath(false);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !player.isDead()) player.setHealth(0.0D);
                messages.send(player, "combat-respawn");
            }, 2L);
        }
        dataStore.saveProfiles();
    }

    public void shutdown() {
        tags.clear();
        recentKills.clear();
    }
}
