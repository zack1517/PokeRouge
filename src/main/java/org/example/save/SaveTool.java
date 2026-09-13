package org.example.save;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Scanner;

/**
 * 存档便捷工具（命令行）：查看 / 删除档位。
 *
 * <p>针对主游戏（{@code org.example.Launcher}）的档位系统
 * （{@code <用户目录>/.pokerouge/saves/slot1~4}，见 {@link SaveStore}），
 * 供测试 / 调试时快速查看或清理进度。</p>
 *
 * <pre>
 * 运行方式（二选一）：
 *   IDEA：右键本类 → Run 'SaveTool.main()'
 *   命令行：mvn -q compile 后 java -cp target/classes org.example.save.SaveTool
 * </pre>
 *
 * <p>支持命令行参数（便于脚本化）：{@code info} 查看全部档位、
 * {@code delete [档位|all]} 删除；档位写 1~4 或 slot1~slot4，缺省为 1。
 * 无参数进入交互菜单。</p>
 */
public final class SaveTool {

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
            case "delete" -> {
                if (args.length > 1 && "all".equalsIgnoreCase(args[1].trim())) {
                    deleteAllSlots(null);
                } else {
                    deleteSave(slotArg(args, 1), null);
                }
            }
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
            System.out.println("2. 删除存档");
            System.out.println("0. 退出");
            System.out.print("请选择: ");
            String line = in.nextLine().trim();
            switch (line) {
                case "1" -> showSlots();
                case "2" -> {
                    System.out.print("删除哪个档位？(1-4，输入 all 删除全部): ");
                    String target = in.nextLine().trim();
                    if ("all".equalsIgnoreCase(target)) {
                        deleteAllSlots(in);
                    } else {
                        deleteSave(parseSlot(target), in);
                    }
                }
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
        System.out.println("  delete [1-4|all] 删除指定档位或全部档位");
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
    // 操作：查看 / 删除
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
}
