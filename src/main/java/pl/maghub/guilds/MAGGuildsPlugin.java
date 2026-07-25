package pl.maghub.guilds;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import pl.maghub.guilds.api.MAGGuildsApi;
import pl.maghub.guilds.command.AdminCommand;
import pl.maghub.guilds.command.GuildCommand;
import pl.maghub.guilds.data.DataStore;
import pl.maghub.guilds.hook.MAGGuildsExpansion;
import pl.maghub.guilds.listener.GuildListener;
import pl.maghub.guilds.service.*;

public final class MAGGuildsPlugin extends JavaPlugin {
    private DataStore dataStore;
    private MessageService messages;
    private GuildService guilds;
    private AchievementService achievements;
    private RegenerationService regeneration;
    private CombatService combat;
    private TeleportService teleports;
    private GuiService gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getDataFolder().exists()) getDataFolder().mkdirs();

        messages = new MessageService(this);
        dataStore = new DataStore(this);
        dataStore.loadProfiles();
        guilds = new GuildService(this, dataStore, messages);
        achievements = new AchievementService(this, guilds, dataStore, messages);
        regeneration = new RegenerationService(this, guilds, messages);
        combat = new CombatService(this, guilds, dataStore, messages, achievements);
        teleports = new TeleportService(this, messages);
        gui = new GuiService(this, guilds, dataStore, messages, regeneration, achievements);

        GuildCommand guildCommand = new GuildCommand(this, guilds, dataStore, messages, teleports, gui, regeneration, achievements);
        PluginCommand g = getCommand("g");
        if (g != null) {
            g.setExecutor(guildCommand);
            g.setTabCompleter(guildCommand);
        }
        AdminCommand admin = new AdminCommand(this, guilds, dataStore, messages, regeneration, achievements);
        PluginCommand ga = getCommand("ga");
        if (ga != null) {
            ga.setExecutor(admin);
            ga.setTabCompleter(admin);
        }

        Bukkit.getPluginManager().registerEvents(new GuildListener(this, guilds, dataStore, messages, combat, teleports, regeneration, achievements, gui), this);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            combat.tick();
            regeneration.tick();
            guilds.tickWars();
        }, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, achievements::tickMinute, 1200L, 1200L);
        long regenSave = Math.max(5, getConfig().getLong("regeneration.autosave-seconds", 10)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, regeneration::flushDirty, regenSave, regenSave);
        long autosave = Math.max(30, getConfig().getLong("settings.autosave-seconds", 300)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, this::saveAll, autosave, autosave);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) new MAGGuildsExpansion(this, guilds, combat, dataStore).register();
        MAGGuildsApi.initialize(this, guilds, combat, achievements);
        getLogger().info("MAGGuilds 4.0.26 uruchomiony. Java 17, YAML, Paper/Spigot 1.18.2-1.21.x.");
    }

    @Override
    public void onDisable() {
        if (teleports != null) teleports.shutdown();
        if (combat != null) combat.shutdown();
        if (regeneration != null) regeneration.shutdown();
        saveAll();
        MAGGuildsApi.shutdown();
    }

    public void reloadEverything() {
        reloadConfig();
        messages.reload();
        achievements.reload();
    }

    public void saveAll() {
        if (guilds != null) guilds.save();
        if (dataStore != null) dataStore.saveProfiles();
        if (regeneration != null) regeneration.flushDirty();
    }

    public MessageService messages() { return messages; }
    public GuildService guilds() { return guilds; }
    public AchievementService achievements() { return achievements; }
    public RegenerationService regeneration() { return regeneration; }
    public CombatService combat() { return combat; }
    public GuiService gui() { return gui; }
}
