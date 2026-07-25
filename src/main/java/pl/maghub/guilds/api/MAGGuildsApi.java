package pl.maghub.guilds.api;

import pl.maghub.guilds.MAGGuildsPlugin;
import pl.maghub.guilds.model.Guild;
import pl.maghub.guilds.service.AchievementService;
import pl.maghub.guilds.service.CombatService;
import pl.maghub.guilds.service.GuildService;

import java.util.Optional;
import java.util.UUID;

public final class MAGGuildsApi{
    private static MAGGuildsPlugin plugin;private static GuildService guilds;private static CombatService combat;private static AchievementService achievements;private MAGGuildsApi(){}
    public static void initialize(MAGGuildsPlugin p,GuildService g,CombatService c,AchievementService a){plugin=p;guilds=g;combat=c;achievements=a;}public static void shutdown(){plugin=null;guilds=null;combat=null;achievements=null;}
    public static boolean available(){return plugin!=null;}public static Optional<Guild> guild(UUID uuid){return Optional.ofNullable(guilds==null?null:guilds.byPlayer(uuid));}public static boolean inCombat(UUID uuid){return combat!=null&&combat.inCombat(uuid);}public static void addAchievement(UUID uuid,String category,long amount){if(achievements!=null)achievements.add(uuid,category,amount);}
}
