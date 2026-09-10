package org.example.growth;

import org.example.battle.BattleDataPort;
import org.example.battle.BattleDataPorts;
import org.example.battle.BattleGrowthPort;
import org.example.battle.BattleService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 成长模块测试：经验增加、升级、学招与进化全部由成长模块判定，战斗模块只申报结算。
 *
 * <p>经验折算规则为「被击败精灵六维种族值总和 × 等级 / 5」，单只不低于 30；
 * 技能与种族一律经 {@link BattleDataPort} 查询，查不到时学招与进化降级为无操作。</p>
 */
class GrowthServiceTest {

    /** 高威力普通系物理技能，仅用于构造精灵的初始技能。 */
    private static final Move SLAM = new Move("m_slam", "猛击", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 500, 100, 40);

    /** 端口提供的「到级习得」技能。 */
    private static final Move NEW_MOVE = new Move("m_new", "新招", ElementType.FIRE,
            MoveCategory.SPECIAL, 40, 100, 25);

    /** 用于占满 4 个技能槽的备用技能。 */
    private static final Move[] FILLERS = {
            new Move("m_f1", "填充一", ElementType.NORMAL, MoveCategory.PHYSICAL, 30, 100, 20),
            new Move("m_f2", "填充二", ElementType.NORMAL, MoveCategory.PHYSICAL, 30, 100, 20),
            new Move("m_f3", "填充三", ElementType.NORMAL, MoveCategory.PHYSICAL, 30, 100, 20),
    };

    /** 记录查询轨迹的端口桩：用于断言成长模块确实经由端口取数据。 */
    private static final class RecordingPort implements BattleDataPort {

