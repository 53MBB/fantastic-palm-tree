package pl.maghub.guilds.service;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import pl.maghub.guilds.MAGGuildsPlugin;

import java.util.HashMap;
import java.util.UUID;

public final class TeleportService {
    private final MAGGuildsPlugin plugin;
    private final MessageService messages;
    private final HashMap<UUID, BukkitTask> tasks = new HashMap<>();
    private final HashMap<UUID, Location> starts = new HashMap<>();

    public TeleportService(MAGGuildsPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void start(Player player, Location target, String targetName) {
        cancel(player, false);
        int seconds = Math.max(0, plugin.getConfig().getInt("settings.teleport-seconds", 5));
        if (seconds == 0) { player.teleport(target); messages.send(player, "teleported", "target", targetName); return; }
        starts.put(player.getUniqueId(), player.getLocation());
        messages.send(player, "teleport-start", "seconds", seconds);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            tasks.remove(player.getUniqueId()); starts.remove(player.getUniqueId());
            if (player.isOnline()) { player.teleport(target); messages.send(player, "teleported", "target", targetName); }
        }, seconds * 20L);
        tasks.put(player.getUniqueId(), task);
    }

    public void checkMovement(Player player, Location to) {
        Location start = starts.get(player.getUniqueId());
        if (start == null || to == null) return;
        if (start.getWorld() != to.getWorld() || start.distanceSquared(to) > 0.04) cancel(player, true);
    }

    public void cancel(Player player, boolean message) {
        BukkitTask task = tasks.remove(player.getUniqueId());
        starts.remove(player.getUniqueId());
        if (task != null) { task.cancel(); if (message) messages.send(player, "teleport-cancel"); }
    }

    public void shutdown() {
        for (BukkitTask task : tasks.values()) task.cancel();
        tasks.clear(); starts.clear();
    }
}
