package org.example.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class OptionGenerator {
    private final Random random = new Random();

    public FloorData generateFloor(int floor) {
        int startingPoints = 8 + floor * 3 + random.nextInt(5);
        List<Option> options = createOptions(floor);
        Option bossOption = createBossOption(floor);
        return new FloorData(floor, startingPoints, options, bossOption);
    }

    public List<Option> createOptions(int floor) {
        List<Option> candidates = new ArrayList<>();
        candidates.add(new Option("野怪遭遇", OptionType.WILD, 1, "遭遇野生精灵，立即进入野怪战斗。"));
        candidates.add(new Option("训练家挑战", OptionType.ENEMY, 3, "挑战训练家，立即进入训练家对战。"));
        candidates.add(new Option("神秘礼物", OptionType.RANDOM, 2 + floor, "获得宝贵经验值奖励，提升队伍成长。"));
        candidates.add(new Option("临时急救站", OptionType.HOSPITAL, 2 + floor, "恢复全队精灵HP，并重整作战状态。"));
        Collections.shuffle(candidates, random);
        return new ArrayList<>(candidates.subList(0, 4));
    }

    public Option createBossOption(int floor) {
        return new Option("BOSS: 终极挑战", OptionType.ENEMY, 6 + floor, "击败当前层数的Boss，获得本层通关奖励。" );
    }
}
