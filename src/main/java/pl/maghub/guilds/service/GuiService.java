package pl.maghub.guilds.service;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import pl.maghub.guilds.MAGGuildsPlugin;
import pl.maghub.guilds.data.DataStore;
import pl.maghub.guilds.model.Guild;
import pl.maghub.guilds.util.Items;

import java.util.*;

public final class GuiService {
    public record Holder(String type, String tag, UUID target, String category) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    private final MAGGuildsPlugin plugin; private final GuildService guilds; private final DataStore dataStore; private final MessageService messages; private final RegenerationService regeneration; private final AchievementService achievements;
    public GuiService(MAGGuildsPlugin plugin, GuildService guilds, DataStore dataStore, MessageService messages, RegenerationService regeneration, AchievementService achievements) { this.plugin=plugin; this.guilds=guilds; this.dataStore=dataStore; this.messages=messages; this.regeneration=regeneration; this.achievements=achievements; }

    public void openMain(Player player) {
        Guild guild = guilds.byPlayer(player.getUniqueId()); if (guild == null) { messages.send(player,"no-guild"); return; }
        Inventory inv = Bukkit.createInventory(new Holder("main", guild.tag(), null, null), 45, messages.literal("&#8B5CF6&lPanel gildii &#F8FAFC["+guild.tag()+"]"));
        inv.setItem(10, Items.menu(Material.PLAYER_HEAD,"&#D946EF&lCzlonkowie",List.of("&#C4B5FDLPM: otworz uprawnienia i role")));
        inv.setItem(12, Items.menu(Material.ENDER_CHEST,"&#D946EF&lMagazyn",List.of("&#C4B5FDWspolny magazyn gildii")));
        inv.setItem(14, Items.menu(Material.BEACON,"&#D946EF&lRegeneracja",List.of("&#C4B5FDZniszczenia: &#F8FAFC"+regeneration.damaged(guild.tag()),"&#C4B5FDKliknij, aby rozpoczac")));
        inv.setItem(16, Items.menu(Material.NETHER_STAR,"&#D946EF&lOsiagniecia",List.of("&#C4B5FDWspolne osiagniecia gildii")));
        inv.setItem(31, Items.menu(Material.COMPASS,"&#D946EF&lInformacje",List.of("&#C4B5FDPunkty: &#F8FAFC"+guild.points(),"&#C4B5FDCzlonkowie: &#F8FAFC"+guild.members().size()+"/"+guild.memberLimit(),"&#C4B5FDZycia: &#F8FAFC"+guild.lives())));
        player.openInventory(inv);
    }

    public void openMembers(Player player) {
        Guild guild=guilds.byPlayer(player.getUniqueId()); if(guild==null)return;
        Inventory inv=Bukkit.createInventory(new Holder("members",guild.tag(),null,null),54,messages.literal("&#8B5CF6&lUprawnienia gildii")); int slot=0;
        for(UUID uuid:guild.members()) { OfflinePlayer target=Bukkit.getOfflinePlayer(uuid); ItemStack head=new ItemStack(Material.PLAYER_HEAD); SkullMeta meta=(SkullMeta)head.getItemMeta(); meta.setOwningPlayer(target); meta.setDisplayName(messages.literal("&#F8FAFC"+(target.getName()==null?uuid.toString():target.getName()))); meta.setLore(List.of(messages.literal("&#C4B5FDLPM: indywidualne uprawnienia"),messages.literal("&#C4B5FDPPM: nadanie roli"),messages.literal("&#6D5B7BRola: &#F8FAFC"+guild.roleId(uuid,guilds.defaultRole())))); head.setItemMeta(meta); inv.setItem(slot++,head); if(slot>=45)break; }
        inv.setItem(49,Items.menu(Material.ANVIL,"&#D946EF&lRole gildii",List.of("&#C4B5FDLista i edycja rol"))); player.openInventory(inv);
    }

    public void openRoles(Player player, UUID target) {
        Guild guild=guilds.byPlayer(player.getUniqueId()); if(guild==null)return; Inventory inv=Bukkit.createInventory(new Holder("roles",guild.tag(),target,null),54,messages.literal("&#8B5CF6&lRole gildii")); int slot=0;
        for(Guild.Role role:guild.roles().values()){ inv.setItem(slot++,Items.menu(Material.NAME_TAG,"&#D946EF&l"+role.name(),List.of("&#C4B5FDUprawnienia: &#F8FAFC"+role.permissions().size(),target==null?"&#6D5B7BLPM: edycja roli":"&#34D399LPM: nadaj role"))); if(slot>=45)break; }
        player.openInventory(inv);
    }

    public void openPermissions(Player player, UUID target) {
        Guild guild=guilds.byPlayer(player.getUniqueId()); if(guild==null)return; Inventory inv=Bukkit.createInventory(new Holder("permissions",guild.tag(),target,null),54,messages.literal("&#8B5CF6&lUprawnienia gracza")); int slot=0;
        for(Guild.Permission permission:Guild.Permission.values()){ boolean allowed=guild.hasPermission(target,permission,guilds.defaultRole()); inv.setItem(slot++,Items.menu(allowed?Material.LIME_DYE:Material.RED_DYE,"&#D946EF&l"+permission.name(),List.of("&#C4B5FDStan efektywny: "+(allowed?"&#34D399WLACZONE":"&#FB7185WYLACZONE"),"&#C4B5FDLPM: ALLOW/DENY","&#C4B5FDPPM: dziedziczenie"))); }
        player.openInventory(inv);
    }

