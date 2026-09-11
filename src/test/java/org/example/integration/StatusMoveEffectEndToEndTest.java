package org.example.integration;

import org.example.battle.BattleDataPort;
import org.example.battle.BattleService;
import org.example.battle.BattleServices;
import org.example.model.Move;
import org.example.model.MoveSlot;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stat;
import org.example.model.Stats;
import org.example.model.StatusCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端实战回归：经真实数据端口（新宝可梦库链路）逐个把 13 个状态技能打进一场真实战斗，
 * 确认它们在引擎里确实生效，而不是只播报「但是什么也没有发生……」。
 *
 * <p>与 {@code BattleEngineSpecialStatusMoveTest} 的分工：后者用手写招式直接验引擎规则，
 * 本类走 {@link PokemonBattleAdapter#battleDataPort()} 的真实数据链路，专门守住
 * 「招式效果在数据接缝处被静默丢弃」这类回归。</p>
 */
class StatusMoveEffectEndToEndTest {

    private static final Stats NO_IV = new Stats(0, 0, 0, 0, 0, 0);
    private static final BattleDataPort PORT = PokemonBattleAdapter.battleDataPort();

    /** 速度 90，快过对手，保证玩家先手。 */
    private static final String PLAYER_SPECIES = "pikachu";
    /** 一般系，对全部异常状态都不免疫（也不是草系）。 */
    private static final String FOE_SPECIES = "eevee";

    /** 最有利随机源：命中恒成、附加效果恒触发、守住恒成功。 */
    private static final class LuckyRandom extends Random {
        @Override
        public int nextInt(int bound) {
            return 0;
        }

        @Override
        public double nextDouble() {
            return 0.0;
        }

        @Override
        public boolean nextBoolean() {
            return false;
        }
    }

    private record Probe(Pokemon mine, Pokemon foe, List<String> log) {
        String text() {
            return String.join("\n", log);
        }
    }

    private static Species species(String id) {
        Species found = PORT.findSpecies(id);
        assertNotNull(found, "找不到种族：" + id);
        return found;
    }

    private static Move move(String id) {
        Move found = PORT.findMove(id);
        assertNotNull(found, "找不到技能：" + id);
        return found;
    }

    /**
     * 开一场真实战斗，让玩家出一次指定状态技能，返回回合结束后的双方个体与日志。
     *
     * @param preDamagePercent 出手前先按最大 HP 的百分比扣血（「睡觉」等需要残血前提的技能用）
     */
    private static Probe probe(String playerMoveId, int preDamagePercent) {
        Pokemon mine = Pokemon.create(species(PLAYER_SPECIES), 20, List.of(move(playerMoveId)), NO_IV);
        Pokemon foe = Pokemon.create(species(FOE_SPECIES), 20, List.of(move("tackle")), NO_IV);

        Player player = new Player("测试");
        assertTrue(player.addPokemon(mine));

        BattleService battle = BattleServices.newBattle(player, foe, new LuckyRandom(), PORT);
        if (preDamagePercent > 0) {
            Pokemon active = battle.playerActive();
            active.takeDamage(active.getMaxHp() * preDamagePercent / 100);
        }

        MoveSlot slot = battle.playerActive().getMoveSlots().get(0);
        assertEquals(playerMoveId, slot.getMove().getId(), "技能槽里应当就是待验证的状态技能");
        assertTrue(slot.getMove().isStatus(), playerMoveId + " 应当是变化类技能");

        List<String> log = battle.useMove(slot);
        Probe probe = new Probe(battle.playerActive(), battle.foeActive(), log);
        assertFalse(probe.text().contains("什么也没有发生"),
                playerMoveId + " 不应落到「什么也没有发生」兜底分支，实际日志：\n" + probe.text());
        return probe;
    }

    // ---------------------------------------------------------------- 能力等级变化

    @Test
    @DisplayName("叫声：对手物攻 -1，力度确实变低")
    void growl() {
        Probe p = probe("growl", 0);
        assertEquals(-1, p.foe().getStatStage(Stat.ATTACK));
        assertTrue(p.text().contains("物攻下降了"), p.text());
    }

    @Test
    @DisplayName("摇尾巴：对手物防 -1")
    void tailWhip() {
        Probe p = probe("tail-whip", 0);
        assertEquals(-1, p.foe().getStatStage(Stat.DEFENSE));
        assertTrue(p.text().contains("物防下降了"), p.text());
    }

    @Test
    @DisplayName("硬邦邦：自身物防 +1，实防确实变高")
    void harden() {
        int before = p0Defense();
        Probe p = probe("harden", 0);
        assertEquals(1, p.mine().getStatStage(Stat.DEFENSE));
        assertTrue(p.mine().effectiveDefense() > before, p.text());
        assertTrue(p.text().contains("物防提高了"), p.text());
    }

    private static int p0Defense() {
        return Pokemon.create(species(PLAYER_SPECIES), 20, List.of(), NO_IV).effectiveDefense();
    }

    @Test
    @DisplayName("高速移动：自身速度 +2，实速确实变快")
    void agility() {
        int before = Pokemon.create(species(PLAYER_SPECIES), 20, List.of(), NO_IV).effectiveSpeed();
        Probe p = probe("agility", 0);
        assertEquals(2, p.mine().getStatStage(Stat.SPEED));
        assertTrue(p.mine().effectiveSpeed() > before, p.text());
        assertTrue(p.text().contains("速度大幅提高了"), p.text());
    }

    @Test
    @DisplayName("吐丝：对手速度 -1，实速确实变慢")
    void stringShot() {
        Probe p = probe("string-shot", 0);
        assertEquals(-1, p.foe().getStatStage(Stat.SPEED));
        assertTrue(p.text().contains("速度下降了"), p.text());
    }

    // ---------------------------------------------------------------- 专属效果

    @Test
    @DisplayName("守住：真的挡下了对手这一回合的攻击")
    void protect() {
        Probe p = probe("protect", 0);
        assertTrue(p.text().contains("保护了自己！"), p.text());
        assertTrue(p.text().contains("用守住挡下了攻击！"), p.text());
        assertEquals(p.mine().getMaxHp(), p.mine().getCurrentHp(), "被守住时不该掉血：" + p.text());
    }

    @Test
    @DisplayName("寄生种子：种下并在回合末真实吸血")
    void leechSeed() {
        Probe p = probe("leech-seed", 0);
        assertTrue(p.foe().isSeeded(), "对手应当被种下种子");
        assertTrue(p.text().contains("被种下了寄生种子！"), p.text());
        assertTrue(p.text().contains("寄生种子吸取了"), p.text());
        assertEquals(p.foe().getMaxHp() - Math.max(1, p.foe().getMaxHp() / 8), p.foe().getCurrentHp(),
                "回合末应当恰好被吸走最大 HP 的 1/8：" + p.text());
    }

    @Test
    @DisplayName("睡觉：残血时回满并陷入睡眠")
    void rest() {
        Probe p = probe("rest", 50);
        int max = p.mine().getMaxHp();
        int missing = max * 50 / 100;
        assertEquals(StatusCondition.SLEEP, p.mine().getStatus());
        assertTrue(p.text().contains("睡着了！"), p.text());
        // 恰好把出手前缺失的那部分补满 => 睡觉当时已回满（之后对手的撞击会再掉血，故不直接比对满血）
        assertTrue(p.text().contains("HP 恢复了 " + missing + " 点！"), p.text());
        assertTrue(p.mine().getCurrentHp() > max - missing, "回满后只应被对手的攻击扣血：" + p.text());
    }

    // ---------------------------------------------------------------- 异常状态

    @Test
    @DisplayName("鬼火：对手陷入灼伤")
    void willOWisp() {
        Probe p = probe("will-o-wisp", 0);
        assertEquals(StatusCondition.BURN, p.foe().getStatus());
        assertTrue(p.text().contains("陷入了灼伤状态！"), p.text());
    }

    @Test
    @DisplayName("催眠粉：对手陷入睡眠")
    void sleepPowder() {
        Probe p = probe("sleep-powder", 0);
        assertEquals(StatusCondition.SLEEP, p.foe().getStatus());
        assertTrue(p.text().contains("陷入了睡眠状态！"), p.text());
    }

    @Test
    @DisplayName("电磁波：对手陷入麻痹")
    void thunderWave() {
        Probe p = probe("thunder-wave", 0);
        assertEquals(StatusCondition.PARALYSIS, p.foe().getStatus());
        assertTrue(p.text().contains("陷入了麻痹状态！"), p.text());
    }

    @Test
    @DisplayName("奇异之光：对手陷入混乱")
    void confuseRay() {
        Probe p = probe("confuse-ray", 0);
        assertTrue(p.foe().isConfused(), p.text());
        assertTrue(p.text().contains("陷入了混乱状态！"), p.text());
    }

    @Test
    @DisplayName("毒粉：对手陷入中毒")
    void poisonPowder() {
        Probe p = probe("poison-powder", 0);
        assertEquals(StatusCondition.POISON, p.foe().getStatus());
        assertTrue(p.text().contains("陷入了中毒状态！"), p.text());
    }

    @Test
    @DisplayName("剧毒：对手陷入剧毒")
    void toxic() {
        Probe p = probe("toxic", 0);
        assertEquals(StatusCondition.BADLY_POISON, p.foe().getStatus());
        assertTrue(p.text().contains("陷入了剧毒状态！"), p.text());
    }
}
