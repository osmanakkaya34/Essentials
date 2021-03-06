package essentials;

import arc.ApplicationListener;
import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.struct.Array;
import arc.util.Strings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import essentials.core.PlayerDB;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.core.GameState;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import mindustry.game.EventType.BlockBuildEndEvent;
import mindustry.game.EventType.BuildSelectEvent;
import mindustry.game.Gamemode;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.io.SaveIO;
import mindustry.maps.Map;
import mindustry.type.Item;
import mindustry.type.ItemType;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.logic.MessageBlock;
import org.codehaus.plexus.util.FileUtils;
import org.jsoup.Jsoup;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static essentials.Global.*;
import static essentials.Main.*;
import static essentials.core.Exp.exp;
import static essentials.core.PlayerDB.getData;
import static essentials.core.PlayerDB.writeData;
import static essentials.special.PingServer.pingServer;
import static essentials.utils.Config.*;
import static mindustry.Vars.*;

public class Threads extends TimerTask{
    public static String playtime;
    public static String uptime;
    static boolean peacetime;
    static ArrayList<String> nukeposition = new ArrayList<>();
    static ArrayList<Process> process = new ArrayList<>();

    @Override
    public void run() {
        // 플레이어 플탐 카운트
        new playtime().start();

        // 맵 플탐 카운트
        new maptime().start();

        // 서버 켜진시간 카운트
        if(uptime != null){
            try{
                Calendar cal1;
                SimpleDateFormat format = new SimpleDateFormat("HH:mm.ss");
                Date d2 = format.parse(uptime);
                cal1 = Calendar.getInstance();
                cal1.setTime(d2);
                cal1.add(Calendar.SECOND, 1);
                uptime = format.format(cal1.getTime());
            }catch (Exception e){
                printStackTrace(e);
            }
        }

        JsonObject data = new JsonObject();
        data.put("banned",banned);
        data.put("blacklist",blacklist);
        data.put("jumpzone",jumpzone);
        data.put("jumpall",jumpall);
        data.put("jumpcount",jumpcount);
        data.put("servername", Core.settings.getString("servername"));
        try {
            new ObjectMapper().writeValue(Core.settings.getDataDirectory().child("mods/Essentials/data/data.json").file(), data);
        } catch (IOException e) {
            printStackTrace(e);
        }

        // 투표 확인
        //executorService.execute(new checkvote());

        // 현재 서버 이름에다가 클라이언트 서버에 대한 인원 새기기
        // new changename().start();

        // 맵이 돌아가고 있을 때
        if(state.is(GameState.State.playing)) {
            // 서버간 이동 패드에 플레이어가 있는지 확인
            // new jumpzone().start();

            // 모든 클라이언트 서버에 대한 인원 총합 카운트
            new jumpall().start();

            // 냉각수 감시
            // executorService.execute(new checkthorium());

            // 메세지 블럭 감시
            new messagemonitoring().start();
        }
    }