    public void openAchievements(Player player){ Guild guild=guilds.byPlayer(player.getUniqueId()); if(guild==null)return; Inventory inv=Bukkit.createInventory(new Holder("achievements",guild.tag(),null,null),54,messages.literal("&#8B5CF6&lOsiagniecia gildii")); int slot=10; for(AchievementService.Category c:achievements.categories()){ Material m=Material.matchMaterial(c.material()); if(m==null)m=Material.PAPER; long progress=guild.stat(c.id()); inv.setItem(slot,Items.menu(m,"&#D946EF&l"+c.name(),List.of("&#C4B5FDPostep: &#F8FAFC"+progress,"&#C4B5FDLPM: poziomy"))); slot++; if(slot%9==8)slot+=2; } player.openInventory(inv); }
    public void openAchievementLevels(Player player,String category){ Guild guild=guilds.byPlayer(player.getUniqueId()); AchievementService.Category c=achievements.category(category); if(guild==null||c==null)return; Inventory inv=Bukkit.createInventory(new Holder("achievement-levels",guild.tag(),null,category),54,messages.literal("&#8B5CF6&l"+c.name())); for(int i=0;i<c.thresholds().size();i++){ int level=i+1; String key=category+":"+level; boolean claimed=guild.claimedAchievements().contains(key); boolean ready=guild.stat(category)>=c.thresholds().get(i); Material material=claimed?Material.GRAY_DYE:ready?Material.LIME_DYE:Material.RED_DYE; inv.setItem(10+i+(i>=7?2:0),Items.menu(material,"&#D946EF&lPoziom "+level,List.of("&#C4B5FDCel: &#F8FAFC"+c.thresholds().get(i),"&#C4B5FDPostep: &#F8FAFC"+guild.stat(category),claimed?"&#6D5B7BODEBRANE":ready?"&#34D399GOTOWE DO ODBIORU":"&#FB7185ZABLOKOWANE"))); } inv.setItem(49,Items.menu(Material.HOPPER,"&#34D399&lOdbierz wszystkie",List.of("&#C4B5FDTylko lider lub zastepca"))); player.openInventory(inv); }

    public void handle(Player player, Holder holder, int slot, ClickType click) {
        Guild guild=guilds.byTag(holder.tag()); if(guild==null)return;
        switch(holder.type()){
            case "main"->{if(slot==10)openMembers(player);else if(slot==12)openStorage(player);else if(slot==14)regeneration.start(player,guild);else if(slot==16)openAchievements(player);}
            case "members"->{if(slot==49){openRoles(player,null);return;} if(slot<0||slot>=guild.members().size())return; UUID target=new ArrayList<>(guild.members()).get(slot); if(click.isLeftClick())openPermissions(player,target);else if(click.isRightClick())openRoles(player,target);}
            case "roles"->{if(slot<0||slot>=guild.roles().size())return; Guild.Role role=new ArrayList<>(guild.roles().values()).get(slot); if(holder.target()!=null&&guild.canManage(player.getUniqueId())){guilds.assignRole(guild,holder.target(),role.id()); messages.send(player,"role-assigned","role",role.name(),"player",Bukkit.getOfflinePlayer(holder.target()).getName());openMembers(player);} }
            case "permissions"->{if(slot<0||slot>=Guild.Permission.values().length||holder.target()==null||!guild.canManage(player.getUniqueId()))return; Guild.Permission permission=Guild.Permission.values()[slot]; EnumMap<Guild.Permission,Guild.TriState> map=guild.overrides().computeIfAbsent(holder.target(),k->new EnumMap<>(Guild.Permission.class)); if(click.isRightClick())map.remove(permission);else {boolean effective=guild.hasPermission(holder.target(),permission,guilds.defaultRole());map.put(permission,effective?Guild.TriState.DENY:Guild.TriState.ALLOW);}guilds.save();openPermissions(player,holder.target());}
            case "achievements"->{List<AchievementService.Category> list=new ArrayList<>(achievements.categories()); int index=achievementIndex(slot);if(index>=0&&index<list.size())openAchievementLevels(player,list.get(index).id());}
            case "achievement-levels"->{if(slot==49){achievements.claimAll(player,holder.category());openAchievementLevels(player,holder.category());return;}int level=levelFromSlot(slot);if(level>0){achievements.claim(player,holder.category(),level);openAchievementLevels(player,holder.category());}}
        }
    }
    private int achievementIndex(int slot){int[] slots={10,11,12,13,14,15,16,19,20};for(int i=0;i<slots.length;i++)if(slots[i]==slot)return i;return-1;} private int levelFromSlot(int slot){for(int i=0;i<9;i++)if(10+i+(i>=7?2:0)==slot)return i+1;return-1;}
    public void openStorage(Player player){Guild guild=guilds.byPlayer(player.getUniqueId());if(guild==null)return;Inventory inv=Bukkit.createInventory(new Holder("storage",guild.tag(),null,null),54,messages.literal("&#8B5CF6&lMagazyn gildii"));inv.setContents(pl.maghub.guilds.util.Items.decode(guild.storageData(),54));player.openInventory(inv);} public void saveStorage(Inventory inventory,Holder holder){Guild guild=guilds.byTag(holder.tag());if(guild!=null){guild.storageData(pl.maghub.guilds.util.Items.encode(inventory.getContents()));guilds.save();}}
}
