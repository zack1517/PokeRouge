package com.bao01.save;

import com.bao01.model.Bag;
import com.bao01.model.Battle;
import com.bao01.model.Item;
import com.bao01.model.Pokemon;
import com.bao01.model.Stat;
import com.bao01.model.Team;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 存档管理器：负责捕获运行状态为 {@link SaveData.World}、恢复回
 * {@code Team} / {@code Battle}，以及文本编解码与磁盘读写。
 *
 * <p>文本格式为自定义的稳定行格式（UTF-8），无需第三方序列化依赖。
 * 各行以固定标记开头；字段用竖线分隔；空行与 # 注释行会被忽略。
 */
public final class SaveManager {

    private static final String HEADER = "BAO01SAVE";

    private SaveManager() {
    }

    // ---------- 捕获 ----------

    /** 世界存档（不在对局中）：保存玩家队伍 + 背包。 */
    public static SaveData.World captureWorld(Team playerTeam) {
        return new SaveData.World(SaveData.FORMAT_VERSION,
                SaveData.TeamData.capture(playerTeam), null);
    }

    /** 对局中存档：额外保存战斗快照（对局已结束时 battle 部分为空）。 */
    public static SaveData.World captureBattle(Battle battle) {
        SaveData.BattleData data = battle.isOver() ? null : SaveData.BattleData.capture(battle);
        return new SaveData.World(SaveData.FORMAT_VERSION,
                SaveData.TeamData.capture(battle.player()), data);
    }

    // ---------- 恢复 ----------

    /** 从世界存档重建玩家队伍（含背包）。 */
    public static Team restorePlayerTeam(SaveData.World world) {
        if (world == null) {
            throw new SaveException("存档为空");
        }
        return world.playerTeam().restore();
    }

    /** 从世界存档恢复一场进行中的对局（存档必须包含战斗快照）。 */
    public static Battle restoreBattle(SaveData.World world) {
        if (world == null || !world.hasBattle()) {
            throw new SaveException("存档中不包含战斗快照");
        }
        SaveData.BattleData d = world.battle();
        Team player = world.playerTeam().restore();
        Team foe = d.foeTeam().restore();
        return Battle.resumed(player, foe, d.opponentEnum(), d.roundNo(),
                d.weatherEnum(), d.weatherTurnsLeft(),
                d.playerPendingSendout(), d.playerKoSwitchPending(), d.history());
    }

    // ---------- 编解码 ----------

