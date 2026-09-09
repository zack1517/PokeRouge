package com.bao01.save;

import org.example.model.Player;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 存档管理器：负责捕获运行状态为 {@link SaveData.World}、恢复回
 * {@link org.example.model.Player}，以及文本编解码与磁盘读写。
 *
 * <p>文本格式为自定义的稳定行格式（UTF-8），无需第三方序列化依赖。
 * 各行以固定标记开头；字段用竖线分隔；空行与 # 注释行会被忽略。</p>
 */
public final class SaveManager {

    private static final String HEADER = "BAO01SAVE";

    private SaveManager() {
    }

    // ---------- 捕获 ----------

    /** 世界存档：保存玩家队伍 + 背包。 */
    public static SaveData.World captureWorld(Player player) {
        if (player == null) {
            throw new SaveException("玩家为空");
        }
        return new SaveData.World(SaveData.FORMAT_VERSION,
                SaveData.PlayerData.capture(player));
    }

    // ---------- 恢复 ----------

    /** 从世界存档重建玩家（含队伍与背包）。 */
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
        writePlayer(sb, world.player());
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

        SaveData.PlayerData player = readPlayer(cur);
        if (cur.hasNext()) {
            throw new SaveException("存档存在多余内容: " + cur.next());
        }
        return new SaveData.World(version, player);
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

    // ---------- 文本解析 ----------

    private static SaveData.PlayerData readPlayer(Cursor cur) {
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
        return new SaveData.PlayerData(members, activeIndex, bag);
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

        String next() {
            if (!hasNext()) {
                throw new SaveException("存档意外结束");
            }
            return lines.get(index++);
        }
    }
}
