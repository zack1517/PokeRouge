package org.example.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.battle.BattleDataPort;
import org.example.growth.GrowthProgress;
import org.example.growth.GrowthService;
import org.example.model.ElementType;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.MoveEffect;
import org.example.model.MoveSlot;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.RouteConfig;
import org.example.model.Stat;
import org.example.model.Trainer;
import org.example.pokemon.domain.LearnableMove;
import org.example.pokemon.domain.Species;
import org.example.pokemon.infrastructure.GameData;
import org.example.pokemon.service.PokemonService;
import org.example.pokemon.service.PokemonServiceImpl;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * {@link PokemonBattleAdapter} 的接缝测试：验证新宝可梦系统与既有战斗系统之间的
 * 数据转换正确，并防止枚举漂移导致运行时崩溃。
 */
class PokemonBattleAdapterTest {

    /** 使用隔离的成长进度，避免测试读到开发者本机的真实成长存档。 */
    private final PokemonService service = new PokemonServiceImpl(new GrowthProgress());

    /** 精确等级创建应无 ±2 浮动：生成结果等级与目标一致（道馆主固定等级配置依赖此口径）。 */
    @Test
    void testCreateWildPokemonExact_levelMatchesExactly() {
        for (int i = 0; i < 10; i++) {
            Optional<Pokemon> wild = PokemonBattleAdapter.createWildPokemonExact(11, new GrowthProgress());
            assertTrue(wild.isPresent());
            assertEquals(11, wild.get().getLevel(), "精确等级创建不应有 ±2 浮动");
        }
    }

    /** 初始精灵转交战斗系统后，身份、属性与种族值应一一对应。 */
    @Test
    void testCreateBattlePlayer_mapsIdentityTypesAndBaseStats() {
        Species species = service.getInitialPool().get(0);
        org.example.pokemon.domain.Pokemon starter = service.createPokemon(species.getId(), 5);

        Player player = PokemonBattleAdapter.createBattlePlayer("测试玩家", starter);

        assertEquals("测试玩家", player.getName());
        assertEquals(1, player.getPartySize());
        Pokemon battlePokemon = player.getActive();
        assertNotNull(battlePokemon);
        assertEquals(starter.getName(), battlePokemon.getName());
        assertEquals(starter.getLevel(), battlePokemon.getLevel());
        assertEquals(species.getId(), battlePokemon.getSpecies().getId());
        assertEquals(species.getName(), battlePokemon.getSpecies().getName());
        assertEquals(
                species.getTypes().stream().map(type -> type.name()).collect(Collectors.toList()),
                battlePokemon.getSpecies().getTypes().stream().map(type -> type.name()).collect(Collectors.toList()));
        assertEquals(species.getBaseStats().getHp(), battlePokemon.getSpecies().getBaseStats().getHp());
        assertEquals(species.getBaseStats().getAttack(), battlePokemon.getSpecies().getBaseStats().getAttack());
        assertEquals(species.getBaseStats().getDefense(), battlePokemon.getSpecies().getBaseStats().getDefense());
        assertEquals(species.getBaseStats().getSpAttack(), battlePokemon.getSpecies().getBaseStats().getSpAttack());
        assertEquals(species.getBaseStats().getSpDefense(), battlePokemon.getSpecies().getBaseStats().getSpDefense());
        assertEquals(species.getBaseStats().getSpeed(), battlePokemon.getSpecies().getBaseStats().getSpeed());
        assertMovesAreValid(battlePokemon);
    }

    /** 初始精灵的技能应按习得表按等级筛选（去重、含数据校验、上限 4 个）。 */
    @Test
    void testCreateBattlePlayer_knownMovesFollowLearnableTable() {
        Species species = service.getInitialPool().get(0);
        org.example.pokemon.domain.Pokemon starter = service.createPokemon(species.getId(), 5);

        Player player = PokemonBattleAdapter.createBattlePlayer("测试玩家", starter);
        Pokemon battlePokemon = player.getActive();

        int level = starter.getLevel();
        long eligible = species.getLearnableMoves().stream()
                .filter(learnable -> learnable.getLevel() <= level)
                .map(LearnableMove::getMoveId)
                .distinct()
                .filter(moveId -> GameData.instance().getMove(moveId).isPresent())
                .count();
        assertEquals(Math.min(Pokemon.MAX_MOVES, (int) eligible), battlePokemon.getMoves().size());
    }

