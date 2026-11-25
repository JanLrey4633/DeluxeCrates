package de.deluxeclans.deluxecrates;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.GZIPInputStream;

public final class DeluxeCrates extends JavaPlugin implements Listener, TabCompleter {
    private final Map<String, YamlConfiguration> crates = new LinkedHashMap<>();
    private final Map<String, Set<String>> locations = new HashMap<>();
    private final Map<UUID, String> views = new HashMap<>();
    private NamespacedKey crateKey, keyKey;
    private File cratesDir, locationsFile;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    @Override public void onEnable() {
        saveDefaultConfig(); crateKey = new NamespacedKey(this, "crate_id"); keyKey = new NamespacedKey(this, "crate_key");
        cratesDir = new File(getDataFolder(), "crates"); cratesDir.mkdirs();
        locationsFile = new File(getDataFolder(), "locations.yml");
        loadLocations(); loadCrates(); getServer().getPluginManager().registerEvents(this, this);
        PluginCommand cmd = getCommand("deluxecrates"); if (cmd != null) { cmd.setExecutor(this); cmd.setTabCompleter(this); }
        getLogger().info("DeluxeCrates aktiviert: " + crates.size() + " Kisten geladen.");
    }
    @Override public void onDisable() { saveLocations(); }

    private void loadCrates() {
        crates.clear(); File[] files = cratesDir.listFiles((d,n)->n.endsWith(".yml")); if(files==null)return;
        Arrays.sort(files); for(File f:files){ YamlConfiguration y=YamlConfiguration.loadConfiguration(f); String id=y.getString("crate-id",f.getName().replaceFirst("(?i)\\.yml$","")); crates.put(id.toLowerCase(Locale.ROOT),y); }
    }
    private void loadLocations(){ locations.clear(); if(!locationsFile.exists())return; YamlConfiguration y=YamlConfiguration.loadConfiguration(locationsFile); ConfigurationSection s=y.getConfigurationSection("locations"); if(s!=null) for(String id:s.getKeys(false)) locations.put(id.toLowerCase(Locale.ROOT),new HashSet<>(s.getStringList(id))); }
    private void saveLocations(){ YamlConfiguration y=new YamlConfiguration(); for(var e:locations.entrySet()) y.set("locations."+e.getKey(),new ArrayList<>(e.getValue())); try{y.save(locationsFile);}catch(IOException e){getLogger().warning("Kistenpositionen konnten nicht gespeichert werden: "+e.getMessage());} }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
        if(a.length==0||a[0].equalsIgnoreCase("help")){ msg(sender,"<gold>DeluxeCrates</gold> <gray>Befehle:"); msg(sender,"<yellow>/dc convert <datei.yml></yellow> <gray>- CrazyCrates importieren"); msg(sender,"<yellow>/dc setcrate <id></yellow> <gray>- Kiste anvisierten Block binden"); msg(sender,"<yellow>/dc create <id></yellow> <gray>- leere Kiste erstellen"); msg(sender,"<yellow>/dc key <spieler> <id> [anzahl]</yellow>"); msg(sender,"<yellow>/dc list</yellow> <gray>- Kisten anzeigen | /dc reload"); return true; }
        if(!sender.hasPermission("deluxecrates.admin")){msg(sender,"<red>Dafür hast du keine Rechte.");return true;}
        switch(a[0].toLowerCase(Locale.ROOT)){
            case "convert" -> {if(a.length<2){msg(sender,"<red>/dc convert <datei.yml>");return true;} convert(sender,a[1]);}
            case "reload" -> {reloadConfig();loadCrates();loadLocations();msg(sender,"<green>Konfiguration und "+crates.size()+" Kisten neu geladen.");}
            case "list" -> {msg(sender,"<gold>Geladene Kisten:"); if(crates.isEmpty())msg(sender,"<gray>Keine Kisten vorhanden."); for(String id:crates.keySet())msg(sender,"<yellow>• "+id+" <gray>("+prizes(crates.get(id)).size()+" Preise)");}
            case "create" -> {if(a.length<2){msg(sender,"<red>/dc create <id>");return true;} createCrate(sender,a[1]);}
            case "setcrate" -> {if(!(sender instanceof Player p)||a.length<2){msg(sender,"<red>Als Spieler: /dc setcrate <id>");return true;} String id=a[1].toLowerCase(Locale.ROOT); if(!crates.containsKey(id)){msg(sender,"<red>Diese Kiste gibt es nicht.");return true;} Block b=p.getTargetBlockExact(6); if(b==null||b.getType().isAir()){msg(sender,"<red>Bitte innerhalb von 6 Blöcken einen Block ansehen.");return true;} locations.computeIfAbsent(id,k->new HashSet<>()).add(loc(b));saveLocations();msg(sender,"<green>Block als Kiste <yellow>"+id+"</yellow> registriert.");}
            case "key" -> {if(a.length<3){msg(sender,"<red>/dc key <spieler> <id> [anzahl]");return true;} Player target=Bukkit.getPlayerExact(a[1]); String id=a[2].toLowerCase(Locale.ROOT); int amount=a.length>3?parseInt(a[3],1):1; if(target==null){msg(sender,"<red>Spieler ist nicht online.");return true;} if(!crates.containsKey(id)){msg(sender,"<red>Kiste nicht gefunden.");return true;} target.getInventory().addItem(keyItem(id,amount));msg(sender,"<green>Schlüssel gegeben.");}
            default -> msg(sender,"<red>Unbekannter Befehl. /dc help");
        } return true;
    }
    private void createCrate(CommandSender s,String idRaw){String id=idRaw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","_");if(id.isBlank()||crates.containsKey(id)){msg(s,"<red>Ungültige oder bereits vorhandene ID.");return;} YamlConfiguration y=new YamlConfiguration();y.set("format","deluxecrates-v1");y.set("crate-id",id);y.set("display-name","<light_purple>"+id);y.set("required-keys",1);y.set("key-item","tripwire_hook");y.set("key-name","<light_purple>"+id+" Schlüssel");y.set("crate-item","chest");y.set("prizes.1.item","diamond");y.set("prizes.1.amount",1);y.set("prizes.1.weight",1.0);y.set("prizes.1.name","<aqua>Diamant");try{y.save(new File(cratesDir,id+".yml"));crates.put(id,y);msg(s,"<green>Kiste erstellt: <yellow>"+id+"</yellow>. Datei in crates/ bearbeiten und /dc reload.");}catch(IOException e){msg(s,"<red>Fehler: "+e.getMessage());}}

    private void convert(CommandSender sender,String requested){if(requested.contains("..")||requested.contains("/")||requested.contains("\\\\")||!(requested.toLowerCase(Locale.ROOT).endsWith(".yml")||requested.toLowerCase(Locale.ROOT).endsWith(".yaml"))){msg(sender,"<red>Ungültiger Dateiname.");return;} File dir=new File(getDataFolder(),"import");dir.mkdirs();File in=new File(dir,requested);if(!in.isFile()){msg(sender,"<red>Datei nach plugins/DeluxeCrates/import/ kopieren.");return;}try{YamlConfiguration src=YamlConfiguration.loadConfiguration(in);ConfigurationSection c=src.getConfigurationSection("Crate");if(c==null||c.getConfigurationSection("Prizes")==null){msg(sender,"<red>Keine gültige CrazyCrates-Kiste gefunden.");return;}String id=in.getName().replaceFirst("(?i)\\.ya?ml$","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","_");File out=new File(cratesDir,id+".yml");if(out.exists()){msg(sender,"<red>Die Kiste existiert bereits. Bitte erst sichern/umbenennen.");return;}YamlConfiguration y=new YamlConfiguration();y.set("format","deluxecrates-v1");y.set("crate-id",id);y.set("source-plugin","CrazyCrates");y.set("source-file",in.getName());y.set("display-name",c.getString("Name",id));y.set("required-keys",c.getInt("RequiredKeys",1));y.set("crate-item",c.getString("Item","chest"));y.set("key-item",c.getString("PhysicalKey.Item","tripwire_hook"));y.set("key-name",c.getString("PhysicalKey.Name","<yellow>"+id+" Key"));y.set("broadcast",c.getBoolean("Settings.Broadcast.Toggle",false));y.set("broadcast-messages",c.getStringList("Settings.Broadcast.Messages"));y.set("prize-message",c.getStringList("Prize-Message"));y.set("prize-commands",c.getStringList("Prize-Commands"));
        ConfigurationSection ps=c.getConfigurationSection("Prizes");int n=0;for(String prizeId:ps.getKeys(false)){ConfigurationSection p=ps.getConfigurationSection(prizeId);if(p==null)continue;String root="prizes."+prizeId;y.set(root+".source-id",prizeId);y.set(root+".name",p.getString("DisplayName",""));y.set(root+".lore",p.getStringList("DisplayLore"));y.set(root+".display-item",p.getString("DisplayItem","stone"));y.set(root+".amount",p.getInt("DisplayAmount",1));y.set(root+".weight",p.getDouble("Weight",1));y.set(root+".enchantments",p.getStringList("DisplayEnchantments"));y.set(root+".items-data",p.getStringList("Items"));y.set(root+".display-data",p.getString("DisplayData"));y.set(root+".commands",p.getStringList("Commands"));n++;}
        y.set("crazycrates-original",c);y.save(out);crates.put(id,y);msg(sender,"<green>Import abgeschlossen: <yellow>"+id+"</yellow> – "+n+" Preise übernommen.");msg(sender,"<gray>Originaldaten und Base64-Payloads sind zusätzlich unverändert in crazycrates-original gespeichert.");
    }catch(Exception e){msg(sender,"<red>Import fehlgeschlagen: "+e.getMessage());getLogger().warning("Importfehler: "+e);}}

    @EventHandler public void onInteract(PlayerInteractEvent e){if(e.getAction()!=Action.RIGHT_CLICK_BLOCK||e.getClickedBlock()==null)return;String at=loc(e.getClickedBlock());for(var en:locations.entrySet())if(en.getValue().contains(at)){e.setCancelled(true);openMain(e.getPlayer());return;}}
    private void openMain(Player p){Inventory inv=Bukkit.createInventory(null,54,Component.text("Kisten"));ItemStack filler=item(Material.GRAY_STAINED_GLASS_PANE," ",List.of());for(int i=0;i<54;i++)inv.setItem(i,filler);int[] slots={20,21,22,23,24,29,30,31,32,33,34};int i=0;for(var en:crates.entrySet()){if(i>=slots.length)break;YamlConfiguration y=en.getValue();Material m=mat(y.getString("crate-item","chest"),Material.CHEST);ItemStack it=item(m,y.getString("display-name",en.getKey()),List.of("<gray>Schlüssel: "+y.getInt("required-keys",1),"<yellow>Linksklick: Öffnen", "<yellow>Rechtsklick: Vorschau"));it.editMeta(meta->meta.getPersistentDataContainer().set(crateKey,PersistentDataType.STRING,en.getKey()));inv.setItem(slots[i++],it);}inv.setItem(4,item(Material.HOPPER,"<gold>Kistenfilter",List.of("<gray>Verfügbare Kisten")));p.openInventory(inv);views.put(p.getUniqueId(),"main");}
    @EventHandler public void onClick(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p))return;String view=views.get(p.getUniqueId());if(view==null)return;e.setCancelled(true);if(e.getClickedInventory()==null||e.getClickedInventory()!=e.getView().getTopInventory())return;ItemStack it=e.getCurrentItem();if(it==null||!it.hasItemMeta())return;String id=it.getItemMeta().getPersistentDataContainer().get(crateKey,PersistentDataType.STRING);if(id==null)return;if(e.isRightClick())openPreview(p,id);else openCrate(p,id);}
    @EventHandler public void onClose(org.bukkit.event.inventory.InventoryCloseEvent e){views.remove(e.getPlayer().getUniqueId());}
    private void openPreview(Player p,String id){YamlConfiguration y=crates.get(id);if(y==null)return;List<Prize> ps=prizes(y);Inventory inv=Bukkit.createInventory(null,54,Component.text(plain(y.getString("display-name",id))+" – Vorschau"));for(int i=0;i<54;i++)inv.setItem(i,item(Material.GRAY_STAINED_GLASS_PANE," ",List.of()));int slot=10;for(Prize pr:ps){if(slot>=44)break;inv.setItem(slot,pr.display);slot++;if(slot%9==8)slot+=2;}p.openInventory(inv);views.put(p.getUniqueId(),"preview");}
    private void openCrate(Player p,String id){YamlConfiguration y=crates.get(id);if(y==null)return;int need=Math.max(1,y.getInt("required-keys",1));if(!hasKey(p,id,need)){msg(p,"<red>Du brauchst "+need+" Schlüssel für diese Kiste.");return;}takeKey(p,id,need);List<Prize> list=prizes(y);if(list.isEmpty()){msg(p,"<red>Diese Kiste enthält noch keine Preise.");return;}double total=list.stream().mapToDouble(x->Math.max(0,x.weight)).sum();if(total<=0){msg(p,"<red>Die Preisgewichte sind ungültig.");return;}double r=ThreadLocalRandom.current().nextDouble(total);Prize chosen=list.get(list.size()-1);for(Prize pr:list){r-=Math.max(0,pr.weight);if(r<=0){chosen=pr;break;}}givePrize(p,id,chosen,y);}
    private void givePrize(Player p,String id,Prize pr,YamlConfiguration y){List<ItemStack> rewards=decodeItems(pr.rawItems);if(rewards.isEmpty())rewards.add(pr.display.clone());for(ItemStack reward:rewards){Map<Integer,ItemStack> left=p.getInventory().addItem(reward);left.values().forEach(x->p.getWorld().dropItemNaturally(p.getLocation(),x));}String prize=plain(pr.name.isBlank()?pr.display.getType().name():pr.name);for(String command:y.getStringList("prize-commands"))runRewardCommand(command,p,prize);for(String command:pr.commands)runRewardCommand(command,p,prize);String message="<green>Du hast <yellow>"+prize+"</yellow> aus <light_purple>"+plain(y.getString("display-name",id))+"</light_purple> erhalten!";for(String m:y.getStringList("prize-message"))msg(p,m.replace("%reward%",prize).replace("%player%",p.getName()));if(y.getStringList("prize-message").isEmpty())msg(p,message);if(y.getBoolean("broadcast",false))for(String m:y.getStringList("broadcast-messages"))Bukkit.broadcast(MM.deserialize(m.replace("%player%",p.getName()).replace("%reward%",prize)));p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,1f,1f);discord(p,id,prize);}
    private void runRewardCommand(String cmd,Player p,String prize){Bukkit.dispatchCommand(Bukkit.getConsoleSender(),cmd.replace("%player%",p.getName()).replace("%reward%",prize).replace("%crate%",p.getName()));}
    private record Prize(String id,String name,double weight,ItemStack display,List<String> rawItems,List<String> commands){}
    private List<Prize> prizes(YamlConfiguration y){List<Prize> out=new ArrayList<>();ConfigurationSection s=y.getConfigurationSection("prizes");if(s==null){s=y.getConfigurationSection("crazycrates.Prizes");}if(s==null)return out;for(String id:s.getKeys(false)){ConfigurationSection p=s.getConfigurationSection(id);if(p==null)continue;String name=p.getString("name",p.getString("DisplayName",""));Material m=mat(p.getString("display-item",p.getString("DisplayItem","stone")),Material.STONE);int amount=Math.max(1,Math.min(64,p.getInt("amount",p.getInt("DisplayAmount",1))));ItemStack display=item(m,name,p.getStringList("lore"));if(display.getAmount()!=amount)display.setAmount(amount);for(String enc:p.getStringList("enchantments")){String[] bits=enc.split(":");if(bits.length==2){Enchantment en=Enchantment.getByName(bits[0].trim().toUpperCase(Locale.ROOT));if(en!=null)display.addUnsafeEnchantment(en,parseInt(bits[1],1));}}out.add(new Prize(id,name,p.getDouble("weight",p.getDouble("Weight",1)),display,p.getStringList("items-data"),p.getStringList("Commands")));}return out;}
    private List<ItemStack> decodeItems(List<String> lines){List<ItemStack> out=new ArrayList<>();for(String line:lines){if(line==null||!line.startsWith("Data:"))continue;String b64=line.substring(5).trim();try{byte[] bytes=Base64.getDecoder().decode(b64);InputStream raw=new ByteArrayInputStream(bytes);raw=new GZIPInputStream(raw);try(BukkitObjectInputStream in=new BukkitObjectInputStream(raw)){Object obj=in.readObject();if(obj instanceof ItemStack item)out.add(item);else if(obj instanceof ItemStack[] items)out.addAll(Arrays.asList(items));else if(obj instanceof Collection<?> c)for(Object x:c)if(x instanceof ItemStack item)out.add(item);}}catch(Exception ignored){}}return out;}
    private ItemStack keyItem(String id,int amount){YamlConfiguration y=crates.get(id);Material m=mat(y.getString("key-item","tripwire_hook"),Material.TRIPWIRE_HOOK);ItemStack it=item(m,y.getString("key-name","<yellow>"+id+" Key"),List.of("<gray>Schlüssel für "+id));it.setAmount(Math.max(1,Math.min(64,amount)));it.editMeta(meta->meta.getPersistentDataContainer().set(keyKey,PersistentDataType.STRING,id));return it;}
    private boolean hasKey(Player p,String id,int n){int count=0;for(ItemStack it:p.getInventory().getContents())if(isKey(it,id))count+=it.getAmount();return count>=n;}
    private void takeKey(Player p,String id,int n){for(int i=0;i<p.getInventory().getSize()&&n>0;i++){ItemStack it=p.getInventory().getItem(i);if(isKey(it,id)){int take=Math.min(n,it.getAmount());it.setAmount(it.getAmount()-take);n-=take;if(it.getAmount()<=0)p.getInventory().setItem(i,null);}}}
    private boolean isKey(ItemStack it,String id){return it!=null&&it.hasItemMeta()&&id.equals(it.getItemMeta().getPersistentDataContainer().get(keyKey,PersistentDataType.STRING));}
    private void discord(Player p,String crate,String prize){if(!getConfig().getBoolean("discord.enabled",false))return;String url=getConfig().getString("discord.webhook-url","");if(url.isBlank()||url.contains("PASTE_WEBHOOK"))return;String now=LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss|dd.MM.yyyy"));String[] dt=now.split("\\|");String body=getConfig().getString("discord.message", "Spieler: {player} | Kiste: {crate} | Gewinn: {prize}").replace("{player}",p.getName()).replace("{crate}",crate).replace("{prize}",prize).replace("{time}",dt[0]).replace("{date}",dt[1]);String json="{\\"username\\":\\""+escape(getConfig().getString("discord.username","DELUXECLANS"))+"\\",\\"content\\":\\""+escape(body)+"\\"}";Bukkit.getScheduler().runTaskAsynchronously(this,()->{try{HttpRequest req=HttpRequest.newBuilder(URI.create(url)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();HttpClient.newHttpClient().send(req,HttpResponse.BodyHandlers.discarding());}catch(Exception ex){getLogger().warning("Discord-Webhook fehlgeschlagen: "+ex.getMessage());}});}
    private String escape(String s){return s.replace("\\\\","\\\\\\\\").replace("\\\"","\\\\\\\"").replace("\\n","\\\\n").replace("\\r","");}
    private ItemStack item(Material m,String name,List<String> lore){ItemStack it=new ItemStack(m);ItemMeta meta=it.getItemMeta();if(name!=null&&!name.isBlank())meta.displayName(MM.deserialize(name));if(lore!=null&&!lore.isEmpty())meta.lore(lore.stream().map(MM::deserialize).toList());it.setItemMeta(meta);return it;}
    private Material mat(String s,Material fallback){if(s==null)return fallback;Material m=Material.matchMaterial(s);return m==null?fallback:m;}
    private String loc(Block b){return b.getWorld().getName()+","+b.getX()+","+b.getY()+","+b.getZ();}
    private String plain(String s){return PlainTextComponentSerializer.plainText().serialize(MM.deserialize(s==null?"":s));}
    private void msg(CommandSender s,String m){String prefix=getConfig().getString("settings.prefix", "<dark_purple>[DeluxeCrates]</dark_purple> ");s.sendMessage(MM.deserialize(prefix+m));}
    private int parseInt(String s,int d){try{return Integer.parseInt(s);}catch(Exception e){return d;}}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){if(args.length==1)return Arrays.asList("help","convert","create","setcrate","key","list","reload").stream().filter(x->x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();if(args.length==2&&args[0].equalsIgnoreCase("setcrate"))return crates.keySet().stream().toList();if(args.length==3&&args[0].equalsIgnoreCase("key"))return crates.keySet().stream().toList();return List.of();}
}
