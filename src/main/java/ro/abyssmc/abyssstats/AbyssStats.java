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
            for(int i=0;i<Math.min(limit,sorted.size());i++){Record r=sorted.get(i);lines.add(cfg.getOrDefault("line","&e#{rank} &f{name} &7- &6{value}").replace("{rank}",""+(i+1)).replace("{name}",r.name==null?"Unknown":r.name).replace("{value}",""+(h.type.equals("kills")?r.kills:r.deaths)));}
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
