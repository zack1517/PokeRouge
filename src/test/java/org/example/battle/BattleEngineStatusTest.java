package org.example.battle;

import org.example.data.GameData;
import org.example.model.ElementType;
import org.example.model.Item;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.MoveEffect;
import org.example.model.MoveSlot;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stats;
import org.example.model.StatusCondition;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 战斗异常状态测试（参照原版宝可梦）。
 *
 * <p>覆盖：异常状态的施加与属性免疫、麻痹/睡眠/冰冻/混乱对行动的影响、中毒/剧毒/灼伤的持续伤害、
 * 解除道具，以及换宠与倒下时对挥发性异常（混乱）的清除。</p>
 *
 * <p>测试数据全部手工构造（不依赖数据文件），并用 {@link ScriptedRandom} 固定随机数，
 * 使「是否命中概率判定、是否自伤、是否解冻、伤害浮动」完全可复现。为让随机源只影响被测行为，
 * 玩家精灵速度（85）远高于敌方（9），因此先后手恒为玩家先动，不会消耗 {@code nextBoolean()}。</p>
 */
class BattleEngineStatusTest {

    /** 低威力普通系物理技能：让玩家出手但不至于击倒对手。 */
    private static final Move TAP = new Move("m_tap", "轻拍", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 1, 100, 40);

    /** 无效果的变化技能：敌方持有它，就能「行动但不改变任何状态」，便于隔离被测效果。 */
    private static final Move GUARD = new Move("m_guard", "变硬", ElementType.NORMAL,
            MoveCategory.STATUS, 0, 100, 40);

    // ------------------------------------------------------------------
    // 固定随机源
    // ------------------------------------------------------------------

    /**
     * 固定随机源：{@code nextInt} 返回 {@code intValue % bound}，{@code nextDouble} 恒返回
     * {@code doubleValue}，{@code nextBoolean} 恒返回 {@code false}。
     * <p>取值经 {@code Math.floorMod} 归一，保证落在 {@code [0, bound)} 内。</p>
     */
    private static final class ScriptedRandom extends Random {

        private final int intValue;
        private final double doubleValue;

        ScriptedRandom(int intValue, double doubleValue) {
            this.intValue = intValue;
            this.doubleValue = doubleValue;
        }

        @Override
        public int nextInt(int bound) {
            return bound <= 0 ? 0 : Math.floorMod(intValue, bound);
        }

        @Override
        public double nextDouble() {
            return doubleValue;
        }

