package org.example.save;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.example.GameSession;
import org.example.growth.GrowthProgress;
import org.example.integration.PokemonBattleAdapter;
import org.example.model.Item;
import org.example.model.ItemStack;
import org.example.model.Option;
import org.example.model.OptionType;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.PokemonInstance;
import org.example.model.RunData;
import org.example.model.RoutePhase;
import org.example.util.LogUtil;

/**
 * 存档编排：在「运行时对象」与「磁盘上的存档」之间搬运进度。
 *
 * <p>三件事：</p>
 * <ul>
 *   <li>{@link #snapshot} / {@link #save} —— 从 {@link Player} + {@link GameSession} 采集快照并落盘；</li>
 *   <li>{@link #load} —— 读盘后重建一支队伍、背包、肉鸽楼层进度与地图段号，并按档位载入图鉴成长；</li>
 *   <li>{@link #newGame} —— 新游戏：先清空该档位，再建一份图鉴从零开始的会话。</li>
 * </ul>
 *
 * <p>存档位之间彼此独立，具体文件布局见 {@link SaveStore}；本类只负责把内存对象与
 * {@link SaveData} 互相转换，不关心 UI。</p>
 */
public final class SaveManager {

    private final SaveStore store;

    public SaveManager(SaveStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** 使用默认存档目录（{@code <用户目录>/.pokerouge/saves}）的编排器。 */
    public static SaveManager defaultManager() {
        return new SaveManager(SaveStore.defaultStore());
    }

    /** 底层存档仓库（UI 需要它查询档位状态 / 删除存档）。 */
    public SaveStore store() {
        return store;
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    /**
     * 采集当前会话的完整快照（不落盘）。
     *
     * @param player  玩家（队伍 + 背包）
     * @param session 会话（肉鸽进度 + 地图段号 + 段背景）
     */
    public SaveData snapshot(Player player, GameSession session) {
        List<SaveData.PokemonData> party = new ArrayList<>();
        for (Pokemon pokemon : player.getParty()) {
            party.add(PokemonMapper.toData(pokemon));
        }
        List<SaveData.ItemData> bag = new ArrayList<>();
        for (ItemStack stack : player.getBag().getAll()) {
            if (!stack.isEmpty()) {
                bag.add(new SaveData.ItemData(stack.getItem().getId(), stack.getCount()));
            }
        }
        RunData run = session.getRogueRunData();
        List<SaveData.OptionData> options = new ArrayList<>();
        List<Option> available = session.getRogueOptions();
        if (available != null) {
            for (Option option : available) {
                options.add(toOptionData(option));
            }
        }
        SaveData.OptionData mandatory = run.getMandatoryOption() == null
                ? null : toOptionData(run.getMandatoryOption());
        return new SaveData(SaveData.FORMAT_VERSION, player.getName(), player.getActiveIndex(),
                party, bag,
                new SaveData.RunRecord(run.getAp(), run.getApMax(), run.getPhase().name(),
                        run.getRetryUsed(), run.getGold(), run.isGameOver(), run.isCleared()),
                options, mandatory, run.getSegment(), session.getMapBackground(),
                System.currentTimeMillis(),
                new SaveData.StoryRecord(run.isRocketLineUnlocked(), run.isRocketBossDefeated(),
                        run.isLegendaryMet(), run.isPendingLegendary(), run.isAggressionTriggered()));
    }

    /**
     * 把当前进度写入指定档位（覆盖该档位原有存档）。
     *
     * @throws java.io.UncheckedIOException 写盘失败，调用方应提示「保存失败」
     */
    public void save(SaveSlot slot, Player player, GameSession session) {
        store.write(slot, snapshot(player, session));
    }

    /**
     * 自动存档：失败只记日志、返回 {@code false}，不打断玩家当前流程。
     *
     * <p>用于「回到主菜单 / 推进楼层」这类玩家没有主动要求保存的时机 —— 存档目录不可写时
     * 不应弹窗打断游戏。</p>
     *
     * @return 是否保存成功
     */
    public boolean autoSave(SaveSlot slot, Player player, GameSession session) {
        try {
            save(slot, player, session);
            return true;
        } catch (RuntimeException e) {
            LogUtil.info("[SaveManager] 自动存档失败，本次进度仅存于内存：" + slot + "（" + e.getMessage() + "）");
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 读档
    // ------------------------------------------------------------------

    /**
     * 读取指定档位并重建会话。
     *
     * @return 该档位无存档、存档损坏，或队伍中所有精灵的物种都已不存在时返回空
     * @throws SaveFormatException 存档内容不合格式（调用方应按「存档损坏」提示）
     */
    public Optional<GameSession> load(SaveSlot slot) {
        Optional<SaveData> loaded = store.read(slot);
        if (loaded.isEmpty()) {
            return Optional.empty();
        }
        SaveData data = loaded.get();

        Player player = new Player(data.playerName().isBlank() ? "训练家" : data.playerName());
        boolean dropped = false;
        for (SaveData.PokemonData entry : data.party()) {
            Optional<Pokemon> pokemon = PokemonMapper.toPokemon(entry);
            if (pokemon.isEmpty()) {
                dropped = true;
                continue;
            }
            player.addPokemon(pokemon.get());
        }
        if (player.getParty().isEmpty()) {
            LogUtil.info("[SaveManager] 存档中没有任何可用精灵，无法继续：" + slot);
            return Optional.empty();
        }
        if (dropped) {
            LogUtil.info("[SaveManager] 存档中有精灵的物种已不存在，读档时已丢弃：" + slot);
        }
        player.setActive(Math.max(0, Math.min(data.activeIndex(), player.getPartySize() - 1)));

        for (SaveData.ItemData entry : data.bag()) {
            Item item = org.example.data.GameData.instance().item(entry.itemId());
            if (item != null && entry.count() > 0) {
                player.getBag().add(item, entry.count());
            }
        }

        GrowthProgress growth = store.loadGrowth(slot);
        GameSession session = new GameSession(player, growth);
        session.setMapBackground(data.mapBackground());

        RunData run = session.getRogueRunData();
        // 段号 0 表示「尚未开始远征」，必须原样还原，否则读档后会被误判为已在远征中
        run.setSegment(Math.max(0, data.segment()));
        run.setAp(data.run().ap());
        run.setApMax(data.run().apMax());
        run.setPhase(parsePhase(data.run().phase()));
        run.setRetryUsed(data.run().retryUsed());
        run.setGold(data.run().gold());
        run.setGameOver(data.run().gameOver());
        run.setCleared(data.run().cleared());
        run.setRocketLineUnlocked(data.story().rocketLineUnlocked());
        run.setRocketBossDefeated(data.story().rocketBossDefeated());
        run.setLegendaryMet(data.story().legendaryMet());
        run.setPendingLegendary(data.story().pendingLegendary());
        run.setAggressionTriggered(data.story().aggressionTriggered());
        run.setTeam(player.getParty().stream().map(PokemonInstance::new).toList());
        run.setAvailableOptions(toOptions(data.options()));
        run.setMandatoryOption(data.mandatoryOption() == null
                ? null : toOption(data.mandatoryOption()).orElse(null));
        return Optional.of(session);
    }

    /** 存档中的阶段名无法识别时退化为「路线探索」，避免读档直接崩在解析上。 */
    private static RoutePhase parsePhase(String phase) {
        if (phase == null || phase.isBlank()) {
            return RoutePhase.EXPLORING;
        }
        try {
            return RoutePhase.valueOf(phase.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            LogUtil.info("[SaveManager] 存档中的阶段无法识别，按路线探索处理：" + phase);
            return RoutePhase.EXPLORING;
        }
    }

    // ------------------------------------------------------------------
    // 新游戏
    // ------------------------------------------------------------------

    /**
     * 开新游戏：清空该档位既有存档，建立一份图鉴成长从零开始的会话。
     *
     * <p>覆盖已有档位应由调用方先向玩家二次确认 —— 本方法一旦执行，旧进度即被删除且不可恢复。</p>
     *
     * @param slot        目标档位
     * @param trainerName 训练家名
     * @param starter     初始精灵（新宝可梦体系）
     * @return 全新会话
     */
    public GameSession newGame(SaveSlot slot, String trainerName,
                               org.example.pokemon.domain.Pokemon starter) {
        store.delete(slot);
        Player player = PokemonBattleAdapter.createBattlePlayer(trainerName, starter);
        GrowthProgress growth = store.createGrowth(slot);
        return new GameSession(player, growth);
    }

    // ------------------------------------------------------------------
    // 选项与道具的转换
    // ------------------------------------------------------------------

    private static SaveData.OptionData toOptionData(Option option) {
        String type = option.getType() == null ? OptionType.WILD.name() : option.getType().name();
        return new SaveData.OptionData(option.getName() == null ? "" : option.getName(),
                type, option.getCost(), option.getDescription() == null ? "" : option.getDescription(),
                option.isConsumed());
    }

    private static List<Option> toOptions(List<SaveData.OptionData> list) {
        List<Option> options = new ArrayList<>(list.size());
        for (SaveData.OptionData entry : list) {
            toOption(entry).ifPresent(options::add);
        }
        return options;
    }

    /** 存档中的事件类型无法识别时返回空（该选项被丢弃，不猜测类型）。 */
    private static Optional<Option> toOption(SaveData.OptionData data) {
        OptionType type;
        try {
            type = OptionType.valueOf(data.type().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            LogUtil.info("[SaveManager] 存档中的事件类型无法识别，已忽略：" + data.type());
            return Optional.empty();
        }
        return Optional.of(new Option(data.name(), type, data.cost(), data.description()))
                .map(option -> {
                    option.setConsumed(data.consumed());
                    return option;
                });
    }
}
