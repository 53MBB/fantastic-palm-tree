package pl.maghub.guilds.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.maghub.guilds.MAGGuildsPlugin;
import pl.maghub.guilds.data.DataStore;
import pl.maghub.guilds.model.Guild;
import pl.maghub.guilds.service.AchievementService;
import pl.maghub.guilds.service.CombatService;
import pl.maghub.guilds.service.GuiService;
import pl.maghub.guilds.service.GuildService;
import pl.maghub.guilds.service.MessageService;
import pl.maghub.guilds.service.RegenerationService;
import pl.maghub.guilds.service.TeleportService;

public final class GuildListener implements Listener {
    private final MAGGuildsPlugin plugin;
    private final GuildService guilds;
    private final DataStore dataStore;
    private final MessageService messages;
    private final CombatService combat;
    private final TeleportService teleports;
    private final RegenerationService regeneration;
    private final AchievementService achievements;
    private final GuiService gui;

    public GuildListener(
            MAGGuildsPlugin plugin,
            GuildService guilds,
            DataStore dataStore,
            MessageService messages,
            CombatService combat,
            TeleportService teleports,
            RegenerationService regeneration,
            AchievementService achievements,
            GuiService gui
    ) {
        this.plugin = plugin;
        this.guilds = guilds;
        this.dataStore = dataStore;
        this.messages = messages;
        this.combat = combat;
        this.teleports = teleports;
        this.regeneration = regeneration;
        this.achievements = achievements;
        this.gui = gui;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        combat.onJoin(event.getPlayer());
        regeneration.onJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        combat.onQuit(event.getPlayer());
        teleports.cancel(event.getPlayer(), false);
        regeneration.onQuit(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        teleports.checkMove(event.getPlayer(), event.getTo());
        if (event.getFrom().getWorld() != event.getTo().getWorld()) return;
        double distance = event.getFrom().distance(event.getTo());
        if (distance >= 1.0D) {
            achievements.add(event.getPlayer().getUniqueId(), "distance", (long) Math.floor(distance));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) return;
        Guild victimGuild = guilds.byPlayer(victim.getUniqueId());
        Guild attackerGuild = guilds.byPlayer(attacker.getUniqueId());
        if (victimGuild != null && victimGuild == attackerGuild && !victimGuild.pvp()) {
            event.setCancelled(true);
            return;
        }
        combat.tag(attacker, victim);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        combat.onDeath(event.getEntity(), event.getEntity().getKiller());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Guild territory = guilds.at(event.getBlock().getLocation());
        if (territory != null && !territory.isMember(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "territory-denied", "permission", "BREAK_BLOCKS");
            return;
        }
        if (territory != null && !territory.hasPermission(event.getPlayer().getUniqueId(), Guild.Permission.BREAK_BLOCKS, guilds.defaultRole())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "territory-denied", "permission", "BREAK_BLOCKS");
            return;
        }
        if (territory != null) regeneration.record(event.getBlock(), territory);
        achievements.add(event.getPlayer().getUniqueId(), "blocks", 1);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        Guild territory = guilds.at(event.getBlock().getLocation());
        if (territory == null) return;
        if (!territory.isMember(event.getPlayer().getUniqueId())
                || !territory.hasPermission(event.getPlayer().getUniqueId(), Guild.Permission.PLACE_BLOCKS, guilds.defaultRole())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "territory-denied", "permission", "PLACE_BLOCKS");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucket(PlayerBucketEmptyEvent event) {
        Guild territory = guilds.at(event.getBlock().getLocation());
        if (territory == null) return;
        Guild.Permission permission;
        if (event.getBlock().getY() <= plugin.getConfig().getInt("territory.fluid-levels.low-max-y", -40)) {
            permission = Guild.Permission.FLUID_LOW;
        } else if (event.getBlock().getY() <= plugin.getConfig().getInt("territory.fluid-levels.middle-max-y", 50)) {
            permission = Guild.Permission.FLUID_MIDDLE;
        } else {
            permission = Guild.Permission.FLUID_HIGH;
        }
        if (!territory.isMember(event.getPlayer().getUniqueId())
                || !territory.hasPermission(event.getPlayer().getUniqueId(), permission, guilds.defaultRole())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "territory-denied", "permission", permission.name());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            achievements.add(event.getPlayer().getUniqueId(), "koxy", 1);
        } else if (event.getItem().getType() == Material.GOLDEN_APPLE) {
            achievements.add(event.getPlayer().getUniqueId(), "refy", 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPearl(PlayerInteractEvent event) {
        if (event.getItem() != null && event.getItem().getType() == Material.ENDER_PEARL) {
            achievements.add(event.getPlayer().getUniqueId(), "pearls", 1);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getInventory().getHolder() instanceof GuiService.Holder holder)) return;

        if (holder.type().equals("storage")) {
            Guild guild = guilds.byTag(holder.tag());
            if (guild == null) {
                event.setCancelled(true);
                return;
            }
            boolean topInventory = event.getRawSlot() >= 0 && event.getRawSlot() < event.getInventory().getSize();
            if (topInventory) {
                boolean withdrawing = event.getCursor() == null || event.getCursor().getType().isAir();
                Guild.Permission permission = withdrawing ? Guild.Permission.STORAGE_WITHDRAW : Guild.Permission.STORAGE_DEPOSIT;
                if (!guild.hasPermission(player.getUniqueId(), permission, guilds.defaultRole())) {
                    event.setCancelled(true);
                    messages.send(player, withdrawing ? "storage-no-withdraw" : "storage-no-deposit");
                }
            } else if (event.isShiftClick()
                    && !guild.hasPermission(player.getUniqueId(), Guild.Permission.STORAGE_DEPOSIT, guilds.defaultRole())) {
                event.setCancelled(true);
                messages.send(player, "storage-no-deposit");
            }
            return;
        }

        event.setCancelled(true);
        gui.handle(player, holder, event.getRawSlot(), event.getClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiService.Holder holder)) return;
        if (!holder.type().equals("storage")) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Guild guild = guilds.byTag(holder.tag());
        if (guild == null || !guild.hasPermission(player.getUniqueId(), Guild.Permission.STORAGE_DEPOSIT, guilds.defaultRole())) {
            event.setCancelled(true);
            messages.send(player, "storage-no-deposit");
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof GuiService.Holder holder && holder.type().equals("storage")) {
            gui.saveStorage(event.getInventory(), holder);
        }
    }
}
