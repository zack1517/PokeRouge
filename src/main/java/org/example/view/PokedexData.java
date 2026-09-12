package org.example.view;

import org.example.growth.GrowthProgress;
import org.example.pokemon.domain.ElementType;
import org.example.pokemon.domain.LearnableMove;
import org.example.pokemon.domain.Move;
import org.example.pokemon.domain.Species;
import org.example.pokemon.infrastructure.GameData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 图鉴数据适配层：把宝可梦库（种族 / 技能 CSV）与局外成长进度（{@link GrowthProgress}）
 * 组装成图鉴条目，供 {@link PokedexView} 纯展示。
 *
 * <p><b>展示口径</b>：当前不按「遇到 / 捕获」解锁 —— {@link #UNLOCK_BY_ENCOUNTER} 为 {@code false}，
 * 全部宝可梦直接展示完整信息。原判定所需参数（成长进度、队伍持有种族）与 {@link PokedexView}
 * 的剪影 / 「？？？」分支均予保留：把开关改回 {@code true} 即恢复解锁机制。</p>
 *
 * <p><b>编号口径</b>：按 {@link #DEX_ORDER} 的固化顺序生成 #001~#030 —— 宝可梦库以 HashMap
 * 存储种族（不保序遍历），而图鉴编号必须稳定，故顺序在本地固化（与 data/species.csv 行序一致）；
 * 库中未来新增、未列入顺序表的物种会按 id 排序追加到末尾，保证不漏条目。</p>
 */
public final class PokedexData {

    /**
     * 图鉴解锁机制开关（2026-09-11 口径变更）。
     *
     * <p>{@code false}（当前）：全部宝可梦直接展示，不按「遇到 / 捕获」隐藏信息；
     * {@code true}：恢复原判定 —— 捕捉过 / 对战过 / 队伍持有才解锁，否则显示剪影与「？？？」。</p>
     */
    private static final boolean UNLOCK_BY_ENCOUNTER = false;

    /** 全国图鉴顺序（与 data/species.csv 行序一致；CSV 加载顺序未被库保留，故在此固化）。 */
    private static final List<String> DEX_ORDER = List.of(
            "bulbasaur", "ivysaur", "venusaur",
            "charmander", "charmeleon", "charizard",
            "squirtle", "wartortle", "blastoise",
            "pikachu", "eevee",
            "growlithe", "arcanine",
            "horsea", "seadra",
            "abra", "kadabra", "alakazam",
            "machop", "machoke", "machamp",
            "geodude", "graveler", "golem",
            "gastly", "haunter", "gengar",
            "dratini", "dragonair", "dragonite");

    private PokedexData() {
        // 数据适配层，禁止实例化
    }

    /**
     * 构建图鉴全部条目（编号稳定，含解锁状态与成长数据）。
     *
     * @param progress         局外成长进度（捕捉 / 对战 / 个体值加成）
     * @param ownedSpeciesIds  当前队伍持有的种族 id 集合（可为 {@code null}；当前口径不参与展示过滤，仅预留）
     * @return 图鉴条目列表（不可变）
     */
    public static List<Entry> build(GrowthProgress progress, Set<String> ownedSpeciesIds) {
        GameData library = GameData.instance();
        Set<String> owned = ownedSpeciesIds == null ? Set.of() : ownedSpeciesIds;
        List<Entry> entries = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        for (String id : DEX_ORDER) {
            Species species = library.getSpecies(id).orElse(null);
            if (species == null || !placed.add(id)) {
                continue; // 库中不存在（数据缺失）或顺序表重复：跳过
            }
            entries.add(toEntry(species, entries.size() + 1, progress, owned));
        }
        // 兜底：库中存在但未列入 DEX_ORDER 的物种（未来扩表），按 id 排序追加，保证不漏
        library.getAllSpecies().stream()
                .filter(species -> !placed.contains(species.getId()))
                .sorted(Comparator.comparing(Species::getId))
                .forEach(species -> entries.add(toEntry(species, entries.size() + 1, progress, owned)));
        return List.copyOf(entries);
    }

    /** 单条图鉴条目：种族数据 + 编号 + 解锁状态 + 成长数据 + 临时展示数据（身高体重）。 */
    public record Entry(Species species, int number, boolean unlocked, int captureCount,
                        int battleCount, int ivBonus, double heightM, double weightKg) {

        /** 三位编号文本，如 #001。 */
        public String numberText() {
            return String.format("#%03d", number);
        }

        /** 英文名（由种族 id 派生，如 bulbasaur → Bulbasaur）。 */
        public String englishName() {
            return englishNameOf(species.getId());
        }

        /** 身高体重文本；缺失数据回退为「--」。 */
        public String sizeText() {
            String height = heightM > 0 ? trimNumber(heightM) + " m" : "--";
            String weight = weightKg > 0 ? trimNumber(weightKg) + " kg" : "--";
            return "身高　" + height + "　　体重　" + weight;
        }
    }

    /** 英文名派生：id 按分隔符逐段首字母大写，如 bulbasaur → Bulbasaur。 */
    public static String englishNameOf(String speciesId) {
        if (speciesId == null || speciesId.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : speciesId.split("[^A-Za-z0-9]+")) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    /** 该物种所在的整条进化链（从最初形态到最终形态；数据缺失时返回其自身或空列表）。 */
    public static List<Species> evolutionChain(String speciesId) {
        GameData library = GameData.instance();
        Map<String, String> parents = new HashMap<>();
        for (Species species : library.getAllSpecies()) {
            if (species.getEvolutionTarget() != null) {
                parents.put(species.getEvolutionTarget(), species.getId());
            }
        }
        String rootId = speciesId;
        while (parents.containsKey(rootId)) {
            rootId = parents.get(rootId); // 沿前身一路回溯到最初形态
        }
        List<Species> chain = new ArrayList<>();
        String cursor = rootId;
        while (cursor != null && chain.size() < 8) { // 8 为防御上限（数据异常成环时不致死循环）
            Species species = library.getSpecies(cursor).orElse(null);
            if (species == null) {
                break;
            }
            chain.add(species);
            cursor = species.getEvolutionTarget();
        }
        return List.copyOf(chain);
    }

    /** 学招表行：习得等级 + 技能。 */
    public record LearnRow(int level, Move move) {
    }

    /** 按等级升序返回学招表（技能数据缺失的记录被跳过）。 */
    public static List<LearnRow> learnRows(String speciesId) {
        GameData library = GameData.instance();
        List<LearnRow> rows = new ArrayList<>();
        for (LearnableMove learnable : library.getLearnableMoves(speciesId)) {
            library.getMove(learnable.getMoveId())
                    .map(move -> new LearnRow(learnable.getLevel(), move))
                    .ifPresent(rows::add);
        }
        rows.sort(Comparator.comparingInt(LearnRow::level));
        return List.copyOf(rows);
    }

    /** 属性配色（手绘柔和风：浅色底 + 同系描边 + 深色文字）。 */
    public record TypeStyle(String background, String border, String text) {
    }

    /** 返回属性的图鉴配色；未知属性回退为「一般」配色。 */
    public static TypeStyle typeStyle(ElementType type) {
        if (type == null) {
            return new TypeStyle("#EDE8DA", "#9C937C", "#6B6350");
        }
        return switch (type) {
            case NORMAL -> new TypeStyle("#EDE8DA", "#9C937C", "#6B6350");
            case FIRE -> new TypeStyle("#FBE3D4", "#C96A4A", "#A03E22");
            case WATER -> new TypeStyle("#DCEAF7", "#5B8FC9", "#2F5E96");
            case GRASS -> new TypeStyle("#E3F0D9", "#7FA86B", "#4E6B3D");
            case ELECTRIC -> new TypeStyle("#FBF3CE", "#D9B23C", "#8F6D14");
            case ICE -> new TypeStyle("#DFF2F4", "#62B8C4", "#2E7885");
            case FIGHTING -> new TypeStyle("#F6DFDA", "#C05A4E", "#8E352B");
            case POISON -> new TypeStyle("#F0DDF0", "#A659A6", "#77377A");
            case GROUND -> new TypeStyle("#F2E8D3", "#C09A55", "#86662A");
            case FLYING -> new TypeStyle("#E4EBF8", "#7D95CF", "#4A62A0");
            case PSYCHIC -> new TypeStyle("#FADFE9", "#D46A96", "#9C3D66");
            case BUG -> new TypeStyle("#EAF0D2", "#96A93F", "#66751F");
            case ROCK -> new TypeStyle("#EEE7D3", "#A89060", "#77602F");
            case GHOST -> new TypeStyle("#E7E0F1", "#7868A8", "#4E3F7E");
            case DARK -> new TypeStyle("#E5DDD6", "#7A674F", "#4F4030");
            case DRAGON -> new TypeStyle("#E1E2F8", "#6A6EC9", "#3D4096");
            case STEEL -> new TypeStyle("#E6ECEE", "#8FA0AC", "#5A6B77");
            case FAIRY -> new TypeStyle("#FADFF0", "#D882B8", "#A34D84");
        };
    }

    /** 组装单条图鉴条目（解锁口径见 {@link #UNLOCK_BY_ENCOUNTER}）。 */
    private static Entry toEntry(Species species, int number, GrowthProgress progress, Set<String> owned) {
        int capture = progress.captureCount(species.getId());
        int battle = progress.battleCount(species.getId());
        boolean unlocked = !UNLOCK_BY_ENCOUNTER || capture > 0 || battle > 0 || owned.contains(species.getId());
        return new Entry(species, number, unlocked, capture, battle, progress.ivBonus(species.getId()),
                PokedexTempData.height(species.getId()), PokedexTempData.weight(species.getId()));
    }

    /** 去掉整数小数的尾随零（2.0 → 2），保留真实一位小数（6.9 → 6.9）。 */
    private static String trimNumber(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
