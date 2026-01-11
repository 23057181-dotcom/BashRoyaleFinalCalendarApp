package com.fop.calendar.gui;

import com.fop.calendar.CalendarService;
import com.fop.calendar.model.Event;
import com.fop.calendar.model.AdditionalFields;
import com.fop.calendar.model.RecurringEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Line;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class EventFormController {
    private CalendarService calendarService;
    private MainController mainController;
    private Event currentEvent;
    private LocalDate selectedDate;
    
    @FXML private TextField titleField, startTimeField, endTimeField, locationField, attendeesField, reminderField;
    @FXML private TextArea descriptionField;
    @FXML private Label dateLabel;
    @FXML private Button changeDateButton, saveButton, cancelButton;
    @FXML private ComboBox<String> categoryComboBox;
    @FXML private CheckBox recurringCheckBox;
    @FXML private javafx.scene.layout.HBox recurringFieldsBox;
    @FXML private TextField intervalField, timesField;
    
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public void setCalendarService(CalendarService calendarService) { this.calendarService = calendarService; }
    public void setMainController(MainController mainController) { this.mainController = mainController; }

    public void setEvent(Event event) {
        this.currentEvent = event;
        if (event != null && calendarService != null) {
            titleField.setText(event.getTitle());
            descriptionField.setText(event.getDescription());
            selectedDate = event.getStartDateTime().toLocalDate();
            dateLabel.setText("Date: " + selectedDate.toString());
            startTimeField.setText(event.getStartDateTime().toLocalTime().format(TIME_FORMATTER));
            endTimeField.setText(event.getEndDateTime().toLocalTime().format(TIME_FORMATTER));
            
            AdditionalFields fields = calendarService.getAdditionalFields(event.getEventId());
            if (fields != null) {
                locationField.setText(fields.getLocation() != null ? fields.getLocation() : "");
                attendeesField.setText(fields.getAttendees() != null ? fields.getAttendees() : "");
                if (fields.getCategory() != null && !fields.getCategory().isEmpty()) categoryComboBox.setValue(fields.getCategory());
                if (fields.getPriority() != null && !fields.getPriority().isEmpty()) reminderField.setText(fields.getPriority());
            }
            
            RecurringEvent recurringConfig = calendarService.getRecurringEventConfig(event.getEventId());
            if (recurringConfig != null) {
                recurringCheckBox.setSelected(true);
                recurringFieldsBox.setVisible(true);
                recurringFieldsBox.setManaged(true);
                intervalField.setText(recurringConfig.getRecurrentInterval());
                timesField.setText(String.valueOf(recurringConfig.getRecurrentTimes()));
            } else {
                recurringCheckBox.setSelected(false);
                recurringFieldsBox.setVisible(false);
                recurringFieldsBox.setManaged(false);
            }
        }
    }

    public void setSelectedDate(LocalDate date) {
        this.selectedDate = date;
        dateLabel.setText("Date: " + date.toString());
        dateLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14pt; -fx-text-fill: #FFC107;");
    }

    @FXML
    public void initialize() {
        categoryComboBox.getItems().addAll("Personal", "Work", "Meeting", "Others");
        categoryComboBox.setEditable(true);
        startTimeField.setText("09:00");
        endTimeField.setText("10:00");
        reminderField.setText("15");
        recurringFieldsBox.setVisible(false);
        recurringFieldsBox.setManaged(false);
        recurringCheckBox.setOnAction(e -> {
            boolean selected = recurringCheckBox.isSelected();
            recurringFieldsBox.setVisible(selected);
            recurringFieldsBox.setManaged(selected);
        });
        if (changeDateButton != null) changeDateButton.setOnAction(e -> openDatePicker());
        saveButton.setOnAction(e -> saveEvent());
        cancelButton.setOnAction(e -> closeWindow());
    }

    private void openDatePicker() {
        Dialog<LocalDate> datePickerDialog = new Dialog<>();
        datePickerDialog.setTitle("Select Date");
        datePickerDialog.setHeaderText("Choose a new date for the event");
        
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10);
        javafx.scene.control.Label headerLabel = new javafx.scene.control.Label("Choose a new date for the event");
        headerLabel.setStyle("-fx-text-fill: #FFC107; -fx-font-size: 14pt; -fx-font-weight: bold;");
        
        javafx.scene.layout.HBox dateContainer = new javafx.scene.layout.HBox(15);
        dateContainer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        
        DatePicker datePicker = new DatePicker(selectedDate);
        datePicker.setPrefWidth(200);
        datePicker.getStyleClass().add("dialog-date-picker");
        
        javafx.scene.control.Button calendarButton = new javafx.scene.control.Button();
        calendarButton.setGraphic(createCalendarIcon());
        calendarButton.getStyleClass().add("dialog-calendar-button");
        calendarButton.setTooltip(new Tooltip("Open calendar picker"));
        calendarButton.setOnAction(e -> datePicker.show());
        
        dateContainer.getChildren().addAll(datePicker, calendarButton);
        content.getChildren().addAll(headerLabel, dateContainer);
        
        datePickerDialog.getDialogPane().setContent(content);
        datePickerDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        datePickerDialog.getDialogPane().setStyle("-fx-background-color: #000000;");
        try {
            datePickerDialog.getDialogPane().getStylesheets().add(getClass().getResource("/com/fop/calendar/styles.css").toExternalForm());
        } catch (Exception e) {
            // CSS file not found, continue without it
        }
        datePickerDialog.getDialogPane().getStyleClass().add("dialog-yellow-black");
        
        datePickerDialog.setResultConverter(dialogButton -> (dialogButton == ButtonType.OK) ? datePicker.getValue() : null);
        datePickerDialog.showAndWait().ifPresent(this::setSelectedDate);
    }

    private void saveEvent() {
        try {
            if (titleField.getText().trim().isEmpty()) { showError("Title is required!"); return; }
            if (selectedDate == null) { showError("Date must be selected!"); return; }
            LocalTime startTime = LocalTime.parse(startTimeField.getText(), TIME_FORMATTER);
            LocalTime endTime = LocalTime.parse(endTimeField.getText(), TIME_FORMATTER);
            if (endTime.isBefore(startTime) || endTime.equals(startTime)) { showError("End time must be after start time!"); return; }

            LocalDateTime startDateTime = LocalDateTime.of(selectedDate, startTime);
            LocalDateTime endDateTime = LocalDateTime.of(selectedDate, endTime);
            
            List<Event> conflicts = calendarService.checkConflict(startDateTime, endDateTime);
            if (currentEvent != null) {
                conflicts.removeIf(e -> e.getEventId() == currentEvent.getEventId());
            }
            
            if (!conflicts.isEmpty()) {
                StringBuilder conflictMsg = new StringBuilder("Time conflict detected! The following event(s) overlap with this time:\n\n");
                for (Event conflict : conflicts) {
                    conflictMsg.append("• ").append(conflict.getTitle())
                        .append(" (").append(conflict.getStartDateTime().toLocalDate())
                        .append(" ").append(conflict.getStartDateTime().toLocalTime())
                        .append(" - ").append(conflict.getEndDateTime().toLocalTime()).append(")\n");
                }
                conflictMsg.append("\nDo you want to continue anyway?");
                
                Alert conflictAlert = new Alert(Alert.AlertType.CONFIRMATION);
                conflictAlert.setTitle("Time Conflict Detected");
                conflictAlert.setHeaderText("Conflicting Events Found");
                conflictAlert.setContentText(conflictMsg.toString());
                
                Optional<ButtonType> result = conflictAlert.showAndWait();
                if (result.isEmpty() || result.get() != ButtonType.OK) {
                    return;
                }
            }
            
            String title = titleField.getText();
            String description = descriptionField.getText();
            String location = locationField.getText();
            String attendees = attendeesField.getText();
            String category = categoryComboBox.getValue() != null ? categoryComboBox.getValue() : "";
            String reminderMinutes = reminderField.getText().trim();
            
            boolean success;
            if (currentEvent == null) {
                if (recurringCheckBox.isSelected()) {
                    String interval = intervalField.getText();
                    int times = timesField.getText().isEmpty() ? 0 : Integer.parseInt(timesField.getText());
                    if (times <= 0) { showError("Please enter a valid number of times for recurring event (must be greater than 0)"); return; }
                    success = calendarService.createRecurringEvent(title, description, startDateTime, endDateTime, interval, times);
                } else {
                    success = calendarService.createEvent(title, description, startDateTime, endDateTime);
                }
                
                if (success) {
                    Event newEvent = calendarService.getAllEvents().stream()
                        .filter(e -> e.getTitle().equals(title) && e.getStartDateTime().equals(startDateTime))
                        .findFirst().orElse(null);
                    
                    if (newEvent != null) {
                        calendarService.saveAdditionalFields(new AdditionalFields(newEvent.getEventId(), location, attendees, category, reminderMinutes));
                    }
                }
            } else {
                success = calendarService.updateEvent(currentEvent.getEventId(), title, description, startDateTime, endDateTime);
                
                if (success) {
                    int rootEventId = calendarService.findRootEventId(currentEvent.getEventId());
                    calendarService.saveAdditionalFields(new AdditionalFields(rootEventId, location, attendees, category, reminderMinutes));
                    
                    RecurringEvent existingRecurring = calendarService.getRecurringEventConfig(rootEventId);
                    
                    if (recurringCheckBox.isSelected()) {
                        String interval = intervalField.getText();
                        int times = timesField.getText().isEmpty() ? 0 : Integer.parseInt(timesField.getText());
                        if (times <= 0) { showError("Please enter a valid number of times for recurring event (must be greater than 0)"); return; }
                        if (existingRecurring != null) {
                            calendarService.updateRecurringEventConfig(rootEventId, interval, times);
                        } else {
                            calendarService.addRecurringEventConfig(rootEventId, interval, times);
                        }
                    } else {
                        if (existingRecurring != null) {
                            calendarService.removeRecurringEventConfig(rootEventId);
                        }
                    }
                }
            }
            
            if (success) {
                showAlert(Alert.AlertType.INFORMATION, "Success", "Event saved successfully!", "");
                if (mainController != null) mainController.refreshCalendar();
                closeWindow();
            } else {
                showError("Failed to save event. Please check your input.");
            }
        } catch (Exception e) {
            showError("Invalid input format. Time should be in HH:mm format (e.g., 09:00, 14:30)");
        }
    }

    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showError(String message) { showAlert(Alert.AlertType.ERROR, "Error", "Invalid Input", message); }
    
    private javafx.scene.Node createCalendarIcon() {
        StackPane iconPane = new StackPane();
        iconPane.setPrefSize(24, 24);
        
        // Calendar base (rectangle)
        Rectangle base = new Rectangle(20, 18);
        base.setFill(Color.WHITE);
        base.setStroke(Color.WHITE);
        base.setStrokeWidth(2);
        
        // Calendar top (binding)
        Rectangle binding = new Rectangle(20, 4);
        binding.setFill(Color.WHITE);
        binding.setTranslateY(-7);
        
        // Calendar lines (days)
        Line line1 = new Line(-6, -2, -6, 6);
        line1.setStroke(Color.WHITE);
        line1.setStrokeWidth(1.5);
        
        Line line2 = new Line(0, -2, 0, 6);
        line2.setStroke(Color.WHITE);
        line2.setStrokeWidth(1.5);
        
        Line line3 = new Line(6, -2, 6, 6);
        line3.setStroke(Color.WHITE);
        line3.setStrokeWidth(1.5);
        
        // Horizontal lines
        Line hLine1 = new Line(-8, 1, 8, 1);
        hLine1.setStroke(Color.WHITE);
        hLine1.setStrokeWidth(1.5);
        
        Line hLine2 = new Line(-8, 5, 8, 5);
        hLine2.setStroke(Color.WHITE);
        hLine2.setStrokeWidth(1.5);
        
        iconPane.getChildren().addAll(base, binding, line1, line2, line3, hLine1, hLine2);
        return iconPane;
    }
    
    private void closeWindow() { ((Stage) cancelButton.getScene().getWindow()).close(); }
}
