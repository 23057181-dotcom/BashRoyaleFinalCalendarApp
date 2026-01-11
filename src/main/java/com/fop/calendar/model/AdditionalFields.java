package com.fop.calendar.model;

public class AdditionalFields {
    private int eventId;
    private String location;
    private String attendees;
    private String category;
    private String priority;

    public AdditionalFields() {}

    public AdditionalFields(int eventId, String location, String attendees, String category, String priority) {
        this.eventId = eventId;
        this.location = location;
        this.attendees = attendees;
        this.category = category;
        this.priority = priority;
    }

    public int getEventId() { return eventId; }
    public void setEventId(int eventId) { this.eventId = eventId; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getAttendees() { return attendees; }
    public void setAttendees(String attendees) { this.attendees = attendees; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    @Override
    public String toString() {
        return String.format("%d, %s, %s, %s, %s", eventId, location, attendees, category, priority);
    }
}
