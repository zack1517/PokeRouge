package org.example.model;

import org.example.integration.PokemonBattleAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RogueTurnManager} 的单元测试：覆盖《需求文档》§4 的规则判定 ——
 * 行动点（AP）扣除与重置、节点消耗标记、医院治疗、节点自回血、
 * 必然节点（道馆 / 四天王 / 冠军）触发与推进、失败与重试规则、金币奖惩。
 *
 * <p>本类只测纯规则状态机，不涉及任何战斗模拟与界面。</p>
 */
class RogueTurnManagerTest {

    /** 开一轮远征并清空随机生成的节点，保证断言确定性。 */
    private static RogueTurnManager newManager() {
        return newManager(List.of());
    }

    private static RogueTurnManager newManager(List<PokemonInstance> team) {
        RogueTurnManager manager = new RogueTurnManager();
        manager.startRun(team);
        manager.getRunData().getAvailableOptions().clear();
        return manager;
    }

    /** 往当前段的节点列表里塞一个指定类型与消耗的节点（清空后唯一的节点）。 */
    private static Option addOption(RogueTurnManager manager, OptionType type, int cost) {
        Option option = new Option(type.getDisplayName(), type, cost, "测试节点");
        manager.getRunData().getAvailableOptions().add(option);
        return option;
    }

    private static PokemonInstance wild(int level) {
        return new PokemonInstance(PokemonBattleAdapter.createWildPokemon(level).orElseThrow());
    }

    // ------------------------------------------------------------------
    // 开轮与行动点
    // ------------------------------------------------------------------

    @Test
    void testStartRun_resetsSegmentApAndGold() {
        RogueTurnManager manager = newManager();
        RunData data = manager.getRunData();

        assertEquals(RunData.FIRST_SEGMENT, data.getSegment(), "新远征从第 1 段开始");
        assertEquals(RoutePhase.EXPLORING, data.getPhase(), "起步阶段为路线探索");
        assertEquals(RouteConfig.apLimitForSegment(1), data.getApMax(), "行动点上限取第 1 段配置");
        assertEquals(data.getApMax(), data.getAp(), "每段开始时行动点重置为该段上限");
        assertEquals(RouteConfig.STARTING_GOLD, data.getGold(), "新远征带起始金币");
        assertFalse(manager.isRunFinished(), "刚开轮不应已结束");
    }

    @Test
    void testEnterSegment_apLimitGrowsWithProgressAndResetsAp() {
        RogueTurnManager manager = newManager();
        manager.getRunData().setAp(1);

        manager.enterSegment(3);
        RunData data = manager.getRunData();

        assertEquals(3, data.getSegment());
        assertEquals(RouteConfig.apLimitForSegment(3), data.getApMax(), "段数越靠后行动点上限越高");
        assertTrue(data.getApMax() > RouteConfig.apLimitForSegment(1), "上限随进度提升");
        assertEquals(data.getApMax(), data.getAp(), "进入新段时行动点被重置");
        assertEquals(0, data.getRetryUsed(), "每段开始时重置失败重试机会");
        assertFalse(data.getAvailableOptions().isEmpty(), "新段应生成路线节点");
    }

    @Test
    void testConsumeNode_deductsApAndMarksConsumed() {
        RogueTurnManager manager = newManager();
        Option trainer = addOption(manager, OptionType.TRAINER, OptionType.TRAINER.getApCost());
        int before = manager.getRunData().getAp();

        assertTrue(manager.consumeNode(trainer), "行动点足够时应可进入节点");

        assertEquals(before - 2, manager.getRunData().getAp(), "路人节点扣除 2 点");
        assertTrue(trainer.isConsumed(), "走过的节点应打上已消耗标记");
        assertEquals(1, manager.getRunData().getSegment(), "进入节点不推进段号");
    }

    @Test
    void testConsumeNode_freeWildCostsNothing() {
        RogueTurnManager manager = newManager();
        Option free = addOption(manager, OptionType.WILD, 0);
        int before = manager.getRunData().getAp();

        assertTrue(manager.consumeNode(free));

        assertEquals(before, manager.getRunData().getAp(), "野外精灵有概率消耗 0 点");
        assertTrue(free.isConsumed());
    }

    @Test
    void testConsumeNode_insufficientApKeepsStateUnchanged() {
        RogueTurnManager manager = newManager();
        Option trainer = addOption(manager, OptionType.TRAINER, 2);
        manager.getRunData().setAp(1);

        assertFalse(manager.consumeNode(trainer), "行动点不足时不能进入节点");

        assertEquals(1, manager.getRunData().getAp(), "失败时不扣点");
        assertFalse(trainer.isConsumed(), "失败时不应标记已走过");
    }

    @Test
    void testConsumeNode_rejectsRepeatedAndForeignOptions() {
        RogueTurnManager manager = newManager();
        Option first = addOption(manager, OptionType.HOSPITAL, 1);

        assertTrue(manager.consumeNode(first));
        assertFalse(manager.consumeNode(first), "已走过的节点不能再次进入");

        Option foreign = new Option("外来节点", OptionType.WILD, 1, "不属于本段");
        assertFalse(manager.consumeNode(foreign), "不属于本段的节点应被拒绝");
        assertFalse(manager.consumeNode(null), "空节点应被拒绝");
    }

