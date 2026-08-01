package pl.maghub.guilds.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import pl.maghub.guilds.MAGGuildsPlugin;
import pl.maghub.guilds.data.DataStore;
import pl.maghub.guilds.model.Guild;
import pl.maghub.guilds.service.*;

import java.util.*;

public final class GuildCommand implements CommandExecutor, TabCompleter {
    private final MAGGuildsPlugin plugin; private final GuildService guilds; private final DataStore dataStore; private final MessageService messages; private final TeleportService teleports; private final GuiService gui; private final RegenerationService regeneration; private final AchievementService achievements;
    public GuildCommand(MAGGuildsPlugin plugin,GuildService guilds,DataStore dataStore,MessageService messages,TeleportService teleports,GuiService gui,RegenerationService regeneration,AchievementService achievements){this.plugin=plugin;this.guilds=guilds;this.dataStore=dataStore;this.messages=messages;this.teleports=teleports;this.gui=gui;this.regeneration=regeneration;this.achievements=achievements;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){if(!(sender instanceof Player player)){messages.send(sender,"player-only");return true;}if(args.length==0){gui.openMain(player);return true;}String sub=args[0].toLowerCase(Locale.ROOT);Guild guild=guilds.byPlayer(player.getUniqueId());switch(sub){
        case "zaloz"->{if(args.length<3)return usage(player,"/g zaloz <tag> <nazwa>");guilds.create(player,args[1],String.join(" ",Arrays.copyOfRange(args,2,args.length)));}
        case "usun"->{if(guild==null){messages.send(player,"no-guild");return true;}if(!guild.isLeader(player.getUniqueId())){messages.send(player,"leader-only");return true;}if(args.length<2||!args[1].equalsIgnoreCase("potwierdz")){messages.send(player,"delete-confirm");return true;}String tag=guild.tag();guilds.delete(guild);messages.send(player,"deleted","tag",tag);}
        case "zapros"->{if(args.length<2)return usage(player,"/g zapros <gracz>");OfflinePlayer target=Bukkit.getOfflinePlayer(args[1]);guilds.invite(player,target);}
        case "dolacz"->{if(args.length<2)return usage(player,"/g dolacz <tag>");guilds.join(player,args[1]);}
        case "opusc"->guilds.leave(player);
        case "wyrzuc"->{if(guild==null){messages.send(player,"no-guild");return true;}if(!guild.canManage(player.getUniqueId())){messages.send(player,"manage-only");return true;}if(args.length<2)return usage(player,"/g wyrzuc <gracz>");OfflinePlayer target=Bukkit.getOfflinePlayer(args[1]);if(target.getUniqueId().equals(guild.leader())){messages.send(player,"leader-only");return true;}guilds.removeMember(guild,target.getUniqueId());messages.send(player,"kicked","player",target.getName());}
        case "lider"->{if(guild==null){messages.send(player,"no-guild");return true;}if(!guild.isLeader(player.getUniqueId())){messages.send(player,"leader-only");return true;}if(args.length<2)return usage(player,"/g lider <gracz>");OfflinePlayer target=Bukkit.getOfflinePlayer(args[1]);if(!guild.isMember(target.getUniqueId())){messages.send(player,"player-not-found","player",args[1]);return true;}guild.leader(target.getUniqueId());guilds.save();messages.send(player,"leader-changed","player",target.getName());}
        case "zastepca"->{if(guild==null){messages.send(player,"no-guild");return true;}if(!guild.isLeader(player.getUniqueId())){messages.send(player,"leader-only");return true;}if(args.length<2)return usage(player,"/g zastepca <gracz>");OfflinePlayer target=Bukkit.getOfflinePlayer(args[1]);if(!guild.isMember(target.getUniqueId())){messages.send(player,"player-not-found","player",args[1]);return true;}guild.deputy(target.getUniqueId());guilds.save();messages.send(player,"deputy-changed","player",target.getName());}
        case "baza"->{if(guild==null||guild.base()==null){messages.send(player,"no-guild");return true;}teleports.teleport(player,guild.base(),"bazy gildii");}
        case "ustawbaze"->{if(guild==null){messages.send(player,"no-guild");return true;}if(!guild.hasPermission(player.getUniqueId(),Guild.Permission.SET_HOME,guilds.defaultRole())){messages.send(player,"no-permission");return true;}guild.base(player.getLocation());guilds.save();messages.send(player,"base-set");}
        case "dom"->{if(guild==null||guild.home()==null){messages.send(player,"no-guild");return true;}teleports.teleport(player,guild.home(),"domu gildii");}
        case "ustawdom"->{if(guild==null){messages.send(player,"no-guild");return true;}if(!guild.hasPermission(player.getUniqueId(),Guild.Permission.SET_HOME,guilds.defaultRole())){messages.send(player,"no-permission");return true;}guild.home(player.getLocation());guilds.save();messages.send(player,"home-set");}
        case "pvp"->{if(guild==null){messages.send(player,"no-guild");return true;}if(!guild.hasPermission(player.getUniqueId(),Guild.Permission.TOGGLE_PVP,guilds.defaultRole())){messages.send(player,"no-permission");return true;}guild.pvp(!guild.pvp());guilds.save();messages.send(player,guild.pvp()?"pvp-enabled":"pvp-disabled");}
        case "uprawnienia"->gui.openMembers(player);
        case "role"->gui.openRoles(player,null);
        case "magazyn"->gui.openStorage(player);
        case "regeneracja"->{if(guild==null){messages.send(player,"no-guild");return true;}if(!guild.hasPermission(player.getUniqueId(),Guild.Permission.START_REGEN,guilds.defaultRole())){messages.send(player,"no-permission");return true;}regeneration.start(player,guild);}
        case "osiagniecia"->gui.openAchievements(player);
        case "wojna"->{if(guild==null){messages.send(player,"no-guild");return true;}if(!guild.hasPermission(player.getUniqueId(),Guild.Permission.DECLARE_WAR,guilds.defaultRole())){messages.send(player,"no-permission");return true;}if(args.length<2)return usage(player,"/g wojna <tag>");Guild defender=guilds.byTag(args[1]);if(defender==null){messages.send(player,"guild-not-found","tag",args[1]);return true;}if(guilds.declareWar(guild,defender))messages.send(player,"war-started","attacker",guild.tag(),"defender",defender.tag(),"time",plugin.getConfig().getLong("wars.duration-minutes",120)+"m");else messages.send(player,"war-active");}
        case "info"->{Guild info=args.length>=2?guilds.byTag(args[1]):guild;if(info==null){messages.send(player,"guild-not-found","tag",args.length>=2?args[1]:"?");return true;}player.sendMessage(messages.literal("&#8B5CF6&l["+info.tag()+"] &#F8FAFC"+info.name()));player.sendMessage(messages.literal("&#C4B5FDLider: &#F8FAFC"+Bukkit.getOfflinePlayer(info.leader()).getName()));player.sendMessage(messages.literal("&#C4B5FDCzlonkowie: &#F8FAFC"+info.members().size()+"/"+info.memberLimit()));player.sendMessage(messages.literal("&#C4B5FDPunkty: &#F8FAFC"+info.points()+" &#4C1D95• &#C4B5FDZycia: &#F8FAFC"+info.lives()));}
        case "itemy"->{player.sendMessage(messages.literal("&#8B5CF6&lPrzedmioty na gildie"));var sec=plugin.getConfig().getConfigurationSection("creation.items");if(sec!=null)for(String key:sec.getKeys(false))player.sendMessage(messages.literal("&#C4B5FD"+key+": &#F8FAFC"+sec.getInt(key)));}
        default->messages.send(player,"unknown-command");}return true;}
    private boolean usage(Player player,String usage){player.sendMessage(messages.literal("&#FB7185Uzycie: &#F8FAFC"+usage));return true;}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){if(args.length==1)return filter(List.of("zaloz","usun","zapros","dolacz","opusc","wyrzuc","lider","zastepca","baza","ustawbaze","dom","ustawdom","pvp","uprawnienia","role","magazyn","regeneracja","osiagniecia","wojna","info","itemy"),args[0]);if(args.length==2&&List.of("zapros","wyrzuc","lider","zastepca").contains(args[0].toLowerCase(Locale.ROOT)))return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(),args[1]);if(args.length==2&&List.of("dolacz","wojna","info").contains(args[0].toLowerCase(Locale.ROOT)))return filter(guilds.all().stream().map(Guild::tag).toList(),args[1]);return List.of();}
    private List<String> filter(Collection<String> values,String input){String lower=input.toLowerCase(Locale.ROOT);return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();}
}