    /** 野生精灵应能在新系统图鉴中回查、等级在目标值 ±2 内且满足进化链合法性（前一进化型进化等级≤实际等级，且自身未过进化等级）、技能转换有效。 */
    @Test
    void testCreateWildPokemon_levelWithinOffsetAndMovesValid() {
        Optional<Pokemon> wild = PokemonBattleAdapter.createWildPokemon(5);

        assertTrue(wild.isPresent());
        Pokemon wildPokemon = wild.get();
        assertTrue(wildPokemon.getLevel() >= 3 && wildPokemon.getLevel() <= 7,
                "野生等级应在 5±2 范围内，实际为 " + wildPokemon.getLevel());
        // 野生精灵从新系统全图鉴抽取，且需同时满足两条进化链筛选（退化型未进化到位的不出、已过进化等级的过时形态不出）
        assertFormLegalAtLevel(wildPokemon);
        assertMovesAreValid(wildPokemon);
    }

    /** 低等级遭遇多次也不应出现非法形态：既无「退化型未进化到位」的早熟形态（如 10 级耿鬼），也无「自身已过进化等级」的过时形态（如 10 级绿毛虫）。 */
    @Test
    void testCreateWildPokemon_lowLevelNeverYieldsIllegalEvolvedForms() {
        for (int i = 0; i < 50; i++) {
            Optional<Pokemon> wild = PokemonBattleAdapter.createWildPokemon(10);
            assertTrue(wild.isPresent());
            assertFormLegalAtLevel(wild.get());
        }
    }

    /** 已过自身进化等级的形态不应再出现：20 级（±2）遭遇不会出现 16 级就该进化的初始形态。 */
    @Test
    void testCreateWildPokemon_neverYieldsFormsPastTheirEvolutionLevel() {
        Set<String> earlyForms = Set.of("bulbasaur", "charmander", "squirtle", "abra", "caterpie");
        for (int i = 0; i < 50; i++) {
            Optional<Pokemon> wild = PokemonBattleAdapter.createWildPokemon(20, new GrowthProgress());
            assertTrue(wild.isPresent());
            assertFalse(earlyForms.contains(wild.get().getSpecies().getId()),
                    "20 级不应出现已过进化等级的形态：" + wild.get().getName());
            assertFormLegalAtLevel(wild.get());
        }
    }

    /** 精确等级创建同样受进化链合法性约束（道馆主固定等级配置依赖此口径）。 */
    @Test
    void testCreateWildPokemonExact_respectsEvolutionLegality() {
        for (int level : new int[]{11, 17, 24, 32}) {
            for (int i = 0; i < 20; i++) {
                Optional<Pokemon> wild = PokemonBattleAdapter.createWildPokemonExact(level, new GrowthProgress());
                assertTrue(wild.isPresent());
                assertEquals(level, wild.get().getLevel(), "精确等级创建不应有浮动");
                assertFormLegalAtLevel(wild.get());
            }
        }
    }

    // ------------------------------------------------------------------
    // 段号出现规则：段 1 无进化宝可梦不出场、绿毛虫一家加权；段 2 无进化宝可梦降权；段 3 起等权
    // ------------------------------------------------------------------

    /** 无进化链宝可梦（自身不进化、也无前序进化型）物种 id，段 1 不应出现在遭遇候选池。 */
    private static final Set<String> NO_EVOLUTION_IDS =
            Set.of("pikachu", "eevee", "vulpix", "lapras", "snorlax", "chansey", "aerodactyl");

