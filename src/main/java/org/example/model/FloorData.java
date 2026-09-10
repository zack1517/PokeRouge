package org.example.model;

import java.util.ArrayList;
import java.util.List;

public class FloorData {
    private int floorNumber;
    private int startingPoints;
    private List<Option> availableOptions = new ArrayList<>();
    private Option bossOption;

    public FloorData() {
    }

    public FloorData(int floorNumber, int startingPoints, List<Option> availableOptions, Option bossOption) {
        this.floorNumber = floorNumber;
        this.startingPoints = startingPoints;
        this.availableOptions = availableOptions == null ? new ArrayList<>() : new ArrayList<>(availableOptions);
        this.bossOption = bossOption;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public void setFloorNumber(int floorNumber) {
        this.floorNumber = floorNumber;
    }

    public int getStartingPoints() {
        return startingPoints;
    }

    public void setStartingPoints(int startingPoints) {
        this.startingPoints = startingPoints;
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
