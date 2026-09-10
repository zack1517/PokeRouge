package com.bao01.save;

import com.bao01.flow.BattleAdapter;
import com.bao01.flow.CaptureLedger;
import com.bao01.flow.Ending;
import com.bao01.flow.FightResult;
import com.bao01.flow.FlowController;
import com.bao01.flow.NodeType;
import com.bao01.flow.RandomSource;
import com.bao01.flow.RouteOption;
import com.bao01.flow.RunSummary;
import com.bao01.flow.ShopOffer;
import com.bao01.flow.StoryFlags;
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
 * 背包）、出战位濒死回退、未知 id 跳过、非法存档拒绝，以及 v2 新增的
 * <b>Run（流程推进状态）回环</b>与 <b>v1 旧档兼容</b>。</p>
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
        run("7. Run 快照回环（v2 新增段）", SaveDemo::runSnapshotRoundTrip);
        run("8. 流程真实推进 → 存盘 → 读档续玩", SaveDemo::flowSaveLoadRoundTrip);
        run("9. v1 旧档向后兼容", SaveDemo::legacyV1Save);

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

    // ---------- 用例 7~9：Run 状态（v2 新增） ----------

    /** Run 快照 → 文本 → 解析 → 逐字段比对，并校验文本含全部 Run 段标记。 */
    private static void runSnapshotRoundTrip() {
        Player player = freshPlayer(2);
        RunSummary before = sampleRunSummary();
        SaveData.World world = SaveManager.captureWorld(player, before);

        check("captureWorld 携带 Run 状态", world.hasRun(), "run 为 null");

        String text = SaveManager.toText(world);
        check("文本含 RUN 段", text.contains("\nRUN "), "缺少 RUN 行");
        check("文本含 STORY 段", text.contains("\nSTORY "), "缺少 STORY 行");
        check("文本含 FLAGS 段", text.contains("\nFLAGS "), "缺少 FLAGS 行");
        check("文本含 CAUGHT 段", text.contains("\nCAUGHT "), "缺少 CAUGHT 行");
        check("文本含 OPTIONS 段", text.contains("\nOPTIONS "), "缺少 OPTIONS 行");
        check("文本含 SHOP 段", text.contains("\nSHOP "), "缺少 SHOP 行");

        SaveData.World parsed = SaveManager.fromText(text);
        check("解析后仍带 Run 状态", parsed.hasRun(), "run 为 null");
        check("解析后再序列化完全一致", SaveManager.toText(parsed).equals(text), "文本回环不一致");

        RunSummary after = parsed.run();
        check("Run 各字段一致（文本回环）", sameRun(before, after), diffRun(before, after));

        // 走一遍真实恢复路径：FlowController 必须精确还原（不能把 AP 重置成满值）。
        FlowController restored = FlowController.restored(
                SaveManager.restorePlayer(parsed), after, new RecordingAdapter());
        check("FlowController.restored 精确还原 Run 快照", sameRun(before, restored.summary()),
                diffRun(before, restored.summary()));
        check("读档不重置 AP（原 bug：AP 被刷满）",
                restored.summary().apLeft() == before.apLeft(),
                "存档 AP " + before.apLeft() + "，读档后 " + restored.summary().apLeft());

        // 无过程态：读档后不应残留待结算战斗。
        check("读档后无挂起的待结算战斗",
                restored.settleFight(NodeType.WILD, FightResult.PLAYER_WON)
                        .get(0).contains("没有待结算"),
                "读档后仍存在 pendingFight");

        // 已结束的 Run 同样要能原样落盘 / 读回（含 ending 与终局标记）。
        RunSummary finished = finishedRunSummary();
        SaveData.World doneWorld = SaveManager.captureWorld(player, finished);
        RunSummary doneBack = SaveManager.fromText(SaveManager.toText(doneWorld)).run();
        check("已结束 Run 的结局一致", doneBack.ending() == Ending.TRUE_CLEAR,
                "实际 " + doneBack.ending());
        check("已结束 Run 各字段一致", sameRun(finished, doneBack), diffRun(finished, doneBack));
        check("已结束 Run 读档后仍判定为结束",
                FlowController.restored(player, doneBack, new RecordingAdapter()).isOver(),
                "isOver 为 false");
    }

    /** 用固定种子真跑一段 Run（含战斗结算）→ 存盘 → 读档 → 续玩状态一致。 */
    private static void flowSaveLoadRoundTrip() {
        Player player = freshPlayer(2);
        RecordingAdapter adapter = new RecordingAdapter();
        FlowController flow = FlowController.startRun(player, RandomSource.seeded(20240910L), adapter);

        int fought = 0;
        for (int i = 0; i < 8 && !flow.options().isEmpty(); i++) {
            NodeType type = flow.options().get(0).type();
            flow.enter(0);
            if (adapter.takeRequested()) {
                flow.settleFight(type, FightResult.PLAYER_WON);
                fought++;
            }
        }
        check("推进过程中至少结算过 1 场战斗", fought > 0, "fought=" + fought);

        RunSummary before = flow.summary();
        SaveData.World world = SaveManager.captureWorld(player, before);
        SaveData.World reloaded = SaveManager.fromText(SaveManager.toText(world));
        check("存档带 Run 状态", reloaded.hasRun(), "run 为 null");

        Player restoredPlayer = SaveManager.restorePlayer(reloaded);
        FlowController restored = FlowController.restored(restoredPlayer, reloaded.run(), adapter);
        check("读档后 Run 与原推进完全一致", sameRun(before, restored.summary()),
                diffRun(before, restored.summary()));
        check("读档后队伍与背包一致",
                bagCounts(restoredPlayer).equals(bagCounts(player))
                        && restoredPlayer.getPartySize() == player.getPartySize(),
                "队伍 " + restoredPlayer.getPartySize() + "/" + player.getPartySize()
                        + "，背包 " + bagCounts(restoredPlayer) + "/" + bagCounts(player));

        // 续玩一步：读档后的实例仍能继续推进（可选节点或必然节点至少有一个可用）。
        boolean canContinue = !restored.options().isEmpty() || restored.nextMilestone() != null
                || restored.isOver();
        check("读档后可继续推进（或 Run 已结束）", canContinue, "无可用节点也无必然节点");
        check("toText 单行摘要可读", flow.toText().startsWith("RUN|") , flow.toText());
    }

    /** v1 旧档（无 NAME / RUN 段）应能加载：name=null、hasRun()=false、队伍可重建。 */
    private static void legacyV1Save() {
        Player player = freshPlayer(2);
        String species0 = player.getParty().get(0).getSpecies().getId();
        String species1 = player.getParty().get(1).getSpecies().getId();
        List<Item> items = GameData.instance().allItems();
        String itemId = items.isEmpty() ? "i_potion" : items.get(0).getId();

        String v1 = "BAO01SAVE\n"
                + "VERSION 1\n"
                + "PT 2 1\n"
                + "POKE " + species0 + "|10|20\n"
                + "POKE " + species1 + "|15|40\n"
                + "BAG 1\n"
                + "ITEM " + itemId + "|2\n";

        SaveData.World world = SaveManager.fromText(v1);
        check("v1 档版本号保留为 1", world.version() == 1, "实际 " + world.version());
        check("v1 档不含 Run 状态", !world.hasRun(), "run 不为 null");
        check("v1 档训练家名为 null（未记录）", world.player().name() == null,
                "实际 " + world.player().name());

        Player restored = SaveManager.restorePlayer(world);
        check("v1 档队伍可重建", restored.getPartySize() == 2,
                "期望 2 只，实际 " + restored.getPartySize());
        check("v1 档出战位保留", restored.getActiveIndex() == 1,
                "期望 1，实际 " + restored.getActiveIndex());
        check("v1 档缺省训练家名生效", SaveData.DEFAULT_PLAYER_NAME.equals(restored.getName()),
                "实际 " + restored.getName());
        check("v1 档背包可重建", bagCounts(restored).getOrDefault(itemId, 0) == 2,
                "实际 " + bagCounts(restored));

        // v1 文本 → v2 采样：应为「无 Run」的世界存档，且能被 v2 解析器读回。
        SaveData.World upgraded = SaveManager.captureWorld(restored);
        check("v1 采样后升级为 v" + SaveData.FORMAT_VERSION,
                upgraded.version() == SaveData.FORMAT_VERSION, "实际 " + upgraded.version());
        check("升级后的存档仍可解析",
                expectSaveException(() -> SaveManager.fromText(SaveManager.toText(upgraded))) == null,
                "升级后的文本被拒绝");

        // Run 段语法错误必须被拒绝，而不是静默降级。
        String v2 = SaveManager.toText(SaveManager.captureWorld(
                SaveManager.restorePlayer(world), sampleRunSummary()));
        check("Run 段基线文本含 GYM 必然节点", v2.contains(" GYM PLAYING"),
                "基线文本不含预期片段");
        check("未知节点种类被拒绝", expectSaveException(() -> SaveManager.fromText(
                v2.replace(" GYM PLAYING", " NOT_A_NODE PLAYING"))) != null,
                "未抛出 SaveException");
        check("未知结局被拒绝", expectSaveException(() -> SaveManager.fromText(
                v2.replace(" GYM PLAYING", " GYM NOT_AN_ENDING"))) != null,
                "未抛出 SaveException");
        check("Run 段基线本身可解析",
                expectSaveException(() -> SaveManager.fromText(v2)) == null, "基线文本被拒绝");
    }

    // ---------- Run 测试数据与比对 ----------

    /** 一份进行中的 Run 快照（所有字段都非缺省；必然节点等派生字段与状态自洽）。 */
    private static RunSummary sampleRunSummary() {
        StoryFlags story = new StoryFlags(true, true, true, true, true, true);
        CaptureLedger ledger = CaptureLedger.empty().record("焰尾狐").record("焰尾狐").record("水箭龟");
        List<RouteOption> options = List.of(
                RouteOption.of(NodeType.WILD, "野生的 焰尾狐", 1),
                new RouteOption(NodeType.GYM, "道馆战", 2, true));
        List<ShopOffer> shop = List.of(
                new ShopOffer("i_potion", 150, "回复 20 HP"),
                new ShopOffer("i_super_potion", 350, "回复 50 HP"));
        // AP 耗尽 → 派生的 nextMilestone() 为 GYM，与快照记录一致。
        return new RunSummary(3, 0, 8, 1234, story, ledger, options,
                NodeType.GYM, true, true, false, false, false, false, null, shop);
    }

    /** 一份已结束（真结局）的 Run 快照：验证 ending 与终局标记的落盘。 */
    private static RunSummary finishedRunSummary() {
        StoryFlags story = new StoryFlags(true, true, true, true, true, true);
        CaptureLedger ledger = CaptureLedger.empty().record("焰尾狐");
        return new RunSummary(8, 4, 12, 777, story, ledger, List.of(),
                null, false, false, true, false, true, true, Ending.TRUE_CLEAR, List.of());
    }

    private static boolean sameRun(RunSummary a, RunSummary b) {
        return diffRun(a, b).isEmpty();
    }

    /** 逐字段比对，返回首个不一致的说明（全部一致返回空串）。 */
    private static String diffRun(RunSummary a, RunSummary b) {
        if (a == null || b == null) {
            return "快照为 null：" + a + " vs " + b;
        }
        if (a.segmentNo() != b.segmentNo()) {
            return "段号 " + a.segmentNo() + " vs " + b.segmentNo();
        }
        if (a.apLeft() != b.apLeft()) {
            return "剩余 AP " + a.apLeft() + " vs " + b.apLeft();
        }
        if (a.apCap() != b.apCap()) {
            return "AP 上限 " + a.apCap() + " vs " + b.apCap();
        }
        if (a.gold() != b.gold()) {
            return "金币 " + a.gold() + " vs " + b.gold();
        }
        if (!a.story().equals(b.story())) {
            return "剧情线 " + a.story() + " vs " + b.story();
        }
        if (!a.ledger().snapshot().equals(b.ledger().snapshot())) {
            return "抓捕账本 " + a.ledger().snapshot() + " vs " + b.ledger().snapshot();
        }
        if (!a.options().equals(b.options())) {
            return "可选节点 " + a.options() + " vs " + b.options();
        }
        if (a.nextMilestone() != b.nextMilestone()) {
            return "必然节点 " + a.nextMilestone() + " vs " + b.nextMilestone();
        }
        if (a.inRoute() != b.inRoute()) {
            return "阶段 inRoute " + a.inRoute() + " vs " + b.inRoute();
        }
        if (a.gymRetried() != b.gymRetried()) {
            return "道馆重试 " + a.gymRetried() + " vs " + b.gymRetried();
        }
        if (a.eliteCleared() != b.eliteCleared()) {
            return "四天王通过 " + a.eliteCleared() + " vs " + b.eliteCleared();
        }
        if (a.eliteRetried() != b.eliteRetried()) {
            return "四天王重试 " + a.eliteRetried() + " vs " + b.eliteRetried();
        }
        if (a.championCleared() != b.championCleared()) {
            return "冠军通过 " + a.championCleared() + " vs " + b.championCleared();
        }
        if (a.invasionCleared() != b.invasionCleared()) {
            return "侵略战通过 " + a.invasionCleared() + " vs " + b.invasionCleared();
        }
        if (a.ending() != b.ending()) {
            return "结局 " + a.ending() + " vs " + b.ending();
        }
        if (!a.shopStock().equals(b.shopStock())) {
            return "商店货架 " + a.shopStock() + " vs " + b.shopStock();
        }
        return "";
    }

    /** 只记录「流程是否发起过战斗」的适配器（无头测试用，不驱动界面）。 */
    private static final class RecordingAdapter implements BattleAdapter {

        private boolean requested;

        @Override
        public List<String> startBattle(Player p, List<Pokemon> foes, boolean canFlee) {
            requested = true;
            return List.of("（测试适配器：对手 " + foes.size() + " 只）");
        }

        private boolean takeRequested() {
            boolean r = requested;
            requested = false;
            return r;
        }
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
