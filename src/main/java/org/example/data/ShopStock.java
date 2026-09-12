package org.example.data;

import org.example.model.HeldItem;
import org.example.model.HeldItemEffect;
import org.example.model.Item;
import org.example.model.RouteConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 商店商品库存（《需求文档》§4.2 商店：随机节点，金币购买道具、回复品、技能机；
 * 随游戏进展商品种类与数量越多）。
 *
 * <p>货架分两个分区：<b>消耗品</b>（购买后进背包，可重复购买）与<b>装备</b>（购买后入装备库，
 * 全库唯一）。两区各自按段解锁、各自抽签，格位由 {@link RouteConfig#shopStockSize(int)} 与
 * {@link RouteConfig#shopEquipmentStockSize(int)} 决定 —— 装备比消耗品稀有，只占 1~2 格。</p>
 *
 * <p>消耗品池的解锁段位与基础价写在 {@link #CONSUMABLES}；装备池则<b>全量取自
 * {@link GameData#allEquipment()}</b>（当前 77 件：47 件装备 + 30 种树果），基础价与解锁段位
 * 按 {@link HeldItemEffect} 由 {@link #equipmentBasePrice} / {@link #equipmentUnlockSegment} 推出，
 * 因此新增装备只要登记进数据表就会自动上架，无需回来改本类。</p>
 *
 * <p>展示名一律取自 {@link GameData} 的注册表（消耗品查 {@link GameData#item(String)}、装备查
 * {@link GameData#equipment(String)}），本类不再自存一份中文名。</p>
 */
public final class ShopStock {

    /** 商品类型：消耗品购买后进背包、可重复购买；装备购买后入库、全库唯一。 */
    public enum Kind {
        CONSUMABLE,
        EQUIPMENT
    }

    /** 一件商品：物品 id、展示名、效果说明（消耗品为空串）、本次售价与类型。 */
    public record Entry(String itemId, String itemName, String description, int price, Kind kind) {

        /** 简化构造：无说明的消耗品。 */
        public Entry(String itemId, String itemName, int price) {
            this(itemId, itemName, "", price, Kind.CONSUMABLE);
        }

        /** 是否为装备（购买走装备库而非背包，且全库唯一）。 */
        public boolean isEquipment() {
            return kind == Kind.EQUIPMENT;
        }
    }

    /** 消耗品池条目：物品 id、注册表缺失时的兜底展示名、基础价、解锁段位。 */
    private record Consumable(String itemId, String fallbackName, int basePrice, int unlockSegment) {
    }

    /**
     * 消耗品池。解锁段位表示「第几段起可能上架」：
     * 初段只卖最基础的伤药与精灵球，越往后越好卖。
     *
     * <p>球类只收录倍率固定的品种；等级球、月亮球、诱饵球、沉重球等「按对手等级/体重/速度
     * 判定倍率」的球种需要条件倍率模型，暂未上架。纪念球与贵重球在正作中即为基准倍率
     * （与精灵球同效）的收藏球，故与精灵球同价、仅作收集用途。</p>
     */
    private static final Consumable[] CONSUMABLES = {
            new Consumable("i_potion", "伤药", 40, 1),
            new Consumable("i_poke_ball", "精灵球", 30, 1),
            new Consumable("i_antidote", "解毒药", 35, 1),
            new Consumable("i_super_potion", "好伤药", 90, 2),
            new Consumable("i_great_ball", "超级球", 80, 2),
            new Consumable("i_paralyze_heal", "麻痹药", 45, 2),
            new Consumable("i_burn_heal", "灼伤药", 45, 2),
            new Consumable("i_ice_heal", "解冻药", 45, 2),
            new Consumable("i_awakening", "醒睡药", 45, 2),
            new Consumable("i_premier_ball", "纪念球", 30, 2),
            new Consumable("i_safari_ball", "狩猎球", 100, 2),
            new Consumable("i_full_heal", "万灵药", 160, 3),
            new Consumable("i_ultra_ball", "高级球", 150, 3),
            new Consumable("i_sport_ball", "竞赛球", 120, 3),
            new Consumable("i_cherish_ball", "贵重球", 200, 4),
            new Consumable("i_master_ball", "大师球", 1200, 5),
    };

    /**
     * 装备基础价，按 {@link HeldItemEffect} 同类同价（再按段数做通胀）。
     * 阶梯与消耗品（40~1200）对齐：一次性树果最便宜，讲究系与生命宝珠最贵。
     */
    private static int equipmentBasePrice(HeldItemEffect effect) {
        return switch (effect) {
            case MOVE_LAST -> 60;
            case CURE_STATUS, IGNORE_IMMUNITY -> 80;
            case HEAL_HP, SPEED_MULTIPLIER -> 100;
            case HEAL_PP -> 120;
            case RESIST_TYPE, GROUND_IMMUNE, END_TURN_STATUS -> 150;
            case PHYSICAL_DAMAGE, SPECIAL_DAMAGE, POWDER_IMMUNE, FOCUS_BAND -> 200;
            case PUNCH_BOOST, UTILITY_UMBRELLA -> 250;
            case DAMAGE_TYPE, POISON_HEAL, TERRAIN_SEED, TYPE_REACTION -> 300;
            case FIRST_STRIKE, WEATHER_DURATION -> 350;
            case SUPER_EFFECTIVE, CRIT_BOOST, CLEAR_AMULET, THROAT_SPRAY, BLUNDER_POLICY -> 400;
            case FLINCH_CHANCE -> 450;
            case FOCUS_SASH, CONSECUTIVE_BOOST -> 500;
            case CONTACT_PUNISH -> 550;
            case LIFE_STEAL, EVOLITE, WEAKNESS_POLICY -> 600;
            case END_TURN_HEAL, ASSAULT_VEST -> 700;
            case LIFE_ORB -> 800;
            case CHOICE -> 900;
        };
    }

    /**
     * 装备解锁段位，按 {@link HeldItemEffect} 分类（1 = 第 1 段即可上架）：
     * 通用且便宜的装备前置，越强 / 越特殊的越后置 —— 讲究系与生命宝珠压到末段，
     * 突击背心、剩饭、进化辉石等留到中后段。
     */
    private static int equipmentUnlockSegment(HeldItemEffect effect) {
        return switch (effect) {
            case MOVE_LAST, IGNORE_IMMUNITY, SPEED_MULTIPLIER, GROUND_IMMUNE,
                 CURE_STATUS, HEAL_HP, PHYSICAL_DAMAGE, SPECIAL_DAMAGE, DAMAGE_TYPE,
                 END_TURN_STATUS, POWDER_IMMUNE, FOCUS_BAND, PUNCH_BOOST, UTILITY_UMBRELLA -> 1;
            case HEAL_PP, RESIST_TYPE, FIRST_STRIKE, WEATHER_DURATION, POISON_HEAL,
                 SUPER_EFFECTIVE, CRIT_BOOST, FLINCH_CHANCE, TERRAIN_SEED, TYPE_REACTION,
                 CLEAR_AMULET, THROAT_SPRAY, BLUNDER_POLICY, CONSECUTIVE_BOOST, FOCUS_SASH -> 2;
            case CONTACT_PUNISH, LIFE_STEAL, EVOLITE, WEAKNESS_POLICY -> 3;
            case END_TURN_HEAL, ASSAULT_VEST -> 4;
            case LIFE_ORB, CHOICE -> 5;
        };
    }

    /**
     * 商品目录条目：id、展示名、效果说明（消耗品为空串）、是否为装备、基础价、解锁段位。
     *
     * <p>与 {@link Entry} 的区别：{@code Entry} 是「本次货架上的一件商品」（价格已按段通胀），
     * 本记录是「这件商品在目录里的定义」（基础价 + 从第几段起可能上架），供道具图鉴使用。</p>
     */
    public record CatalogEntry(String id, String name, String description, boolean equipment,
                               int basePrice, int unlockSegment) {
    }

    /**
     * 完整商品目录：全部消耗品 + 全部装备（当前 16 + 77 = 93 件），供道具图鉴展示
     * 「第几段起可买、基础价多少」。
     *
     * <p>解锁段位与基础价直接复用 {@link #forSegment} 的同一份口径（消耗品读 {@link #CONSUMABLES}，
     * 装备经 {@link #equipmentBasePrice} / {@link #equipmentUnlockSegment} 推出），
     * 因此图鉴与货架永远不会说法不一。展示名一律取自 {@link GameData}。</p>
     */
    public static List<CatalogEntry> catalog() {
        List<CatalogEntry> all = new ArrayList<>();
        for (Consumable candidate : CONSUMABLES) {
            Item item = GameData.instance().item(candidate.itemId());
            all.add(new CatalogEntry(candidate.itemId(),
                    item == null ? candidate.fallbackName() : item.getName(), "",
                    false, candidate.basePrice(), candidate.unlockSegment()));
        }
        for (HeldItem equipment : GameData.instance().allEquipment()) {
            all.add(new CatalogEntry(equipment.getId(), equipment.getName(), equipment.getDescription(),
                    true, equipmentBasePrice(equipment.getEffectType()),
                    equipmentUnlockSegment(equipment.getEffectType())));
        }
        return List.copyOf(all);
    }

    private final int segment;
    private final List<Entry> entries;

    private ShopStock(int segment, List<Entry> entries) {
        this.segment = segment;
        this.entries = entries;
    }

    /** 生成某一段的商店库存（按段解锁商品池 + 抽取固定数量 + 通货膨胀计价）。 */
    public static ShopStock forSegment(int segment) {
        return forSegment(segment, new Random(), Set.of());
    }

    /** 生成某一段的商店库存，使用指定随机源（便于测试固定结果）。 */
    public static ShopStock forSegment(int segment, Random random) {
        return forSegment(segment, random, Set.of());
    }

    /**
     * 生成某一段的商店库存：消耗品与装备各自抽签。
     *
     * <p>装备池会剔除玩家已拥有的装备 —— 装备全库唯一，重复上架只会让玩家白跑一趟。</p>
     *
     * @param ownedEquipmentIds 玩家已拥有的装备 id，可为 {@code null}（视作无）
     */
    public static ShopStock forSegment(int segment, Random random, Set<String> ownedEquipmentIds) {
        int seg = Math.max(1, segment);
        Random source = random == null ? new Random() : random;
        Set<String> owned = ownedEquipmentIds == null ? Set.of() : ownedEquipmentIds;

        List<Entry> stock = new ArrayList<>();
        stock.addAll(rollConsumables(seg,
                RouteConfig.shopStockSize(seg) - RouteConfig.shopEquipmentStockSize(seg), source));
        stock.addAll(rollEquipment(seg, RouteConfig.shopEquipmentStockSize(seg), owned, source));
        return new ShopStock(seg, stock);
    }

    /** 消耗品分区：从已解锁的消耗品池抽 {@code slots} 件（至少 1 件，超出池容量则取满池）。 */
    private static List<Entry> rollConsumables(int segment, int slots, Random source) {
        List<Consumable> unlocked = consumablePool(segment);
        Collections.shuffle(unlocked, source);

        int size = Math.min(Math.max(1, slots), unlocked.size());
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Consumable candidate = unlocked.get(i);
            Item item = GameData.instance().item(candidate.itemId());
            String name = item == null ? candidate.fallbackName() : item.getName();
            entries.add(new Entry(candidate.itemId(), name, "",
                    RouteConfig.shopPrice(candidate.basePrice(), segment), Kind.CONSUMABLE));
        }
        return entries;
    }

    /** 装备分区：从已解锁且未被拥有的装备池抽 {@code slots} 件。 */
    private static List<Entry> rollEquipment(int segment, int slots, Set<String> owned, Random source) {
        List<HeldItem> unlocked = unlockedEquipment(segment, owned);
        Collections.shuffle(unlocked, source);

        int size = Math.min(slots, unlocked.size());
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            HeldItem equipment = unlocked.get(i);
            entries.add(new Entry(equipment.getId(), equipment.getName(), equipment.getDescription(),
                    RouteConfig.shopPrice(equipmentBasePrice(equipment.getEffectType()), segment),
                    Kind.EQUIPMENT));
        }
        return entries;
    }

    /** 已解锁的消耗品池（抽签用，可安全打乱）。 */
    private static List<Consumable> consumablePool(int segment) {
        List<Consumable> unlocked = new ArrayList<>();
        for (Consumable candidate : CONSUMABLES) {
            if (candidate.unlockSegment() <= segment) {
                unlocked.add(candidate);
            }
        }
        return unlocked;
    }

    /** 某一段可上架的消耗品 id（供测试断言解锁口径）。 */
    static List<String> unlockedConsumableIds(int segment) {
        List<String> ids = new ArrayList<>();
        for (Consumable candidate : consumablePool(segment)) {
            ids.add(candidate.itemId());
        }
        return ids;
    }

    /**
     * 某一段可上架的装备池（已按数据表顺序排列；剔除未解锁与已拥有者）。
     *
     * @param ownedEquipmentIds 玩家已拥有的装备 id，可为 {@code null}（视作无）
     */
    static List<HeldItem> unlockedEquipment(int segment, Set<String> ownedEquipmentIds) {
        Set<String> owned = ownedEquipmentIds == null ? Set.of() : ownedEquipmentIds;
        List<HeldItem> unlocked = new ArrayList<>();
        for (HeldItem equipment : GameData.instance().allEquipment()) {
            if (!owned.contains(equipment.getId())
                    && equipmentUnlockSegment(equipment.getEffectType()) <= segment) {
                unlocked.add(equipment);
            }
        }
        return unlocked;
    }

    /** 下架某件商品（装备售出后同步移出货架，避免同一件重复上架）。 */
    public ShopStock withoutEntry(String itemId) {
        List<Entry> remaining = new ArrayList<>();
        for (Entry entry : entries) {
            if (!entry.itemId().equals(itemId)) {
                remaining.add(entry);
            }
        }
        return remaining.size() == entries.size() ? this : new ShopStock(segment, remaining);
    }

    /** 该库存所属段号。 */
    public int segment() {
        return segment;
    }

    /** 本次上架商品（不可变）。 */
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
