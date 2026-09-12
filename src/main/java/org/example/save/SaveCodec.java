package org.example.save;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 存档文本编解码：把 {@link SaveData} 渲染成可读的纯文本，或从纯文本解析回快照。
 *
 * <p>项目未引入 JSON 库（离线构建不便新增依赖），因此沿用成长进度存档的文本方案：
 * 每行一条记录，形如 {@code 键|值1|值2…}，正文中的竖线与换行按 {@code \|}{@code \n} 转义。</p>
 *
 * <p>设计约束：</p>
 * <ul>
 *   <li>未知键直接跳过 —— 旧版本程序读到新版本多写的字段不会崩，只是忽略。</li>
 *   <li>缺失的可选键（{@code MAP}、{@code BOSS}）视为 {@code null}，因此不需要额外的空值字面量。</li>
 *   <li>格式错误（版本行缺失、数值非法、字段个数不足）一律抛 {@link SaveFormatException}，
 *       由上层按「存档损坏」处理，绝不猜测解析。</li>
 * </ul>
 */
public final class SaveCodec {

    /** 文件头（第一行），便于人工辨认文件用途。 */
    public static final String HEADER = "# PokeRouge 存档 v2";

    /** 格式提示行。 */
    private static final String FORMAT_HINT = "# 格式：键|值…（正文中的 | 与换行会被反斜杠转义）";

    private static final String KEY_VERSION = "VERSION";
    private static final String KEY_SAVED_AT = "SAVED_AT";
    private static final String KEY_PLAYER = "PLAYER";
    private static final String KEY_ACTIVE = "ACTIVE";
    private static final String KEY_SEGMENT = "SEGMENT";
    private static final String KEY_MAP = "MAP";
    private static final String KEY_RUN = "RUN";
    private static final String KEY_POKEMON = "PP";
    private static final String KEY_MOVE = "MOVE";
    /** 技能库（未出战的已学会技能）；可选键，旧版本忽略、旧档缺失时技能库按出战技能兜底。 */
    private static final String KEY_KNOWN_MOVE = "KMOVE";
    private static final String KEY_ITEM = "ITEM";
    private static final String KEY_OPTION = "OPTION";
    private static final String KEY_BOSS = "BOSS";
    private static final String KEY_STORY = "STORY";

