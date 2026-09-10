package org.example.view;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.battle.BattleEvent;
import org.example.battle.BattleService;
import org.example.battle.BattleServices;
import org.example.controller.BattleController;
import org.example.model.ElementType;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.MoveSlot;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stats;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 战斗演出冒烟测试（默认跳过，需显式开启）：
 *
 * <pre>mvn -o test -Dtest=BattleAnimationSmokeTest -Dbattle.smoke=true -DfailIfNoSpecifiedTests=false</pre>
 *
 * <p>验证两件无法靠纯逻辑单测保证的事：① 全部事件类型的动画都能在真实 JavaFX 工具包中
 * 播完并触发完成回调（串联的 {@code setOnFinished} 不会断链）；② 控制器在动画结束后会解锁
 * 行动输入，不会把界面永久锁死。同时真实走一遍布局，确保特效层的坐标换算（{@code sceneToLocal}）
 * 在场景被放进 {@link Stage} 并完成布局后不会抛异常。</p>
 */
@EnabledIfSystemProperty(named = "battle.smoke", matches = "true")
class BattleAnimationSmokeTest {

    private static final Move TACKLE = new Move("m_tackle", "撞击", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 40, 100, 40);
    private static final Move EMBER = new Move("m_ember", "火花", ElementType.FIRE,
            MoveCategory.SPECIAL, 40, 100, 40);

    private static Stage stage;

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        }
        assertTrue(started.await(20, TimeUnit.SECONDS), "JavaFX 工具包应能启动");
    }

    @AfterAll
    static void stopToolkit() throws Exception {
        if (stage != null) {
            onFx(() -> stage.close());
        }
        Platform.exit();
    }

    private static Species species(String id, int hp, int atk, int spe) {
        return new Species(id, id, ElementType.NORMAL, null,
                new Stats(hp, atk, 40, atk, 40, spe), 51, List.of(), null, 0, Map.of());
    }

    /** 在 FX 线程执行并等待完成。 */
    private static void onFx(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(20, TimeUnit.SECONDS), "FX 任务未在超时内完成");
    }

    /** 把场景装进真实窗口并完成一次布局（特效层坐标换算依赖布局结果）。 */
    private static void showOnStage(Scene scene) throws Exception {
        onFx(() -> {
            stage = new Stage();
            stage.setScene(scene);
            stage.show();
        });
        Thread.sleep(600);
    }

    @Test
    void 全部事件类型的动画都能播完并回调() throws Exception {
        final BattleView[] holder = new BattleView[1];
        onFx(() -> holder[0] = new BattleView(new NoopActions()));
        showOnStage(holder[0].createScene());

        List<BattleEvent> all = List.of(
                BattleEvent.battleStart(),
                BattleEvent.sendOut(BattleEvent.Side.FOE, "野生精灵"),
                BattleEvent.sendOut(BattleEvent.Side.PLAYER, "伙伴"),
                BattleEvent.move(BattleEvent.Side.PLAYER, "伙伴", EMBER),
                BattleEvent.hit(BattleEvent.Side.FOE, "野生精灵", ElementType.FIRE, MoveCategory.SPECIAL),
                BattleEvent.faint(BattleEvent.Side.FOE, "野生精灵"),
                BattleEvent.sendOut(BattleEvent.Side.FOE, "下一只"),
                BattleEvent.move(BattleEvent.Side.PLAYER, "伙伴", null),
                BattleEvent.recall(BattleEvent.Side.PLAYER, "伙伴"),
                BattleEvent.item("伤药"),
                BattleEvent.capture("精灵球", true),
                BattleEvent.capture("精灵球", false),
                BattleEvent.run(false),
                BattleEvent.run(true));

        CountDownLatch finished = new CountDownLatch(1);
        onFx(() -> holder[0].playEvents(all, finished::countDown));

        assertTrue(finished.await(60, TimeUnit.SECONDS), "全部事件动画应在超时内播完并回调");
    }

    @Test
    void 演出播完后行动输入解锁并可继续出手() throws Exception {
        Player player = new Player("玩家");
        Pokemon mine = Pokemon.create(species("mine_sp", 400, 200, 300), 20, List.of(TACKLE, EMBER));
        player.addPokemon(mine);
        Pokemon wild = Pokemon.create(species("wild_sp", 900, 20, 10), 20, List.of(TACKLE));
        BattleService engine = BattleServices.newBattle(player, wild, new Random(7));

        final BattleController[] holder = new BattleController[1];
        final Scene[] scenes = new Scene[1];
        onFx(() -> {
            holder[0] = new BattleController(engine, () -> { }, 1);
            scenes[0] = holder[0].createScene();
        });
        showOnStage(scenes[0]);

        // createScene() 会播放入场动画；先等它结束，确认输入已解锁
        Thread.sleep(2000);
        int before = engine.getLog().size();
        MoveSlot slot = mine.getMoveSlots().get(0);
        onFx(() -> holder[0].onMoveSelected(slot));
        Thread.sleep(2000);

        assertTrue(engine.getLog().size() > before,
                "入场动画结束后行动应能生效（否则说明完成回调未触发、输入被永久锁死）: " + engine.getLog());
        assertTrue(engine.getLog().stream().anyMatch(line -> line.contains("使用了")),
                "日志应含出招记录: " + engine.getLog());

        int afterFirst = engine.getLog().size();
        onFx(() -> holder[0].onMoveSelected(slot));
        Thread.sleep(2000);

        assertTrue(engine.getLog().size() > afterFirst,
                "上一回合演出播完后应能再次行动: " + engine.getLog());
    }

    /** 冒烟测试只关心渲染与动画，不触发任何业务动作。 */
    private static final class NoopActions implements BattleView.Actions {

        @Override
        public void onMoveSelected(MoveSlot slot) {
        }

        @Override
        public void onItemSelected(int stackIndex) {
        }

        @Override
        public void onSwitchSelected(int partyIndex) {
        }

        @Override
        public void onRun() {
        }

        @Override
        public void onExit() {
        }
    }
}
