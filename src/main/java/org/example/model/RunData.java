package org.example.model;

import java.util.ArrayList;
import java.util.List;

public class RunData {
    private List<PokemonInstance> team = new ArrayList<>();
    private int currentFloor;
    private int currentPoints;
    private boolean isGameOver;
    private List<Option> availableOptions = new ArrayList<>();
    private Option bossOption;

    public RunData() {
    }

    public RunData(List<PokemonInstance> team, int currentFloor, int currentPoints, boolean isGameOver) {
        this.team = team == null ? new ArrayList<>() : new ArrayList<>(team);
        this.currentFloor = currentFloor;
        this.currentPoints = currentPoints;
        this.isGameOver = isGameOver;
    }

    public List<PokemonInstance> getTeam() {
        return team;
    }

    public void setTeam(List<PokemonInstance> team) {
        this.team = team == null ? new ArrayList<>() : new ArrayList<>(team);
    }

    public int getCurrentFloor() {
        return currentFloor;
    }

    public void setCurrentFloor(int currentFloor) {
        this.currentFloor = currentFloor;
    }

    public int getCurrentPoints() {
        return currentPoints;
    }

    public void setCurrentPoints(int currentPoints) {
        this.currentPoints = currentPoints;
    }

    public boolean isGameOver() {
        return isGameOver;
    }

    public void setGameOver(boolean gameOver) {
        isGameOver = gameOver;
    }

    public List<Option> getAvailableOptions() {
        return availableOptions;
    }

    public void setAvailableOptions(List<Option> availableOptions) {
        this.availableOptions = availableOptions == null ? new ArrayList<>() : new ArrayList<>(availableOptions);
    }

    public Option getBossOption() {
        return bossOption;
    }

    public void setBossOption(Option bossOption) {
        this.bossOption = bossOption;
    }
}
