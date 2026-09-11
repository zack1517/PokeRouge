package org.example.data;

import org.example.model.Item;
import org.example.model.RouteConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 商店商品库存（《需求文档》§4.2 商店：随机节点，金币购买道具、回复品、技能机；
 * 随游戏进展商品种类与数量越多）。
 *
 * <p>商品池按段解锁：段数越靠后，可上架的物品种类越多；每次进入商店从已解锁池中抽取
 * {@link RouteConfig#shopStockSize(int)} 件商品。售价在基础价上按段数做通货膨胀。数量与
 * 解锁段位均为「待配置」的初版取值，集中在 {@link RouteConfig} 与本类顶部调整。</p>
 *
 * <p>商品展示名统一取自 {@link GameData} 的道具注册表，本类不再自存一份中文名。</p>
 */
public final class ShopStock {

    /** 一件商品：物品 id、展示名与本次售价。 */
    public record Entry(String itemId, String itemName, int price) {
    }

    /** 商品池条目：物品 id、注册表缺失时的兜底展示名、基础价、解锁段位。 */
    private record Candidate(String itemId, String fallbackName, int basePrice, int unlockSegment) {
    }

    /**
     * 商品池。解锁段位表示「第几段起可能上架」：
     * 初段只卖最基础的伤药与精灵球，越往后越好卖。
     *
     * <p>球类只收录倍率固定的品种；等级球、月亮球、诱饵球、沉重球等「按对手等级/体重/速度
     * 判定倍率」的球种需要条件倍率模型，暂未上架。纪念球与贵重球在正作中即为基准倍率
     * （与精灵球同效）的收藏球，故与精灵球同价、仅作收集用途。</p>
     */
    private static final Candidate[] CANDIDATES = {
            new Candidate("i_potion", "伤药", 40, 1),
            new Candidate("i_poke_ball", "精灵球", 30, 1),
            new Candidate("i_antidote", "解毒药", 35, 1),
            new Candidate("i_super_potion", "好伤药", 90, 2),
            new Candidate("i_great_ball", "超级球", 80, 2),
            new Candidate("i_paralyze_heal", "麻痹药", 45, 2),
            new Candidate("i_burn_heal", "灼伤药", 45, 2),
            new Candidate("i_ice_heal", "解冻药", 45, 2),
            new Candidate("i_awakening", "醒睡药", 45, 2),
            new Candidate("i_premier_ball", "纪念球", 30, 2),
            new Candidate("i_safari_ball", "狩猎球", 100, 2),
            new Candidate("i_full_heal", "万灵药", 160, 3),
            new Candidate("i_ultra_ball", "高级球", 150, 3),
            new Candidate("i_sport_ball", "竞赛球", 120, 3),
            new Candidate("i_cherish_ball", "贵重球", 200, 4),
            new Candidate("i_master_ball", "大师球", 1200, 5),
    };

    private final int segment;
    private final List<Entry> entries;

    private ShopStock(int segment, List<Entry> entries) {
        this.segment = segment;
        this.entries = entries;
    }

    /** 生成某一段的商店库存（按段解锁商品池 + 抽取固定数量 + 通货膨胀计价）。 */
    public static ShopStock forSegment(int segment) {
        return forSegment(segment, new Random());
    }

    /** 生成某一段的商店库存，使用指定随机源（便于测试固定结果）。 */
    public static ShopStock forSegment(int segment, Random random) {
        int seg = Math.max(1, segment);
        Random source = random == null ? new Random() : random;

        List<Candidate> unlocked = new ArrayList<>();
        for (Candidate candidate : CANDIDATES) {
            if (candidate.unlockSegment() <= seg) {
                unlocked.add(candidate);
            }
        }
        Collections.shuffle(unlocked, source);

        int size = Math.min(RouteConfig.shopStockSize(seg), unlocked.size());
        List<Entry> stock = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Candidate candidate = unlocked.get(i);
            stock.add(new Entry(candidate.itemId(), displayName(candidate),
                    RouteConfig.shopPrice(candidate.basePrice(), seg)));
        }
        return new ShopStock(seg, stock);
    }

    /**
     * 商品展示名以道具注册表（{@link GameData#item(String)}）为准，兜底名只在注册表缺该 id 时使用。
     * 历史上商店自存过一份名字，导致 {@code i_awakening} 在这里叫「醒神药」、在数据表里叫「醒睡药」。
     */
    private static String displayName(Candidate candidate) {
        Item item = GameData.instance().item(candidate.itemId());
        return item == null ? candidate.fallbackName() : item.getName();
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
