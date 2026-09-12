package org.example.demo;

import org.example.battle.BattleService;
import org.example.battle.BattleServices;
import org.example.data.GameData;
import org.example.integration.WildEncounter;
import org.example.model.Bag;
import org.example.model.Item;
import org.example.model.ItemCategory;
import org.example.model.ItemStack;
import org.example.model.MoveSlot;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Terrain;
import org.example.model.Weather;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

/**
 * 交互式控制台对战（临时演示驱动，不入库）。
 * 复用 BattleService 真实引擎，让玩家在终端输入指令游玩：
 * 1~4 出招 / 5 道具 / 6 换宠 / 7 逃跑 / 0 退出。
 */
public class BattleConsole {

    static final GameData g = GameData.instance();
    /** 注入给战斗模块的数据端口：战斗模块只消费，数据由本演示（组装层）提供。 */
    static final org.example.data.GameDataBattleDataPort BATTLE_DATA =
            new org.example.data.GameDataBattleDataPort(g);
    static final Scanner in = new Scanner(System.in, StandardCharsets.UTF_8);
    /** 注入给战斗模块的成长端口：经验 / 升级 / 学招 / 进化由成长模块判定。 */
    static final org.example.growth.GrowthService GROWTH = new org.example.growth.GrowthService(BATTLE_DATA);
    static Player player;
    static int wins = 0, losses = 0, caught = 0, fled = 0;

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(
                new java.io.FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.out.println("============== PokeRouge 交互战斗演示 ==============");
        System.out.println("  [1-4] 出招   [5] 道具   [6] 换宠   [7] 逃跑   [0] 退出");
        buildStarter();

        while (true) {
            oneWildBattle();
            System.out.println("\n──── 本场结束 ────────────────────────────────");
            if (!player.hasHealthyPokemon()) {
                System.out.println("队伍全员倒下，已送回精灵中心自动恢复。");
                player.healParty();
            }
            System.out.print("回车 = 继续遇敌   [R] 回精灵中心补满   [Q] 退出 > ");
            String c = read().trim().toUpperCase();
            if (c.equals("Q")) {
                break;
            }
            if (c.equals("R")) {
                player.healParty();
                System.out.println("全体精灵已恢复至满状态。");
            }
        }
        System.out.printf("\n战绩：胜 %d / 负 %d / 捕捉 %d / 逃跑 %d %n", wins, losses, caught, fled);
        System.out.println("再见，训练家！");
    }

    // ------------------------------------------------------------------
    // 开局队伍与背包
    // ------------------------------------------------------------------

    static void buildStarter() {
        player = new Player("训练家");
        addStarter("s_fire_cat", 10);
        addStarter("s_leaf_chick", 10);
        addStarter("s_spark_rat", 10);
        Bag bag = player.getBag();
        bag.add(g.item("i_potion"), 5);
        bag.add(g.item("i_great_ball"), 3);
        bag.add(g.item("i_ultra_ball"), 2);
        bag.add(g.item("i_master_ball"), 1);
        System.out.println("队伍：火猫 / 草鸡 / 电鼠 Lv10，背包附赠 5 伤药 + 3 高级球 + 2 超级球 + 1 大师球");
    }

    static void addStarter(String speciesId, int level) {
        player.addToParty(g.createPokemon(speciesId, level)
                .orElseThrow(() -> new IllegalStateException("数据缺失: " + speciesId)));
    }

    // ------------------------------------------------------------------
    // 一场野生对战
    // ------------------------------------------------------------------

    static void oneWildBattle() {
        if (!player.hasHealthyPokemon()) {
            player.healParty();
        }
        int top = player.getParty().stream().mapToInt(Pokemon::getLevel).max().orElse(5);
        Pokemon wild = WildEncounter.randomWild(WildEncounter.levelAround(top), BATTLE_DATA)
                .orElseThrow(() -> new IllegalStateException("野生池为空"));
        player.leadWithFirstHealthy();
        BattleService b = BattleServices.newBattle(player, wild, BATTLE_DATA, GROWTH);

        System.out.println("\n野生的 " + wild.getSpecies().getName() + " Lv" + wild.getLevel() + " 出现了！");
        System.out.println("你派出了 " + player.getActive().getName() + "！");

        while (b.isOngoing()) {
            settlePending(b);
            if (!b.isOngoing()) {
                break;
            }
            if (b.isAwaitingReplacement()) {
                doReplacement(b); // 出战精灵倒下：必须先选出下一只才能继续
                continue;
            }
            render(b);
            takeAction(b);
        }
        settlePending(b);
        tally(b);
        System.out.println("战斗结束：" + b.getStatus());
    }

