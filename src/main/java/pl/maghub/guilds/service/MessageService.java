package pl.maghub.guilds.service;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.maghub.guilds.MAGGuildsPlugin;
import pl.maghub.guilds.util.Text;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MessageService {
    private final MAGGuildsPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    public MessageService(MAGGuildsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        reload();
    }

    public void reload() {
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public String format(String key, Object... replacements) {
        String raw = yaml.getString(key, key);
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("prefix", yaml.getString("prefix", "MAGHUB »"));
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            values.put(String.valueOf(replacements[i]).replace("%", ""), String.valueOf(replacements[i + 1]));
        }

        // Najwazniejsza kolejnosc 4.0.26:
        // 1. smallcaps obejmuje tylko tekst staly i omija %placeholdery%,
        // 2. wartosci sa podstawiane dopiero po konwersji i zostaja zwykla czcionka.
        String prepared = Text.smallCapsPreservingTokens(raw);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            prepared = prepared.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return Text.color(prepared);
    }

    public String literal(String text) {
        return Text.color(Text.smallCapsPreservingTokens(text));
    }

    public void send(CommandSender sender, String key, Object... replacements) {
        sender.sendMessage(format(key, replacements));
    }
}
