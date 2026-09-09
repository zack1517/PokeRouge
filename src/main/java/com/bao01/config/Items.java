package com.bao01.config;

import com.bao01.model.Item;

import java.util.Map;
import java.util.Set;

/**
 * 对战道具常量（恢复类）。数值平衡统一在此调整。
 */
public final class Items {

    private Items() {
    }

    /** 伤药：固定回复 50 HP。 */
    public static final Item POTION = Item.heal("伤药", 50);
    /** 好伤药：固定回复 120 HP。 */
    public static final Item SUPER_POTION = Item.heal("好伤药", 120);
    /** 全满药：直接回满。 */
    public static final Item FULL_RESTORE = Item.fullHeal("全满药");

    /** 道具注册表：名称 → 唯一实例（存档恢复用，保持 Bag 的实例键语义一致）。 */
    private static final Map<String, Item> BY_NAME = Map.of(
            POTION.getName(), POTION,
            SUPER_POTION.getName(), SUPER_POTION,
            FULL_RESTORE.getName(), FULL_RESTORE
    );

    /** 按名称取唯一实例；未知名称返回 null。 */
    public static Item byName(String name) {
        return BY_NAME.get(name);
    }

    /** 全部已注册道具名称。 */
    public static Set<String> itemNames() {
        return BY_NAME.keySet();
    }
}
