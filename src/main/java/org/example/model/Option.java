package org.example.model;

public class Option {
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
