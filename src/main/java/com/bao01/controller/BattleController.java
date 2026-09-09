package com.bao01.controller;

import com.bao01.config.SamplePokemon;
import com.bao01.model.Bag;
import com.bao01.model.Battle;
import com.bao01.model.BattleAction;
import com.bao01.model.Item;
import com.bao01.model.Pokemon;
import com.bao01.model.Team;
import com.bao01.save.SaveData;
import com.bao01.save.SaveException;
import com.bao01.save.SaveManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 对战控制器：持有当前对局，负责开新对局与提交玩家行动。
 * 不依赖任何 JavaFX 类型，可独立测试。
 */
public final class BattleController {

    /** 对局模式：野生对战（可逃跑）或训练家对战（不可逃跑）。 */
    public enum Mode {
        WILD, TRAINER
    }

    /** 默认对战等级。 */
    public static final int LEVEL = 50;

    private Battle battle;
    private Mode mode;

    /** 开局并返回首次对白（出战提示）。 */
    public List<String> start(Mode mode) {
        this.mode = mode;
        Team player = buildPlayerTeam();
        Team foe = buildFoeTeam(mode);
        battle = new Battle(player, foe, mode == Mode.WILD
                ? Battle.Opponent.WILD : Battle.Opponent.TRAINER);
        List<String> open = new ArrayList<>();
        open.add("🎮 战斗开始！你与" + battle.opponent().getLabel() + "展开了对战。");
        if (mode == Mode.WILD) {
            open.add("前方出现了野生的 " + foe.active().getName() + "！");
        } else {
            open.add("对方派出了 " + foe.active().getName() + "！");
        }
        open.add("我方派出了 " + player.active().getName() + "！");
        return open;
    }

    /** 提交玩家本回合行动，返回本回合日志。 */
    public List<String> act(BattleAction action) {
        return battle.resolve(action).logs();
    }

    /** 玩家濒死后派出替补。 */
    public List<String> sendOut(int memberIndex) {
        return battle.sendOut(memberIndex).logs();
    }

    /** 击倒对方后免费轮换到替补（index 为我方队伍下标）。 */
    public List<String> koSwitch(int memberIndex) {
        return battle.koSwitch(memberIndex).logs();
    }

    /** 击倒对方后选择不轮换、继续战斗。 */
    public List<String> koSwitchKeep() {
        return battle.koSwitchKeep().logs();
    }

    public Battle battle() {
        return battle;
    }

    public Mode mode() {
        return mode;
    }

    public boolean isOver() {
        return battle != null && battle.isOver();
    }

    public boolean isKoSwitchPending() {
        return battle != null && battle.isPlayerKoSwitchPending();
    }

    public boolean isWild() {
        return battle != null && battle.isWildBattle();
    }

    // ---------- 队伍构建 ----------

    private Team buildPlayerTeam() {
        List<Pokemon> mons = new ArrayList<>();
        mons.add(SamplePokemon.flameFox(LEVEL));
        mons.add(SamplePokemon.waveFrog(LEVEL));
        mons.add(SamplePokemon.vineLizard(LEVEL));
        mons.add(SamplePokemon.thunderBird(LEVEL));
        mons.add(SamplePokemon.rockTurtle(LEVEL));
        mons.add(SamplePokemon.frostFox(LEVEL));
        return new Team(mons, Bag.startingBag());
    }

    private Team buildFoeTeam(Mode mode) {
        List<Pokemon> mons = new ArrayList<>();
        if (mode == Mode.WILD) {
            mons.add(SamplePokemon.rockTurtle(LEVEL + 5));
        } else {
            mons.add(SamplePokemon.vineLizard(LEVEL + 5));
            mons.add(SamplePokemon.thunderBird(LEVEL + 5));
            mons.add(SamplePokemon.waveFrog(LEVEL + 5));
            mons.add(SamplePokemon.rockTurtle(LEVEL + 5));
            mons.add(SamplePokemon.frostFox(LEVEL + 5));
        }
        return new Team(mons, new Bag());
    }

