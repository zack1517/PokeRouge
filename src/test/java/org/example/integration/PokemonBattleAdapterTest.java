package org.example.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.example.model.ElementType;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.MoveSlot;
import org.example.model.Player;
import org.example.model.Pokemon;
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

    /** 野生精灵应能在新系统图鉴中回查、等级在目标值 ±2 内且满足 BST 等级门槛（5 级遭遇仅出 BST ≤ 350 的基础形态）、技能转换有效。 */
    @Test
    void testCreateWildPokemon_levelWithinOffsetAndMovesValid() {
        Optional<Pokemon> wild = PokemonBattleAdapter.createWildPokemon(5);

        assertTrue(wild.isPresent());
        Pokemon wildPokemon = wild.get();
        assertTrue(wildPokemon.getLevel() >= 3 && wildPokemon.getLevel() <= 7,
                "野生等级应在 5±2 范围内，实际为 " + wildPokemon.getLevel());
        // 野生精灵从新系统全图鉴按 BST 等级门槛抽取（低 BST 早出现），不再限定初始池
        Species source = GameData.instance()
                .getSpecies(wildPokemon.getSpecies().getId())
                .orElseThrow(() -> new AssertionError(
                        "野生精灵种族不在新系统图鉴中: " + wildPokemon.getSpecies().getId()));
        int bst = source.getBaseStats().getTotal();
        int minLevel = bst <= 350 ? 1 : (bst <= 500 ? 12 : 20);
        assertTrue(wildPokemon.getLevel() >= minLevel,
                "野生精灵 " + source.getId() + "（BST " + bst + "）不应在等级 " + wildPokemon.getLevel()
                        + " 出现，其最低出现等级为 " + minLevel);
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
