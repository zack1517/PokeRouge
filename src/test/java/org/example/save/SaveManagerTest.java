package org.example.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.example.GameSession;
import org.example.growth.GrowthProgress;
import org.example.integration.PokemonBattleAdapter;
import org.example.model.Item;
import org.example.model.Option;
import org.example.model.OptionType;
import org.example.model.Player;
import org.example.model.RouteConfig;
import org.example.model.RoutePhase;
import org.example.model.RunData;
import org.example.model.Stats;
import org.example.pokemon.domain.Species;
import org.example.pokemon.service.PokemonService;
import org.example.pokemon.service.PokemonServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 存档编排测试：覆盖「存档 → 读档 → 继续玩」的完整闭环，以及 4 个档位彼此独立。
 *
 * <p>这是存档系统最上层的验收面：玩家按下保存后关掉游戏、第二天读档，队伍、背包、路线进度（段号 /
 * 行动点 / 阶段 / 金币）、地图段号与图鉴成长都必须回到原样。所有用例都在 {@link TempDir} 内进行，
 * 不触碰开发者的真实存档。</p>
 */
class SaveManagerTest {

    private final PokemonService service = new PokemonServiceImpl(new GrowthProgress());

    private Player newPlayer(String name) {
        Species species = service.getInitialPool().get(0);
        return PokemonBattleAdapter.createBattlePlayer(name,
                service.createPokemon(species.getId(), 12));
    }

    /** 完整闭环：存档 → 读档后队伍、训练家名、出战下标、背包、段号与路线进度全部还原。 */
    @Test
    void 存档后读档还原完整进度(@TempDir Path dir) {
        SaveManager manager = new SaveManager(new SaveStore(dir));
        Player player = newPlayer("小明");
        GameSession session = new GameSession(player, new GrowthProgress());
        Item potion = item();
        assertNotNull(potion, "测试数据应包含 i_potion");
        int potionsBefore = bagCounts(player).getOrDefault("i_potion", 0);
        player.getBag().add(potion, 3);
        session.setSegment(3);
        session.setMapBackground("map/forest.png");
        RunData run = session.getRogueRunData();
        run.setAp(4);
        run.setApMax(12);
        run.setPhase(RoutePhase.GYM);
        run.setRetryUsed(1);
        run.setGold(275);

        manager.save(SaveSlot.SLOT_2, player, session);
        GameSession restored = manager.load(SaveSlot.SLOT_2).orElseThrow();

        assertEquals("小明", restored.getPlayer().getName());
        assertEquals(1, restored.getPlayer().getPartySize());
        assertEquals(0, restored.getPlayer().getActiveIndex());
        assertEquals(potionsBefore + 3, bagCounts(restored.getPlayer()).get("i_potion"),
                "背包数量应还原");
        assertEquals(bagCounts(player), bagCounts(restored.getPlayer()), "整个背包应逐项一致");
        assertEquals(3, restored.getSegment());
        assertEquals("map/forest.png", restored.getMapBackground());
        RunData restoredRun = restored.getRogueRunData();
        assertEquals(4, restoredRun.getAp(), "行动点应还原");
        assertEquals(12, restoredRun.getApMax(), "行动点上限应还原");
        assertEquals(RoutePhase.GYM, restoredRun.getPhase(), "推进阶段应还原");
        assertEquals(1, restoredRun.getRetryUsed(), "已用重试次数应还原");
        assertEquals(275, restoredRun.getGold(), "金币应还原");
        assertFalse(restoredRun.isGameOver());
    }

    /** 读档后精灵的中途状态（残血 / 技能 PP）必须保留，不能满血复活。 */
    @Test
    void 读档保留精灵中途状态(@TempDir Path dir) {
        SaveManager manager = new SaveManager(new SaveStore(dir));
        Player player = newPlayer("小明");
        GameSession session = new GameSession(player, new GrowthProgress());
        int wounded = player.getActive().getMaxHp() / 4;
        player.getActive().takeDamage(player.getActive().getMaxHp() - wounded);
        player.getActive().getMoveSlots().get(0).usePp();

        manager.save(SaveSlot.SLOT_1, player, session);
        GameSession restored = manager.load(SaveSlot.SLOT_1).orElseThrow();

        assertEquals(wounded, restored.getPlayer().getActive().getCurrentHp());
        assertEquals(player.getActive().getMoveSlots().get(0).getPp(),
                restored.getPlayer().getActive().getMoveSlots().get(0).getPp());
        Stats expectedIvs = player.getActive().getIvs();
        Stats actualIvs = restored.getPlayer().getActive().getIvs();
        assertEquals(expectedIvs.getHp(), actualIvs.getHp());
        assertEquals(expectedIvs.getAttack(), actualIvs.getAttack());
        assertEquals(expectedIvs.getDefense(), actualIvs.getDefense());
        assertEquals(expectedIvs.getSpAttack(), actualIvs.getSpAttack());
        assertEquals(expectedIvs.getSpDefense(), actualIvs.getSpDefense());
        assertEquals(expectedIvs.getSpeed(), actualIvs.getSpeed());
    }

