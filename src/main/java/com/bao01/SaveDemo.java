package com.bao01;

import com.bao01.config.Items;
import com.bao01.config.SamplePokemon;
import com.bao01.model.Bag;
import com.bao01.model.Battle;
import com.bao01.model.BattleAction;
import com.bao01.model.Pokemon;
import com.bao01.model.Team;
import com.bao01.save.SaveData;
import com.bao01.save.SaveManager;

import java.nio.file.Path;
import java.util.List;

/** 存档系统无头验证：进行若干回合 → 中途存档 → 文本/磁盘回环 → 恢复继续对战。 */
public final class SaveDemo {

    private SaveDemo() {
    }

    public static void main(String[] args) {
        Pokemon fox = SamplePokemon.flameFox(50);
        Pokemon lizard = SamplePokemon.vineLizard(50);
        Battle b = new Battle(
                new Team(List.of(fox), Bag.startingBag()),
                new Team(List.of(lizard), new Bag()),
                Battle.Opponent.WILD);

        // 制造非平凡状态：回合 1 大晴天，回合 2 火花攻击（敌方自动行动）
        b.resolve(new BattleAction.Attack(moveOf(fox, "大晴天")));
        b.resolve(new BattleAction.Attack(moveOf(fox, "火花")));
        System.out.println("对局状态：回合 " + b.roundNo() + "，天气 "
                + b.weather() + " 剩余 " + b.weatherTurnsLeft() + "，是否结束 " + b.isOver());

        // 1) 文本编解码
        SaveData.World world = SaveManager.captureBattle(b);
        String text = SaveManager.toText(world);
        SaveData.World parsed = SaveManager.fromText(text);

        // 2) 恢复战斗，比对关键状态
        Battle restored = SaveManager.restoreBattle(parsed);
        boolean stateOk = restored.roundNo() == b.roundNo()
                && restored.weather() == b.weather()
                && restored.weatherTurnsLeft() == b.weatherTurnsLeft()
                && restored.playerActive().getName().equals(b.playerActive().getName())
                && restored.playerActive().currentHp() == b.playerActive().currentHp();
        System.out.println("恢复状态一致: " + stateOk);

        // 3) 磁盘 IO
        Path tmp = Path.of("target", "save_demo.txt");
        SaveManager.writeToFile(tmp, world);
        SaveData.World fromDisk = SaveManager.readFromFile(tmp);
        Battle resumed2 = SaveManager.restoreBattle(fromDisk);
        System.out.println("磁盘读取一致: " + SaveManager.toText(fromDisk).equals(text));

        // 4) 恢复后继续对战（证明可玩）
        int guard = 0;
        while (!resumed2.isOver() && guard < 30) {
            resumed2.resolveAuto();
            guard++;
        }
        System.out.println("恢复后继续对战 " + guard + " 回合，结果 " + resumed2.outcome());

        // 5) 世界存档（队伍+背包）回环
        SaveData.World w2 = SaveManager.captureWorld(resumed2.player());
        Team t2 = SaveManager.restorePlayerTeam(w2);
        Pokemon p0 = t2.member(0);
        System.out.println("世界存档回环: 出战 " + t2.active().getName()
                + " HP " + p0.currentHp() + "/" + p0.maxHp()
                + " 背包(伤药) " + t2.bag().count(Items.POTION));
    }

    private static int moveOf(Pokemon p, String name) {
        for (int i = 0; i < p.moveCount(); i++) {
            if (p.move(i).getName().equals(name)) {
                return i;
            }
        }
        throw new IllegalStateException("招式不存在: " + name);
    }
}
