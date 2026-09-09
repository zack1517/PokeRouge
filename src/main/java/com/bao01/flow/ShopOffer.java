package com.bao01.flow;

/**
 * 商店商品（对应《游戏流程接口设计》3.5）。
 *
 * @param itemName    道具名（用 {@code Items.byName} 解析为唯一实例）
 * @param price       售价（金币）
 * @param description 展示描述
 */
public record ShopOffer(String itemName, int price, String description) {

    public ShopOffer {
        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("商品名不能为空");
        }
        if (price < 0) {
            throw new IllegalArgumentException("售价不能为负");
        }
    }
}
