package pl.maghub.guilds.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import pl.maghub.guilds.MAGGuildsPlugin;
import pl.maghub.guilds.data.DataStore;
import pl.maghub.guilds.model.Guild;
import pl.maghub.guilds.service.*;

import java.util.Locale;

public final class GuildListener implements Listener{
    private final MAGGuildsPlugin plugin;private final GuildService guilds;private final DataStore dataStore;private final MessageService messages;private final CombatService combat;private final TeleportService teleports;private final RegenerationService regeneration;private final AchievementService achievements;private final GuiService gui;
    public GuildListener(MAGGuildsPlugin plugin,GuildService guilds,DataStore dataStore,MessageService messages,CombatService combat,TeleportService teleports,RegenerationService regeneration,AchievementService achievements,GuiService gui){this.plugin=plugin;this.guilds=guilds;this.dataStore=dataStore;this.messages=messages;this.combat=combat;this.teleports=teleports;this.regeneration=regeneration;this.achievements=achievements;this.gui=gui;}
    @EventHandler(ignoreCancelled=true,priority=EventPriority.MONITOR) public void onJoin(PlayerJoinEvent e){combat.onJoin(e.getPlayer());regeneration.onJoin(e.getPlayer());}
    @EventHandler public void onQuit(PlayerQuitEvent e){combat.onQuit(e.getPlayer());teleports.cancel(e.getPlayer(),false);regeneration.onQuit(e.getPlayer());}
    @EventHandler(ignoreCancelled=true) public void onMove(PlayerMoveEvent e){teleports.checkMove(e.getPlayer(),e.getTo());if(e.getFrom().getWorld()==e.getTo().getWorld())achievements.add(e.getPlayer().getUniqueId(),"distance",(long)Math.floor(e.getFrom().distance(e.getTo())));}
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void onDamage(EntityDamageByEntityEvent e){if(!(e.getEntity()instanceof Player victim)||!(e.getDamager()instanceof Player attacker))return;Guild vg=guilds.byPlayer(victim.getUniqueId()),ag=guilds.byPlayer(attacker.getUniqueId());if(vg!=null&&vg==ag&&!vg.pvp()){e.setCancelled(true);return;}combat.tag(attacker,victim);}
    @EventHandler(priority=EventPriority.MONITOR) public void onDeath(PlayerDeathEvent e){combat.onDeath(e.getEntity(),e.getEntity().getKiller());}
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void onBreak(BlockBreakEvent e){Guild territory=guilds.at(e.getBlock().getLocation());if(territory!=null&&!territory.isMember(e.getPlayer().getUniqueId())){e.setCancelled(true);messages.send(e.getPlayer(),"territory-denied","permission","BREAK_BLOCKS");return;}if(territory!=null&&!territory.hasPermission(e.getPlayer().getUniqueId(),Guild.Permission.BREAK_BLOCKS,guilds.defaultRole())){e.setCancelled(true);messages.send(e.getPlayer(),"territory-denied","permission","BREAK_BLOCKS");return;}if(territory!=null)regeneration.record(e.getBlock(),territory);achievements.add(e.getPlayer().getUniqueId(),"blocks",1);}
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void onPlace(BlockPlaceEvent e){Guild territory=guilds.at(e.getBlock().getLocation());if(territory!=null&&!territory.isMember(e.getPlayer().getUniqueId())){e.setCancelled(true);messages.send(e.getPlayer(),"territory-denied","permission","PLACE_BLOCKS");return;}if(territory!=null&&!territory.hasPermission(e.getPlayer().getUniqueId(),Guild.Permission.PLACE_BLOCKS,guilds.defaultRole())){e.setCancelled(true);messages.send(e.getPlayer(),"territory-denied","permission","PLACE_BLOCKS");}}
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void onBucket(PlayerBucketEmptyEvent e){Guild territory=guilds.at(e.getBlock().getLocation());if(territory==null)return;Guild.Permission permission=e.getBlock().getY()<=plugin.getConfig().getInt("territory.fluid-levels.low-max-y",-40)?Guild.Permission.FLUID_LOW:e.getBlock().getY()<=plugin.getConfig().getInt("territory.fluid-levels.middle-max-y",50)?Guild.Permission.FLUID_MIDDLE:Guild.Permission.FLUID_HIGH;if(!territory.isMember(e.getPlayer().getUniqueId())||!territory.hasPermission(e.getPlayer().getUniqueId(),permission,guilds.defaultRole())){e.setCancelled(true);messages.send(e.getPlayer(),"territory-denied","permission",permission.name());}}
    @EventHandler(ignoreCancelled=true) public void onConsume(PlayerItemConsumeEvent e){if(e.getItem().getType()==Material.ENCHANTED_GOLDEN_APPLE)achievements.add(e.getPlayer().getUniqueId(),"koxy",1);else if(e.getItem().getType()==Material.GOLDEN_APPLE)achievements.add(e.getPlayer().getUniqueId(),"refy",1);}
    @EventHandler(ignoreCancelled=true) public void onPearl(PlayerInteractEvent e){if(e.getItem()!=null&&e.getItem().getType()==Material.ENDER_PEARL)achievements.add(e.getPlayer().getUniqueId(),"pearls",1);}
    @EventHandler public void onClick(InventoryClickEvent e){if(!(e.getWhoClicked()instanceof Player player)||!(e.getInventory().getHolder()instanceof GuiService.Holder holder))return;e.setCancelled(true);gui.handle(player,holder,e.getRawSlot(),e.getClick());}
    @EventHandler public void onDrag(InventoryDragEvent e){if(e.getInventory().getHolder()instanceof GuiService.Holder holder&&!holder.type().equals("storage"))e.setCancelled(true);}
    @EventHandler public void onClose(InventoryCloseEvent e){if(e.getInventory().getHolder()instanceof GuiService.Holder holder&&holder.type().equals("storage"))gui.saveStorage(e.getInventory(),holder);}
}
