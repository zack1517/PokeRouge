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
        if (runData.getCurrentPoints() <= 0) {
            triggerBossFight();
        }
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
        if ("隐藏事件".equals(chosen.getName())) {
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
        if (chosen.getType() != OptionType.RANDOM && chosen.getType() != OptionType.HOSPITAL) {
            return;
        }
        int index = runData.getAvailableOptions().indexOf(chosen);
        if (index >= 0) {
            runData.getAvailableOptions().set(index, createMysteryReplacement());
        }
    }

    private Option createMysteryReplacement() {
        OptionType replacementType = random.nextBoolean() ? OptionType.RANDOM : OptionType.HOSPITAL;
        String title = "隐藏事件";
        String detail = "新的神秘事件正在暗中生成……继续探索吧。";
        return new Option(title, replacementType, 0, detail);
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
        System.out.println("神秘礼物：你获得了宝贵的经验值奖励！");
        if (runData.getTeam() != null) {
            for (PokemonInstance pokemon : runData.getTeam()) {
                if (pokemon != null && pokemon.getPokemon() != null) {
                    pokemon.getPokemon().addExp(25 + random.nextInt(30));
                }
            }
        }
    }

    public void triggerBossFight() {
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
