package org.example.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class FloorEventGenerator {
    private static final Random RANDOM = new Random();

    public FloorData generateFloorData(int floorNumber) {
        int startingPoints = 8 + floorNumber * 3 + RANDOM.nextInt(5);
        List<Option> options = generateOptions(floorNumber);
        Option bossOption = generateBossOption(floorNumber);
        return new FloorData(floorNumber, startingPoints, options, bossOption);
    }

    public List<Option> generateOptions(int floorNumber) {
        List<Option> candidates = new ArrayList<>();
        candidates.add(createOption("野怪遭遇", OptionType.WILD, 1, "遭遇野生精灵，立即进入野怪战斗。"));
        candidates.add(createOption("训练家挑战", OptionType.ENEMY, 3, "挑战训练家，立即进入训练家对战。"));
        candidates.add(createOption("神秘礼物", OptionType.RANDOM, 2 + floorNumber, "获得宝贵经验值奖励，提升队伍成长。"));
        candidates.add(createOption("临时急救站", OptionType.HOSPITAL, 2 + floorNumber, "恢复全队精灵HP，并重整作战状态。"));

        Collections.shuffle(candidates, RANDOM);
        return candidates.subList(0, 4);
    }

    public Option generateBossOption(int floorNumber) {
        return createOption("BOSS: 终极挑战", OptionType.ENEMY, 6 + floorNumber, "击败当前层数的首领，获得本层最终奖励与进阶资格。" );
    }

    private Option createOption(String name, OptionType type, int cost, String description) {
        return new Option(name, type, cost, description);
    }
}