    @Test
    void testHasSelectableOption_respectsApAndConsumedFlag() {
        RogueTurnManager manager = newManager();
        Option gymLike = addOption(manager, OptionType.TRAINER, 5);
        manager.getRunData().setAp(4);

        assertFalse(manager.hasSelectableOption(), "行动点不够的节点不算可走");

        manager.getRunData().setAp(5);
        assertTrue(manager.hasSelectableOption(), "行动点刚好够时应可走");

        gymLike.markConsumed();
        assertFalse(manager.hasSelectableOption(), "已走过的节点不算可走");
    }

    // ------------------------------------------------------------------
    // 必然节点触发与推进
    // ------------------------------------------------------------------

    @Test
    void testAdvanceAfterNode_triggersGymWhenApExhausted() {
        RogueTurnManager manager = newManager();
        addOption(manager, OptionType.TRAINER, 2);
        manager.getRunData().setAp(0);

        assertTrue(manager.advanceAfterNode(), "行动点耗尽应触发必然节点");

        RunData data = manager.getRunData();
        assertEquals(RoutePhase.GYM, data.getPhase(), "行动点耗尽后触发道馆战");
        assertNotNull(data.getMandatoryOption(), "应生成道馆战节点");
        assertEquals(OptionType.GYM, data.getMandatoryOption().getType());
        assertEquals(0, data.getMandatoryOption().getCost(), "必然节点不占用行动点");
        assertTrue(manager.isAtMandatoryNode());
    }

    @Test
    void testAdvanceAfterNode_doesNothingWhileApRemains() {
        RogueTurnManager manager = newManager();
        addOption(manager, OptionType.WILD, 1);

        assertFalse(manager.advanceAfterNode(), "还有可走节点时不触发道馆战");

        assertEquals(RoutePhase.EXPLORING, manager.getRunData().getPhase());
    }

    @Test
    void testAdvanceAfterNode_triggersGymWhenNoNodeLeft() {
        RogueTurnManager manager = newManager();
        Option only = addOption(manager, OptionType.HOSPITAL, 1);
        manager.consumeNode(only);
        manager.getRunData().setAp(5);

        assertTrue(manager.advanceAfterNode(), "本段已无节点可走时直接触发道馆战");

        assertEquals(RoutePhase.GYM, manager.getRunData().getPhase());
    }

    @Test
    void testGymVictory_entersNextSegmentAndResetsAp() {
        RogueTurnManager manager = newManager();
        manager.enterPhase(RoutePhase.GYM);
        int goldBefore = manager.getRunData().getGold();

        manager.resolveMandatoryVictory();

        RunData data = manager.getRunData();
        assertEquals(2, data.getSegment(), "道馆胜利后进入下一段");
        assertEquals(RoutePhase.EXPLORING, data.getPhase(), "新段回到路线探索");
        assertEquals(RouteConfig.apLimitForSegment(2), data.getAp(), "新段行动点重置为该段上限");
        assertEquals(goldBefore + RouteConfig.gymWinGold(1), data.getGold(), "道馆胜利发放金币");
    }

    @Test
    void testLastSegmentGymVictory_leadsToEliteFourThenChampionThenClear() {
        RogueTurnManager manager = newManager();
        manager.enterSegment(RouteConfig.TOTAL_SEGMENTS);

        manager.enterPhase(RoutePhase.GYM);
        manager.resolveMandatoryVictory();
        assertEquals(RoutePhase.ELITE_FOUR, manager.getRunData().getPhase(),
                "末段道馆胜利后进入四天王连打");

        manager.resolveMandatoryVictory();
        assertEquals(RoutePhase.CHAMPION, manager.getRunData().getPhase(),
                "四天王胜利后进入冠军战");

        manager.resolveMandatoryVictory();
        assertTrue(manager.isCleared(), "冠军胜利即通关");
        assertTrue(manager.isRunFinished(), "通关后本轮结束");
    }

    @Test
    void testMandatoryDefeat_gymAllowsOneRetryThenEndsRun() {
        RogueTurnManager manager = newManager();
        manager.enterPhase(RoutePhase.GYM);
        int goldBefore = manager.getRunData().getGold();

        assertTrue(manager.resolveMandatoryDefeat(), "道馆战可失败一次");
        assertEquals(1, manager.getRunData().getRetryUsed());
        assertEquals(goldBefore - RouteConfig.mandatoryDefeatGoldPenalty(1),
                manager.getRunData().getGold(), "首次失败大量扣金币");
        assertFalse(manager.isGameOver(), "首次失败不结束本轮");
        assertNotNull(manager.getMandatoryOption(), "重试时仍保留必然节点");

        assertFalse(manager.resolveMandatoryDefeat(), "重试再败则本轮结束");
        assertTrue(manager.isGameOver());
    }

