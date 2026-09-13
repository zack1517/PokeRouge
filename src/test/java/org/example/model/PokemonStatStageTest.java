package org.example.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Pokemon} 能力等级测试：{@code statStages} 的读取、±6 截断、清零，
 * 以及能力等级对 {@code effective*} 属性的实际影响。
 *
 * <p>能力项取 {@link Stat}（ATTACK/DEFENSE/SP_ATTACK/SP_DEFENSE/SPEED）；HP 不参与能力等级，
 * 因此不在枚举中。</p>
 */
class PokemonStatStageTest {

    private static final Stats FIXED_IVS = new Stats(31, 31, 31, 31, 31, 31);

    private static Pokemon poke(int attack, int defense, int spAttack, int spDefense, int speed) {
        Species species = new Species("stage_sp", "等级兽", ElementType.NORMAL, null,
                new Stats(100, attack, defense, spAttack, spDefense, speed), 100,
                List.of(), null, 0, Map.of());
        return Pokemon.create(species, 50, List.of(), FIXED_IVS);
    }

    @Test
    void 初始等级全为0() {
        Pokemon p = poke(100, 100, 100, 100, 100);
        for (Stat stat : Stat.values()) {
            assertEquals(0, p.getStatStage(stat), stat + " 初始应为 0 级");
        }
        assertEquals(0, p.getStatStage(null), "null 能力项返回 0 而不是抛异常");
    }

    @Test
    void 等级倍率符合原版公式() {
        assertEquals(1.0, Pokemon.stageMultiplier(0), 1e-9);
        assertEquals(1.5, Pokemon.stageMultiplier(1), 1e-9);
        assertEquals(2.0, Pokemon.stageMultiplier(2), 1e-9);
        assertEquals(3.5, Pokemon.stageMultiplier(5), 1e-9);
        assertEquals(4.0, Pokemon.stageMultiplier(6), 1e-9);
        assertEquals(2.0 / 3.0, Pokemon.stageMultiplier(-1), 1e-9);
        assertEquals(0.5, Pokemon.stageMultiplier(-2), 1e-9);
        assertEquals(0.25, Pokemon.stageMultiplier(-6), 1e-9);
        assertEquals(4.0, Pokemon.stageMultiplier(99), 1e-9, "越界应按 ±6 截断");
        assertEquals(0.25, Pokemon.stageMultiplier(-99), 1e-9, "越界应按 ±6 截断");
    }

    @Test
    void 提升降低返回实际变化量() {
        Pokemon p = poke(100, 100, 100, 100, 100);
        assertEquals(1, p.changeStatStage(Stat.ATTACK, 1));
        assertEquals(1, p.getStatStage(Stat.ATTACK));
        assertEquals(-2, p.changeStatStage(Stat.ATTACK, -2));
        assertEquals(-1, p.getStatStage(Stat.ATTACK));
        assertEquals(0, p.changeStatStage(Stat.ATTACK, 0), "变化量 0 不生效");
        assertEquals(0, p.changeStatStage(null, 2), "null 能力项不生效");
    }

    @Test
    void 等级按正负六截断() {
        Pokemon p = poke(100, 100, 100, 100, 100);
        assertEquals(6, p.changeStatStage(Stat.SPEED, 10), "一次超量提升应被截断到 +6");
        assertEquals(6, p.getStatStage(Stat.SPEED));
        assertEquals(0, p.changeStatStage(Stat.SPEED, 1), "已到上限时实际变化量为 0");

        // 从 +6 一次降到下限：实际变化量为 -12（+6 → -6），不是 -20
        assertEquals(-12, p.changeStatStage(Stat.SPEED, -20), "超量降低应按 ±6 截断");
        assertEquals(-6, p.getStatStage(Stat.SPEED));
        assertEquals(0, p.changeStatStage(Stat.SPEED, -1), "已到下限期时实际变化量为 0");
    }

    @Test
    void 能力等级影响实际面板() {
        Pokemon p = poke(100, 100, 100, 100, 100);
        int baseAttack = p.effectiveAttack();
        int baseDefense = p.effectiveDefense();
        int baseSpAttack = p.effectiveSpAttack();
        int baseSpDefense = p.effectiveSpDefense();
        int baseSpeed = p.effectiveSpeed();

        p.changeStatStage(Stat.ATTACK, 2);
        p.changeStatStage(Stat.DEFENSE, -2);
        p.changeStatStage(Stat.SP_ATTACK, 1);
        p.changeStatStage(Stat.SP_DEFENSE, 1);
        p.changeStatStage(Stat.SPEED, 1);

        assertEquals(Math.round(baseAttack * 2.0), p.effectiveAttack(), "+2 级物攻应翻倍");
        assertEquals(Math.round(baseDefense * 0.5), p.effectiveDefense(), "-2 级物防应减半");
        assertEquals(Math.round(baseSpAttack * 1.5), p.effectiveSpAttack());
        assertEquals(Math.round(baseSpDefense * 1.5), p.effectiveSpDefense());
        assertEquals(Math.round(baseSpeed * 1.5), p.effectiveSpeed());
    }

    @Test
    void 清零恢复原始面板() {
        Pokemon p = poke(100, 100, 100, 100, 100);
        int baseAttack = p.effectiveAttack();
        p.changeStatStage(Stat.ATTACK, 6);
        p.changeStatStage(Stat.SPEED, -6);
        p.clearStatStages();
        assertEquals(baseAttack, p.effectiveAttack(), "清零后应恢复原始物攻");
        for (Stat stat : Stat.values()) {
            assertEquals(0, p.getStatStage(stat), stat + " 清零后应为 0 级");
        }
    }

    @Test
    void 实际面板最低为1() {
        Pokemon p = poke(1, 1, 1, 1, 1);
        p.changeStatStage(Stat.ATTACK, -6);
        p.changeStatStage(Stat.DEFENSE, -6);
        p.changeStatStage(Stat.SPEED, -6);
        assertTrue(p.effectiveAttack() >= 1);
        assertTrue(p.effectiveDefense() >= 1);
        assertTrue(p.effectiveSpeed() >= 1);
    }
}