    /** 路线节点选项与必然节点都要能存档（否则读档后玩家会面对一个空的节点列表）。 */
    @Test
    void 路线节点选项随存档往返(@TempDir Path dir) {
        SaveManager manager = new SaveManager(new SaveStore(dir));
        Player player = newPlayer("小明");
        GameSession session = new GameSession(player, new GrowthProgress());
        session.getRogueOptions().clear();
        Option wild = new Option("野生遭遇", OptionType.WILD, 0, "遇到一只野生精灵");
        wild.markConsumed();
        session.getRogueOptions().add(wild);
        session.getRogueOptions().add(new Option("医院", OptionType.HOSPITAL, 1, "回复全队"));
        Option gym = new Option("道馆战", OptionType.GYM, 0, "打倒馆主");
        session.getRogueRunData().setMandatoryOption(gym);

        manager.save(SaveSlot.SLOT_3, player, session);
        GameSession restored = manager.load(SaveSlot.SLOT_3).orElseThrow();

        List<Option> options = restored.getRogueOptions();
        assertEquals(2, options.size());
        assertEquals("野生遭遇", options.get(0).getName());
        assertEquals(OptionType.WILD, options.get(0).getType());
        assertTrue(options.get(0).isConsumed(), "「已走过」标记应随存档往返");
        assertEquals(1, options.get(1).getCost());
        assertFalse(options.get(1).isConsumed(), "未走过的节点读档后仍可进入");
        assertNotNull(restored.getRogueRunData().getMandatoryOption());
        assertEquals("道馆战", restored.getRogueRunData().getMandatoryOption().getName());
        assertEquals(OptionType.GYM, restored.getRogueRunData().getMandatoryOption().getType());
    }

    /** 没有必然节点的段读档后必须仍是空，不能凭上次的残留。 */
    @Test
    void 无必然节点时读档为空(@TempDir Path dir) {
        SaveManager manager = new SaveManager(new SaveStore(dir));
        Player player = newPlayer("小明");
        GameSession session = new GameSession(player, new GrowthProgress());
        session.getRogueRunData().setMandatoryOption(null);

        manager.save(SaveSlot.SLOT_1, player, session);

        assertNull(manager.load(SaveSlot.SLOT_1).orElseThrow()
                .getRogueRunData().getMandatoryOption());
    }

    /** 结束状态也要存档：读档后不能把已经结束的远征复活成进行中。 */
    @Test
    void 远征结束状态随存档往返(@TempDir Path dir) {
        SaveManager manager = new SaveManager(new SaveStore(dir));
        Player player = newPlayer("小明");
        GameSession session = new GameSession(player, new GrowthProgress());
        session.getRogueRunData().setGameOver(true);

        manager.save(SaveSlot.SLOT_1, player, session);

        GameSession restored = manager.load(SaveSlot.SLOT_1).orElseThrow();
        assertTrue(restored.getRogueRunData().isGameOver());
        // UI 读档时据 isRogueRunFinished 判断「这一轮没得继续了」：改为引导玩家在同一档位开新一轮
        // 或回退到上一个存档点，但结束标记本身必须随存档保留
        assertTrue(restored.isRogueRunFinished(), "已结束的一轮读档后仍应判定为已结束");
    }

    /** 空档位读档返回空，UI 据此提示「该档位没有存档」。 */
    @Test
    void 空档位读档返回空(@TempDir Path dir) {
        SaveManager manager = new SaveManager(new SaveStore(dir));

        assertTrue(manager.load(SaveSlot.SLOT_4).isEmpty());
    }

