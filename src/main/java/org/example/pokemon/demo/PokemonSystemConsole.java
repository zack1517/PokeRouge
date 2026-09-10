package org.example.pokemon.demo;

import java.util.List;
import java.util.Scanner;
import org.example.pokemon.domain.GrowthEvent;
import org.example.pokemon.domain.Pokemon;
import org.example.pokemon.domain.Species;
import org.example.pokemon.service.PokemonService;
import org.example.pokemon.service.PokemonServiceImpl;

/**
 * 新宝可梦系统的独立控制台入口。
 *
 * <p>该入口不经过旧战斗 UI，可直接验证新模型、CSV 数据加载及成长服务。</p>
 */
public final class PokemonSystemConsole {

    private PokemonSystemConsole() {
    }

    public static void main(String[] args) {
        PokemonService service = new PokemonServiceImpl();
        Scanner scanner = new Scanner(System.in);
        System.out.print("训练家名称（直接回车使用 测试训练师）：");
        String name = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
        service.createPlayer(name.isEmpty() ? "测试训练师" : name);

        List<Species> starters = service.getInitialPool();
        System.out.println("\n可选初始宝可梦：");
        for (int i = 0; i < starters.size(); i++) {
            System.out.printf("%d. %s (%s)%n", i + 1, starters.get(i).getName(), starters.get(i).getId());
        }
        System.out.print("选择编号（直接回车选择 1）：");
        int choice = readChoice(scanner, starters.size());
        Pokemon starter = service.createPokemon(starters.get(choice - 1).getId(), 5);
        service.addPokemonToParty(starter);

        System.out.println("\n新宝可梦系统已启动。输入数字进行操作：");
        while (true) {
            System.out.println("1. 查看当前宝可梦  2. 获得 1000 经验  3. 受到 20 伤害  4. 完全恢复  0. 退出");
            System.out.print("> ");
            if (!scanner.hasNextLine()) {
                return;
            }
            switch (scanner.nextLine().trim()) {
                case "1" -> show(starter);
                case "2" -> {
                    List<GrowthEvent> events = service.gainExp(starter, 1000);
                    System.out.println("成长事件：" + events);
                    show(starter);
                }
                case "3" -> {
                    starter.takeDamage(20);
                    show(starter);
                }
                case "4" -> {
                    service.healPokemon(starter);
                    show(starter);
                }
                case "0" -> {
                    System.out.println("新宝可梦系统测试结束。");
                    return;
                }
                default -> System.out.println("请输入 0 到 4。 ");
            }
        }
    }

    private static int readChoice(Scanner scanner, int size) {
        if (!scanner.hasNextLine()) {
            return 1;
        }
        try {
            int choice = Integer.parseInt(scanner.nextLine().trim());
            return choice >= 1 && choice <= size ? choice : 1;
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static void show(Pokemon pokemon) {
        System.out.printf("%s Lv.%d | HP %d/%d | EXP %d | 性格 %s | 技能 %s%n",
                pokemon.getName(), pokemon.getLevel(), pokemon.getCurrentHp(), pokemon.getMaxHp(),
                pokemon.getExp(), pokemon.getNature().getName(), pokemon.getMoves());
    }
}
