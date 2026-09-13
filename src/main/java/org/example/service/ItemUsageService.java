package org.example.service;

import java.util.List;

import org.example.battle.BattleGrowthPort;
import org.example.model.Bag;
import org.example.model.Item;
import org.example.model.ItemCategory;
import org.example.model.Pokemon;
import org.example.model.StatusCondition;

/**
 * 局外道具使用服务：在主菜单中栏把背包里的道具用在指定精灵身上。
 *
 * <p><b>可在局外使用的道具</b>：回复类（{@link ItemCategory#HEAL}）、解除类
 * （{@link ItemCategory#CURE}）与升级类（{@link ItemCategory#LEVEL_UP}，神奇糖果）。
 * 精灵球只能在对战中投出，因此 {@link Item#usableOutsideBattle()} 为 false 的道具会被拒绝。</p>
 *
 * <p><b>与战斗内使用同口径</b>：伤药对满 HP / 已倒下的精灵不生效、解除药对没有对应异常的精灵不生效，
 * 这两种情况都<b>不消耗道具</b>（见 {@code BattleEngine#useItem}）；神奇糖果对满级精灵同样不消耗。
 * 升级走 {@link BattleGrowthPort#boostLevel}，因此与战斗结算出的升级一样会到级学招与进化。</p>
 *
 * <p>未注入成长端口时（{@link BattleGrowthPort#levelsOnly()}）升级降级为「只涨等级与属性」，
 * 不会学招或进化，便于不吃成长模块的单测。</p>
 */
public final class ItemUsageService {

    /**
     * 一次局外使用的结算结果。
     *
     * @param used    是否真的消耗掉了道具
     * @param message 展示给玩家的文本（多行用 {@code \n} 分隔，如升级 / 学招 / 进化日志）
     */
    public record Result(boolean used, String message) {

        static Result refused(String message) {
            return new Result(false, message);
        }

        static Result consumed(String message) {
            return new Result(true, message);
        }
    }

    private final BattleGrowthPort growth;

    /** 未接入成长模块时的构造：升级只涨等级与属性，不学招、不进化。 */
    public ItemUsageService() {
        this(BattleGrowthPort.levelsOnly());
    }

    public ItemUsageService(BattleGrowthPort growth) {
        this.growth = growth == null ? BattleGrowthPort.levelsOnly() : growth;
    }

    /**
     * 对指定精灵使用一个道具。
     *
     * @param item   要使用的道具（{@code null} 视为未选择）
     * @param bag    道具所在的背包（{@code null} 或数量不足时拒绝）
     * @param target 目标精灵（{@code null} 视为没有目标）
     * @return 结算结果；{@code used} 为 false 时道具未被消耗
     */
    public Result use(Item item, Bag bag, Pokemon target) {
        if (item == null) {
            return Result.refused("没有选择要使用的道具。");
        }
        if (bag == null || bag.countOf(item) <= 0) {
            return Result.refused("背包里没有【" + item.getName() + "】。");
        }
        if (!item.usableOutsideBattle()) {
            return Result.refused("【" + item.getName() + "】只能在对战中投出。");
        }
        if (target == null) {
            return Result.refused("没有可以使用的目标。");
        }
        return switch (item.getCategory()) {
            case HEAL -> heal(item, bag, target);
            case CURE -> cure(item, bag, target);
            case LEVEL_UP -> levelUp(item, bag, target);
            default -> Result.refused("【" + item.getName() + "】暂时无法使用。");
        };
    }

    /** 回复类：濒死或满 HP 时不消耗；否则回复 {@link Item#getEffect()} 点 HP。 */
    private Result heal(Item item, Bag bag, Pokemon target) {
        if (target.isFainted()) {
            return Result.refused(target.getName() + " 已经倒下了，【" + item.getName()
                    + "】无法使用；请到医院治疗。");
        }
        int healed = target.heal((int) item.getEffect());
        if (healed <= 0) {
            return Result.refused(target.getName() + " 的 HP 是满的，【" + item.getName() + "】没有使用。");
        }
        bag.consume(item);
        return Result.consumed("使用了【" + item.getName() + "】，" + target.getName()
                + " 回复了 " + healed + " HP。");
    }

    /** 解除类：没有对应异常时不消耗；否则治愈一项异常状态。 */
    private Result cure(Item item, Bag bag, Pokemon target) {
        StatusCondition current = target.getStatus();
        if (current == StatusCondition.NONE || !item.canCure(current)) {
            return Result.refused(target.getName() + " 没有可解除的异常状态，【" + item.getName()
                    + "】没有使用。");
        }
        target.cureStatus();
        bag.consume(item);
        return Result.consumed("使用了【" + item.getName() + "】，" + target.getName()
                + " 的" + current.getDisplayName() + "治愈了！");
    }

    /** 升级类：满级时不消耗；否则直接提升 {@link Item#getEffect()} 级（含到级学招与进化）。 */
    private Result levelUp(Item item, Bag bag, Pokemon target) {
        if (target.getLevel() >= Pokemon.MAX_LEVEL) {
            return Result.refused(target.getName() + " 已经是 Lv." + Pokemon.MAX_LEVEL
                    + "，【" + item.getName() + "】没有使用。");
        }
        int levels = Math.max(1, (int) item.getEffect());
        bag.consume(item);
        List<String> log = growth.boostLevel(target, levels);
        String detail = log.isEmpty()
                ? target.getName() + " 吃下了【" + item.getName() + "】，但等级没有变化。"
                : String.join("\n", log);
        return Result.consumed(detail);
    }
}
