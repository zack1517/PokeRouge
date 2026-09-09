package org.example.integration;

import org.example.battle.BattleDataPort;
import org.example.battle.BattleDataPorts;
import org.example.model.ElementType;
import org.example.model.Move;
import org.example.model.MoveCategory;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 野生遭遇生成测试（组装/流程侧）。
 *
 * <p>遭遇生成本不属于战斗模块，本测试同时验证两点：随机源可注入（结果可复现），
 * 以及野生池与个体生成仍然只经由外部 {@link BattleDataPort} 查询。</p>
 */
class WildEncounterTest {

    /** 端口提供的技能。 */
    private static final Move SLAM = new Move("m_slam", "猛击", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 500, 100, 40);

    /** 返回固定值的随机源桩：让等级浮动与挑池结果可复现。 */
    private static final class FixedRandom extends Random {

        private final int value;

        FixedRandom(int value) {
            this.value = value;
        }

        @Override
        public int nextInt(int bound) {
            return Math.floorMod(value, bound);
        }
    }

    /** 记录查询轨迹的端口桩，个体生成走接口默认实现。 */
    private static final class RecordingPort implements BattleDataPort {

        final Map<String, Move> moves = new HashMap<>();
        final Map<String, Species> species = new HashMap<>();
        final List<String> pool = new ArrayList<>();
        final List<String> calls = new ArrayList<>();

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
            return BattleDataPort.super.createPokemon(speciesId, level);
        }
    }

    private static Species species(String id, List<String> moveIds) {
        return new Species(id, id, ElementType.NORMAL, null,
                new Stats(60, 60, 60, 60, 60, 60), 100, moveIds, null, 0, Map.of());
    }

    // ------------------------------------------------------------------
    // 等级浮动
    // ------------------------------------------------------------------

    @Test
    void 等级浮动落在玩家等级正负二范围内() {
        Random random = new Random(20240910);
        for (int i = 0; i < 200; i++) {
            int playerLevel = 3 + i % 40;
            int level = WildEncounter.levelAround(playerLevel, random);
            assertTrue(level >= playerLevel - WildEncounter.LEVEL_SPREAD
                            && level <= playerLevel + WildEncounter.LEVEL_SPREAD,
                    "等级应在 [等级-2, 等级+2] 内，实际 " + level);
        }
    }

    @Test
    void 等级浮动不低于下限() {
        assertEquals(WildEncounter.MIN_LEVEL, WildEncounter.levelAround(1, new FixedRandom(0)));
        assertEquals(WildEncounter.MIN_LEVEL, WildEncounter.levelAround(WildEncounter.MIN_LEVEL,
                new FixedRandom(0)));
    }

    @Test
    void 等级浮动可用注入随机源复现() {
        assertEquals(10, WildEncounter.levelAround(10, new FixedRandom(2)));
        assertEquals(8, WildEncounter.levelAround(10, new FixedRandom(0)));
        assertEquals(12, WildEncounter.levelAround(10, new FixedRandom(4)));
    }

    // ------------------------------------------------------------------
    // 随机遭遇：数据来自端口
    // ------------------------------------------------------------------

    @Test
    void 随机遭遇经由注入端口取野生池() {
        RecordingPort port = new RecordingPort();
        port.pool.add("w_a");
        port.species.put("w_a", species("w_a", List.of("m_slam")));
        port.moves.put("m_slam", SLAM);

        Optional<Pokemon> wild = WildEncounter.randomWild(5, port, new FixedRandom(0));

        assertTrue(wild.isPresent(), "应生成野生精灵");
        assertEquals("w_a", wild.get().getSpecies().getId());
        assertEquals(5, wild.get().getLevel());
        assertTrue(port.calls.contains("pool"), "野生池应经端口查询");
        assertTrue(port.calls.contains("create:w_a@5"), "个体应经端口生成");
    }

    @Test
    void 端口无野生池时返回空() {
        assertTrue(WildEncounter.randomWild(5, BattleDataPorts.none()).isEmpty());
        assertTrue(WildEncounter.randomWild(5, new RecordingPort()).isEmpty(), "空池应返回空");
    }
}
