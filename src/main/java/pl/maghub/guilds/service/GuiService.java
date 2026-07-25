package pl.maghub.guilds.service;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import pl.maghub.guilds.MAGGuildsPlugin;
import pl.maghub.guilds.data.DataStore;
import pl.maghub.guilds.model.Guild;
import pl.maghub.guilds.util.Items;

import java.util.*;

public final class GuiService {
    private sealed interface Holder extends InventoryHolder permits MainHolder, MembersHolder, PlayerPermissionsHolder, RolesHolder, AchievementsHolder, StorageHolder { default Inventory getInventory() { return null; } }
    private record MainHolder(String tag) implements Holder {}
    private record MembersHolder(String tag) implements Holder {}
    private record PlayerPermissionsHolder(String tag, UUID target) implements Holder {}
    private record RolesHolder(String tag) implements Holder {}
    private record AchievementsHolder(String tag, String category) implements Holder {}
    private record StorageHolder(String tag) implements Holder {}

    private final MAGGuildsPlugin plugin;
    private final GuildService guilds;
    private final DataStore store;
    private final MessageService messages;
    private final RegenerationService regeneration;
    private final AchievementService achievements;

    public GuiService(MAGGuildsPlugin plugin, GuildService guilds, DataStore store, MessageService messages, RegenerationService regeneration, AchievementService achievements) {
        this.plugin = plugin; this.guilds = guilds; this.store = store; this.messages = messages; this.regeneration = regeneration; this.achievements = achievements;
    }

    public void openMain(Player player) {
        Guild guild = guilds.byPlayer(player.getUniqueId());
        if (guild == null) { messages.send(player, "no-guild"); return; }
        Inventory inventory = Bukkit.createInventory(new MainHolder(guild.tag()), 27, "§5§lMAGGuilds §8• §f" + guild.tag());
        inventory.setItem(10, Items.menu(Material.PLAYER_HEAD, "&#D946EF&lCZLONKOWIE I ROLE", List.of("&#C4B5FDLPM: otworz uprawnienia")));
        inventory.setItem(12, Items.menu(Material.CHEST, "&#D946EF&lMAGAZYN", List.of("&#C4B5FDWspolny magazyn gildii")));
        inventory.setItem(14, Items.menu(Material.NETHER_STAR, "&#D946EF&lOSIAGNIECIA", List.of("&#C4B5FDWspolny postep gildii")));
        inventory.setItem(16, Items.menu(Material.EMERALD_BLOCK, "&#D946EF&lREGENERACJA", List.of("&#C4B5FDZniszczone bloki: &#F8FAFC" + regeneration.damaged(guild), "&#C4B5FDKliknij, aby rozpoczac")));
        player.openInventory(inventory);
    }

