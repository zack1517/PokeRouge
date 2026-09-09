package com.bao01.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 队伍：最多携带 6 只宝可梦，同时只有一只出战（单打）。
 * 队伍同时携带一个背包。
 */
public final class Team {

    public static final int MAX_MEMBERS = 6;

    private final List<Pokemon> members;
    private final Bag bag;
    private int activeIndex;

    public Team(List<Pokemon> members, Bag bag) {
        if (members == null || members.isEmpty() || members.size() > MAX_MEMBERS) {
            throw new IllegalArgumentException("队伍成员数量必须在 1~" + MAX_MEMBERS + " 之间");
        }
        this.members = new ArrayList<>(members);
        this.bag = bag == null ? new Bag() : bag;
        this.activeIndex = 0;
    }

    public static Team of(Bag bag, Pokemon... members) {
        return new Team(List.of(members), bag);
    }

    public Pokemon member(int index) {
        return members.get(index);
    }

    public Pokemon active() {
        return members.get(activeIndex);
    }

    public int activeIndex() {
        return activeIndex;
    }

    public int size() {
        return members.size();
    }

    public List<Pokemon> members() {
        return Collections.unmodifiableList(members);
    }

    public Bag bag() {
        return bag;
    }

    public boolean isAlive(int index) {
        return index >= 0 && index < members.size() && !members.get(index).isFainted();
    }

    /** 当前出战宝可梦是否还活着。 */
    public boolean activeAlive() {
        return isAlive(activeIndex);
    }

    public int aliveCount() {
        int n = 0;
        for (Pokemon p : members) {
            if (!p.isFainted()) {
                n++;
            }
        }
        return n;
    }

    public boolean allFainted() {
        return aliveCount() == 0;
    }

    /** 切换出战位。index 必须是存活成员且不等于当前出战位。 */
    public void switchTo(int index) {
        if (index == activeIndex) {
            throw new IllegalArgumentException("不能切换到当前出战的宝可梦");
        }
        if (!isAlive(index)) {
            throw new IllegalArgumentException("目标宝可梦已经濒死，无法上场");
        }
        activeIndex = index;
    }

    /** 换上新首发出战位（战斗开始时使用）。 */
    public void setActiveIndex(int index) {
        if (!isAlive(index)) {
            throw new IllegalArgumentException("目标宝可梦无法上场");
        }
        activeIndex = index;
    }

    /** 每只成员都恢复到满状态（开局/重新对局用）。 */
    public void resetAll() {
        for (Pokemon p : members) {
            p.reset();
        }
        activeIndex = 0;
    }
}
