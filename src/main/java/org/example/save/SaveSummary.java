package org.example.save;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 存档位摘要：仅供档位列表展示（玩家名、队伍规模、段号、行动点、金币、存档时间），
 * 不参与实际读档。这样列表渲染无需把整份存档反序列化成一个完整会话。
 *
 * @param slot         档位
 * @param playerName   训练家名
 * @param partySize    队伍精灵数量
 * @param segment      地图段号（0 表示尚未开始）
 * @param ap           当前剩余行动点
 * @param gold         金币余额
 * @param savedAtMillis 存档时间（epoch millis）
 */
public record SaveSummary(SaveSlot slot, String playerName, int partySize,
                          int segment, int ap, int gold, long savedAtMillis) {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /** 存档时间的可读文本（本地时区，分钟精度）；时间未知时返回「时间未知」。 */
    public String savedAtText() {
        if (savedAtMillis <= 0) {
            return "时间未知";
        }
        return TIME_FORMAT.format(Instant.ofEpochMilli(savedAtMillis));
    }

    /** 一行式摘要文本，例如「小明 · 队伍 3 · 第 2 段 · 行动点 4 · 金币 320 · 2026-09-10 15:30」。 */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(playerName == null || playerName.isBlank() ? "无名训练家" : playerName);
        sb.append(" · 队伍 ").append(partySize);
        if (segment > 0) {
            sb.append(" · 第 ").append(segment).append(" 段");
            sb.append(" · 行动点 ").append(Math.max(0, ap));
            sb.append(" · 金币 ").append(Math.max(0, gold));
        } else {
            sb.append(" · 未开始远征");
        }
        sb.append(" · ").append(savedAtText());
        return sb.toString();
    }
}
