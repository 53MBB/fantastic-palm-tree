package pl.maghub.guilds.model;

import org.bukkit.Location;

import java.util.*;

public final class Guild {
    public enum Permission {
        BREAK_BLOCKS, PLACE_BLOCKS, BREAK_GRAVITY, PLACE_REDSTONE_TNT,
        FLUID_LOW, FLUID_MIDDLE, FLUID_HIGH, TOGGLE_PVP,
        STORAGE_DEPOSIT, STORAGE_WITHDRAW, MANAGE_CONTRIBUTIONS,
        INVITE, KICK, SET_HOME, START_REGEN, MANAGE_ROLES, DECLARE_WAR
    }

    public enum TriState { INHERIT, ALLOW, DENY }

    public static final class Role {
        private final String id;
        private String name;
        private final EnumSet<Permission> permissions;

        public Role(String id, String name, Collection<Permission> permissions) {
            this.id = id.toLowerCase(Locale.ROOT);
            this.name = name;
            this.permissions = permissions.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(permissions);
        }

        public String id() { return id; }
        public String name() { return name; }
        public void name(String name) { this.name = name; }
        public EnumSet<Permission> permissions() { return permissions; }
    }

    private final String tag;
    private String name;
    private UUID leader;
    private UUID deputy;
    private final LinkedHashSet<UUID> members = new LinkedHashSet<>();
    private final LinkedHashSet<String> allies = new LinkedHashSet<>();
    private Location center;
    private Location base;
    private Location home;
    private int radius;
    private int memberLimit;
    private boolean pvp;
    private int lives;
    private long points;
    private long kills;
    private long deaths;
    private long expiresAt;
    private int radiusLevel;
    private int memberLevel;
    private String storageData = "";
    private final LinkedHashMap<String, Role> roles = new LinkedHashMap<>();
    private final HashMap<UUID, String> memberRoles = new HashMap<>();
    private final HashMap<UUID, EnumMap<Permission, TriState>> overrides = new HashMap<>();
    private final HashMap<String, Long> achievementStats = new HashMap<>();
    private final HashSet<String> claimedAchievements = new HashSet<>();
    private final HashSet<String> unlockedAchievements = new HashSet<>();

    public Guild(String tag, String name, UUID leader) {
        this.tag = tag.toUpperCase(Locale.ROOT);
        this.name = name;
        this.leader = leader;
        this.members.add(leader);
    }

    public String tag() { return tag; }
    public String name() { return name; }
    public void name(String value) { name = value; }
    public UUID leader() { return leader; }
    public void leader(UUID value) { leader = value; members.add(value); }
    public UUID deputy() { return deputy; }
    public void deputy(UUID value) { deputy = value; if (value != null) members.add(value); }
    public Set<UUID> members() { return members; }
    public Set<String> allies() { return allies; }
    public Location center() { return center == null ? null : center.clone(); }
    public void center(Location value) { center = value == null ? null : value.clone(); }
    public Location base() { return base == null ? null : base.clone(); }
    public void base(Location value) { base = value == null ? null : value.clone(); }
    public Location home() { return home == null ? null : home.clone(); }
    public void home(Location value) { home = value == null ? null : value.clone(); }
    public int radius() { return radius; }
    public void radius(int value) { radius = value; }
    public int memberLimit() { return memberLimit; }
    public void memberLimit(int value) { memberLimit = value; }
    public boolean pvp() { return pvp; }
    public void pvp(boolean value) { pvp = value; }
    public int lives() { return lives; }
    public void lives(int value) { lives = Math.max(0, value); }
    public long points() { return points; }
    public void points(long value) { points = Math.max(0, value); }
    public long kills() { return kills; }
    public void kills(long value) { kills = Math.max(0, value); }
    public long deaths() { return deaths; }
    public void deaths(long value) { deaths = Math.max(0, value); }
    public long expiresAt() { return expiresAt; }
    public void expiresAt(long value) { expiresAt = value; }
    public int radiusLevel() { return radiusLevel; }
    public void radiusLevel(int value) { radiusLevel = Math.max(0, value); }
    public int memberLevel() { return memberLevel; }
    public void memberLevel(int value) { memberLevel = Math.max(0, value); }
    public String storageData() { return storageData; }
    public void storageData(String value) { storageData = value == null ? "" : value; }
    public Map<String, Role> roles() { return roles; }
    public Map<UUID, String> memberRoles() { return memberRoles; }
    public Map<UUID, EnumMap<Permission, TriState>> overrides() { return overrides; }
    public Map<String, Long> achievementStats() { return achievementStats; }
    public Set<String> claimedAchievements() { return claimedAchievements; }
    public Set<String> unlockedAchievements() { return unlockedAchievements; }

    public boolean isLeader(UUID uuid) { return leader.equals(uuid); }
    public boolean isDeputy(UUID uuid) { return deputy != null && deputy.equals(uuid); }
    public boolean canManage(UUID uuid) { return isLeader(uuid) || isDeputy(uuid); }
    public boolean isMember(UUID uuid) { return members.contains(uuid); }

    public String roleId(UUID uuid, String defaultRole) {
        if (isLeader(uuid)) return "lider";
        if (isDeputy(uuid)) return "zastepca";
        return memberRoles.getOrDefault(uuid, defaultRole);
    }

    public boolean hasPermission(UUID uuid, Permission permission, String defaultRole) {
        if (canManage(uuid)) return true;
        EnumMap<Permission, TriState> playerOverrides = overrides.get(uuid);
        if (playerOverrides != null) {
            TriState state = playerOverrides.get(permission);
            if (state == TriState.ALLOW) return true;
            if (state == TriState.DENY) return false;
        }
        Role role = roles.get(roleId(uuid, defaultRole));
        return role != null && role.permissions().contains(permission);
    }

    public long stat(String category) { return achievementStats.getOrDefault(category, 0L); }
    public void addStat(String category, long amount) {
        achievementStats.merge(category.toLowerCase(Locale.ROOT), Math.max(0, amount), Long::sum);
    }
}
