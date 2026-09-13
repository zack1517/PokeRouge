package org.example.view;

import java.util.HashMap;
import java.util.Map;

/**
 * 宝可梦图鉴的<b>临时展示数据</b>：身高 / 体重。
 *
 * <p>⚠️ 临时数据：{@code data/species.csv} 目前没有身高、体重列，
 * {@link org.example.pokemon.domain.Species} 也不含这两个字段，这里先用近似值把图鉴 UI 填完整。
 * 待正式数据表补充「身高 / 体重」列后，删除本文件即可 —— 全项目唯一引用方是
 * {@link PokedexData}（届时改从 {@code Species} 读取）。</p>
 */
final class PokedexTempData {

    /** speciesId → {身高(m), 体重(kg)}。 */
    private static final Map<String, double[]> SIZES = new HashMap<>();

    static {
        put("bulbasaur", 0.7, 6.9);
        put("ivysaur", 1.0, 13.0);
        put("venusaur", 2.0, 100.0);
        put("charmander", 0.6, 8.5);
        put("charmeleon", 1.1, 19.0);
        put("charizard", 1.7, 90.5);
        put("squirtle", 0.5, 9.0);
        put("wartortle", 1.0, 22.5);
        put("blastoise", 1.6, 85.5);
        put("pikachu", 0.4, 6.0);
        put("eevee", 0.3, 6.5);
        put("growlithe", 0.7, 19.0);
        put("arcanine", 1.9, 155.0);
        put("horsea", 0.4, 8.0);
        put("seadra", 1.2, 25.0);
        put("abra", 0.9, 19.5);
        put("kadabra", 1.3, 56.5);
        put("alakazam", 1.5, 48.0);
        put("machop", 0.8, 19.5);
        put("machoke", 1.5, 70.5);
        put("machamp", 1.6, 130.0);
        put("geodude", 0.4, 20.0);
        put("graveler", 1.0, 105.0);
        put("golem", 1.4, 300.0);
        put("gastly", 1.3, 0.1);
        put("haunter", 1.6, 0.1);
        put("gengar", 1.5, 40.5);
        put("dratini", 1.8, 3.3);
        put("dragonair", 4.0, 16.5);
        put("dragonite", 2.2, 210.0);
        put("milk-dragon", 0.5, 8.5);
        put("milk-frog", 0.9, 25.0);
        put("milk-toad", 1.6, 95.0);
        put("milk-god", 1.8, 120.0);
        put("articuno", 1.7, 55.4);
        put("zapdos", 1.6, 52.6);
        put("moltres", 2.0, 60.0);
        put("raikou", 1.9, 178.0);
        put("entei", 2.1, 198.0);
        put("suicune", 2.0, 187.0);
        put("magnemite", 0.3, 6.0);
        put("magneton", 1.0, 60.0);
    }

    private PokedexTempData() {
        // 工具类，禁止实例化
    }

    /** 身高（米）；无数据返回 0（展示层回退为「--」）。 */
    static double height(String speciesId) {
        double[] size = SIZES.get(speciesId);
        return size == null ? 0 : size[0];
    }

    /** 体重（千克）；无数据返回 0（展示层回退为「--」）。 */
    static double weight(String speciesId) {
        double[] size = SIZES.get(speciesId);
        return size == null ? 0 : size[1];
    }

    private static void put(String id, double heightM, double weightKg) {
        SIZES.put(id, new double[]{heightM, weightKg});
    }
}