    static class playtime extends Thread {
        @Override
        public void run(){
            try{
                if(playerGroup.size() > 0){
                    for(int i = 0; i < playerGroup.size(); i++) {
                        Player player = playerGroup.all().get(i);

                        if (isLogin(player)) {
                            JsonObject db = new JsonObject();
                            try {
                                db = getData(player.uuid);
                            } catch (Exception e) {
                                printStackTrace(e);
                            }
                            String data;
                            if (db.has("playtime")) {
                                data = db.getString("playtime");
                            } else {
                                return;
                            }
                            SimpleDateFormat format = new SimpleDateFormat("HH:mm.ss");
                            Date d1;
                            Calendar cal;
                            String newTime = null;
                            try {
                                d1 = format.parse(data);
                                cal = Calendar.getInstance();
                                cal.setTime(d1);
                                cal.add(Calendar.SECOND, 1);
                                newTime = format.format(cal.getTime());
                            } catch (ParseException e1) {
                                printStackTrace(e1);
                            }

                            // Exp caculating
                            int ex = db.getInt("exp");
                            int newexp = ex + (int) (Math.random() * 5);

                            writeData("UPDATE players SET exp = ?, playtime = ? WHERE uuid = ?", newexp, newTime, player.uuid);
                            if(!state.rules.editor){
                                exp(player.name, player.uuid);
                            }
                        }
                    }
                }
            }catch (Exception ex){
                printStackTrace(ex);
            }

        }
    }
    static class bantime extends Thread {
        @Override
        public void run(){
            Thread.currentThread().setName("Ban time monitoring thread");
            while(!Thread.currentThread().isInterrupted()) {
                try {
                    LocalDateTime now = LocalDateTime.now();
                    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yy-MM-dd a hh:mm.ss", Locale.ENGLISH);
                    SimpleDateFormat format = new SimpleDateFormat("yy-MM-dd a hh:mm.ss", Locale.ENGLISH);
                    Date myTime = format.parse(dateTimeFormatter.format(now));

                    for (int i = 0; i < banned.size(); i++) {
                        JsonObject value1 = banned.getObject(i);
                        Date d = format.parse(value1.getString("date"));

                        String uuid = value1.getString("uuid");
                        String name = value1.getString("name");

                        if (d.after(myTime)) {
                            banned.remove(i);
                            Data.getArray("banned").remove(i);
                            netServer.admins.unbanPlayerID(uuid);
                            nlog("log","[" + myTime + "] [Bantime]" + name + "/" + uuid + " player unbanned!");
                            break;
                        }
                    }
                } catch (Exception ex) {
                    printStackTrace(ex);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
    static class maptime extends Thread {
        @Override
        public void run(){
            if(playtime != null){
                try{
                    Calendar cal1;
                    SimpleDateFormat format = new SimpleDateFormat("HH:mm.ss");
                    Date d2 = format.parse(playtime);
                    cal1 = Calendar.getInstance();
                    cal1.setTime(d2);
                    cal1.add(Calendar.SECOND, 1);
                    playtime = format.format(cal1.getTime());
                    // Anti PvP rushing timer
                    if(config.isEnableantirush() && Vars.state.rules.pvp && cal1.after(config.getAntirushtime()) && peacetime) {
                        state.rules.playerDamageMultiplier = 0.66f;
                        state.rules.playerHealthMultiplier = 0.8f;
                        peacetime = false;
                        for(int i = 0; i < playerGroup.size(); i++) {
                            Player player = playerGroup.all().get(i);
                            player.sendMessage(bundle("pvp-peacetime"));
                            Call.onPlayerDeath(player);
                        }
                    }
                }catch (Exception e){
                    printStackTrace(e);
                }
            }
        }
    }
    static class jumpzone extends Thread {
        @Override
        public void run(){
            if (playerGroup.size() > 0) {
                for (int i=0;i<jumpzone.size();i++) {
                    String jumpdata = jumpzone.getString(i);
                    String[] data = jumpdata.split("/");
                    int startx = Integer.parseInt(data[0]);
                    int starty = Integer.parseInt(data[1]);
                    int tilex = Integer.parseInt(data[2]);
                    int tiley = Integer.parseInt(data[3]);
                    String serverip = data[4];

                    for(int ix = 0; ix < playerGroup.size(); ix++) {
                        Player player = playerGroup.all().get(ix);
                        if (player.tileX() > startx && player.tileX() < tilex) {
                            if (player.tileY() > starty && player.tileY() < tiley){
                                String resultIP = data[4];
                                int port = 6567;
                                if(data[4].contains(":") && Strings.canParsePostiveInt(data[4].split(":")[1])){
                                    resultIP = data[4].split(":")[0];
                                    port = Strings.parseInt(data[4].split(":")[1]);
                                }
                                Global.log("player-jumped", player.name, resultIP+":"+port);
                                Call.onConnect(player.con, serverip, port);
                            }
                        }
                    }
                }
            }
        }
    }
    public static class checkgrief extends Thread {
        Player player;

        int routercount;
        int breakcount;
        int conveyorcount;
        int impcount;

        int routerlimit;
        int breaklimit;
        int conveyorlimit;
        int implimit;

        ArrayList<Block> impblock = new ArrayList<>();
        ArrayList<Block> block = new ArrayList<>();

        public checkgrief(Player player){
            this.player = player;
        }

        @Override
        public void run() {
            Thread.currentThread().setName(player.name+" Player anti-grief thread");
            // 중요 건물 추가
            impblock.add(Blocks.thoriumReactor);
            impblock.add(Blocks.impactReactor);
            impblock.add(Blocks.blastDrill);
            impblock.add(Blocks.siliconSmelter);
            impblock.add(Blocks.cryofluidMixer);
            impblock.add(Blocks.oilExtractor);
            impblock.add(Blocks.spectre);
            impblock.add(Blocks.meltdown);
            impblock.add(Blocks.turbineGenerator);

            // 일반 블록 추가
            block.add(Blocks.phaseConduit);

            // 기본값 설정
            routercount = 0;
            breakcount = 0;
            conveyorcount = 0;
            impcount = 0;

            // 최대값 설정 (레벨비례)
            int level = getData(player.uuid).getInt("level");
            routerlimit = 10 + (level * 3);
            implimit = 6 + (level * 3);
            breaklimit = 25 + (level * 4);
            conveyorlimit = 30 + (level * 4);

            // 블럭 파괴 카운트
            Events.on(BuildSelectEvent.class, e -> {
                if (e.builder instanceof Player && e.builder.buildRequest() != null && !e.builder.buildRequest().block.name.matches(".*build.*") && e.builder == player) {
                    if (e.breaking) {
                        // 그냥 빠른파괴
                        breakcount++;
                        if(breakcount > breaklimit){
                            allsendMessage("grief-fast-destroy", ((Player)e.builder).name);
                        }
                        if(breakcount > breaklimit + 5){
                            Call.onKick(((Player) e.builder).con, nbundle("grief-detect-kick"));
                            allsendMessage("grief-detect", ((Player)e.builder).name);
                        }

                        // 중요 건물
                        for (Block value : impblock) {
                            if (e.builder.buildRequest().block == value) {
                                implimit++;
                                if (impcount > implimit) {
                                    allsendMessage("grief-fast-imp", ((Player)e.builder).name);
                                }
                            }
                            if(impcount > impcount + 3){
                                Call.onKick(((Player) e.builder).con, nbundle("grief-detect-kick"));
                                allsendMessage("grief-detect", ((Player)e.builder).name);
                            }
                        }

                        // 컨베이어
                        if (e.builder.buildRequest().block == Blocks.conveyor || e.builder.buildRequest().block == Blocks.titaniumConveyor) {
                            conveyorcount++;
                            if (conveyorcount > conveyorlimit) {
                                allsendMessage("grief-fast-conveyor", ((Player)e.builder).name);
                            }
                            if(conveyorcount > conveyorcount + 5){
                                Call.onKick(((Player) e.builder).con, nbundle("grief-detect-kick"));
                                allsendMessage("grief-detect", ((Player)e.builder).name);
                            }
                        }
                    }
                }
            });

            // Place count
            Events.on(BlockBuildEndEvent.class, e -> {
                if (!e.breaking && e.player != null && e.player.buildRequest() != null && !state.teams.get(e.player.getTeam()).cores.isEmpty() && e.player == player) {
                    if (e.player.buildRequest().block == Blocks.router) {
                        routercount++;
                        if (routercount > routerlimit) {
                            allsendMessage("grief-fast-router", e.player.name);
                        }
                        if(routercount > routerlimit + 5){
                            Call.onDeconstructFinish(e.tile, Blocks.air, e.player.id);
                            Call.onKick(e.player.con, nbundle("grief-detect-kick"));
                            allsendMessage("grief-detect", e.player.name);
                        }
                    }
                }
            });
            TimerTask timer = new TimerTask() {
                @Override
                public void run() {
                    routercount = 0;
                    breakcount = 0;
                    conveyorcount = 0;
                    impcount = 0;
                }
            };
            Timer timer1 = new Timer(true);
            timer1.scheduleAtFixedRate(timer, 20000, 20000);
            if(player == null){
                timer1.cancel();
                this.interrupt();
            }
        }
    }
    static class checkthorium extends Thread {
        Tile getNear(Tile tile, int count){
            int x = tile.x;
            int y = tile.y;
            Tile result;
            switch(count){
                case 0:
                    result = world.tile(x-1,y+2);
                    break;
                case 1:
                    result = world.tile(x,y+2);
                    break;
                case 2:
                    result = world.tile(x+1,y+2);
                    break;
                case 3:
                    result = world.tile(x+2,y+1);
                    break;
                case 4:
                    result = world.tile(x+2,y);
                    break;
                case 5:
                    result = world.tile(x+2,y-1);
                    break;
                case 6:
                    result = world.tile(x-1,y-2);
                    break;
                case 7:
                    result = world.tile(x,y-2);
                    break;
                case 8:
                    result = world.tile(x+1,y-2);
                    break;
                case 9:
                    result = world.tile(x-2,y-1);
                    break;
                case 10:
                    result = world.tile(x-2,y);
                    break;
                case 11:
                    result = world.tile(x-2,y+1);
                    break;
                default:
                    result = tile;
            }
            return result;
        }

        @Override
        public void run() {
            for (int a = 0; a < nukeposition.size(); a++) {
                String nukedata = nukeposition.get(a);
                String[] data = nukedata.split("/");
                int x = Integer.parseInt(data[0]);
                int y = Integer.parseInt(data[1]);
                Tile tile = world.tile(x, y);

                ArrayList<Tile> open = new ArrayList<>();
                ArrayList<Tile> close = new ArrayList<>();

                boolean success;

                if (world.tile(x, y).block() != Blocks.thoriumReactor) {
                    nukeposition.remove(a);
                    return;
                }
                // 12면을 검색함
                nlog("debug","SEARCH START");
                int count = 0;
                for (int b = 0; b < 12; b++) {
                    open.add(getNear(tile, b));
                }
                for(int b=0;b<open.size();b++){
                    Tile target = open.get(b);
                    if(target.block() == Blocks.air){
                        open.remove(b);
                        break;
                    }
                    for(int c=0;c<4;c++){
                        if(target.getNearby(c).block() == Blocks.conduit || target.getNearby(c).block() == Blocks.pulseConduit){
                            open.add(target.getNearby(c));
                        } else if (target.getNearby(c).block() == Blocks.cryofluidMixer) {

                        }
                    }
                    // 파이프의 4면을 검색함
                    while (count < 10) {
                        for (int c = 0; c < 4; c++) {
                            nlog("debug",target.x+"/"+target.y);
                            // 파이프를 발견했다면
                            if (target.getNearby(c).block() == Blocks.conduit || target.getNearby(c).block() == Blocks.pulseConduit) {
                                target = target.getNearby(c);
                            } else if (target.getNearby(c).block() == Blocks.cryofluidMixer) {
                                nlog("debug","냉각수 공장 발견");
                                count = 100;
                            }
                        }
                        count++;
                        //Global.normal(count + " 번째 " + target.x + "/" + target.y);
                    }
                }
            }
        }
    }
    static class login extends TimerTask{
        @Override
        public void run() {
            Thread.currentThread().setName("Login alert thread");
            if (playerGroup.size() > 0) {
                for(int i = 0; i < playerGroup.size(); i++) {
                    Player player = playerGroup.all().get(i);
                    if (isNocore(player)) {
                        try {
                            String message;
                            String json = Jsoup.connect("http://ipapi.co/" + Vars.netServer.admins.getInfo(player.uuid).lastIP + "/json").ignoreContentType(true).execute().body();
                            JsonObject result = JsonParser.object().from(json);
                            String language = result.getString("languages") == null ? "en" : result.getString("languages");
                            if (config.getPasswordmethod().equals("discord")) {
                                message = nbundle(language, "login-require-discord") + "\n" + config.getDiscordLink();
                            } else {
                                message = nbundle(language, "login-require-password");
                            }
                            player.sendMessage(message);
                        }catch (Exception e){
                            printStackTrace(e);
                        }
                    }
                }
            }
        }
    }
    static class jumpcheck extends Thread {
        // Source from Anuken/CoreBot
        @Override
        public void run() {
            Thread.currentThread().setName("Server to server thread");
            while(!Thread.currentThread().isInterrupted()) {
                if(state.is(GameState.State.playing)) {
                    for (int i = 0; i < jumpcount.size(); i++) {
                        String jumpdata = jumpcount.getString(i);
                        String[] data = jumpdata.split("/");
                        String serverip = data[0];
                        int x = Integer.parseInt(data[1]);
                        int y = Integer.parseInt(data[2]);
                        String count = data[3];
                        int length = Integer.parseInt(data[4]);

                        int i2 = i;
                        pingServer(serverip, result -> {
                            if (result.name != null) {
                                String str = String.valueOf(result.players);
                                int[] digits = new int[str.length()];
                                for (int a = 0; a < str.length(); a++) digits[a] = str.charAt(a) - '0';

                                Tile tile = world.tile(x, y);
                                if (!count.equals(str)) {
                                    if (length != digits.length) {
                                        for (int px = 0; px < 3; px++) {
                                            for (int py = 0; py < 5; py++) {
                                                Call.onDeconstructFinish(world.tile(tile.x + 4 + px, tile.y + py), Blocks.air, 0);
                                            }
                                        }
                                    }
                                    for (int digit : digits) {
                                        setcount(tile, digit);
                                        tile = world.tile(tile.x + 4, tile.y);
                                    }
                                } else {
                                    for (int l = 0; l < length; l++) {
                                        setcount(tile, digits[l]);
                                        tile = world.tile(x + 4, y);
                                    }
                                }
                                // i 번째 server ip, 포트, x좌표, y좌표, 플레이어 인원, 플레이어 인원 길이
                                jumpcount.set(i2, serverip + "/" + x + "/" + y + "/" + result.players + "/" + digits.length);
                            } else {
                                setno(world.tile(x, y));
                            }
                        });
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
    static class jumpall extends Thread {
        @Override
        public void run() {
            for (int i=0;i<jumpall.size();i++) {
                String jumpdata = jumpall.getString(i);
                String[] data = jumpdata.split("/");
                int x = Integer.parseInt(data[0]);
                int y = Integer.parseInt(data[1]);
                int count = Integer.parseInt(data[2]);
                int length = Integer.parseInt(data[3]);

                int result = 0;
                for (int a=0;i<jumpcount.size();a++) {
                    String dat = jumpcount.getString(a);
                    String[] re = dat.split("/");
                    result += Integer.parseInt(re[3]);
                }

                String str = String.valueOf(result);
                int[] digits = new int[str.length()];
                for(int a = 0; a < str.length(); a++) digits[a] = str.charAt(a) - '0';

                Tile tile = world.tile(x, y);
                if(count != result) {
                    if(length != digits.length){
                        for(int px=0;px<3;px++){
                            for(int py=0;py<5;py++){
                                Call.onDeconstructFinish(world.tile(tile.x+4+px,tile.y+py), Blocks.air, 0);
                            }
                        }
                    }
                    for (int digit : digits) {
                        setcount(tile, digit);
                        tile = world.tile(tile.x+4, tile.y);
                    }
                } else {
                    for(int l=0;l<length;l++) {
                        setcount(tile, digits[l]);
                        tile = world.tile(x+4, y);
                    }
                }
                jumpall.set(i, x+"/"+y+"/"+result+"/"+digits.length);
            }
        }
    }
    static class changename extends Thread {
        @Override
        public void run(){
            if(jumpcount.size() > 1){
                int result = 0;
                for (int a=0;a<jumpcount.size();a++) {
                    String dat = jumpcount.getString(a);
                    String[] re = dat.split("/");
                    result += Integer.parseInt(re[3]);
                }
                Core.settings.put("servername", config.getServername()+", "+result+" players");
            }
        }
    }
    public static class AutoRollback extends TimerTask {
        private void save() {
            try {
                Fi file = saveDirectory.child(config.getSlotnumber() + "." + saveExtension);
                SaveIO.save(file);
            } catch (Exception e) {
                printStackTrace(e);
            }
        }

        void load() {
            Array<Player> all = Vars.playerGroup.all();
            Array<Player> players = new Array<>();
            players.addAll(all);

            try {
                Fi file = saveDirectory.child(config.getSlotnumber() + "." + saveExtension);
                SaveIO.load(file);
            } catch (SaveIO.SaveException e) {
                printStackTrace(e);
            }

            Call.onWorldDataBegin();

            for (Player p : players) {
                Vars.netServer.sendWorldData(p);
                p.reset();

                if (Vars.state.rules.pvp) {
                    p.setTeam(Vars.netServer.assignTeam(p, new Array.ArrayIterable<>(players)));
                }
            }
            nlog("log","Map rollbacked.");
            Call.sendMessage("[green]Map rollbacked.");
        }

        @Override
        public void run() {
            save();
        }
    }
    static class eventserver extends Thread {
        String roomname;
        String map;
        String gamemode;
        int customport;

        eventserver(String roomname, String map, String gamemode, int customport){
            this.gamemode = gamemode;
            this.map = map;
            this.roomname = roomname;
            this.customport = customport;
        }

        @Override
        public void run() {
            try {
                FileUtils.copyURLToFile(new URL("https://github.com/Anuken/Mindustry/releases/download/v102/server-release.jar"), new File(Paths.get("").toAbsolutePath().toString()+"/config/mods/Essentials/temp/"+roomname+"/server.jar"));
                Service service = new Service(roomname, map, gamemode, customport);
                service.start();
                Thread.sleep(10000);
            } catch (Exception e) {
                printStackTrace(e);
            }
        }

        public static class Service extends Thread {
            String roomname;
            String map;
            String gamemode;
            int customport;
            int disablecount;

            Service(String roomname, String map, String gamemode, int customport) {
                this.gamemode = gamemode;
                this.map = map;
                this.roomname = roomname;
                this.customport = customport;
            }

            @Override
            public void run(){
                try {
                    Process p;
                    ProcessBuilder pb;
                    if(gamemode.equals("wave")){
                        pb = new ProcessBuilder("java", "-jar", Paths.get("").toAbsolutePath().toString() + "/config/mods/Essentials/temp/" + roomname + "/server.jar", "config port "+customport+",host "+map);
                    } else {
                        pb = new ProcessBuilder("java", "-jar", Paths.get("").toAbsolutePath().toString() + "/config/mods/Essentials/temp/" + roomname + "/server.jar", "config port "+customport+",host "+map+" "+gamemode);
                    }
                    pb.directory(new File(Paths.get("").toAbsolutePath().toString() + "/config/mods/Essentials/temp/" + roomname));
                    pb.inheritIO().redirectOutput(Core.settings.getDataDirectory().child("test.txt").file());
                    p = pb.start();
                    process.add(p);
                    if(p.isAlive()) nlog("log","online");
                    Process finalP = p;
                    TimerTask t = new TimerTask() {
                        @Override
                        public void run() {
                            pingServer("localhost", result -> {
                                if (disablecount > 300) {
                                    try {
                                        JsonObject settings = JsonParser.object().from(Core.settings.getDataDirectory().child("mods/Essentials/data/data.json").readString());
                                        for (int a = 0; a < settings.getArray("servers").size(); a++) {
                                            if (settings.getArray("servers").getObject(a).getInt("port") == customport) {
                                                settings.getArray("servers").remove(a);
                                                Core.settings.getDataDirectory().child("mods/Essentials/data/data.json").writeString(settings.toString());
                                                break;
                                            }
                                        }

                                        finalP.destroy();
                                        process.remove(finalP);
                                        this.cancel();
                                    } catch (JsonParserException e) {
                                        printStackTrace(e);
                                    }
                                } else if (result.players == 0) {
                                    disablecount++;
                                }
                            });
                        }
                    };
                    Timer timer = new Timer(true);
                    timer.scheduleAtFixedRate(t, 1000, 1000);

                    Core.app.addListener(new ApplicationListener(){
                        @Override
                        public void dispose(){
                            timer.cancel();
                        }
                    });
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    }
    public static class ColorNick implements Runnable{
        private static int colorOffset = 0;
        private static long updateIntervalMs = config.getCupdatei();
        Player player;

        public ColorNick(Player player){
            this.player = player;
        }

        @Override
        public void run() {
            Thread.currentThread().setName(player.name+" color nickname thread");
            JsonObject db = getData(player.uuid);
            boolean connected = db.getBoolean("connected");
            while (connected) {
                connected = db.getBoolean("connected");
                String name = db.getString("name").replaceAll("\\[(.*?)]", "");
                try {
                    Thread.sleep(updateIntervalMs);
                    nickcolor(name, player);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        private void nickcolor(String name, Player player) {
            StringBuilder stringBuilder = new StringBuilder();

            String[] colors = new String[11];
            colors[0] = "[#ff0000]";
            colors[1] = "[#ff7f00]";
            colors[2] = "[#ffff00]";
            colors[3] = "[#7fff00]";
            colors[4] = "[#00ff00]";
            colors[5] = "[#00ff7f]";
            colors[6] = "[#00ffff]";
            colors[7] = "[#007fff]";
            colors[8] = "[#0000ff]";
            colors[9] = "[#8000ff]";
            colors[10] = "[#ff00ff]";

            String[] newnick = new String[name.length()];
            for (int i = 0; i<name.length(); i++) {
                char c = name.charAt(i);
                int colorIndex = (i+colorOffset)%colors.length;
                if (colorIndex < 0) {
                    colorIndex += colors.length;
                }
                String newtext = colors[colorIndex]+c;
                newnick[i]=newtext;
            }
            colorOffset--;
            for (String s : newnick) {
                stringBuilder.append(s);
            }
            player.name = stringBuilder.toString();
        }
    }
    static class monitorresource extends Thread {
        Array<Integer> pre = new Array<>();
        Array<Integer> cur = new Array<>();
        Array<Item> name = new Array<>();

        @Override
        public void run(){
            Thread.currentThread().setName("Resource monitoring thread");
            while(Thread.currentThread().isInterrupted()) {
                if(state.is(GameState.State.playing)) {
                    for (Item item : content.items()) {
                        if (item.type == ItemType.material) {
                            pre.add(state.teams.get(Team.sharded).cores.first().items.get(item));
                        }
                    }

                    for (Item item : content.items()) {
                        if (item.type == ItemType.material) {
                            name.add(item);
                        }
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }

                    int a = 0;
                    for (Item item : content.items()) {
                        if (item.type == ItemType.material) {
                            int resource;
                            if (state.teams.get(Team.sharded).cores.isEmpty()) return;
                            if (state.teams.get(Team.sharded).cores.first().items.has(item)) {
                                resource = state.teams.get(Team.sharded).cores.first().items.get(item);
                            } else {
                                return;
                            }
                            int temp = resource - pre.get(a);
                            if (temp <= -55) {
                                StringBuilder using = new StringBuilder();
                                if(Vars.state.is(GameState.State.playing)) {
                                    for (int b = 0; b < playerGroup.size(); b++) {
                                        Player p = playerGroup.all().get(b);
                                        if (p.buildRequest().block == null) return;
                                        for (int c = 0; c < p.buildRequest().block.requirements.length; c++) {
                                            Item ad = p.buildRequest().block.requirements[c].item;
                                            if (ad == name.get(a)) {
                                                using.append(p.name).append(", ");
                                            }
                                        }
                                    }
                                    allsendMessage("resource-fast", name.get(a).name);
                                    allsendMessage("resource-fast-use", name.get(a).name, using.substring(0, using.length() - 2));
                                }
                            }
                            cur.add(a, state.teams.get(Team.sharded).cores.first().items.get(item));
                            a++;
                        }
                    }

                    for (Item item : content.items()) {
                        if (item.type == ItemType.material) {
                            pre.add(state.teams.get(Team.sharded).cores.first().items.get(item));
                        }
                    }
                } else {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }
    }
    public static class Vote{
        private static Player player;
        private static Player target;
        private static Map map;
        private static String type;
        private static Timer votetimer = new Timer();
        private static Timer bundletimer = new Timer();

        private static int time = 0;
        private static int bundletime = 0;

        public static boolean isvoting;
        static ArrayList<String> list = new ArrayList<>();
        static int require;

        Vote(Player player, String type, Player target){
            Vote.player = player;
            Vote.type = type;
            Vote.target = target;
        }

        Vote(Player player, String type, Map map){
            Vote.player = player;
            Vote.type = type;
            Vote.map = map;
        }

        Vote(Player player, String type){
            Vote.player = player;
            Vote.type = type;
        }

        // 1초마다 실행됨
        TimerTask counting = new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName("Vote counting timertask");
                time++;
                if(time >= 60){
                    Vote.cancel();
                }
            }
        };

        // 10초마다 실행됨
        TimerTask alert = new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName("Vote alert timertask");
                String[] bundlename = {"vote-50sec", "vote-40sec", "vote-30sec", "vote-20sec", "vote-10sec"};

                if(bundletime <= 4){
                    if (playerGroup != null && playerGroup.size() > 0) {
                        allsendMessage(bundlename[bundletime]);
                    }
                    bundletime++;
                }
            }
        };

        static void cancel() {
            isvoting = false;

            votetimer.cancel();
            votetimer = new Timer();
            time = 0;

            bundletimer.cancel();
            bundletimer = new Timer();
            bundletime = 0;

            switch (type) {
                case "gameover":
                    if (list.size() >= require) {
                        allsendMessage("vote-gameover-done");
                        Events.fire(new EventType.GameOverEvent(Team.crux));
                    } else {
                        allsendMessage("vote-gameover-fail");
                    }
                    break;
                case "skipwave":
                    if (list.size() >= require) {
                        allsendMessage("vote-skipwave-done");
                        for (int i = 0; i < 5; i++) {
                            logic.runWave();
                        }
                    } else {
                        allsendMessage("vote-skipwave-fail");
                    }
                    break;
                case "kick":
                    if (list.size() >= require) {
                        allsendMessage("vote-kick-done", target.name);
                        PlayerDB.addtimeban(target.name, target.uuid, 4);
                        log("player",target.name + " / " + target.uuid + " Player has banned due to voting. " + list.size() + "/" + require);

                        Path path = Paths.get(String.valueOf(Core.settings.getDataDirectory().child("mods/Essentials/Logs/Player.log")));
                        Path total = Paths.get(String.valueOf(Core.settings.getDataDirectory().child("mods/Essentials/Logs/Total.log")));
                        try {
                            JsonObject other = getData(target.uuid);
                            String text = other.get("name") + " / " + target.uuid + " Player has banned due to voting. " + list.size() + "/" + require + "\n";
                            byte[] result = text.getBytes();
                            Files.write(path, result, StandardOpenOption.APPEND);
                            Files.write(total, result, StandardOpenOption.APPEND);
                        } catch (IOException error) {
                            printStackTrace(error);
                        }

                        netServer.admins.banPlayer(target.uuid);
                        target.con.kick("You're kicked.");
                    } else {
                        allsendMessage("vote-kick-fail");
                    }
                    break;
                case "rollback":
                    if (list.size() >= require) {
                        allsendMessage("vote-rollback-done");
                        Threads.AutoRollback rl = new Threads.AutoRollback();
                        rl.load();
                    } else {
                        allsendMessage("vote-rollback-fail");
                    }
                    break;
                case "map":
                    if (list.size() >= require) {
                        Array<Player> all = Vars.playerGroup.all();
                        Array<Player> players = new Array<>();
                        players.addAll(all);

                        Gamemode current = Gamemode.survival;
                        if(state.rules.attackMode){
                            current = Gamemode.attack;
                        } else if(state.rules.pvp){
                            current = Gamemode.pvp;
                        } else if(state.rules.editor){
                            current = Gamemode.editor;
                        }

                        world.loadMap(map, map.applyRules(current));

                        Call.onWorldDataBegin();

                        for (Player p : players) {
                            Vars.netServer.sendWorldData(p);
                            p.reset();

                            if (Vars.state.rules.pvp) {
                                p.setTeam(Vars.netServer.assignTeam(p, new Array.ArrayIterable<>(players)));
                            }
                        }
                        nlog("log","Map rollbacked.");
                        allsendMessage("vote-map-done");
                    } else {
                        allsendMessage("vote-map-fail");
                    }
                    break;
            }
            list.clear();
        }

        void command(){
            if(playerGroup.size() == 1){
                player.sendMessage(bundle(player, "vote-min"));
                return;
            } else if(playerGroup.size() <= 3){
                require = 2;
            } else {
                require = (int) Math.ceil((double) playerGroup.size() / 2);
            }

            if(!isvoting){
                switch (type){
                    case "gameover":
                        allsendMessage("vote-gameover");
                        break;
                    case "skipwave":
                        allsendMessage("vote-skipwave");
                        break;
                    case "kick":
                        allsendMessage("vote-kick", target.name);
                        break;
                    case "rollback":
                        allsendMessage("vote-rollback");
                        break;
                    case "map":
                        allsendMessage("vote-map");
                        break;
                    default:
                        // 모드가 잘못되었을 때
                        player.sendMessage("wrong mode");
                        return;
                }
                isvoting = true;
                votetimer.schedule(counting, 0, 1000);
                bundletimer.schedule(alert, 10000, 10000);
            } else {
                player.sendMessage(bundle("vote-in-processing"));
            }
        }
    }
    public static class getip{
        public String main(){
            try{
                URL whatismyip = new URL("http://checkip.amazonaws.com");
                BufferedReader in = new BufferedReader(new InputStreamReader(whatismyip.openStream()));
                return in.readLine();
            }catch (Exception e){
                e.printStackTrace();
                return "127.0.0.1";
            }
        }
    }
    static class messagemonitoring extends Thread{
        @Override
        public void run(){
            for(int a=0;a<messagemonitor.size();a++) {
                String[] xy = messagemonitor.get(a).split("\\|");
                int x = Integer.parseInt(xy[0]);
                int y = Integer.parseInt(xy[1]);

                String msg;
                try {
                    MessageBlock.MessageBlockEntity entity = (MessageBlock.MessageBlockEntity) world.tile(x, y).entity;
                    msg = entity.message;
                }catch (NullPointerException e){
                    messagemonitor.remove(a);
                    return;
                }

                if (msg.equals("powerblock")) {
                    powerblock(world.tile(x, y));
                    messagemonitor.remove(a);
                    break;
                } else if (msg.contains("jump")) {
                    messagejump.add(x + "|" + y + "|" + msg);
                    messagemonitor.remove(a);
                    break;
                } else if (msg.equals("scancore")) {
                    scancore.add(world.tile(x, y));
                    messagemonitor.remove(a);
                    break;
                }
            }
        }

        void powerblock(Tile tile){
            try {
                int x = tile.x;
                int y = tile.y;
                int target_x;
                int target_y;

                if (tile.getNearby(0).entity != null) {
                    target_x = tile.getNearby(0).x;
                    target_y = tile.getNearby(0).y;
                } else if (tile.getNearby(1).entity != null) {
                    target_x = tile.getNearby(1).x;
                    target_y = tile.getNearby(1).y;
                } else if (tile.getNearby(2).entity != null) {
                    target_x = tile.getNearby(2).x;
                    target_y = tile.getNearby(2).y;
                } else if (tile.getNearby(3).entity != null) {
                    target_x = tile.getNearby(3).x;
                    target_y = tile.getNearby(3).y;
                } else {
                    return;
                }
                powerblock.add(x + "/" + y + "/" + target_x + "/" + target_y);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
    public static class jumpdata extends Thread{
        @Override
        public void run() {
            while(!Thread.currentThread().isInterrupted()) {
                for (int a = 0; a < messagejump.size(); a++) {
                    String[] xy = messagejump.get(a).split("\\|");
                    int x = Integer.parseInt(xy[0]);
                    int y = Integer.parseInt(xy[1]);
                    String message = xy[2];

                    if(world.tile(x,y).entity.block != Blocks.message){
                        messagejump.remove(a);
                        break;
                    }
                    Call.setMessageBlockText(null, world.tile(x, y), "[green]Working...");

                    String[] arr = message.split(" ");
                    String ip = arr[1];

                    pingServer(ip, result -> {
                        if (result.name != null){
                            Call.setMessageBlockText(null, world.tile(x, y), "[green]"+result.players + " Players in this server.");
                        } else {
                            Call.setMessageBlockText(null, world.tile(x, y), "[scarlet]Server offline");
                        }
                    });
                }
                try {
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                    printStackTrace(e);
                }
            }
        }
    }
    public static class visualjump extends Thread{
        int length;
        ArrayList<Thread> thread = new ArrayList<>();

        @Override
        public void run() {
            main();

            while(!Thread.currentThread().isInterrupted()) {
                try {
                    if (length != jumpzone.size()) {
                        for (Thread value : thread) {
                            value.interrupt();
                        }
                        thread.clear();
                        sleep(3000);
                        main();
                    } else {
                        sleep(3000);
                    }
                } catch (InterruptedException ignored) {}
            }
        }

        public void main(){
            length = jumpzone.size();

            for (int b = 0; b < jumpzone.size(); b++) {
                String[] data = jumpzone.getString(b).split("/");
                Thread t = new Thread(() -> {
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            String ip = data[4];
                            AtomicBoolean online = new AtomicBoolean(false);
                            pingServer(ip,result->{if(result.name != null) online.set(true);});
                            if(online.get()) {
                                int xt = Integer.parseInt(data[0]);
                                int yt = Integer.parseInt(data[1]);
                                int tilexfinal = Integer.parseInt(data[2]) - 1;
                                int tileyfinal = Integer.parseInt(data[3]) - 1;
                                int size = tilexfinal - xt;

                                for (int x = 0; x < size; x++) {
                                    Tile tile = world.tile(xt + x, yt);
                                    Call.onConstructFinish(tile, Blocks.air, 0, (byte) 0, Team.sharded, true);
                                    sleep(96);
                                }
                                for (int y = 0; y < size; y++) {
                                    Tile tile = world.tile(tilexfinal, yt + y);
                                    Call.onConstructFinish(tile, Blocks.air, 0, (byte) 0, Team.sharded, true);
                                    sleep(96);
                                }
                                for (int x = 0; x < size; x++) {
                                    Tile tile = world.tile(tilexfinal - x, tileyfinal);
                                    Call.onConstructFinish(tile, Blocks.air, 0, (byte) 0, Team.sharded, true);
                                    sleep(96);
                                }
                                for (int y = 0; y < size; y++) {
                                    Tile tile = world.tile(xt, tileyfinal - y);
                                    Call.onConstructFinish(tile, Blocks.air, 0, (byte) 0, Team.sharded, true);
                                    sleep(96);
                                }
                            } else {
                                nlog("debug","jump zone"+ip+" offline! After 30 seconds, try to connect again.");
                                sleep(30000);
                            }
                        }
                    } catch (InterruptedException ignored) {}
                });
                thread.add(t);
                t.start();
            }
        }
    }
}