    /** 世界存档 → 文本。 */
    public static String toText(SaveData.World world) {
        if (world == null) {
            throw new SaveException("存档为空");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        sb.append("VERSION ").append(world.version()).append('\n');
        writeTeam(sb, "PT", world.playerTeam());
        if (world.hasBattle()) {
            SaveData.BattleData d = world.battle();
            sb.append("BATTLE 1\n");
            sb.append("OPP ").append(d.opponent()).append('\n');
            sb.append("ROUND ").append(d.roundNo()).append('\n');
            sb.append("WEATHER ").append(d.weather()).append(' ')
                    .append(d.weatherTurnsLeft()).append('\n');
            sb.append("PEND ").append(bool01(d.playerPendingSendout())).append(' ')
                    .append(bool01(d.playerKoSwitchPending())).append('\n');
            writeTeam(sb, "FT", d.foeTeam());
            sb.append("HIST ").append(d.history().size()).append('\n');
            for (String line : d.history()) {
                sb.append("H ").append(line).append('\n');
            }
        } else {
            sb.append("BATTLE 0\n");
        }
        return sb.toString();
    }

    /** 文本 → 世界存档。 */
    public static SaveData.World fromText(String text) {
        if (text == null || text.isBlank()) {
            throw new SaveException("存档文本为空");
        }
        List<String> lines = new ArrayList<>();
        for (String s : text.split("\r?\n", -1)) {
            String t = s.trim();
            if (!t.isEmpty() && !t.startsWith("#")) {
                lines.add(t);
            }
        }
        if (lines.isEmpty() || !HEADER.equals(lines.get(0))) {
            throw new SaveException("不是有效的宝可梦存档（缺少头标记）");
        }
        Cursor cur = new Cursor(lines);

        int version = parseInt(stripPrefix(cur.next(), "VERSION "), "版本号");
        if (version > SaveData.FORMAT_VERSION) {
            throw new SaveException("存档版本过新（" + version + " > " + SaveData.FORMAT_VERSION + "）");
        }

        SaveData.TeamData playerTeam = readTeam(cur, "PT");
        String battleLine = cur.next();
        SaveData.BattleData battle;
        if ("BATTLE 1".equals(battleLine)) {
            String opp = stripPrefix(cur.next(), "OPP ");
            int round = parseInt(stripPrefix(cur.next(), "ROUND "), "回合数");
            String[] w = split2(stripPrefix(cur.next(), "WEATHER "));
            String weather = w[0];
            int turns = parseInt(w[1], "天气剩余回合");
            String[] pend = split2(stripPrefix(cur.next(), "PEND "));
            boolean pendingSendout = parse01(pend[0], "等待派出");
            boolean pendingKo = parse01(pend[1], "等待击倒轮换");
            SaveData.TeamData foeTeam = readTeam(cur, "FT");
            String histLine = cur.next();
            if (!histLine.startsWith("HIST ")) {
                throw new SaveException("期望 HIST 标记，实际: " + histLine);
            }
            int histCount = parseInt(histLine.substring("HIST ".length()), "历史条数");
            List<String> history = new ArrayList<>();
            for (int i = 0; i < histCount; i++) {
                history.add(stripPrefix(cur.next(), "H "));
            }
            battle = new SaveData.BattleData(opp, round, weather, turns,
                    pendingSendout, pendingKo, foeTeam, history);
        } else if ("BATTLE 0".equals(battleLine)) {
            battle = null;
        } else {
            throw new SaveException("期望 BATTLE 标记，实际: " + battleLine);
        }
        if (cur.hasNext()) {
            throw new SaveException("存档存在多余内容: " + cur.next());
        }
        return new SaveData.World(version, playerTeam, battle);
    }

    // ---------- 文本写出 ----------

    private static void writeTeam(StringBuilder sb, String tag, SaveData.TeamData team) {
        sb.append(tag).append(' ').append(team.members().size()).append(' ')
                .append(team.activeIndex()).append('\n');
        for (SaveData.PokemonData pd : team.members()) {
            sb.append("POKE ").append(pd.species()).append('|').append(pd.level())
                    .append('|').append(pd.hp()).append('|').append(stagesCsv(pd.stages())).append('\n');
        }
        sb.append("BAG ").append(team.bag().size()).append('\n');
        for (SaveData.ItemData id : team.bag()) {
            sb.append("ITEM ").append(id.itemName()).append('|').append(id.count()).append('\n');
        }
    }

    private static String stagesCsv(int[] stages) {
        StringBuilder sb = new StringBuilder();
        int n = Stat.values().length;
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(i < stages.length ? stages[i] : 0);
        }
        return sb.toString();
    }

    // ---------- 文本解析 ----------

