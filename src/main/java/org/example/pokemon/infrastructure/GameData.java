package org.example.pokemon.infrastructure;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.example.pokemon.domain.BaseStats;
import org.example.pokemon.domain.ElementType;
import org.example.pokemon.domain.LearnableMove;
import org.example.pokemon.domain.Move;
import org.example.pokemon.domain.MoveCategory;
import org.example.pokemon.domain.Nature;
import org.example.pokemon.domain.Pokemon;
import org.example.pokemon.domain.Species;
import org.example.pokemon.domain.StatModifier;
import org.example.pokemon.domain.Stats;
import org.example.util.LogUtil;

/**
 * 游戏数据中心（单例），从 classpath 的 CSV 文件加载并集中管理种族、技能、性格等静态数据。
 */
public class GameData {

    private static final String SPECIES_CSV = "data/species.csv";
    private static final String MOVES_CSV = "data/moves.csv";
    private static final String LEARNABLE_MOVES_CSV = "data/learnable_moves.csv";
    private static final String NATURES_CSV = "data/natures.csv";

    private static GameData instance;

    private final Map<String, Species> speciesMap = new HashMap<>();
    private final Map<String, Move> moveMap = new HashMap<>();
    private final Map<String, Nature> natureMap = new HashMap<>();
    private final List<Nature> allNatures = new ArrayList<>();
    private final Map<String, List<LearnableMove>> learnableMovesCache = new HashMap<>();

    private GameData() {
        loadData();
    }

    /**
     * 获取全局唯一实例（懒加载）。
     *
     * @return GameData 单例
     */
    public static GameData instance() {
        if (instance == null) {
            instance = new GameData();
        }
        return instance;
    }

    /**
     * 加载全部静态数据。
     *
     * <p>加载顺序：species 和 moves 必须先加载，再加载 learnable_moves（依赖前两者），
     * 最后加载 natures。全部加载完成后进行数据验证并打印统计信息。</p>
     */
    private void loadData() {
        loadSpecies();
        loadMoves();
        loadLearnableMoves();
        loadNatures();
        validateData();
        printStatistics();
    }

