package org.example.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.example.growth.GrowthProgress;
import org.example.integration.PokemonBattleAdapter;
import org.example.model.MoveSlot;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stats;
import org.example.model.StatusCondition;
import org.example.pokemon.service.PokemonService;
import org.example.pokemon.service.PokemonServiceImpl;
import org.junit.jupiter.api.Test;

/**
 * 精灵映射测试：确认存档能完整还原一只精灵的<b>全部个体状态</b>。
 *
 * <p>存档系统最容易在「精灵」这一层出错 —— 只存种族与等级会丢掉残血、异常状态与技能剩余 PP，
 * 玩家读档后会莫名满血复活。因此这里逐项验证这些「属于这只精灵自己的东西」都能原样往返。</p>
 *
 * <p>使用隔离的成长进度，避免读到开发者本机的真实成长存档。</p>
 */
class PokemonMapperTest {

    private final PokemonService service = new PokemonServiceImpl(new GrowthProgress());

    /** {@link Stats} 未实现 equals，这里逐项比对。 */
    private static void assertIvs(Stats expected, Stats actual) {
        assertEquals(expected.getHp(), actual.getHp(), "个体值 HP");
        assertEquals(expected.getAttack(), actual.getAttack(), "个体值 物攻");
        assertEquals(expected.getDefense(), actual.getDefense(), "个体值 物防");
        assertEquals(expected.getSpAttack(), actual.getSpAttack(), "个体值 特攻");
        assertEquals(expected.getSpDefense(), actual.getSpDefense(), "个体值 特防");
        assertEquals(expected.getSpeed(), actual.getSpeed(), "个体值 速度");
    }

    private Pokemon starter() {
        org.example.pokemon.domain.Species species = service.getInitialPool().get(0);
        org.example.pokemon.domain.Pokemon starter = service.createPokemon(species.getId(), 20);
        Player player = PokemonBattleAdapter.createBattlePlayer("测试玩家", starter);
        return player.getActive();
    }

    /** 完整的精灵状态写出再读回必须一致（个体值、当前 HP、经验、异常、技能 PP）。 */
    @Test
    void 精灵状态完整往返() {
        Pokemon base = starter();
        Stats ivs = new Stats(31, 20, 15, 10, 5, 0);
        List<MoveSlot> slots = base.getMoveSlots().stream()
                .map(slot -> new MoveSlot(slot.getMove(), 1))
                .toList();
        // 先探一次上限，再按上限的 1/3 构造残血个体，避免测试里重算属性公式
        int maxHp = Pokemon.restore(base.getSpecies(), 20, ivs, slots, 0L,
                StatusCondition.NONE, 0, 0, 0, 0).getMaxHp();
        int wounded = maxHp / 3;
        Pokemon original = Pokemon.restore(base.getSpecies(), 20, ivs, slots, 340L,
                StatusCondition.POISON, 2, 3, 1, wounded);

        SaveData.PokemonData data = PokemonMapper.toData(original);
        Pokemon restored = PokemonMapper.toPokemon(data).orElseThrow();

        assertEquals(original.getSpecies().getId(), restored.getSpecies().getId());
        assertEquals(original.getLevel(), restored.getLevel());
        assertEquals(original.getExp(), restored.getExp());
        assertEquals(wounded, restored.getCurrentHp(), "残血状态必须原样还原");
        assertEquals(StatusCondition.POISON, restored.getStatus());
        assertEquals(2, restored.getSleepTurns());
        assertEquals(3, restored.getBadlyPoisonCounter());
        assertEquals(1, restored.getConfusionTurns());
        assertIvs(original.getIvs(), restored.getIvs());
        assertEquals(original.getMoveSlots().size(), restored.getMoveSlots().size());
        if (!original.getMoveSlots().isEmpty()) {
            assertEquals(original.getMoveSlots().get(0).getMove().getId(),
                    restored.getMoveSlots().get(0).getMove().getId());
            assertEquals(1, restored.getMoveSlots().get(0).getPp(), "技能剩余 PP 必须原样还原");
        }
    }

