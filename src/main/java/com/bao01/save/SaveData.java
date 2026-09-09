package com.bao01.save;

import org.example.data.GameData;
import org.example.model.Item;
import org.example.model.ItemStack;
import org.example.model.Player;
import org.example.model.Pokemon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 存档数据模型（纯数据，不含 IO）。
 *
 * <p>运行在 {@code dev} 的 {@code org.example} 对象模型之上：世界存档 = 一名玩家的
 * 队伍（每只精灵：物种 id / 等级 / 当前 HP）+ 背包（道具 id / 数量）。所有类型为
 * 不可变 record，方便序列化与校验。等级、技能等由数据注册表
 * （{@link org.example.data.GameData}）按物种重建。</p>
 */
public final class SaveData {

    /** 存档格式版本号。 */
    public static final int FORMAT_VERSION = 1;

    /** 恢复玩家时使用的默认训练家名。 */
    public static final String DEFAULT_PLAYER_NAME = "训练家";

    private SaveData() {
    }

    /**
     * 单只宝可梦快照。
     *
     * @param speciesId 物种 id（由 {@code GameData} 注册表按 id 重建）
     * @param level     等级
     * @param currentHp 当前 HP（0 = 濒死）
     */
    public record PartyEntry(String speciesId, int level, int currentHp) {

        public PartyEntry {
            Objects.requireNonNull(speciesId, "speciesId");
            if (speciesId.isBlank()) {
                throw new IllegalArgumentException("物种 id 不能为空");
            }
            if (level <= 0) {
                throw new IllegalArgumentException("非法等级: " + level);
            }
            if (currentHp < 0) {
                throw new IllegalArgumentException("非法 HP: " + currentHp);
            }
        }

        /** 从运行时精灵采样。 */
        public static PartyEntry capture(Pokemon p) {
            return new PartyEntry(p.getSpecies().getId(), p.getLevel(),
                    Math.max(0, p.getCurrentHp()));
        }

        /** 按物种注册表重建一只精灵并还原当前 HP；未知物种返回 null。 */
        public Pokemon restore() {
            return GameData.instance().createPokemon(speciesId, level).map(p -> {
                int cur = Math.min(currentHp, p.getMaxHp());
                if (cur < p.getMaxHp()) {
                    p.takeDamage(p.getMaxHp() - cur);
                }
                return p;
            }).orElse(null);
        }

        /** 面向人类的状态摘要。 */
        public String describe() {
            return speciesId + " Lv." + level + " HP " + currentHp + "/?";
        }
    }

    /** 背包中某道具堆叠。 */
    public record ItemEntry(String itemId, int count) {

        public ItemEntry {
            Objects.requireNonNull(itemId, "itemId");
            if (itemId.isBlank()) {
                throw new IllegalArgumentException("道具 id 不能为空");
            }
            if (count <= 0) {
                throw new IllegalArgumentException("非法道具数量: " + count);
            }
        }

        public static ItemEntry capture(ItemStack stack) {
            return new ItemEntry(stack.getItem().getId(), stack.getCount());
        }

        /** 解析回 {@code GameData} 注册表实例；未知 id 返回 null。 */
        public Item restore() {
            return GameData.instance().item(itemId);
        }
    }

    /**
     * 玩家快照：队伍（含出场顺序与当前 HP/等级）+ 出战位 + 背包。
     */
    public record PlayerData(List<PartyEntry> party, int activeIndex, List<ItemEntry> bag) {

        public PlayerData {
            party = party == null ? List.of() : List.copyOf(party);
            bag = bag == null ? List.of() : List.copyOf(bag);
            if (activeIndex < 0) {
                throw new IllegalArgumentException("非法出战位: " + activeIndex);
            }
        }

        public static PlayerData capture(Player player) {
            List<PartyEntry> ms = new ArrayList<>();
            for (Pokemon p : player.getParty()) {
                ms.add(PartyEntry.capture(p));
            }
            List<ItemEntry> bagItems = new ArrayList<>();
            for (ItemStack stack : player.getBag().getAll()) {
                if (!stack.isEmpty()) {
                    bagItems.add(ItemEntry.capture(stack));
                }
            }
            return new PlayerData(ms, player.getActiveIndex(), bagItems);
        }

        /**
         * 重建玩家。个别成员可能因未知物种而丢失；出战位若指向濒死或越界则回退到
         * 第一只存活成员。
         */
        public Player restore() {
            return restore(DEFAULT_PLAYER_NAME);
        }

        public Player restore(String playerName) {
            String name = playerName == null || playerName.isBlank()
                    ? DEFAULT_PLAYER_NAME : playerName;
            Player player = new Player(name);
            List<Pokemon> rebuilt = new ArrayList<>();
            for (PartyEntry pe : party) {
                Pokemon p = pe == null ? null : pe.restore();
                if (p != null) {
                    rebuilt.add(p);
                    player.addToParty(p);
                }
            }
            if (rebuilt.isEmpty()) {
                throw new SaveException("存档中没有可恢复的宝可梦");
            }
            for (ItemEntry ie : bag) {
                if (ie != null && ie.count() > 0) {
                    Item item = ie.restore();
                    if (item != null) {
                        player.getBag().add(item, ie.count());
                    }
                }
            }
            if (activeIndex >= 0 && activeIndex < rebuilt.size()
                    && !rebuilt.get(activeIndex).isFainted()) {
                player.setActive(activeIndex);
            } else {
                player.leadWithFirstHealthy();
            }
            return player;
        }
    }

    /** 顶层世界存档：一名玩家的队伍 + 背包快照。 */
    public record World(int version, PlayerData player) {

        public World {
            player = Objects.requireNonNull(player, "player");
        }
    }
}
