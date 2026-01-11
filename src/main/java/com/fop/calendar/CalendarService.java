package com.fop.calendar;

import com.fop.calendar.model.AdditionalFields;
import com.fop.calendar.model.Event;
import com.fop.calendar.model.RecurringEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class CalendarService {
    private DataManager dataManager;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public CalendarService() { this.dataManager = new DataManager(); }
    public CalendarService(String userId) {
        this.dataManager = new DataManager(userId);
        regenerateAllRecurringInstances();
    }

    public List<Event> getAllEvents() { return dataManager.readEvents(); }

    private void regenerateAllRecurringInstances() {
        List<RecurringEvent> configs = dataManager.readRecurring();
        if (configs.isEmpty()) return;

        List<Event> allEvents = getAllEvents();
        Set<Integer> rootIds = configs.stream().map(RecurringEvent::getEventId).collect(Collectors.toSet());
        List<Event> toKeep = new ArrayList<>();

        for (Event e : allEvents) {
            if (rootIds.contains(e.getEventId())) {
                toKeep.add(e);
            } else if (!isRecurringInstance(e, configs)) {
                toKeep.add(e);
            }
        }

        dataManager.writeEvents(toKeep);
        configs.forEach(c -> generateRecurringInstances(c.getEventId()));
    }

    private boolean isRecurringInstance(Event event, List<RecurringEvent> configs) {
        for (RecurringEvent config : configs) {
            Event root = getEventById(config.getEventId());
            if (root != null && event.getTitle().equals(root.getTitle()) &&
                event.getStartDateTime().isAfter(root.getStartDateTime())) {
                long daysDiff = java.time.Duration.between(root.getStartDateTime(), event.getStartDateTime()).toDays();
                int intervalDays = config.getIntervalInDays();
                if (intervalDays > 0 && daysDiff > 0 && daysDiff % intervalDays == 0) return true;
            }
        }
        return false;
    }

    private int getNextEventId() {
        List<Event> events = getAllEvents();
        return events.isEmpty() ? 1 : events.stream().mapToInt(Event::getEventId).max().orElse(0) + 1;
    }

    public boolean createEvent(String title, String description, LocalDateTime start, LocalDateTime end) {
        if (start.isAfter(end)) return false;
        List<Event> events = getAllEvents();
        events.add(new Event(getNextEventId(), title, description, start, end));
        dataManager.writeEvents(events);
        return true;
    }

    public boolean updateEvent(int eventId, String title, String description, LocalDateTime start, LocalDateTime end) {
        if (start.isAfter(end)) return false;
        int rootId = findRootEventId(eventId);
        boolean isRecurring = dataManager.readRecurring().stream().anyMatch(r -> r.getEventId() == rootId);

        List<Event> events = getAllEvents();
        if (isRecurring) {
            Event oldRoot = null;
            for (int i = 0; i < events.size(); i++) {
                if (events.get(i).getEventId() == rootId) {
                    oldRoot = events.get(i);
                    events.set(i, new Event(rootId, title, description, start, end));
                    dataManager.writeEvents(events);
                    break;
                }
            }
            if (oldRoot != null) {
                removeAllInstancesForRecurringSeries(rootId, oldRoot);
                generateRecurringInstances(rootId);
            }
            return oldRoot != null;
        } else {
            for (int i = 0; i < events.size(); i++) {
                if (events.get(i).getEventId() == eventId) {
                    events.set(i, new Event(eventId, title, description, start, end));
                    dataManager.writeEvents(events);
                    return true;
                }
            }
            return false;
        }
    }

    public boolean deleteEvent(int eventId) {
        int rootId = findRootEventId(eventId);
        boolean isRecurring = (rootId != eventId) || dataManager.readRecurring().stream().anyMatch(r -> r.getEventId() == eventId);

        List<Event> events = getAllEvents();
        if (isRecurring) {
            Event root = getEventById(rootId);
            if (root != null) {
                events.removeIf(e -> e.getEventId() == rootId ||
                    (e.getTitle().equals(root.getTitle()) && e.getStartDateTime().isAfter(root.getStartDateTime())));
                dataManager.writeEvents(events);
                removeRecurringEvent(rootId);
                removeAdditionalFields(rootId);
                return true;
            }
            return false;
        } else {
            boolean removed = events.removeIf(e -> e.getEventId() == eventId);
            if (removed) {
                dataManager.writeEvents(events);
                removeRecurringEvent(eventId);
                removeAdditionalFields(eventId);
            }
            return removed;
        }
    }

    public Event getEventById(int eventId) {
        return getAllEvents().stream().filter(e -> e.getEventId() == eventId).findFirst().orElse(null);
    }

    public int findRootEventId(int eventId) {
        Event event = getEventById(eventId);
        if (event == null) return eventId;

        List<RecurringEvent> configs = dataManager.readRecurring();
        for (RecurringEvent config : configs) {
            if (config.getEventId() == eventId) return eventId;
        }

        for (RecurringEvent config : configs) {
            Event root = getEventById(config.getEventId());
            if (root != null && event.getTitle().equals(root.getTitle()) &&
                event.getStartDateTime().isAfter(root.getStartDateTime())) {
                long daysDiff = java.time.Duration.between(
                    root.getStartDateTime().toLocalDate().atStartOfDay(),
                    event.getStartDateTime().toLocalDate().atStartOfDay()).toDays();
                int intervalDays = config.getIntervalInDays();
                if (intervalDays > 0 && daysDiff > 0 && daysDiff % intervalDays == 0) return config.getEventId();
            }
        }
        return eventId;
    }

    public boolean createRecurringEvent(String title, String description, LocalDateTime start, LocalDateTime end, String interval, int times) {
        if (!createEvent(title, description, start, end)) return false;
        int rootId = getAllEvents().stream().mapToInt(Event::getEventId).max().orElse(0);
        List<RecurringEvent> recurring = dataManager.readRecurring();
        recurring.add(new RecurringEvent(rootId, interval, times, "0"));
        dataManager.writeRecurring(recurring);
        generateRecurringInstances(rootId);
        return true;
    }

    private void removeAllInstancesForRecurringSeries(int rootId, Event root) {
        List<Event> events = getAllEvents();
        RecurringEvent config = dataManager.readRecurring().stream()
            .filter(r -> r.getEventId() == rootId).findFirst().orElse(null);
        if (config == null) return;

        int intervalDays = config.getIntervalInDays();
        LocalDateTime rootStart = root.getStartDateTime();
        events.removeIf(e -> {
            if (e.getEventId() == rootId || !e.getTitle().equals(root.getTitle())) return false;
            long daysDiff = java.time.Duration.between(rootStart.toLocalDate().atStartOfDay(),
                e.getStartDateTime().toLocalDate().atStartOfDay()).toDays();
            return intervalDays > 0 && daysDiff != 0 && daysDiff % intervalDays == 0;
        });
        dataManager.writeEvents(events);
    }

    public void generateRecurringInstances(int rootId) {
        Event root = getEventById(rootId);
        if (root == null) return;
        RecurringEvent config = dataManager.readRecurring().stream()
            .filter(r -> r.getEventId() == rootId).findFirst().orElse(null);
        if (config == null) return;

        List<Event> events = getAllEvents();
        LocalDateTime rootStart = root.getStartDateTime();
        int intervalDays = config.getIntervalInDays();

        List<Event> toRemove = new ArrayList<>();
        for (Event e : events) {
            if (e.getEventId() == rootId || !e.getTitle().equals(root.getTitle())) continue;
            long daysDiff = java.time.Duration.between(rootStart.toLocalDate().atStartOfDay(),
                e.getStartDateTime().toLocalDate().atStartOfDay()).toDays();
            if (intervalDays > 0 && daysDiff != 0 && daysDiff % intervalDays == 0) toRemove.add(e);
        }
        events.removeAll(toRemove);

        int maxTimes = config.getRecurrentTimes();
        if (maxTimes <= 0) {
            dataManager.writeEvents(events);
            return;
        }

        LocalDateTime currentStart = root.getStartDateTime();
        LocalDateTime currentEnd = root.getEndDateTime();
        long durationMinutes = java.time.Duration.between(currentStart, currentEnd).toMinutes();
        int nextId = getNextEventId();

        for (int i = 0; i < maxTimes; i++) {
            currentStart = currentStart.plusDays(intervalDays);
            currentEnd = currentStart.plusMinutes(durationMinutes);
            events.add(new Event(nextId++, root.getTitle(), root.getDescription(), currentStart, currentEnd));
        }
        dataManager.writeEvents(events);
    }

    private void removeRecurringEvent(int eventId) {
        List<RecurringEvent> recurring = dataManager.readRecurring();
        recurring.removeIf(r -> r.getEventId() == eventId);
        dataManager.writeRecurring(recurring);
    }

    public void updateRecurringEventConfig(int eventId, String interval, int times) {
        List<RecurringEvent> recurring = dataManager.readRecurring();
        for (int i = 0; i < recurring.size(); i++) {
            if (recurring.get(i).getEventId() == eventId) {
                recurring.set(i, new RecurringEvent(eventId, interval, times, "0"));
                dataManager.writeRecurring(recurring);
                generateRecurringInstances(eventId);
                return;
            }
        }
    }

    public void addRecurringEventConfig(int eventId, String interval, int times) {
        List<RecurringEvent> recurring = dataManager.readRecurring();
        recurring.add(new RecurringEvent(eventId, interval, times, "0"));
        dataManager.writeRecurring(recurring);
        generateRecurringInstances(eventId);
    }

    public void removeRecurringEventConfig(int eventId) {
        removeRecurringEvent(eventId);
        List<Event> events = getAllEvents();
        Event root = getEventById(eventId);
        if (root != null) {
            events.removeIf(e -> e.getEventId() != eventId &&
                e.getTitle().equals(root.getTitle()) && e.getStartDateTime().isAfter(root.getStartDateTime()));
            dataManager.writeEvents(events);
        }
    }

    public List<Event> searchByDate(LocalDate date) {
        LocalDateTime now = LocalDateTime.now();
        return getAllEvents().stream()
            .filter(e -> e.getStartDateTime().toLocalDate().equals(date))
            .filter(e -> e.getStartDateTime().isAfter(now) || e.getStartDateTime().isEqual(now))
            .collect(Collectors.toList());
    }

    public List<Event> searchByDateRange(LocalDate start, LocalDate end) {
        LocalDateTime now = LocalDateTime.now();
        return getAllEvents().stream()
            .filter(e -> {
                LocalDate eventDate = e.getStartDateTime().toLocalDate();
                return !eventDate.isBefore(start) && !eventDate.isAfter(end);
            })
            .filter(e -> e.getStartDateTime().isAfter(now) || e.getStartDateTime().isEqual(now))
            .collect(Collectors.toList());
    }

    public List<Event> searchByDateRangeIncludingPast(LocalDate start, LocalDate end) {
        return getAllEvents().stream().filter(e -> {
            LocalDate eventDate = e.getStartDateTime().toLocalDate();
            return !eventDate.isBefore(start) && !eventDate.isAfter(end);
        }).collect(Collectors.toList());
    }

    public List<Event> searchByTitle(String keyword) {
        LocalDateTime now = LocalDateTime.now();
        String lower = keyword.toLowerCase();
        return getAllEvents().stream()
            .filter(e -> e.getTitle().toLowerCase().contains(lower))
            .filter(e -> e.getStartDateTime().isAfter(now) || e.getStartDateTime().isEqual(now))
            .collect(Collectors.toList());
    }

    public List<Event> filterByCategory(String category) {
        Set<Integer> eventIds = dataManager.readAdditional().stream()
            .filter(af -> category.equalsIgnoreCase(af.getCategory())).map(AdditionalFields::getEventId).collect(Collectors.toSet());
        return getAllEvents().stream().filter(e -> eventIds.contains(e.getEventId())).collect(Collectors.toList());
    }

    public int getTotalEvents() {
        return getAllEvents().size();
    }

    public int getUpcomingEventsCount() {
        LocalDateTime now = LocalDateTime.now();
        return (int) getAllEvents().stream().filter(e -> e.getStartDateTime().isAfter(now)).count();
    }

    public int getPastEventsCount() {
        LocalDateTime now = LocalDateTime.now();
        return (int) getAllEvents().stream().filter(e -> e.getEndDateTime().isBefore(now)).count();
    }

    public String getBusiestDate() {
        Map<LocalDate, Long> counts = getAllEvents().stream()
            .collect(Collectors.groupingBy(e -> e.getStartDateTime().toLocalDate(), Collectors.counting()));
        return counts.entrySet().stream().max(Map.Entry.comparingByValue())
            .map(e -> e.getKey().toString() + " (" + e.getValue() + " event(s))").orElse("No events");
    }

    public Map<String, Long> getCategoryBreakdown() {
        Map<String, Long> counts = new HashMap<>();
        for (Event event : getAllEvents()) {
            String category = getCategoryForEvent(event.getEventId());
            String cat = (category != null && !category.isEmpty()) ? category : "Uncategorized";
            counts.put(cat, counts.getOrDefault(cat, 0L) + 1);
        }
        return counts;
    }

    private String getCategoryForEvent(int eventId) {
        AdditionalFields fields = getAdditionalFields(eventId);
        return (fields != null && fields.getCategory() != null && !fields.getCategory().isEmpty()) ? fields.getCategory() : null;
    }

    public List<Event> checkConflict(LocalDateTime start, LocalDateTime end) {
        Event temp = new Event(0, "", "", start, end);
        return getAllEvents().stream().filter(e -> temp.overlapsWith(e)).collect(Collectors.toList());
    }

    public List<Event> checkReminders(int minutes) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusMinutes(minutes);
        return getAllEvents().stream()
            .filter(e -> e.getStartDateTime().isAfter(now) && e.getStartDateTime().isBefore(threshold))
            .sorted(Comparator.comparing(Event::getStartDateTime)).collect(Collectors.toList());
    }

    public List<Event> checkIndividualReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<Event> dueReminders = new ArrayList<>();

        for (Event event : getAllEvents()) {
            if (!event.getStartDateTime().isAfter(now)) continue;

            AdditionalFields fields = getAdditionalFields(event.getEventId());
            if (fields != null && fields.getPriority() != null && !fields.getPriority().isEmpty()) {
                try {
                    int reminderMinutes = Integer.parseInt(fields.getPriority().trim());
                    if (reminderMinutes <= 0) continue;

                    LocalDateTime reminderTime = event.getStartDateTime().minusMinutes(reminderMinutes);
                    long secondsUntilReminder = java.time.Duration.between(now, reminderTime).getSeconds();

                    if ((secondsUntilReminder <= 15 && secondsUntilReminder >= 0) ||
                        (secondsUntilReminder < 0 && secondsUntilReminder >= -120)) {
                        dueReminders.add(event);
                    }
                } catch (NumberFormatException e) {}
            }
        }

        return dueReminders.stream()
            .sorted(Comparator.comparing(Event::getStartDateTime))
            .collect(Collectors.toList());
    }

    public AdditionalFields getAdditionalFields(int eventId) {
        AdditionalFields fields = dataManager.readAdditional().stream()
            .filter(af -> af.getEventId() == eventId).findFirst().orElse(null);
        if (fields != null) return fields;
        int rootId = findRootEventId(eventId);
        if (rootId != eventId) {
            return dataManager.readAdditional().stream()
                .filter(af -> af.getEventId() == rootId).findFirst().orElse(null);
        }
        return null;
    }

    public RecurringEvent getRecurringEventConfig(int eventId) {
        return dataManager.readRecurring().stream()
            .filter(r -> r.getEventId() == eventId).findFirst().orElse(null);
    }

    public void saveAdditionalFields(AdditionalFields fields) {
        List<AdditionalFields> all = dataManager.readAdditional();
        all.removeIf(af -> af.getEventId() == fields.getEventId());
        all.add(fields);
        dataManager.writeAdditional(all);
    }

    private void removeAdditionalFields(int eventId) {
        List<AdditionalFields> all = dataManager.readAdditional();
        all.removeIf(af -> af.getEventId() == eventId);
        dataManager.writeAdditional(all);
    }

    public String backupData(String path) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("=== EVENTS ===\n").append("eventId,title,description,startDateTime,endDateTime\n");
            getAllEvents().forEach(e -> sb.append(e.toString()).append("\n"));
            sb.append("\n=== RECURRING ===\n").append("eventId,recurrentInterval,recurrentTimes,recurrentEndDate\n");
            dataManager.readRecurring().forEach(r -> sb.append(r.toString()).append("\n"));
            sb.append("\n=== ADDITIONAL ===\n").append("eventId,location,attendees,category,priority\n");
            dataManager.readAdditional().forEach(a -> sb.append(a.toString()).append("\n"));
            java.nio.file.Files.write(java.nio.file.Paths.get(path), sb.toString().getBytes());
            return path;
        } catch (Exception e) { return null; }
    }

    public boolean restoreData(String path, boolean append) {
        try {
            if (!append) {
                dataManager.writeEvents(new ArrayList<>());
                dataManager.writeRecurring(new ArrayList<>());
                dataManager.writeAdditional(new ArrayList<>());
            }
            List<String> lines = java.nio.file.Files.readAllLines(java.nio.file.Paths.get(path));
            String section = "";
            List<Event> events = append ? getAllEvents() : new ArrayList<>();
            List<RecurringEvent> recurring = append ? dataManager.readRecurring() : new ArrayList<>();
            List<AdditionalFields> additional = append ? dataManager.readAdditional() : new ArrayList<>();

            for (String line : lines) {
                if (line.startsWith("===")) { section = line; continue; }
                if (line.trim().isEmpty() || line.contains("eventId")) continue;
                String[] parts = line.split(",");
                try {
                    if (section.contains("EVENTS") && parts.length >= 5) {
                        events.add(new Event(Integer.parseInt(parts[0].trim()), parts[1].trim(), parts[2].trim(),
                            LocalDateTime.parse(parts[3].trim(), DATE_TIME_FORMATTER),
                            LocalDateTime.parse(parts[4].trim(), DATE_TIME_FORMATTER)));
                    } else if (section.contains("RECURRING") && parts.length >= 4) {
                        recurring.add(new RecurringEvent(Integer.parseInt(parts[0].trim()), parts[1].trim(),
                            Integer.parseInt(parts[2].trim()), parts[3].trim()));
                    } else if (section.contains("ADDITIONAL") && parts.length >= 5) {
                        additional.add(new AdditionalFields(Integer.parseInt(parts[0].trim()), parts[1].trim(),
                            parts[2].trim(), parts[3].trim(), parts[4].trim()));
                    }
                } catch (Exception e) {}
            }
            dataManager.writeEvents(events);
            dataManager.writeRecurring(recurring);
            dataManager.writeAdditional(additional);
            return true;
        } catch (Exception e) { return false; }
    }

    public List<String> findAllConflicts() {
        List<Event> events = getAllEvents();
        List<String> conflicts = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            for (int j = i + 1; j < events.size(); j++) {
                if (events.get(i).overlapsWith(events.get(j))) {
                    conflicts.add(String.format("Event %d (%s) conflicts with Event %d (%s)",
                        events.get(i).getEventId(), events.get(i).getTitle(),
                        events.get(j).getEventId(), events.get(j).getTitle()));
                }
            }
        }
        return conflicts;
    }
}
