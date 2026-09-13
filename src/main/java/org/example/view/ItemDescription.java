package org.example.view;

import org.example.model.Item;
import org.example.model.StatusCondition;

import java.util.stream.Collectors;

/**
 * 消耗品效果说明的唯一拼接口径：商店货架、道具图鉴与主菜单背包三处共用。
 *
 * <p>数据表里只有装备带描述列（{@code equipment.csv}），消耗品的说明只能按类别现推 ——
 * 若各处各推一份，很容易出现「商店里没有说明、图鉴里有」或同一种道具说法不一。
 * 因此这里集中实现，任何界面都调它。</p>
 *
 * <p>说明文案里带使用位置提示的道具（神奇糖果）统一指「主菜单的道具区」——
 * 即宝可梦主界面的中栏，可在那里把道具用在指定精灵身上。</p>
 */
public final class ItemDescription {

    private ItemDescription() {
    }

    /** 按类别现推效果说明；数据缺失（{@code null}）返回空串。 */
    public static String describe(Item item) {
        if (item == null) {
            return "";
        }
        return switch (item.getCategory()) {
            case HEAL -> "回复 " + number(item.getEffect()) + " 点 HP";
            case POKE_BALL -> item.isAlwaysCatch()
                    ? "必定捕捉成功"
                    : "捕捉倍率 ×" + number(item.getEffect());
            case CURE -> item.curesAll()
                    ? "解除全部主要异常状态"
                    : "解除" + item.curedStatuses().stream()
                            .map(StatusCondition::getDisplayName)
                            .collect(Collectors.joining("、"));
            case LEVEL_UP -> "提升精灵 " + number(item.getEffect()) + " 级（可在主菜单的道具区喂食）";
        };
    }

    /** 整数倍率不显示小数点（3 → "3"，4.5 仍为 "4.5"）。 */
    private static String number(double value) {
        return value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.valueOf(value);
    }
}