    /** 存档损坏时抛出格式异常，由上层提示，而不是返回一个半成品会话。 */
    @Test
    void 损坏存档读档抛格式异常(@TempDir Path dir) throws IOException {
        SaveStore store = new SaveStore(dir);
        Files.createDirectories(store.slotDir(SaveSlot.SLOT_1));
        Files.writeString(store.saveFile(SaveSlot.SLOT_1), "VERSION|1\nSEGMENT|abc",
                StandardCharsets.UTF_8);

        assertThrows(SaveFormatException.class,
                () -> new SaveManager(store).load(SaveSlot.SLOT_1));
    }

    /** 存档里的物种已不存在时视为空档，避免玩家带着一支空队伍进入游戏。 */
    @Test
    void 全部精灵物种失效时读档返回空(@TempDir Path dir) throws IOException {
        SaveStore store = new SaveStore(dir);
        Files.createDirectories(store.slotDir(SaveSlot.SLOT_1));
        Files.writeString(store.saveFile(SaveSlot.SLOT_1), String.join("\n",
                "VERSION|1",
                "PLAYER|小明",
                "SEGMENT|1",
                "RUN|1|0|0",
                "PP|不存在的物种|5|1|1|1|1|1|1|0|NONE|0|0|0|10"),
                StandardCharsets.UTF_8);

        assertTrue(new SaveManager(store).load(SaveSlot.SLOT_1).isEmpty());
    }

    /** 自动存档成功返回 true，且确实写入了文件。 */
    @Test
    void 自动存档成功返回真(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);
        SaveManager manager = new SaveManager(store);
        Player player = newPlayer("小明");

