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
 * 全库唯一）。两区各自抽签，格位由 {@link RouteConfig#shopStockSize(int)} 与
 * {@link RouteConfig#shopEquipmentStockSize(int)} 决定 —— 装备比消耗品稀有，只占 1~2 格。</p>
 *
 * <p><b>不做分段解锁</b>：除剧情专属道具外，全部消耗品与全部装备从<b>第 1 段起</b>都可能上架，
 * 玩家在货架上看到什么只由抽签与「已拥有」决定；随段变化的只有格位数量与售价通胀
 * （{@link RouteConfig#shopPrice}）。</p>
 *
 * <p>消耗品池与基础价写在 {@link #CONSUMABLES}（其中 {@code sold = false} 的条目只进
 * {@link #catalog()}、不进商店，如击败火箭队首领必得的大师球）；装备池<b>全量取自
 * {@link GameData#allEquipment()}</b>（当前 77 件：47 件装备 + 30 种树果），基础价按
 * {@link HeldItemEffect} 由 {@link #equipmentBasePrice} 推出，因此新增装备只要登记进数据表
 * 就会自动上架，无需回来改本类。</p>
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

    /**
     * 消耗品池条目：物品 id、注册表缺失时的兜底展示名、基础价、是否在商店售卖。
     *
     * <p>{@code sold == false} 的条目（剧情专属道具）不参与商店抽签，只出现在 {@link #catalog()}
     * 供道具图鉴展示；此时 {@code basePrice} 仅作参考价，界面不展示。</p>
     */
    private record Consumable(String itemId, String fallbackName, int basePrice, boolean sold) {
    }

    /**
     * 消耗品池，基础价为「第 1 段售价」（再按 {@link RouteConfig#shopPrice} 通胀）。
     *
     * <p><b>全部条目从第 1 段起都可能上架</b>，不再按段解锁 —— 玩家越往后走，能买到的
     * 东西变多只体现在格位数量（{@link RouteConfig#shopStockSize}）与售价通胀上。</p>
     *
     * <p>球类只收录倍率固定的品种；等级球、月亮球、诱饵球、沉重球等「按对手等级/体重/速度
     * 判定倍率」的球种需要条件倍率模型，暂未上架。纪念球与贵重球在正作中即为基准倍率
     * （与精灵球同效）的收藏球，故与精灵球同价、仅作收集用途。</p>
     *
     * <p>大师球 {@code sold = false}：它是「火箭队首领战」的固定战利品
     * （{@link RouteConfig#ROCKET_BOSS_REWARD_ITEM_ID}），不进随机货架；道具图鉴仍会列出它
     * 并标注「不售卖」。</p>
     */
    private static final Consumable[] CONSUMABLES = {
            new Consumable("i_potion", "伤药", 40, true),
            new Consumable("i_poke_ball", "精灵球", 30, true),
            new Consumable("i_antidote", "解毒药", 35, true),
            new Consumable("i_super_potion", "好伤药", 90, true),
            new Consumable("i_great_ball", "超级球", 80, true),
            new Consumable("i_paralyze_heal", "麻痹药", 45, true),
            new Consumable("i_burn_heal", "灼伤药", 45, true),
            new Consumable("i_ice_heal", "解冻药", 45, true),
            new Consumable("i_awakening", "醒睡药", 45, true),
            new Consumable("i_premier_ball", "纪念球", 30, true),
            new Consumable("i_safari_ball", "狩猎球", 100, true),
            new Consumable("i_full_heal", "万灵药", 160, true),
            new Consumable("i_ultra_ball", "高级球", 150, true),
            new Consumable("i_sport_ball", "竞赛球", 120, true),
            new Consumable("i_cherish_ball", "贵重球", 200, true),
            new Consumable("i_master_ball", "大师球", 1200, false),
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
     * 商品目录条目：id、展示名、效果说明（消耗品为空串）、是否为装备、基础价、是否在商店售卖。
     *
     * <p>与 {@link Entry} 的区别：{@code Entry} 是「本次货架上的一件商品」（价格已按段通胀），
     * 本记录是「这件商品在目录里的定义」，供道具图鉴使用 —— 因此会包含 {@code sold = false}
     * 的剧情专属道具（它们不在货架上）。</p>
     */
    public record CatalogEntry(String id, String name, String description, boolean equipment,
                               int basePrice, boolean sold) {
    }

    /**
     * 完整商品目录：全部消耗品 + 全部装备（当前 16 + 77 = 93 件），供道具图鉴展示。
     *
     * <p>基础价直接复用 {@link #forSegment} 的同一份口径（消耗品读 {@link #CONSUMABLES}，
     * 装备经 {@link #equipmentBasePrice} 推出），因此图鉴与货架永远不会说法不一。
     * 展示名一律取自 {@link GameData}。含 {@code sold = false} 的剧情专属道具（大师球），
     * 图鉴照常列出并标注「不售卖」。</p>
     */
    public static List<CatalogEntry> catalog() {
        List<CatalogEntry> all = new ArrayList<>();
        for (Consumable candidate : CONSUMABLES) {
            Item item = GameData.instance().item(candidate.itemId());
            all.add(new CatalogEntry(candidate.itemId(),
                    item == null ? candidate.fallbackName() : item.getName(), "",
                    false, candidate.basePrice(), candidate.sold()));
        }
        for (HeldItem equipment : GameData.instance().allEquipment()) {
            all.add(new CatalogEntry(equipment.getId(), equipment.getName(), equipment.getDescription(),
                    true, equipmentBasePrice(equipment.getEffectType()), true));
        }
        return List.copyOf(all);
    }

    private final int segment;
    private final List<Entry> entries;

    private ShopStock(int segment, List<Entry> entries) {
        this.segment = segment;
        this.entries = entries;
    }

    /** 生成某一段的商店库存（抽取固定数量 + 通货膨胀计价）。 */
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

    /** 消耗品分区：从消耗品池抽 {@code slots} 件（至少 1 件，超出池容量则取满池）。 */
    private static List<Entry> rollConsumables(int segment, int slots, Random source) {
        List<Consumable> pool = consumablePool();
        Collections.shuffle(pool, source);

        int size = Math.min(Math.max(1, slots), pool.size());
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Consumable candidate = pool.get(i);
            Item item = GameData.instance().item(candidate.itemId());
            String name = item == null ? candidate.fallbackName() : item.getName();
            entries.add(new Entry(candidate.itemId(), name, "",
                    RouteConfig.shopPrice(candidate.basePrice(), segment), Kind.CONSUMABLE));
        }
        return entries;
    }

    /** 装备分区：从未被拥有的装备池抽 {@code slots} 件。 */
    private static List<Entry> rollEquipment(int segment, int slots, Set<String> owned, Random source) {
        List<HeldItem> pool = sellableEquipment(owned);
        Collections.shuffle(pool, source);

        int size = Math.min(slots, pool.size());
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            HeldItem equipment = pool.get(i);
            entries.add(new Entry(equipment.getId(), equipment.getName(), equipment.getDescription(),
                    RouteConfig.shopPrice(equipmentBasePrice(equipment.getEffectType()), segment),
                    Kind.EQUIPMENT));
        }
        return entries;
    }

    /** 可上架的消耗品池（抽签用，可安全打乱）：排除 {@code sold = false} 的剧情专属道具。 */
    private static List<Consumable> consumablePool() {
        List<Consumable> sellable = new ArrayList<>();
        for (Consumable candidate : CONSUMABLES) {
            if (candidate.sold()) {
                sellable.add(candidate);
            }
        }
        return sellable;
    }

    /** 可上架的消耗品 id（供测试断言商品池，不含剧情专属道具）。 */
    static List<String> sellableConsumableIds() {
        List<String> ids = new ArrayList<>();
        for (Consumable candidate : consumablePool()) {
            ids.add(candidate.itemId());
        }
        return ids;
    }

    /**
     * 可上架的装备池（已按数据表顺序排列；<b>全部装备自第 1 段起即可上架</b>，仅剔除已拥有者）。
     *
     * @param ownedEquipmentIds 玩家已拥有的装备 id，可为 {@code null}（视作无）
     */
    static List<HeldItem> sellableEquipment(Set<String> ownedEquipmentIds) {
        Set<String> owned = ownedEquipmentIds == null ? Set.of() : ownedEquipmentIds;
        List<HeldItem> sellable = new ArrayList<>();
        for (HeldItem equipment : GameData.instance().allEquipment()) {
            if (!owned.contains(equipment.getId())) {
                sellable.add(equipment);
            }
        }
        return sellable;
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
