package com.wifioptimizer;

import java.util.UUID;

public class Schedule {
    private String id;
    private int startHour;
    private int startMinute;
    private int endHour;
    private int endMinute;
    private boolean isEnabled;

    public Schedule() {
        // Required for JSON deserialization
    }

    public Schedule(int startHour, int startMinute, int endHour, int endMinute) {
        this.id = UUID.randomUUID().toString();
        this.startHour = startHour;
        this.startMinute = startMinute;
        this.endHour = endHour;
        this.endMinute = endMinute;
        this.isEnabled = true;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getStartHour() { return startHour; }
    public void setStartHour(int startHour) { this.startHour = startHour; }

    public int getStartMinute() { return startMinute; }
    public void setStartMinute(int startMinute) { this.startMinute = startMinute; }

    public int getEndHour() { return endHour; }
    public void setEndHour(int endHour) { this.endHour = endHour; }

    public int getEndMinute() { return endMinute; }
    public void setEndMinute(int endMinute) { this.endMinute = endMinute; }

    public boolean isEnabled() { return isEnabled; }
    public void setEnabled(boolean enabled) { isEnabled = enabled; }
}