    /** 画当前战况。 */
    static void render(BattleService b) {
        System.out.println("────────────────────────────────────────────");
        if (b.getWeather() != Weather.NONE) {
            System.out.println("  天气：" + b.getWeather());
        }
        if (b.getTerrain() != Terrain.NONE) {
            System.out.println("  场地：" + b.getTerrain());
        }
        Pokemon ac = b.playerActive();
        if (ac != null) {
            System.out.printf("我方 %s Lv%d  HP %d/%d%n",
                    ac.getName(), ac.getLevel(), ac.getCurrentHp(), ac.getMaxHp());
            List<MoveSlot> slots = ac.getMoveSlots();
            for (int i = 0; i < slots.size(); i++) {
                MoveSlot s = slots.get(i);
                String pp = s.exhausted()
                        ? "PP枯竭"
                        : "PP " + s.getPp() + "/" + s.getMove().getMaxPp();
                System.out.printf("  [%d] %-8s %s%n", i + 1, s.getMove().getName(), pp);
            }
        }
        Pokemon wild = b.getWild();
        System.out.printf("野生 %s Lv%d  HP %d/%d%n",
                wild.getSpecies().getName(), wild.getLevel(),
                wild.getCurrentHp(), wild.getMaxHp());
    }

    /** 主行动菜单：循环直到完成一次有效行动（消耗本回合）。 */
    static void takeAction(BattleService b) {
        while (true) {
            System.out.print("行动 > ");
            String raw = read().trim();
            switch (raw) {
                case "1", "2", "3", "4" -> {
                    if (doMove(b, raw)) {
                        return;
                    }
                }
                case "5" -> {
                    if (doItem(b)) {
                        return;
                    }
                }
                case "6" -> doSwitch(b);
                case "7" -> {
                    printLogs(b.tryRun());
                    return;
                }
                case "0", "Q", "q" -> {
                    System.out.println("退出游戏。");
                    System.exit(0);
                }
                default -> System.out.println("无效输入，请重新输入。");
            }
        }
    }

    /** 使用第 n 号技能（1~4）。失败不消耗回合。 */
    static boolean doMove(BattleService b, String no) {
        Pokemon ac = b.playerActive();
        List<MoveSlot> slots = ac.getMoveSlots();
        int idx = Integer.parseInt(no) - 1;
        if (idx < 0 || idx >= slots.size()) {
            System.out.println("没有这个技能。");
            return false;
        }
        MoveSlot s = slots.get(idx);
        if (s.exhausted()) {
            System.out.println(s.getMove().getName() + " PP 不足，无法使用。");
            return false;
        }
        printLogs(b.useMove(s));
        return true;
    }

    /** 背包子菜单：选一个道具使用（消耗本回合）。选 c 或非法输入不消耗。 */
    static boolean doItem(BattleService b) {
        List<ItemStack> stacks = b.getBag().availableStacks();
        if (stacks.isEmpty()) {
            System.out.println("背包已经空了！");
            return false;
        }
        System.out.println("── 背包 ──────────────────────────────");
        for (int i = 0; i < stacks.size(); i++) {
            Item it = stacks.get(i).getItem();
            String desc;
            if (it.getCategory() == ItemCategory.HEAL) {
                desc = "回复 HP " + (int) it.getEffect();
            } else if (it.isAlwaysCatch()) {
                desc = "必定捕捉";
            } else {
                desc = "捕捉倍率 ×" + it.getEffect();
            }
            System.out.printf("  [%d] %-10s ×%d  (%s)%n",
                    i, it.getName(), stacks.get(i).getCount(), desc);
        }
        System.out.print("  输入编号使用，[c] 返回 > ");
        String raw = read().trim().toLowerCase();
        if (raw.equals("c")) {
            return false;
        }
        int idx;
        try {
            idx = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return false;
        }
        if (idx < 0 || idx >= stacks.size()) {
            return false;
        }
        Item chosen = stacks.get(idx).getItem();
        if (chosen.getCategory() == ItemCategory.POKE_BALL) {
            // 精灵球始终投向敌方野生精灵（与队伍目标无关）
            printLogs(b.useItem(chosen));
            return true;
        }
        // 回复/解除道具：再选作用目标（可为队伍任意精灵，含替补）
        List<Pokemon> party = b.getPlayer().getParty();
        Pokemon cur = b.playerActive();
        System.out.println("── 目标 ──────────────────────────────");
        for (int i = 0; i < party.size(); i++) {
            Pokemon p = party.get(i);
            String tag = p == cur ? "(出战)" : (p.isFainted() ? "(倒下)" : "");
            System.out.printf("  [%d] %-8s Lv%d  HP %d/%d  %s%n",
                    i + 1, p.getName(), p.getLevel(), p.getCurrentHp(), p.getMaxHp(), tag);
        }
        System.out.print("  输入目标编号，[c] 返回 > ");
        String pick = read().trim().toLowerCase();
        if (pick.equals("c")) {
            return false;
        }
        int target;
        try {
            target = Integer.parseInt(pick) - 1;
        } catch (NumberFormatException e) {
            return false;
        }
        printLogs(b.useItem(chosen, target));
        return true;
    }

