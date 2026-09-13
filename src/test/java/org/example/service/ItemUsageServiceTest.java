package org.example.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.example.data.GameData;
import org.example.model.Bag;
import org.example.model.ElementType;
import org.example.model.Item;
import org.example.model.ItemCategory;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stats;
import org.example.model.StatusCondition;

import org.junit.jupiter.api.Test;

/**
 * {@link ItemUsageService} 的单元测试：局外用伤药 / 状态药 / 神奇糖果作用于指定精灵的口径 ——
 * 该消耗的消耗、不该消耗的一件都不动，精灵球在局外被拒绝。
 *
 * <p>升级走成长端口；未注入端口时降级为「只涨等级与属性」，因此这里的等级断言不依赖成长模块。</p>
 */
class ItemUsageServiceTest {

    private static final Stats FIXED_IVS = new Stats(31, 31, 31, 31, 31, 31);

    private static final Move TACKLE = new Move("m_tackle", "撞击", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 40, 100, 35);

    @Test
    void 伤药回复hp并消耗一件() {
        Pokemon target = pokemon();
        target.heal(9999);
        target.takeDamage(30);
        Item potion = GameData.instance().item("i_potion");
        Bag bag = bagOf(potion, 2);

        ItemUsageService.Result result = new ItemUsageService().use(potion, bag, target);

        assertTrue(result.used(), result.message());
        assertEquals(1, bag.countOf(potion), "应消耗一件伤药");
        assertEquals(Math.min(target.getMaxHp(), target.getMaxHp() - 30 + (int) potion.getEffect()),
                target.getCurrentHp(), "伤药应回复 effect 点 HP（最多回满）");
    }

    @Test
    void 满hp时伤药不消耗() {
        Pokemon target = pokemon();
        target.heal(9999);
        Item potion = GameData.instance().item("i_potion");
        Bag bag = bagOf(potion, 2);

        ItemUsageService.Result result = new ItemUsageService().use(potion, bag, target);

        assertFalse(result.used(), result.message());
        assertEquals(2, bag.countOf(potion), "满 HP 时不应消耗道具");
    }

    @Test
    void 倒下时伤药不消耗() {
        Pokemon target = pokemon();
        target.takeDamage(99999);
        assertTrue(target.isFainted());
        Item potion = GameData.instance().item("i_potion");
        Bag bag = bagOf(potion, 1);

        ItemUsageService.Result result = new ItemUsageService().use(potion, bag, target);

        assertFalse(result.used(), result.message());
        assertEquals(1, bag.countOf(potion));
    }

    @Test
    void 状态药治愈异常并消耗一件() {
        Pokemon target = pokemon();
        target.setStatus(StatusCondition.POISON);
        Item antidote = GameData.instance().item("i_antidote");
        Bag bag = bagOf(antidote, 1);

        ItemUsageService.Result result = new ItemUsageService().use(antidote, bag, target);

        assertTrue(result.used(), result.message());
        assertEquals(StatusCondition.NONE, target.getStatus());
        assertEquals(0, bag.countOf(antidote));
    }

    @Test
    void 没有对应异常时状态药不消耗() {
        Pokemon target = pokemon();
        target.setStatus(StatusCondition.BURN);
        Item antidote = GameData.instance().item("i_antidote");
        Bag bag = bagOf(antidote, 1);

        ItemUsageService.Result result = new ItemUsageService().use(antidote, bag, target);

        assertFalse(result.used(), result.message());
        assertEquals(StatusCondition.BURN, target.getStatus(), "不应误治其它异常");
        assertEquals(1, bag.countOf(antidote));
    }

    @Test
    void 神奇糖果提升一级并消耗一件() {
        Pokemon target = pokemon();
        int before = target.getLevel();
        Item candy = GameData.instance().item("i_rare_candy");
        Bag bag = bagOf(candy, 8);

        ItemUsageService.Result result = new ItemUsageService().use(candy, bag, target);

        assertTrue(result.used(), result.message());
        assertEquals(before + 1, target.getLevel(), "吃一个糖果升一级");
        assertEquals(7, bag.countOf(candy));
    }

    @Test
    void 满级时神奇糖果不消耗() {
        Pokemon target = pokemon();
        while (target.getLevel() < Pokemon.MAX_LEVEL) {
            target.addExp(target.expToNextLevel());
        }
        assertEquals(Pokemon.MAX_LEVEL, target.getLevel());
        Item candy = GameData.instance().item("i_rare_candy");
        Bag bag = bagOf(candy, 3);

        ItemUsageService.Result result = new ItemUsageService().use(candy, bag, target);

        assertFalse(result.used(), result.message());
        assertEquals(3, bag.countOf(candy), "满级时不应消耗糖果");
    }

    @Test
    void 精灵球不能在局外使用() {
        Pokemon target = pokemon();
        Item ball = GameData.instance().item("i_poke_ball");
        Bag bag = bagOf(ball, 5);

        ItemUsageService.Result result = new ItemUsageService().use(ball, bag, target);

        assertFalse(result.used(), result.message());
        assertEquals(5, bag.countOf(ball));
    }

    @Test
    void 数据缺失或数量为零时拒绝() {
        Pokemon target = pokemon();
        Item potion = GameData.instance().item("i_potion");

        assertFalse(new ItemUsageService().use(null, bagOf(potion, 1), target).used(), "未选道具");
        assertFalse(new ItemUsageService().use(potion, new Bag(), target).used(), "背包为空");
        assertFalse(new ItemUsageService().use(potion, null, target).used(), "背包缺失");
        assertFalse(new ItemUsageService().use(potion, bagOf(potion, 1), null).used(), "没有目标");
    }

    @Test
    void 升级道具可带自定义提升级数() {
        Pokemon target = pokemon();
        int before = target.getLevel();
        Item doubleCandy = Item.levelUpItem("i_double_candy", "双倍糖果", 2);
        Bag bag = bagOf(doubleCandy, 2);

        ItemUsageService.Result result = new ItemUsageService().use(doubleCandy, bag, target);

        assertTrue(result.used(), result.message());
        assertEquals(before + 2, target.getLevel(), "effect 为提升级数");
        assertEquals(1, bag.countOf(doubleCandy));
    }

    @Test
    void 精灵球之外的道具都可在局外使用() {
        assertTrue(GameData.instance().item("i_potion").usableOutsideBattle());
        assertTrue(GameData.instance().item("i_antidote").usableOutsideBattle());
        assertTrue(GameData.instance().item("i_rare_candy").usableOutsideBattle());
        assertFalse(GameData.instance().item("i_poke_ball").usableOutsideBattle());
    }

    @Test
    void 神奇糖果登记为升级道具() {
        Item candy = GameData.instance().item("i_rare_candy");

        assertEquals("神奇糖果", candy.getName());
        assertEquals(ItemCategory.LEVEL_UP, candy.getCategory());
        assertEquals(1, (int) candy.getEffect(), "一个糖果升一级");
        assertEquals(1, Item.levelUpItem("i_x", "测试", 0).getEffect(), "级数不足 1 时按 1 处理");
    }

    private static Bag bagOf(Item item, int count) {
        Bag bag = new Bag();
        bag.add(item, count);
        return bag;
    }

    private static Pokemon pokemon() {
        Species species = new Species("sp_test", "测试精灵", ElementType.NORMAL, null,
                new Stats(100, 100, 100, 100, 100, 100), 100, List.of(), null, 0, Map.of());
        return Pokemon.create(species, 10, List.of(TACKLE), FIXED_IVS);
    }
}
