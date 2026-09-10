package com.bao01.save;

import org.example.data.GameData;
import org.example.model.Item;
import org.example.model.ItemStack;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 存档系统无头自测入口（可运行 Demo）。
 *
 * <p>用例覆盖：采样 → 文本编解码 → 磁盘读写 → 重建比对（队伍 / 等级 / HP / 出战位 /
 * 背包）、出战位濒死回退、未知 id 跳过、非法存档拒绝。</p>
 *
 * <pre>
 * mvn clean compile
 * java -cp target/classes com.bao01.save.SaveDemo
 * </pre>
 *
 * <p>全部用例通过退出码 0；任一失败退出码 1（便于 CI 或验收脚本判定）。</p>
 */
public final class SaveDemo {

    /** 自测使用的存档路径（位于 target/ 下，不污染工作区）。 */
    private static final Path DEMO_SAVE = Path.of("target", "save-demo.txt");

    private static final List<String> FAILURES = new ArrayList<>();
    private static int checks;

    private SaveDemo() {
    }

    public static void main(String[] args) {
        System.out.println("==== 存档系统自测 SaveDemo ====");
        System.out.println("存档格式版本 FORMAT_VERSION = " + SaveData.FORMAT_VERSION);
        System.out.println("默认存档路径 = " + SaveManager.defaultPath());

        Player player = samplePlayer();
        System.out.println("测试玩家: " + describe(player));

        SaveData.World world = SaveManager.captureWorld(player);
        System.out.println("采样得到的存档文本:");
        System.out.print(SaveManager.toText(world));

        run("1. 内存采样 → 重建回环", () -> memoryRoundTrip(player, world));
        run("2. 文本编解码", () -> textCodec(world));
        run("3. 磁盘读写回环", () -> diskRoundTrip(world));
        run("4. 出战位濒死回退", SaveDemo::activeIndexFallback);
        run("5. 未知物种 / 道具跳过", SaveDemo::unknownIdsSkipped);
        run("6. 非法存档与缺失文件拒绝", () -> rejects(world));

        System.out.println("==== 结果: " + (checks - FAILURES.size()) + "/" + checks + " 通过 ====");
        if (FAILURES.isEmpty()) {
            System.out.println("存档系统自测全部通过");
            return;
        }
        for (String f : FAILURES) {
            System.out.println("  失败: " + f);
        }
        System.exit(1);
    }

    // ---------- 用例 ----------

    private static void memoryRoundTrip(Player player, SaveData.World world) {
        Player restored = SaveManager.restorePlayer(world, player.getName());

        check("训练家名可指定", player.getName().equals(restored.getName()),
                "期望 " + player.getName() + "，实际 " + restored.getName());
        check("队伍成员数一致", player.getPartySize() == restored.getPartySize(),
                "期望 " + player.getPartySize() + "，实际 " + restored.getPartySize());

        int n = Math.min(player.getPartySize(), restored.getPartySize());
        for (int i = 0; i < n; i++) {
            Pokemon before = player.getParty().get(i);
            Pokemon after = restored.getParty().get(i);
            check("第 " + (i + 1) + " 只物种一致",
                    before.getSpecies().getId().equals(after.getSpecies().getId()),
                    "期望 " + before.getSpecies().getId() + "，实际 " + after.getSpecies().getId());
            check("第 " + (i + 1) + " 只等级一致", before.getLevel() == after.getLevel(),
                    "期望 " + before.getLevel() + "，实际 " + after.getLevel());
            check("第 " + (i + 1) + " 只 HP 一致（" + before.getCurrentHp() + "/" + before.getMaxHp() + "）",
                    before.getCurrentHp() == after.getCurrentHp(),
                    "期望 " + before.getCurrentHp() + "，实际 " + after.getCurrentHp());
            check("第 " + (i + 1) + " 只濒死标记一致", before.isFainted() == after.isFainted(),
                    "期望 " + before.isFainted() + "，实际 " + after.isFainted());
        }

        check("出战位一致", player.getActiveIndex() == restored.getActiveIndex(),
                "期望 " + player.getActiveIndex() + "，实际 " + restored.getActiveIndex());
        check("背包一致（" + describeBag(player) + "）",
                bagCounts(restored).equals(bagCounts(player)),
                "期望 " + bagCounts(player) + "，实际 " + bagCounts(restored));
    }