    /** 补位子菜单：己方出战精灵倒下后强制选出下一只上场精灵（不消耗回合）。 */
    static void doReplacement(BattleService b) {
        List<Pokemon> party = b.getPlayer().getParty();
        System.out.println("── 请选择接下来上场的精灵 ──────────────");
        for (int i = 0; i < party.size(); i++) {
            Pokemon p = party.get(i);
            String tag = p.isFainted() ? "(倒下)" : "";
            System.out.printf("  [%d] %-8s Lv%d  HP %d/%d  %s%n",
                    i + 1, p.getName(), p.getLevel(), p.getCurrentHp(), p.getMaxHp(), tag);
        }
        System.out.print("  输入编号上场 > ");
        int idx;
        try {
            idx = Integer.parseInt(read().trim()) - 1;
        } catch (NumberFormatException e) {
            return; // 输入非法：重新提示（战斗仍处于等待补位状态）
        }
        printLogs(b.chooseReplacement(idx)); // 引擎自行校验下标/倒下，非法时保持等待状态
    }

    /** 换宠子菜单：选目标精灵切换。不消耗回合时返回继续主菜单。 */
    static void doSwitch(BattleService b) {
        Pokemon cur = b.playerActive();
        List<Pokemon> party = b.getPlayer().getParty();
        System.out.println("── 队伍 ──────────────────────────────");
        for (int i = 0; i < party.size(); i++) {
            Pokemon p = party.get(i);
            String tag = p == cur ? "(出战)" : (p.isFainted() ? "(倒下)" : "");
            System.out.printf("  [%d] %-8s Lv%d  HP %d/%d  %s%n",
                    i + 1, p.getName(), p.getLevel(), p.getCurrentHp(), p.getMaxHp(), tag);
        }
        System.out.print("  输入编号切换，[0] 返回 > ");
        int idx;
        try {
            idx = Integer.parseInt(read().trim()) - 1;
        } catch (NumberFormatException e) {
            return;
        }
        if (idx < 0 || idx >= party.size()) {
            return;
        }
        Pokemon target = party.get(idx);
        if (target == cur) {
            System.out.println("它已经在场上了。");
        } else if (target.isFainted()) {
            System.out.println("倒下的精灵不能上场。");
        } else {
            printLogs(b.switchActive(idx));
        }
    }

    /** 战后/升级触发的学招抉择：满 4 招时逐个询问玩家。 */
    static void settlePending(BattleService b) {
        while (!b.pendingLearnChoices().isEmpty()) {
            BattleService.LearnChoice c = b.pendingLearnChoices().get(0);
            Pokemon p = c.pokemon();
            System.out.println(p.getName() + " 想学新技能【" + c.move().getName()
                    + "】，但 4 招已满，请选择要遗忘的招数：");
            List<MoveSlot> slots = p.getMoveSlots();
            for (int i = 0; i < slots.size(); i++) {
                System.out.printf("  [%d] 遗忘 %s%n", i, slots.get(i).getMove().getName());
            }
            System.out.print("  输入 0~3 遗忘指定招数，[-1] 放弃学习 > ");
            int idx;
            try {
                idx = Integer.parseInt(read().trim());
            } catch (NumberFormatException e) {
                idx = -1;
            }
            printLogs(b.decideLearn(idx >= 0 && idx <= 3 ? idx : -1));
        }
    }

    static void tally(BattleService b) {
        switch (b.getStatus()) {
            case PLAYER_WIN -> wins++;
            case PLAYER_LOSE -> losses++;
            case CAUGHT -> caught++;
            case FLED -> fled++;
            default -> {
            }
        }
    }

    static void printLogs(List<String> lines) {
        for (String s : lines) {
            System.out.println("  · " + s);
        }
    }

    static String read() {
        try {
            return in.nextLine();
        } catch (Exception e) {
            // 输入流关闭/EOF 时视为退出
            return "Q";
        }
    }
}