    /** 段 1 不应出现无进化宝可梦：多次采样（固定等级 5）均不得抽中无进化链物种。 */
    @Test
    void testCreateWildPokemon_segment1NeverYieldsNoEvolutionSpecies() {
        GrowthProgress progress = new GrowthProgress();
        for (int i = 0; i < 200; i++) {
            Optional<Pokemon> wild = PokemonBattleAdapter.createWildPokemonExact(5, 1, progress);
            assertTrue(wild.isPresent());
            assertFalse(NO_EVOLUTION_IDS.contains(wild.get().getSpecies().getId()),
                    "段 1 不应出现无进化宝可梦：" + wild.get().getName());
        }
    }

    /** 段 1 绿毛虫一家权重更高：多次采样统计应显著高于无加权时的期望占比。 */
    @Test
    void testCreateWildPokemon_segment1BoostsCaterpieLine() {
        GrowthProgress progress = new GrowthProgress();
        int caterpieLine = 0;
        int samples = 3000;
        for (int i = 0; i < samples; i++) {
            Optional<Pokemon> wild = PokemonBattleAdapter.createWildPokemonExact(5, 1, progress);
            assertTrue(wild.isPresent());
            if (Set.of("caterpie", "metapod", "butterfree").contains(wild.get().getSpecies().getId())) {
                caterpieLine++;
            }
        }
        // 等级 5 的段 1 候选约 22 只基础形态 + 绿毛虫额外 1 份：加权后期望 ≈ 261/3000；
        // 无加权时绿毛虫期望 ≈ 136/3000，其 5σ 上界 ≈ 193，故以 200 为「加权生效」的可靠下界。
        assertTrue(caterpieLine >= 200,
                "段 1 绿毛虫一家出现次数应显著高于无加权期望，实际 " + caterpieLine + "/" + samples);
    }

    /** 第 2 段无进化宝可梦应进入遭遇候选池（虽经降权，仍可被抽中）。 */
    @Test
    void testCreateWildPokemon_segment2AllowsNoEvolutionSpecies() {
        GrowthProgress progress = new GrowthProgress();
        boolean seen = false;
        for (int i = 0; i < 200; i++) {
            Optional<Pokemon> wild = PokemonBattleAdapter.createWildPokemonExact(20, 2, progress);
            assertTrue(wild.isPresent());
            if (NO_EVOLUTION_IDS.contains(wild.get().getSpecies().getId())) {
                seen = true;
                break;
            }
        }
        assertTrue(seen, "第 2 段起无进化宝可梦应进入遭遇候选池");
    }

    /**
     * 候选池组成的确定性断言（不依赖采样随机）：
     * 段 1 排除无进化链宝可梦、绿毛虫一家 2 份；段 2 无进化链 1 份、其余候选 2 份；
     * 段 3~{@link RouteConfig#TOTAL_SEGMENTS} 全部等权（每只 1 份，规则恢复默认）。
     */
    @Test
    void testWildCandidates_segmentRulesOnNoEvolutionSpecies() {
        int level = 5;
        Map<String, Long> seg1 = candidatesById(level, 1);
        assertTrue(seg1.keySet().stream().noneMatch(NO_EVOLUTION_IDS::contains),
                "段 1 候选池不应包含无进化链宝可梦：" + seg1.keySet());
        assertEquals(2L, seg1.get("caterpie"), "段 1 绿毛虫一家应按 2 份加权");
        assertEquals(1L, seg1.get("bulbasaur"), "段 1 其余候选应保持 1 份");

        Map<String, Long> seg2 = candidatesById(level, 2);
        for (String id : NO_EVOLUTION_IDS) {
            assertEquals(1L, seg2.getOrDefault(id, 0L), "段 2 无进化链宝可梦应只占 1 份：" + id);
        }
        assertEquals(2L, seg2.get("bulbasaur"), "段 2 其余候选应按 2 份计入（无进化链相对概率减半）");

        for (int segment = 3; segment <= RouteConfig.TOTAL_SEGMENTS; segment++) {
            Map<String, Long> pool = candidatesById(level, segment);
            assertTrue(pool.values().stream().allMatch(count -> count == 1L),
                    "段 " + segment + " 候选池应全部等权（每只 1 份）");
            assertTrue(pool.keySet().containsAll(NO_EVOLUTION_IDS),
                    "段 " + segment + " 候选池应包含无进化链宝可梦");
        }
    }

