package pl.maghub.guilds.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import pl.maghub.guilds.MAGGuildsPlugin;
import pl.maghub.guilds.data.DataStore;
import pl.maghub.guilds.model.Guild;
import pl.maghub.guilds.util.Items;

import java.util.*;

public final class GuildService {
    private final MAGGuildsPlugin plugin;
    private final DataStore store;
    private final MessageService messages;
    private final LinkedHashMap<String, Guild> guilds;
    private final HashMap<UUID, String> membership = new HashMap<>();
    private final HashMap<UUID, Invite> invites = new HashMap<>();
    private final ArrayList<DataStore.War> wars;

    private record Invite(String tag, long expiresAt) {}

    public GuildService(MAGGuildsPlugin plugin, DataStore store, MessageService messages) {
        this.plugin = plugin;
        this.store = store;
        this.messages = messages;
        this.guilds = new LinkedHashMap<>(store.loadGuilds());
        this.wars = new ArrayList<>(store.loadWars());
        rebuildMembership();
        ensureDefaultRoles();
    }

    public Collection<Guild> all() { return Collections.unmodifiableCollection(guilds.values()); }
    public Guild byTag(String tag) { return tag == null ? null : guilds.get(tag.toUpperCase(Locale.ROOT)); }
    public Guild byPlayer(UUID uuid) {
        String tag = membership.get(uuid);
        return tag == null ? null : guilds.get(tag);
    }

    public Guild at(Location location) {
        if (location == null || location.getWorld() == null) return null;
        for (Guild guild : guilds.values()) {
            Location center = guild.center();
            if (center == null || center.getWorld() == null || !center.getWorld().equals(location.getWorld())) continue;
            if (Math.abs(center.getBlockX() - location.getBlockX()) <= guild.radius()
                    && Math.abs(center.getBlockZ() - location.getBlockZ()) <= guild.radius()) return guild;
        }
        return null;
    }

    public boolean create(Player player, String tag, String name) {
        if (byPlayer(player.getUniqueId()) != null) { messages.send(player, "already-guild"); return false; }
        tag = tag.toUpperCase(Locale.ROOT);
        if (!tag.matches("[A-Z0-9]{2,6}")) { messages.send(player, "invalid-tag", "min", 2, "max", 6); return false; }
        if (guilds.containsKey(tag)) { messages.send(player, "tag-taken", "tag", tag); return false; }
        int minimum = plugin.getConfig().getInt("settings.minimum-guild-distance", 100);
        for (Guild guild : guilds.values()) {
            Location center = guild.center();
            if (center != null && center.getWorld() != null && center.getWorld().equals(player.getWorld())
                    && center.distanceSquared(player.getLocation()) < (double) minimum * minimum) {
                messages.send(player, "too-close", "distance", minimum);
                return false;
            }
        }
        Map<Material, Integer> costs = creationCosts();
        if (!Items.has(player.getInventory(), costs)) { messages.send(player, "missing-items"); return false; }
        for (Map.Entry<Material, Integer> cost : costs.entrySet()) Items.remove(player.getInventory(), cost.getKey(), cost.getValue());

        Guild guild = new Guild(tag, name, player.getUniqueId());
        Location center = player.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        int configuredY = plugin.getConfig().getInt("settings.heart-y", 30);
        configuredY = Math.max(player.getWorld().getMinHeight() + 1, Math.min(player.getWorld().getMaxHeight() - 2, configuredY));
        center.setY(configuredY);
        guild.center(center);
        guild.base(player.getLocation());
        guild.home(player.getLocation());
        guild.radius(plugin.getConfig().getInt("settings.default-radius", 30));
        guild.memberLimit(plugin.getConfig().getInt("settings.default-member-limit", 12));
        guild.lives(plugin.getConfig().getInt("settings.default-lives", 3));
        guild.expiresAt(System.currentTimeMillis() + plugin.getConfig().getLong("settings.expiration-days", 14) * 86_400_000L);
        addDefaultRoles(guild);
        guild.memberRoles().put(player.getUniqueId(), plugin.getConfig().getString("roles.default-role", "czlonek"));
        guilds.put(tag, guild);
        membership.put(player.getUniqueId(), tag);
        Material heartMaterial = Material.matchMaterial("BEACON");
        if (heartMaterial != null) center.getBlock().setType(heartMaterial);
        messages.send(player, "created", "tag", tag, "name", name);
        save();
        return true;
    }