    private static void textCodec(SaveData.World world) {
        String text = SaveManager.toText(world);
        check("文本以头标记开头", text.startsWith("BAO01SAVE"), text.lines().findFirst().orElse("(空)"));
        check("文本含 VERSION 行", text.contains("VERSION " + world.version()), "缺少版本行");
        check("文本含 POKE 记录", text.contains("\nPOKE "), "缺少 POKE 行");
        check("文本含 ITEM 记录", text.contains("\nITEM "), "缺少 ITEM 行");

        SaveData.World parsed = SaveManager.fromText(text);
        check("解析后再序列化完全一致", SaveManager.toText(parsed).equals(text), "文本回环不一致");
        check("解析后版本号保留", parsed.version() == world.version(),
                "期望 " + world.version() + "，实际 " + parsed.version());

        SaveData.World recaptured = SaveManager.captureWorld(SaveManager.restorePlayer(parsed));
        check("capture → restore → capture 稳定", SaveManager.toText(recaptured).equals(text), "二次采样文本不同");
    }

    private static void diskRoundTrip(SaveData.World world) {
        SaveManager.writeToFile(DEMO_SAVE, world);
        check("存档文件已写入磁盘", Files.isRegularFile(DEMO_SAVE), "未找到 " + DEMO_SAVE.toAbsolutePath());

        SaveData.World fromDisk = SaveManager.readFromFile(DEMO_SAVE);
        check("磁盘读取内容与内存一致", SaveManager.toText(fromDisk).equals(SaveManager.toText(world)),
                "磁盘内容不一致");
        Player reloaded = SaveManager.restorePlayer(fromDisk);
        check("磁盘存档可重建玩家", reloaded.getPartySize() > 0,
                "重建后队伍为空（" + DEMO_SAVE.toAbsolutePath() + "）");
        System.out.println("  存档文件: " + DEMO_SAVE.toAbsolutePath());
    }

    private static void activeIndexFallback() {
        Player player = freshPlayer(2);
        Pokemon lead = player.getParty().get(0);
        lead.takeDamage(lead.getMaxHp());
        player.setActive(0);
        check("采样前：出战位指向濒死成员", player.getActive().isFainted(), "前置条件不成立");

        SaveData.World world = SaveManager.captureWorld(player);
        check("采样记录原出战位", world.player().activeIndex() == 0,
                "期望 0，实际 " + world.player().activeIndex());

        Player restored = SaveManager.restorePlayer(world, player.getName());
        check("濒死出战位回退到第一只存活成员",
                restored.getActiveIndex() == 1 && !restored.getActive().isFainted(),
                "activeIndex=" + restored.getActiveIndex());
    }

    private static void unknownIdsSkipped() {
        SaveData.PlayerData mixed = new SaveData.PlayerData(
                List.of(new SaveData.PartyEntry("s_does_not_exist", 10, 10),
                        SaveData.PartyEntry.capture(freshPlayer(1).getParty().get(0))),
                0,
                List.of(new SaveData.ItemEntry("i_does_not_exist", 3)));
        Player restored = SaveManager.restorePlayer(new SaveData.World(SaveData.FORMAT_VERSION, mixed));
        check("未知物种条目被跳过", restored.getPartySize() == 1,
                "期望 1 只，实际 " + restored.getPartySize());
        check("未知道具条目被跳过", restored.getBag().getAll().isEmpty(),
                "实际 " + describeBag(restored));

        SaveData.PlayerData allUnknown = new SaveData.PlayerData(
                List.of(new SaveData.PartyEntry("s_does_not_exist", 5, 5)), 0, List.of());
        SaveException e = expectSaveException(
                () -> SaveManager.restorePlayer(new SaveData.World(SaveData.FORMAT_VERSION, allUnknown)));
        check("全员不可恢复时抛 SaveException", e != null,
                "未抛出异常；消息=" + (e == null ? "(无)" : e.getMessage()));
    }

