package org.example.model;

import java.util.List;
import java.util.Random;

public class RogueTurnManager {
    private final RunData runData;
    private final OptionGenerator generator;
    private final Random random = new Random();

    public RogueTurnManager() {
        this(new RunData(), new OptionGenerator());
    }

    public RogueTurnManager(RunData runData, OptionGenerator generator) {
        this.runData = runData == null ? new RunData() : runData;
        this.generator = generator == null ? new OptionGenerator() : generator;
    }

    public RunData getRunData() {
        return runData;
    }

    public OptionGenerator getGenerator() {
        return generator;
    }

    public void enterFloor(int floor) {
        FloorData floorData = generator.generateFloor(floor);
        runData.setCurrentFloor(floor);
        runData.setCurrentPoints(floorData.getStartingPoints());
        runData.setAvailableOptions(floorData.getAvailableOptions());
        runData.setBossOption(floorData.getBossOption());
        runData.setGameOver(false);
    }

    public void setTeam(List<PokemonInstance> team) {
        runData.setTeam(team);
    }

    public void startRun(List<PokemonInstance> team, int startFloor) {
        runData.setTeam(team);
        enterFloor(startFloor);
    }

    public List<Option> getAvailableOptions() {
        return runData.getAvailableOptions();
    }

    public Option getBossOption() {
        return runData.getBossOption();
    }

    public boolean hasAvailableOptions() {
        return runData.getAvailableOptions() != null && !runData.getAvailableOptions().isEmpty();
    }

    public void selectOption(Option chosen) {
        if (!consumeOption(chosen)) {
            return;
        }
        resolveOptionEffect(chosen);
        if (isPointsExhausted()) {
            triggerBossFight();
        }
    }

    /**
     * 剩余点数是否已无法继续消费：点数归零，或剩余可选事件都买不起。
     *
     * <p>起始点数（{@code 8 + 层号 * 3 + 0~4}）与事件消耗不保证整除：本层若没刷出 1 点的
     * 「野怪遭遇」，可反复选择的只剩 3 点的「训练家挑战」，点数会停在 1 或 2 点——既买不起
     * 任何事件，也永远走不到「点数归零」。故把「买不起任何事件」与「点数归零」一并视为
     * 本层事件阶段结束，否则玩家会卡死在楼层页。</p>
     */
    public boolean isPointsExhausted() {
        return runData.getCurrentPoints() <= 0 || !hasAffordableOption();
    }

    /** 是否还存在「可选中且当前点数买得起」的事件（0 成本的隐藏事件不计）。 */
    public boolean hasAffordableOption() {
        List<Option> options = runData.getAvailableOptions();
        if (options == null) {
            return false;
        }
        for (Option option : options) {
            if (isSelectable(option) && option.getCost() <= runData.getCurrentPoints()) {
                return true;
            }
        }
        return false;
    }

    /** 事件是否可被玩家选中：非空、非隐藏事件，且属于本层可选列表或本层 BOSS。 */
    private boolean isSelectable(Option option) {
        if (option == null || option.isHiddenEvent()) {
            return false;
        }
        if (runData.getBossOption() != null && option == runData.getBossOption()) {
            return true;
        }
        List<Option> options = runData.getAvailableOptions();
        return options != null && options.contains(option);
    }

    /**
     * 只做点数校验/扣除与选项替换（不做事件效果结算、不自动触发 BOSS）：
     * 供 UI 接管战斗型事件（WILD/ENEMY 走真实战斗）时使用。
     *
     * @return 扣点成功返回 {@code true}；隐藏事件、非法选项或点数不足返回 {@code false}
     */
    public boolean consumeOption(Option chosen) {
        if (chosen == null) {
            return false;
        }
        if (chosen.isHiddenEvent()) {
            System.out.println("此处已被掩盖，无法再次选择。");
            return false;
        }
        if (runData.getAvailableOptions() == null || !runData.getAvailableOptions().contains(chosen)) {
            if (runData.getBossOption() != null && chosen == runData.getBossOption()) {
                // 允许直接强制选择 Boss，通常由 BOSS 判定触发
            } else {
                System.out.println("当前楼层中不存在该选项，无法执行。");
                return false;
            }
        }
        int nextPoints = runData.getCurrentPoints() - chosen.getCost();
        if (nextPoints < 0) {
            System.out.println("点数不足，无法选择：" + chosen.getName());
            return false;
        }

        runData.setCurrentPoints(nextPoints);
        replaceMysterySlot(chosen);
        return true;
    }

    private void replaceMysterySlot(Option chosen) {
        if (runData.getAvailableOptions() == null || chosen == null) {
            return;
        }
        if (chosen.getType() != OptionType.RANDOM && chosen.getType() != OptionType.HOSPITAL
                && chosen.getType() != OptionType.REWARD) {
            return;
        }
        int index = runData.getAvailableOptions().indexOf(chosen);
        if (index >= 0) {
            runData.getAvailableOptions().set(index, createMysteryReplacement());
        }
    }

    private Option createMysteryReplacement() {
        OptionType replacementType = random.nextBoolean() ? OptionType.RANDOM : OptionType.HOSPITAL;
        String detail = "新的神秘事件正在暗中生成……继续探索吧。";
        return new Option(Option.HIDDEN_EVENT_NAME, replacementType, 0, detail);
    }