    /** 技能库必须随存档往返：写出方向完整保留；读回方向按数据表还原（未知技能丢弃）。 */
    @Test
    void 技能库随存档往返() {
        Pokemon original = starter();
        org.example.model.Move poolMove = new org.example.model.Move(
                "m_pool_test", "库中招", org.example.model.ElementType.NORMAL,
                org.example.model.MoveCategory.PHYSICAL, 50, 100, 20);
        original.learnMoveToPool(poolMove);

        SaveData.PokemonData data = PokemonMapper.toData(original);
        assertTrue(data.knownMoves().contains("m_pool_test"), "写出方向：技能库包含未出战技能");

        // 读回方向：出战技能在数据表中可还原，未出战的未知技能被丢弃、已知技能保留
        String battleMoveId = original.getMoves().get(0).getId();
        SaveData.PokemonData mixed = new SaveData.PokemonData(
                original.getSpecies().getId(), original.getLevel(),
                new SaveData.IvData(5, 5, 5, 5, 5, 5), 0L, "NONE", 0, 0, 0,
                original.getMaxHp(),
                List.of(new SaveData.MoveData(battleMoveId, 12)),
                List.of(battleMoveId, "m_unknown_pool"));

        Pokemon restored = PokemonMapper.toPokemon(mixed).orElseThrow();

        assertTrue(restored.knowsMove(battleMoveId), "技能库中的已知技能读档后保留");
        assertFalse(restored.knowsMove("m_unknown_pool"), "未知技能读档时丢弃");
        assertEquals(1, restored.getKnownMoves().size(), "技能库仅保留可还原的技能");
    }

    /** 旧档没有技能库字段时，技能库按出战技能兜底（升级机制照常运行）。 */
    @Test
    void 旧档技能库按出战技能兜底() {
        Pokemon original = starter();
        SaveData.PokemonData legacy = new SaveData.PokemonData(
                original.getSpecies().getId(), original.getLevel(),
                new SaveData.IvData(5, 5, 5, 5, 5, 5), 0L, "NONE", 0, 0, 0,
                original.getMaxHp(),
                List.of(new SaveData.MoveData(
                        original.getMoves().get(0).getId(), 12)));

        Pokemon restored = PokemonMapper.toPokemon(legacy).orElseThrow();

        assertEquals(1, restored.getKnownMoves().size(), "旧档技能库 = 出战技能");
        assertTrue(restored.knowsMove(original.getMoves().get(0).getId()));
    }

    /** 个体值必须随存档往返 —— 成长加成提升的就是个体值，丢了等于成长机制失效。 */
    @Test
    void 个体值随存档往返() {
        Species species = starter().getSpecies();
        Stats ivs = new Stats(31, 30, 29, 28, 27, 26);
        Pokemon pokemon = Pokemon.restore(species, 25, ivs, List.of(), 0L,
                StatusCondition.NONE, 0, 0, 0, 10);

        Pokemon restored = PokemonMapper.toPokemon(PokemonMapper.toData(pokemon)).orElseThrow();

        assertIvs(ivs, restored.getIvs());
        assertEquals(31, restored.getIvs().getHp());
        assertEquals(30, restored.getIvs().getAttack());
        assertEquals(26, restored.getIvs().getSpeed());
        assertEquals(pokemon.getMaxHp(), restored.getMaxHp(), "属性由个体值现算，读档后应一致");
    }

    /** 越界的当前 HP 被裁剪到上限；负值被裁剪到 0（手改存档不应产生非法个体）。 */
    @Test
    void 越界的当前HP被裁剪() {
        Species species = starter().getSpecies();
        Stats ivs = new Stats(10, 10, 10, 10, 10, 10);
        Pokemon overfull = Pokemon.restore(species, 10, ivs, List.of(), 0L,
                StatusCondition.NONE, 0, 0, 0, 99999);
        Pokemon negative = Pokemon.restore(species, 10, ivs, List.of(), 0L,
                StatusCondition.NONE, 0, 0, 0, -5);

        assertEquals(overfull.getMaxHp(), overfull.getCurrentHp());
        assertEquals(0, negative.getCurrentHp());
        assertEquals(overfull.getMaxHp(), PokemonMapper.toData(overfull).currentHp());
    }

    /** 技能剩余 PP 越界时裁剪到 [0, 最大PP]。 */
    @Test
    void 技能PP受最大值约束() {
        org.example.model.Move move = starter().getMoves().isEmpty()
                ? null : starter().getMoves().get(0);
        org.junit.jupiter.api.Assumptions.assumeTrue(move != null, "初始精灵应至少有一个技能");

        MoveSlot over = new MoveSlot(move, 9999);
        MoveSlot negative = new MoveSlot(move, -3);

        assertEquals(move.getMaxPp(), over.getPp());
        assertEquals(0, negative.getPp());
    }

    /** 物种 id 已不存在时返回空 —— 不能凭空造一只精灵顶替。 */
    @Test
    void 未知物种返回空() {
        SaveData.PokemonData data = new SaveData.PokemonData("不存在的物种", 5,
                new SaveData.IvData(1, 1, 1, 1, 1, 1), 0L, "NONE", 0, 0, 0, 10, List.of());

        assertTrue(PokemonMapper.toPokemon(data).isEmpty());
        assertTrue(PokemonMapper.toPokemon(null).isEmpty());
    }

