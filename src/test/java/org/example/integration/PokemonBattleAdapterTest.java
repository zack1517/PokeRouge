package org.example.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.example.growth.GrowthProgress;
import org.example.model.ElementType;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.Player;
import org.example.model.Pokemon;
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

    /** 野生精灵应来自初始池、等级在目标值 ±2 内、技能转换有效。 */
    @Test
    void testCreateWildPokemon_levelWithinOffsetAndMovesValid() {
        Optional<Pokemon> wild = PokemonBattleAdapter.createWildPokemon(5);

        assertTrue(wild.isPresent());
        Pokemon wildPokemon = wild.get();
        assertTrue(wildPokemon.getLevel() >= 3 && wildPokemon.getLevel() <= 7,
                "野生等级应在 5±2 范围内，实际为 " + wildPokemon.getLevel());
        Set<String> poolIds = service.getInitialPool().stream()
                .map(Species::getId)
                .collect(Collectors.toSet());
        assertTrue(poolIds.contains(wildPokemon.getSpecies().getId()),
                "野生精灵应来自初始池，实际为 " + wildPokemon.getSpecies().getId());
        assertMovesAreValid(wildPokemon);
    }

    /** 新体系全部属性必须能在旧战斗模型中解析（防枚举漂移）。 */
    @Test
    void testElementType_everyNewTypeIsMappableToLegacy() {
        for (org.example.pokemon.domain.ElementType type : org.example.pokemon.domain.ElementType.values()) {
            assertNotNull(ElementType.parse(type.name()),
                    "新体系属性 " + type.name() + " 在旧战斗模型中缺少对应枚举");
        }
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

    /** 战斗侧技能应能在新系统数据中回查，且关键字段与源数据一致。 */
    private void assertMovesAreValid(Pokemon battlePokemon) {        assertTrue(battlePokemon.getMoves().size() <= Pokemon.MAX_MOVES,
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
        }
    }
}
