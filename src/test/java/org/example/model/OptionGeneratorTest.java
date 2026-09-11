package org.example.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 楼层事件刷取规则：临时急救站必刷，其余四选三。 */
class OptionGeneratorTest {

    /** 多刷几层，避免随机性掩盖「必刷」失效的偶发情况。 */
    private static final int SAMPLES_PER_FLOOR = 200;

    private static Option find(List<Option> options, OptionType type) {
        return options.stream().filter(o -> o.getType() == type).findFirst().orElse(null);
    }

    @Test
    void hospitalIsAlwaysGenerated() {
        OptionGenerator generator = new OptionGenerator();
        for (int floor = 1; floor <= 5; floor++) {
            for (int i = 0; i < SAMPLES_PER_FLOOR; i++) {
                List<Option> options = generator.createOptions(floor);
                Option hospital = find(options, OptionType.HOSPITAL);
                assertNotNull(hospital, "第 " + floor + " 层第 " + i + " 次刷取缺少临时急救站");
                assertEquals("临时急救站", hospital.getName());
            }
        }
    }

    @Test
    void hospitalCostScalesWithFloor() {
        OptionGenerator generator = new OptionGenerator();
        for (int floor = 1; floor <= 5; floor++) {
            for (int i = 0; i < 20; i++) {
                Option hospital = find(generator.createOptions(floor), OptionType.HOSPITAL);
                assertEquals(2 + floor, hospital.getCost(), "第 " + floor + " 层急救站消耗应为 2 + 层号");
            }
        }
    }

    @Test
    void eachFloorStillOffersFourDistinctOptions() {
        OptionGenerator generator = new OptionGenerator();
        for (int floor = 1; floor <= 5; floor++) {
            for (int i = 0; i < SAMPLES_PER_FLOOR; i++) {
                List<Option> options = generator.createOptions(floor);
                assertEquals(4, options.size(), "每层应提供 4 个可选事件");
                Set<String> names = options.stream().map(Option::getName).collect(Collectors.toSet());
                assertEquals(4, names.size(), "同层不应出现重复事件：" + names);
            }
        }
    }

    @Test
    void hospitalIsTheOnlyAlwaysPresentOption() {
        // 其余四个事件里每层各剔除一个，长期看都应至少缺失过一次；
        // 反过来确认「必刷」确实是急救站的专属规则，而不是把 5 个全塞进去。
        OptionGenerator generator = new OptionGenerator();
        Set<String> everMissing = new HashSet<>();
        for (int floor = 1; floor <= 5; floor++) {
            for (int i = 0; i < SAMPLES_PER_FLOOR; i++) {
                Set<String> names = generator.createOptions(floor).stream()
                        .map(Option::getName).collect(Collectors.toSet());
                for (String candidate : List.of("野怪遭遇", "训练家挑战", "神秘礼物", "装备补给")) {
                    if (!names.contains(candidate)) {
                        everMissing.add(candidate);
                    }
                }
            }
        }
        assertEquals(4, everMissing.size(), "除急救站外每个事件都应存在被剔除的可能：" + everMissing);
    }

    @Test
    void bossOptionCostScalesWithFloor() {
        OptionGenerator generator = new OptionGenerator();
        for (int floor = 1; floor <= 5; floor++) {
            assertEquals(6 + floor, generator.createBossOption(floor).getCost());
        }
    }

    @Test
    void generatedFloorCarriesPointsAndOptions() {
        OptionGenerator generator = new OptionGenerator();
        for (int floor = 1; floor <= 5; floor++) {
            for (int i = 0; i < 50; i++) {
                FloorData data = generator.generateFloor(floor);
                assertEquals(floor, data.getFloorNumber());
                assertTrue(data.getStartingPoints() >= 8 + floor * 3 && data.getStartingPoints() <= 12 + floor * 3,
                        "起始点数应在 8 + 层号*3 ~ 12 + 层号*3 之间");
                assertNotNull(find(data.getAvailableOptions(), OptionType.HOSPITAL), "生成的楼层必须含急救站");
                assertNotNull(data.getBossOption());
            }
        }
    }
}
