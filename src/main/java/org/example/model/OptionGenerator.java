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
        // 临时急救站为本层必刷：它承担全队续航，缺失时容易出现连续损血却无处恢复的楼层。
        Option hospital = new Option("临时急救站", OptionType.HOSPITAL, 2 + floor, "恢复全队精灵HP，并重整作战状态。");

        List<Option> others = new ArrayList<>();
        others.add(new Option("野怪遭遇", OptionType.WILD, 1, "遭遇野生精灵，立即进入野怪战斗。"));
        others.add(new Option("训练家挑战", OptionType.ENEMY, 3, "挑战训练家，立即进入训练家对战。"));
        others.add(new Option("神秘礼物", OptionType.RANDOM, 2 + floor, "获得经验值奖励（奖励随层数增长），提升队伍成长。"));
        others.add(new Option("装备补给", OptionType.REWARD, 2 + floor, "随机获得一件可携带装备，可穿戴给精灵。"));
        Collections.shuffle(others, random);

        List<Option> options = new ArrayList<>(others.subList(0, 3));
        options.add(hospital);
        Collections.shuffle(options, random); // 位置同样随机，急救站不会固定落在同一格
        return options;
    }

    public Option createBossOption(int floor) {
        return new Option("BOSS: 终极挑战", OptionType.ENEMY, 6 + floor, "击败当前层数的Boss，获得本层通关奖励。" );
    }
}