        final Map<String, Move> moves = new HashMap<>();
        final Map<String, Species> species = new HashMap<>();
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
            return List.of();
        }
    }

    // ------------------------------------------------------------------
    // 测试数据构造
    // ------------------------------------------------------------------

    /** 六维种族值均为 base 的种族：种族值总和为 6 × base，便于断言经验数值。 */
    private static Species species(String id, int base, List<String> moveIds,
                                   String evolvesTo, int evolveLevel, Map<Integer, String> learnAt) {
        return new Species(id, id, ElementType.NORMAL, null,
                new Stats(base, base, base, base, base, base), 100,
                moveIds, evolvesTo, evolveLevel, learnAt);
    }

    private static Pokemon pokemon(Species s, int level, Move... moves) {
        return Pokemon.create(s, level, List.of(moves));
    }

    private static Move[] fourMoves() {
        return new Move[]{SLAM, FILLERS[0], FILLERS[1], FILLERS[2]};
    }

    private static boolean knows(Pokemon p, String moveId) {
        return p.hasMove(moveId);
    }

    /** 被击败对手：种族值总和 300、等级 2，折算经验 300 × 2 / 5 = 120。 */
    private static Pokemon foe120() {
        return pokemon(species("foe_a", 50, List.of("m_slam"), null, 0, Map.of()), 2, SLAM);
    }

    // ------------------------------------------------------------------
    // 经验折算
    // ------------------------------------------------------------------

    @Test
    void 经验按种族值总和乘等级除五折算() {
        Species mine = species("mine_sp", 50, List.of("m_slam"), null, 0, Map.of());
        Pokemon active = pokemon(mine, 1, SLAM);

        BattleGrowthPort.Settlement settlement =
                new GrowthService(new RecordingPort()).settle(List.of(active), List.of(foe120()));

        assertEquals(4, active.getLevel(), "120 点经验应把 1 级精灵升到 4 级");
        assertTrue(settlement.log().contains(active.getName() + " 升到了 Lv.4！"),
                "逐级升级日志应逐条返回");
        assertTrue(settlement.pendingLearns().isEmpty());
    }

    @Test
    void 经验折算有三十点下限() {
        Species mine = species("mine_sp", 50, List.of("m_slam"), null, 0, Map.of());
        Species tiny = species("tiny_sp", 1, List.of("m_slam"), null, 0, Map.of());
        Pokemon active = pokemon(mine, 1, SLAM);

        // 6 × 1 / 5 = 1，低于下限，按 30 点结算：升到 3 级（26 点），不足以升到 4 级（63 点）
        new GrowthService(new RecordingPort()).settle(List.of(active), List.of(pokemon(tiny, 1, SLAM)));

        assertEquals(3, active.getLevel(), "经验折算不应低于 30 点");
    }

    @Test
    void 训练师轮战按整队被击败对手求和结算() {
        Species mine = species("mine_sp", 50, List.of("m_slam"), null, 0, Map.of());
        Species half = species("foe_half", 25, List.of("m_slam"), null, 0, Map.of());
        Pokemon active = pokemon(mine, 1, SLAM);

        // 单只 25 × 6 × 2 / 5 = 60 点（只升到 3 级）；两只求和 120 点升到 4 级
        new GrowthService(new RecordingPort()).settle(List.of(active),
                List.of(pokemon(half, 2, SLAM), pokemon(half, 2, SLAM)));

        assertEquals(4, active.getLevel(), "训练师战应把整队被击败对手的经验一次性结算");
    }

    @Test
    void 已倒下的精灵不结算成长() {
        Species mine = species("mine_sp", 50, List.of("m_slam"), null, 0, Map.of());
        Pokemon fainted = pokemon(mine, 1, SLAM);
        fainted.takeDamage(fainted.getMaxHp());

        BattleGrowthPort.Settlement settlement =
                new GrowthService(new RecordingPort()).settle(List.of(fainted), List.of(foe120()));

        assertTrue(fainted.isFainted());
        assertEquals(1, fainted.getLevel(), "倒下的精灵不结算经验与成长");
        assertTrue(settlement.log().isEmpty());
    }

    @Test
    void 无被击败对手时不结算经验() {
        Species mine = species("mine_sp", 50, List.of("m_slam"), null, 0, Map.of());
        Pokemon active = pokemon(mine, 1, SLAM);

        BattleGrowthPort.Settlement settlement =
                new GrowthService(new RecordingPort()).settle(List.of(active), List.of());

        assertEquals(1, active.getLevel());
        assertTrue(settlement.log().isEmpty());
    }

    // ------------------------------------------------------------------
    // 到级学招
    // ------------------------------------------------------------------

    @Test
    void 到级学招在空槽时直接学会() {
        Species mine = species("mine_sp", 50, List.of("m_slam"), null, 0, Map.of(2, "m_new"));
        Pokemon active = pokemon(mine, 1, SLAM);

        RecordingPort port = new RecordingPort();
        port.moves.put("m_new", NEW_MOVE);

        BattleGrowthPort.Settlement settlement =
                new GrowthService(port).settle(List.of(active), List.of(foe120()));

        assertTrue(port.calls.contains("move:m_new"), "应经数据端口查询到级技能");
        assertTrue(knows(active, "m_new"), "有空槽时直接学会");
        assertTrue(settlement.pendingLearns().isEmpty(), "直接学会不需要玩家抉择");
        assertTrue(settlement.log().stream().anyMatch(line -> line.contains("记住了")));
    }

    @Test
    void 技能栏已满时挂起等待抉择() {
        Species mine = species("mine_sp", 50, List.of("m_slam"), null, 0, Map.of(2, "m_new"));
        Pokemon active = pokemon(mine, 1, fourMoves());

        RecordingPort port = new RecordingPort();
        port.moves.put("m_new", NEW_MOVE);

        BattleGrowthPort.Settlement settlement =
                new GrowthService(port).settle(List.of(active), List.of(foe120()));

        assertEquals(1, settlement.pendingLearns().size(), "满 4 招时应挂起一个学习抉择");
        BattleService.LearnChoice choice = settlement.pendingLearns().get(0);
        assertEquals(active, choice.pokemon());
        assertEquals("m_new", choice.move().getId());
        assertEquals(4, active.getMoveSlots().size(), "挂起期间技能栏不变");
        assertFalse(knows(active, "m_new"));
    }

    @Test
    void 数据端口查不到技能时学招降级为无操作() {
        Species mine = species("mine_sp", 50, List.of("m_slam"), null, 0, Map.of(2, "m_new"));
        Pokemon active = pokemon(mine, 1, SLAM);

        BattleGrowthPort.Settlement settlement =
                new GrowthService(new RecordingPort()).settle(List.of(active), List.of(foe120()));

        assertEquals(4, active.getLevel(), "学招降级不影响经验与升级");
        assertFalse(knows(active, "m_new"), "查不到技能时不学会任何技能");
        assertTrue(settlement.pendingLearns().isEmpty());
    }

    // ------------------------------------------------------------------
    // 进化
    // ------------------------------------------------------------------

    @Test
    void 达到进化等级时经数据端口取种族进化() {
        Species mine = species("mine_sp", 50, List.of("m_slam"), "mine_evo", 2, Map.of());
        Species evolved = species("mine_evo", 60, List.of("m_slam"), null, 0, Map.of());
        Pokemon active = pokemon(mine, 1, SLAM);

        RecordingPort port = new RecordingPort();
        port.species.put("mine_evo", evolved);

        BattleGrowthPort.Settlement settlement =
                new GrowthService(port).settle(List.of(active), List.of(foe120()));

        assertTrue(port.calls.contains("species:mine_evo"), "应经数据端口查询进化目标种族");
        assertEquals("mine_evo", active.getSpecies().getId());
        assertTrue(settlement.log().stream().anyMatch(line -> line.contains("进化成了")));
    }

    @Test
    void 数据端口查不到种族时进化降级为无操作() {
        Species mine = species("mine_sp", 50, List.of("m_slam"), "mine_evo", 2, Map.of());
        Pokemon active = pokemon(mine, 1, SLAM);

        new GrowthService(new RecordingPort()).settle(List.of(active), List.of(foe120()));

        assertEquals(4, active.getLevel(), "进化降级不影响经验与升级");
        assertEquals("mine_sp", active.getSpecies().getId(), "查不到种族时不进化");
    }

    // ------------------------------------------------------------------
    // 学习抉择执行
    // ------------------------------------------------------------------

    @Test
    void 抉择遗忘并学会时替换指定槽位() {
        Species mine = species("mine_sp", 50, List.of("m_slam"), null, 0, Map.of());
        Pokemon active = pokemon(mine, 5, fourMoves());
        GrowthService growth = new GrowthService(new RecordingPort());

        List<String> log = growth.resolveLearn(new BattleService.LearnChoice(active, NEW_MOVE), 0);

        assertEquals("m_new", active.getMoveSlots().get(0).getMove().getId());
        assertEquals(1, log.size());
        assertTrue(log.get(0).contains("忘记了") && log.get(0).contains("学会了"));
    }

    @Test
    void 抉择放弃学习时保留原技能栏() {
        Species mine = species("mine_sp", 50, List.of("m_slam"), null, 0, Map.of());
        Pokemon active = pokemon(mine, 5, fourMoves());
        GrowthService growth = new GrowthService(new RecordingPort());

        List<String> log = growth.resolveLearn(new BattleService.LearnChoice(active, NEW_MOVE), -1);

        assertFalse(knows(active, "m_new"), "放弃学习不改变技能栏");
        assertEquals(4, active.getMoveSlots().size());
        assertEquals(1, log.size());
        assertTrue(log.get(0).contains("没有学习"));
    }

    @Test
    void 空抉择返回空日志() {
        GrowthService growth = new GrowthService(BattleDataPorts.none());

        assertNotNull(growth.resolveLearn(null, 0));
        assertTrue(growth.resolveLearn(null, 0).isEmpty());
    }
}
