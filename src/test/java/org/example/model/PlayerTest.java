package org.example.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link Player} 的契约补充测试：队伍成员交换（宝可梦交换事件依赖
 * {@link Player#swapPartyMember(int, Pokemon)}）。
 */
class PlayerTest {

    /** 从内建数据中心取一只指定等级的精灵（固定种族，保证断言稳定）。 */
    private static Pokemon pokemon(int level) {
        return org.example.data.GameData.instance().createPokemon("s_fire_cat", level).orElseThrow();
    }

    @Test
    void 交换替换指定成员且队伍数量不变() {
        Player player = new Player("测试玩家");
        Pokemon a = pokemon(5);
        Pokemon b = pokemon(6);
        player.addPokemon(a);
        player.addPokemon(b);
        Pokemon offer = pokemon(12);

        Pokemon gone = player.swapPartyMember(0, offer);

        assertEquals(a, gone, "应返回被交换离队的原成员");
        assertEquals(2, player.getPartySize(), "交换不应改变队伍数量");
        assertEquals(offer, player.getParty().get(0), "新成员应占据被交换者的槽位");
        assertEquals(offer, player.getActive(), "交换出战精灵时新成员应直接成为出战精灵");
        assertFalse(player.getParty().contains(a), "原成员应已离队");
    }

    @Test
    void 交换非法下标或空新成员时失败() {
        Player player = new Player("测试玩家");
        player.addPokemon(pokemon(5));

        assertNull(player.swapPartyMember(-1, pokemon(8)), "负下标应失败");
        assertNull(player.swapPartyMember(1, pokemon(8)), "越界下标应失败");
        assertNull(player.swapPartyMember(0, null), "空新成员应失败");
        assertEquals(1, player.getPartySize(), "失败交换不应改变队伍");
    }
}