    private static void rejects(SaveData.World world) {
        check("空文本被拒绝", expectSaveException(() -> SaveManager.fromText("")) != null, "未抛出 SaveException");
        check("缺少头标记被拒绝",
                expectSaveException(() -> SaveManager.fromText("HELLO\nVERSION 1\nPT 0 0\nBAG 0\n")) != null,
                "未抛出 SaveException");
        check("字段不足被拒绝",
                expectSaveException(() -> SaveManager.fromText("BAO01SAVE\nVERSION 1\nPT 1 0\nPOKE s_x\nBAG 0\n")) != null,
                "未抛出 SaveException");
        check("高于当前版本被拒绝", expectSaveException(() -> SaveManager.fromText(
                "BAO01SAVE\nVERSION " + (SaveData.FORMAT_VERSION + 1) + "\nPT 0 0\nBAG 0\n")) != null,
                "未抛出 SaveException");
        check("不存在的存档文件被拒绝",
                expectSaveException(() -> SaveManager.readFromFile(Path.of("target", "no-such-save.txt"))) != null,
                "未抛出 SaveException");
        check("正常存档不被误判", expectSaveException(() -> SaveManager.fromText(SaveManager.toText(world))) == null,
                "正常存档被拒绝");
    }

    // ---------- 测试数据 ----------

    /** 构造含 3 只精灵（含掉血、濒死各一）与非空背包的玩家。 */
    private static Player samplePlayer() {
        Player player = freshPlayer(3);
        Pokemon first = player.getParty().get(0);
        first.takeDamage(Math.max(1, first.getMaxHp() / 3));
        if (player.getPartySize() > 1) {
            Pokemon second = player.getParty().get(1);
            second.takeDamage(second.getMaxHp());
        }
        player.setActive(0);

        List<Item> items = GameData.instance().allItems();
        if (!items.isEmpty()) {
            player.getBag().add(items.get(0), 3);
        }
        if (items.size() > 1) {
            player.getBag().add(items.get(1), 1);
        }
        return player;
    }

    /** 从注册表取前 count 个物种组建队伍（等级递增）。 */
    private static Player freshPlayer(int count) {
        List<Species> species = GameData.instance().allSpecies();
        if (species.isEmpty()) {
            throw new SaveException("物种注册表为空，无法构造测试数据");
        }
        Player player = new Player("测试训练家");
        for (int i = 0; i < count; i++) {
            Species s = species.get(i % species.size());
            Pokemon p = GameData.instance().createPokemon(s.getId(), 10 + 5 * i)
                    .orElseThrow(() -> new SaveException("无法创建精灵: " + s.getId()));
            player.addToParty(p);
        }
        return player;
    }

    private static Map<String, Integer> bagCounts(Player player) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : player.getBag().getAll()) {
            if (!stack.isEmpty()) {
                counts.merge(stack.getItem().getId(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    private static String describe(Player player) {
        StringBuilder sb = new StringBuilder(player.getName()).append(" | 队伍:");
        for (Pokemon p : player.getParty()) {
            sb.append(' ').append(p.getSpecies().getId())
                    .append(" Lv.").append(p.getLevel())
                    .append(" HP ").append(p.getCurrentHp()).append('/').append(p.getMaxHp());
            if (p.isFainted()) {
                sb.append("(濒死)");
            }
        }
        return sb.append(" | 出战位 ").append(player.getActiveIndex())
                .append(" | 背包:").append(describeBag(player).isEmpty() ? "(空)" : describeBag(player))
                .toString();
    }

    private static String describeBag(Player player) {
        StringBuilder sb = new StringBuilder();
        for (ItemStack stack : player.getBag().getAll()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(stack.getItem().getId()).append('x').append(stack.getCount());
        }
        return sb.toString();
    }

    // ---------- 断言与输出 ----------

    private static void run(String title, Runnable body) {
        System.out.println();
        System.out.println("-- " + title);
        try {
            body.run();
        } catch (RuntimeException e) {
            checks++;
            FAILURES.add(title + " 抛出异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("  [FAIL] 抛出异常: " + e);
        }
    }

    private static void check(String name, boolean ok, String detail) {
        checks++;
        if (ok) {
            System.out.println("  [PASS] " + name);
        } else {
            FAILURES.add(name + " -> " + detail);
            System.out.println("  [FAIL] " + name + " -> " + detail);
        }
    }

    private static SaveException expectSaveException(Runnable action) {
        try {
            action.run();
        } catch (SaveException e) {
            return e;
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }
}
