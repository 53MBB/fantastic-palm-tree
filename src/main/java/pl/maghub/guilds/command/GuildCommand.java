package pl.maghub.guilds.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.maghub.guilds.MAGGuildsPlugin;
import pl.maghub.guilds.data.DataStore;
import pl.maghub.guilds.model.Guild;
import pl.maghub.guilds.service.*;
import pl.maghub.guilds.util.Items;
import pl.maghub.guilds.util.Text;

import java.util.*;

public final class GuildCommand implements CommandExecutor, TabCompleter {
    private final MAGGuildsPlugin plugin;
    private final GuildService guilds;
    private final DataStore store;
    private final MessageService messages;
    private final TeleportService teleports;
    private final GuiService gui;
    private final RegenerationService regeneration;
    private final AchievementService achievements;

    public GuildCommand(MAGGuildsPlugin plugin, GuildService guilds, DataStore store, MessageService messages, TeleportService teleports, GuiService gui, RegenerationService regeneration, AchievementService achievements) {
        this.plugin = plugin; this.guilds = guilds; this.store = store; this.messages = messages; this.teleports = teleports; this.gui = gui; this.regeneration = regeneration; this.achievements = achievements;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { messages.send(sender, "player-only"); return true; }
        if (args.length == 0) { gui.openMain(player); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "zaloz", "create" -> {
                if (!player.hasPermission("magguilds.create")) { messages.send(player, "no-permission"); return true; }
                if (args.length < 3) { player.sendMessage(Text.color("&#D946EFUzycie: &#F8FAFC/g zaloz <tag> <nazwa>")); return true; }
                guilds.create(player, args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
            }
            case "usun", "delete" -> {
                Guild guild = guilds.byPlayer(player.getUniqueId());
                if (guild == null) messages.send(player, "no-guild");
                else if (!guild.isLeader(player.getUniqueId())) messages.send(player, "leader-only");
                else if (args.length < 2 || !args[1].equalsIgnoreCase("potwierdz")) messages.send(player, "delete-confirm");
                else { guilds.delete(guild); messages.send(player, "deleted", "tag", guild.tag()); }
            }
            case "zapros", "invite" -> {
                if (args.length < 2) return usage(player, "/g zapros <gracz>");
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) messages.send(player, "player-not-found", "player", args[1]); else guilds.invite(player, target);
            }
            case "dolacz", "join" -> { if (args.length < 2) return usage(player, "/g dolacz <tag>"); guilds.join(player, args[1]); }
            case "opusc", "leave" -> guilds.leave(player);
            case "wyrzuc", "kick" -> { if (args.length < 2) return usage(player, "/g wyrzuc <gracz>"); guilds.kick(player, Bukkit.getOfflinePlayer(args[1])); }
            case "lider", "leader" -> { if (args.length < 2) return usage(player, "/g lider <gracz>"); guilds.setLeader(player, Bukkit.getOfflinePlayer(args[1])); }
            case "zastepca", "deputy" -> { if (args.length < 2) return usage(player, "/g zastepca <gracz>"); guilds.setDeputy(player, Bukkit.getOfflinePlayer(args[1])); }
            case "pvp" -> guilds.togglePvp(player);
            case "ustawbaze", "setbase" -> guilds.setBase(player, false);
            case "ustawdom", "sethome" -> guilds.setBase(player, true);
            case "baza", "base" -> teleport(player, false);
            case "dom", "home" -> teleport(player, true);
            case "panel" -> gui.openMain(player);
            case "uprawnienia", "permissions" -> {
                if (args.length > 1) {
                    Guild guild = guilds.byPlayer(player.getUniqueId());
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    if (guild == null || !guild.isMember(target.getUniqueId())) messages.send(player, "player-not-found", "player", args[1]);
                    else gui.openPermissions(player, target.getUniqueId());
                } else gui.openMembers(player);
            }
            case "role", "roles" -> handleRoles(player, args);
            case "osiagniecia", "achievements" -> gui.openAchievements(player, null);
            case "regeneracja", "regen" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("start")) regeneration.start(player);
                else player.sendMessage(Text.color("&#D946EFUzycie: &#F8FAFC/g regeneracja start"));
            }
            case "magazyn", "storage" -> gui.openStorage(player);
            case "sojusz", "ally" -> { if (args.length < 2) return usage(player, "/g sojusz <tag>"); guilds.ally(player, args[1]); }
            case "wojna", "war" -> { if (args.length < 2) return usage(player, "/g wojna <tag>"); guilds.declareWar(player, args[1]); }
            case "info" -> info(player, args.length > 1 ? guilds.byTag(args[1]) : guilds.byPlayer(player.getUniqueId()));
            case "itemy", "items" -> showItems(player);
            default -> messages.send(player, "unknown-command");
        }
        return true;
    }

    private void teleport(Player player, boolean home) {
        Guild guild = guilds.byPlayer(player.getUniqueId());
        if (guild == null) { messages.send(player, "no-guild"); return; }
        org.bukkit.Location target = home ? guild.home() : guild.base();
        if (target == null) { messages.send(player, "no-guild"); return; }
        teleports.start(player, target, home ? "domu gildii" : "bazy gildii");
    }

    private void handleRoles(Player player, String[] args) {
        Guild guild = guilds.byPlayer(player.getUniqueId());
        if (guild == null) { messages.send(player, "no-guild"); return; }
        if (args.length == 1) { gui.openRoles(player); return; }
        if (!guild.canManage(player.getUniqueId())) { messages.send(player, "manage-only"); return; }
        if (args[1].equalsIgnoreCase("utworz") && args.length >= 3) {
            String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
            String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
            if (guild.roles().containsKey(id)) { messages.send(player, "role-exists", "role", name); return; }
            if (guild.roles().size() >= plugin.getConfig().getInt("roles.maximum", 12)) { messages.send(player, "role-limit", "max", plugin.getConfig().getInt("roles.maximum", 12)); return; }
            guild.roles().put(id, new Guild.Role(id, name, EnumSet.noneOf(Guild.Permission.class)));
            messages.send(player, "role-created", "role", name); guilds.save();
        } else if (args[1].equalsIgnoreCase("usun") && args.length >= 3) {
            String id = args[2].toLowerCase(Locale.ROOT);
            Guild.Role removed = guild.roles().remove(id);
            if (removed == null) messages.send(player, "role-not-found", "role", args[2]);
            else { guild.memberRoles().replaceAll((uuid, role) -> role.equals(id) ? guilds.defaultRole() : role); messages.send(player, "role-deleted", "role", removed.name()); guilds.save(); }
        } else if (args[1].equalsIgnoreCase("nadaj") && args.length >= 4) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            String id = args[3].toLowerCase(Locale.ROOT);
            Guild.Role role = guild.roles().get(id);
            if (!guild.isMember(target.getUniqueId())) messages.send(player, "player-not-found", "player", args[2]);
            else if (role == null) messages.send(player, "role-not-found", "role", args[3]);
            else { guild.memberRoles().put(target.getUniqueId(), id); messages.send(player, "role-assigned", "role", role.name(), "player", target.getName()); guilds.save(); }
        } else usage(player, "/g role <utworz|usun|nadaj>");
    }

    private void info(Player player, Guild guild) {
        if (guild == null) { messages.send(player, "guild-not-found", "tag", "?"); return; }
        OfflinePlayer leader = Bukkit.getOfflinePlayer(guild.leader());
        player.sendMessage(Text.color("&#8B5CF6&lMAG&#D946EF&lHUB &#6D5B7B» &#F8FAFC[" + guild.tag() + "] " + guild.name()));
        player.sendMessage(Text.color("&#C4B5FDLider: &#F8FAFC" + leader.getName()));
        player.sendMessage(Text.color("&#C4B5FDCzlonkowie: &#F8FAFC" + guild.members().size() + "/" + guild.memberLimit()));
        player.sendMessage(Text.color("&#C4B5FDPunkty: &#F8FAFC" + guild.points() + " &#4C1D95• &#C4B5FDZycia: &#F8FAFC" + guild.lives()));
        player.sendMessage(Text.color("&#C4B5FDTeren: &#F8FAFC" + guild.radius() + " blokow"));
    }

    private void showItems(Player player) {
        player.sendMessage(Text.color("&#8B5CF6&lMAG&#D946EF&lHUB &#6D5B7B» &#D946EFPʀᴢᴇᴅᴍɪᴏᴛʏ ɴᴀ ɢɪʟᴅɪᴇ:"));
        org.bukkit.configuration.ConfigurationSection section = plugin.getConfig().getConfigurationSection("creation.items");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key); if (material == null) continue;
            int need = section.getInt(key), have = Items.count(player.getInventory(), material);
            player.sendMessage(Text.color("&#C4B5FD" + Text.plainMaterial(key) + ": &#F8FAFC" + have + "/" + need));
        }
    }

    private boolean usage(Player player, String usage) { player.sendMessage(Text.color("&#D946EFUzycie: &#F8FAFC" + usage)); return true; }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("zaloz", "usun", "zapros", "dolacz", "opusc", "wyrzuc", "lider", "zastepca", "baza", "ustawbaze", "dom", "ustawdom", "pvp", "panel", "uprawnienia", "role", "osiagniecia", "regeneracja", "magazyn", "sojusz", "wojna", "info", "itemy"), args[0]);
        if (args.length == 2 && List.of("zapros", "wyrzuc", "lider", "zastepca", "uprawnienia").contains(args[0].toLowerCase(Locale.ROOT))) return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        if (args.length == 2 && List.of("dolacz", "sojusz", "wojna", "info").contains(args[0].toLowerCase(Locale.ROOT))) return filter(guilds.all().stream().map(Guild::tag).toList(), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("regeneracja")) return filter(List.of("start"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("role")) return filter(List.of("utworz", "usun", "nadaj"), args[1]);
        return List.of();
    }

    private List<String> filter(Collection<String> values, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
    }
}