    @Test
    void testMandatoryDefeat_championEndsRunImmediately() {
        RogueTurnManager manager = newManager();
        manager.enterPhase(RoutePhase.CHAMPION);

        assertFalse(manager.resolveMandatoryDefeat(), "冠军战不可失败");
        assertTrue(manager.isGameOver(), "冠军战败直接结束本轮");
        assertEquals(0, manager.getRunData().getRetryUsed(), "冠军战不消耗重试机会");
    }

    // ------------------------------------------------------------------
    // 医院与节点自回血（§4.4）
    // ------------------------------------------------------------------

    @Test
    void testHospitalNode_fullyRestoresPartyIncludingFainted() {
        PokemonInstance hurt = wild(10);
        PokemonInstance fainted = wild(10);
        hurt.setHp(1);
        fainted.setHp(0);
        RogueTurnManager manager = newManager(List.of(hurt, fainted));
        Option hospital = addOption(manager, OptionType.HOSPITAL, 1);

        assertTrue(manager.resolveNode(hospital), "行动点足够时医院可进入");

        assertEquals(hurt.getMaxHp(), hurt.getCurrentHp(), "受伤宝可梦应回满 HP");
        assertEquals(fainted.getMaxHp(), fainted.getCurrentHp(), "濒死宝可梦应被治疗复活");
        assertFalse(fainted.getPokemon().isFainted());
    }

    @Test
    void testApplyNodeHeal_restoresOneFifthAndSkipsFainted() {
        PokemonInstance hurt = wild(10);
        PokemonInstance fainted = wild(10);
        hurt.setHp(1);
        fainted.setHp(0);
        RogueTurnManager manager = newManager(List.of(hurt, fainted));
        int expected = 1 + RouteConfig.nodeHealAmount(hurt.getMaxHp());

        manager.applyNodeHeal();

        assertEquals(expected, hurt.getCurrentHp(), "未濒死宝可梦回复最大 HP 的 1/5");
        assertEquals(0, fainted.getCurrentHp(), "濒死宝可梦不享受自回血");
        assertTrue(fainted.getPokemon().isFainted(), "濒死宝可梦仍需医院或道具治疗");
    }

    @Test
    void testApplyNodeHeal_neverExceedsMaxHp() {
        PokemonInstance full = wild(10);
        RogueTurnManager manager = newManager(List.of(full));

        manager.applyNodeHeal();

        assertEquals(full.getMaxHp(), full.getCurrentHp(), "满血时自回血不会溢出");
    }

    // ------------------------------------------------------------------
    // 金币（§4.2 / §4.3）
    // ------------------------------------------------------------------

    @Test
    void testSpecialNodeAwardsGold() {
        RogueTurnManager manager = newManager();
        Option special = addOption(manager, OptionType.SPECIAL, RouteConfig.SPECIAL_AP_COST);
        int before = manager.getRunData().getGold();

        assertTrue(manager.resolveNode(special));

        assertEquals(before + RouteConfig.specialGold(1), manager.getRunData().getGold(),
                "特殊事件应发放对应金币");
    }

    @Test
    void testAwardWinGold_matchesConfigPerNodeType() {
        RogueTurnManager manager = newManager();
        RunData data = manager.getRunData();
        data.setGold(0);

        assertEquals(RouteConfig.trainerWinGold(1), manager.awardWinGold(OptionType.TRAINER));
        assertEquals(RouteConfig.trainerWinGold(1), data.getGold(), "路人胜利金币写入余额");

        data.setGold(0);
        assertEquals(RouteConfig.wildWinGold(1), manager.awardWinGold(OptionType.WILD));

        data.setGold(0);
        assertEquals(0, manager.awardWinGold(OptionType.HOSPITAL), "医院不产金币");
        assertEquals(0, manager.awardWinGold(OptionType.SHOP), "商店不产金币");
        assertEquals(0, manager.awardWinGold(null), "空类型不产金币");
    }

    @Test
    void testApplyDefeatPenalty_neverGoesNegative() {
        RogueTurnManager manager = newManager();
        manager.getRunData().setGold(10);
        int penalty = RouteConfig.defeatGoldPenalty(1);
        assertTrue(penalty > 10, "测试前提：惩罚高于余额");

        assertEquals(10, manager.applyDefeatPenalty(OptionType.TRAINER), "返回实际扣除的金币");
        assertEquals(0, manager.getRunData().getGold(), "金币不会被扣成负数");

        assertEquals(0, manager.applyDefeatPenalty(null), "空类型不扣金币");
        assertEquals(0, manager.applyDefeatPenalty(OptionType.SHOP), "商店节点无战败惩罚");
    }

    @Test
    void testMandatoryDefeatPenaltyIsHeavierThanRouteDefeat() {
        assertTrue(RouteConfig.mandatoryDefeatGoldPenalty(1) > RouteConfig.defeatGoldPenalty(1),
                "道馆 / 四天王战败的代价应显著高于路人 / 野外战败");
    }
}