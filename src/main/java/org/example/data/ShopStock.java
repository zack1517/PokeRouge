package org.example.data;

import org.example.model.HeldItem;
import org.example.model.HeldItemEffect;
import org.example.model.Item;
import org.example.model.RouteConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 商店商品库存（《需求文档》§4.2 商店：随机节点，金币购买道具、回复品、技能机。
 *
 * <p><b>两个分区都全量上架、都不抽签</b>：</p>
 * <ul>
 *   <li><b>装备</b>：玩家尚未拥有的装备<b>全量列出</b>（{@link GameData#allEquipment()} 全 77 件：
 *       47 件对战道具 + 30 种树果），<b>从第 1 段起</b>即可任选购买，不分段解锁；</li>
 *   <li><b>消耗品</b>：列出<b>本段已解锁</b>的那几件（解锁段写在 {@link #CONSUMABLES}），
 *       解锁后<b>永久保留</b>在货架上、只增不减（第 1 段 7 件 → 第 5 段 16 件），
 *       购买后进背包、可重复购买。</li>
 * </ul>
 *
 * <p>两个分区都随段涨价，口径见 {@link RouteConfig#shopPrice}（装备 2026-09-12 裁决不分段解锁、
 * 消耗品 2026-09-13 裁决按段渐进解锁）。</p>
 *
 * <p>消耗品池、基础价与解锁段写在 {@link #CONSUMABLES}（其中解锁段为 {@link #NEVER_UNLOCKED}
 * 的条目只进 {@link #catalog()}、不进商店，如击败火箭队首领必得的大师球）；装备基础价按
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
     * 剧情专属道具的解锁段号哨兵：取该值表示<b>永不进商店</b>（只出现在 {@link #catalog()}
     * 供道具图鉴展示，靠剧情获得）。
     */
    public static final int NEVER_UNLOCKED = Integer.MAX_VALUE;

    /**
     * 消耗品池条目：物品 id、注册表缺失时的兜底展示名、基础价、解锁段号。
     *
     * <p>解锁段号是「从第几段起可以在商店买到」；{@link #NEVER_UNLOCKED} 表示剧情专属道具
     * （如击败火箭队首领必得的大师球），此时 {@code basePrice} 仅作参考价，界面不展示。</p>
     */
    private record Consumable(String itemId, String fallbackName, int basePrice, int unlockSegment) {

        /** 是否在商店售卖：解锁段号有效即在售。 */
        boolean sold() {
            return unlockSegment != NEVER_UNLOCKED;
        }
    }

    /**
     * 消耗品池，基础价为「第 1 段售价」（再按 {@link RouteConfig#shopPrice} 通胀）。
     *
     * <p><b>解锁节奏</b>（需求方 2026-09-13 裁决：逐步增加，加了后就一直都有）——
     * 第 1 段只有最基础的一套（精灵球 + 伤药 + 5 种状态药），之后每段再放开 2~3 件，
     * 因为解锁条件是「段号 ≥ 解锁段」，所以<b>解锁过的消耗品在任何后续段都仍然在货架上</b>，
     * 货架只增不减：7 → 10 → 12 → 14 → 16 件。每段各解锁了什么见各条 {@code unlockSegment}。</p>
     *
     * <p>球类只收录倍率固定的品种；等级球、月亮球、诱饵球、沉重球等「按对手等级/体重/速度
     * 判定倍率」的球种需要条件倍率模型，暂未上架。纪念球与贵重球在正作中即为基准倍率
     * （与精灵球同效）的收藏球，故与精灵球同价、仅作收集用途。</p>
     *
     * <p>大师球解锁段为 {@link #NEVER_UNLOCKED}：它是「火箭队首领战」的固定战利品
     * （{@link RouteConfig#ROCKET_BOSS_REWARD_ITEM_ID}），不进商店；道具图鉴仍会列出它
     * 并标注「不售卖」。</p>
     */
    private static final Consumable[] CONSUMABLES = {
            // 第 1 段：基础捕捉 + 伤药 + 状态药（玩家进第一间商店就能补齐最基础的一套）
            new Consumable("i_poke_ball", "精灵球", 30, 1),
            new Consumable("i_antidote", "解毒药", 35, 1),
            new Consumable("i_potion", "伤药", 40, 1),
            new Consumable("i_burn_heal", "灼伤药", 45, 1),
            new Consumable("i_ice_heal", "解冻药", 45, 1),
            new Consumable("i_awakening", "醒睡药", 45, 1),
            new Consumable("i_paralyze_heal", "麻痹药", 45, 1),
            // 第 2 段：收藏球 + 好伤药 + 神奇糖果
            new Consumable("i_premier_ball", "纪念球", 30, 2),
            new Consumable("i_super_potion", "好伤药", 90, 2),
            // 神奇糖果：首段已由「糖果补给」事件供货，故商店从第 2 段起卖，按树果回复档定价
            new Consumable("i_rare_candy", "神奇糖果", 100, 2),
            // 第 3 段：超级球 + 狩猎球
            new Consumable("i_great_ball", "超级球", 80, 3),
            new Consumable("i_safari_ball", "狩猎球", 100, 3),
            // 第 4 段：竞赛球 + 高级球
            new Consumable("i_sport_ball", "竞赛球", 120, 4),
            new Consumable("i_ultra_ball", "高级球", 150, 4),
            // 第 5 段：万灵药 + 贵重球
            new Consumable("i_full_heal", "万灵药", 160, 5),
            new Consumable("i_cherish_ball", "贵重球", 200, 5),
            // 剧情专属：不售卖
            new Consumable("i_master_ball", "大师球", 1200, NEVER_UNLOCKED),
    };

    /**
     * 对战道具（{@code equipment.csv} 里的 {@code e_*} 条目）的统一基础价。
     *
     * <p>它们都是「可重复生效的携带道具」，强弱差异由效果本身体现，故不再分档定价。</p>
     */
    private static final int BATTLE_ITEM_BASE_PRICE = 200;

    /**
     * 装备基础价（再按段数做通胀），分两组定价：
     *
     * <ul>
     *   <li><b>对战道具</b>（{@code e_*}，可重复生效）—— 统一 {@value #BATTLE_ITEM_BASE_PRICE}；</li>
     *   <li><b>树果</b>（{@code b_*}，一次性触发）—— 按效果强弱分四档 80 / 100 / 120 / 150。</li>
     * </ul>
     *
     * <p>树果的四个 {@link HeldItemEffect}（{@code CURE_STATUS} / {@code HEAL_HP} / {@code HEAL_PP} /
     * {@code RESIST_TYPE}）只被 {@code b_*} 条目使用，因此按效果类型即可区分两组，无需回查 id。
     * 新增效果类型时枚举里会缺分支、编译期报错，强制回来定价。</p>
     */
    private static int equipmentBasePrice(HeldItemEffect effect) {
        return switch (effect) {
            // 树果四档
            case CURE_STATUS -> 80;
            case HEAL_HP -> 100;
            case HEAL_PP -> 120;
            case RESIST_TYPE -> 150;
            // 其余全部为对战道具，统一定价
            case MOVE_LAST, IGNORE_IMMUNITY, SPEED_MULTIPLIER, GROUND_IMMUNE, END_TURN_STATUS,
                 PHYSICAL_DAMAGE, SPECIAL_DAMAGE, POWDER_IMMUNE, FOCUS_BAND, PUNCH_BOOST,
                 UTILITY_UMBRELLA, DAMAGE_TYPE, POISON_HEAL, TERRAIN_SEED, TYPE_REACTION,
                 FIRST_STRIKE, WEATHER_DURATION, SUPER_EFFECTIVE, CRIT_BOOST, CLEAR_AMULET,
                 THROAT_SPRAY, BLUNDER_POLICY, FLINCH_CHANCE, FOCUS_SASH, CONSECUTIVE_BOOST,
                 CONTACT_PUNISH, LIFE_STEAL, EVOLITE, WEAKNESS_POLICY, END_TURN_HEAL,
                 ASSAULT_VEST, LIFE_ORB, CHOICE -> BATTLE_ITEM_BASE_PRICE;
        };
    }

    /**
     * 商品目录条目：id、展示名、效果说明（消耗品为空串）、是否为装备、基础价、解锁段号。
     *
     * <p>与 {@link Entry} 的区别：{@code Entry} 是「本次货架上的一件商品」（价格已按段通胀），
     * 本记录是「这件商品在目录里的定义」，供道具图鉴使用 —— 因此会包含
     * {@link #NEVER_UNLOCKED} 的剧情专属道具（它们不在货架上）。</p>
     *
     * @param unlockSegment 从第几段起可买到；装备恒为第 1 段（不分段解锁），
     *                      {@link #NEVER_UNLOCKED} 表示不售卖
     */
    public record CatalogEntry(String id, String name, String description, boolean equipment,
                               int basePrice, int unlockSegment) {

        /** 是否在商店售卖（{@link #NEVER_UNLOCKED} 之外的条目都在售）。 */
        public boolean sold() {
            return unlockSegment != NEVER_UNLOCKED;
        }
    }

    /**
     * 完整商品目录：全部消耗品 + 全部装备（当前 17 + 77 = 94 件），供道具图鉴展示。
     *
     * <p>基础价与解锁段直接复用 {@link #forSegment} 的同一份口径（消耗品读 {@link #CONSUMABLES}，
     * 装备经 {@link #equipmentBasePrice} 推出），因此图鉴与货架永远不会说法不一。
     * 展示名一律取自 {@link GameData}。含不售卖的道具（大师球），图鉴照常列出并标注「不售卖」。</p>
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
                    true, equipmentBasePrice(equipment.getEffectType()), 1));
        }
        return List.copyOf(all);
    }

    private final int segment;
    private final List<Entry> entries;

    private ShopStock(int segment, List<Entry> entries) {
        this.segment = segment;
        this.entries = entries;
    }

    /** 生成某一段的商店库存（本段已解锁的消耗品 + 未拥有的全量装备，均按段通胀计价）。 */
    public static ShopStock forSegment(int segment) {
        return forSegment(segment, new Random(), Set.of());
    }

    /**
     * 生成某一段的商店库存。
     *
     * <p><b>随机源已不影响结果</b>：货架从 2026-09-12 起不再抽签（装备全量、消耗品按段解锁后全量），
     * 保留 {@code random} 参数只为兼容既有调用方与历史测试的多随机源遍历。</p>
     */
    public static ShopStock forSegment(int segment, Random random) {
        return forSegment(segment, random, Set.of());
    }

    /**
     * 生成某一段的商店库存：本段已解锁的消耗品全量上架，未拥有的装备全量上架。
     *
     * <p>装备池会剔除玩家已拥有的装备 —— 装备全库唯一，已拥有的列在货架上也买不了。
     * 消耗品不剔除：它买进背包、可重复购买。</p>
     *
     * @param ownedEquipmentIds 玩家已拥有的装备 id，可为 {@code null}（视作无）
     */
    public static ShopStock forSegment(int segment, Random random, Set<String> ownedEquipmentIds) {
        int seg = Math.max(1, segment);
        Set<String> owned = ownedEquipmentIds == null ? Set.of() : ownedEquipmentIds;

        List<Entry> stock = new ArrayList<>();
        stock.addAll(unlockedConsumables(seg));
        stock.addAll(allEquipmentEntries(seg, owned));
        return new ShopStock(seg, stock);
    }

    /**
     * 消耗品分区：把该段<b>已解锁</b>的消耗品全量上架（不抽签、不限件数），
     * 排列顺序为「基础价升序 → id」，与装备分区同一口径。
     *
     * <p>解锁段号是「段号 ≥ N」，所以上一段买到的那些消耗品在下一段仍然在货架上 ——
     * 这正是需求方要的「逐步增加，加了后就一直都有」。</p>
     */
    private static List<Entry> unlockedConsumables(int segment) {
        List<Consumable> pool = consumablePool(segment);
        pool.sort(Comparator
                .comparingInt(Consumable::basePrice)
                .thenComparing(Consumable::itemId));

        List<Entry> entries = new ArrayList<>();
        for (Consumable candidate : pool) {
            Item item = GameData.instance().item(candidate.itemId());
            String name = item == null ? candidate.fallbackName() : item.getName();
            entries.add(new Entry(candidate.itemId(), name, "",
                    RouteConfig.shopPrice(candidate.basePrice(), segment), Kind.CONSUMABLE));
        }
        return entries;
    }

    /**
     * 装备分区：把玩家尚未拥有的装备<b>全量上架</b>（不抽签、不限件数），玩家想买哪件就买哪件。
     *
     * <p>排列顺序为「基础价升序 → id」，与消耗品分区同一口径，越往下越贵；售价仍按段通胀。
     * 47 件对战道具基础价统一，因此这一段实际按 id 排列。</p>
     */
    private static List<Entry> allEquipmentEntries(int segment, Set<String> owned) {
        List<HeldItem> sellable = new ArrayList<>(sellableEquipment(owned));
        sellable.sort(Comparator
                .comparingInt((HeldItem item) -> equipmentBasePrice(item.getEffectType()))
                .thenComparing(HeldItem::getId));

        List<Entry> entries = new ArrayList<>();
        for (HeldItem equipment : sellable) {
            entries.add(new Entry(equipment.getId(), equipment.getName(), equipment.getDescription(),
                    RouteConfig.shopPrice(equipmentBasePrice(equipment.getEffectType()), segment),
                    Kind.EQUIPMENT));
        }
        return entries;
    }

    /** 某一段已解锁的消耗品池（不含剧情专属道具，可安全排序）。 */
    private static List<Consumable> consumablePool(int segment) {
        int seg = Math.max(1, segment);
        List<Consumable> unlocked = new ArrayList<>();
        for (Consumable candidate : CONSUMABLES) {
            if (candidate.sold() && candidate.unlockSegment() <= seg) {
                unlocked.add(candidate);
            }
        }
        return unlocked;
    }

    /** 某一段已解锁的可售消耗品 id（供测试断言解锁节奏，不含剧情专属道具）。 */
    static List<String> sellableConsumableIds(int segment) {
        List<String> ids = new ArrayList<>();
        for (Consumable candidate : consumablePool(segment)) {
            ids.add(candidate.itemId());
        }
        return ids;
    }

    /**
     * 可上架的装备池（<b>全部装备自第 1 段起即可购买</b>，仅剔除已拥有者），供商店全量上架与测试断言。
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
