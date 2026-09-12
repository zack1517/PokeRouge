package org.example.view;

import org.example.data.GameData;
import org.example.data.ShopStock;
import org.example.model.HeldItem;
import org.example.model.Item;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.StatusCondition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 道具图鉴的数据层：把「商店商品目录」（{@link ShopStock#catalog()}，16 件消耗品 + 77 件装备）
 * 与玩家的持有情况合并成图鉴条目列表，供 {@link ItemDexView} 纯展示。
 *
 * <p>图鉴<b>不做收集解锁</b>：93 件全部列出，只标注「已拥有 / 未拥有」（当前口径与
 * {@code PokedexView} 一致）。装备额外标注穿戴者，便于在详情页外直接查「这件装备在谁身上」。</p>
 *
 * <p>消耗品在数据表里没有描述列，本类按类别现推一句效果说明（回复量 / 捕捉倍率 / 解除范围）；
 * 装备描述直接取 {@link HeldItem#getDescription()}。</p>
 */
public final class ItemDexData {

    /**
     * 图鉴单条：商品定义 + 玩家持有情况。
     *
     * @param id            道具 / 装备 id
     * @param name          展示名（取自数据注册表）
     * @param description   效果说明（消耗品由本类按类别生成，装备取自数据表）
     * @param equipment     是否为可携带装备（false = 消耗品）
     * @param basePrice     第 1 段的基础售价（段数越靠后越贵，见 {@code RouteConfig#shopPrice}）
     * @param unlockSegment 从第几段起可能在商店上架（1~5）
     * @param ownedCount    持有数量：消耗品为背包件数，装备为 0 / 1（全库唯一）
     * @param holderName    当前穿戴该装备的精灵名；未穿戴或非装备时为 {@code null}
     */
    public record Entry(String id, String name, String description, boolean equipment,
                        int basePrice, int unlockSegment, int ownedCount, String holderName) {

        /** 是否已拥有（消耗品看背包件数，装备看是否入库）。 */
        public boolean owned() {
            return ownedCount > 0;
        }

        /** 是否正被某只精灵穿戴。 */
        public boolean equipped() {
            return holderName != null && !holderName.isBlank();
        }
    }

    private ItemDexData() {
    }

    /**
     * 构建完整道具图鉴：消耗品在前、装备在后，各自按「解锁段位 → 基础价 → 名称」排序，
     * 读起来就是从便宜常见到稀有强力的一条推进线。
     *
     * @param player 玩家（{@code null} 视作什么都没有，条目仍全部列出）
     */
    public static List<Entry> build(Player player) {
        Set<String> ownedEquipment = new HashSet<>();
        Map<String, String> holders = new HashMap<>();
        if (player != null) {
            for (HeldItem item : player.getEquipment()) {
                ownedEquipment.add(item.getId());
            }
            for (Pokemon member : player.getParty()) {
                HeldItem worn = member.getHeldItem();
                if (worn != null) {
                    holders.putIfAbsent(worn.getId(), member.getName());
                }
            }
        }

        List<Entry> entries = new ArrayList<>();
        for (ShopStock.CatalogEntry candidate : ShopStock.catalog()) {
            if (candidate.equipment()) {
                entries.add(new Entry(candidate.id(), candidate.name(), candidate.description(), true,
                        candidate.basePrice(), candidate.unlockSegment(),
                        ownedEquipment.contains(candidate.id()) ? 1 : 0,
                        holders.get(candidate.id())));
            } else {
                Item item = GameData.instance().item(candidate.id());
                int count = player == null || item == null ? 0 : player.getBag().countOf(item);
                entries.add(new Entry(candidate.id(), candidate.name(), describe(item), false,
                        candidate.basePrice(), candidate.unlockSegment(), count, null));
            }
        }
        entries.sort(Comparator.comparing(Entry::equipment)
                .thenComparingInt(Entry::unlockSegment)
                .thenComparingInt(Entry::basePrice)
                .thenComparing(Entry::name));
        return List.copyOf(entries);
    }

    /** 按类别现推消耗品效果说明；数据缺失（{@code null}）返回空串。 */
    static String describe(Item item) {
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
        };
    }

    /** 整数倍率不显示小数点（3 → "3"，4.5 仍为 "4.5"）。 */
    private static String number(double value) {
        return value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.valueOf(value);
    }
}
