package pl.maghub.guilds.util;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public final class Items {
    private Items() {
    }

    public static int count(Inventory inventory, Material material) {
        int amount = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() == material) amount += item.getAmount();
        }
        return amount;
    }

    public static boolean has(Inventory inventory, Map<Material, Integer> costs) {
        for (Map.Entry<Material, Integer> entry : costs.entrySet()) {
            if (count(inventory, entry.getKey()) < entry.getValue()) return false;
        }
        return true;
    }

    public static void remove(Inventory inventory, Material material, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() != material) continue;
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) inventory.setItem(slot, null);
        }
    }

    public static ItemStack menu(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            List<String> colored = new ArrayList<>();
            for (String line : lore) colored.add(Text.color(line));
            meta.setLore(colored);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static String encode(ItemStack[] contents) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
                output.writeInt(contents.length);
                for (ItemStack item : contents) output.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception exception) {
            return "";
        }
    }

    public static ItemStack[] decode(String data, int fallbackSize) {
        if (data == null || data.isBlank()) return new ItemStack[fallbackSize];
        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
                int size = input.readInt();
                ItemStack[] contents = new ItemStack[size];
                for (int i = 0; i < size; i++) contents[i] = (ItemStack) input.readObject();
                return contents;
            }
        } catch (Exception exception) {
            return new ItemStack[fallbackSize];
        }
    }
}
