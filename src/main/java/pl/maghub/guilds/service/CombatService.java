package pl.maghub.guilds.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.maghub.guilds.MAGGuildsPlugin;
import pl.maghub.guilds.data.DataStore;
import pl.maghub.guilds.model.Guild;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class CombatService {
    private record Tag(UUID enemy, long until) {}
    private final MAGGuildsPlugin plugin;
    private final GuildService guilds;
    private final DataStore store;
    private final MessageService messages;
    private final AchievementService achievements;
    private final HashMap<UUID, Tag> tags = new HashMap<>();
    private final HashMap<String, Long> lastKills = new HashMap<>();

    public CombatService(MAGGuildsPlugin plugin, GuildService guilds, DataStore store, MessageService messages, AchievementService achievements) {
        this.plugin = plugin; this.guilds = guilds; this.store = store; this.messages = messages; this.achievements = achievements;
    }

    public void tag(Player first, Player second) {
        if (!plugin.getConfig().getBoolean("anti-logout.enabled", true)) return;
        if (bypass(first) || bypass(second)) return;
        long until = System.currentTimeMillis() + plugin.getConfig().getLong("anti-logout.combat-seconds", 30) * 1000L;
        tags.put(first.getUniqueId(), new Tag(second.getUniqueId(), until));
        tags.put(second.getUniqueId(), new Tag(first.getUniqueId(), until));
    }

    private boolean bypass(Player player) {
        return plugin.getConfig().getBoolean("anti-logout.bypass-permission-enabled", false) && player.hasPermission("magguilds.antylogout.bypass");
    }

    public boolean inCombat(UUID uuid) {
        Tag tag = tags.get(uuid);
        return tag != null && tag.until() > System.currentTimeMillis();
    }

    public long remaining(UUID uuid) {
        Tag tag = tags.get(uuid);
        return tag == null ? 0 : Math.max(0, (tag.until() - System.currentTimeMillis() + 999) / 1000);
    }

    public UUID enemy(UUID uuid) {
        Tag tag = tags.get(uuid); return tag == null ? null : tag.enemy();
    }

    public void clear(UUID uuid) { tags.remove(uuid); }

    public void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Tag>> iterator = tags.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Tag> entry = iterator.next();
            if (entry.getValue().until() <= now) { iterator.remove(); continue; }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && plugin.getConfig().getBoolean("anti-logout.actionbar-enabled", true)) {
                String format = plugin.getConfig().getString("anti-logout.actionbar-format", "&#FB7185&lᴀɴᴛʏʟᴏɢᴏᴜᴛ &#F8FAFC%time%s");
                String prepared = pl.maghub.guilds.util.Text.smallCapsPreservingTokens(format).replace("%time%", String.valueOf(remaining(player.getUniqueId())));
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new net.md_5.bungee.api.chat.TextComponent(pl.maghub.guilds.util.Text.color(prepared)));
            }
        }
    }

    public void handleDeath(Player victim, Player killer) {
        clear(victim.getUniqueId());
        if (killer != null) clear(killer.getUniqueId());
        DataStore.PlayerProfile victimProfile = store.profile(victim.getUniqueId(), victim.getName());
        victimProfile.deaths(victimProfile.deaths() + 1);
        Guild victimGuild = guilds.byPlayer(victim.getUniqueId());
        if (victimGuild != null) victimGuild.deaths(victimGuild.deaths() + 1);
        if (killer == null || killer.equals(victim)) return;

        DataStore.PlayerProfile killerProfile = store.profile(killer.getUniqueId(), killer.getName());
        Guild killerGuild = guilds.byPlayer(killer.getUniqueId());
        if (killerGuild != null && killerGuild == victimGuild) {
            int penalty = plugin.getConfig().getInt("ranking.same-guild-penalty", 25);
            killerProfile.points(killerProfile.points() - penalty);
            victimProfile.points(victimProfile.points() - penalty);
            messages.send(killer, "ranking-same-killer", "points", penalty, "total", killerProfile.points());
            messages.send(victim, "ranking-same-victim", "points", penalty, "total", victimProfile.points());
            return;
        }

        String farmKey = killer.getUniqueId() + ":" + victim.getUniqueId();
        long now = System.currentTimeMillis();
        long protection = plugin.getConfig().getLong("ranking.anti-farm-seconds", 300) * 1000L;
        Long last = lastKills.get(farmKey);
        if (last != null && now - last < protection) { messages.send(killer, "ranking-farm"); return; }
        lastKills.put(farmKey, now);

        int base = plugin.getConfig().getInt("ranking.base-kill-points", 25);
        int min = plugin.getConfig().getInt("ranking.minimum-kill-points", 5);
        int max = plugin.getConfig().getInt("ranking.maximum-kill-points", 50);
        long difference = victimProfile.points() - killerProfile.points();
        int gain = (int) Math.max(min, Math.min(max, base + difference / 100));
        int loss = gain;
        killerProfile.points(killerProfile.points() + gain);
        victimProfile.points(victimProfile.points() - loss);
        killerProfile.kills(killerProfile.kills() + 1);
        if (killerGuild != null) {
            killerGuild.kills(killerGuild.kills() + 1);
            killerGuild.points(killerGuild.points() + gain);
            achievements.add(killerGuild, "kills", 1);
            achievements.add(killerGuild, "points", gain);
        }
        messages.send(killer, "ranking-kill", "points", gain, "victim", victim.getName(), "total", killerProfile.points());
        messages.send(victim, "ranking-death", "points", loss, "killer", killer.getName(), "total", victimProfile.points());
    }

    public void handleLogout(Player player) {
        if (!inCombat(player.getUniqueId())) return;
        UUID enemyId = enemy(player.getUniqueId());
        Player enemy = enemyId == null ? null : Bukkit.getPlayer(enemyId);
        DataStore.PlayerProfile profile = store.profile(player.getUniqueId(), player.getName());
        profile.pendingCombatDeath(true);
        if (plugin.getConfig().getBoolean("anti-logout.broadcast-enabled", true)) {
            String killerName = enemy == null ? "brak" : enemy.getName();
            for (Player online : Bukkit.getOnlinePlayers()) messages.send(online, "combat-logout", "player", player.getName(), "killer", killerName);
        }
        if (enemy != null) handleDeath(player, enemy);
        else clear(player.getUniqueId());
    }

    public void handleJoin(Player player) {
        DataStore.PlayerProfile profile = store.profile(player.getUniqueId(), player.getName());
        profile.lastName(player.getName());
        if (profile.pendingCombatDeath() && plugin.getConfig().getBoolean("anti-logout.force-death-on-rejoin", true)) {
            profile.pendingCombatDeath(false);
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (player.isOnline() && !player.isDead()) player.setHealth(0.0); messages.send(player, "combat-respawn"); }, 2L);
        }
    }

    public void shutdown() { tags.clear(); }
}
