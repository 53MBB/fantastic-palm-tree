package pl.maghub.guilds.command;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import pl.maghub.guilds.MAGGuildsPlugin;
import pl.maghub.guilds.data.DataStore;
import pl.maghub.guilds.model.Guild;
import pl.maghub.guilds.service.*;

import java.util.*;

public final class AdminCommand implements CommandExecutor,TabCompleter{
    private final MAGGuildsPlugin plugin;private final GuildService guilds;private final DataStore dataStore;private final MessageService messages;private final RegenerationService regeneration;private final AchievementService achievements;
    public AdminCommand(MAGGuildsPlugin plugin,GuildService guilds,DataStore dataStore,MessageService messages,RegenerationService regeneration,AchievementService achievements){this.plugin=plugin;this.guilds=guilds;this.dataStore=dataStore;this.messages=messages;this.regeneration=regeneration;this.achievements=achievements;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){if(!sender.hasPermission("magguilds.admin")){messages.send(sender,"no-permission");return true;}if(args.length==0){sender.sendMessage(messages.literal("&#8B5CF6&l/ga reload, save, points, lives, delete"));return true;}switch(args[0].toLowerCase(Locale.ROOT)){case"reload"->{plugin.reloadEverything();messages.send(sender,"reload");}case"save"->{plugin.saveAll();sender.sendMessage(messages.literal("&#34D399Zapisano dane."));}case"delete"->{if(args.length<2)return true;Guild guild=guilds.byTag(args[1]);if(guild!=null)guilds.delete(guild);}case"points"->{if(args.length<3)return true;Guild guild=guilds.byTag(args[1]);if(guild!=null){guild.points(Long.parseLong(args[2]));guilds.save();}}case"lives"->{if(args.length<3)return true;Guild guild=guilds.byTag(args[1]);if(guild!=null){guild.lives(Integer.parseInt(args[2]));guilds.save();}}default->messages.send(sender,"unknown-command");}return true;}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){if(args.length==1)return List.of("reload","save","points","lives","delete");if(args.length==2)return guilds.all().stream().map(Guild::tag).toList();return List.of();}
}
