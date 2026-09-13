package org.example.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.growth.GrowthProgress;
import org.example.model.ElementType;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.MoveEffect;
import org.example.model.MoveSlot;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Stat;
import org.example.model.Trainer;
import org.example.pokemon.domain.LearnableMove;
import org.example.pokemon.domain.Species;
import org.example.pokemon.infrastructure.GameData;
import org.example.pokemon.service.PokemonService;
import org.example.pokemon.service.PokemonServiceImpl;
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
            Optional<Pokemon> wild = PokemonBattleAdapter.createWildPokemonExact(12, new GrowthProgress());
            assertTrue(wild.isPresent());
            assertEquals(12, wild.get().getLevel(), "精确等级创建不应有 ±2 浮动");
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

    /** 野生精灵应能在新系统图鉴中回查、等级在目标值 ±2 内且满足进化链合法性（前一进化型进化等级≤实际等级）、技能转换有效。 */
    @Test
    void testCreateWildPokemon_levelWithinOffsetAndMovesValid() {
        Optional<Pokemon> wild = PokemonBattleAdapter.createWildPokemon(5);

        assertTrue(wild.isPresent());
        Pokemon wildPokemon = wild.get();
        assertTrue(wildPokemon.getLevel() >= 3 && wildPokemon.getLevel() <= 7,
                "野生等级应在 5±2 范围内，实际为 " + wildPokemon.getLevel());
        // 野生精灵从新系统全图鉴抽取，且不得是「前一进化型进化等级高于实际等级」的非法形态
        Species source = GameData.instance()
                .getSpecies(wildPokemon.getSpecies().getId())
                .orElseThrow(() -> new AssertionError(
                        "野生精灵种族不在新系统图鉴中: " + wildPokemon.getSpecies().getId()));
        assertEvolvedFormLegal(wildPokemon);
        assertMovesAreValid(wildPokemon);
    }

    /** 低等级遭遇多次也不应出现「前一进化型进化等级高于实际等级」的非法形态（如 10 级的耿鬼）。 */
    @Test
    void testCreateWildPokemon_lowLevelNeverYieldsIllegalEvolvedForms() {
        for (int i = 0; i < 50; i++) {
            Optional<Pokemon> wild = PokemonBattleAdapter.createWildPokemon(10);
            assertTrue(wild.isPresent());
            assertEvolvedFormLegal(wild.get());
        }
    }

    /** 精确等级创建同样受进化链合法性约束（道馆主固定等级配置依赖此口径）。 */
    @Test
    void testCreateWildPokemonExact_respectsEvolutionLegality() {
        for (int level : new int[]{12, 18, 25, 34}) {
            for (int i = 0; i < 20; i++) {
                Optional<Pokemon> wild = PokemonBattleAdapter.createWildPokemonExact(level, new GrowthProgress());
                assertTrue(wild.isPresent());
                assertEquals(level, wild.get().getLevel(), "精确等级创建不应有浮动");
                assertEvolvedFormLegal(wild.get());
            }
        }
    }

    /** 断言该野生精灵不是非法进化形态：其前一进化型的进化等级必须不高于其实际等级。 */
    private static void assertEvolvedFormLegal(Pokemon wild) {
        for (Species pre : GameData.instance().getAllSpecies()) {
            if (wild.getSpecies().getId().equals(pre.getEvolutionTarget())) {
                assertTrue(pre.getEvolutionLevel() <= wild.getLevel(),
                        "非法遭遇：" + wild.getSpecies().getId() + "（" + wild.getName() + "）出现在等级 "
                                + wild.getLevel() + "，但其前一进化型 " + pre.getId() + " 在 "
                                + pre.getEvolutionLevel() + " 级才进化");
            }
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