    private SaveCodec() {
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    /** 渲染存档正文（含头部与格式提示）。 */
    public static String render(SaveData data) {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        sb.append(FORMAT_HINT).append('\n');
        sb.append(KEY_VERSION).append('|').append(data.version()).append('\n');
        sb.append(KEY_SAVED_AT).append('|').append(data.savedAtMillis()).append('\n');
        sb.append(KEY_PLAYER).append('|').append(escape(data.playerName())).append('\n');
        sb.append(KEY_ACTIVE).append('|').append(data.activeIndex()).append('\n');
        sb.append(KEY_SEGMENT).append('|').append(data.segment()).append('\n');
        if (data.mapBackground() != null) {
            sb.append(KEY_MAP).append('|').append(escape(data.mapBackground())).append('\n');
        }
        sb.append(KEY_RUN).append('|').append(data.run().ap())
                .append('|').append(data.run().apMax())
                .append('|').append(escape(data.run().phase()))
                .append('|').append(data.run().retryUsed())
                .append('|').append(data.run().gold())
                .append('|').append(data.run().gameOver() ? 1 : 0)
                .append('|').append(data.run().cleared() ? 1 : 0).append('\n');

        for (int i = 0; i < data.party().size(); i++) {
            SaveData.PokemonData p = data.party().get(i);
            SaveData.IvData iv = p.ivs();
            sb.append(KEY_POKEMON).append('|').append(escape(p.speciesId()))
                    .append('|').append(p.level())
                    .append('|').append(iv.hp())
                    .append('|').append(iv.attack())
                    .append('|').append(iv.defense())
                    .append('|').append(iv.spAttack())
                    .append('|').append(iv.spDefense())
                    .append('|').append(iv.speed())
                    .append('|').append(p.exp())
                    .append('|').append(escape(p.status()))
                    .append('|').append(p.sleepTurns())
                    .append('|').append(p.badlyPoisonCounter())
                    .append('|').append(p.confusionTurns())
                    .append('|').append(p.currentHp())
                    .append('\n');
            for (int slot = 0; slot < p.moves().size(); slot++) {
                SaveData.MoveData m = p.moves().get(slot);
                sb.append(KEY_MOVE).append('|').append(i)
                        .append('|').append(slot)
                        .append('|').append(escape(m.moveId()))
                        .append('|').append(m.pp())
                        .append('\n');
            }
            // 技能库：完整写出（含出战技能），保证读回后池与存档前一致（旧版本读新档时自动忽略本键）
            for (String moveId : p.knownMoves()) {
                sb.append(KEY_KNOWN_MOVE).append('|').append(i)
                        .append('|').append(escape(moveId))
                        .append('\n');
            }
        }

        for (SaveData.ItemData item : data.bag()) {
            sb.append(KEY_ITEM).append('|').append(escape(item.itemId()))
                    .append('|').append(item.count()).append('\n');
        }
        for (SaveData.OptionData option : data.options()) {
            renderOption(sb, KEY_OPTION, option);
        }
        if (data.mandatoryOption() != null) {
            renderOption(sb, KEY_BOSS, data.mandatoryOption());
        }
        if (data.story().isAnySet()) {
            SaveData.StoryRecord story = data.story();
            sb.append(KEY_STORY).append('|').append(story.rocketLineUnlocked() ? 1 : 0)
                    .append('|').append(story.rocketBossDefeated() ? 1 : 0)
                    .append('|').append(story.legendaryMet() ? 1 : 0)
                    .append('|').append(story.pendingLegendary() ? 1 : 0)
                    .append('|').append(story.aggressionTriggered() ? 1 : 0)
                    .append('\n');
        }
        return sb.toString();
    }

    /** 渲染一条路线节点记录：{@code 键|名称|类型|行动点消耗|说明|是否已走过}。 */
    private static void renderOption(StringBuilder sb, String key, SaveData.OptionData option) {
        sb.append(key).append('|').append(escape(option.name()))
                .append('|').append(escape(option.type()))
                .append('|').append(option.cost())
                .append('|').append(escape(option.description()))
                .append('|').append(option.consumed() ? 1 : 0)
                .append('\n');
    }

    // ------------------------------------------------------------------
    // 解析
    // ------------------------------------------------------------------

    /**
     * 解析存档正文。
     *
     * @param text 文件全文
     * @return 存档快照
     * @throws SaveFormatException 内容为空、缺少版本行、版本不支持或字段格式非法
     */
    public static SaveData parse(String text) {
        if (text == null || text.isBlank()) {
            throw new SaveFormatException("存档内容为空");
        }

        Integer version = null;
        long savedAt = 0L;
        String playerName = "";
        int activeIndex = 0;
        int segment = 0;
        String mapBackground = null;
        SaveData.RunRecord run = SaveData.RunRecord.notStarted();
        List<SaveData.PokemonData> party = new ArrayList<>();
        Map<Integer, List<SaveData.MoveData>> movesByPokemon = new HashMap<>();
        Map<Integer, List<String>> knownMovesByPokemon = new HashMap<>();
        List<SaveData.ItemData> bag = new ArrayList<>();
        List<SaveData.OptionData> options = new ArrayList<>();
        SaveData.OptionData mandatoryOption = null;
        SaveData.StoryRecord story = SaveData.StoryRecord.none();

        int lineNo = 0;
        for (String raw : text.split("\r?\n", -1)) {
            lineNo++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            List<String> parts = splitFields(line);
            String key = parts.get(0);
            switch (key) {
                case KEY_VERSION -> version = parseInt(field(parts, 1), key, lineNo);
                case KEY_SAVED_AT -> savedAt = parseLong(field(parts, 1), key, lineNo);
                case KEY_PLAYER -> playerName = field(parts, 1);
                case KEY_ACTIVE -> activeIndex = parseInt(field(parts, 1), key, lineNo);
                case KEY_SEGMENT -> segment = parseInt(field(parts, 1), key, lineNo);
                case KEY_MAP -> mapBackground = field(parts, 1);
                case KEY_RUN -> run = parseRun(parts, key, lineNo);
                case KEY_POKEMON -> party.add(new SaveData.PokemonData(
                        field(parts, 1),
                        parseInt(field(parts, 2), key, lineNo),
                        new SaveData.IvData(
                                parseInt(field(parts, 3), key, lineNo),
                                parseInt(field(parts, 4), key, lineNo),
                                parseInt(field(parts, 5), key, lineNo),
                                parseInt(field(parts, 6), key, lineNo),
                                parseInt(field(parts, 7), key, lineNo),
                                parseInt(field(parts, 8), key, lineNo)),
                        parseLong(field(parts, 9), key, lineNo),
                        field(parts, 10),
                        parseInt(field(parts, 11), key, lineNo),
                        parseInt(field(parts, 12), key, lineNo),
                        parseInt(field(parts, 13), key, lineNo),
                        parseInt(field(parts, 14), key, lineNo),
                        List.of()));
                case KEY_MOVE -> {
                    int owner = parseInt(field(parts, 1), key, lineNo);
                    movesByPokemon.computeIfAbsent(owner, k -> new ArrayList<>())
                            .add(new SaveData.MoveData(field(parts, 3),
                                    parseInt(field(parts, 4), key, lineNo)));
                }
                case KEY_KNOWN_MOVE -> {
                    int owner = parseInt(field(parts, 1), key, lineNo);
                    knownMovesByPokemon.computeIfAbsent(owner, k -> new ArrayList<>())
                            .add(field(parts, 2));
                }
                case KEY_ITEM -> bag.add(new SaveData.ItemData(field(parts, 1),
                        parseInt(field(parts, 2), key, lineNo)));
                case KEY_OPTION -> options.add(parseOption(parts, key, lineNo));
                case KEY_BOSS -> mandatoryOption = parseOption(parts, key, lineNo);
                case KEY_STORY -> story = parseStory(parts, key, lineNo);
                default -> {
                    // 未知键：为向前兼容直接跳过（新版本多写的字段不影响旧版本读取）
                }
            }
        }

        if (version == null) {
            throw new SaveFormatException("缺少版本行 " + KEY_VERSION + "，不是有效的存档");
        }
        if (version > SaveData.FORMAT_VERSION) {
            throw new SaveFormatException("存档版本 " + version + " 高于本程序支持的 "
                    + SaveData.FORMAT_VERSION + "，请升级游戏后再读取");
        }

        List<SaveData.PokemonData> partyWithMoves = new ArrayList<>(party.size());
        for (int i = 0; i < party.size(); i++) {
            SaveData.PokemonData p = party.get(i);
            List<SaveData.MoveData> battleMoves = movesByPokemon.getOrDefault(i, List.of());
            List<String> known = mergeKnownMoves(battleMoves, knownMovesByPokemon.get(i));
            partyWithMoves.add(new SaveData.PokemonData(p.speciesId(), p.level(), p.ivs(), p.exp(),
                    p.status(), p.sleepTurns(), p.badlyPoisonCounter(), p.confusionTurns(),
                    p.currentHp(), battleMoves, known));
        }
        return new SaveData(version, playerName, activeIndex, partyWithMoves, bag, run,
                options, mandatoryOption, segment, mapBackground, savedAt, story);
    }

    /** 技能库 = 存档中的 KMOVE 行；旧档缺 {@code KMOVE} 行时保持空列表（由映射层按出战技能兜底）。 */
    private static List<String> mergeKnownMoves(List<SaveData.MoveData> battleMoves,
                                                List<String> storedKnown) {
        List<String> merged = new ArrayList<>();
        if (storedKnown != null) {
            for (String moveId : storedKnown) {
                if (!moveId.isBlank() && !merged.contains(moveId)) {
                    merged.add(moveId);
                }
            }
        }
        return merged;
    }

    /** 解析 {@code RUN} 行；兼容 v1 的「楼层|点数|是否结束」三字段格式（旧的点数即行动点）。 */
    private static SaveData.RunRecord parseRun(List<String> parts, String key, int lineNo) {
        if (parts.size() <= 4) {
            return SaveData.RunRecord.fromLegacy(
                    parseInt(field(parts, 2), key, lineNo),
                    parseInt(field(parts, 3), key, lineNo) != 0);
        }
        return new SaveData.RunRecord(
                parseInt(field(parts, 1), key, lineNo),
                parseInt(field(parts, 2), key, lineNo),
                field(parts, 3),
                parseInt(field(parts, 4), key, lineNo),
                parseInt(field(parts, 5), key, lineNo),
                parseInt(field(parts, 6), key, lineNo) != 0,
                parseInt(field(parts, 7), key, lineNo) != 0);
    }

    /** 解析 {@code OPTION}/{@code BOSS} 行；第 6 个字段（是否已走过）缺失时按未走过处理。 */
    private static SaveData.OptionData parseOption(List<String> parts, String key, int lineNo) {
        boolean consumed = parts.size() > 5 && parseInt(field(parts, 5), key, lineNo) != 0;
        return new SaveData.OptionData(field(parts, 1), field(parts, 2),
                parseInt(field(parts, 3), key, lineNo), field(parts, 4), consumed);
    }

    /**
     * 解析 {@code STORY} 行（火箭队剧情线与神兽偶遇状态）：五个标记分别对应第 1~5 个字段，
     * 缺失的字段按 {@code false} 处理，因此旧文件少写标记也不会解析失败。
     */
    private static SaveData.StoryRecord parseStory(List<String> parts, String key, int lineNo) {
        return new SaveData.StoryRecord(
                flag(parts, 1, key, lineNo),
                flag(parts, 2, key, lineNo),
                flag(parts, 3, key, lineNo),
                flag(parts, 4, key, lineNo),
                flag(parts, 5, key, lineNo));
    }

    /** 取第 index 个字段并当作布尔标记解析；字段缺失返回 {@code false}。 */
    private static boolean flag(List<String> parts, int index, String key, int lineNo) {
        if (index >= parts.size()) {
            return false;
        }
        return parseInt(field(parts, index), key, lineNo) != 0;
    }

    /**
     * 解析存档正文中的部分字段（供档位列表显示摘要），不受队伍/技能等区块的完整性约束。
     *
     * <p>与 {@link #parse} 的差别：不校验必须存在的字段，仅挑出摘要需要的键；因此对损坏的
     * 存档也能尽量给出可读信息。</p>
     */
    public static SaveSummary parseSummary(SaveSlot slot, String text) {
        String playerName = "";
        int segment = 0;
        int ap = 0;
        int gold = 0;
        long savedAt = 0L;
        int partySize = 0;
        int aliveCount = 0;
        if (text != null) {
            for (String raw : text.split("\r?\n", -1)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                List<String> parts = splitFields(line);
                switch (parts.get(0)) {
                    case KEY_PLAYER -> playerName = field(parts, 1);
                    case KEY_SEGMENT -> segment = softInt(field(parts, 1), segment);
                    case KEY_RUN -> {
                        if (parts.size() <= 4) {
                            // v1 旧档：RUN|楼层|点数|已结束 —— 点数才是剩余进度
                            ap = softInt(field(parts, 2), ap);
                        } else {
                            // v2 起：RUN|行动点|上限|阶段|重试|金币|已结束|已通关
                            ap = softInt(field(parts, 1), ap);
                            gold = softInt(field(parts, 5), gold);
                        }
                    }
                    case KEY_SAVED_AT -> savedAt = softLong(field(parts, 1), savedAt);
                    case KEY_POKEMON -> {
                        partySize++;
                        // 第 15 个字段是剩余 HP：为 0 记倒下；字段缺失（旧档）按存活计，避免误报「全倒下」
                        int hp = parts.size() > 14 ? softInt(field(parts, 14), 1) : 1;
                        if (hp > 0) {
                            aliveCount++;
                        }
                    }
                    default -> {
                    }
                }
            }
        }
        return new SaveSummary(slot, playerName, partySize, aliveCount, segment, ap, gold, savedAt);
    }

    // ------------------------------------------------------------------
    // 字段工具
    // ------------------------------------------------------------------

    /** 转义字段分隔符与换行，使任意文本都能安全放进一行。 */
    static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '\\' -> sb.append("\\\\");
                case '|' -> sb.append("\\|");
                case '\n' -> sb.append("\\n");
                case '\r' -> {
                    // 统一丢弃，避免 Windows 换行破坏「一行一条记录」的约定
                }
                default -> sb.append(ch);
            }
        }
        return sb.toString();
    }

    /** 按未转义的 {@code |} 切分字段，并把转义序列还原。 */
    static List<String> splitFields(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (escaped) {
                current.append(ch == 'n' ? '\n' : ch);
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '|') {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (escaped) {
            current.append('\\'); // 行尾孤立反斜杠：按字面处理
        }
        parts.add(current.toString());
        return parts;
    }

    /** 取第 index 个字段；越界返回空串（调用方按需校验）。 */
    private static String field(List<String> parts, int index) {
        return index < parts.size() ? parts.get(index) : "";
    }

    private static int parseInt(String value, String key, int lineNo) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new SaveFormatException("第 " + lineNo + " 行 " + key + " 的整数非法：" + value);
        }
    }

    private static long parseLong(String value, String key, int lineNo) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new SaveFormatException("第 " + lineNo + " 行 " + key + " 的整数非法：" + value);
        }
    }

    /** 宽松取整数（摘要用）：非法时返回兜底值。 */
    private static int softInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long softLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