    /**
     * 从 classpath 加载种族数据。
     */
    private void loadSpecies() {
        try (InputStream in = openResource(SPECIES_CSV);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split(",", -1);
                String id = f[0];
                String name = f[1];
                List<ElementType> types = new ArrayList<>();
                types.add(ElementType.valueOf(f[2]));
                if (!f[3].isEmpty()) {
                    types.add(ElementType.valueOf(f[3]));
                }
                BaseStats baseStats = new BaseStats(
                        Integer.parseInt(f[4]), Integer.parseInt(f[5]), Integer.parseInt(f[6]),
                        Integer.parseInt(f[7]), Integer.parseInt(f[8]), Integer.parseInt(f[9]));
                int evolutionLevel = Integer.parseInt(f[10]);
                String evolutionTarget = f[11].isEmpty() ? null : f[11];
                int baseExpYield = Integer.parseInt(f[12]);
                double captureRate = Double.parseDouble(f[13]);
                String category = f[14];
                String description = f[15];
                speciesMap.put(id, new Species(id, name, types, baseStats, evolutionLevel,
                        evolutionTarget, baseExpYield, captureRate, category, description));
            }
            LogUtil.info("加载种族数据完成，共 " + speciesMap.size() + " 个种族");
        } catch (IOException e) {
            throw new RuntimeException("加载 " + SPECIES_CSV + " 失败", e);
        }
    }

    /**
     * 从 classpath 加载技能数据。
     */
    private void loadMoves() {
        try (InputStream in = openResource(MOVES_CSV);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split(",", -1);
                Move move = new Move(f[0], f[1], ElementType.valueOf(f[2]), MoveCategory.valueOf(f[3]),
                        Integer.parseInt(f[4]), Integer.parseInt(f[5]),
                        Integer.parseInt(f[6]), Integer.parseInt(f[7]));
                moveMap.put(move.getId(), move);
            }
            LogUtil.info("加载技能数据完成，共 " + moveMap.size() + " 个技能");
        } catch (IOException e) {
            throw new RuntimeException("加载 " + MOVES_CSV + " 失败", e);
        }
    }

    /**
     * 从 classpath 加载学招关系数据。依赖 speciesMap 与 moveMap 已填充。
     */
    private void loadLearnableMoves() {
        try (InputStream in = openResource(LEARNABLE_MOVES_CSV);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split(",", -1);
                String speciesId = f[0];
                int level = Integer.parseInt(f[1]);
                String moveId = f[2];
                Species species = speciesMap.get(speciesId);
                Move move = moveMap.get(moveId);
                if (species == null || move == null) {
                    System.err.println("跳过无效学招记录（找不到种族或技能）: " + line);
                    continue;
                }
                species.addLearnableMove(new LearnableMove(moveId, level));
            }
        } catch (IOException e) {
            throw new RuntimeException("加载 " + LEARNABLE_MOVES_CSV + " 失败", e);
        }

        // 按 speciesId 分组缓存
        learnableMovesCache.clear();
        for (Species species : speciesMap.values()) {
            learnableMovesCache.put(species.getId(), new ArrayList<>(species.getLearnableMoves()));
        }
        LogUtil.info("加载学招数据完成，已缓存 " + learnableMovesCache.size() + " 个种族的学招表");
    }

    /**
     * 从 classpath 加载性格数据；CSV 加载失败时回退到硬编码常量。
     */
    private void loadNatures() {
        try (InputStream in = openResource(NATURES_CSV);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split(",", -1);
                StatModifier increasedStat = f[2].isEmpty() ? null : StatModifier.valueOf(f[2]);
                StatModifier decreasedStat = f[3].isEmpty() ? null : StatModifier.valueOf(f[3]);
                Nature nature = new Nature(f[0], f[1], increasedStat, decreasedStat);
                natureMap.put(nature.getId(), nature);
                allNatures.add(nature);
            }
            LogUtil.info("加载性格数据完成，共 " + allNatures.size() + " 种性格");
        } catch (IOException e) {
            System.err.println("加载 " + NATURES_CSV + " 失败，回退到硬编码性格常量: " + e.getMessage());
            initNaturesFallback();
        }
    }

    /**
     * 性格数据硬编码回退方案。
     */
    private void initNaturesFallback() {
        Nature[] natures = {
                Nature.HARDY, Nature.LONELY, Nature.BRAVE, Nature.ADAMANT, Nature.NAUGHTY,
                Nature.BOLD, Nature.RELAXED, Nature.IMPISH, Nature.LAX, Nature.TIMID,
                Nature.HASTY, Nature.JOLLY, Nature.NAIVE, Nature.MODEST, Nature.MILD,
                Nature.QUIET, Nature.RASH, Nature.CALM, Nature.GENTLE, Nature.SASSY,
                Nature.CAREFUL
        };
        natureMap.clear();
        allNatures.clear();
        for (Nature nature : natures) {
            natureMap.put(nature.getId(), nature);
            allNatures.add(nature);
        }
    }

    /**
     * 验证加载完成后的必要数据是否齐全。
     *
     * <p>缺失关键数据时抛出 {@link IllegalStateException}，
     * 学招表缺失则输出警告（不阻断启动）。</p>
     *
     * @throws IllegalStateException 种族、技能或性格数据为空时抛出
     */
    private void validateData() {
        if (speciesMap.isEmpty()) {
            throw new IllegalStateException("数据验证失败：未加载到任何种族数据");
        }
        if (moveMap.isEmpty()) {
            throw new IllegalStateException("数据验证失败：未加载到任何技能数据");
        }
        if (allNatures.isEmpty()) {
            throw new IllegalStateException("数据验证失败：未加载到任何性格数据");
        }
        getInitialPool();
        for (Species species : speciesMap.values()) {
            if (species.getLearnableMoves().isEmpty()) {
                System.err.println("警告：种族 " + species.getId() + " 未配置任何学招记录");
            }
        }
    }

    /**
     * 打印数据加载统计信息。
     */
    private void printStatistics() {
        int totalLearnable = learnableMovesCache.values().stream().mapToInt(List::size).sum();
        LogUtil.info("=== 数据加载统计 ===");
        LogUtil.info("种族数量: " + speciesMap.size());
        LogUtil.info("技能数量: " + moveMap.size());
        LogUtil.info("性格数量: " + allNatures.size());
        LogUtil.info("学招记录总数: " + totalLearnable);
    }

    /**
     * 打开 classpath 资源。
     *
     * @param path 资源路径
     * @return 输入流
     * @throws IOException 资源不存在时抛出
     */
    private InputStream openResource(String path) throws IOException {
        InputStream in = getClass().getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IOException("classpath 中找不到资源: " + path);
        }
        return in;
    }

    /**
     * 返回全部种族的副本。
     */
    public List<Species> getAllSpecies() {
        return new ArrayList<>(speciesMap.values());
    }

    /**
     * 按 id 查询种族。
     *
     * @param id 种族 id
     * @return 种族包装，不存在时为空
     */
    public Optional<Species> getSpecies(String id) {
        return Optional.ofNullable(speciesMap.get(id));
    }

    /**
     * 返回全部技能的副本。
     */
    public List<Move> getAllMoves() {
        return new ArrayList<>(moveMap.values());
    }

    /**
     * 按 id 查询技能。
     *
     * @param id 技能 id
     * @return 技能包装，不存在时为空
     */
    public Optional<Move> getMove(String id) {
        return Optional.ofNullable(moveMap.get(id));
    }

    /**
     * 返回全部性格的副本。
     */
    public List<Nature> getAllNatures() {
        return new ArrayList<>(allNatures);
    }

    /**
     * 按 id 查询性格。
     *
     * @param id 性格 id
     * @return 性格包装，不存在时为空
     */
    public Optional<Nature> getNature(String id) {
        return Optional.ofNullable(natureMap.get(id));
    }

    /**
     * 按种族 id 返回缓存的学招表副本。
     *
     * @param speciesId 种族 id
     * @return 学招列表，无记录时返回空列表
     */
    public List<LearnableMove> getLearnableMoves(String speciesId) {
        return new ArrayList<>(learnableMovesCache.getOrDefault(speciesId, List.of()));
    }

    /**
     * 根据种族 id 创建宝可梦：个体值随机、性格为勤奋（HARDY）。
     *
     * @param speciesId 种族 id
     * @param level 初始等级
     * @return 新创建的宝可梦
     * @throws IllegalArgumentException 种族 id 不存在时抛出
     */
    public Pokemon createPokemon(String speciesId, int level) {
        Species species = speciesMap.get(speciesId);
        if (species == null) {
            throw new IllegalArgumentException("未知的种族id: " + speciesId);
        }
        Stats ivs = Stats.randomIv();
        return new Pokemon(species, level, ivs, Nature.HARDY);
    }

    /**
     * 返回初始可选宝可梦池。
     *
     * @return 初始可选种族列表
     * @throws IllegalStateException 初始种族数据缺失时抛出
     */
    public List<Species> getInitialPool() {
        Species bulbasaur = speciesMap.get("bulbasaur");
        Species charmander = speciesMap.get("charmander");
        Species squirtle = speciesMap.get("squirtle");
        if (bulbasaur == null || charmander == null || squirtle == null) {
            throw new IllegalStateException("初始宝可梦池加载不完整，缺少种族数据");
        }
        return List.of(bulbasaur, charmander, squirtle);
    }
}
