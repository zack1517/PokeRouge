package com.bao01.save;

import com.bao01.model.Battle;
import com.bao01.model.Item;
import com.bao01.model.Pokemon;
import com.bao01.model.Stat;
import com.bao01.model.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 存档数据模型（纯数据，不含 IO）。
 *
 * <p>所有类型都设计为不可变 record，方便序列化、校验与文档描述。
 * 对战内可变的量（当前 HP、能力等级、出战位、背包数量、天气等）
 * 都会被快照保存；等级/努力值/招式等由「物种注册表」重建。
 */
public final class SaveData {

    /** 存档格式版本号。 */
    public static final int FORMAT_VERSION = 1;

    private SaveData() {
    }

    /**
     * 单只宝可梦快照。
     *
     * @param species 物种名（由 {@code SamplePokemon} 注册表按名重建）
     * @param level   等级
     * @param hp      当前 HP（0 = 濒死）
     * @param stages  六项能力等级，顺序固定为 {@code Stat.values()}
     */
    public record PokemonData(String species, int level, int hp, int[] stages) {

        public PokemonData {
            Objects.requireNonNull(species, "species");
            if (level <= 0) {
                throw new IllegalArgumentException("非法等级: " + level);
            }
            if (hp < 0) {
                throw new IllegalArgumentException("非法 HP: " + hp);
            }
            int n = Stat.values().length;
            stages = stages == null ? new int[n] : stages.clone();
            if (stages.length != n) {
                throw new IllegalArgumentException("能力等级数量必须是 " + n);
            }
        }

        public int[] stages() {
            return stages.clone();
        }

        /** 从运行时宝可梦采样。 */
        public static PokemonData capture(Pokemon p) {
            int[] stages = new int[Stat.values().length];
            for (Stat s : Stat.values()) {
                stages[s.ordinal()] = p.stageOf(s);
            }
            return new PokemonData(p.getName(), p.getLevel(),
                    Math.max(0, p.currentHp()), stages);
        }

        /** 按物种注册表重建一只宝可梦并还原 HP 与能力等级；未知物种返回 null。 */
        public Pokemon restore() {
            Pokemon p = com.bao01.config.SamplePokemon.create(species, level);
            if (p == null) {
                return null;
            }
            int cur = Math.min(hp, p.maxHp());
            p.takeDamage(p.maxHp() - cur);
            for (Stat s : Stat.values()) {
                int stage = stages[s.ordinal()];
                if (stage != 0) {
                    p.changeStage(s, stage);
                }
            }
            return p;
        }

        /** 面向人类的状态摘要。 */
        public String describe() {
            return species + " Lv." + level + " HP " + hp + "/?";
        }
    }

    /** 背包中某道具及其数量。 */
    public record ItemData(String itemName, int count) {

        public ItemData {
            Objects.requireNonNull(itemName, "itemName");
            if (count <= 0) {
                throw new IllegalArgumentException("非法道具数量: " + count);
            }
        }

        public static ItemData capture(Item item, int count) {
            return new ItemData(item.getName(), count);
        }

        /** 解析回配置中心实例（保持 Bag 以实例为键的语义）；未知道具返回 null。 */
        public Item restore() {
            return com.bao01.config.Items.byName(itemName);
        }
    }

    /**
     * 一支队伍快照：成员（含出场顺序与当前 HP/等级）+ 出战位 + 背包。
     */
    public record TeamData(List<PokemonData> members, int activeIndex, List<ItemData> bag) {

        public TeamData {
            members = members == null ? List.of() : List.copyOf(members);
            bag = bag == null ? List.of() : List.copyOf(bag);
        }

        public static TeamData capture(Team team) {
            List<PokemonData> ms = new ArrayList<>();
            for (Pokemon p : team.members()) {
                ms.add(PokemonData.capture(p));
            }
            List<ItemData> bagItems = new ArrayList<>();
            for (var e : team.bag().items().entrySet()) {
                if (e.getValue() > 0) {
                    bagItems.add(ItemData.capture(e.getKey(), e.getValue()));
                }
            }
            return new TeamData(ms, team.activeIndex(), bagItems);
        }

        /**
         * 重建队伍。个别成员可能因未知物种而丢失；
         * 出战位若指向濒死或越界则回退到第一只存活成员。
         */
        public Team restore() {
            List<Pokemon> rebuilt = new ArrayList<>();
            for (PokemonData pd : members) {
                Pokemon p = pd == null ? null : pd.restore();
                if (p != null) {
                    rebuilt.add(p);
                }
            }
            if (rebuilt.isEmpty()) {
                throw new SaveException("存档中没有可恢复的宝可梦");
            }
            com.bao01.model.Bag bagObj = new com.bao01.model.Bag();
            for (ItemData id : bag) {
                if (id != null && id.count() > 0) {
                    Item item = id.restore();
                    if (item != null) {
                        bagObj.give(item, id.count());
                    }
                }
            }
            Team team = new Team(rebuilt, bagObj);
            if (activeIndex >= 0 && activeIndex < team.size()
                    && team.isAlive(activeIndex)) {
                try {
                    team.setActiveIndex(activeIndex);
                } catch (IllegalArgumentException ignored) {
                    // 回退到默认首只
                }
            }
            return team;
        }
    }

    /**
     * 一场进行中对局的完整快照（玩家队伍单独存放于 {@link World}）。
     */
    public record BattleData(String opponent, int roundNo, String weather, int weatherTurnsLeft,
                             boolean playerPendingSendout, boolean playerKoSwitchPending,
                             TeamData foeTeam, List<String> history) {

        public BattleData {
            opponent = Objects.requireNonNull(opponent, "opponent");
            weather = Objects.requireNonNull(weather, "weather");
            foeTeam = Objects.requireNonNull(foeTeam, "foeTeam");
            history = history == null ? List.of() : List.copyOf(history);
        }

        public static BattleData capture(Battle b) {
            return new BattleData(
                    b.opponent().name(),
                    b.roundNo(),
                    b.weather().name(),
                    b.weatherTurnsLeft(),
                    b.isPlayerPendingSendout(),
                    b.isPlayerKoSwitchPending(),
                    TeamData.capture(b.foe()),
                    b.history());
        }

        public Battle.Opponent opponentEnum() {
            try {
                return Battle.Opponent.valueOf(opponent);
            } catch (IllegalArgumentException e) {
                throw new SaveException("未知对战对象类型: " + opponent);
            }
        }

        public com.bao01.model.Weather weatherEnum() {
            try {
                return com.bao01.model.Weather.valueOf(weather);
            } catch (IllegalArgumentException e) {
                throw new SaveException("未知天气: " + weather);
            }
        }
    }

    /**
     * 顶层世界存档：玩家队伍（含背包），若对局尚未结束则附带战斗快照。
     */
    public record World(int version, TeamData playerTeam, BattleData battle) {

        public World {
            playerTeam = Objects.requireNonNull(playerTeam, "playerTeam");
        }

        public boolean hasBattle() {
            return battle != null;
        }
    }
}
