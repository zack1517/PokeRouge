package org.example.save;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Scanner;

import org.example.GameSession;
import org.example.integration.PokemonBattleAdapter;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.RunData;
import org.example.pokemon.service.PokemonService;
import org.example.pokemon.service.PokemonServiceImpl;

/**
 * 存档便捷工具（命令行）：查看 / 删除 / 写入调试存档。
 *
 * <p>针对主游戏（{@code org.example.Launcher}）的档位系统
 * （{@code <用户目录>/.pokerouge/saves/slot1~4}，见 {@link SaveStore}），
 * 供测试 / 调试时快速重置进度。</p>
 *
 * <p>写入的调试存档内容：从 <b>第 3 段</b> 开始（行动点满），队伍为 20 级
 * 火恐龙（charmeleon）、20 级妙蛙草（ivysaur）、20 级巴大蝶（butterfree）各一只，
 * 满状态、第一只先发；金币 {@value #DEBUG_GOLD}；本段路线节点由游戏自身的
 * {@code NodeGenerator} 正常生成，与真玩到第 3 段一致。写入会<b>重置该档位</b>
 * （旧存档与该档位图鉴成长一并清除）。</p>
 *
 * <pre>
 * 运行方式（二选一）：
 *   IDEA：右键本类 → Run 'SaveTool.main()'
 *   命令行：mvn -q compile 后 java -cp target/classes org.example.save.SaveTool
 * </pre>
 *
 * <p>支持命令行参数（便于脚本化）：{@code info} 查看全部档位、
 * {@code write [档位]} 写入、{@code delete [档位|all]} 删除、
 * {@code reset [档位]} 删除并写入；档位写 1~4 或 slot1~slot4，缺省为 1。
 * 无参数进入交互菜单。</p>
 */
public final class SaveTool {

    /** 调试存档：从第 3 段开始。 */
    private static final int DEBUG_SEGMENT = 3;
    /** 调试队伍等级。 */
    private static final int DEBUG_LEVEL = 20;
    /** 调试存档金币。 */
    private static final int DEBUG_GOLD = 500;
    /** 调试存档训练家名。 */
    private static final String DEBUG_PLAYER_NAME = "测试训练家";

    /** 调试队伍：物种 id（顺序即队伍顺序，第一只先发）。 */
    private static final List<String> DEBUG_TEAM = List.of("charmeleon", "ivysaur", "butterfree");

    private SaveTool() {
    }

