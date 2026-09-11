package org.example;

import org.example.model.ElementType;
import org.example.model.Option;
import org.example.model.OptionType;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stats;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归测试：肉鸽轮次中的队伍快照必须覆盖开局后新入队的精灵。
 *
 * <p>历史缺陷：{@code RunData.team} 只在 {@link GameSession#startRogueRun()} 时快照一次，
 * 而战斗直接读写唯一的 {@link Player}，因此中途捕捉到的精灵不在快照里，
 * 「临时急救站」看起来只对开局那只生效。</p>
 */
class GameSessionRogueTeamTest {

    private static Species spec(String id) {
        return new Species(id, id, ElementType.NORMAL, null,
                new Stats(200, 50, 40, 50, 40, 50), 51, List.of(), null, 0, Map.of());
    }

    private static Pokemon damaged(String id) {
        Pokemon pokemon = Pokemon.create(spec(id), 20, List.of());
        pokemon.takeDamage(80);
        return pokemon;
    }

    private static Option hospitalOption() {
        return new Option("临时急救站", OptionType.HOSPITAL, 0, "恢复全队精灵HP。");
    }

    @Test
    void hospitalHealsPokemonCaughtAfterRunStarted() {
        Player player = new Player("P");
        Pokemon starter = damaged("starter");
        player.addPokemon(starter);

        GameSession session = new GameSession(player);
        session.startRogueRun();

        // 开局之后才入队的精灵：只进 Player，不进开局时生成的队伍快照
        Pokemon caught = damaged("caught");
        player.addPokemon(caught);

        Option hospital = hospitalOption();
        session.getRogueRunData().getAvailableOptions().add(hospital);
        assertTrue(session.enterRogueNode(hospital));
        session.resolveRogueOptionEffect(hospital);

        assertEquals(starter.getMaxHp(), starter.getCurrentHp());
        assertEquals(caught.getMaxHp(), caught.getCurrentHp(),
                "开局后入队的精灵同样应被急救站恢复");
    }

    @Test
    void hospitalHealsWholeTeamWhenRunTeamIsResynced() {
        Player player = new Player("P");
        for (int i = 0; i < 3; i++) {
            player.addPokemon(damaged("sp" + i));
        }

        GameSession session = new GameSession(player);
        session.startRogueRun();

        Option hospital = hospitalOption();
        session.getRogueRunData().getAvailableOptions().add(hospital);
        assertTrue(session.enterRogueNode(hospital));
        session.resolveRogueOptionEffect(hospital);

        for (Pokemon pokemon : player.getParty()) {
            assertEquals(pokemon.getMaxHp(), pokemon.getCurrentHp());
        }
    }
}