    /** 统计候选池内各物种的条目份数（候选池用重复条目实现权重）。 */
    private static Map<String, Long> candidatesById(int level, int segment) {
        return PokemonBattleAdapter.wildCandidates(level, segment).stream()
                .collect(Collectors.groupingBy(Species::getId, Collectors.counting()));
    }

    /**
     * 断言该野生精灵的形态与遭遇等级匹配（两条筛选规则都不得违反）：
     * 前一进化型（退化型）的进化等级不高于实际等级；有进化目标的形态其进化等级不低于实际等级。
     */
    private static void assertFormLegalAtLevel(Pokemon wild) {
        for (Species pre : GameData.instance().getAllSpecies()) {
            if (wild.getSpecies().getId().equals(pre.getEvolutionTarget())) {
                assertTrue(pre.getEvolutionLevel() <= wild.getLevel(),
                        "非法遭遇：" + wild.getSpecies().getId() + "（" + wild.getName() + "）出现在等级 "
                                + wild.getLevel() + "，但其前一进化型 " + pre.getId() + " 在 "
                                + pre.getEvolutionLevel() + " 级才进化");
            }
        }
        Species source = GameData.instance().getSpecies(wild.getSpecies().getId())
                .orElseThrow(() -> new AssertionError(
                        "野生精灵种族不在新系统图鉴中: " + wild.getSpecies().getId()));
        if (source.getEvolutionTarget() != null) {
            assertTrue(source.getEvolutionLevel() >= wild.getLevel(),
                    "非法遭遇：" + source.getId() + "（" + wild.getName() + "）出现在等级 "
                            + wild.getLevel() + "，但其在 " + source.getEvolutionLevel() + " 级就该进化");
        }
    }

    /** 新体系全部属性必须能在旧战斗模型中解析（防枚举漂移）。 */
    @Test
    void testElementType_everyNewTypeIsMappableToLegacy() {
        for (org.example.pokemon.domain.ElementType type : org.example.pokemon.domain.ElementType.values()) {
            assertNotNull(ElementType.parse(type.name()),
                    "新体系属性 " + type.name() + " 在旧战斗模型中缺少对应枚举");
        }
    }

    /** 小队对战：队伍按顺序转交战斗系统（第 0 只首发），全员满级、满血、满 PP。 */
    @Test
    void testCreateBattlePlayer_squadKeepsOrderAndFullState() {
        List<Species> all = GameData.instance().getAllSpecies();
        List<org.example.pokemon.domain.Pokemon> squad = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            squad.add(service.createPokemon(all.get(i).getId(), 100));
        }

        Player player = PokemonBattleAdapter.createBattlePlayer("小队测试", squad);

