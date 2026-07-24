package pl.maghub.incognito;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MAGGuildsIncognitoPlugin extends JavaPlugin implements Listener {
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final String GUI_MARKER = "MAGI-INCOGNITO";

    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, NamePair> labels = new ConcurrentHashMap<>();
    private final Map<String, String> previousTeams = new ConcurrentHashMap<>();
    private File dataFile;
    private YamlConfiguration data;
    private File messagesFile;
    private YamlConfiguration messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        messagesFile = new File(getDataFolder(), "messages.yml");
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        dataFile = new File(getDataFolder(), "incognito.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        loadStates();

        Bukkit.getPluginManager().registerEvents(this, this);
        installProtocolListener();
        Bukkit.getScheduler().runTaskTimer(this, this::tickLabels, 5L, Math.max(1L, getConfig().getLong("incognito.update-interval-ticks", 5L)));
        Bukkit.getScheduler().runTaskLater(this, () -> Bukkit.getOnlinePlayers().forEach(player -> {
            if (enabled.contains(player.getUniqueId())) activateVisuals(player);
        }), 20L);
        getLogger().info("MAGGuilds Incognito 4.0.25 uruchomiony.");
    }

    @Override
    public void onDisable() {
        new HashSet<>(labels.keySet()).forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) removeVisuals(player, false);
        });
        saveStates();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("only-player"));
            return true;
        }
        if (!player.hasPermission("magguilds.incognito")) {
            player.sendMessage(msg("no-permission"));
            return true;
        }
        if (args.length > 0 && player.hasPermission("magguilds.incognito.admin")) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target != null) {
                openGui(target);
                return true;
            }
        }
        openGui(player);
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (UUID uuid : enabled) {
                Player target = Bukkit.getPlayer(uuid);
                if (target != null) applyViewerState(player, target);
            }
            if (enabled.contains(player.getUniqueId())) activateVisuals(player);
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        removeVisuals(player, false);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null || !title.contains("INCOGNITO")) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;
        int slot = event.getRawSlot();
        int onSlot = getConfig().getInt("gui.enabled-slot", 11);
        int offSlot = getConfig().getInt("gui.disabled-slot", 15);
        if (slot == onSlot) {
            setEnabled(player, true);
            openGui(player);
        } else if (slot == offSlot) {
            setEnabled(player, false);
            openGui(player);
        }
    }

    private void openGui(Player player) {
        int size = Math.max(9, Math.min(54, getConfig().getInt("gui.rows", 3) * 9));
        Inventory inv = Bukkit.createInventory(null, size, color(getConfig().getString("gui.title", "&5&lINCOGNITO")) + ChatColor.DARK_GRAY + GUI_MARKER);
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < size; i++) inv.setItem(i, filler);
        inv.setItem(getConfig().getInt("gui.enabled-slot", 11), item(Material.LIME_DYE, text("gui.enable-name"), textList("gui.enable-lore", player)));
        inv.setItem(getConfig().getInt("gui.disabled-slot", 15), item(Material.RED_DYE, text("gui.disable-name"), textList("gui.disable-lore", player)));
        boolean state = enabled.contains(player.getUniqueId());
        String status = text(state ? "gui.status-enabled" : "gui.status-disabled");
        inv.setItem(getConfig().getInt("gui.status-slot", 13), item(state ? Material.PLAYER_HEAD : Material.SKELETON_SKULL, status, textList("gui.status-lore", player)));
        player.openInventory(inv);
    }

    private void setEnabled(Player player, boolean state) {
        if (state) {
            if (enabled.add(player.getUniqueId())) activateVisuals(player);
            player.sendMessage(msg("enabled"));
        } else {
            enabled.remove(player.getUniqueId());
            removeVisuals(player, true);
            player.sendMessage(msg("disabled"));
        }
        saveStates();
    }

    private void activateVisuals(Player player) {
        removeVisuals(player, false);
        World world = player.getWorld();
        String anonymous = getConfig().getString("incognito.anonymous-name", "Anonimowy");
        ArmorStand normal = spawnLabel(world, player.getLocation(), anonymous);
        ArmorStand admin = spawnLabel(world, player.getLocation(), anonymous + " (" + player.getName() + ")");
        labels.put(player.getUniqueId(), new NamePair(normal, admin));
        for (Player viewer : Bukkit.getOnlinePlayers()) applyViewerState(viewer, player);
        refreshSkin(player);
    }

    private ArmorStand spawnLabel(World world, Location location, String name) {
        return world.spawn(location, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setCollidable(false);
            stand.setPersistent(false);
            stand.setCustomName(color(name));
            stand.setCustomNameVisible(true);
            stand.setSmall(true);
        });
    }

    private void applyViewerState(Player viewer, Player target) {
        if (!enabled.contains(target.getUniqueId())) return;
        hideRealName(viewer, target);
        NamePair pair = labels.get(target.getUniqueId());
        if (pair == null) return;
        boolean admin = viewer.hasPermission("magguilds.incognito.see-real-name");
        if (admin) {
            viewer.hideEntity(this, pair.normal());
            viewer.showEntity(this, pair.admin());
        } else {
            viewer.showEntity(this, pair.normal());
            viewer.hideEntity(this, pair.admin());
        }
    }

    private void hideRealName(Player viewer, Player target) {
        Scoreboard board = viewer.getScoreboard();
        String key = viewer.getUniqueId() + ":" + target.getUniqueId();
        Team existing = board.getEntryTeam(target.getName());
        if (existing != null && !existing.getName().startsWith("mgi")) previousTeams.putIfAbsent(key, existing.getName());
        String name = "mgi" + target.getUniqueId().toString().replace("-", "").substring(0, 12);
        Team team = board.getTeam(name);
        if (team == null) team = board.registerNewTeam(name);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        team.addEntry(target.getName());
    }

    private void restoreRealName(Player viewer, Player target) {
        Scoreboard board = viewer.getScoreboard();
        String name = "mgi" + target.getUniqueId().toString().replace("-", "").substring(0, 12);
        Team team = board.getTeam(name);
        if (team != null) {
            team.removeEntry(target.getName());
            if (team.getEntries().isEmpty()) team.unregister();
        }
        String old = previousTeams.remove(viewer.getUniqueId() + ":" + target.getUniqueId());
        if (old != null) {
            Team previous = board.getTeam(old);
            if (previous != null) previous.addEntry(target.getName());
        }
    }

    private void removeVisuals(Player player, boolean refresh) {
        NamePair pair = labels.remove(player.getUniqueId());
        if (pair != null) {
            remove(pair.normal());
            remove(pair.admin());
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) restoreRealName(viewer, player);
        if (refresh) refreshSkin(player);
    }

    private void remove(Entity entity) {
        if (entity != null && entity.isValid()) entity.remove();
    }

    private void tickLabels() {
        double height = getConfig().getDouble("incognito.name-height", 2.25D);
        for (Map.Entry<UUID, NamePair> entry : new HashMap<>(labels).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || !enabled.contains(entry.getKey())) continue;
            Location location = player.getLocation().add(0.0D, height, 0.0D);
            NamePair pair = entry.getValue();
            if (pair.normal().getWorld() != player.getWorld()) {
                activateVisuals(player);
                continue;
            }
            pair.normal().teleport(location);
            pair.admin().teleport(location.clone().add(0.0D, 0.23D, 0.0D));
        }
    }

    private void installProtocolListener() {
        List<PacketType> types = new ArrayList<>();
        if (PacketType.Play.Server.PLAYER_INFO.isSupported()) types.add(PacketType.Play.Server.PLAYER_INFO);
        if (PacketType.Play.Server.PLAYER_INFO_UPDATE.isSupported()) types.add(PacketType.Play.Server.PLAYER_INFO_UPDATE);
        if (types.isEmpty()) return;
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, types) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!getConfig().getBoolean("incognito.change-skin", true)) return;
                String texture = getConfig().getString("incognito.skin.texture", "");
                if (texture == null || texture.isBlank()) return;
                List<PlayerInfoData> list = event.getPacket().getPlayerInfoDataLists().readSafely(0);
                if (list == null) return;
                String signature = getConfig().getString("incognito.skin.signature", "");
                boolean changed = false;
                for (PlayerInfoData info : list) {
                    WrappedGameProfile profile = info.getProfile();
                    if (profile == null || !enabled.contains(profile.getUUID())) continue;
                    profile.getProperties().removeAll("textures");
                    profile.getProperties().put("textures", new WrappedSignedProperty("textures", texture, signature == null || signature.isBlank() ? null : signature));
                    changed = true;
                }
                if (changed) event.getPacket().getPlayerInfoDataLists().writeSafely(0, list);
            }
        });
    }

    private void refreshSkin(Player target) {
        Bukkit.getScheduler().runTask(this, () -> {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(target)) continue;
                viewer.hidePlayer(this, target);
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (viewer.isOnline() && target.isOnline()) viewer.showPlayer(this, target);
                    if (target.isOnline() && enabled.contains(target.getUniqueId())) applyViewerState(viewer, target);
                }, 3L);
            }
        });
    }

    private void loadStates() {
        for (String raw : data.getStringList("enabled")) {
            try { enabled.add(UUID.fromString(raw)); } catch (IllegalArgumentException ignored) { }
        }
    }

    private void saveStates() {
        if (!getConfig().getBoolean("incognito.save-state", true)) return;
        List<String> values = enabled.stream().map(UUID::toString).toList();
        data.set("enabled", values);
        try { data.save(dataFile); } catch (IOException ex) { getLogger().warning("Nie mozna zapisac incognito.yml: " + ex.getMessage()); }
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(lore.stream().map(this::color).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private String msg(String path) {
        return color(messages.getString(path, "").replace("%prefix%", messages.getString("prefix", "")));
    }

    private String text(String path) {
        return color(messages.getString(path, path));
    }

    private List<String> textList(String path, Player player) {
        String anonymous = getConfig().getString("incognito.anonymous-name", "Anonimowy");
        List<String> result = new ArrayList<>();
        for (String line : messages.getStringList(path)) {
            result.add(color(line.replace("%anonymous%", anonymous).replace("%player%", player.getName())));
        }
        return result;
    }

    private String color(String input) {
        if (input == null) return "";
        Matcher matcher = HEX.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) replacement.append('§').append(c);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    private record NamePair(ArmorStand normal, ArmorStand admin) { }
}
