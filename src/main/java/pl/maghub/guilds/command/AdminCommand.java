package pl.maghub.guilds.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.maghub.guilds.MAGGuildsPlugin;
import pl.maghub.guilds.data.DataStore;
import pl.maghub.guilds.model.Guild;
import pl.maghub.guilds.service.*;

import java.util.List;
import java.util.Locale;

public final class AdminCommand implements CommandExecutor, TabCompleter {
    private final MAGGuildsPlugin plugin;
    private final GuildService guilds;
    private final DataStore store;
    private final MessageService messages;
    private final RegenerationService regeneration;
    private final AchievementService achievements;

    public AdminCommand(MAGGuildsPlugin plugin, GuildService guilds, DataStore store, MessageService messages, RegenerationService regeneration, AchievementService achievements) {
        this.plugin = plugin; this.guilds = guilds; this.store = store; this.messages = messages; this.regeneration = regeneration; this.achievements = achievements;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("magguilds.admin")) { messages.send(sender, "no-permission"); return true; }
        if (args.length == 0) { sender.sendMessage("/ga reload | points <gracz> <set|add|remove> <ilosc> | guildpoints <tag> <set|add|remove> <ilosc> | lives <tag> <ilosc> | regen <tag>"); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> { plugin.reloadEverything(); messages.send(sender, "reload"); }
            case "points" -> {
                if (args.length < 4) return true;
                org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                DataStore.PlayerProfile profile = store.profile(target.getUniqueId(), target.getName());
                long value; try { value = Long.parseLong(args[3]); } catch (NumberFormatException e) { messages.send(sender, "invalid-number"); return true; }
                profile.points(apply(profile.points(), args[2], value)); plugin.saveAll(); sender.sendMessage("Punkty " + args[1] + ": " + profile.points());
            }
            case "guildpoints" -> {
                if (args.length < 4) return true;
                Guild guild = guilds.byTag(args[1]); if (guild == null) { messages.send(sender, "guild-not-found", "tag", args[1]); return true; }
                long value; try { value = Long.parseLong(args[3]); } catch (NumberFormatException e) { messages.send(sender, "invalid-number"); return true; }
                guild.points(apply(guild.points(), args[2], value)); guilds.save(); sender.sendMessage("Punkty gildii " + guild.tag() + ": " + guild.points());
            }
            case "lives" -> {
                if (args.length < 3) return true;
                Guild guild = guilds.byTag(args[1]); if (guild == null) return true;
                try { guild.lives(Integer.parseInt(args[2])); guilds.save(); } catch (NumberFormatException e) { messages.send(sender, "invalid-number"); }
            }
            case "regen" -> {
                if (!(sender instanceof Player player) || args.length < 2) return true;
                Guild guild = guilds.byTag(args[1]); if (guild == null) return true;
                Guild own = guilds.byPlayer(player.getUniqueId());
                if (own == guild) regeneration.start(player); else sender.sendMessage("Administrator musi nalezec do tej gildii albo uzyc koszt bypass po dolaczeniu.");
            }
        }
        return true;
    }

    private long apply(long current, String mode, long value) {
        return switch (mode.toLowerCase(Locale.ROOT)) { case "add" -> current + value; case "remove" -> current - value; default -> value; };
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("reload", "points", "guildpoints", "lives", "regen");
        return List.of();
    }
}