    public void delete(Guild guild) {
        guilds.remove(guild.tag());
        for (UUID member : guild.members()) membership.remove(member);
        for (Guild other : guilds.values()) other.allies().remove(guild.tag());
        wars.removeIf(war -> war.involves(guild.tag()));
        save();
    }

    public void invite(Player sender, Player target) {
        Guild guild = byPlayer(sender.getUniqueId());
        if (guild == null) { messages.send(sender, "no-guild"); return; }
        if (!guild.hasPermission(sender.getUniqueId(), Guild.Permission.INVITE, defaultRole())) { messages.send(sender, "no-permission"); return; }
        if (byPlayer(target.getUniqueId()) != null) { messages.send(sender, "already-guild"); return; }
        invites.put(target.getUniqueId(), new Invite(guild.tag(), System.currentTimeMillis() + plugin.getConfig().getLong("settings.invite-seconds", 60) * 1000L));
        messages.send(sender, "invite-sent", "player", target.getName());
        messages.send(target, "invite-received", "tag", guild.tag());
    }

    public void join(Player player, String tag) {
        Guild guild = byTag(tag);
        Invite invite = invites.get(player.getUniqueId());
        if (guild == null || invite == null || !invite.tag().equalsIgnoreCase(tag) || invite.expiresAt() < System.currentTimeMillis()) {
            messages.send(player, "invite-missing"); return;
        }
        if (guild.members().size() >= guild.memberLimit()) { messages.send(player, "member-limit"); return; }
        guild.members().add(player.getUniqueId());
        guild.memberRoles().put(player.getUniqueId(), defaultRole());
        membership.put(player.getUniqueId(), guild.tag());
        invites.remove(player.getUniqueId());
        messages.send(player, "joined", "tag", guild.tag());
        save();
    }

    public void leave(Player player) {
        Guild guild = byPlayer(player.getUniqueId());
        if (guild == null) { messages.send(player, "no-guild"); return; }
        if (guild.isLeader(player.getUniqueId())) { messages.send(player, "leader-cannot-leave"); return; }
        guild.members().remove(player.getUniqueId());
        guild.memberRoles().remove(player.getUniqueId());
        guild.overrides().remove(player.getUniqueId());
        if (guild.isDeputy(player.getUniqueId())) guild.deputy(null);
        membership.remove(player.getUniqueId());
        messages.send(player, "left");
        save();
    }

    public void kick(Player sender, OfflinePlayer target) {
        Guild guild = byPlayer(sender.getUniqueId());
        if (guild == null) { messages.send(sender, "no-guild"); return; }
        if (!guild.hasPermission(sender.getUniqueId(), Guild.Permission.KICK, defaultRole())) { messages.send(sender, "no-permission"); return; }
        UUID uuid = target.getUniqueId();
        if (!guild.isMember(uuid) || guild.isLeader(uuid)) { messages.send(sender, "player-not-found", "player", target.getName()); return; }
        guild.members().remove(uuid);
        guild.memberRoles().remove(uuid);
        guild.overrides().remove(uuid);
        if (guild.isDeputy(uuid)) guild.deputy(null);
        membership.remove(uuid);
        messages.send(sender, "kicked", "player", target.getName());
        if (target.isOnline() && target.getPlayer() != null) messages.send(target.getPlayer(), "kicked", "player", target.getName());
        save();
    }

    public void setLeader(Player sender, OfflinePlayer target) {
        Guild guild = byPlayer(sender.getUniqueId());
        if (guild == null) { messages.send(sender, "no-guild"); return; }
        if (!guild.isLeader(sender.getUniqueId())) { messages.send(sender, "leader-only"); return; }
        if (!guild.isMember(target.getUniqueId())) { messages.send(sender, "player-not-found", "player", target.getName()); return; }
        guild.leader(target.getUniqueId());
        messages.send(sender, "leader-changed", "player", target.getName());
        save();
    }

    public void setDeputy(Player sender, OfflinePlayer target) {
        Guild guild = byPlayer(sender.getUniqueId());
        if (guild == null) { messages.send(sender, "no-guild"); return; }
        if (!guild.isLeader(sender.getUniqueId())) { messages.send(sender, "leader-only"); return; }
        if (!guild.isMember(target.getUniqueId())) { messages.send(sender, "player-not-found", "player", target.getName()); return; }
        guild.deputy(target.getUniqueId());
        messages.send(sender, "deputy-changed", "player", target.getName());
        save();
    }

