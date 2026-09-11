package org.example.model;

public class Option {
    /** 用掉的事件会原位替换为该名字的占位选项：0 成本且不可再次选择。 */
    public static final String HIDDEN_EVENT_NAME = "隐藏事件";

    private String name;
    private OptionType type;
    private int cost;
    private String description;

    public Option() {
    }

    public Option(String name, OptionType type, int cost, String description) {
        this.name = name;
        this.type = type;
        this.cost = cost;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public OptionType getType() {
        return type;
    }

    public void setType(OptionType type) {
        this.type = type;
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /** 是否为用掉事件后留下的「隐藏事件」占位：不可再被选中。 */
    public boolean isHiddenEvent() {
        return HIDDEN_EVENT_NAME.equals(name);
    }

    @Override
    public String toString() {
        return "Option{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", cost=" + cost +
                ", description='" + description + '\'' +
                '}';
    }
}
