package ro.abyssmc.abyssstats;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;

public final class AbyssStats implements ModInitializer {
    static final Path DIR=Path.of("config","abyssstats"), DATA=DIR.resolve("data.json"), HOLOS=DIR.resolve("holograms");
    static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    static final Map<String,Arena> arenas=new LinkedHashMap<>();
    static final Map<UUID,Selection> selections=new HashMap<>();
    static final Map<String,Hologram> holograms=new LinkedHashMap<>();
    static MinecraftServer server;
    static int ticks;
    static class Selection { String world; BlockPos a,b; }
    static class Arena { String world; int x1,y1,z1,x2,y2,z2; Map<String,Record> players=new HashMap<>(); boolean contains(ServerPlayer p) { BlockPos b=p.blockPosition(); return world.equals(p.level().dimension().identifier().toString()) && b.getX()>=Math.min(x1,x2)&&b.getX()<=Math.max(x1,x2)&&b.getY()>=Math.min(y1,y2)&&b.getY()<=Math.max(y1,y2)&&b.getZ()>=Math.min(z1,z2)&&b.getZ()<=Math.max(z1,z2); } }
    static class Record { String name; int kills,deaths; }
    static class Hologram { String id,arena,type,world; double x,y,z; }
    static class State { Map<String,Arena> arenas=new LinkedHashMap<>(); Map<String,Hologram> holograms=new LinkedHashMap<>(); }
    @Override public void onInitialize() {
        load();
        CommandRegistrationCallback.EVENT.register((dispatcher,access,env)-> {
            var root=literal("as");
            root.then(literal("pos1").requires(AbyssStats::admin).executes(c->select(c.getSource(),true)));
            root.then(literal("pos2").requires(AbyssStats::admin).executes(c->select(c.getSource(),false)));
            root.then(literal("create").requires(AbyssStats::admin).then(argument("arena",StringArgumentType.word()).executes(c->create(c.getSource(),StringArgumentType.getString(c,"arena")))));
            root.then(literal("delete").requires(AbyssStats::admin).then(argument("arena",StringArgumentType.word()).executes(c->delete(c.getSource(),StringArgumentType.getString(c,"arena")))));
            root.then(literal("list").executes(c->{reply(c.getSource(),"Arene: "+arenas.keySet());return 1;}));
            root.then(literal("stats").then(argument("player",StringArgumentType.word()).then(argument("arena",StringArgumentType.word()).executes(c->stats(c.getSource(),StringArgumentType.getString(c,"player"),StringArgumentType.getString(c,"arena"))))));
            root.then(literal("reload").requires(AbyssStats::admin).executes(c->{load(); refresh();reply(c.getSource(),"Configuratia a fost reincarcata.");return 1;}));
            root.then(literal("holo").then(literal("create").requires(AbyssStats::admin).then(argument("arena",StringArgumentType.word()).then(literal("kills").executes(c->holoCreate(c.getSource(),StringArgumentType.getString(c,"arena"),"kills"))).then(literal("deaths").executes(c->holoCreate(c.getSource(),StringArgumentType.getString(c,"arena"),"deaths"))))));
            root.then(literal("holo").then(literal("remove").requires(AbyssStats::admin).then(argument("arena",StringArgumentType.word()).then(literal("kills").executes(c->holoRemove(c.getSource(),StringArgumentType.getString(c,"arena"),"kills"))).then(literal("deaths").executes(c->holoRemove(c.getSource(),StringArgumentType.getString(c,"arena"),"deaths"))))));
            dispatcher.register(root);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)-> {
            if (!(entity instanceof ServerPlayer victim)) return;
            ServerPlayer killer=source.getEntity() instanceof ServerPlayer p ? p : null;
            for (Arena a:arenas.values()) if (a.contains(victim)) {
                Record dead=a.players.computeIfAbsent(victim.getUUID().toString(),k->new Record()); dead.name=victim.getGameProfile().name();dead.deaths++;
                if(killer!=null && killer!=victim) { Record kr=a.players.computeIfAbsent(killer.getUUID().toString(),k->new Record()); kr.name=killer.getGameProfile().name();kr.kills++; }
            }
            save(); refresh();
        });
        ServerTickEvents.END_SERVER_TICK.register(s->{server=s;if(++ticks%1200==0) save();if(ticks%600==0) refresh();});
    }
    static boolean admin(CommandSourceStack s){return s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_ADMIN);}
    static void reply(CommandSourceStack s,String msg){s.sendSuccess(()->Component.literal("[AbyssStats] "+msg),false);}
    static ServerPlayer player(CommandSourceStack s){try{return s.getPlayerOrException();}catch(Exception e){reply(s,"Comanda este disponibila doar in joc.");return null;}}
    static int select(CommandSourceStack s,boolean first){ServerPlayer p=player(s);if(p==null)return 0;Selection sel=selections.computeIfAbsent(p.getUUID(),u->new Selection());String w=p.level().dimension().identifier().toString();if(sel.world!=null&&!sel.world.equals(w)){sel.a=null;sel.b=null;}sel.world=w;if(first)sel.a=p.blockPosition();else sel.b=p.blockPosition();reply(s,(first?"Pos1":"Pos2")+": "+p.blockPosition().toShortString()+" ("+w+")");return 1;}
    static int create(CommandSourceStack s,String name){ServerPlayer p=player(s);if(p==null)return 0;Selection sel=selections.get(p.getUUID());if(sel==null||sel.a==null||sel.b==null){reply(s,"Selecteaza /as pos1 si /as pos2.");return 0;}if(arenas.containsKey(name)){reply(s,"Arena exista deja.");return 0;}Arena a=new Arena();a.world=sel.world;a.x1=sel.a.getX();a.y1=sel.a.getY();a.z1=sel.a.getZ();a.x2=sel.b.getX();a.y2=sel.b.getY();a.z2=sel.b.getZ();arenas.put(name,a);save();reply(s,"Arena "+name+" creata.");return 1;}
    static int delete(CommandSourceStack s,String name){if(arenas.remove(name)==null){reply(s,"Arena inexistenta.");return 0;}for(String id:new ArrayList<>(holograms.keySet()))if(holograms.get(id).arena.equals(name))holograms.remove(id);save();refresh();reply(s,"Arena stearsa.");return 1;}
    static int stats(CommandSourceStack s,String name,String arena){Arena a=arenas.get(arena);if(a==null){reply(s,"Arena inexistenta.");return 0;}Record r=a.players.values().stream().filter(v->name.equalsIgnoreCase(v.name)).findFirst().orElse(null);reply(s,r==null?"Fara statistici pentru "+name:"["+arena+"] "+r.name+": "+r.kills+" kills / "+r.deaths+" deaths");return 1;}
    static int holoCreate(CommandSourceStack s,String arena,String type){ServerPlayer p=player(s);if(p==null)return 0;if(!arenas.containsKey(arena)){reply(s,"Arena inexistenta.");return 0;}String id=arena+"_"+type;Hologram h=new Hologram();h.id=id;h.arena=arena;h.type=type;h.world=p.level().dimension().identifier().toString();Vec3 v=p.position();h.x=v.x;h.y=v.y+2.2;h.z=v.z;holograms.put(id,h);ensureConfig(h);save();refresh();reply(s,"Holograma "+id+" creata la pozitia ta.");return 1;}
    static int holoRemove(CommandSourceStack s,String arena,String type){String id=arena+"_"+type;if(holograms.remove(id)==null){reply(s,"Holograma inexistenta.");return 0;}save();refresh();reply(s,"Holograma eliminata.");return 1;}
    static void ensureConfig(Hologram h){try{Files.createDirectories(HOLOS);Path f=HOLOS.resolve("scoreboard-"+h.id+".yml");if(!Files.exists(f))Files.writeString(f,"# Holograma AbyssStats; nu este scoreboard lateral\ntitle: '&6&lABYSSMC &8| &f"+h.arena.toUpperCase(Locale.ROOT)+" &7- &e"+h.type.toUpperCase(Locale.ROOT)+"'\nlimit: 10\nline: '&e#{rank} &f{name} &7- &6{value}'\nempty: '&7Nu exista statistici inca'\n",StandardCharsets.UTF_8);}catch(Exception e){System.err.println("[AbyssStats] Config: "+e);}}
    static Map<String,String> config(Hologram h){ensureConfig(h);Map<String,String> m=new HashMap<>();try{for(String l:Files.readAllLines(HOLOS.resolve("scoreboard-"+h.id+".yml"))){int i=l.indexOf(':');if(i<1||l.trim().startsWith("#"))continue;String v=l.substring(i+1).trim();if(v.length()>1&&v.startsWith("'")&&v.endsWith("'"))v=v.substring(1,v.length()-1);m.put(l.substring(0,i).trim(),v);}}catch(Exception ignored){}return m;}
    static String json(String value){return GSON.toJson(Map.of("text",value));}
    static String format(String s){return s.replace('&','§');}
    static String tag(String id){return "abyssstats_"+id.replaceAll("[^a-zA-Z0-9_]","_");}
    static void command(String cmd){if(server!=null)try{server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),cmd);}catch(Exception e){System.err.println("[AbyssStats] command: "+e);}}
    static void refresh(){if(server==null)return;
        // Remove old display lines, including holograms deleted from config.
        command("kill @e[type=minecraft:text_display,tag=abyssstats_holo]");
        for(Hologram h:holograms.values()){
            Arena a=arenas.get(h.arena);if(a==null)continue;Map<String,String> cfg=config(h);int limit=10;try{limit=Math.max(1,Math.min(30,Integer.parseInt(cfg.getOrDefault("limit","10"))));}catch(Exception ignored){}
            List<Record> sorted=new ArrayList<>(a.players.values());sorted.sort(Comparator.comparingInt((Record r)->h.type.equals("kills")?r.kills:r.deaths).reversed());
            List<String> lines=new ArrayList<>();lines.add(format(cfg.getOrDefault("title","&6TOP "+h.type.toUpperCase(Locale.ROOT))));
            for(int i=0;i<Math.min(limit,sorted.size());i++){Record r=sorted.get(i);lines.add(format(cfg.getOrDefault("line","&e#{rank} &f{name} &7- &6{value}").replace("{rank}",""+(i+1)).replace("{name}",r.name==null?"Unknown":r.name).replace("{value}",""+(h.type.equals("kills")?r.kills:r.deaths))));}
            if(sorted.isEmpty())lines.add(format(cfg.getOrDefault("empty","&7Nu exista statistici inca")));
            for(int i=0;i<lines.size();i++){
                String text=json(lines.get(i));String nbt="{Tags:[\"abyssstats_holo\",\""+tag(h.id)+"\"],billboard:\"center\",text:'"+text.replace("'","\\'")+"',see_through:1b,shadow:1b}";
                command("execute in "+h.world+" run summon minecraft:text_display "+h.x+" "+(h.y-i*0.27)+" "+h.z+" "+nbt);
            }
        }
    }
    static void load(){try{Files.createDirectories(DIR);Files.createDirectories(HOLOS);if(!Files.exists(DATA))return;State s=GSON.fromJson(Files.readString(DATA),State.class);arenas.clear();holograms.clear();if(s!=null){if(s.arenas!=null)arenas.putAll(s.arenas);if(s.holograms!=null)holograms.putAll(s.holograms);}}catch(Exception e){throw new IllegalStateException("AbyssStats nu poate citi data.json",e);}}
    static void save(){try{Files.createDirectories(DIR);State s=new State();s.arenas.putAll(arenas);s.holograms.putAll(holograms);Path tmp=DIR.resolve("data.json.tmp");Files.writeString(tmp,GSON.toJson(s),StandardCharsets.UTF_8);Files.move(tmp,DATA,StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){System.err.println("[AbyssStats] Salvare esuata: "+e);}}
}
