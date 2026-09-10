package org.example.pokemon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.example.growth.GrowthProgress;
import org.example.growth.IvGrowthRule;
import org.example.pokemon.domain.Nature;
import org.example.pokemon.domain.Pokemon;
import org.example.pokemon.domain.Species;
import org.example.pokemon.domain.Stats;
import org.example.pokemon.infrastructure.GameData;
import org.junit.jupiter.api.Test;

/**
 * 成长机制落地测试：局外累计的个体值加成必须真实作用到<b>之后新建的所有宝可梦</b>
 * （普通创建、指定性格创建、野生遭遇三条入口），且<b>种族值不变</b>。
 *
 * <p>断言口径：个体值 = {@code min(31, 随机 0-31 + 全局加成)}，因此
 * 「每项 ≥ 加成」与「加成满 31 时每项恰为 31」都是确定性断言，不依赖随机种子。</p>
 */
class PokemonServiceImplGrowthTest {

    /** 数据模块的初始池种族 id，用于按真实数据构造个体。 */
    private static final String SPECIES_ID = "bulbasaur";

    /** 把进度灌满到个体值加成上限所需的捕捉次数。 */
    private static int capturesForFullBonus() {
        return IvGrowthRule.CAPTURES_PER_STEP * IvGrowthRule.MAX_IV;
    }

    private static void recordCaptures(GrowthProgress progress, String speciesId, int times) {
        for (int i = 0; i < times; i++) {
            progress.recordCapture(speciesId);
        }
    }

    private static void assertEveryIvAtLeast(Stats ivs, int floor) {
        assertTrue(ivs.getHp() >= floor, "HP 个体值应 ≥ " + floor + "，实际 " + ivs.getHp());
        assertTrue(ivs.getAttack() >= floor, "物攻个体值应 ≥ " + floor);
        assertTrue(ivs.getDefense() >= floor, "物防个体值应 ≥ " + floor);
        assertTrue(ivs.getSpAttack() >= floor, "特攻个体值应 ≥ " + floor);
        assertTrue(ivs.getSpDefense() >= floor, "特防个体值应 ≥ " + floor);
        assertTrue(ivs.getSpeed() >= floor, "速度个体值应 ≥ " + floor);
    }

    private static void assertEveryIvAtMost(Stats ivs, int ceiling) {
        assertTrue(ivs.getHp() <= ceiling, "HP 个体值应 ≤ " + ceiling + "，实际 " + ivs.getHp());
        assertTrue(ivs.getAttack() <= ceiling);
        assertTrue(ivs.getDefense() <= ceiling);
        assertTrue(ivs.getSpAttack() <= ceiling);
        assertTrue(ivs.getSpDefense() <= ceiling);
        assertTrue(ivs.getSpeed() <= ceiling);
    }

    /** 无任何成长进度时，个体值仍在 0-31 合法区间内（不改变原有行为）。 */
    @Test
    void 无成长进度时个体值仍在合法区间() {
        PokemonServiceImpl service = new PokemonServiceImpl(new GrowthProgress());

        Pokemon created = service.createPokemon(SPECIES_ID, 20);
        Pokemon wild = service.createWildPokemon(SPECIES_ID, 20);

        assertEveryIvAtLeast(created.getIvs(), 0);
        assertEveryIvAtMost(created.getIvs(), IvGrowthRule.MAX_IV);
        assertEveryIvAtLeast(wild.getIvs(), 0);
        assertEveryIvAtMost(wild.getIvs(), IvGrowthRule.MAX_IV);
    }