        assertEquals(6, player.getPartySize());
        assertEquals(squad.get(0).getName(), player.getActive().getName(), "队伍第 0 只应为首发出战");
        for (int i = 0; i < player.getPartySize(); i++) {
            Pokemon member = player.getParty().get(i);
            assertEquals(100, member.getLevel(), member.getName() + " 应为满级 Lv.100");
            assertEquals(member.getMaxHp(), member.getCurrentHp(), member.getName() + " 应满血入场");
            for (MoveSlot slot : member.getMoveSlots()) {
                assertEquals(slot.getMaxPp(), slot.getPp(), member.getName() + " 技能 PP 应补满");
            }
        }
    }

    /** 小队对战对手：随机不重复抽取指定数量、满级满状态的整队；数量上限 6。 */
    @Test
    void testCreateSquadTrainer_buildsRequestedCountAtFullState() {
        Trainer trainer = PokemonBattleAdapter.createSquadTrainer("测试对手", 3, 100);

        assertEquals(3, trainer.getPartySize());
        assertEquals(3L, trainer.getParty().stream()
                .map(member -> member.getSpecies().getId()).distinct().count(), "对手队伍不应重复抽取种族");
        for (Pokemon member : trainer.getParty()) {
            assertEquals(100, member.getLevel(), member.getName() + " 应为满级 Lv.100");
            assertEquals(member.getMaxHp(), member.getCurrentHp(), member.getName() + " 应满血入场");
        }
        // 数量边界：1 vs 1 / 2 vs 2 等小数量模式按需生成；超过 6 只按 6 截断
        assertEquals(1, PokemonBattleAdapter.createSquadTrainer("单人对手", 1, 100).getPartySize());
        assertEquals(6, PokemonBattleAdapter.createSquadTrainer("满编对手", 9, 100).getPartySize());
    }

    /** 新体系全部技能类别必须能在旧战斗模型中解析（防枚举漂移）。 */
    @Test
    void testMoveCategory_everyNewCategoryIsMappableToLegacy() {
        for (org.example.pokemon.domain.MoveCategory category : org.example.pokemon.domain.MoveCategory.values()) {
            assertDoesNotThrow(() -> MoveCategory.valueOf(category.name()),
                    "新体系类别 " + category.name() + " 在旧战斗模型中缺少对应枚举");
        }
    }

    /**
     * 个体值必须随个体一起跨系统转换（原先在接缝处被丢弃并重新随机）。
     *
     * <p>这是「捕捉次数 → 个体值加成」能被玩家感知到的关键一环：成长模块提升的是新体系
     * 精灵的个体值，若接缝不搬运，战斗面板就看不到任何变化。</p>
     */
    @Test
    void testToBattlePokemon_carriesIvs() {
        Species species = service.getInitialPool().get(0);
        org.example.pokemon.domain.Pokemon starter = service.createPokemon(species.getId(), 20);

        Player player = PokemonBattleAdapter.createBattlePlayer("测试玩家", starter);
        Pokemon battlePokemon = player.getActive();

        org.example.pokemon.domain.Stats source = starter.getIvs();
        assertEquals(source.getHpIv(), battlePokemon.getIvs().getHp());
        assertEquals(source.getAttackIv(), battlePokemon.getIvs().getAttack());
        assertEquals(source.getDefenseIv(), battlePokemon.getIvs().getDefense());
        assertEquals(source.getSpAttackIv(), battlePokemon.getIvs().getSpAttack());
        assertEquals(source.getSpDefenseIv(), battlePokemon.getIvs().getSpDefense());
        assertEquals(source.getSpeedIv(), battlePokemon.getIvs().getSpeed());
    }

    /** 局外成长加成应经接缝传导到野生遭遇的战斗模型上（满加成 → 满个体）。 */
    @Test
    void testCreateWildPokemon_ivBonusReachesBattleModel() {
        org.example.growth.GrowthProgress progress = new org.example.growth.GrowthProgress();
        int captures = org.example.growth.IvGrowthRule.CAPTURES_PER_STEP
                * org.example.growth.IvGrowthRule.MAX_IV;
        for (int i = 0; i < captures; i++) {
            progress.recordCapture(service.getInitialPool().get(0).getId());
        }

        Optional<Pokemon> wild = PokemonBattleAdapter.createWildPokemon(20, progress);

        assertTrue(wild.isPresent());
        org.example.model.Stats ivs = wild.get().getIvs();
        assertEquals(31, ivs.getHp(), "满成长加成下野生精灵应为满个体");
        assertEquals(31, ivs.getAttack());
        assertEquals(31, ivs.getDefense());
        assertEquals(31, ivs.getSpAttack());
        assertEquals(31, ivs.getSpDefense());
        assertEquals(31, ivs.getSpeed());
    }

    /**
     * 变化类技能的效果（专属效果 / 能力等级变化）必须经接缝完整搬运。
     *
     * <p>回归防护：这两项漏搬时，招式在战斗引擎里会被判定为「空变化招」，玩家看到的只有
     * 「但是什么也没有发生……」。</p>
     */
    @Test
    void testToBattleMove_carriesEffectsAndStatChanges() {
        Move growl = PokemonBattleAdapter.toBattleMove(requireLibraryMove("growl"));
        assertEquals(1, growl.getStatChanges().size(), "叫声应带一项能力等级变化");
        assertEquals(org.example.model.StatChange.Recipient.OPPONENT,
                growl.getStatChanges().get(0).recipient());
        assertEquals(Stat.ATTACK, growl.getStatChanges().get(0).stat());
        assertEquals(-1, growl.getStatChanges().get(0).delta());

        assertEquals(org.example.model.StatChange.Recipient.SELF,
                PokemonBattleAdapter.toBattleMove(requireLibraryMove("harden")).getStatChanges().get(0).recipient(),
                "硬邦邦应作用于自身");

        assertEquals(MoveEffect.PROTECT, PokemonBattleAdapter.toBattleMove(requireLibraryMove("protect")).getEffect());
        assertEquals(MoveEffect.LEECH_SEED, PokemonBattleAdapter.toBattleMove(requireLibraryMove("leech-seed")).getEffect());
        assertEquals(MoveEffect.REST, PokemonBattleAdapter.toBattleMove(requireLibraryMove("rest")).getEffect());

        Move tackle = PokemonBattleAdapter.toBattleMove(requireLibraryMove("tackle"));
        assertEquals(MoveEffect.NONE, tackle.getEffect(), "攻击招不应凭空获得专属效果");
        assertTrue(tackle.getStatChanges().isEmpty(), "攻击招不应凭空获得能力等级变化");
    }

    /** 新体系全部招式效果必须能在旧战斗模型中解析（防枚举漂移，守住/寄生种子/睡觉最容易漏）。 */
    @Test
    void testMoveEffect_everyNewEffectIsMappableToLegacy() {
        for (org.example.pokemon.domain.MoveEffect effect : org.example.pokemon.domain.MoveEffect.values()) {
            if (effect == org.example.pokemon.domain.MoveEffect.NONE) {
                continue;
            }
            assertNotEquals(MoveEffect.NONE, MoveEffect.parse(effect.name()),
                    "新体系效果 " + effect.name() + " 在旧战斗模型中缺少对应枚举");
        }
    }

    /**
     * 宝可梦库中任何变化招转换后都必须保留至少一项效果，否则战斗日志只会是「但是什么也没有发生……」。
     *
     * <p>例外：{@code splash}（跃起）在原作中<b>本就毫无效果</b>，属于数据侧有意为之，不视为降级。</p>
     */
    @Test
    void testToBattleMove_noStatusMoveDegradesToEmptyEffect() {
        int checked = 0;
        Set<String> intentionallyNoEffect = Set.of("splash");
        for (org.example.pokemon.domain.Move source : GameData.instance().getAllMoves()) {
            if (source.getCategory() != org.example.pokemon.domain.MoveCategory.STATUS) {
                continue;
            }
            checked++;
            if (intentionallyNoEffect.contains(source.getId())) {
                continue;
            }
            Move battle = PokemonBattleAdapter.toBattleMove(source);
            assertTrue(battle.getEffect() != MoveEffect.NONE || battle.hasStatChanges() || battle.hasInfliction(),
                    "变化招 " + source.getId() + " 经接缝转换后效果全部丢失");
        }
        assertTrue(checked > 0, "宝可梦库应至少包含一个变化招");
    }

    /** 端到端：走游戏真实数据端口（{@link PokemonLibraryDataPort}）拿到的变化招必须在战斗引擎里真正生效。 */
    @Test
    void testLibraryStatusMove_actuallyTakesEffectInBattle() {
        org.example.battle.BattleDataPort port = PokemonBattleAdapter.battleDataPort();
        Move growl = port.findMove("growl");
        assertNotNull(growl, "宝可梦库应定义叫声");

        org.example.model.Species species = port.findSpecies("bulbasaur");
        Pokemon mine = Pokemon.create(species, 20, List.of(growl), NO_IV);
        Pokemon foe = Pokemon.create(species, 20, List.of(port.findMove("tackle")), NO_IV);
        Player player = new Player("玩家");
        player.addPokemon(mine);

        org.example.battle.BattleService engine =
                org.example.battle.BattleServices.newBattle(player, foe, new java.util.Random(7));
        engine.useMove(mine.getMoveSlots().get(0));

        String log = String.join("\n", engine.getLog());
        assertEquals(-1, foe.getStatStage(Stat.ATTACK), "叫声应降低对方物攻：" + log);
        assertTrue(log.contains("物攻"), "应播报能力等级下降：" + log);
        assertFalse(log.contains("什么也没有发生"), "不应落到无效果兜底：" + log);
    }

    /** 无个体值，使端到端断言不受个体浮动影响。 */
    private static final org.example.model.Stats NO_IV = new org.example.model.Stats(0, 0, 0, 0, 0, 0);

    /** 按 id 取出宝可梦库中必然存在的技能。 */
    private static org.example.pokemon.domain.Move requireLibraryMove(String moveId) {
        return GameData.instance().getMove(moveId)
                .orElseThrow(() -> new AssertionError("宝可梦库中缺少技能: " + moveId));
    }

    /** 种族经验值 baseExp 必须随种族一起跨系统转换，否则经验折算会退化。 */
    @Test
    void testToBattleSpecies_carriesBaseExpYield() {
        Species species = service.getInitialPool().get(0);
        org.example.pokemon.domain.Pokemon starter = service.createPokemon(species.getId(), 5);

        Player player = PokemonBattleAdapter.createBattlePlayer("测试玩家", starter);

        assertTrue(species.getBaseExpYield() > 0, "新体系种族数据应提供种族经验值");
        assertEquals(species.getBaseExpYield(),
                player.getActive().getSpecies().getBaseExpYield(),
                "战斗模型种族应带上原种族的经验值，经验折算才与种族挂钩");
    }

    // ------------------------------------------------------------------
    // 学招等级门槛（回归：高等级招式曾因被当作「出生技能」而在低等级绕过门槛学会）
    // ------------------------------------------------------------------

    /** 转换到战斗模型后每条学招记录必须保留真实习得等级：终极吸取（16 级）不得变成 1 级。 */
    @Test
    void testToBattleSpecies_keepsTrueLearnLevels() {
        Species bulbasaur = GameData.instance().getSpecies("bulbasaur").orElseThrow();
        org.example.model.Species battle = PokemonBattleAdapter.toBattleSpecies(bulbasaur);

        Map<String, Integer> minLevels = battle.getLearnableMoves().stream()
                .collect(Collectors.toMap(org.example.model.LearnableMove::getMoveId,
                        org.example.model.LearnableMove::getLevel, Math::min));

        assertEquals(1, minLevels.get("tackle"), "撞击应为 1 级出生技能");
        assertEquals(6, minLevels.get("sleep-powder"), "催眠粉应为 6 级习得");
        assertEquals(16, minLevels.get("giga-drain"),
                "终极吸取必须保留 16 级门槛（曾被误标为 1 级出生技能，导致低等级直接学会）");
    }

    /** 泛化防护：全部种族的学招等级经接缝转换后必须与宝可梦库数据完全一致。 */
    @Test
    void testToBattleSpecies_allSpeciesKeepTrueLevels() {
        for (Species source : GameData.instance().getAllSpecies()) {
            org.example.model.Species battle = PokemonBattleAdapter.toBattleSpecies(source);
            Map<String, Integer> expected = source.getLearnableMoves().stream()
                    .collect(Collectors.toMap(LearnableMove::getMoveId, LearnableMove::getLevel, Math::min));
            Map<String, Integer> actual = battle.getLearnableMoves().stream()
                    .collect(Collectors.toMap(org.example.model.LearnableMove::getMoveId,
                            org.example.model.LearnableMove::getLevel, Math::min));
            for (Map.Entry<String, Integer> entry : expected.entrySet()) {
                assertEquals(entry.getValue(), actual.get(entry.getKey()),
                        source.getId() + " 的 " + entry.getKey() + " 习得等级经转换后被篡改");
            }
        }
    }

    /** 行为回归：真实升级结算（5 → 6 级）不得学会 9/12/16 级才解锁的招式。 */
    @Test
    void testGrowth_level6BulbasaurDoesNotLearnFutureMoves() {
        org.example.model.Pokemon seed = PokemonBattleAdapter.toBattlePokemon(
                service.createPokemon("bulbasaur", 5));

        BattleDataPort dataPort = PokemonBattleAdapter.battleDataPort();
        GrowthService growth = new GrowthService(dataPort, new GrowthProgress());
        // 固定 8 级对手：每轮折算 73 点经验，从 5 级升级到 6 级恰好且不会跳级
        org.example.model.Pokemon dummy = PokemonBattleAdapter.toBattlePokemon(
                service.createPokemon("bulbasaur", 8, org.example.pokemon.domain.Nature.HARDY));

        for (int i = 0; i < 20 && seed.getLevel() < 6; i++) {
            growth.settle(List.of(seed), List.of(dummy));
        }

        assertEquals(6, seed.getLevel(), "前提：结算后应恰为 6 级");
        assertTrue(seed.knowsMove("sleep-powder"), "6 级应学会本等级的催眠粉");
        assertFalse(seed.knowsMove("leech-seed"), "6 级不得学会 9 级的寄生种子");
        assertFalse(seed.knowsMove("razor-leaf"), "6 级不得学会 12 级的飞叶快刀");
        assertFalse(seed.knowsMove("poison-powder"), "6 级不得学会 16 级的毒粉");
        assertFalse(seed.knowsMove("giga-drain"), "6 级不得学会 16 级的终极吸取");
    }

    /**
     * 反向回归：等级门槛修复不得把同等级追加条目「改丢」。15 → 16 级（也是妙蛙种子进化等级）
     * 必须同时学会毒粉与终极吸取（两条 16 级记录），进化换族后不得丢失。
     */
    @Test
    void testGrowth_level16Bulbasaur_canStillLearnGigaDrain() {
        org.example.model.Pokemon seed = PokemonBattleAdapter.toBattlePokemon(
                service.createPokemon("bulbasaur", 15, org.example.pokemon.domain.Nature.HARDY));

        BattleDataPort dataPort = PokemonBattleAdapter.battleDataPort();
        GrowthService growth = new GrowthService(dataPort, new GrowthProgress());
        // 50 级对手每轮折算 457 点经验：从 15 级升到 16 级恰好在两轮内完成且不会跳到 17 级
        org.example.model.Pokemon dummy = PokemonBattleAdapter.toBattlePokemon(
                service.createPokemon("bulbasaur", 50, org.example.pokemon.domain.Nature.HARDY));

        for (int i = 0; i < 20 && seed.getLevel() < 16; i++) {
            growth.settle(List.of(seed), List.of(dummy));
        }

        assertEquals(16, seed.getLevel(), "前提：结算后应恰为 16 级");
        assertTrue(seed.knowsMove("poison-powder"), "16 级应学会毒粉（等级表条目）");
        assertTrue(seed.knowsMove("giga-drain"),
                "16 级应学会终极吸取（同等级追加条目，不得因同时进化而被新种族表覆盖丢失）");
    }

    /** 战斗侧技能应能在新系统数据中回查，且关键字段与源数据一致。 */
    private void assertMovesAreValid(Pokemon battlePokemon) {
        assertTrue(battlePokemon.getMoves().size() <= Pokemon.MAX_MOVES,
                "战斗侧技能数不应超过 " + Pokemon.MAX_MOVES);
        for (Move move : battlePokemon.getMoves()) {
            org.example.pokemon.domain.Move source = GameData.instance().getMove(move.getId())
                    .orElseThrow(() -> new AssertionError("战斗侧技能在新系统数据中不存在: " + move.getId()));
            assertEquals(source.getName(), move.getName());
            assertEquals(source.getPower(), move.getPower());
            assertEquals(source.getAccuracy(), move.getAccuracy());
            assertEquals(source.getMaxPp(), move.getMaxPp());
            assertEquals(source.getType().name(), move.getType().name());
            assertEquals(source.getCategory().name(), move.getCategory().name());
            assertEquals(source.getEffect().name(), move.getEffect().name(),
                    "技能 " + move.getId() + " 的专属效果在生成个体时丢失");
            assertEquals(source.getStatChanges().size(), move.getStatChanges().size(),
                    "技能 " + move.getId() + " 的能力等级变化在生成个体时丢失");
        }
    }
}
