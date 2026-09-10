package org.example.save;

import java.util.List;
import java.util.Optional;

/**
 * 单机存档位：本游戏固定提供 4 个存档位，玩家在非战斗状态下可把进度写入任意一位，
 * 或从任意一位继续游戏。
 *
 * <p>每个档位是<b>完全独立</b>的一份进度：训练家队伍、背包、肉鸽楼层进度、地图段号，
 * 以及该档位自己的图鉴成长记录（个体值加成）。档位之间互不影响。</p>
 *
 * <p>档位 id 同时用作磁盘目录名（见 {@link SaveStore}），因此 id 一经发布<b>不可更改</b>，
 * 否则玩家已有存档会找不到。</p>
 */
public enum SaveSlot {

    SLOT_1("slot1", "存档 1"),
    SLOT_2("slot2", "存档 2"),
    SLOT_3("slot3", "存档 3"),
    SLOT_4("slot4", "存档 4");

    private final String id;
    private final String displayName;

    SaveSlot(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** 档位 id（磁盘目录名）；一经发布不可更改。 */
    public String id() {
        return id;
    }

    /** 展示名（界面上的档位标题）。 */
    public String displayName() {
        return displayName;
    }

    /** 全部档位，按 1~4 顺序。 */
    public static List<SaveSlot> all() {
        return List.of(values());
    }

    /** 档位数量（4）。 */
    public static int count() {
        return values().length;
    }

    /** 按 id 反查档位；未知 id 返回空（容忍存档目录被手工改名）。 */
    public static Optional<SaveSlot> ofId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (SaveSlot slot : values()) {
            if (slot.id.equals(id)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }

    /** 展示名（枚举默认 toString 会是 {@code SLOT_1}，日志与界面统一用中文名）。 */
    @Override
    public String toString() {
        return displayName;
    }
}
