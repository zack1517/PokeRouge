package org.example.growth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 成长进度 / 图鉴数据接口测试：捕捉次数、对战次数与个体值加成的累计与换算。
 *
 * <p>口径：每累计捕捉同族精灵 {@link IvGrowthRule#CAPTURES_PER_STEP} 只记 1 点个体值加成，
 * 单族上限 {@link IvGrowthRule#MAX_IV}；全局加成为各族之和（同样封顶 31），
 * 由创建流程叠加到之后生成的所有宝可梦上。</p>
 */
class GrowthProgressTest {

    private static final String BULBASAUR = "bulbasaur";
    private static final String CHARMANDER = "charmander";

    @Test
    void 无记录时图鉴返回全零空记录且不产生写入() {
        GrowthProgress progress = new GrowthProgress();

        SpeciesGrowthRecord record = progress.record(BULBASAUR);

        assertEquals(BULBASAUR, record.getSpeciesId());
        assertEquals(0, record.getCaptureCount());
        assertEquals(0, record.getBattleCount());
        assertEquals(0, record.getIvBonus());
        assertTrue(record.isBlank());
        assertTrue(progress.dexEntries().isEmpty(), "查询不应产生图鉴条目");
    }

    @Test
    void 累计捕捉次数逐次递增并返回新值() {
        GrowthProgress progress = new GrowthProgress();

        assertEquals(1, progress.recordCapture(BULBASAUR));
        assertEquals(2, progress.recordCapture(BULBASAUR));
        assertEquals(3, progress.recordCapture(BULBASAUR));

        assertEquals(3, progress.captureCount(BULBASAUR));
        assertEquals(0, progress.captureCount(CHARMANDER), "不同种族互不影响");
    }

    @Test
    void 个体值加成按每两次捕捉记一点换算() {
        GrowthProgress progress = new GrowthProgress();

        assertEquals(0, progress.ivBonus(BULBASAUR), "0 次捕捉无加成");
        progress.recordCapture(BULBASAUR);
        assertEquals(0, progress.ivBonus(BULBASAUR), "1 次捕捉不足一步，仍无加成");
        progress.recordCapture(BULBASAUR);
        assertEquals(1, progress.ivBonus(BULBASAUR), "2 次捕捉记 1 点");
        for (int i = 0; i < 3; i++) {
            progress.recordCapture(BULBASAUR);
        }
        assertEquals(2, progress.ivBonus(BULBASAUR), "5 次捕捉记 2 点（向下取整）");
    }

    @Test
    void 单族加成封顶为个体值上限() {
        GrowthProgress progress = new GrowthProgress();

        for (int i = 0; i < IvGrowthRule.CAPTURES_PER_STEP * (IvGrowthRule.MAX_IV + 10); i++) {
            progress.recordCapture(BULBASAUR);
        }

        assertEquals(IvGrowthRule.MAX_IV, progress.ivBonus(BULBASAUR));
    }

    @Test
    void 全局加成为各族加成之和并封顶() {
        GrowthProgress progress = new GrowthProgress();

        progress.recordCapture(BULBASAUR);
        progress.recordCapture(BULBASAUR);
        progress.recordCapture(CHARMANDER);
        progress.recordCapture(CHARMANDER);

        assertEquals(2, progress.globalIvBonus(), "1 + 1 = 2");

        for (int i = 0; i < IvGrowthRule.CAPTURES_PER_STEP * 100; i++) {
            progress.recordCapture("squirtle");
        }
        assertEquals(IvGrowthRule.MAX_IV, progress.globalIvBonus(), "全局加成同样封顶 31");
    }

    @Test
    void 对战次数独立于捕捉次数累计() {
        GrowthProgress progress = new GrowthProgress();

        progress.recordCapture(BULBASAUR);
        progress.recordBattle(BULBASAUR);
        progress.recordBattle(BULBASAUR);

        assertEquals(1, progress.captureCount(BULBASAUR));
        assertEquals(2, progress.battleCount(BULBASAUR));
        assertEquals(0, progress.ivBonus(BULBASAUR), "对战次数不参与加成演算");
    }

    @Test
    void 图鉴按物种id排序并同时包含捕捉与对战记录() {
        GrowthProgress progress = new GrowthProgress();
        progress.recordCapture(CHARMANDER);
        progress.recordBattle(BULBASAUR);

        List<SpeciesGrowthRecord> entries = progress.dexEntries();

        assertEquals(2, entries.size());
        assertEquals(BULBASAUR, entries.get(0).getSpeciesId());
        assertEquals(CHARMANDER, entries.get(1).getSpeciesId());
        assertEquals(0, entries.get(0).getCaptureCount(), "只参战未捕捉");
        assertEquals(1, entries.get(0).getBattleCount());
        assertEquals(1, entries.get(1).getCaptureCount());
        assertFalse(entries.get(1).isBlank());
    }

    @Test
    void 同一进度实例跨多次查询持续累计() {
        GrowthProgress progress = new GrowthProgress();

        progress.recordCapture(BULBASAUR);
        progress.recordCapture(BULBASAUR);
        int bonusBeforeRun = progress.globalIvBonus();
        progress.recordCapture(BULBASAUR);

        assertEquals(1, bonusBeforeRun, "首轮结束后已有加成");
        assertEquals(1, progress.globalIvBonus(), "第 3 次捕捉仍不足一步");
        assertEquals(3, progress.captureCount(BULBASAUR), "进度跨轮累积不重置");
    }

    @Test
    void 清空进度后图鉴归零() {
        GrowthProgress progress = new GrowthProgress();
        progress.recordCapture(BULBASAUR);
        progress.recordBattle(BULBASAUR);

        progress.clear();

        assertEquals(0, progress.captureCount(BULBASAUR));
        assertEquals(0, progress.battleCount(BULBASAUR));
        assertEquals(0, progress.globalIvBonus());
        assertTrue(progress.dexEntries().isEmpty());
    }

    @Test
    void 物种id不可为空() {
        GrowthProgress progress = new GrowthProgress();

        assertThrows(NullPointerException.class, () -> progress.recordCapture(null));
        assertThrows(NullPointerException.class, () -> progress.recordBattle(null));
        assertThrows(NullPointerException.class, () -> progress.record(null));
    }

    @Test
    void 加成叠加到个体值后截断到上限() {
        assertEquals(0, IvGrowthRule.apply(0, 0));
        assertEquals(10, IvGrowthRule.apply(5, 5));
        assertEquals(IvGrowthRule.MAX_IV, IvGrowthRule.apply(31, 0));
        assertEquals(IvGrowthRule.MAX_IV, IvGrowthRule.apply(20, 30), "超出上限即满个体");
        assertEquals(5, IvGrowthRule.apply(5, -10), "负加成不产生负个体值");
    }
}