        @Override
        public boolean nextBoolean() {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 构造助手
    // ------------------------------------------------------------------

    /** 单属性种族：{@code spe} 越大越快。 */
    private static Species species(String id, ElementType type, int spe) {
        return new Species(id, id, type, null,
                new Stats(300, 20, 300, 20, 300, spe), 100,
                List.of(), null, 0, Map.of());
    }

    /** 指定异常状态的变化技能（100% 触发，必中，无天气/场地效果）。 */
    private static Move inflictMove(StatusCondition condition) {
        return new Move("m_" + condition.name().toLowerCase(), condition.getDisplayName(),
                ElementType.NORMAL, MoveCategory.STATUS, 0, 100, 20, 0,
                MoveEffect.NONE, condition, 100);
    }

    /** 高速玩家精灵（速度 85 > 敌方 9）：先手、高耐久，不会被反打倒下。 */
    private static Pokemon sturdyPlayer(String id, Move... extraMoves) {
        List<Move> pool = new java.util.ArrayList<>();
        pool.add(TAP);
        pool.addAll(List.of(extraMoves));
        return Pokemon.create(species(id + "_sp", ElementType.NORMAL, 200), 20, pool);
    }

    /** 低速被动敌方精灵：只会无效果变化技能。 */
    private static Pokemon passiveFoe(String id) {
        return passiveFoe(id, ElementType.NORMAL);
    }

    private static Pokemon passiveFoe(String id, ElementType type) {
        return Pokemon.create(species(id + "_sp", type, 10), 20, List.of(GUARD));
    }

    private static Player playerWith(Pokemon... party) {
        Player player = new Player("玩家");
        for (Pokemon p : party) {
            player.addPokemon(p);
        }
        return player;
    }

    /** 以单个精灵组建临时玩家队伍创建对战（默认随机源：nextInt=0、nextDouble=0.9）。 */
    private static BattleService battle(Pokemon playerPokemon, Pokemon foe) {
        return battle(playerPokemon, foe, 0, 0.9);
    }

    private static BattleService battle(Pokemon playerPokemon, Pokemon foe, int intValue, double doubleValue) {
        return battle(playerWith(playerPokemon), foe, intValue, doubleValue);
    }

    private static BattleService battle(Player owner, Pokemon foe) {
        return battle(owner, foe, 0, 0.9);
    }

    private static BattleService battle(Player owner, Pokemon foe, int intValue, double doubleValue) {
        return BattleServices.newBattle(owner, foe, new ScriptedRandom(intValue, doubleValue));
    }

    private static String joinLog(BattleService engine) {
        return String.join("\n", engine.getLog());
    }

    // ------------------------------------------------------------------
    // 施加异常状态
    // ------------------------------------------------------------------

    @Test
    void 变化技能命中后必定施加异常状态() {
        Pokemon player = sturdyPlayer("p", inflictMove(StatusCondition.PARALYSIS));
        Pokemon foe = passiveFoe("f");
        BattleService engine = battle(player, foe);

        engine.useMove(player.getMoveSlots().get(1));

        assertEquals(StatusCondition.PARALYSIS, foe.getStatus());
        assertTrue(joinLog(engine).contains("陷入了麻痹状态"));
    }

    @Test
    void 概率未命中时不施加异常状态() {
        Move sometimes = new Move("m_maybe", "偶尔麻痹", ElementType.NORMAL, MoveCategory.STATUS,
                0, 100, 20, 0, MoveEffect.NONE, StatusCondition.PARALYSIS, 30);
        Pokemon player = sturdyPlayer("p", sometimes);
        Pokemon foe = passiveFoe("f");
        // nextInt(100) 返回 50，不小于 30 → 本次不触发
        BattleService engine = battle(player, foe, 50, 0.9);

        engine.useMove(player.getMoveSlots().get(1));

        assertEquals(StatusCondition.NONE, foe.getStatus());
        assertFalse(joinLog(engine).contains("陷入了麻痹状态"));
    }

    @Test
    void 属性免疫的精灵不会陷入对应异常状态() {
        assertImmune(StatusCondition.PARALYSIS, ElementType.ELECTRIC);
        assertImmune(StatusCondition.POISON, ElementType.POISON);
        assertImmune(StatusCondition.BADLY_POISON, ElementType.POISON);
        assertImmune(StatusCondition.BURN, ElementType.FIRE);
        assertImmune(StatusCondition.FREEZE, ElementType.ICE);
    }

    /** 用 {@code condition} 对应的变化技能攻击 {@code immuneType} 属性的精灵，应因属性免疫而不生效。 */
    private void assertImmune(StatusCondition condition, ElementType immuneType) {
        Pokemon player = sturdyPlayer("p", inflictMove(condition));
        Pokemon foe = passiveFoe("f", immuneType);
        BattleService engine = battle(player, foe);

        engine.useMove(player.getMoveSlots().get(1));

        assertEquals(StatusCondition.NONE, foe.getStatus(),
                immuneType + " 属性应免疫 " + condition.getDisplayName());
        assertTrue(joinLog(engine).contains("因属性免疫"),
                "应给出属性免疫提示：" + joinLog(engine));
    }

    @Test
    void 已有主要异常时不会被另一种主要异常覆盖() {
        Pokemon target = sturdyPlayer("p");
        target.setStatus(StatusCondition.BURN);

        assertFalse(StatusCondition.POISON.canApply(target));
        assertFalse(target.tryApplyStatus(StatusCondition.POISON, 0));
        assertEquals(StatusCondition.BURN, target.getStatus());
    }

    @Test
    void 混乱可与主要异常共存但不可叠加() {
        Pokemon target = sturdyPlayer("p");
        target.setStatus(StatusCondition.POISON);

        assertTrue(StatusCondition.CONFUSION.canApply(target));
        assertTrue(target.tryApplyStatus(StatusCondition.CONFUSION, 3));
        assertEquals(StatusCondition.POISON, target.getStatus(), "混乱不应覆盖主要异常");
        assertTrue(target.isConfused());

        assertFalse(StatusCondition.CONFUSION.canApply(target), "已混乱时不可再叠加");
    }

    // ------------------------------------------------------------------
    // 麻痹
    // ------------------------------------------------------------------

    @Test
    void 麻痹时有概率无法行动且不消耗PP() {
        Pokemon player = sturdyPlayer("p");
        player.setStatus(StatusCondition.PARALYSIS);
        // nextDouble 返回 0.0 < 25% → 本回合无法行动
        BattleService engine = battle(player, passiveFoe("f"), 0, 0.0);
        MoveSlot slot = player.getMoveSlots().get(0);
        int ppBefore = slot.getPp();

        engine.useMove(slot);

        assertTrue(joinLog(engine).contains("因麻痹而无法行动"));
        assertEquals(ppBefore, slot.getPp(), "无法行动时不应消耗 PP");
    }

    @Test
    void 麻痹未触发时正常行动() {
        Pokemon player = sturdyPlayer("p");
        player.setStatus(StatusCondition.PARALYSIS);
        // nextDouble 返回 0.9 ≥ 25% → 正常行动
        BattleService engine = battle(player, passiveFoe("f"), 0, 0.9);

        engine.useMove(player.getMoveSlots().get(0));

        assertTrue(joinLog(engine).contains("使用了【轻拍】"));
        assertFalse(joinLog(engine).contains("因麻痹而无法行动"));
    }

    @Test
    void 麻痹使实际速度减半() {
        Pokemon player = sturdyPlayer("p");
        int speed = player.getStats().getSpeed();

        assertEquals(speed, player.effectiveSpeed(), "未麻痹时实际速度等于面板速度");
        player.setStatus(StatusCondition.PARALYSIS);
        assertEquals(Math.max(1, (int) Math.round(speed * 0.5)), player.effectiveSpeed());
    }

    @Test
    void 麻痹降速后失去先手() {
        // 玩家速度 85 → 麻痹后 43；敌方速度 65 > 43，因此敌方先出手
        Pokemon player = sturdyPlayer("p");
        Pokemon foe = Pokemon.create(species("foe_sp", ElementType.NORMAL, 150), 20, List.of(TAP));
        player.setStatus(StatusCondition.PARALYSIS);
        BattleService engine = battle(player, foe, 0, 0.9);

        engine.useMove(player.getMoveSlots().get(0));

        String log = joinLog(engine);
        int foeIndex = log.indexOf("foe_sp 使用了");
        int playerIndex = log.indexOf("p_sp 使用了");
        assertTrue(foeIndex >= 0 && playerIndex >= 0, "双方都应出手：" + log);
        assertTrue(foeIndex < playerIndex, "麻痹降速后应由速度更快的敌方先出手：" + log);
    }

    // ------------------------------------------------------------------
    // 睡眠 / 冰冻
    // ------------------------------------------------------------------

    @Test
    void 睡眠期间无法行动并在睡满后醒来() {
        Pokemon player = sturdyPlayer("p");
        assertTrue(player.tryApplyStatus(StatusCondition.SLEEP, 2));
        BattleService engine = battle(player, passiveFoe("f"));
        MoveSlot slot = player.getMoveSlots().get(0);

        engine.useMove(slot); // 剩余 2 → 1，仍在睡

        assertEquals(StatusCondition.SLEEP, player.getStatus());
        assertTrue(joinLog(engine).contains("正在呼呼大睡"));

        engine.useMove(slot); // 剩余 1 → 0，醒来并正常行动

        assertEquals(StatusCondition.NONE, player.getStatus(), "睡满后应自动醒来");
        assertTrue(joinLog(engine).contains("醒过来了"));
        assertTrue(joinLog(engine).contains("使用了【轻拍】"), "醒来的当回合即可行动");
    }

    @Test
    void 冰冻未解冻时无法行动() {
        Pokemon player = sturdyPlayer("p");
        player.setStatus(StatusCondition.FREEZE);
        // nextDouble 返回 0.9 ≥ 20% → 本回合未解冻
        BattleService engine = battle(player, passiveFoe("f"), 0, 0.9);

        engine.useMove(player.getMoveSlots().get(0));

        assertEquals(StatusCondition.FREEZE, player.getStatus());
        assertTrue(joinLog(engine).contains("被冻住了，无法行动"));
    }

    @Test
    void 冰冻有概率自行解冻并恢复行动() {
        Pokemon player = sturdyPlayer("p");
        player.setStatus(StatusCondition.FREEZE);
        // nextDouble 返回 0.0 < 20% → 解冻
        BattleService engine = battle(player, passiveFoe("f"), 0, 0.0);

        engine.useMove(player.getMoveSlots().get(0));

        assertEquals(StatusCondition.NONE, player.getStatus());
        assertTrue(joinLog(engine).contains("冰冻解除了"));
        assertTrue(joinLog(engine).contains("使用了【轻拍】"), "解冻的当回合即可行动");
    }

    // ------------------------------------------------------------------
    // 混乱
    // ------------------------------------------------------------------

    @Test
    void 混乱时按概率攻击自己() {
        Pokemon player = sturdyPlayer("p");
        player.applyConfusion(3);
        // nextDouble 返回 0.0 < 33% → 自伤
        BattleService engine = battle(player, passiveFoe("f"), 0, 0.0);
        int hpBefore = player.getCurrentHp();

        engine.useMove(player.getMoveSlots().get(0));

        assertTrue(joinLog(engine).contains("因混乱攻击了自己"));
        assertTrue(player.getCurrentHp() < hpBefore, "自伤应扣除自身 HP");
        assertFalse(joinLog(engine).contains("使用了【轻拍】"), "自伤的回合不应使出技能");
    }

    @Test
    void 混乱未触发时仍能行动() {
        Pokemon player = sturdyPlayer("p");
        player.applyConfusion(3);
        // nextDouble 返回 0.9 ≥ 33% → 正常行动
        BattleService engine = battle(player, passiveFoe("f"), 0, 0.9);

        engine.useMove(player.getMoveSlots().get(0));

        assertTrue(joinLog(engine).contains("虽然混乱，但还是行动了"));
        assertTrue(joinLog(engine).contains("使用了【轻拍】"));
    }

    @Test
    void 换宠清除混乱但保留主要异常() {
        Pokemon first = sturdyPlayer("first");
        Pokemon second = sturdyPlayer("second");
        first.setStatus(StatusCondition.POISON);
        first.applyConfusion(3);
        Player owner = playerWith(first, second);
        BattleService engine = battle(owner, passiveFoe("f"));

        engine.switchActive(1);

        assertSame(second, engine.playerActive());
        assertFalse(first.isConfused(), "换下的精灵应清除混乱");
        assertEquals(StatusCondition.POISON, first.getStatus(), "主要异常换宠后仍然保留");
        assertTrue(joinLog(engine).contains("混乱解除了"));
    }

    @Test
    void 倒下时清除自身混乱() {
        Pokemon player = sturdyPlayer("p");
        player.applyConfusion(3);

        player.takeDamage(player.getMaxHp());

        assertTrue(player.isFainted());
        assertFalse(player.isConfused(), "倒下的精灵不应保留混乱");
    }

    // ------------------------------------------------------------------
    // 持续伤害
    // ------------------------------------------------------------------

    @Test
    void 中毒回合末损失最大HP的八分之一() {
        Pokemon player = sturdyPlayer("p");
        player.setStatus(StatusCondition.POISON);
        BattleService engine = battle(player, passiveFoe("f"));
        int maxHp = player.getMaxHp();

        engine.useMove(player.getMoveSlots().get(0));

        assertEquals(maxHp - expectedResidual(maxHp, 1.0 / 8.0), player.getCurrentHp());
        assertTrue(joinLog(engine).contains("受到中毒的伤害"));
    }

    @Test
    void 灼伤回合末扣血且物理攻击减半() {
        Pokemon player = sturdyPlayer("p");
        int attack = player.getStats().getAttack();
        player.setStatus(StatusCondition.BURN);
        BattleService engine = battle(player, passiveFoe("f"));
        int maxHp = player.getMaxHp();

        engine.useMove(player.getMoveSlots().get(0));

        assertEquals(maxHp - expectedResidual(maxHp, 1.0 / 16.0), player.getCurrentHp());
        assertEquals(Math.max(1, (int) Math.round(attack * 0.5)), player.effectiveAttack());
        assertTrue(joinLog(engine).contains("受到灼伤的伤害"));
    }

    @Test
    void 剧毒回合末伤害逐回合递增() {
        Pokemon player = sturdyPlayer("p");
        player.setStatus(StatusCondition.BADLY_POISON);
        BattleService engine = battle(player, passiveFoe("f"));
        int maxHp = player.getMaxHp();
        MoveSlot slot = player.getMoveSlots().get(0);

        assertEquals(StatusCondition.BADLY_POISON_START, player.getBadlyPoisonCounter());

        engine.useMove(slot);
        int firstLoss = maxHp - player.getCurrentHp();

        assertEquals(expectedResidual(maxHp, 1.0 / 16.0), firstLoss, "剧毒首回合扣 1/16");
        assertEquals(StatusCondition.BADLY_POISON_START + 1, player.getBadlyPoisonCounter());

        engine.useMove(slot);
        int secondLoss = maxHp - firstLoss - player.getCurrentHp();

        assertEquals(expectedResidual(maxHp, 2.0 / 16.0), secondLoss, "剧毒次回合扣 2/16");
        assertEquals(StatusCondition.BADLY_POISON_START + 2, player.getBadlyPoisonCounter());
        assertTrue(joinLog(engine).contains("受到剧毒的伤害"));
    }

    @Test
    void 无效异常状态不产生持续伤害() {
        Pokemon player = sturdyPlayer("p");
        player.setStatus(StatusCondition.PARALYSIS);
        BattleService engine = battle(player, passiveFoe("f"), 0, 0.9);
        int maxHp = player.getMaxHp();

        engine.useMove(player.getMoveSlots().get(0));

        assertEquals(maxHp, player.getCurrentHp(), "麻痹不造成回合末伤害");
    }

    private static int expectedResidual(int maxHp, double ratio) {
        return Math.max(1, (int) (maxHp * ratio));
    }

    // ------------------------------------------------------------------
    // 解除道具
    // ------------------------------------------------------------------

    @Test
    void 解除道具治愈对应异常状态并消耗() {
        Pokemon player = sturdyPlayer("p");
        player.setStatus(StatusCondition.BURN);
        Player owner = playerWith(player);
        Item burnHeal = GameData.instance().item("i_burn_heal");
        owner.getBag().add(burnHeal, 1);
        BattleService engine = battle(owner, passiveFoe("f"));

        engine.useItem(burnHeal);

        assertEquals(StatusCondition.NONE, player.getStatus());
        assertEquals(0, owner.getBag().countOf(burnHeal), "使用成功后应消耗道具");
        assertTrue(joinLog(engine).contains("灼伤治愈了"));
    }

    @Test
    void 解除道具对无异常精灵不消耗() {
        Pokemon player = sturdyPlayer("p");
        Player owner = playerWith(player);
        Item antidote = GameData.instance().item("i_antidote");
        owner.getBag().add(antidote, 2);
        BattleService engine = battle(owner, passiveFoe("f"));

        engine.useItem(antidote);

        assertEquals(2, owner.getBag().countOf(antidote), "无对应异常时不应消耗道具");
        assertTrue(joinLog(engine).contains("没有可解除的异常状态"));
    }

    @Test
    void 解除道具不治愈不对应的异常状态() {
        Pokemon player = sturdyPlayer("p");
        player.setStatus(StatusCondition.PARALYSIS);
        Player owner = playerWith(player);
        Item antidote = GameData.instance().item("i_antidote");
        owner.getBag().add(antidote, 1);
        BattleService engine = battle(owner, passiveFoe("f"));

        engine.useItem(antidote);

        assertEquals(StatusCondition.PARALYSIS, player.getStatus());
        assertEquals(1, owner.getBag().countOf(antidote));
    }

    @Test
    void 万灵药治愈任意主要异常状态() {
        Pokemon player = sturdyPlayer("p");
        player.setStatus(StatusCondition.FREEZE);
        Player owner = playerWith(player);
        Item fullHeal = GameData.instance().item("i_full_heal");
        owner.getBag().add(fullHeal, 1);
        BattleService engine = battle(owner, passiveFoe("f"));

        engine.useItem(fullHeal);

        assertEquals(StatusCondition.NONE, player.getStatus());
        assertTrue(joinLog(engine).contains("冰冻治愈了"));
    }

    @Test
    void 解毒药同样解除剧毒() {
        Pokemon player = sturdyPlayer("p");
        player.setStatus(StatusCondition.BADLY_POISON);
        Player owner = playerWith(player);
        Item antidote = GameData.instance().item("i_antidote");
        owner.getBag().add(antidote, 1);
        BattleService engine = battle(owner, passiveFoe("f"));

        engine.useItem(antidote);

        assertEquals(StatusCondition.NONE, player.getStatus());
        assertEquals(0, player.getBadlyPoisonCounter(), "解毒后剧毒计数应清零");
    }

    @Test
    void 精灵被完全治疗时清除全部异常状态() {
        Pokemon player = sturdyPlayer("p");
        player.setStatus(StatusCondition.BURN);
        player.applyConfusion(3);

        player.fullHeal();

        assertEquals(StatusCondition.NONE, player.getStatus());
        assertFalse(player.isConfused());
    }

    // ------------------------------------------------------------------
    // 规则表与道具契约
    // ------------------------------------------------------------------

    @Test
    void 异常状态规则表符合原版约定() {
        assertEquals(6, StatusCondition.majorStatuses().size());
        assertTrue(StatusCondition.PARALYSIS.isMajor());
        assertTrue(StatusCondition.BADLY_POISON.isMajor());
        assertTrue(StatusCondition.CONFUSION.isVolatile());
        assertFalse(StatusCondition.CONFUSION.isMajor());
        assertFalse(StatusCondition.NONE.isMajor());

        assertTrue(StatusCondition.SLEEP.preventsAction());
        assertTrue(StatusCondition.FREEZE.preventsAction());
        assertFalse(StatusCondition.PARALYSIS.preventsAction(), "麻痹是按概率妨碍行动，不彻底阻止");
        assertFalse(StatusCondition.CONFUSION.preventsAction());

        assertEquals(0.5, StatusCondition.PARALYSIS.speedMultiplier());
        assertEquals(1.0, StatusCondition.BURN.speedMultiplier());
        assertEquals(0.5, StatusCondition.BURN.attackMultiplier());
        assertEquals(1.0, StatusCondition.POISON.attackMultiplier());
    }

    @Test
    void 异常状态持续伤害比例符合原版约定() {
        assertEquals(1.0 / 8.0, StatusCondition.POISON.residualDamageRatio(0));
        assertEquals(1.0 / 16.0, StatusCondition.BURN.residualDamageRatio(0));
        assertEquals(1.0 / 16.0, StatusCondition.BADLY_POISON.residualDamageRatio(0), "剧毒从 1 层起算");
        assertEquals(2.0 / 16.0, StatusCondition.BADLY_POISON.residualDamageRatio(2));
        assertEquals(0.0, StatusCondition.PARALYSIS.residualDamageRatio(0));
        assertEquals(0.0, StatusCondition.SLEEP.residualDamageRatio(0));

        assertEquals("中毒", StatusCondition.POISON.residualMessage(0));
        assertEquals("剧毒", StatusCondition.BADLY_POISON.residualMessage(3));
        assertEquals("", StatusCondition.PARALYSIS.residualMessage(0));
    }

    @Test
    void 异常状态按英文名或中文名解析() {
        assertEquals(StatusCondition.PARALYSIS, StatusCondition.parse("PARALYSIS"));
        assertEquals(StatusCondition.PARALYSIS, StatusCondition.parse("麻痹"));
        assertEquals(StatusCondition.PARALYSIS, StatusCondition.parse("paralysis"));
        assertEquals(StatusCondition.BADLY_POISON, StatusCondition.parse("BADLY_POISON"));
        assertEquals(StatusCondition.BADLY_POISON, StatusCondition.parse(" 剧毒 "));
        assertEquals(StatusCondition.NONE, StatusCondition.parse(""));
        assertEquals(StatusCondition.NONE, StatusCondition.parse("   "));
        assertEquals(StatusCondition.NONE, StatusCondition.parse(null));
        assertEquals(StatusCondition.NONE, StatusCondition.parse("不存在的状态"));
    }

    @Test
    void 异常状态的属性免疫表符合原版约定() {
        assertEquals(ElementType.ELECTRIC, StatusCondition.PARALYSIS.immunityType());
        assertEquals(ElementType.POISON, StatusCondition.POISON.immunityType());
        assertEquals(ElementType.POISON, StatusCondition.BADLY_POISON.immunityType());
        assertEquals(ElementType.FIRE, StatusCondition.BURN.immunityType());
        assertEquals(ElementType.ICE, StatusCondition.FREEZE.immunityType());
        assertNull(StatusCondition.SLEEP.immunityType());
        assertNull(StatusCondition.CONFUSION.immunityType());
    }

    @Test
    void 解除道具契约区分单项与全部() {
        Item burnHeal = Item.cureItem("i_burn_heal", "灼伤药", "BURN");
        assertTrue(burnHeal.canCure(StatusCondition.BURN));
        assertFalse(burnHeal.canCure(StatusCondition.POISON));
        assertFalse(burnHeal.canCure(StatusCondition.NONE));
        assertFalse(burnHeal.canCure(null));
        assertEquals(List.of(StatusCondition.BURN), burnHeal.curedStatuses());

        // 多项解除：解毒药同时覆盖中毒与剧毒
        Item antidote = Item.cureItem("i_antidote", "解毒药", "POISON|BADLY_POISON");
        assertTrue(antidote.canCure(StatusCondition.POISON));
        assertTrue(antidote.canCure(StatusCondition.BADLY_POISON));
        assertFalse(antidote.canCure(StatusCondition.BURN));
        assertEquals(List.of(StatusCondition.POISON, StatusCondition.BADLY_POISON),
                antidote.curedStatuses());

        // 全部解除：万灵药覆盖所有主要异常，但不含混乱
        Item fullHeal = Item.cureAllItem("i_full_heal", "万灵药");
        assertTrue(fullHeal.curesAll());
        for (StatusCondition condition : StatusCondition.majorStatuses()) {
            assertTrue(fullHeal.canCure(condition), "万灵药应能解除 " + condition);
        }
        assertFalse(fullHeal.canCure(StatusCondition.CONFUSION), "万灵药不解除挥发性异常（混乱）");
        assertTrue(fullHeal.curedStatuses().isEmpty(), "万灵药的范围用 CURE_ALL 表示");

        // 回复道具不是解除道具
        Item potion = new Item("i_potion", "伤药", org.example.model.ItemCategory.HEAL, 20);
        assertFalse(potion.canCure(StatusCondition.POISON), "回复道具不是解除道具");
    }

    @Test
    void 注册表中的解除道具覆盖范围符合原版约定() {
        assertTrue(GameData.instance().item("i_antidote").canCure(StatusCondition.BADLY_POISON),
                "解毒药应同时解除剧毒");
        assertTrue(GameData.instance().item("i_paralyze_heal").canCure(StatusCondition.PARALYSIS));
        assertTrue(GameData.instance().item("i_burn_heal").canCure(StatusCondition.BURN));
        assertTrue(GameData.instance().item("i_ice_heal").canCure(StatusCondition.FREEZE));
        assertTrue(GameData.instance().item("i_awakening").canCure(StatusCondition.SLEEP));
        assertTrue(GameData.instance().item("i_full_heal").curesAll());

        // 单解道具不应越界
        assertFalse(GameData.instance().item("i_burn_heal").canCure(StatusCondition.FREEZE));
        assertFalse(GameData.instance().item("i_awakening").canCure(StatusCondition.BURN));
    }
}
