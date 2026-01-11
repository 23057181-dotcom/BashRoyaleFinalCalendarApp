package com.fop.calendar;

import com.fop.calendar.model.AdditionalFields;
import com.fop.calendar.model.Event;
import com.fop.calendar.model.RecurringEvent;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class DataManager {
    private static final String DATA_DIR = "data";
    private static final String EVENT_FILE = "event.csv";
    private static final String RECURRENT_FILE = "recurrent.csv";
    private static final String ADDITIONAL_FILE = "additional.csv";
    
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private String userId;

    public DataManager() { this(null); }

    public DataManager(String userId) {
        this.userId = userId;
        ensureDataDirectoryExists();
    }

    private void ensureDataDirectoryExists() {
        File dir = new File(getDataDirectory());
        if (!dir.exists()) dir.mkdirs();
    }

    private String getDataDirectory() {
        return (userId != null && !userId.isEmpty()) ? 
            DATA_DIR + File.separator + "users" + File.separator + userId : DATA_DIR;
    }

    private String getDataPath(String filename) {
        return getDataDirectory() + File.separator + filename;
    }

    public List<Event> readEvents() {
        List<Event> events = new ArrayList<>();
        String filePath = getDataPath(EVENT_FILE);
        if (!Files.exists(Paths.get(filePath))) return events;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            if (reader.readLine() == null) return events;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = parseCSVLine(line);
                if (parts.length >= 5) {
                    try {
                        events.add(new Event(Integer.parseInt(parts[0].trim()), parts[1].trim(), parts[2].trim(),
                            LocalDateTime.parse(parts[3].trim(), DATE_TIME_FORMATTER), LocalDateTime.parse(parts[4].trim(), DATE_TIME_FORMATTER)));
                    } catch (Exception e) { System.err.println("Error parsing event line: " + line); }
                }
            }
        } catch (IOException e) { System.err.println("Error reading events file: " + e.getMessage()); }
        return events;
    }

    public void writeEvents(List<Event> events) {
        String filePath = getDataPath(EVENT_FILE);
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("eventId,title,description,startDateTime,endDateTime");
            for (Event event : events) {
                writer.println(String.format("%d,%s,%s,%s,%s", event.getEventId(), escapeCSV(event.getTitle()),
                    escapeCSV(event.getDescription()), event.getStartDateTime().format(DATE_TIME_FORMATTER),
                    event.getEndDateTime().format(DATE_TIME_FORMATTER)));
            }
        } catch (IOException e) { System.err.println("Error writing events file: " + e.getMessage()); }
    }

    public List<RecurringEvent> readRecurring() {
        List<RecurringEvent> recurringEvents = new ArrayList<>();
        String filePath = getDataPath(RECURRENT_FILE);
        if (!Files.exists(Paths.get(filePath))) return recurringEvents;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            if (reader.readLine() == null) return recurringEvents;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = parseCSVLine(line);
                if (parts.length >= 4) {
                    try {
                        recurringEvents.add(new RecurringEvent(Integer.parseInt(parts[0].trim()), parts[1].trim(),
                            Integer.parseInt(parts[2].trim()), parts[3].trim()));
                    } catch (Exception e) { System.err.println("Error parsing recurring event line: " + line); }
                }
            }
        } catch (IOException e) { System.err.println("Error reading recurring events file: " + e.getMessage()); }
        return recurringEvents;
    }

    public void writeRecurring(List<RecurringEvent> recurringEvents) {
        String filePath = getDataPath(RECURRENT_FILE);
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("eventId,recurrentInterval,recurrentTimes,recurrentEndDate");
            for (RecurringEvent recurring : recurringEvents) {
                writer.println(String.format("%d,%s,%d,%s", recurring.getEventId(),
                    recurring.getRecurrentInterval(), recurring.getRecurrentTimes(),
                    recurring.getRecurrentEndDate()));
            }
        } catch (IOException e) { System.err.println("Error writing recurring events file: " + e.getMessage()); }
    }

    public List<AdditionalFields> readAdditional() {
        List<AdditionalFields> additionalFieldsList = new ArrayList<>();
        String filePath = getDataPath(ADDITIONAL_FILE);
        if (!Files.exists(Paths.get(filePath))) return additionalFieldsList;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            if (reader.readLine() == null) return additionalFieldsList;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = parseCSVLine(line);
                if (parts.length >= 5) {
                    try {
                        additionalFieldsList.add(new AdditionalFields(Integer.parseInt(parts[0].trim()), parts[1].trim(),
                            parts[2].trim(), parts[3].trim(), parts[4].trim()));
                    } catch (Exception e) { System.err.println("Error parsing additional fields line: " + line); }
                }
            }
        } catch (IOException e) { System.err.println("Error reading additional fields file: " + e.getMessage()); }
        return additionalFieldsList;
    }

    public void writeAdditional(List<AdditionalFields> additionalFieldsList) {
        String filePath = getDataPath(ADDITIONAL_FILE);
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("eventId,location,attendees,category,priority");
            for (AdditionalFields fields : additionalFieldsList) {
                writer.println(String.format("%d,%s,%s,%s,%s", fields.getEventId(),
                    escapeCSV(fields.getLocation()), escapeCSV(fields.getAttendees()),
                    escapeCSV(fields.getCategory()), escapeCSV(fields.getPriority())));
            }
        } catch (IOException e) { System.err.println("Error writing additional fields file: " + e.getMessage()); }
    }

    private String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') inQuotes = !inQuotes;
            else if (c == ',' && !inQuotes) { fields.add(currentField.toString()); currentField = new StringBuilder(); }
            else currentField.append(c);
        }
        fields.add(currentField.toString());
        return fields.toArray(new String[0]);
    }

    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }-
}
