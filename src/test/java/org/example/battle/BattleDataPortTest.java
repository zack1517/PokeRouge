package org.example.battle;

import org.example.model.ElementType;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stats;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 战斗数据端口测试：验证战斗模块<b>只消费</b>外部数据。
 *
 * <p>引擎运行期需要的技能 / 种族 / 野生池全部经由 {@link BattleDataPort} 查询，
 * 未注入端口时相关功能降级为无操作，不依赖任何内建数据。</p>
 */
class BattleDataPortTest {

    /** 高威力普通系物理技能：对本测试中的对手稳定一击必杀。 */
    private static final Move SLAM = new Move("m_slam", "猛击", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 500, 100, 40);

    /** 端口提供的「到级习得」技能。 */
    private static final Move NEW_MOVE = new Move("m_new", "新招", ElementType.FIRE,
            MoveCategory.SPECIAL, 40, 100, 25);

    /** 记录查询轨迹的端口桩：用于断言引擎确实经由端口取数据。 */
    private static final class RecordingPort implements BattleDataPort {

        final Map<String, Move> moves = new HashMap<>();
        final Map<String, Species> species = new HashMap<>();
        final List<String> pool = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        /** true 时使用自定义个体生成，false 时走接口默认实现。 */
        boolean customCreate;

        @Override
        public Move findMove(String moveId) {
            calls.add("move:" + moveId);
            return moves.get(moveId);
        }

        @Override
        public Species findSpecies(String speciesId) {
            calls.add("species:" + speciesId);
            return species.get(speciesId);
        }

        @Override
        public List<String> wildSpeciesPool() {
            calls.add("pool");
            return pool;
        }

        @Override
        public Optional<Pokemon> createPokemon(String speciesId, int level) {
            calls.add("create:" + speciesId + "@" + level);
            if (!customCreate) {
                return BattleDataPort.super.createPokemon(speciesId, level);
            }
            Species s = species.get(speciesId);
            return s == null ? Optional.empty() : Optional.of(Pokemon.create(s, level, List.of(SLAM)));
        }
    }

    // ------------------------------------------------------------------
    // 测试数据构造
    // ------------------------------------------------------------------

    private static Species species(String id, int hp, int atk, int def, int spe,
                                   List<String> moveIds, String evolvesTo, int evolveLevel,
                                   Map<Integer, String> learnAt) {
        return new Species(id, id, ElementType.NORMAL, null,
                new Stats(hp, atk, def, atk, def, spe), 100, moveIds, evolvesTo, evolveLevel, learnAt);
    }

    private static Pokemon pokemon(Species s, int level, Move... moves) {
        return Pokemon.create(s, level, List.of(moves));
    }

    private static Player playerWith(Pokemon... party) {
        Player player = new Player("玩家");
        for (Pokemon p : party) {
            player.addPokemon(p);
        }
        return player;
    }

    private static boolean knows(Pokemon p, String moveId) {
        return p.getMoveSlots().stream().anyMatch(slot -> slot.getMove().getId().equals(moveId));
    }

    // ------------------------------------------------------------------
    // 空端口
    // ------------------------------------------------------------------

    @Test
    void 空端口不提供任何数据() {
        BattleDataPort none = BattleDataPorts.none();
        assertNull(none.findMove("m_slam"));
        assertNull(none.findSpecies("sp"));
        assertTrue(none.wildSpeciesPool().isEmpty());
        assertFalse(none.createPokemon("sp", 5).isPresent());
    }

    // ------------------------------------------------------------------
    // 升级学招：技能来自端口
    // ------------------------------------------------------------------

    @Test
    void 升级学招经由注入端口查询技能() {
        Species mine = species("mine_sp", 200, 200, 40, 200,
                List.of("m_slam"), null, 0, Map.of(2, "m_new"));
        Species foe = species("foe_sp", 40, 10, 40, 10, List.of("m_slam"), null, 0, Map.of());
        Player player = playerWith(pokemon(mine, 1, SLAM));

        RecordingPort port = new RecordingPort();
        port.moves.put("m_new", NEW_MOVE);

        BattleService battle = BattleServices.newBattle(player, pokemon(foe, 5, SLAM), new Random(7), port);
        battle.useMove(player.getActive().getMoveSlots().get(0));

        assertEquals(BattleService.Status.PLAYER_WIN, battle.getStatus());
        assertTrue(port.calls.contains("move:m_new"), "引擎应通过端口查询到级技能");
        assertTrue(knows(player.getActive(), "m_new"), "应学会端口返回的技能");
    }