    /** 未知技能被丢弃，其余技能照常还原（技能表调整后老存档仍能读）。 */
    @Test
    void 未知技能被丢弃而其余保留() {
        Pokemon original = starter();
        org.junit.jupiter.api.Assumptions.assumeTrue(!original.getMoves().isEmpty(),
                "初始精灵应至少有一个技能");
        String knownMoveId = original.getMoves().get(0).getId();
        SaveData.PokemonData data = new SaveData.PokemonData(original.getSpecies().getId(),
                original.getLevel(), new SaveData.IvData(5, 5, 5, 5, 5, 5), 0L, "NONE", 0, 0, 0,
                original.getMaxHp(),
                List.of(new SaveData.MoveData("不存在的技能", 10),
                        new SaveData.MoveData(knownMoveId, 12)));

        Pokemon restored = PokemonMapper.toPokemon(data).orElseThrow();

        assertEquals(1, restored.getMoveSlots().size());
        assertEquals(knownMoveId, restored.getMoveSlots().get(0).getMove().getId());
        assertEquals(12, restored.getMoveSlots().get(0).getPp());
    }

    /** 等级缺失或越界时被裁剪到合法范围，而不是抛异常让读档整档失败。 */
    @Test
    void 越界等级被裁剪() {
        String speciesId = starter().getSpecies().getId();

        Pokemon tooHigh = PokemonMapper.toPokemon(new SaveData.PokemonData(speciesId,
                Pokemon.MAX_LEVEL + 50, new SaveData.IvData(1, 1, 1, 1, 1, 1), 0L, "NONE",
                0, 0, 0, 10, List.of())).orElseThrow();
        Pokemon tooLow = PokemonMapper.toPokemon(new SaveData.PokemonData(speciesId, 0,
                new SaveData.IvData(1, 1, 1, 1, 1, 1), 0L, "NONE", 0, 0, 0, 10,
                List.of())).orElseThrow();

        assertEquals(Pokemon.MAX_LEVEL, tooHigh.getLevel());
        assertEquals(1, tooLow.getLevel());
    }

    /** 异常状态名缺失或无法识别时按「无异常」处理；个体值缺失按全 0 处理。 */
    @Test
    void 缺失字段使用安全默认值() {
        String speciesId = starter().getSpecies().getId();
        SaveData.PokemonData data = new SaveData.PokemonData(speciesId, 5, null, 0L,
                "不存在的异常", 0, 0, 0, 10, null);

        Pokemon restored = PokemonMapper.toPokemon(data).orElseThrow();

        assertNotNull(restored.getIvs());
        assertIvs(new Stats(0, 0, 0, 0, 0, 0), restored.getIvs());
        assertEquals(StatusCondition.NONE, restored.getStatus());
        assertTrue(restored.getMoveSlots().isEmpty());
    }

    /** 读档还原出的种族定义必须与战斗中新生成的精灵同源（否则面板数值会前后不一致）。 */
    @Test
    void 读档种族与战斗生成同源() {
        Pokemon original = starter();

        Pokemon restored = PokemonMapper.toPokemon(PokemonMapper.toData(original)).orElseThrow();

        Species expected = original.getSpecies();
        Species actual = restored.getSpecies();
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getTypes(), actual.getTypes());
        assertEquals(expected.getBaseStats().getHp(), actual.getBaseStats().getHp());
        assertEquals(expected.getBaseStats().getSpeed(), actual.getBaseStats().getSpeed());
    }

    /** 队伍顺序必须保持：出战下标依赖它，顺序错了玩家会带着错误的精灵上场。 */
    @Test
    void 队伍顺序被保持() {
        List<org.example.pokemon.domain.Species> pool = service.getInitialPool();
        Optional<org.example.pokemon.domain.Species> second = pool.stream().skip(1).findFirst();
        org.junit.jupiter.api.Assumptions.assumeTrue(second.isPresent() && pool.size() > 1,
                "初始池至少需要两只精灵");

        Player player = PokemonBattleAdapter.createBattlePlayer("测试玩家",
                service.createPokemon(pool.get(0).getId(), 10));
        player.addPokemon(PokemonBattleAdapter.toBattlePokemon(
                service.createPokemon(second.get().getId(), 10)));

        String firstId = player.getParty().get(0).getSpecies().getId();
        String secondId = player.getParty().get(1).getSpecies().getId();

        assertEquals(firstId, PokemonMapper.toPokemon(
                PokemonMapper.toData(player.getParty().get(0))).orElseThrow()
                .getSpecies().getId());
        assertEquals(secondId, PokemonMapper.toPokemon(
                PokemonMapper.toData(player.getParty().get(1))).orElseThrow()
                .getSpecies().getId());
    }
}
