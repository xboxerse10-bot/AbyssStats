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
    static final Set<String> previousHologramDimensions=new HashSet<>();
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
    static final String PREFIX="&#00D6FF&lᴀ&#00CDFF&lʙ&#00C4FF&lʏ&#00BBFF&lꜱ&#00B1FF&lꜱ&#00A7FF&lꜱ&#009CFF&lᴛ&#0091FF&lᴀ&#0085FF&lᴛ&#0079FF&lꜱ";
    static void reply(CommandSourceStack s,String msg){
        s.sendSuccess(()->parseText(PREFIX+" &8» &f"+msg),false);
    }
    static ServerPlayer player(CommandSourceStack s){try{return s.getPlayerOrException();}catch(Exception e){reply(s,"Comanda este disponibila doar in joc.");return null;}}
    static int select(CommandSourceStack s,boolean first){ServerPlayer p=player(s);if(p==null)return 0;Selection sel=selections.computeIfAbsent(p.getUUID(),u->new Selection());String w=p.level().dimension().identifier().toString();if(sel.world!=null&&!sel.world.equals(w)){sel.a=null;sel.b=null;}sel.world=w;if(first)sel.a=p.blockPosition();else sel.b=p.blockPosition();reply(s,(first?"Pos1":"Pos2")+": "+p.blockPosition().toShortString()+" ("+w+")");return 1;}
    static int create(CommandSourceStack s,String name){ServerPlayer p=player(s);if(p==null)return 0;Selection sel=selections.get(p.getUUID());if(sel==null||sel.a==null||sel.b==null){reply(s,"Selecteaza /as pos1 si /as pos2.");return 0;}if(arenas.containsKey(name)){reply(s,"Arena exista deja.");return 0;}Arena a=new Arena();a.world=sel.world;a.x1=sel.a.getX();a.y1=sel.a.getY();a.z1=sel.a.getZ();a.x2=sel.b.getX();a.y2=sel.b.getY();a.z2=sel.b.getZ();arenas.put(name,a);save();reply(s,"Arena "+name+" creata.");return 1;}
    static int delete(CommandSourceStack s,String name){if(arenas.remove(name)==null){reply(s,"Arena inexistenta.");return 0;}for(String id:new ArrayList<>(holograms.keySet()))if(holograms.get(id).arena.equals(name))holograms.remove(id);save();refresh();reply(s,"Arena stearsa.");return 1;}
    static int stats(CommandSourceStack s,String name,String arena){Arena a=arenas.get(arena);if(a==null){reply(s,"Arena inexistenta.");return 0;}Record r=a.players.values().stream().filter(v->name.equalsIgnoreCase(v.name)).findFirst().orElse(null);reply(s,r==null?"Fara statistici pentru "+name:"["+arena+"] "+r.name+": "+r.kills+" kills / "+r.deaths+" deaths");return 1;}
    static int holoCreate(CommandSourceStack s,String arena,String type){ServerPlayer p=player(s);if(p==null)return 0;if(!arenas.containsKey(arena)){reply(s,"Arena inexistenta.");return 0;}String id=arena+"_"+type;Hologram h=new Hologram();h.id=id;h.arena=arena;h.type=type;h.world=p.level().dimension().identifier().toString();Vec3 v=p.position();h.x=v.x;h.y=v.y+2.2;h.z=v.z;holograms.put(id,h);ensureConfig(h);save();refresh();reply(s,"Holograma "+id+" creata la pozitia ta.");return 1;}
    static int holoRemove(CommandSourceStack s,String arena,String type){String id=arena+"_"+type;if(holograms.remove(id)==null){reply(s,"Holograma inexistenta.");return 0;}save();refresh();reply(s,"Holograma eliminata.");return 1;}
    static void ensureConfig(Hologram h){try{Files.createDirectories(HOLOS);Path f=HOLOS.resolve("scoreboard-"+h.id+".yml");if(!Files.exists(f))Files.writeString(f,"# Holograma AbyssStats; nu este scoreboard lateral\ntitle: '&6&lABYSSMC &8| &f"+h.arena.toUpperCase(Locale.ROOT)+" &7- &e"+h.type.toUpperCase(Locale.ROOT)+"'\nlimit: 10\nline: '&e#{rank} &f{name} &7- &6{value}'\nempty: '&7Nu exista statistici inca'\n",StandardCharsets.UTF_8);}catch(Exception e){System.err.println("[AbyssStats] Config: "+e);}}
    static Map<String,String> config(Hologram h){ensureConfig(h);Map<String,String> m=new HashMap<>();try{for(String l:Files.readAllLines(HOLOS.resolve("scoreboard-"+h.id+".yml"))){int i=l.indexOf(':');if(i<1||l.trim().startsWith("#"))continue;String v=l.substring(i+1).trim();if(v.length()>1&&v.startsWith("'")&&v.endsWith("'"))v=v.substring(1,v.length()-1);m.put(l.substring(0,i).trim(),v);}}catch(Exception ignored){}return m;}
    // Parse legacy & codes and HEX &#RRGGBB into actual Minecraft text components.
    // Do not put JSON into a quoted NBT string: text_display expects a component compound.
    static Component parseText(String input){
        net.minecraft.network.chat.MutableComponent out=Component.empty();
        StringBuilder chunk=new StringBuilder();
        net.minecraft.network.chat.Style style=net.minecraft.network.chat.Style.EMPTY;
        for(int i=0;i<input.length();){
            if(input.charAt(i)=='&' && i+1<input.length()){
                if(input.charAt(i+1)=='#' && i+8<=input.length()){
                    String hex=input.substring(i+2,i+8);
                    if(hex.matches("[0-9a-fA-F]{6}")){
                        if(chunk.length()>0){out.append(Component.literal(chunk.toString()).setStyle(style));chunk.setLength(0);}
                        style=net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(Integer.parseInt(hex,16)));
                        i+=8;continue;
                    }
                }
                char code=Character.toLowerCase(input.charAt(i+1));
                String palette="0123456789abcdef";
                int idx=palette.indexOf(code);
                if(idx>=0 || "klmnor".indexOf(code)>=0){
                    if(chunk.length()>0){out.append(Component.literal(chunk.toString()).setStyle(style));chunk.setLength(0);}
                    if(idx>=0){
                        int[] colors={0x000000,0x0000AA,0x00AA00,0x00AAAA,0xAA0000,0xAA00AA,0xFFAA00,0xAAAAAA,0x555555,0x5555FF,0x55FF55,0x55FFFF,0xFF5555,0xFF55FF,0xFFFF55,0xFFFFFF};
                        style=net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(colors[idx]));
                    }else if(code=='l')style=style.withBold(true);
                    else if(code=='o')style=style.withItalic(true);
                    else if(code=='n')style=style.withUnderlined(true);
                    else if(code=='m')style=style.withStrikethrough(true);
                    else if(code=='k')style=style.withObfuscated(true);
                    else if(code=='r')style=net.minecraft.network.chat.Style.EMPTY;
                    i+=2;continue;
                }
            }
            chunk.append(input.charAt(i++));
        }
        if(chunk.length()>0)out.append(Component.literal(chunk.toString()).setStyle(style));
        return out;
    }
    static String snbtQuote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static String hologramComponent(String text, String fallbackColor) {
        // 3R.5.1: Support legacy & colors, HEX, formatting and Birdflop gradients in Text Displays.
        // Build a structured SNBT component rather than displaying formatting tokens literally.
        Matcher gradient = Pattern.compile("(?i)<gradient:#([0-9a-f]{6}):#([0-9a-f]{6})>(.*?)</gradient>", Pattern.DOTALL).matcher(text);
        StringBuffer expanded = new StringBuffer();
        while (gradient.find()) {
            String body = gradient.group(3);
            int from = Integer.parseInt(gradient.group(1), 16);
            int to = Integer.parseInt(gradient.group(2), 16);
            int count = body.codePointCount(0, body.length());
            StringBuilder coloredBody = new StringBuilder();
            int i = 0;
            for (int off = 0; off < body.length();) {
                int cp = body.codePointAt(off);
                double t = count <= 1 ? 0 : (double)i / (count - 1);
                int r = (int)Math.round(((from >> 16) & 255) * (1-t) + ((to >> 16) & 255) * t);
                int g = (int)Math.round(((from >> 8) & 255) * (1-t) + ((to >> 8) & 255) * t);
                int bl = (int)Math.round((from & 255) * (1-t) + (to & 255) * t);
                coloredBody.append(String.format(Locale.ROOT, "&#%02X%02X%02X", r, g, bl));
                coloredBody.appendCodePoint(cp);
                off += Character.charCount(cp);
                i++;
            }
            gradient.appendReplacement(expanded, Matcher.quoteReplacement(coloredBody.toString()));
        }
        gradient.appendTail(expanded);
        text = expanded.toString();

        List<String> segments = new ArrayList<>();
        Matcher token = Pattern.compile("(?i)&#([0-9a-f]{6})|&([0-9a-fk-or])").matcher(text);
        String color = fallbackColor;
        boolean bold = false, italic = false, underlined = false, strikethrough = false, obfuscated = false;
        int start = 0;
        while (token.find()) {
            if (token.start() > start) {
                segments.add(hologramSegment(text.substring(start, token.start()), color, bold, italic, underlined, strikethrough, obfuscated));
            }
            if (token.group(1) != null) {
                color = "#" + token.group(1).toUpperCase(Locale.ROOT);
                bold = italic = underlined = strikethrough = obfuscated = false;
            } else {
                char code = Character.toLowerCase(token.group(2).charAt(0));
                switch (code) {
                    case 'l' -> bold = true;
                    case 'o' -> italic = true;
                    case 'n' -> underlined = true;
                    case 'm' -> strikethrough = true;
                    case 'k' -> obfuscated = true;
                    case 'r' -> { color = fallbackColor; bold = italic = underlined = strikethrough = obfuscated = false; }
                    default -> {
                        color = switch (code) {
                            case '0' -> "#000000"; case '1' -> "#0000AA"; case '2' -> "#00AA00";
                            case '3' -> "#00AAAA"; case '4' -> "#AA0000"; case '5' -> "#AA00AA";
                            case '6' -> "#FFAA00"; case '7' -> "#AAAAAA"; case '8' -> "#555555";
                            case '9' -> "#5555FF"; case 'a' -> "#55FF55"; case 'b' -> "#55FFFF";
                            case 'c' -> "#FF5555"; case 'd' -> "#FF55FF"; case 'e' -> "#FFFF55";
                            case 'f' -> "#FFFFFF"; default -> fallbackColor;
                        };
                        bold = italic = underlined = strikethrough = obfuscated = false;
                    }
                }
            }
            start = token.end();
        }
        if (start < text.length()) {
            segments.add(hologramSegment(text.substring(start), color, bold, italic, underlined, strikethrough, obfuscated));
        }
        if (segments.isEmpty()) return "{text:\"\"}";
        if (segments.size() == 1) return segments.get(0);
        return "{text:\"\",extra:[" + String.join(",", segments) + "]}";
    }

    static String hologramSegment(String value, String color, boolean bold, boolean italic,
                                          boolean underlined, boolean strikethrough, boolean obfuscated) {
        return "{text:" + snbtQuote(value) + ",color:" + snbtQuote(color)
                + ",bold:" + bold + ",italic:" + italic + ",underlined:" + underlined
                + ",strikethrough:" + strikethrough + ",obfuscated:" + obfuscated + "}";
    }
    static String tag(String id){return "abyssstats_"+id.replaceAll("[^a-zA-Z0-9_]","_");}
    static void command(String cmd){if(server!=null)try{server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),cmd);}catch(Exception e){System.err.println("[AbyssStats] command: "+e);}}
    static Set<String> hologramDimensions(){
        Set<String> dims=new LinkedHashSet<>(List.of("minecraft:overworld","minecraft:the_nether","minecraft:the_end"));
        for(Hologram h:holograms.values())if(h.world!=null)dims.add(h.world);
        for(Arena a:arenas.values())if(a.world!=null)dims.add(a.world);
        return dims;
    }
    static void refresh(){if(server==null)return;
        // Remove old display lines, including holograms deleted from config.
        Set<String> cleanupDimensions=hologramDimensions();cleanupDimensions.addAll(previousHologramDimensions);previousHologramDimensions.clear();previousHologramDimensions.addAll(cleanupDimensions);for (String dim : cleanupDimensions) command("execute in "+dim+" run kill @e[type=minecraft:text_display,tag=abyssstats_holo]");
        for(Hologram h:holograms.values()){
            Arena a=arenas.get(h.arena);if(a==null)continue;Map<String,String> cfg=config(h);int limit=10;try{limit=Math.max(1,Math.min(30,Integer.parseInt(cfg.getOrDefault("limit","10"))));}catch(Exception ignored){}
            List<Record> sorted=new ArrayList<>(a.players.values());sorted.removeIf(r->h.type.equals("kills") ? r.kills<=0 : r.deaths<=0);sorted.sort(Comparator.comparingInt((Record r)->h.type.equals("kills")?r.kills:r.deaths).reversed());
            List<String> lines=new ArrayList<>();lines.add(cfg.getOrDefault("title","&6TOP "+h.type.toUpperCase(Locale.ROOT)));
            for(int i=0;i<Math.min(limit,sorted.size());i++){Record r=sorted.get(i);lines.add(cfg.getOrDefault("line","&e#{rank} &f{name} &7- &6{value}").replace("{rank}",""+(i+1)).replace("{name}",r.name==null?"Unknown":r.name).replace("{value}",""+(h.type.equals("kills")?r.kills:r.deaths))));}
            if(sorted.isEmpty())lines.add(cfg.getOrDefault("empty","&7Nu exista statistici inca"));
            for(int i=0;i<lines.size();i++){
                String text=hologramComponent(lines.get(i), i==0 ? "#FFD700" : "#FFFFFF");String nbt="{Tags:[\"abyssstats_holo\",\""+tag(h.id)+"\"],billboard:\"center\",text:"+text+",see_through:1b,shadow:1b}";
                command("execute in "+h.world+" run summon minecraft:text_display "+h.x+" "+(h.y-i*0.27)+" "+h.z+" "+nbt);
            }
        }
    }
    static void load(){try{Files.createDirectories(DIR);Files.createDirectories(HOLOS);if(!Files.exists(DATA))return;State s=GSON.fromJson(Files.readString(DATA),State.class);arenas.clear();holograms.clear();if(s!=null){if(s.arenas!=null)arenas.putAll(s.arenas);if(s.holograms!=null)holograms.putAll(s.holograms);}}catch(Exception e){throw new IllegalStateException("AbyssStats nu poate citi data.json",e);}}
    static void save(){try{Files.createDirectories(DIR);State s=new State();s.arenas.putAll(arenas);s.holograms.putAll(holograms);Path tmp=DIR.resolve("data.json.tmp");Files.writeString(tmp,GSON.toJson(s),StandardCharsets.UTF_8);Files.move(tmp,DATA,StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){System.err.println("[AbyssStats] Salvare esuata: "+e);}}
}
