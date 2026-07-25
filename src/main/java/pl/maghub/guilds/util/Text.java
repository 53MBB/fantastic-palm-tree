package pl.maghub.guilds.util;

import net.md_5.bungee.api.ChatColor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text {
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private Text() {
    }

    public static String color(String input) {
        if (input == null) return "";
        Matcher matcher = HEX.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(ChatColor.of("#" + matcher.group(1)).toString()));
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static String smallCapsPreservingTokens(String input) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '%') {
                int end = input.indexOf('%', i + 1);
                if (end > i) {
                    out.append(input, i, end + 1);
                    i = end;
                    continue;
                }
            }
            if (c == '&') {
                if (i + 7 < input.length() && input.charAt(i + 1) == '#') {
                    out.append(input, i, i + 8);
                    i += 7;
                    continue;
                }
                if (i + 1 < input.length()) {
                    out.append(c).append(input.charAt(++i));
                    continue;
                }
            }
            if (c == '§' && i + 1 < input.length()) {
                out.append(c).append(input.charAt(++i));
                continue;
            }
            out.append(small(c));
        }
        return out.toString();
    }

    private static String small(char c) {
        boolean upper = Character.isUpperCase(c);
        char lower = Character.toLowerCase(c);
        String mapped = switch (lower) {
            case 'a' -> "ᴀ"; case 'b' -> "ʙ"; case 'c' -> "ᴄ"; case 'd' -> "ᴅ";
            case 'e' -> "ᴇ"; case 'f' -> "ꜰ"; case 'g' -> "ɢ"; case 'h' -> "ʜ";
            case 'i' -> "ɪ"; case 'j' -> "ᴊ"; case 'k' -> "ᴋ"; case 'l' -> "ʟ";
            case 'm' -> "ᴍ"; case 'n' -> "ɴ"; case 'o' -> "ᴏ"; case 'p' -> "ᴘ";
            case 'q' -> "q"; case 'r' -> "ʀ"; case 's' -> "ꜱ"; case 't' -> "ᴛ";
            case 'u' -> "ᴜ"; case 'v' -> "ᴠ"; case 'w' -> "ᴡ"; case 'x' -> "x";
            case 'y' -> "ʏ"; case 'z' -> "ᴢ"; default -> String.valueOf(c);
        };
        return upper ? mapped : mapped;
    }

    public static String plainMaterial(String enumName) {
        if (enumName == null) return "";
        String[] split = enumName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : split) {
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    public static String duration(long seconds) {
        seconds = Math.max(0, seconds);
        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60; seconds %= 60;
        StringBuilder out = new StringBuilder();
        if (days > 0) out.append(days).append("d ");
        if (hours > 0) out.append(hours).append("h ");
        if (minutes > 0) out.append(minutes).append("m ");
        if (seconds > 0 || out.isEmpty()) out.append(seconds).append('s');
        return out.toString().trim();
    }
}
