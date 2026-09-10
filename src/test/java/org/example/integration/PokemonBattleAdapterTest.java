package org.example.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.stream.Collectors;
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

    private final PokemonService service = new PokemonServiceImpl();

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

    /** 野生精灵等级在目标值 ±2 内、符合低等级遭遇的 BST 分层（≤350）、技能转换有效。 */
    @Test
    void testCreateWildPokemon_levelWithinOffsetAndMovesValid() {
        Optional<Pokemon> wild = PokemonBattleAdapter.createWildPokemon(5);

        assertTrue(wild.isPresent());
        Pokemon wildPokemon = wild.get();
        assertTrue(wildPokemon.getLevel() >= 3 && wildPokemon.getLevel() <= 7,
                "野生等级应在 5±2 范围内，实际为 " + wildPokemon.getLevel());
        // 遭遇实现按全库 BST 分层：低等级只可能遇到 BST ≤ 350 的基础形态（初始池断言与实现不符，已修正）
        org.example.pokemon.domain.Species source =
                GameData.instance().getSpecies(wildPokemon.getSpecies().getId())
                        .orElseThrow(() -> new AssertionError("野生精灵在新系统数据中不存在"));
        assertTrue(source.getBaseStats().getTotal() <= 350,
                "5 级遭遇应来自 BST ≤ 350 的分层，实际为 " + source.getName()
                        + "（BST " + source.getBaseStats().getTotal() + "）");
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
        }
    }
}