        assertTrue(manager.autoSave(SaveSlot.SLOT_1, player,
                new GameSession(player, new GrowthProgress())));
        assertTrue(store.exists(SaveSlot.SLOT_1));
    }

    /** 磁盘不可写时自动存档只返回 false，绝不抛出异常打断玩家（例如回到主菜单时）。 */
    @Test
    void 自动存档失败不抛异常(@TempDir Path dir) throws IOException {
        Path blocker = dir.resolve("blocker");
        Files.writeString(blocker, "占位文件", StandardCharsets.UTF_8);
        SaveManager manager = new SaveManager(new SaveStore(blocker));
        Player player = newPlayer("小明");

        boolean saved = manager.autoSave(SaveSlot.SLOT_1, player,
                new GameSession(player, new GrowthProgress()));

        assertFalse(saved);
    }

    /** 手动存档失败要抛出异常，让 UI 明确提示「保存失败」。 */
    @Test
    void 手动存档失败抛异常(@TempDir Path dir) throws IOException {
        Path blocker = dir.resolve("blocker");
        Files.writeString(blocker, "占位文件", StandardCharsets.UTF_8);
        SaveManager manager = new SaveManager(new SaveStore(blocker));
        Player player = newPlayer("小明");

        assertThrows(RuntimeException.class, () -> manager.save(SaveSlot.SLOT_1, player,
                new GameSession(player, new GrowthProgress())));
    }

    /** 新游戏应清空目标档位：旧进度与旧图鉴成长都不能残留。 */
    @Test
    void 新游戏清空目标档位(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);
        SaveManager manager = new SaveManager(store);
        GrowthProgress old = store.createGrowth(SaveSlot.SLOT_1);
        for (int i = 0; i < 10; i++) {
            old.recordCapture("bulbasaur");
        }
        manager.save(SaveSlot.SLOT_1, newPlayer("旧玩家"), new GameSession(newPlayer("旧玩家"),
                new GrowthProgress()));

        Species species = service.getInitialPool().get(0);
        GameSession fresh = manager.newGame(SaveSlot.SLOT_1, "新玩家",
                service.createPokemon(species.getId(), 5));

        assertEquals("新玩家", fresh.getPlayer().getName());
        assertEquals(1, fresh.getPlayer().getPartySize());
        assertEquals(0, fresh.getGrowthProgress().globalIvBonus(), "新档的图鉴成长必须从零开始");
        assertEquals(0, store.loadGrowth(SaveSlot.SLOT_1).globalIvBonus(),
                "磁盘上的旧成长记录也必须被清掉");
        assertFalse(store.exists(SaveSlot.SLOT_1), "清空后尚未保存，应没有进度快照");
    }

    /** 每个档位各自独立的图鉴成长：换档位重新开始累积。 */
    @Test
    void 图鉴成长按档位独立(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);
        SaveManager manager = new SaveManager(store);

        GameSession slot1 = manager.newGame(SaveSlot.SLOT_1, "小明",
                service.createPokemon(service.getInitialPool().get(0).getId(), 5));
        GameSession slot2 = manager.newGame(SaveSlot.SLOT_2, "小红",
                service.createPokemon(service.getInitialPool().get(1).getId(), 5));

        for (int i = 0; i < 6; i++) {
            slot1.getGrowthProgress().recordCapture("bulbasaur");
        }

        assertEquals(3, slot1.getGrowthProgress().globalIvBonus(), "档位 1 累积 6 次捕捉 → 3 点加成");
        assertEquals(0, slot2.getGrowthProgress().globalIvBonus(), "档位 2 必须从零开始");
        assertEquals(3, store.loadGrowth(SaveSlot.SLOT_1).globalIvBonus(),
                "档位 1 的成长应已落盘");
        assertEquals(0, store.loadGrowth(SaveSlot.SLOT_2).globalIvBonus(),
                "档位 2 不应受到档位 1 的影响");
    }

    /** 「上一个存档点」能读回上一份进度：战败之后据此退回进战斗之前重来（SL 大法）。 */
    @Test
    void 读档可回退到上一个存档点(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);
        SaveManager manager = new SaveManager(store);
        Player player = newPlayer("小明");
        GameSession session = new GameSession(player, new GrowthProgress());
        session.setSegment(2);
        RunData run = session.getRogueRunData();
        run.setAp(5);
        run.setGold(300);
        manager.save(SaveSlot.SLOT_1, player, session);

        run.setAp(1);
        run.setGold(40);
        run.setGameOver(true); // 模拟「又走了一步之后战败」
        manager.save(SaveSlot.SLOT_1, player, session);

        assertTrue(store.hasPrevious(SaveSlot.SLOT_1), "第二次落盘应留下上一个存档点");
        GameSession previous = manager.loadPrevious(SaveSlot.SLOT_1).orElseThrow();
        assertEquals(5, previous.getRogueRunData().getAp(), "回退读档应拿回上一份行动点");
        assertEquals(300, previous.getRogueRunData().getGold(), "回退读档应拿回上一份金币");
        assertFalse(previous.isRogueRunFinished(), "回退后应回到还没结束的那一刻");

        GameSession latest = manager.load(SaveSlot.SLOT_1).orElseThrow();
        assertEquals(1, latest.getRogueRunData().getAp(), "当前档位仍是最新那份，回退前不受影响");
        assertTrue(latest.isRogueRunFinished());
    }

    /** 没有上一个存档点时回退读档返回空，UI 据此提示「无法回退」而不是把档读坏。 */
    @Test
    void 没有上一个存档点时回退读档返回空(@TempDir Path dir) {
        SaveManager manager = new SaveManager(new SaveStore(dir));

        assertTrue(manager.loadPrevious(SaveSlot.SLOT_3).isEmpty());
    }

    /** 同一档位开新一轮：图鉴成长跨轮继承，远征进度（金币 / 队伍 / 回合数据）回到初始状态。 */
    @Test
    void 同档位开新一轮保留图鉴成长(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);
        SaveManager manager = new SaveManager(store);
        Species species = service.getInitialPool().get(0);
        GameSession first = manager.newGame(SaveSlot.SLOT_1, "小明",
                service.createPokemon(species.getId(), 5));
        for (int i = 0; i < 6; i++) {
            first.getGrowthProgress().recordCapture("bulbasaur");
        }
        first.getRogueRunData().setSegment(4);
        first.getRogueRunData().setGold(888);
        manager.save(SaveSlot.SLOT_1, first.getPlayer(), first);
        first.getRogueRunData().setGold(500);
        first.getRogueRunData().setGameOver(true); // 这一轮结束：进度真的变了，于是留下了一个存档点
        manager.save(SaveSlot.SLOT_1, first.getPlayer(), first);
        assertTrue(store.hasPrevious(SaveSlot.SLOT_1));

        GameSession rerun = manager.newRun(SaveSlot.SLOT_1, "小明",
                service.createPokemon(service.getInitialPool().get(1).getId(), 5));

        assertEquals(3, rerun.getGrowthProgress().globalIvBonus(),
                "新一轮必须继承该档位已攒下的图鉴成长（6 次捕捉 → 3 点加成）");
        assertEquals(3, store.loadGrowth(SaveSlot.SLOT_1).globalIvBonus(),
                "继承的成长要能继续落盘到同一个档位");
        assertEquals(RouteConfig.STARTING_GOLD, rerun.getRogueRunData().getGold(),
                "金币回到初始值");
        assertEquals(0, rerun.getRogueRunData().getAp(), "行动点回到初始值");
        assertEquals(0, rerun.getRogueRunData().getSegment(),
                "段号回到「尚未开始远征」（界面兜底显示第 1 段的逻辑不参与这里）");
        assertFalse(rerun.getRogueRunData().isGameOver(), "上一轮的结束标记必须被清掉");
        assertEquals(1, rerun.getPlayer().getPartySize(), "队伍重新从初始精灵开始");
        assertEquals(service.getInitialPool().get(1).getId(),
                rerun.getPlayer().getActive().getSpecies().getId(), "新队伍带的是这次选的初始精灵");
        assertFalse(store.exists(SaveSlot.SLOT_1), "旧进度快照已清掉，等待这一步之后的自动保存");
        assertFalse(store.hasPrevious(SaveSlot.SLOT_1), "同档重开也不该留着上一轮的存档点");
    }

    /** 把进度另存到别的档位时，图鉴成长要跟着一起搬（此后成长写在新档位，不再回流原档位）。 */
    @Test
    void 另存档位时图鉴成长一并搬迁(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);
        SaveManager manager = new SaveManager(store);
        Player player = newPlayer("小明");
        GameSession session = new GameSession(player, store.createGrowth(SaveSlot.SLOT_1));
        for (int i = 0; i < 4; i++) {
            session.getGrowthProgress().recordCapture("bulbasaur");
        }

        manager.save(SaveSlot.SLOT_2, player, session);

        assertEquals(2, store.loadGrowth(SaveSlot.SLOT_2).globalIvBonus(),
                "换档后成长应写到新档位");
        session.getGrowthProgress().recordCapture("charmander"); // 触发一次自动落盘
        assertEquals(2, store.loadGrowth(SaveSlot.SLOT_2).dexEntries().size(),
                "此后的成长落盘应指向新档位");
        assertEquals(1, store.loadGrowth(SaveSlot.SLOT_1).dexEntries().size(),
                "原档位的成长文件不应再被写入（记录仍只有 bulbasaur）");
    }

    /** 一个档位有存档不影响另一个档位的「空档」状态。 */
    @Test
    void 档位互不影响(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);
        SaveManager manager = new SaveManager(store);
        Player player = newPlayer("小明");
        manager.save(SaveSlot.SLOT_1, player, new GameSession(player, new GrowthProgress()));

        assertTrue(store.exists(SaveSlot.SLOT_1));
        assertFalse(store.exists(SaveSlot.SLOT_2));
        assertTrue(manager.load(SaveSlot.SLOT_2).isEmpty());
        assertEquals(SaveSlot.SLOT_1, store.statuses().get(0).slot());
        assertTrue(store.statuses().get(1).empty());
    }

    /** 采集的快照应带上存档时间，供档位列表展示「上次游玩」。 */
    @Test
    void 快照带存档时间(@TempDir Path dir) {
        SaveManager manager = new SaveManager(new SaveStore(dir));
        Player player = newPlayer("小明");
        long before = System.currentTimeMillis();

        SaveData snapshot = manager.snapshot(player, new GameSession(player, new GrowthProgress()));

        assertTrue(snapshot.savedAtMillis() >= before, "应记录当前时间");
        assertEquals(SaveData.FORMAT_VERSION, snapshot.version());
        assertEquals("小明", snapshot.playerName());
        assertEquals(1, snapshot.party().size());
        assertEquals(0, snapshot.run().ap(), "新会话尚未开始远征");
        assertEquals(RouteConfig.STARTING_GOLD, snapshot.run().gold(), "新会话带起始金币");
        assertEquals(0, snapshot.segment(), "尚未开始远征时段号为 0");
    }

    /** 默认编排器落在用户主目录下的存档目录，而不是工程目录。 */
    @Test
    void 默认编排器使用用户主目录() {
        SaveManager manager = SaveManager.defaultManager();

        assertEquals(SaveStore.defaultRoot(), manager.store().root());
        assertNotNull(manager.store());
    }

    private static Item item() {
        return org.example.data.GameData.instance().item("i_potion");
    }

    /** 背包按道具 id 汇总成 map，便于整包比对（初始补给数量随数据表变化，不能写死）。 */
    private static java.util.Map<String, Integer> bagCounts(Player player) {
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        for (org.example.model.ItemStack stack : player.getBag().getAll()) {
            counts.merge(stack.getItem().getId(), stack.getCount(), Integer::sum);
        }
        return counts;
    }
}