    public void openMembers(Player player) {
        Guild guild = guilds.byPlayer(player.getUniqueId());
        if (guild == null) { messages.send(player, "no-guild"); return; }
        Inventory inventory = Bukkit.createInventory(new MembersHolder(guild.tag()), 54, "§5§lUPRAWNIENIA §8• §f" + guild.tag());
        int slot = 0;
        for (UUID uuid : guild.members()) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName("§f" + (target.getName() == null ? uuid.toString() : target.getName()));
                meta.setLore(List.of("§dLPM §8- §fuprawnienia gracza", "§dPPM §8- §fnastepna rola", "§7Rola: §f" + guild.roleId(uuid, guilds.defaultRole())));
                head.setItemMeta(meta);
            }
            inventory.setItem(slot++, head);
            if (slot >= 45) break;
        }
        inventory.setItem(49, Items.menu(Material.BOOK, "&#D946EF&lROLE GILDII", List.of("&#C4B5FDKliknij, aby edytowac role")));
        player.openInventory(inventory);
    }

    public void openRoles(Player player) {
        Guild guild = guilds.byPlayer(player.getUniqueId());
        if (guild == null) return;
        Inventory inventory = Bukkit.createInventory(new RolesHolder(guild.tag()), 27, "§5§lROLE §8• §f" + guild.tag());
        int slot = 0;
        for (Guild.Role role : guild.roles().values()) inventory.setItem(slot++, Items.menu(Material.NAME_TAG, "&#D946EF&l" + role.name(), List.of("&#C4B5FDUprawnienia: &#F8FAFC" + role.permissions().size(), "&#C4B5FDLPM: przelacz pierwsze uprawnienie", "&#C4B5FDSHIFT+PPM: usun role")));
        player.openInventory(inventory);
    }

    public void openPermissions(Player player, UUID target) {
        Guild guild = guilds.byPlayer(player.getUniqueId());
        if (guild == null) return;
        Inventory inventory = Bukkit.createInventory(new PlayerPermissionsHolder(guild.tag(), target), 54, "§5§lUPRAWNIENIA GRACZA");
        int slot = 0;
        for (Guild.Permission permission : Guild.Permission.values()) {
            Guild.TriState state = guild.overrides().getOrDefault(target, new EnumMap<>(Guild.Permission.class)).getOrDefault(permission, Guild.TriState.INHERIT);
            inventory.setItem(slot++, Items.menu(state == Guild.TriState.ALLOW ? Material.LIME_DYE : state == Guild.TriState.DENY ? Material.RED_DYE : Material.GRAY_DYE,
                    "&#D946EF&l" + permission.name(), List.of("&#C4B5FDStan: &#F8FAFC" + state, "&#C4B5FDLPM: zmien", "&#C4B5FDPPM: dziedzicz z roli")));
        }
        player.openInventory(inventory);
    }

    public void openAchievements(Player player, String category) {
        Guild guild = guilds.byPlayer(player.getUniqueId());
        if (guild == null) { messages.send(player, "no-guild"); return; }
        Inventory inventory = Bukkit.createInventory(new AchievementsHolder(guild.tag(), category), 54, "§5§lOSIAGNIECIA §8• §f" + guild.tag());
        if (category == null) {
            int slot = 10;
            for (AchievementService.Category definition : achievements.categories()) {
                Material material = Material.matchMaterial(definition.material()); if (material == null) material = Material.PAPER;
                long progress = guild.stat(definition.id());
                inventory.setItem(slot++, Items.menu(material, "&#D946EF&l" + definition.name(), List.of("&#C4B5FDPostep: &#F8FAFC" + progress, "&#C4B5FDKliknij, aby otworzyc")));
                if (slot == 17) slot = 19;
            }
        } else {
            AchievementService.Category definition = achievements.category(category);
            if (definition != null) for (int i = 0; i < definition.thresholds().size(); i++) {
                int level = i + 1;
                String key = definition.id() + ":" + level;
                long target = definition.thresholds().get(i);
                boolean ready = guild.stat(definition.id()) >= target;
                boolean claimed = guild.claimedAchievements().contains(key);
                Material material = claimed ? Material.LIME_STAINED_GLASS_PANE : ready ? Material.GOLD_INGOT : Material.RED_STAINED_GLASS_PANE;
                inventory.setItem(10 + i, Items.menu(material, "&#D946EF&lPOZIOM " + level, List.of("&#C4B5FDCel: &#F8FAFC" + target, "&#C4B5FDPostep: &#F8FAFC" + guild.stat(definition.id()), "&#C4B5FDStan: &#F8FAFC" + (claimed ? "ODEBRANE" : ready ? "GOTOWE" : "ZABLOKOWANE"))));
            }
            inventory.setItem(49, Items.menu(Material.CHEST, "&#34D399&lODBIERZ WSZYSTKIE", List.of("&#C4B5FDTylko lider lub zastepca")));
        }
        player.openInventory(inventory);
    }

    public void openStorage(Player player) {
        Guild guild = guilds.byPlayer(player.getUniqueId());
        if (guild == null) return;
        Inventory inventory = Bukkit.createInventory(new StorageHolder(guild.tag()), 54, "§5§lMAGAZYN §8• §f" + guild.tag());
        inventory.setContents(Items.decode(guild.storageData(), 54));
        player.openInventory(inventory);
    }

    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        Guild guild = guilds.byTag(holder instanceof MainHolder h ? h.tag() : holder instanceof MembersHolder h ? h.tag() : holder instanceof PlayerPermissionsHolder h ? h.tag() : holder instanceof RolesHolder h ? h.tag() : holder instanceof AchievementsHolder h ? h.tag() : ((StorageHolder) holder).tag());
        if (guild == null || !guild.isMember(player.getUniqueId())) { player.closeInventory(); return; }
        int slot = event.getRawSlot();
        if (holder instanceof MainHolder) {
            if (slot == 10) openMembers(player); else if (slot == 12) openStorage(player); else if (slot == 14) openAchievements(player, null); else if (slot == 16) { player.closeInventory(); regeneration.start(player); }
        } else if (holder instanceof MembersHolder) {
            if (slot == 49) { openRoles(player); return; }
            ItemStack item = event.getCurrentItem();
            if (item == null || !(item.getItemMeta() instanceof SkullMeta skull) || skull.getOwningPlayer() == null) return;
            UUID target = skull.getOwningPlayer().getUniqueId();
            if (event.isLeftClick()) openPermissions(player, target);
            else if (event.isRightClick() && guild.canManage(player.getUniqueId())) {
                List<String> roles = new ArrayList<>(guild.roles().keySet());
                if (roles.isEmpty()) return;
                String current = guild.memberRoles().getOrDefault(target, guilds.defaultRole());
                int index = roles.indexOf(current);
                String next = roles.get((index + 1 + roles.size()) % roles.size());
                guild.memberRoles().put(target, next); messages.send(player, "role-assigned", "role", guild.roles().get(next).name(), "player", skull.getOwningPlayer().getName()); guilds.save(); openMembers(player);
            }
        } else if (holder instanceof PlayerPermissionsHolder h) {
            if (!guild.canManage(player.getUniqueId()) || slot < 0 || slot >= Guild.Permission.values().length) return;
            Guild.Permission permission = Guild.Permission.values()[slot];
            EnumMap<Guild.Permission, Guild.TriState> map = guild.overrides().computeIfAbsent(h.target(), key -> new EnumMap<>(Guild.Permission.class));
            if (event.isRightClick()) map.remove(permission);
            else {
                Guild.TriState current = map.getOrDefault(permission, Guild.TriState.INHERIT);
                map.put(permission, current == Guild.TriState.INHERIT ? Guild.TriState.ALLOW : current == Guild.TriState.ALLOW ? Guild.TriState.DENY : Guild.TriState.INHERIT);
            }
            guilds.save(); openPermissions(player, h.target());
        } else if (holder instanceof RolesHolder) {
            if (!guild.canManage(player.getUniqueId()) || slot < 0 || slot >= guild.roles().size()) return;
            Guild.Role role = new ArrayList<>(guild.roles().values()).get(slot);
            if (event.getClick() == ClickType.SHIFT_RIGHT && !role.id().equals(guilds.defaultRole())) {
                guild.roles().remove(role.id()); guild.memberRoles().replaceAll((uuid, id) -> id.equals(role.id()) ? guilds.defaultRole() : id); messages.send(player, "role-deleted", "role", role.name());
            } else {
                Guild.Permission permission = Guild.Permission.values()[0];
                if (!role.permissions().remove(permission)) role.permissions().add(permission);
            }
            guilds.save(); openRoles(player);
        } else if (holder instanceof AchievementsHolder h) {
            if (h.category() == null) {
                List<AchievementService.Category> list = new ArrayList<>(achievements.categories());
                int index = slot >= 19 ? slot - 12 : slot - 10;
                if (index >= 0 && index < list.size()) openAchievements(player, list.get(index).id());
            } else if (slot >= 10 && slot < 19) achievements.claim(player, h.category(), slot - 9);
            else if (slot == 49) achievements.claimAll(player, h.category());
        } else if (holder instanceof StorageHolder) {
            event.setCancelled(false);
            if (event.getClickedInventory() == event.getInventory()) {
                if (!guild.hasPermission(player.getUniqueId(), Guild.Permission.STORAGE_WITHDRAW, guilds.defaultRole())) event.setCancelled(true);
            } else if (!guild.hasPermission(player.getUniqueId(), Guild.Permission.STORAGE_DEPOSIT, guilds.defaultRole())) event.setCancelled(true);
        }
    }

    public void close(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof StorageHolder holder)) return;
        Guild guild = guilds.byTag(holder.tag());
        if (guild != null) { guild.storageData(Items.encode(event.getInventory().getContents())); guilds.save(); }
    }
}
