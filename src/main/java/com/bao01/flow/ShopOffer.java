package com.bao01.flow;

/**
 * 商店商品（对应《游戏流程接口设计》3.5）。
 *
 * @param itemId      道具 id（{@code org.example.data.GameData#item} 按 id 解析）
 * @param price       售价（金币）
 * @param description 展示描述
 */
public record ShopOffer(String itemId, int price, String description) {

    public ShopOffer {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("道具 id 不能为空");
        }
        if (price < 0) {
            throw new IllegalArgumentException("售价不能为负");
        }
    }
}
