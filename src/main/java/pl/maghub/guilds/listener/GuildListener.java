package pl.maghub.guilds.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import pl.maghub.guilds.MAGGuildsPlugin;
import pl.maghub.guilds.data.DataStore;
import pl.maghub.guilds.model.Guild;
import pl.maghub.guilds.service.*;

public final class GuildListener implements Listener {
    private final MAGGuildsPlugin plugin;
    private final GuildService guilds;
    private final DataStore store;
    private final MessageService messages;
    private final CombatService combat;
    private final TeleportService teleports;
    private final RegenerationService regeneration;
    private final AchievementService achievements;
    private final GuiService gui;

    public GuildListener(MAGGuildsPlugin plugin, GuildService guilds, DataStore store, MessageService messages, CombatService combat, TeleportService teleports, RegenerationService regeneration, AchievementService achievements, GuiService gui) {
        this.plugin = plugin; this.guilds = guilds; this.store = store; this.messages = messages; this.combat = combat; this.teleports = teleports; this.regeneration = regeneration; this.achievements = achievements; this.gui = gui;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = event.getDamager() instanceof Player player ? player : null;
        if (attacker == null) return;
        Guild first = guilds.byPlayer(attacker.getUniqueId()), second = guilds.byPlayer(victim.getUniqueId());
        if (first != null && first == second && !first.pvp()) { event.setCancelled(true); return; }
        combat.tag(attacker, victim);
    }

    @EventHandler
    public void death(PlayerDeathEvent event) { combat.handleDeath(event.getEntity(), event.getEntity().getKiller()); }

    @EventHandler
    public void quit(PlayerQuitEvent event) { combat.handleLogout(event.getPlayer()); teleports.cancel(event.getPlayer(), false); }

    @EventHandler
    public void join(PlayerJoinEvent event) { combat.handleJoin(event.getPlayer()); }

    @EventHandler(ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        teleports.checkMovement(event.getPlayer(), event.getTo());
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            Guild guild = guilds.byPlayer(event.getPlayer().getUniqueId()); if (guild != null) achievements.add(guild, "distance", 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) {
        Guild owner = guilds.at(event.getBlock().getLocation());
        if (owner == null) return;
        Player player = event.getPlayer();
        Guild playerGuild = guilds.byPlayer(player.getUniqueId());
        Location center = owner.center();
        if (center != null && center.getWorld() == event.getBlock().getWorld() && center.getBlockX() == event.getBlock().getX() && center.getBlockY() == event.getBlock().getY() && center.getBlockZ() == event.getBlock().getZ()) { event.setCancelled(true); messages.send(player, "heart-protected"); return; }
        boolean member = owner == playerGuild;
        if (member && !owner.hasPermission(player.getUniqueId(), Guild.Permission.BREAK_BLOCKS, guilds.defaultRole())) { event.setCancelled(true); messages.send(player, "territory-denied", "permission", "BREAK_BLOCKS"); return; }
        if (!member && !guilds.atWar(playerGuild, owner)) { event.setCancelled(true); messages.send(player, "territory-denied", "permission", "WOJNA"); return; }
        regeneration.record(event.getBlock(), owner);
        if (playerGuild != null) achievements.add(playerGuild, "blocks", 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void placeBlock(BlockPlaceEvent event) {
        Guild owner = guilds.at(event.getBlock().getLocation());
        if (owner == null) return;
        Guild playerGuild = guilds.byPlayer(event.getPlayer().getUniqueId());
        if (owner != playerGuild || !owner.hasPermission(event.getPlayer().getUniqueId(), Guild.Permission.PLACE_BLOCKS, guilds.defaultRole())) { event.setCancelled(true); messages.send(event.getPlayer(), "territory-denied", "permission", "PLACE_BLOCKS"); }
    }

    @EventHandler(ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent event) {
        Guild guild = guilds.byPlayer(event.getPlayer().getUniqueId()); if (guild == null) return;
        if (event.getItem().getType() == Material.ENCHANTED_GOLDEN_APPLE) achievements.add(guild, "koxy", 1);
        else if (event.getItem().getType() == Material.GOLDEN_APPLE) achievements.add(guild, "refy", 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (event.getItem() != null && event.getItem().getType() == Material.ENDER_PEARL) {
            Guild guild = guilds.byPlayer(event.getPlayer().getUniqueId()); if (guild != null) achievements.add(guild, "pearls", 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void explode(EntityExplodeEvent event) {
        Entity source = event.getEntity();
        event.blockList().removeIf(block -> {
            Guild owner = guilds.at(block.getLocation());
            return owner != null && !plugin.getConfig().getBoolean("territory.explosion-requires-war", true);
        });
    }

    @EventHandler public void click(InventoryClickEvent event) { gui.click(event); }
    @EventHandler public void close(InventoryCloseEvent event) { gui.close(event); }
}
