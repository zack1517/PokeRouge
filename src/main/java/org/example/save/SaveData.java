package org.example.save;

import java.util.List;

/**
 * 一个存档位的完整进度快照（纯数据，不持有任何运行时对象引用）。
 *
 * <p>快照覆盖一次会话的全部可变状态：训练家队伍与出战下标、背包、肉鸽路线进度
 * （段号/行动点/阶段/重试次数/金币/是否已结束/当前段节点），以及段地图背景。图鉴成长记录不在此结构中，
 * 而是与档位同目录的另一份文件（见 {@link SaveStore#growthFile}），沿用成长机制既有的格式。</p>
 *
 * <p>{@link #version} 用于将来格式升级时识别旧档；读取到不认识的版本应当拒绝载入而不是猜着解析。</p>
 *
 * @param version      存档格式版本
 * @param playerName   训练家名
 * @param activeIndex  出战精灵在队伍中的下标
 * @param party        队伍（按顺序）
 * @param bag          背包中的道具堆叠
 * @param run          肉鸽路线进度（段号之外的行动点 / 阶段 / 金币等）
 * @param options      当前段可选路线节点（可为空列表）
 * @param mandatoryOption 当前待攻略的必然节点（道馆 / 四天王 / 冠军）；无则为 {@code null}
 * @param segment      当前段号（0 表示尚未开始本轮远征）
 * @param mapBackground 当前段地图背景 classpath；未抽取过为 {@code null}
 * @param savedAtMillis 存档时间（epoch millis），仅供档位列表展示
 * @param story        火箭队剧情线与神兽偶遇状态（《需求文档》§五）
 */