    /** 累计捕捉同族精灵后，之后创建的精灵个体值整体抬升，且下限等于当前全局加成。 */
    @Test
    void 捕捉累计后新建精灵个体值获得加成() {
        GrowthProgress progress = new GrowthProgress();
        recordCaptures(progress, SPECIES_ID, 6);
        int bonus = progress.globalIvBonus();
        assertEquals(3, bonus, "6 次捕捉应为 3 点加成");

        PokemonServiceImpl service = new PokemonServiceImpl(progress);
        Pokemon created = service.createPokemon(SPECIES_ID, 20);
        Pokemon wild = service.createWildPokemon(SPECIES_ID, 20);
        Pokemon withNature = service.createPokemon(SPECIES_ID, 20, Nature.ADAMANT);

        assertEveryIvAtLeast(created.getIvs(), bonus);
        assertEveryIvAtLeast(wild.getIvs(), bonus);
        assertEveryIvAtLeast(withNature.getIvs(), bonus);
    }

    /** 加成累计到上限时，新建精灵必定是满个体（六项全 31）。 */
    @Test
    void 加成满值时新建精灵必为满个体() {
        GrowthProgress progress = new GrowthProgress();
        recordCaptures(progress, SPECIES_ID, capturesForFullBonus());
        assertEquals(IvGrowthRule.MAX_IV, progress.globalIvBonus());

        PokemonServiceImpl service = new PokemonServiceImpl(progress);

        for (Pokemon p : List.of(
                service.createPokemon(SPECIES_ID, 20),
                service.createWildPokemon(SPECIES_ID, 20),
                service.createPokemon(SPECIES_ID, 20, Nature.MODEST))) {
            assertEveryIvAtLeast(p.getIvs(), IvGrowthRule.MAX_IV);
            assertEveryIvAtMost(p.getIvs(), IvGrowthRule.MAX_IV);
        }
    }

    /** 个体值成长只作用于个体值：种族值（含六维）必须与数据模块完全一致。 */
    @Test
    void 个体值成长不改变种族值() {
        GrowthProgress progress = new GrowthProgress();
        recordCaptures(progress, SPECIES_ID, capturesForFullBonus());
        PokemonServiceImpl service = new PokemonServiceImpl(progress);

        Pokemon created = service.createPokemon(SPECIES_ID, 20);
        Species expected = GameData.instance().getSpecies(SPECIES_ID).orElseThrow();

        assertEquals(expected.getBaseStats().getHp(), created.getSpecies().getBaseStats().getHp());
        assertEquals(expected.getBaseStats().getAttack(), created.getSpecies().getBaseStats().getAttack());
        assertEquals(expected.getBaseStats().getDefense(), created.getSpecies().getBaseStats().getDefense());
        assertEquals(expected.getBaseStats().getSpAttack(), created.getSpecies().getBaseStats().getSpAttack());
        assertEquals(expected.getBaseStats().getSpDefense(), created.getSpecies().getBaseStats().getSpDefense());
        assertEquals(expected.getBaseStats().getSpeed(), created.getSpecies().getBaseStats().getSpeed());
    }

    /** 个体值真实参与面板演算：相同种族与等级下，满个体 HP 上限必定高于零个体。 */
    @Test
    void 满个体面板高于零个体面板() {
        Species species = GameData.instance().getSpecies(SPECIES_ID).orElseThrow();

        Pokemon zeroIv = new Pokemon(species, 50, new Stats(0, 0, 0, 0, 0, 0), Nature.HARDY);
        Pokemon fullIv = new Pokemon(species, 50, new Stats(31, 31, 31, 31, 31, 31), Nature.HARDY);

        assertTrue(fullIv.getStats().getHp() > zeroIv.getStats().getHp(),
                "满个体 HP 上限应高于零个体：满=" + fullIv.getStats().getHp()
                        + "，零=" + zeroIv.getStats().getHp());
        assertTrue(fullIv.getStats().getSpeed() > zeroIv.getStats().getSpeed(),
                "满个体速度应高于零个体");
    }

    /** 服务对外暴露同一份成长进度，供局外图鉴查询。 */
    @Test
    void 服务暴露所注入的成长进度() {
        GrowthProgress progress = new GrowthProgress();
        PokemonServiceImpl service = new PokemonServiceImpl(progress);

        assertSame(progress, service.getGrowthProgress());
    }
}