    /** 执行选项的事件效果结算（WILD/ENEMY 为战斗模拟；HOSPITAL/RANDOM 为直接效果）。 */
    public void resolveOptionEffect(Option option) {
        if (option == null) {
            return;
        }

        switch (option.getType()) {
            case WILD -> resolveWildEvent();
            case ENEMY -> resolveEnemyEvent();
            case HOSPITAL -> resolveHospitalEvent();
            case RANDOM -> resolveRandomEvent();
            case REWARD -> System.out.println("装备补给事件：真实结算由控制器执行（随机装备入库）。");
            default -> System.out.println("未知事件：" + option.getName());
        }
    }

    private void resolveWildEvent() {
        System.out.println("遭遇野怪，开始野外战斗模拟...");
        if (runData.getTeam() == null || runData.getTeam().isEmpty()) {
            System.out.println("队伍为空，野怪事件无效。");
            return;
        }

        int totalDamage = 8 + random.nextInt(12);
        for (PokemonInstance pokemon : runData.getTeam()) {
            if (pokemon != null) {
                pokemon.setHp(Math.max(0, pokemon.getCurrentHp() - totalDamage / 2));
            }
        }
        System.out.println("野怪造成了 " + totalDamage + " 点总伤害，队伍受到波及。");
    }

    private void resolveEnemyEvent() {
        System.out.println("遭遇训练家，开始对战模拟...");
        if (runData.getTeam() == null || runData.getTeam().isEmpty()) {
            System.out.println("队伍为空，训练家事件无效。");
            return;
        }

        int damage = 10 + random.nextInt(15);
        PokemonInstance target = runData.getTeam().get(random.nextInt(runData.getTeam().size()));
        if (target != null) {
            target.setHp(Math.max(0, target.getCurrentHp() - damage));
        }
        System.out.println("训练家攻击命中，造成 " + damage + " 点伤害。");
    }

    private void resolveHospitalEvent() {
        System.out.println("进入医院，恢复全队状态...");
        if (runData.getTeam() != null) {
            for (PokemonInstance pokemon : runData.getTeam()) {
                if (pokemon != null) {
                    pokemon.setHp(pokemon.getMaxHp());
                }
            }
        }
    }

    private void resolveRandomEvent() {
        int floor = runData.getCurrentFloor();
        int base = randomEventBaseExp(floor);
        int spread = randomEventExpSpread(floor);
        System.out.println("神秘礼物：全队获得 " + base + "~" + (base + spread - 1) + " 点经验值！");
        if (runData.getTeam() != null) {
            for (PokemonInstance pokemon : runData.getTeam()) {
                if (pokemon != null && pokemon.getPokemon() != null) {
                    pokemon.getPokemon().addExp(base + random.nextInt(spread));
                }
            }
        }
    }

    /**
     * 神秘礼物的经验下限：第 1 层为 25，此后每层 +10。
     *
     * <p>事件消耗为 {@code 2 + 层号}，收益若固定不变则越深越不值；故下限随层号线性提高。
     * 注意升到下一级所需经验为 {@code 3n² + 3n + 1}（随等级二次增长），
     * 所以深层相对收益仍会缓慢下降——这是刻意的：经验主要由战斗产出，本事件只是补充。</p>
     */
    public static int randomEventBaseExp(int floor) {
        return 25 + (Math.max(1, floor) - 1) * 10;
    }

    /**
     * 神秘礼物的经验随机宽度：第 1 层为 30，此后每层 +5。
     * 即每只精灵实际到手 {@code [下限, 下限 + 宽度 - 1]} 区间内的独立随机值。
     */
    public static int randomEventExpSpread(int floor) {
        return 30 + (Math.max(1, floor) - 1) * 5;
    }

    public void triggerBossFight() {
        runData.setCurrentPoints(0); // 买不起任何事件时也归零：本层事件阶段到此结束
        System.out.println("⚔️ 点数耗尽！强制进入第 " + runData.getCurrentFloor() + " 层 BOSS 战！");
        if (runData.getBossOption() == null) {
            System.out.println("当前层没有 BOSS 配置，直接进入下一层。");
            enterFloor(runData.getCurrentFloor() + 1);
            return;
        }

        boolean bossWin = resolveBossEncounter(runData.getBossOption());
        if (bossWin) {
            System.out.println("BOSS 战胜利，进入下一层。");
            enterFloor(runData.getCurrentFloor() + 1);
            return;
        }

        runData.setGameOver(true);
        System.out.println("BOSS 战失败，游戏结束。");
    }

    private boolean resolveBossEncounter(Option bossOption) {
        if (bossOption == null) {
            return true;
        }

        int bossPower = 20 + runData.getCurrentFloor() * 6;
        int totalTeamHp = 0;
        int teamCurrentHp = 0;
        if (runData.getTeam() != null) {
            for (PokemonInstance pokemon : runData.getTeam()) {
                if (pokemon != null) {
                    totalTeamHp += pokemon.getMaxHp();
                    teamCurrentHp += pokemon.getCurrentHp();
                }
            }
        }

        int bossScore = bossPower + random.nextInt(10);
        int teamScore = Math.max(1, teamCurrentHp * 2 + totalTeamHp / 4);
        boolean win = teamScore >= bossScore;
        if (!win) {
            System.out.println("Boss 造成压倒性打击，当前层队伍承受失败。");
        } else {
            System.out.println("Boss 被击败，队伍获得层间奖励。 ");
        }
        return win;
    }

    public boolean isGameOver() {
        return runData.isGameOver();
    }
}