    @Test
    void 未注入端口时升级学招降级为无操作() {
        Species mine = species("mine_sp", 200, 200, 40, 200,
                List.of("m_slam"), null, 0, Map.of(2, "m_new"));
        Species foe = species("foe_sp", 40, 10, 40, 10, List.of("m_slam"), null, 0, Map.of());
        Player player = playerWith(pokemon(mine, 1, SLAM));

        BattleService battle = BattleServices.newBattle(player, pokemon(foe, 5, SLAM), new Random(7));
        battle.useMove(player.getActive().getMoveSlots().get(0));

        assertEquals(BattleService.Status.PLAYER_WIN, battle.getStatus());
        assertFalse(knows(player.getActive(), "m_new"), "无端口时不学会任何技能");
        assertEquals(1, player.getActive().getMoveSlots().size(), "只保留原有技能");
    }

    // ------------------------------------------------------------------
    // 进化：种族来自端口
    // ------------------------------------------------------------------

    @Test
    void 升级进化经由注入端口查询种族() {
        Species mine = species("mine_sp", 200, 200, 40, 200,
                List.of("m_slam"), "mine_evo", 2, Map.of());
        Species evolved = species("mine_evo", 220, 220, 50, 220,
                List.of("m_slam"), null, 0, Map.of());
        Species foe = species("foe_sp", 40, 10, 40, 10, List.of("m_slam"), null, 0, Map.of());
        Player player = playerWith(pokemon(mine, 1, SLAM));

        RecordingPort port = new RecordingPort();
        port.species.put("mine_evo", evolved);

        BattleService battle = BattleServices.newBattle(player, pokemon(foe, 5, SLAM), new Random(7), port);
        battle.useMove(player.getActive().getMoveSlots().get(0));

        assertTrue(port.calls.contains("species:mine_evo"), "引擎应通过端口查询进化目标种族");
        assertEquals("mine_evo", player.getActive().getSpecies().getId(), "应进化为端口返回的种族");
    }

    @Test
    void 未注入端口时进化降级为无操作() {
        Species mine = species("mine_sp", 200, 200, 40, 200,
                List.of("m_slam"), "mine_evo", 2, Map.of());
        Species foe = species("foe_sp", 40, 10, 40, 10, List.of("m_slam"), null, 0, Map.of());
        Player player = playerWith(pokemon(mine, 1, SLAM));

        BattleService battle = BattleServices.newBattle(player, pokemon(foe, 5, SLAM), new Random(7));
        battle.useMove(player.getActive().getMoveSlots().get(0));

        assertEquals("mine_sp", player.getActive().getSpecies().getId(), "无端口时不进化");
    }

    // ------------------------------------------------------------------
    // 端口默认个体生成
    // ------------------------------------------------------------------

    @Test
    void 端口默认个体生成依据种族首批技能() {
        RecordingPort port = new RecordingPort();
        List<String> moveIds = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String id = "m_" + i;
            moveIds.add(id);
            port.moves.put(id, new Move(id, "技能" + i, ElementType.NORMAL,
                    MoveCategory.PHYSICAL, 40, 100, 20));
        }
        port.species.put("w_a", species("w_a", 40, 10, 40, 10, moveIds, null, 0, Map.of()));

        Optional<Pokemon> wild = port.createPokemon("w_a", 5);

        assertTrue(wild.isPresent());
        assertEquals(Pokemon.MAX_MOVES, wild.get().getMoveSlots().size(),
                "默认实现最多取种族首批 " + Pokemon.MAX_MOVES + " 个可查到的技能");
        assertTrue(port.createPokemon("unknown_sp", 5).isEmpty(), "种族查不到时返回空");
    }
}