public record SaveData(int version,
                       String playerName,
                       int activeIndex,
                       List<PokemonData> party,
                       List<ItemData> bag,
                       RunRecord run,
                       List<OptionData> options,
                       OptionData mandatoryOption,
                       int segment,
                       String mapBackground,
                       long savedAtMillis,
                       StoryRecord story) {

    /** 当前存档格式版本。 */
    public static final int FORMAT_VERSION = 2;

    public SaveData {
        party = party == null ? List.of() : List.copyOf(party);
        bag = bag == null ? List.of() : List.copyOf(bag);
        options = options == null ? List.of() : List.copyOf(options);
        run = run == null ? RunRecord.notStarted() : run;
        story = story == null ? StoryRecord.none() : story;
        playerName = playerName == null ? "" : playerName;
    }

    /**
     * 不含剧情线状态的构造器：{@code STORY} 行是格式 v2 的可选扩展键，
     * v2 早期文件与 v1 旧档都没有它，读回时按「剧情线未开启」还原。
     */
    public SaveData(int version, String playerName, int activeIndex, List<PokemonData> party,
                    List<ItemData> bag, RunRecord run, List<OptionData> options,
                    OptionData mandatoryOption, int segment, String mapBackground,
                    long savedAtMillis) {
        this(version, playerName, activeIndex, party, bag, run, options, mandatoryOption,
                segment, mapBackground, savedAtMillis, StoryRecord.none());
    }

    /**
     * 一只精灵的可存档状态。
     *
     * @param speciesId          物种 id（战斗模型 {@code Species#getId()}）
     * @param level              等级
     * @param ivs                个体值六项（已含成长机制加成）
     * @param exp                当前等级内累积的经验
     * @param status             主要异常状态枚举名（如 {@code POISON}）
     * @param sleepTurns         睡眠剩余回合数
     * @param badlyPoisonCounter 剧毒计数
     * @param confusionTurns     混乱剩余回合数
     * @param currentHp          当前 HP
     * @param moves              技能槽（含各自剩余 PP）
     * @param heldItemId         携带装备 id；空串表示未携带（或该装备已从数据表移除）
     */
    public record PokemonData(String speciesId, int level, IvData ivs, long exp,
                              String status, int sleepTurns, int badlyPoisonCounter,
                              int confusionTurns, int currentHp, List<MoveData> moves,
                              String heldItemId) {

        public PokemonData {
            moves = moves == null ? List.of() : List.copyOf(moves);
            ivs = ivs == null ? new IvData(0, 0, 0, 0, 0, 0) : ivs;
            // 异常状态统一用空串表示「无」，避免 null 在写文件时被转义成空字段后又读回 null 之外的值
            status = status == null ? "" : status;
            speciesId = speciesId == null ? "" : speciesId;
            // 未携带装备同样用空串表示，理由同上
            heldItemId = heldItemId == null ? "" : heldItemId;
        }
    }

    /** 精灵的一个技能槽：技能 id + 剩余 PP。 */
    public record MoveData(String moveId, int pp) {
    }

    /** 背包中的一个道具堆叠：道具 id + 数量。 */
    public record ItemData(String itemId, int count) {
    }

    /**
     * 肉鸽路线进度（段号另存于 {@link SaveData#segment}）。
     *
     * @param ap        当前剩余行动点
     * @param apMax     本段行动点上限
     * @param phase     推进阶段（{@code RoutePhase} 枚举名）
     * @param retryUsed 本段已用掉的「失败一次」次数
     * @param gold      金币余额
     * @param gameOver  是否已战败结束
     * @param cleared   是否已通关
     */
    public record RunRecord(int ap, int apMax, String phase, int retryUsed, int gold,
                            boolean gameOver, boolean cleared) {

        public RunRecord {
            phase = phase == null ? "" : phase;
            ap = Math.max(0, ap);
            apMax = Math.max(1, apMax);
            retryUsed = Math.max(0, retryUsed);
            gold = Math.max(0, gold);
        }

        /** 尚未开始任何一轮远征时的初始进度。 */
        public static RunRecord notStarted() {
            return new RunRecord(0, 1, "", 0, 0, false, false);
        }

        /**
         * 由 v1 存档的旧字段（楼层 / 点数）迁移而来：旧的「点数」即为行动点，
         * 旧存档没有阶段与金币概念，按探索阶段与初始金币还原。
         */
        public static RunRecord fromLegacy(int points, boolean gameOver) {
            int ap = Math.max(0, points);
            return new RunRecord(ap, Math.max(1, ap), "", 0, 0, gameOver, false);
        }
    }

    /** 路线节点（{@code type} 为 {@code OptionType} 枚举名；{@code consumed} 表示已走过）。 */
    public record OptionData(String name, String type, int cost, String description, boolean consumed) {

        public OptionData {
            name = name == null ? "" : name;
            type = type == null ? "" : type;
            description = description == null ? "" : description;
        }

        /** 便捷构造：默认「未走过」。 */
        public OptionData(String name, String type, int cost, String description) {
            this(name, type, cost, description, false);
        }
    }

    /** 个体值六项（0~31）。 */
    public record IvData(int hp, int attack, int defense, int spAttack, int spDefense, int speed) {
    }

    /**
     * 火箭队剧情线与神兽偶遇状态（《需求文档》§五），对应存档中的可选 {@code STORY} 行。
     *
     * <p>五个标记与 §5.3 的三行分支表一一对应：</p>
     * <ul>
     *   <li>{@code rocketLineUnlocked} —— 进入过任意一次火箭队节点，即已开启剧情线；</li>
     *   <li>{@code rocketBossDefeated} —— 已完成「火箭队抓捕神兽」并击败首领（拿到大师球）；</li>
     *   <li>{@code legendaryMet} —— 本局已触发过神兽偶遇（每局至多一次，含必然触发的那次）；</li>
     *   <li>{@code pendingLegendary} —— 有一次 0 点的神兽偶遇待进入；</li>
     *   <li>{@code aggressionTriggered} —— 首领侵略战已触发（用于结局文案）。</li>
     * </ul>
     *
     * @param rocketLineUnlocked 是否已开启火箭队剧情线
     * @param rocketBossDefeated 是否已击败火箭队首领
     * @param legendaryMet       本局是否已触发过神兽偶遇
     * @param pendingLegendary   是否有必然触发的神兽偶遇待进入
     * @param aggressionTriggered 首领侵略战是否已触发
     */
    public record StoryRecord(boolean rocketLineUnlocked, boolean rocketBossDefeated,
                              boolean legendaryMet, boolean pendingLegendary,
                              boolean aggressionTriggered) {

        /** 剧情线未开启（v1 旧档与 v2 早期文件缺 {@code STORY} 行时的默认值）。 */
        public static StoryRecord none() {
            return new StoryRecord(false, false, false, false, false);
        }

        /** 是否存在需要写入档案的剧情线状态。 */
        public boolean isAnySet() {
            return rocketLineUnlocked || rocketBossDefeated || legendaryMet
                    || pendingLegendary || aggressionTriggered;
        }
    }
}
