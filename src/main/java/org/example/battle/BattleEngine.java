package org.example.battle;

import org.example.data.GameData;
import org.example.model.ElementType;
import org.example.model.Item;
import org.example.model.ItemCategory;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.MoveSlot;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stats;
import org.example.model.TypeChart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * 回合制对战引擎。
 *
 * <p>规则：玩家与野生精灵每回合各执行一次行动（技能 / 道具 / 逃跑）。双方都用技能时按
 * 速度快者先动（相同速度随机）。物理技能取 物攻 vs 物防，特殊技能取 特攻 vs 特防，
 * 伤害受克制倍率、STAB(本系加成) 与随机浮动影响。捕捉成功、逃跑成功或一方全灭即结束。</p>
 */
public class BattleEngine {

    /** 战斗状态。 */
    public enum Status {
        /** 进行中。 */
        ONGOING,
        /** 玩家获胜（野生精灵倒下）。 */
        PLAYER_WIN,
        /** 玩家战败（队伍全部倒下）。 */
        PLAYER_LOSE,
        /** 逃跑成功。 */
        FLED,
        /** 捕捉成功。 */
        CAUGHT
    }

    private final Player player;
    private final Pokemon wild;
    private final Random random;
    /** 全程日志（按行累积）。 */
    private final List<String> log = new ArrayList<>();

    private Status status = Status.ONGOING;

    public BattleEngine(Player player, Pokemon wild) {
        this(player, wild, new Random());
    }

