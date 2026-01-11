package com.fop.calendar.model;

public class RecurringEvent {
    private int eventId;
    private String recurrentInterval;
    private int recurrentTimes;
    private String recurrentEndDate;

    public RecurringEvent() {}

    public RecurringEvent(int eventId, String recurrentInterval, int recurrentTimes, String recurrentEndDate) {
        this.eventId = eventId;
        this.recurrentInterval = recurrentInterval;
        this.recurrentTimes = recurrentTimes;
        this.recurrentEndDate = recurrentEndDate;
    }

    public int getEventId() { return eventId; }
    public void setEventId(int eventId) { this.eventId = eventId; }
    public String getRecurrentInterval() { return recurrentInterval; }
    public void setRecurrentInterval(String recurrentInterval) { this.recurrentInterval = recurrentInterval; }
    public int getRecurrentTimes() { return recurrentTimes; }
    public void setRecurrentTimes(int recurrentTimes) { this.recurrentTimes = recurrentTimes; }
    public String getRecurrentEndDate() { return recurrentEndDate; }
    public void setRecurrentEndDate(String recurrentEndDate) { this.recurrentEndDate = recurrentEndDate; }

    public int getIntervalInDays() {
        if (recurrentInterval == null || recurrentInterval.isEmpty()) return 0;
        String interval = recurrentInterval.toLowerCase().trim();
        if (interval.endsWith("d")) return Integer.parseInt(interval.substring(0, interval.length() - 1));
        if (interval.endsWith("w")) return Integer.parseInt(interval.substring(0, interval.length() - 1)) * 7;
        if (interval.endsWith("m")) return Integer.parseInt(interval.substring(0, interval.length() - 1)) * 30;
        return 0;
    }

    @Override
    public String toString() {
        return String.format("%d, %s, %d, %s", eventId, recurrentInterval, recurrentTimes, recurrentEndDate);
    }
}
