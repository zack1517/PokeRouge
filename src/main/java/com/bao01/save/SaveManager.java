package com.bao01.save;

import com.bao01.flow.CaptureLedger;
import com.bao01.flow.Ending;
import com.bao01.flow.NodeType;
import com.bao01.flow.RouteOption;
import com.bao01.flow.RunSummary;
import com.bao01.flow.ShopOffer;
import com.bao01.flow.StoryFlags;
import org.example.model.Player;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 存档管理器：负责捕获运行状态为 {@link SaveData.World}、恢复回
 * {@link org.example.model.Player}，以及文本编解码与磁盘读写。
 *
 * <p>文本格式为自定义的稳定行格式（UTF-8），无需第三方序列化依赖。
 * 各行以固定标记开头；字段用竖线分隔；空行与 # 注释行会被忽略。</p>
 *
 * <p>格式 v2（自 v1 追加 NAME 段与 RUN 段，v1 存档仍可读）：</p>
 * <pre>
 * BAO01SAVE
 * VERSION 2
 * NAME 小红                                  # 可选：训练家名
 * PT 3 0                                     # 队伍成员数、出战位
 * POKE s_fire_cat|5|18                       # 物种 id | 等级 | 当前 HP
 * BAG 2                                      # 背包条数
 * ITEM i_potion|5                            # 道具 id | 数量
 * RUN 1 6/6 500 ROUTE NONE PLAYING           # 段号 AP剩余/上限 金币 阶段 必然节点 结局
 * STORY 0 0 0 0 0 0                          # 6 个剧情线开关（1/0）
 * FLAGS 0 0 0 0 0                            # 里程碑记账（1/0，见 RunSummary）
 * CAUGHT 1                                   # 抓捕账本条数
 * CATCH 焰尾狐|2                              # 物种 | 累计捕获次数
 * OPTIONS 2                                  # 本段剩余可选节点数
 * OPTION WILD|1|0|野生的 焰尾狐                # 种类 | AP 消耗 | 是否必然 | 展示文案
 * SHOP 0                                     # 当前商店货架条数
 * OFFER i_potion|150|回复 20 HP               # 道具 id | 售价 | 描述
 * </pre>
 *
 * <p>「SHOP / OFFER」段只在存档时正处于商店节点才非空；RUN 段可整体缺省
 * （{@link SaveData.World#hasRun()} 为 {@code false}），表示存档不含流程状态。</p>
 */
public final class SaveManager {

    private static final String HEADER = "BAO01SAVE";

    private SaveManager() {
    }

    // ---------- 捕获 ----------

    /** 世界存档：保存玩家队伍 + 背包（不含流程状态）。 */
    public static SaveData.World captureWorld(Player player) {
        return captureWorld(player, null);
    }

    /**
     * 世界存档：保存玩家队伍 + 背包 + 一段 Run 推进状态。
     *
     * @param run Run 快照，通常为 {@code flow.summary()}；{@code null} 表示不含流程状态
     */
    public static SaveData.World captureWorld(Player player, RunSummary run) {
        if (player == null) {
            throw new SaveException("玩家为空");
        }
        return new SaveData.World(SaveData.FORMAT_VERSION,
                SaveData.PlayerData.capture(player), run);
    }

    // ---------- 恢复 ----------

    /** 从世界存档重建玩家（含训练家名、队伍与背包）。 */
    public static Player restorePlayer(SaveData.World world) {
        if (world == null) {
            throw new SaveException("存档为空");
        }
        return world.player().restore();
    }

    /** 从世界存档重建玩家，使用指定训练家名。 */
    public static Player restorePlayer(SaveData.World world, String playerName) {
        if (world == null) {
            throw new SaveException("存档为空");
        }
        return world.player().restore(playerName);
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
        String name = world.player().name();
        if (name != null && !name.isBlank()) {
            sb.append("NAME ").append(name).append('\n');
        }
        writePlayer(sb, world.player());
        if (world.run() != null) {
            writeRun(sb, world.run());
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

        String name = null;
        if (cur.hasNext() && cur.peek().startsWith("NAME ")) {
            name = cur.next().substring("NAME ".length()).trim();
        }
        SaveData.PlayerData player = readPlayer(cur, name);
        RunSummary run = null;
        if (cur.hasNext() && cur.peek().startsWith("RUN ")) {
            run = readRun(cur);
        }
        if (cur.hasNext()) {
            throw new SaveException("存档存在多余内容: " + cur.next());
        }
        return new SaveData.World(version, player, run);
    }

    // ---------- 文本写出 ----------

    private static void writePlayer(StringBuilder sb, SaveData.PlayerData player) {
        List<SaveData.PartyEntry> party = player.party();
        sb.append("PT ").append(party.size()).append(' ')
                .append(player.activeIndex()).append('\n');
        for (SaveData.PartyEntry pe : party) {
            sb.append("POKE ").append(pe.speciesId()).append('|')
                    .append(pe.level()).append('|').append(pe.currentHp()).append('\n');
        }
        List<SaveData.ItemEntry> bag = player.bag();
        sb.append("BAG ").append(bag.size()).append('\n');
        for (SaveData.ItemEntry ie : bag) {
            sb.append("ITEM ").append(ie.itemId()).append('|')
                    .append(ie.count()).append('\n');
        }
    }

    /** Run 快照 → 文本行（对应《游戏流程接口设计》§7）。 */
    private static void writeRun(StringBuilder sb, RunSummary run) {
        sb.append("RUN ").append(run.segmentNo()).append(' ')
                .append(run.apLeft()).append('/').append(run.apCap()).append(' ')
                .append(run.gold()).append(' ')
                .append(run.inRoute() ? "ROUTE" : "FINAL").append(' ')
                .append(NodeType.textOf(run.nextMilestone())).append(' ')
                .append(Ending.textOf(run.ending())).append('\n');

        StoryFlags st = run.story();
        sb.append("STORY ").append(bit(st.rocketEntered())).append(' ')
                .append(bit(st.legendaryEncountered())).append(' ')
                .append(bit(st.masterBallHeld())).append(' ')
                .append(bit(st.rocketBossDefeated())).append(' ')
                .append(bit(st.championDefeated())).append(' ')
                .append(bit(st.invasionDone())).append('\n');

        sb.append("FLAGS ").append(bit(run.gymRetried())).append(' ')
                .append(bit(run.eliteCleared())).append(' ')
                .append(bit(run.eliteRetried())).append(' ')
                .append(bit(run.championCleared())).append(' ')
                .append(bit(run.invasionCleared())).append('\n');

        Map<String, Integer> catches = run.ledger().snapshot();
        sb.append("CAUGHT ").append(catches.size()).append('\n');
        for (Map.Entry<String, Integer> e : catches.entrySet()) {
            sb.append("CATCH ").append(e.getKey()).append('|').append(e.getValue()).append('\n');
        }

        sb.append("OPTIONS ").append(run.options().size()).append('\n');
        for (RouteOption o : run.options()) {
            sb.append("OPTION ").append(o.type().name()).append('|')
                    .append(o.apCost()).append('|').append(bit(o.forced())).append('|')
                    .append(o.display()).append('\n');
        }

        sb.append("SHOP ").append(run.shopStock().size()).append('\n');
        for (ShopOffer offer : run.shopStock()) {
            sb.append("OFFER ").append(offer.itemId()).append('|')
                    .append(offer.price()).append('|')
                    .append(offer.description()).append('\n');
        }
    }

    // ---------- 文本解析 ----------

    private static SaveData.PlayerData readPlayer(Cursor cur, String name) {
        String header = cur.next();
        if (!header.startsWith("PT ")) {
            throw new SaveException("期望 PT 标记，实际: " + header);
        }
        String[] head = header.substring("PT ".length()).split(" ", -1);
        int memberCount = parseInt(head[0], "队伍成员数");
        int activeIndex = head.length > 1 ? parseInt(head[1], "出战位") : 0;

        List<SaveData.PartyEntry> members = new ArrayList<>();
        for (int i = 0; i < memberCount; i++) {
            String line = cur.next();
            if (!line.startsWith("POKE ")) {
                throw new SaveException("期望 POKE 记录，实际: " + line);
            }
            String[] f = line.substring("POKE ".length()).split("\\|", -1);
            if (f.length < 3) {
                throw new SaveException("POKE 字段不足: " + line);
            }
            members.add(new SaveData.PartyEntry(f[0], parseInt(f[1], "等级"),
                    parseInt(f[2], "HP")));
        }

        String bagLine = cur.next();
        if (!bagLine.startsWith("BAG ")) {
            throw new SaveException("期望 BAG 标记，实际: " + bagLine);
        }
        int bagCount = parseInt(bagLine.substring("BAG ".length()), "背包条数");
        List<SaveData.ItemEntry> bag = new ArrayList<>();
        for (int i = 0; i < bagCount; i++) {
            String line = cur.next();
            if (!line.startsWith("ITEM ")) {
                throw new SaveException("期望 ITEM 记录，实际: " + line);
            }
            String[] f = line.substring("ITEM ".length()).split("\\|", -1);
            if (f.length < 2) {
                throw new SaveException("ITEM 字段不足: " + line);
            }
            bag.add(new SaveData.ItemEntry(f[0], parseInt(f[1], "道具数量")));
        }
        return new SaveData.PlayerData(members, activeIndex, bag, name);
    }

    /** 解析 Run 段；字段非法（未知节点种类/结局、数字格式错误）统一转成 {@link SaveException}。 */
    private static RunSummary readRun(Cursor cur) {
        try {
            return readRunFields(cur);
        } catch (IllegalArgumentException e) {
            throw new SaveException("存档 Run 段非法: " + e.getMessage(), e);
        }
    }

    private static RunSummary readRunFields(Cursor cur) {
        String[] f = cur.next().substring("RUN ".length()).split(" +", -1);
        if (f.length < 6) {
            throw new SaveException("RUN 字段不足（期望 6 项）");
        }
        int segmentNo = parseInt(f[0], "段号");
        int slash = f[1].indexOf('/');
        if (slash < 0) {
            throw new SaveException("RUN 的 AP 字段应为 剩余/上限：" + f[1]);
        }
        int apLeft = parseInt(f[1].substring(0, slash), "剩余 AP");
        int apCap = parseInt(f[1].substring(slash + 1), "AP 上限");
        int gold = parseInt(f[2], "金币");
        boolean inRoute = parseEnum(f[3], "阶段", "ROUTE", "FINAL") == 0;
        NodeType nextMilestone = NodeType.parse(f[4]);
        Ending ending = Ending.parse(f[5]);

        String[] storyFields = requireCount(cur.next(), "STORY ", 6);
        StoryFlags story = new StoryFlags(parseBool(storyFields[0], "火箭队线"),
                parseBool(storyFields[1], "神兽偶遇"), parseBool(storyFields[2], "大师球"),
                parseBool(storyFields[3], "首领已击败"), parseBool(storyFields[4], "冠军已击败"),
                parseBool(storyFields[5], "侵略战已结束"));

        String[] flagFields = requireCount(cur.next(), "FLAGS ", 5);
        boolean gymRetried = parseBool(flagFields[0], "道馆已失败一次");
        boolean eliteCleared = parseBool(flagFields[1], "四天王已通过");
        boolean eliteRetried = parseBool(flagFields[2], "四天王已失败一次");
        boolean championCleared = parseBool(flagFields[3], "冠军已击败");
        boolean invasionCleared = parseBool(flagFields[4], "侵略战已通过");

        int caughtCount = parseInt(prefixCount(cur.next(), "CAUGHT "), "抓捕条数");
        Map<String, Integer> catches = new LinkedHashMap<>();
        for (int i = 0; i < caughtCount; i++) {
            String line = cur.next();
            if (!line.startsWith("CATCH ")) {
                throw new SaveException("期望 CATCH 记录，实际: " + line);
            }
            String body = line.substring("CATCH ".length());
            int bar = body.indexOf('|');
            if (bar <= 0) {
                throw new SaveException("CATCH 字段不足: " + line);
            }
            catches.put(body.substring(0, bar), parseInt(body.substring(bar + 1), "捕获次数"));
        }

        int optionCount = parseInt(prefixCount(cur.next(), "OPTIONS "), "可选节点数");
        List<RouteOption> options = new ArrayList<>();
        for (int i = 0; i < optionCount; i++) {
            String line = cur.next();
            if (!line.startsWith("OPTION ")) {
                throw new SaveException("期望 OPTION 记录，实际: " + line);
            }
            String[] o = line.substring("OPTION ".length()).split("\\|", 4);
            if (o.length < 4) {
                throw new SaveException("OPTION 字段不足: " + line);
            }
            options.add(new RouteOption(NodeType.parse(o[0]), o[3],
                    parseInt(o[1], "AP 消耗"), parseBool(o[2], "是否必然")));
        }

        int shopCount = parseInt(prefixCount(cur.next(), "SHOP "), "商店条数");
        List<ShopOffer> shopStock = new ArrayList<>();
        for (int i = 0; i < shopCount; i++) {
            String line = cur.next();
            if (!line.startsWith("OFFER ")) {
                throw new SaveException("期望 OFFER 记录，实际: " + line);
            }
            String[] o = line.substring("OFFER ".length()).split("\\|", 3);
            if (o.length < 3) {
                throw new SaveException("OFFER 字段不足: " + line);
            }
            shopStock.add(new ShopOffer(o[0], parseInt(o[1], "售价"), o[2]));
        }

        return new RunSummary(segmentNo, apLeft, apCap, gold, story,
                new CaptureLedger(catches), options, nextMilestone, inRoute, gymRetried,
                eliteCleared, eliteRetried, championCleared, invasionCleared, ending, shopStock);
    }

    // ---------- 磁盘 IO ----------

    /** 默认存档位置：用户主目录下的 .bao01/save.txt。 */
    public static Path defaultPath() {
        return Path.of(System.getProperty("user.home", "."), ".bao01", "save.txt");
    }

    /** 默认存档位置是否已存在存档文件（UI 用来决定「读取存档」是否可用）。 */
    public static boolean defaultSaveExists() {
        return Files.isRegularFile(defaultPath());
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

    private static char bit(boolean value) {
        return value ? '1' : '0';
    }

    private static boolean parseBool(String s, String what) {
        String t = s == null ? "" : s.trim();
        if ("1".equals(t) || "true".equalsIgnoreCase(t)) {
            return true;
        }
        if ("0".equals(t) || "false".equalsIgnoreCase(t)) {
            return false;
        }
        throw new SaveException("非法布尔值（" + what + "）: " + s);
    }

    /** 二选一标记解析：返回命中下标。 */
    private static int parseEnum(String s, String what, String... allowed) {
        String t = s == null ? "" : s.trim().toUpperCase(java.util.Locale.ROOT);
        for (int i = 0; i < allowed.length; i++) {
            if (allowed[i].equals(t)) {
                return i;
            }
        }
        throw new SaveException("非法" + what + "标记: " + s);
    }

    /** 校验行前缀并返回其后的空白分隔字段（要求字段数恰好为 count）。 */
    private static String[] requireCount(String line, String prefix, int count) {
        if (!line.startsWith(prefix)) {
            throw new SaveException("期望 " + prefix.trim() + " 标记，实际: " + line);
        }
        String[] f = line.substring(prefix.length()).trim().split(" +", -1);
        if (f.length != count) {
            throw new SaveException(prefix.trim() + " 字段数应为 " + count + "，实际 "
                    + f.length + ": " + line);
        }
        return f;
    }

    /** 校验行前缀并返回其后的计数文本。 */
    private static String prefixCount(String line, String prefix) {
        if (!line.startsWith(prefix)) {
            throw new SaveException("期望 " + prefix.trim() + " 标记，实际: " + line);
        }
        return line.substring(prefix.length()).trim();
    }

    private static String stripPrefix(String line, String prefix) {
        if (!line.startsWith(prefix)) {
            throw new SaveException("期望 " + prefix.trim() + " 标记，实际: " + line);
        }
        return line.substring(prefix.length()).trim();
    }

    private static int parseInt(String s, String what) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            throw new SaveException("非法" + what + ": " + s);
        }
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

        /** 预览下一行（不消费）；无下一行返回空串。 */
        String peek() {
            return hasNext() ? lines.get(index) : "";
        }

        String next() {
            if (!hasNext()) {
                throw new SaveException("存档意外结束");
            }
            return lines.get(index++);
        }
    }
}