    public BattleEngine(Player player, Pokemon wild, Random random) {
        this.player = Objects.requireNonNull(player);
        this.wild = Objects.requireNonNull(wild);
        this.random = Objects.requireNonNull(random);
        if (player.getActive() == null || player.getActive().isFainted()) {
            throw new IllegalArgumentException("玩家没有可用精灵出战");
        }
        if (wild == null || wild.isFainted()) {
            throw new IllegalArgumentException("野生精灵无效");
        }
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    public Player getPlayer() {
        return player;
    }

    public Pokemon getWild() {
        return wild;
    }

    public Pokemon playerActive() {
        return player.getActive();
    }

    public Status getStatus() {
        return status;
    }

    public boolean isOngoing() {
        return status == Status.ONGOING;
    }

    /** 完整战斗日志（只读）。 */
    public List<String> getLog() {
        return Collections.unmodifiableList(log);
    }

    /** 背包（从玩家处转发，便捷）。 */
    public org.example.model.Bag getBag() {
        return player.getBag();
    }

    // ------------------------------------------------------------------
    // 回合行动入口
    // ------------------------------------------------------------------

    /**
     * 玩家选择技能。野生精灵自动选择可用技能；按速度决定先后。
     *
     * @return 本回合产生的新日志
     */
    public List<String> useMove(MoveSlot slot) {
        int mark = log.size();
        requireOngoing();
        MoveSlot usable = usableSlot(playerActive(), slot);
        if (usable == null) {
            return slice(mark);
        }
        if (!usable.use()) {
            append(playerActive().getName() + " 的【" + usable.getMove().getName() + "】PP 不足！");
            return slice(mark);
        }
        append(playerActive().getName() + " 使用了【" + usable.getMove().getName() + "】！");

        // 决定本回合先后手：双方都行动，比较速度
        int playerSpeed = playerActive().getStats().getSpeed();
        int wildSpeed = wild.getStats().getSpeed();
        boolean playerFirst = playerSpeed > wildSpeed
                || (playerSpeed == wildSpeed && random.nextBoolean());

        if (playerFirst) {
            performAttack(playerActive(), wild, usable.getMove());
            if (isOngoing() && !wild.isFainted() && !playerActive().isFainted()) {
                wildTurn();
            }
        } else {
            wildTurn();
            if (isOngoing() && !playerActive().isFainted() && !wild.isFainted()) {
                performAttack(playerActive(), wild, usable.getMove());
            }
        }
        resolveRoundEnd();
        return slice(mark);
    }

    /**
     * 玩家使用道具：回复道具回复当前精灵 HP；精灵球尝试捕捉野生精灵。
     * 道具使用不计先后手（视为先行动作），使用后若战斗未结束则野生精灵行动一次。
     *
     * @return 本回合产生的新日志
     */
    public List<String> useItem(Item item) {
        int mark = log.size();
        requireOngoing();
        if (item == null || player.getBag().countOf(item) <= 0) {
            return slice(mark);
        }
        if (item.getCategory() == ItemCategory.HEAL) {
            Pokemon active = playerActive();
            int healed = active.heal((int) item.getEffect());
            if (healed <= 0) {
                append(active.getName() + " 的 HP 是满的，【" + item.getName() + "】没有使用。");
                return slice(mark);
            }
            player.getBag().consume(item);
            append("使用了【" + item.getName() + "】，" + active.getName() + " 回复了 " + healed + " HP");
            if (isOngoing() && !wild.isFainted() && !playerActive().isFainted()) {
                wildTurn();
            }
        } else if (item.getCategory() == ItemCategory.POKE_BALL) {
            player.getBag().consume(item);
            append("向 " + wild.getName() + " 投出了【" + item.getName() + "】！");
            if (!tryCapture(item) && isOngoing() && !wild.isFainted() && !playerActive().isFainted()) {
                wildTurn();
            }
        } else {
            append("该道具暂时无法使用");
        }
        resolveRoundEnd();
        return slice(mark);
    }

    /**
     * 玩家尝试逃跑：速度越快成功率越高。失败则野生精灵行动一次。
     *
     * @return 本回合产生的新日志
     */
    public List<String> tryRun() {
        int mark = log.size();
        requireOngoing();
        int playerSpeed = playerActive().getStats().getSpeed();
        int wildSpeed = wild.getStats().getSpeed();
        double ratio = (double) playerSpeed / Math.max(1, playerSpeed + wildSpeed);
        double chance = 0.35 + 0.6 * ratio; // 速度相当约 0.65，远超时接近 0.95
        if (random.nextDouble() < chance) {
            append("成功逃跑了！");
            status = Status.FLED;
            return slice(mark);
        }
        append("逃跑失败……");
        if (!wild.isFainted() && !playerActive().isFainted()) {
            wildTurn();
        }
        resolveRoundEnd();
        return slice(mark);
    }

    /**
     * 玩家回合切换出战精灵：消耗本回合行动，收换完成后野生精灵行动一次。
     *
     * @return 本回合产生的新日志
     */
    public List<String> switchActive(int partyIndex) {
        int mark = log.size();
        requireOngoing();
        Pokemon current = playerActive();
        Pokemon target = player.switchTo(partyIndex);
        if (current == null || target == null || target == current) {
            return slice(mark);
        }
        append(player.getName() + " 收回了 " + current.getName() + "！");
        append("你派出了 " + target.getName() + "！");
        if (!wild.isFainted() && !target.isFainted()) {
            wildTurn();
        }
        resolveRoundEnd();
        return slice(mark);
    }

    // ------------------------------------------------------------------
    // 内部流程
    // ------------------------------------------------------------------

    private void wildTurn() {
        if (status != Status.ONGOING) {
            return;
        }
        MoveSlot usable = pickWildMove();
        if (usable == null) {
            append(wild.getName() + " 没有可用技能了，正在挣扎！");
            int dmg = Math.max(1, wild.getLevel() / 4);
            int dealt = playerActive().takeDamage(dmg);
            append("对 " + playerActive().getName() + " 造成了 " + dealt + " 点伤害");
        } else {
            usable.use();
            append(wild.getName() + " 使用了【" + usable.getMove().getName() + "】！");
            performAttack(wild, playerActive(), usable.getMove());
        }
    }

    /** 敌方自动选择技能：从仍有 PP 的技能中随机挑一个。 */
    private MoveSlot pickWildMove() {
        List<MoveSlot> usable = wild.getMoveSlots().stream()
                .filter(s -> !s.exhausted())
                .toList();
        if (usable.isEmpty()) {
            return null;
        }
        return usable.get(random.nextInt(usable.size()));
    }

    private MoveSlot usableSlot(Pokemon pokemon, MoveSlot preferred) {
        // 优先使用玩家指定的槽位（必须是当前精灵且仍有 PP）
        if (preferred != null && !preferred.exhausted()) {
            for (MoveSlot s : pokemon.getMoveSlots()) {
                if (s == preferred) {
                    return s;
                }
            }
        }
        // 否则（如指定的精灵已倒下被切换）退回当前精灵第一个可用技能
        return pokemon.getMoveSlots().stream()
                .filter(s -> !s.exhausted())
                .findFirst()
                .orElse(null);
    }

    private void performAttack(Pokemon attacker, Pokemon defender, Move move) {
        double effectiveness = typeEffectiveness(move.getType(), defender);
        if (effectiveness <= 0) {
            append("这招对 " + defender.getName() + " 没有效果……");
            return;
        }
        int damage = computeDamage(attacker, defender, move);
        int dealt = defender.takeDamage(damage);
        StringBuilder sb = new StringBuilder();
        sb.append("造成 ").append(dealt).append(" 点伤害");
        if (effectiveness > 1.0) {
            sb.append("，效果拔群！");
        } else if (effectiveness < 1.0) {
            sb.append("，效果不太理想……");
        }
        append(sb.toString());
        if (defender.isFainted()) {
            append(defender.getName() + " 倒下了！");
        }
    }

    private int computeDamage(Pokemon attacker, Pokemon defender, Move move) {
        double atk;
        double def;
        if (move.getCategory() == MoveCategory.PHYSICAL) {
            atk = attacker.getStats().getAttack();
            def = defender.getStats().getDefense();
        } else {
            atk = attacker.getStats().getSpAttack();
            def = defender.getStats().getSpDefense();
        }
        int level = attacker.getLevel();
        double base = (2.0 * level / 5.0 + 2.0) * move.getPower()
                * (atk / Math.max(1.0, def)) / 50.0 + 2.0;
        double stab = attacker.hasType(move.getType()) ? 1.5 : 1.0;
        double effectiveness = typeEffectiveness(move.getType(), defender);
        double randomFactor = 0.85 + random.nextDouble() * 0.15;
        int raw = (int) Math.floor(base * stab * effectiveness * randomFactor);
        return Math.max(1, raw);
    }

    private boolean tryCapture(Item ball) {
        boolean caught;
        if (ball.isAlwaysCatch()) {
            caught = true;
        } else {
            int maxHp = wild.getMaxHp();
            int curHp = wild.getCurrentHp();
            // 血量越低、捕获率越高、球倍率越大则越容易
            double hpFactor = Math.max(0.0, (3.0 * maxHp - 2.0 * curHp) / (3.0 * maxHp));
            double a = hpFactor * wild.getSpecies().getCatchRate() * ball.getEffect();
            double chance = Math.min(0.98, a / 255.0);
            caught = random.nextDouble() < chance;
        }
        if (caught) {
            append("咔哒…… 球停止了晃动！");
            append("成功捕捉了野生的 " + wild.getName() + "！");
            status = Status.CAUGHT;
            // 被捕捉的精灵加入玩家队伍，后续可再次派出
            if (!player.getParty().contains(wild)) {
                player.addToParty(wild);
            }
            return true;
        }
        append("野生的 " + wild.getName() + " 挣脱了出来！");
        return false;
    }

    /** 回合结束结算：胜负判定与玩家精灵倒下后的自动换宠。 */
    private void resolveRoundEnd() {
        if (status != Status.ONGOING) {
            return;
        }
        if (wild.isFainted()) {
            status = Status.PLAYER_WIN;
            append("野生的 " + wild.getName() + " 倒下了！你赢了！");
            awardExpAndSettle();
            return;
        }
        if (playerActive() != null && playerActive().isFainted()) {
            append(playerActive().getName() + " 倒下了……");
            if (player.switchToNextHealthy() != null) {
                append("你派出了 " + playerActive().getName() + "！");
            } else {
                status = Status.PLAYER_LOSE;
                append("你已没有能战斗的精灵，战败了……");
            }
        }
    }

    /**
     * 胜利后向队伍发放经验：每只未倒下的精灵获得全额经验，结算逐级升级、
     * 到级学招与进化。
     */
    private void awardExpAndSettle() {
        if (status != Status.PLAYER_WIN) {
            return;
        }
        int gain = expGain(wild);
        for (Pokemon p : player.getParty()) {
            if (p.isFainted()) {
                continue;
            }
            int before = p.getLevel();
            int gainedLevels = p.addExp(gain);
            if (gainedLevels <= 0) {
                continue;
            }
            for (int lv = before + 1; lv <= p.getLevel(); lv++) {
                append(p.getName() + " 升到了 Lv." + lv + "！");
                tryLearnAt(p, lv);
                tryEvolve(p, lv);
            }
        }
    }

    /** 依据被击败精灵的种族与等级折算经验：六维种族值总和 × 等级 / 5。 */
    private static int expGain(Pokemon defeated) {
        Stats stats = defeated.getSpecies().getBaseStats();
        int total = stats.getHp() + stats.getAttack() + stats.getDefense()
                + stats.getSpAttack() + stats.getSpDefense() + stats.getSpeed();
        return Math.max(30, total * defeated.getLevel() / 5);
    }

    /** 等级达到习得表要求时尝试学会新技能（4 招满时自动遗忘最弱的）。 */
    private void tryLearnAt(Pokemon p, int level) {
        String moveId = p.getSpecies().moveLearnedAt(level);
        if (moveId == null) {
            return;
        }
        Move move = GameData.instance().move(moveId);
        if (move == null || p.hasMove(move)) {
            return;
        }
        Move forgotten = p.learnMove(move);
        if (forgotten == null) {
            append(p.getName() + " 记住了【" + move.getName() + "】！");
        } else {
            append(p.getName() + " 记住了【" + move.getName()
                    + "】，遗忘了【" + forgotten.getName() + "】！");
        }
    }

    /** 达到进化等级时进化为目标形态（重新演算属性并回满状态）。 */
    private void tryEvolve(Pokemon p, int level) {
        if (!p.canEvolve()) {
            return;
        }
        Species target = GameData.instance().species(p.getSpecies().getEvolvesToId());
        if (target == null) {
            return;
        }
        append(p.getName() + " 进化成了 " + target.getName() + "！");
        p.evolveTo(target);
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static double typeEffectiveness(ElementType attack, Pokemon defender) {
        double result = 1.0;
        for (ElementType t : defender.getSpecies().getTypes()) {
            result *= TypeChart.effectiveness(attack, t);
        }
        return result;
    }

    private void requireOngoing() {
        if (status != Status.ONGOING) {
            throw new IllegalStateException("战斗已结束，状态: " + status);
        }
    }

    private void append(String message) {
        log.add(message);
    }

    /** 返回自 mark 起新增的日志行。 */
    private List<String> slice(int mark) {
        return new ArrayList<>(log.subList(Math.max(0, mark), log.size()));
    }

    /** 便捷静态方法：野生等级围绕玩家等级浮动。 */
    public static int wildLevelAround(int playerLevel) {
        return Math.max(2, playerLevel + (int) (Math.random() * 5) - 2);
    }

    /** 便捷静态方法：从数据注册表随机挑一只野生精灵。 */
    public static Optional<Pokemon> randomWild(int aroundLevel) {
        java.util.List<String> pool = org.example.data.GameData.instance().wildPool();
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        String id = pool.get((int) (Math.random() * pool.size()));
        return org.example.data.GameData.instance().createPokemon(id, aroundLevel);
    }
}
