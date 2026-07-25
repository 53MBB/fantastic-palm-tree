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
    private final DataStore dataStore;
    private final MessageService messages;
    private final LinkedHashMap<String, Guild> guilds;
    private final HashMap<UUID, String> memberIndex = new HashMap<>();
    private final HashMap<UUID, Invite> invites = new HashMap<>();
    private final ArrayList<DataStore.War> wars;

    private record Invite(String tag, long expiresAt) { }

    public GuildService(MAGGuildsPlugin plugin, DataStore dataStore, MessageService messages) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.messages = messages;
        this.guilds = new LinkedHashMap<>(dataStore.loadGuilds());
        this.wars = new ArrayList<>(dataStore.loadWars());
        for (Guild guild : guilds.values()) {
            ensureDefaultRoles(guild);
            for (UUID member : guild.members()) memberIndex.put(member, guild.tag());
        }
    }

    public Collection<Guild> all() { return Collections.unmodifiableCollection(guilds.values()); }
    public Guild byTag(String tag) { return tag == null ? null : guilds.get(tag.toUpperCase(Locale.ROOT)); }
    public Guild byPlayer(UUID uuid) { String tag = memberIndex.get(uuid); return tag == null ? null : guilds.get(tag); }
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
        int min = 2, max = 6;
        if (!tag.matches("[A-Z0-9]{" + min + "," + max + "}")) { messages.send(player, "invalid-tag", "min", min, "max", max); return false; }
        if (guilds.containsKey(tag)) { messages.send(player, "tag-taken", "tag", tag); return false; }
        int distance = plugin.getConfig().getInt("settings.minimum-guild-distance", 100);
        for (Guild other : guilds.values()) {
            Location center = other.center();
            if (center != null && center.getWorld() != null && center.getWorld().equals(player.getWorld()) && center.distanceSquared(player.getLocation()) < (double) distance * distance) {
                messages.send(player, "too-close", "distance", distance); return false;
            }
        }
        Map<Material, Integer> costs = creationCosts();
        if (!player.hasPermission("magguilds.admin") && !Items.has(player.getInventory(), costs)) { messages.send(player, "missing-items"); return false; }
        if (!player.hasPermission("magguilds.admin")) for (Map.Entry<Material, Integer> e : costs.entrySet()) Items.remove(player.getInventory(), e.getKey(), e.getValue());
        Guild guild = new Guild(tag, name, player.getUniqueId());
        Location center = player.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        guild.center(center);
        guild.base(center);
        guild.home(center);
        guild.radius(plugin.getConfig().getInt("settings.default-radius", 30));
        guild.memberLimit(plugin.getConfig().getInt("settings.default-member-limit", 12));
        guild.lives(plugin.getConfig().getInt("settings.default-lives", 3));
        guild.expiresAt(System.currentTimeMillis() + plugin.getConfig().getLong("settings.expiration-days", 14) * 86400000L);
        ensureDefaultRoles(guild);
        guilds.put(tag, guild);
        memberIndex.put(player.getUniqueId(), tag);
        placeHeart(guild);
        save();
        messages.send(player, "created", "tag", tag, "name", name);
        return true;
    }

    private Map<Material, Integer> creationCosts() {
        LinkedHashMap<Material, Integer> costs = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("creation.items");
        if (section != null) for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material != null) costs.put(material, section.getInt(key));
        }
        return costs;
    }

    private void placeHeart(Guild guild) {
        Location center = guild.center();
        if (center == null || center.getWorld() == null) return;
        int configuredY = plugin.getConfig().getInt("settings.heart-y", 30);
        int y = Math.max(center.getWorld().getMinHeight(), Math.min(center.getWorld().getMaxHeight() - 1, configuredY));
        Location heart = new Location(center.getWorld(), center.getBlockX(), y, center.getBlockZ());
        heart.getBlock().setType(Material.BEACON);
    }

    public boolean delete(Guild guild) {
        if (guild == null) return false;
        guilds.remove(guild.tag());
        for (UUID uuid : guild.members()) memberIndex.remove(uuid);
        for (Guild other : guilds.values()) other.allies().remove(guild.tag());
        wars.removeIf(w -> w.involves(guild.tag()));
        save();
        return true;
    }

    public void invite(Player sender, OfflinePlayer target) {
        Guild guild = byPlayer(sender.getUniqueId());
        if (guild == null) { messages.send(sender, "no-guild"); return; }
        if (!guild.hasPermission(sender.getUniqueId(), Guild.Permission.INVITE, defaultRole())) { messages.send(sender, "no-permission"); return; }
        if (target == null || target.getName() == null) { messages.send(sender, "player-not-found", "player", "?"); return; }
        if (byPlayer(target.getUniqueId()) != null) { messages.send(sender, "already-guild"); return; }
        long expiry = System.currentTimeMillis() + plugin.getConfig().getLong("settings.invite-seconds", 60) * 1000L;
        invites.put(target.getUniqueId(), new Invite(guild.tag(), expiry));
        messages.send(sender, "invite-sent", "player", target.getName());
        if (target.isOnline()) messages.send(target.getPlayer(), "invite-received", "tag", guild.tag());
    }

    public boolean join(Player player, String tag) {
        Guild guild = byTag(tag);
        Invite invite = invites.get(player.getUniqueId());
        if (guild == null || invite == null || invite.expiresAt() < System.currentTimeMillis() || !invite.tag().equalsIgnoreCase(tag)) { messages.send(player, "invite-missing"); return false; }
        if (guild.members().size() >= guild.memberLimit()) { messages.send(player, "member-limit"); return false; }
        guild.members().add(player.getUniqueId());
        guild.memberRoles().put(player.getUniqueId(), defaultRole());
        memberIndex.put(player.getUniqueId(), guild.tag());
        invites.remove(player.getUniqueId());
        save();
        messages.send(player, "joined", "tag", guild.tag());
        return true;
    }

    public boolean leave(Player player) {
        Guild guild = byPlayer(player.getUniqueId());
        if (guild == null) { messages.send(player, "no-guild"); return false; }
        if (guild.isLeader(player.getUniqueId())) { messages.send(player, "leader-cannot-leave"); return false; }
        removeMember(guild, player.getUniqueId());
        messages.send(player, "left");
        return true;
    }

    public void removeMember(Guild guild, UUID uuid) {
        guild.members().remove(uuid); guild.memberRoles().remove(uuid); guild.overrides().remove(uuid); memberIndex.remove(uuid);
        if (guild.deputy() != null && guild.deputy().equals(uuid)) guild.deputy(null);
        save();
    }

    public void ensureDefaultRoles(Guild guild) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("roles.defaults");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            if (guild.roles().containsKey(id.toLowerCase(Locale.ROOT))) continue;
            EnumSet<Guild.Permission> permissions = EnumSet.noneOf(Guild.Permission.class);
            for (String value : section.getStringList(id + ".permissions")) try { permissions.add(Guild.Permission.valueOf(value)); } catch (IllegalArgumentException ignored) { }
            guild.roles().put(id.toLowerCase(Locale.ROOT), new Guild.Role(id, section.getString(id + ".name", id), permissions));
        }
    }

    public String defaultRole() { return plugin.getConfig().getString("roles.default-role", "czlonek").toLowerCase(Locale.ROOT); }

    public boolean createRole(Guild guild, String name) {
        String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (id.isBlank() || guild.roles().containsKey(id)) return false;
        if (guild.roles().size() >= plugin.getConfig().getInt("roles.maximum", 12)) return false;
        guild.roles().put(id, new Guild.Role(id, name, EnumSet.noneOf(Guild.Permission.class)));
        save(); return true;
    }

    public boolean assignRole(Guild guild, UUID member, String roleId) {
        if (!guild.members().contains(member) || !guild.roles().containsKey(roleId.toLowerCase(Locale.ROOT))) return false;
        guild.memberRoles().put(member, roleId.toLowerCase(Locale.ROOT)); save(); return true;
    }

    public boolean activeWarBetween(String first, String second) {
        long now = System.currentTimeMillis();
        return wars.stream().anyMatch(w -> w.active(now) && w.between(first, second));
    }

    public boolean declareWar(Guild attacker, Guild defender) {
        if (!plugin.getConfig().getBoolean("wars.enabled", true) || attacker == null || defender == null || attacker == defender) return false;
        long now = System.currentTimeMillis();
        if (wars.stream().anyMatch(w -> w.active(now) && (w.involves(attacker.tag()) || w.involves(defender.tag())))) return false;
        long ends = now + plugin.getConfig().getLong("wars.duration-minutes", 120) * 60000L;
        wars.add(new DataStore.War(attacker.tag(), defender.tag(), now, ends));
        save(); return true;
    }

    public void tickWars() {
        long now = System.currentTimeMillis();
        boolean changed = wars.removeIf(w -> w.endsAt() <= now);
        if (changed) dataStore.saveWars(wars);
    }

    public void save() {
        dataStore.saveGuilds(guilds.values());
        dataStore.saveWars(wars);
    }
}