    public static void main(String[] args) {
        // 控制台统一按 UTF-8 输出，避免中文在 Windows 终端乱码
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true,
                StandardCharsets.UTF_8));
        if (args.length == 0) {
            interactive();
            return;
        }
        String cmd = args[0].trim().toLowerCase(Locale.ROOT);
        switch (cmd) {
            case "info" -> showSlots();
            case "write" -> writeDebugSave(slotArg(args, 1));
            case "delete" -> {
                if (args.length > 1 && "all".equalsIgnoreCase(args[1].trim())) {
                    deleteAllSlots(null);
                } else {
                    deleteSave(slotArg(args, 1), null);
                }
            }
            case "reset" -> resetSave(slotArg(args, 1));
            case "help", "-h", "--help" -> printUsage();
            default -> {
                System.out.println("未知命令：" + cmd);
                printUsage();
            }
        }
    }

    // ------------------------------------------------------------------
    // 交互菜单
    // ------------------------------------------------------------------

    private static void interactive() {
        Scanner in = new Scanner(System.in, StandardCharsets.UTF_8);
        while (true) {
            System.out.println();
            System.out.println("==== PokeRouge 存档工具 ====");
            System.out.println("1. 查看全部档位");
            System.out.println("2. 写入调试存档（第 " + DEBUG_SEGMENT
                    + " 段 + 火恐龙/妙蛙草/巴大蝶 Lv." + DEBUG_LEVEL + "）");
            System.out.println("3. 删除存档");
            System.out.println("4. 一键重置（删除并写入调试存档）");
            System.out.println("0. 退出");
            System.out.print("请选择: ");
            String line = in.nextLine().trim();
            switch (line) {
                case "1" -> showSlots();
                case "2" -> writeDebugSave(askSlot(in));
                case "3" -> {
                    System.out.print("删除哪个档位？(1-4，输入 all 删除全部): ");
                    String target = in.nextLine().trim();
                    if ("all".equalsIgnoreCase(target)) {
                        deleteAllSlots(in);
                    } else {
                        deleteSave(parseSlot(target), in);
                    }
                }
                case "4" -> resetSave(askSlot(in));
                case "0" -> {
                    System.out.println("再见。");
                    return;
                }
                default -> System.out.println("无效选项: " + line);
            }
        }
    }

    private static void printUsage() {
        System.out.println("用法: SaveTool [命令] [档位]");
        System.out.println("  无参数          进入交互菜单");
        System.out.println("  info            查看全部档位");
        System.out.println("  write [1-4]     写入调试存档（第 " + DEBUG_SEGMENT
                + " 段 + 火恐龙/妙蛙草/巴大蝶 Lv." + DEBUG_LEVEL + "）");
        System.out.println("  delete [1-4|all] 删除指定档位或全部档位");
        System.out.println("  reset [1-4]     删除指定档位并写入调试存档");
    }

    /** 交互选择档位：输入 1~4 / slot1~slot4，回车默认 1。 */
    private static SaveSlot askSlot(Scanner in) {
        System.out.print("选择档位 (1-4, 回车=1): ");
        String line = in.nextLine().trim();
        return line.isEmpty() ? SaveSlot.SLOT_1 : parseSlot(line);
    }

    /** 从命令行参数取档位：第 index 个参数缺省时用档位 1。 */
    private static SaveSlot slotArg(String[] args, int index) {
        return args.length > index ? parseSlot(args[index]) : SaveSlot.SLOT_1;
    }

    /** 解析档位文本：1~4 或 slot1~slot4；无法识别时提示并回退档位 1。 */
    private static SaveSlot parseSlot(String text) {
        String t = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (t.matches("[1-4]")) {
            t = "slot" + t;
        }
        Optional<SaveSlot> slot = SaveSlot.ofId(t);
        if (slot.isEmpty()) {
            System.out.println("无法识别档位：" + text + "，已使用档位 1。");
            return SaveSlot.SLOT_1;
        }
        return slot.get();
    }

    // ------------------------------------------------------------------
    // 操作：查看 / 写入 / 删除 / 重置
    // ------------------------------------------------------------------

    /** 查看全部档位状态（空 / 摘要 / 损坏）。 */
    private static void showSlots() {
        SaveStore store = SaveManager.defaultManager().store();
        System.out.println("存档目录：" + store.root());
        boolean any = false;
        for (SaveSlot slot : SaveSlot.all()) {
            SaveStore.SlotStatus status = store.status(slot);
            if (!status.exists()) {
                System.out.println("  " + slot + "：（空）");
                continue;
            }
            any = true;
            if (!status.readable()) {
                System.out.println("  " + slot + "：存在但已损坏 → " + store.saveFile(slot));
                continue;
            }
            System.out.println("  " + slot + "：" + status.summary().describe());
        }
        if (!any) {
            System.out.println("（还没有任何存档）");
        }
    }

    /**
     * 写入调试存档（会重置该档位：旧存档与该档位图鉴成长一并清除）。
     * 写入后立即回读校验，确保游戏「继续游戏」能正常读取。
     */
    private static void writeDebugSave(SaveSlot slot) {
        SaveManager mgr = SaveManager.defaultManager();
        try {
            PokemonService source = new PokemonServiceImpl();
            // 第一只经 newGame 入场（新游戏口径：初始道具 + 新建图鉴成长）
            org.example.pokemon.domain.Pokemon starter =
                    source.createPokemon(DEBUG_TEAM.get(0), DEBUG_LEVEL);
            GameSession session = mgr.newGame(slot, DEBUG_PLAYER_NAME, starter);
            Player player = session.getPlayer();
            for (int i = 1; i < DEBUG_TEAM.size(); i++) {
                player.addPokemon(PokemonBattleAdapter.toBattlePokemon(
                        source.createPokemon(DEBUG_TEAM.get(i), DEBUG_LEVEL)));
            }
            player.setActive(0);
            addItem(player, "i_super_potion", 3);
            addItem(player, "i_great_ball", 3);

            // 直接进入第 3 段：AP 重置为本段上限、路线节点由游戏自身重新生成
            session.enterRogueSegment(DEBUG_SEGMENT);
            session.getRogueRunData().setGold(DEBUG_GOLD);

            mgr.save(slot, player, session);
            System.out.println("【已写入调试存档】" + slot + " → " + mgr.store().saveFile(slot));
            describe(mgr, slot);
        } catch (RuntimeException e) {
            System.out.println("【写入失败】" + e.getMessage());
        }
    }

    /**
     * 删除指定档位（进度快照 + 图鉴成长记录）；交互模式下（{@code in} 非空）需确认。
     */
    private static void deleteSave(SaveSlot slot, Scanner in) {
        SaveStore store = SaveManager.defaultManager().store();
        if (!store.exists(slot)) {
            System.out.println(slot + " 没有存档，无需删除。");
            return;
        }
        if (in != null) {
            System.out.print("确定删除 " + slot + " ? (y/N): ");
            String answer = in.nextLine().trim();
            if (!"y".equalsIgnoreCase(answer) && !"yes".equalsIgnoreCase(answer)) {
                System.out.println("已取消。");
                return;
            }
        }
        try {
            store.delete(slot);
            System.out.println("【已删除】" + slot + "（进度快照 + 图鉴成长）");
            cleanupEmptyDirs(store.slotDir(slot));
        } catch (RuntimeException e) {
            System.out.println("【删除失败】" + e.getMessage());
        }
    }

    /** 删除全部档位；交互模式下（{@code in} 非空）需一次总确认。 */
    private static void deleteAllSlots(Scanner in) {
        if (in != null) {
            System.out.print("确定删除全部 4 个档位 ? (y/N): ");
            String answer = in.nextLine().trim();
            if (!"y".equalsIgnoreCase(answer) && !"yes".equalsIgnoreCase(answer)) {
                System.out.println("已取消。");
                return;
            }
        }
        for (SaveSlot slot : SaveSlot.all()) {
            deleteSave(slot, null);
        }
    }

    /** 一键重置：删除旧存档（若有）并写入调试存档。 */
    private static void resetSave(SaveSlot slot) {
        deleteSave(slot, null);
        writeDebugSave(slot);
    }

    /** 删除后顺手清理空档位目录与空存档根目录，保持用户目录整洁。 */
    private static void cleanupEmptyDirs(Path slotDir) {
        try {
            if (Files.isDirectory(slotDir) && isEmptyDir(slotDir)) {
                Files.delete(slotDir);
                Path root = slotDir.getParent();
                if (root != null && Files.isDirectory(root) && isEmptyDir(root)) {
                    Files.delete(root);
                }
            }
        } catch (IOException ignored) {
            // 空目录清理失败不影响功能
        }
    }

    private static boolean isEmptyDir(Path dir) throws IOException {
        try (var children = Files.list(dir)) {
            return children.findAny().isEmpty();
        }
    }

    /** 回读校验：用游戏自身的读档链路重新载入，打印队伍与进度。 */
    private static void describe(SaveManager mgr, SaveSlot slot) {
        mgr.load(slot).ifPresentOrElse(session -> {
            Player player = session.getPlayer();
            System.out.println("  训练家：" + player.getName());
            for (Pokemon p : player.getParty()) {
                System.out.println("  - " + p.getName() + " Lv." + p.getLevel()
                        + " HP " + p.getCurrentHp() + "/" + p.getMaxHp());
            }
            RunData run = session.getRogueRunData();
            System.out.println("  进度：第 " + run.getSegment() + " 段  AP " + run.getAp()
                    + "/" + run.getApMax() + "  金币 " + run.getGold()
                    + "  阶段 " + run.getPhase().getDisplayName()
                    + "  可选节点 " + run.getAvailableOptions().size() + " 个");
            System.out.println("  回读校验通过：启动游戏「继续游戏」选择该档位即可。");
        }, () -> System.out.println("  [警告] 回读校验失败，写入的内容可能无法被游戏读取。"));
    }

    /** 背包发放：按道具 id 添加指定数量；未注册道具跳过并告警。 */
    private static void addItem(Player player, String itemId, int count) {
        org.example.model.Item item = org.example.data.GameData.instance().item(itemId);
        if (item == null) {
            System.out.println("  [警告] 未注册道具，跳过: " + itemId);
            return;
        }
        player.getBag().add(item, count);
    }
}
