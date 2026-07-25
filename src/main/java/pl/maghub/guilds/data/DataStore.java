package pl.maghub.guilds.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.maghub.guilds.MAGGuildsPlugin;
import pl.maghub.guilds.model.Guild;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class DataStore {
    public static final class PlayerProfile {
        private final UUID uuid;
        private String lastName;
        private long points;
        private long kills;
        private long deaths;
        private boolean pendingCombatDeath;

        public PlayerProfile(UUID uuid, String lastName, long points) {
            this.uuid = uuid;
            this.lastName = lastName == null ? uuid.toString() : lastName;
            this.points = points;
        }
        public UUID uuid() { return uuid; }
        public String lastName() { return lastName; }
        public void lastName(String value) { if (value != null) lastName = value; }
        public long points() { return points; }
        public void points(long value) { points = Math.max(0, value); }
        public long kills() { return kills; }
        public void kills(long value) { kills = Math.max(0, value); }
        public long deaths() { return deaths; }
        public void deaths(long value) { deaths = Math.max(0, value); }
        public boolean pendingCombatDeath() { return pendingCombatDeath; }
        public void pendingCombatDeath(boolean value) { pendingCombatDeath = value; }
    }

    public record War(String attacker, String defender, long startedAt, long endsAt) {
        public boolean active(long now) { return now >= startedAt && now < endsAt; }
        public boolean involves(String tag) { return attacker.equalsIgnoreCase(tag) || defender.equalsIgnoreCase(tag); }
        public boolean between(String first, String second) {
            return (attacker.equalsIgnoreCase(first) && defender.equalsIgnoreCase(second))
                    || (attacker.equalsIgnoreCase(second) && defender.equalsIgnoreCase(first));
        }
    }

    private final MAGGuildsPlugin plugin;
    private final File guildsFile;
    private final File playersFile;
    private final File warsFile;
    private final LinkedHashMap<UUID, PlayerProfile> profiles = new LinkedHashMap<>();

    public DataStore(MAGGuildsPlugin plugin) {
        this.plugin = plugin;
        guildsFile = new File(plugin.getDataFolder(), "guilds.yml");
        playersFile = new File(plugin.getDataFolder(), "players.yml");
        warsFile = new File(plugin.getDataFolder(), "wars.yml");
    }

    public Map<String, Guild> loadGuilds() {
        LinkedHashMap<String, Guild> result = new LinkedHashMap<>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(guildsFile);
        ConfigurationSection root = yaml.getConfigurationSection("guilds");
        if (root == null) return result;
        for (String tag : root.getKeys(false)) {
            String path = "guilds." + tag;
            try {
                UUID leader = UUID.fromString(yaml.getString(path + ".leader"));
                Guild guild = new Guild(tag, yaml.getString(path + ".name", tag), leader);
                String deputy = yaml.getString(path + ".deputy");
                if (deputy != null && !deputy.isBlank()) guild.deputy(UUID.fromString(deputy));
                guild.members().clear();
                for (String value : yaml.getStringList(path + ".members")) guild.members().add(UUID.fromString(value));
                guild.members().add(leader);
                guild.allies().addAll(yaml.getStringList(path + ".allies"));
                guild.center(readLocation(yaml.getString(path + ".center")));
                guild.base(readLocation(yaml.getString(path + ".base")));
                guild.home(readLocation(yaml.getString(path + ".home")));
                guild.radius(yaml.getInt(path + ".radius", 30));
                guild.memberLimit(yaml.getInt(path + ".member-limit", 12));
                guild.pvp(yaml.getBoolean(path + ".pvp", false));
                guild.lives(yaml.getInt(path + ".lives", 3));
                guild.points(yaml.getLong(path + ".points", 0));
                guild.kills(yaml.getLong(path + ".kills", 0));
                guild.deaths(yaml.getLong(path + ".deaths", 0));
                guild.expiresAt(yaml.getLong(path + ".expires-at", 0));
                guild.radiusLevel(yaml.getInt(path + ".radius-level", 0));
                guild.memberLevel(yaml.getInt(path + ".member-level", 0));
                guild.storageData(yaml.getString(path + ".storage", ""));

                ConfigurationSection roles = yaml.getConfigurationSection(path + ".roles");
                if (roles != null) {
                    for (String id : roles.getKeys(false)) {
                        EnumSet<Guild.Permission> permissions = EnumSet.noneOf(Guild.Permission.class);
                        for (String permission : yaml.getStringList(path + ".roles." + id + ".permissions")) {
                            try { permissions.add(Guild.Permission.valueOf(permission)); } catch (IllegalArgumentException ignored) { }
                        }
                        guild.roles().put(id.toLowerCase(Locale.ROOT), new Guild.Role(id, yaml.getString(path + ".roles." + id + ".name", id), permissions));
                    }
                }
                ConfigurationSection memberRoles = yaml.getConfigurationSection(path + ".member-roles");
                if (memberRoles != null) {
                    for (String uuid : memberRoles.getKeys(false)) guild.memberRoles().put(UUID.fromString(uuid), memberRoles.getString(uuid));
                }
                ConfigurationSection overrides = yaml.getConfigurationSection(path + ".overrides");
                if (overrides != null) {
                    for (String uuidText : overrides.getKeys(false)) {
                        UUID uuid = UUID.fromString(uuidText);
                        EnumMap<Guild.Permission, Guild.TriState> map = new EnumMap<>(Guild.Permission.class);
                        ConfigurationSection permissions = overrides.getConfigurationSection(uuidText);
                        if (permissions != null) {
                            for (String permission : permissions.getKeys(false)) {
                                try {
                                    map.put(Guild.Permission.valueOf(permission), Guild.TriState.valueOf(permissions.getString(permission, "INHERIT")));
                                } catch (IllegalArgumentException ignored) { }
                            }
                        }
                        guild.overrides().put(uuid, map);
                    }
                }
                ConfigurationSection stats = yaml.getConfigurationSection(path + ".achievement-stats");
                if (stats != null) for (String key : stats.getKeys(false)) guild.achievementStats().put(key, stats.getLong(key));
                guild.claimedAchievements().addAll(yaml.getStringList(path + ".claimed-achievements"));
                guild.unlockedAchievements().addAll(yaml.getStringList(path + ".unlocked-achievements"));
                result.put(guild.tag(), guild);
            } catch (Exception exception) {
                plugin.getLogger().warning("Pominieto uszkodzona gildie " + tag + ": " + exception.getMessage());
            }
        }
        return result;
    }

    public void saveGuilds(Collection<Guild> guilds) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Guild guild : guilds) {
            String path = "guilds." + guild.tag();
            yaml.set(path + ".name", guild.name());
            yaml.set(path + ".leader", guild.leader().toString());
            yaml.set(path + ".deputy", guild.deputy() == null ? null : guild.deputy().toString());
            yaml.set(path + ".members", guild.members().stream().map(UUID::toString).toList());
            yaml.set(path + ".allies", new ArrayList<>(guild.allies()));
            yaml.set(path + ".center", writeLocation(guild.center()));
            yaml.set(path + ".base", writeLocation(guild.base()));
            yaml.set(path + ".home", writeLocation(guild.home()));
            yaml.set(path + ".radius", guild.radius());
            yaml.set(path + ".member-limit", guild.memberLimit());
            yaml.set(path + ".pvp", guild.pvp());
            yaml.set(path + ".lives", guild.lives());
            yaml.set(path + ".points", guild.points());
            yaml.set(path + ".kills", guild.kills());
            yaml.set(path + ".deaths", guild.deaths());
            yaml.set(path + ".expires-at", guild.expiresAt());
            yaml.set(path + ".radius-level", guild.radiusLevel());
            yaml.set(path + ".member-level", guild.memberLevel());
            yaml.set(path + ".storage", guild.storageData());
            for (Guild.Role role : guild.roles().values()) {
                String rolePath = path + ".roles." + role.id();
                yaml.set(rolePath + ".name", role.name());
                yaml.set(rolePath + ".permissions", role.permissions().stream().map(Enum::name).toList());
            }
            for (Map.Entry<UUID, String> entry : guild.memberRoles().entrySet()) yaml.set(path + ".member-roles." + entry.getKey(), entry.getValue());
            for (Map.Entry<UUID, EnumMap<Guild.Permission, Guild.TriState>> entry : guild.overrides().entrySet()) {
                for (Map.Entry<Guild.Permission, Guild.TriState> state : entry.getValue().entrySet()) {
                    yaml.set(path + ".overrides." + entry.getKey() + "." + state.getKey().name(), state.getValue().name());
                }
            }
            for (Map.Entry<String, Long> stat : guild.achievementStats().entrySet()) yaml.set(path + ".achievement-stats." + stat.getKey(), stat.getValue());
            yaml.set(path + ".claimed-achievements", new ArrayList<>(guild.claimedAchievements()));
            yaml.set(path + ".unlocked-achievements", new ArrayList<>(guild.unlockedAchievements()));
        }
        save(yaml, guildsFile);
    }

    public void loadProfiles() {
        profiles.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(playersFile);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) return;
        for (String uuidText : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidText);
                String path = "players." + uuidText;
                PlayerProfile profile = new PlayerProfile(uuid, yaml.getString(path + ".name", uuidText), yaml.getLong(path + ".points", plugin.getConfig().getLong("ranking.start-points", 1000)));
                profile.kills(yaml.getLong(path + ".kills"));
                profile.deaths(yaml.getLong(path + ".deaths"));
                profile.pendingCombatDeath(yaml.getBoolean(path + ".pending-combat-death"));
                profiles.put(uuid, profile);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public void saveProfiles() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PlayerProfile profile : profiles.values()) {
            String path = "players." + profile.uuid();
            yaml.set(path + ".name", profile.lastName());
            yaml.set(path + ".points", profile.points());
            yaml.set(path + ".kills", profile.kills());
            yaml.set(path + ".deaths", profile.deaths());
            yaml.set(path + ".pending-combat-death", profile.pendingCombatDeath());
        }
        save(yaml, playersFile);
    }

    public PlayerProfile profile(UUID uuid, String name) {
        return profiles.computeIfAbsent(uuid, key -> new PlayerProfile(key, name, plugin.getConfig().getLong("ranking.start-points", 1000)));
    }
    public Collection<PlayerProfile> profiles() { return profiles.values(); }

    public List<War> loadWars() {
        List<War> wars = new ArrayList<>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(warsFile);
        ConfigurationSection root = yaml.getConfigurationSection("wars");
        if (root == null) return wars;
        for (String id : root.getKeys(false)) {
            String path = "wars." + id;
            wars.add(new War(yaml.getString(path + ".attacker", ""), yaml.getString(path + ".defender", ""), yaml.getLong(path + ".started-at"), yaml.getLong(path + ".ends-at")));
        }
        return wars;
    }

    public void saveWars(Collection<War> wars) {
        YamlConfiguration yaml = new YamlConfiguration();
        int index = 0;
        for (War war : wars) {
            String path = "wars." + (++index);
            yaml.set(path + ".attacker", war.attacker());
            yaml.set(path + ".defender", war.defender());
            yaml.set(path + ".started-at", war.startedAt());
            yaml.set(path + ".ends-at", war.endsAt());
        }
        save(yaml, warsFile);
    }

    private void save(YamlConfiguration yaml, File file) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Nie mozna zapisac " + file.getName() + ": " + exception.getMessage());
        }
    }

    public static String writeLocation(Location location) {
        if (location == null || location.getWorld() == null) return null;
        return location.getWorld().getName() + ";" + location.getX() + ";" + location.getY() + ";" + location.getZ() + ";" + location.getYaw() + ";" + location.getPitch();
    }

    public static Location readLocation(String value) {
        if (value == null || value.isBlank()) return null;
        String[] split = value.split(";");
        if (split.length < 4) return null;
        World world = Bukkit.getWorld(split[0]);
        if (world == null) return null;
        float yaw = split.length > 4 ? Float.parseFloat(split[4]) : 0F;
        float pitch = split.length > 5 ? Float.parseFloat(split[5]) : 0F;
        return new Location(world, Double.parseDouble(split[1]), Double.parseDouble(split[2]), Double.parseDouble(split[3]), yaw, pitch);
    }
}