    /** 供界面查看我方队伍里可用的对战道具。 */
    public List<Item> usableItems() {
        List<Item> items = new ArrayList<>();
        if (battle != null) {
            battle.player().bag().items().keySet().stream()
                    .filter(Item::isBattleUsable)
                    .forEach(items::add);
        }
        return items;
    }

    // ---------- 存档 / 读档（战斗续档） ----------

    /** 将当前对局连同玩家队伍写入默认存档文件，返回界面提示。 */
    public List<String> save() {
        if (battle == null) {
            return List.of("当前没有进行中的对局。");
        }
        Path path = SaveManager.defaultPath();
        try {
            SaveManager.writeToFile(path, SaveManager.captureBattle(battle));
            return List.of("💾 存档成功：" + path.toAbsolutePath());
        } catch (SaveException e) {
            return List.of("⚠ 存档失败：" + e.getMessage());
        }
    }

    /** 从默认存档文件恢复对局（覆盖当前战斗），返回界面提示。 */
    public List<String> load() {
        Path path = SaveManager.defaultPath();
        SaveData.World world;
        try {
            world = SaveManager.readFromFile(path);
        } catch (SaveException e) {
            return List.of("⚠ 读档失败：" + e.getMessage());
        }
        if (!world.hasBattle()) {
            return List.of("存档中不含战斗快照（仅队伍），无法恢复对局。");
        }
        Battle restored = SaveManager.restoreBattle(world);
        this.battle = restored;
        this.mode = restored.isWildBattle() ? Mode.WILD : Mode.TRAINER;
        List<String> open = new ArrayList<>();
        open.add("📂 已读取存档：第 " + restored.roundNo() + " 回合"
                + (restored.hasWeather()
                        ? "，天气 " + restored.weather().getLabel()
                          + "（剩余 " + restored.weatherTurnsLeft() + " 回合）"
                        : "，无天气"));
        if (restored.isPlayerPendingSendout()) {
            open.add("请选择一只宝可梦上场。");
        } else if (restored.isPlayerKoSwitchPending()) {
            open.add("对方宝可梦倒下了，可决定是否轮换。");
        } else {
            open.add("请继续行动。");
        }
        return open;
    }

    // ---------- 天气测试场 ----------

    /**
     * 天气测试场：岩甲龟首发（携带沙暴），对手为草系沙包，
     * 可轮换 雪绒狐/碧波蛙/焰尾狐 分别测试 下雪/求雨/大晴天。
     */
    public List<String> startWeatherTest() {
        this.mode = Mode.WILD;
        List<Pokemon> playerMons = new ArrayList<>();
        playerMons.add(SamplePokemon.rockTurtle(LEVEL));   // 沙暴
        playerMons.add(SamplePokemon.frostFox(LEVEL));     // 下雪
        playerMons.add(SamplePokemon.waveFrog(LEVEL));     // 求雨
        playerMons.add(SamplePokemon.flameFox(LEVEL));     // 大晴天
        Team player = new Team(playerMons, Bag.startingBag());
        Team foe = new Team(List.of(SamplePokemon.vineLizard(LEVEL + 3)), new Bag());
        battle = new Battle(player, foe, Battle.Opponent.WILD);
        List<String> open = new ArrayList<>();
        open.add("🌦 天气测试场（对手：青藤蜥 Lv." + (LEVEL + 3) + " 草系沙包）");
        open.add("首发 岩甲龟·岩：使用『沙暴』→ 对手每回合受 1/16 环境伤害，本系免疫");
        open.add("轮换测试：雪绒狐『下雪』冰系物防×1.5 · 碧波蛙『求雨』水系威力×1.5 · 焰尾狐『大晴天』火系威力×1.5");
        return open;
    }
}