    public void togglePvp(Player player) {
        Guild guild = byPlayer(player.getUniqueId());
        if (guild == null) { messages.send(player, "no-guild"); return; }
        if (!guild.hasPermission(player.getUniqueId(), Guild.Permission.TOGGLE_PVP, defaultRole())) { messages.send(player, "no-permission"); return; }
        guild.pvp(!guild.pvp());
        messages.send(player, guild.pvp() ? "pvp-enabled" : "pvp-disabled");
        save();
    }

    public void setBase(Player player, boolean home) {
        Guild guild = byPlayer(player.getUniqueId());
        if (guild == null) { messages.send(player, "no-guild"); return; }
        if (!guild.hasPermission(player.getUniqueId(), Guild.Permission.SET_HOME, defaultRole())) { messages.send(player, "no-permission"); return; }
        if (home) guild.home(player.getLocation()); else guild.base(player.getLocation());
        messages.send(player, home ? "home-set" : "base-set");
        save();
    }

    public void ally(Player player, String targetTag) {
        Guild guild = byPlayer(player.getUniqueId());
        Guild target = byTag(targetTag);
        if (guild == null) { messages.send(player, "no-guild"); return; }
        if (!guild.canManage(player.getUniqueId())) { messages.send(player, "manage-only"); return; }
        if (target == null || target == guild) { messages.send(player, "guild-not-found", "tag", targetTag); return; }
        if (guild.allies().contains(target.tag())) {
            guild.allies().remove(target.tag()); target.allies().remove(guild.tag());
        } else {
            guild.allies().add(target.tag()); target.allies().add(guild.tag());
            messages.send(player, "alliance-created", "tag", target.tag());
        }
        save();
    }

    public void declareWar(Player player, String targetTag) {
        Guild guild = byPlayer(player.getUniqueId());
        Guild target = byTag(targetTag);
        if (guild == null) { messages.send(player, "no-guild"); return; }
        if (!guild.hasPermission(player.getUniqueId(), Guild.Permission.DECLARE_WAR, defaultRole())) { messages.send(player, "no-permission"); return; }
        if (target == null || target == guild || guild.allies().contains(target.tag())) { messages.send(player, "guild-not-found", "tag", targetTag); return; }
        long now = System.currentTimeMillis();
        if (wars.stream().anyMatch(war -> war.active(now) && war.between(guild.tag(), target.tag()))) { messages.send(player, "war-active"); return; }
        long ends = now + plugin.getConfig().getLong("wars.duration-minutes", 120) * 60_000L;
        wars.add(new DataStore.War(guild.tag(), target.tag(), now, ends));
        for (Player online : Bukkit.getOnlinePlayers()) messages.send(online, "war-started", "attacker", guild.tag(), "defender", target.tag(), "time", pl.maghub.guilds.util.Text.duration((ends - now) / 1000));
        save();
    }

    public boolean atWar(Guild first, Guild second) {
        if (first == null || second == null) return false;
        long now = System.currentTimeMillis();
        return wars.stream().anyMatch(war -> war.active(now) && war.between(first.tag(), second.tag()));
    }

    public void tickWars() {
        long now = System.currentTimeMillis();
        if (wars.removeIf(war -> war.endsAt() <= now)) store.saveWars(wars);
        invites.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    public void save() {
        store.saveGuilds(guilds.values());
        store.saveWars(wars);
    }

    public String defaultRole() { return plugin.getConfig().getString("roles.default-role", "czlonek").toLowerCase(Locale.ROOT); }

    public void ensureDefaultRoles() {
        for (Guild guild : guilds.values()) addDefaultRoles(guild);
    }

    private void addDefaultRoles(Guild guild) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("roles.defaults");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            if (guild.roles().containsKey(id.toLowerCase(Locale.ROOT))) continue;
            EnumSet<Guild.Permission> permissions = EnumSet.noneOf(Guild.Permission.class);
            for (String raw : root.getStringList(id + ".permissions")) {
                try { permissions.add(Guild.Permission.valueOf(raw)); } catch (IllegalArgumentException ignored) { }
            }
            guild.roles().put(id.toLowerCase(Locale.ROOT), new Guild.Role(id, root.getString(id + ".name", id), permissions));
        }
        for (UUID member : guild.members()) if (!guild.canManage(member)) guild.memberRoles().putIfAbsent(member, defaultRole());
    }

    private void rebuildMembership() {
        membership.clear();
        for (Guild guild : guilds.values()) for (UUID member : guild.members()) membership.put(member, guild.tag());
    }

    private Map<Material, Integer> creationCosts() {
        LinkedHashMap<Material, Integer> costs = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("creation.items");
        if (section == null) return costs;
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material != null) costs.put(material, section.getInt(key));
        }
        return costs;
    }
}