    private static SaveData.TeamData readTeam(Cursor cur, String tag) {
        String header = cur.next();
        if (!header.startsWith(tag + " ")) {
            throw new SaveException("期望 " + tag + " 标记，实际: " + header);
        }
        String[] head = header.substring((tag + " ").length()).split(" ", -1);
        int memberCount = parseInt(head[0], tag + " 成员数");
        int activeIndex = head.length > 1 ? parseInt(head[1], tag + " 出战位") : 0;

        List<SaveData.PokemonData> members = new ArrayList<>();
        for (int i = 0; i < memberCount; i++) {
            String line = cur.next();
            if (!line.startsWith("POKE ")) {
                throw new SaveException("期望 POKE 记录，实际: " + line);
            }
            String[] f = line.substring("POKE ".length()).split("\\|", -1);
            if (f.length < 4) {
                throw new SaveException("POKE 字段不足: " + line);
            }
            int n = Stat.values().length;
            int[] stages = new int[n];
            String[] parts = f[3].isEmpty() ? new String[0] : f[3].split(",");
            for (int j = 0; j < n; j++) {
                stages[j] = j < parts.length ? parseInt(parts[j], "能力等级") : 0;
            }
            members.add(new SaveData.PokemonData(f[0], parseInt(f[1], "等级"),
                    parseInt(f[2], "HP"), stages));
        }

        String bagLine = cur.next();
        if (!bagLine.startsWith("BAG ")) {
            throw new SaveException("期望 BAG 标记，实际: " + bagLine);
        }
        int bagCount = parseInt(bagLine.substring("BAG ".length()), "背包条数");
        List<SaveData.ItemData> bag = new ArrayList<>();
        for (int i = 0; i < bagCount; i++) {
            String line = cur.next();
            if (!line.startsWith("ITEM ")) {
                throw new SaveException("期望 ITEM 记录，实际: " + line);
            }
            String[] f = line.substring("ITEM ".length()).split("\\|", -1);
            if (f.length < 2) {
                throw new SaveException("ITEM 字段不足: " + line);
            }
            bag.add(new SaveData.ItemData(f[0], parseInt(f[1], "道具数量")));
        }
        return new SaveData.TeamData(members, activeIndex, bag);
    }

    // ---------- 磁盘 IO ----------

    /** 默认存档位置：用户主目录下的 .bao01/save.txt。 */
    public static Path defaultPath() {
        return Path.of(System.getProperty("user.home", "."), ".bao01", "save.txt");
    }

    /** 写入磁盘（自动创建父目录）。 */
    public static void writeToFile(Path path, SaveData.World world) {
        if (path == null) {
            throw new SaveException("存档路径为空");
        }
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, toText(world), StandardCharsets.UTF_8);
        } catch (SaveException e) {
            throw e;
        } catch (Exception e) {
            throw new SaveException("写入存档失败: " + path, e);
        }
    }

    /** 从磁盘读取并解析。 */
    public static SaveData.World readFromFile(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new SaveException("存档文件不存在: " + path);
        }
        try {
            return fromText(Files.readString(path, StandardCharsets.UTF_8));
        } catch (SaveException e) {
            throw e;
        } catch (Exception e) {
            throw new SaveException("读取存档失败: " + path, e);
        }
    }

    // ---------- 工具 ----------

    private static String stripPrefix(String line, String prefix) {
        if (!line.startsWith(prefix)) {
            throw new SaveException("期望 " + prefix.trim() + " 标记，实际: " + line);
        }
        return line.substring(prefix.length()).trim();
    }

    private static String[] split2(String s) {
        String[] parts = s.split(" ", -1);
        if (parts.length < 2) {
            throw new SaveException("字段不足: " + s);
        }
        return new String[]{parts[0], parts[1]};
    }

    private static int parseInt(String s, String what) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            throw new SaveException("非法" + what + ": " + s);
        }
    }

    private static boolean parse01(String s, String what) {
        if ("1".equals(s)) {
            return true;
        }
        if ("0".equals(s)) {
            return false;
        }
        throw new SaveException("非法" + what + "标记: " + s);
    }

    private static String bool01(boolean b) {
        return b ? "1" : "0";
    }

    /** 逐行游标（从第 1 行开始，第 0 行是 HEADER，已消费）。 */
    private static final class Cursor {
        private final List<String> lines;
        private int index = 1;

        Cursor(List<String> lines) {
            this.lines = lines;
        }

        boolean hasNext() {
            return index < lines.size();
        }

        String next() {
            if (!hasNext()) {
                throw new SaveException("存档意外结束");
            }
            return lines.get(index++);
        }
    }
}
