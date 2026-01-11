package com.fop.calendar.gui;

import com.fop.calendar.CalendarService;
import com.fop.calendar.UserManager;
import com.fop.calendar.model.Event;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.control.ScrollPane;
import javafx.scene.Cursor;
import javafx.scene.paint.Color;
import javafx.scene.effect.Glow;
import javafx.scene.effect.DropShadow;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Line;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import com.fop.calendar.model.AdditionalFields;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class MainController {
    private CalendarService calendarService;
    private UserManager userManager;
    private LocalDate currentMonth;
    private LocalDate selectedDate;
    private VBox selectedDayBox;
    private List<Event> currentDateEvents;
    private Set<Integer> shownReminders;
    private Timeline reminderTimeline;
    
    @FXML private Label monthYearLabel;
    @FXML private GridPane calendarGrid;
    @FXML private ListView<String> eventListView; // No longer in FXML, kept for compatibility
    @FXML private Button prevMonthButton, nextMonthButton, sidebarCreateEventButton;
    @FXML private Button dateSearchButton, rangeSearchButton, searchButton, statisticsButton, logoutButton;
    @FXML private VBox upcomingEventsContainer;
    @FXML private VBox notificationsContainer;

    public MainController() {
        this.currentMonth = LocalDate.now();
        this.shownReminders = new HashSet<>();
    }
    
    public void setUserManager(UserManager userManager) {
        this.userManager = userManager;
        try {
            if (userManager != null && userManager.isLoggedIn()) {
                String userId = userManager.getCurrentUser().getUserId();
                this.calendarService = new CalendarService(userId);
            } else {
                this.calendarService = new CalendarService();
            }
            updateCalendarView();
            updateUpcomingEvents();
            updateNotifications();
            startReminderTimer();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error initializing CalendarService: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    public void initialize() {
        // eventListView removed from FXML, so skip context menu setup
        
        try {
            if (prevMonthButton != null) {
                prevMonthButton.setOnAction(e -> {
                    if (calendarService != null) {
                        currentMonth = currentMonth.minusMonths(1);
                        clearSelectionIfNeeded();
                        updateCalendarView();
                        updateUpcomingEvents();
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Error setting up prevMonthButton: " + e.getMessage());
            e.printStackTrace();
        }
        
        try {
            if (nextMonthButton != null) {
                nextMonthButton.setOnAction(e -> {
                    if (calendarService != null) {
                        currentMonth = currentMonth.plusMonths(1);
                        clearSelectionIfNeeded();
                        updateCalendarView();
                        updateUpcomingEvents();
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Error setting up nextMonthButton: " + e.getMessage());
            e.printStackTrace();
        }
        
        try {
            if (sidebarCreateEventButton != null) {
                sidebarCreateEventButton.setOnAction(e -> {
                    if (selectedDate != null) {
                        openEventFormWithDate(selectedDate);
                    } else {
                        showAlert(Alert.AlertType.WARNING, "No Date Selected", "Please select a date first",
                            "Click on a date in the calendar to select it, then click 'Add Event'.");
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Error setting up sidebarCreateEventButton: " + e.getMessage());
            e.printStackTrace();
        }
        
        try {
            if (dateSearchButton != null) dateSearchButton.setOnAction(e -> openDateSearchDialog());
            if (rangeSearchButton != null) rangeSearchButton.setOnAction(e -> openRangeSearchDialog());
            if (searchButton != null) searchButton.setOnAction(e -> openSearchDialog());
            if (statisticsButton != null) statisticsButton.setOnAction(e -> showAnalytics());
            if (logoutButton != null) logoutButton.setOnAction(e -> handleLogout());
        } catch (Exception e) {
            System.err.println("Error setting up other buttons: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void clearSelectionIfNeeded() {
        if (selectedDate != null && (selectedDate.getMonthValue() != currentMonth.getMonthValue() ||
            selectedDate.getYear() != currentMonth.getYear())) {
            selectedDate = null;
            selectedDayBox = null;
        }
    }

    private void updateCalendarView() {
        if (calendarService == null || monthYearLabel == null || calendarGrid == null) return;
        
        monthYearLabel.setText(currentMonth.getMonth().toString().toUpperCase() + " " + currentMonth.getYear());
        calendarGrid.getChildren().clear();
        calendarGrid.getColumnConstraints().clear();
        
        // Set column constraints to allow expansion
        for (int i = 0; i < 7; i++) {
            ColumnConstraints colConstraints = new ColumnConstraints();
            colConstraints.setHgrow(javafx.scene.layout.Priority.ALWAYS);
            colConstraints.setMinWidth(150);
            calendarGrid.getColumnConstraints().add(colConstraints);
        }
        
        String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (int i = 0; i < 7; i++) {
            Label header = new Label(dayNames[i]);
            header.getStyleClass().add("calendar-header-day");
            calendarGrid.add(header, i, 0);
        }
        
        LocalDate firstDay = currentMonth.withDayOfMonth(1);
        int startOffset = firstDay.getDayOfWeek().getValue() % 7;
        int daysInMonth = firstDay.lengthOfMonth();
        int row = 1, col = startOffset;
        
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = LocalDate.of(currentMonth.getYear(), currentMonth.getMonthValue(), day);
            List<Event> dayEvents = calendarService.searchByDate(date);
            boolean hasEvents = !dayEvents.isEmpty();
            boolean isToday = date.equals(LocalDate.now());
            
            VBox dayBox = new VBox();
            dayBox.getStyleClass().add("calendar-day");
            Label dayLabel = new Label(String.valueOf(day));
            dayLabel.getStyleClass().add("calendar-day-label");
            
            if (hasEvents) {
                dayBox.getStyleClass().add("has-events");
                dayLabel.getStyleClass().add("has-events");
            }
            
            if (isToday) {
                dayBox.getStyleClass().add("today");
            }
            
            dayBox.getChildren().add(dayLabel);
            dayBox.setMinSize(150, 100);
            
            if (selectedDate != null && date.equals(selectedDate) && !hasEvents) {
                dayBox.getStyleClass().add("selected");
                dayLabel.getStyleClass().add("selected");
                selectedDayBox = dayBox;
            }
            
            final LocalDate clickDate = date;
            dayBox.setOnMouseClicked(e -> selectDate(clickDate, dayBox));
            calendarGrid.add(dayBox, col, row);
            
            col++;
            if (col > 6) {
                col = 0;
                row++;
            }
        }
        
        if (selectedDate != null && selectedDate.getMonthValue() == currentMonth.getMonthValue() &&
            selectedDate.getYear() == currentMonth.getYear()) {
            updateEventList(selectedDate);
        } else {
            updateEventList(currentMonth.withDayOfMonth(1));
        }
    }

    private void selectDate(LocalDate date, VBox dayBox) {
        if (calendarService == null) return;
        List<Event> dateEvents = calendarService.searchByDate(date);
        boolean hasEvents = !dateEvents.isEmpty();
        
        if (hasEvents) {
            selectedDate = date;
            showEventsForDateDialog(date, dateEvents);
            return;
        }
        
        if (selectedDayBox != null && selectedDate != null) {
            selectedDayBox.getStyleClass().remove("selected");
            if (selectedDayBox.getChildren().size() > 0) {
                Label prevLabel = (Label) selectedDayBox.getChildren().get(0);
                prevLabel.getStyleClass().remove("selected");
            }
        }
        
        selectedDate = date;
        selectedDayBox = dayBox;
        
        dayBox.getStyleClass().add("selected");
        if (dayBox.getChildren().size() > 0) {
            Label label = (Label) dayBox.getChildren().get(0);
            label.getStyleClass().add("selected");
        }
        
        updateEventList(date);
    }
    
    private void setupEventListContextMenu() {
        // eventListView removed from UI, so context menu setup is no longer needed
    }
    
    private void handleDeleteEvent(Event event) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Event");
        confirm.setHeaderText("Confirm Deletion");
        confirm.setContentText("Are you sure you want to delete the event:\n\"" + event.getTitle() + "\"?");
        
        confirm.showAndWait().ifPresent(buttonType -> {
            if (buttonType == ButtonType.OK && calendarService.deleteEvent(event.getEventId())) {
                showAlert(Alert.AlertType.INFORMATION, "Success", "Event Deleted", "The event has been deleted successfully.");
                updateCalendarView();
                updateUpcomingEvents();
                if (selectedDate != null) updateEventList(selectedDate);
            }
        });
    }
    
    private void updateEventList(LocalDate date) {
        if (calendarService == null) return;
        currentDateEvents = calendarService.searchByDate(date);
        // eventListView removed from UI, but keep currentDateEvents for context menu if needed
    }
    
    private void openEventFormWithDate(LocalDate date) {
        openEventForm(null, date != null ? date : (selectedDate != null ? selectedDate : LocalDate.now()));
    }

    private void startReminderTimer() {
        if (reminderTimeline != null) {
            reminderTimeline.stop();
        }
        
        reminderTimeline = new Timeline(new KeyFrame(Duration.seconds(10), e -> checkAndShowReminders()));
        reminderTimeline.setCycleCount(Timeline.INDEFINITE);
        reminderTimeline.play();
        
        checkAndShowReminders();
    }
    
    private void checkAndShowReminders() {
        if (calendarService == null) return;
        
        List<Event> dueReminders = calendarService.checkIndividualReminders();
        
        for (Event event : dueReminders) {
            if (!shownReminders.contains(event.getEventId())) {
                shownReminders.add(event.getEventId());
                javafx.application.Platform.runLater(() -> {
                    showReminderPopup(event);
                    updateNotifications();
                });
            }
        }
        
        LocalDateTime now = LocalDateTime.now();
        shownReminders.removeIf(eventId -> {
            Event e = calendarService.getEventById(eventId);
            return e == null || e.getStartDateTime().isBefore(now);
        });
        
        javafx.application.Platform.runLater(() -> updateNotifications());
    }
    
    private void showReminderPopup(Event event) {
        AdditionalFields fields = calendarService.getAdditionalFields(event.getEventId());
        
        StringBuilder content = new StringBuilder();
        content.append("Event: ").append(event.getTitle()).append("\n\n");
        content.append("Time: ").append(event.getStartDateTime().toLocalDate())
            .append(" ").append(event.getStartDateTime().toLocalTime())
            .append(" - ").append(event.getEndDateTime().toLocalTime()).append("\n\n");
        
        if (fields != null && fields.getLocation() != null && !fields.getLocation().isEmpty()) {
            content.append("Location: ").append(fields.getLocation()).append("\n\n");
        }
        
        if (event.getDescription() != null && !event.getDescription().isEmpty()) {
            content.append("Description: ").append(event.getDescription()).append("\n");
        }
        
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Event Reminder");
        alert.setHeaderText("Reminder: " + event.getTitle());
        alert.setContentText(content.toString());
        alert.setResizable(true);
        alert.getDialogPane().setPrefWidth(500);
        alert.showAndWait();
    }

    private void openEventForm(Event event) {
        openEventForm(event, null);
    }
    
    private void openEventForm(Event event, LocalDate preSelectedDate) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fop/calendar/eventform.fxml"));
            Parent root = loader.load();
            EventFormController controller = loader.getController();
            controller.setCalendarService(calendarService);
            controller.setMainController(this);
            if (preSelectedDate != null) controller.setSelectedDate(preSelectedDate);
            controller.setEvent(event);
            
            Stage stage = new Stage();
            stage.setTitle(event == null ? "Create Event" : "Edit Event");
            stage.setScene(new Scene(root, 600, 650));
            stage.show();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to open event form", "An error occurred: " + e.getMessage());
        }
    }

    private void openDateSearchDialog() {
        if (calendarService == null) return;
        Dialog<LocalDate> dialog = new Dialog<>();
        dialog.setTitle("Search Events by Date");
        dialog.setHeaderText("Select a date to view events");
        
        HBox dateContainer = new HBox(15);
        dateContainer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        
        TextField dateField = new TextField();
        dateField.setPromptText("MM/DD/YYYY");
        dateField.setPrefWidth(200);
        dateField.getStyleClass().add("dialog-date-field");
        
        DatePicker hiddenDatePicker = new DatePicker();
        hiddenDatePicker.setValue(LocalDate.now());
        hiddenDatePicker.setVisible(false);
        hiddenDatePicker.setManaged(false);
        hiddenDatePicker.getStyleClass().add("dialog-date-picker");
        hiddenDatePicker.valueProperty().addListener((obs, oldDate, newDate) -> {
            if (newDate != null) {
                dateField.setText(newDate.format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy")));
            }
        });
        
        Button calendarButton = new Button();
        calendarButton.setGraphic(createCalendarIcon());
        calendarButton.getStyleClass().add("dialog-calendar-button");
        calendarButton.setTooltip(new Tooltip("Open calendar picker"));
        calendarButton.setOnAction(e -> {
            // Add the hidden date picker to the dialog pane so it can show its popup
            if (hiddenDatePicker.getParent() == null) {
                dialog.getDialogPane().getChildren().add(hiddenDatePicker);
            }
            hiddenDatePicker.show();
        });
        
        dateContainer.getChildren().addAll(dateField, calendarButton);
        
        dialog.getDialogPane().setContent(dateContainer);
        ButtonType searchButtonType = new ButtonType("Search", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(searchButtonType, ButtonType.CANCEL);
        
        dialog.getDialogPane().setStyle("-fx-background-color: #000000;");
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/com/fop/calendar/styles.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dialog-yellow-black");
        
        dialog.setResultConverter(buttonType -> {
            if (buttonType == searchButtonType) {
                try {
                    String dateText = dateField.getText();
                    if (dateText.isEmpty()) {
                        dateText = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy"));
                    }
                    return LocalDate.parse(dateText, java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy"));
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Invalid Date", "Please enter a valid date (MM/DD/YYYY)", "");
                    return null;
                }
            }
            return null;
        });
        dialog.showAndWait().ifPresent(this::showDateSearchResults);
    }
    
    private void showDateSearchResults(LocalDate date) {
        List<Event> events = calendarService.searchByDate(date);
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Events on " + date.toString());
        alert.setHeaderText("Found " + events.size() + " event(s) on " + date.toString());
        
        Label contentLabel = new Label();
        contentLabel.setStyle("-fx-text-fill: #FFC107; -fx-font-size: 12pt;");
        contentLabel.setWrapText(true);
        
        if (events.isEmpty()) {
            contentLabel.setText("No events found for this date.");
        } else {
            StringBuilder content = new StringBuilder();
            for (Event event : events) {
                content.append("• ").append(event.getTitle()).append(" - ")
                    .append(event.getStartDateTime().toLocalTime()).append(" to ")
                    .append(event.getEndDateTime().toLocalTime());
                if (event.getDescription() != null && !event.getDescription().isEmpty()) {
                    content.append("\n  ").append(event.getDescription());
                }
                content.append("\n\n");
            }
            contentLabel.setText(content.toString());
        }
        
        ScrollPane scrollPane = new ScrollPane(contentLabel);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(300);
        scrollPane.setPrefViewportWidth(450);
        scrollPane.setStyle("-fx-background-color: #000000;");
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        alert.getDialogPane().setContent(scrollPane);
        alert.getDialogPane().setStyle("-fx-background-color: #000000;");
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/com/fop/calendar/styles.css").toExternalForm());
        alert.getDialogPane().getStyleClass().add("dialog-yellow-black");
        alert.setResizable(true);
        alert.getDialogPane().setPrefWidth(500);
        alert.showAndWait();
    }

    private void openRangeSearchDialog() {
        if (calendarService == null) return;
        Dialog<javafx.util.Pair<LocalDate, LocalDate>> dialog = new Dialog<>();
        dialog.setTitle("Search Events by Date Range");
        dialog.setHeaderText("Select a date range to view events");
        
        VBox content = new VBox(15);
        
        Label startLabel = new Label("Start Date:");
        startLabel.setStyle("-fx-text-fill: #FFC107; -fx-font-size: 12pt;");
        HBox startDateContainer = new HBox(15);
        startDateContainer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        TextField startDateField = new TextField();
        startDateField.setPromptText("MM/DD/YYYY");
        startDateField.setPrefWidth(200);
        startDateField.getStyleClass().add("dialog-date-field");
        DatePicker hiddenStartDatePicker = new DatePicker();
        hiddenStartDatePicker.setValue(LocalDate.now());
        hiddenStartDatePicker.setVisible(false);
        hiddenStartDatePicker.setManaged(false);
        hiddenStartDatePicker.getStyleClass().add("dialog-date-picker");
        hiddenStartDatePicker.valueProperty().addListener((obs, oldDate, newDate) -> {
            if (newDate != null) {
                startDateField.setText(newDate.format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy")));
            }
        });
        
        Button startCalendarButton = new Button();
        startCalendarButton.setGraphic(createCalendarIcon());
        startCalendarButton.getStyleClass().add("dialog-calendar-button");
        startCalendarButton.setTooltip(new Tooltip("Open calendar picker"));
        startCalendarButton.setOnAction(e -> {
            if (hiddenStartDatePicker.getParent() == null) {
                dialog.getDialogPane().getChildren().add(hiddenStartDatePicker);
            }
            hiddenStartDatePicker.show();
        });
        startDateContainer.getChildren().addAll(startDateField, startCalendarButton);
        
        Label endLabel = new Label("End Date:");
        endLabel.setStyle("-fx-text-fill: #FFC107; -fx-font-size: 12pt;");
        HBox endDateContainer = new HBox(15);
        endDateContainer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        TextField endDateField = new TextField();
        endDateField.setPromptText("MM/DD/YYYY");
        endDateField.setPrefWidth(200);
        endDateField.getStyleClass().add("dialog-date-field");
        DatePicker hiddenEndDatePicker = new DatePicker();
        hiddenEndDatePicker.setValue(LocalDate.now().plusDays(7));
        hiddenEndDatePicker.setVisible(false);
        hiddenEndDatePicker.setManaged(false);
        hiddenEndDatePicker.getStyleClass().add("dialog-date-picker");
        hiddenEndDatePicker.valueProperty().addListener((obs, oldDate, newDate) -> {
            if (newDate != null) {
                endDateField.setText(newDate.format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy")));
            }
        });
        
        Button endCalendarButton = new Button();
        endCalendarButton.setGraphic(createCalendarIcon());
        endCalendarButton.getStyleClass().add("dialog-calendar-button");
        endCalendarButton.setTooltip(new Tooltip("Open calendar picker"));
        endCalendarButton.setOnAction(e -> {
            if (hiddenEndDatePicker.getParent() == null) {
                dialog.getDialogPane().getChildren().add(hiddenEndDatePicker);
            }
            hiddenEndDatePicker.show();
        });
        endDateContainer.getChildren().addAll(endDateField, endCalendarButton);
        
        content.getChildren().addAll(startLabel, startDateContainer, endLabel, endDateContainer);
        dialog.getDialogPane().setContent(content);
        ButtonType searchButtonType = new ButtonType("Search", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(searchButtonType, ButtonType.CANCEL);
        
        dialog.getDialogPane().setStyle("-fx-background-color: #000000;");
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/com/fop/calendar/styles.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dialog-yellow-black");
        
        dialog.setResultConverter(buttonType -> {
            if (buttonType == searchButtonType) {
                try {
                    String startText = startDateField.getText();
                    String endText = endDateField.getText();
                    if (startText.isEmpty()) startText = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy"));
                    if (endText.isEmpty()) endText = LocalDate.now().plusDays(7).format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy"));
                    
                    LocalDate startDate = LocalDate.parse(startText, java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy"));
                    LocalDate endDate = LocalDate.parse(endText, java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy"));
                    
                    if (!startDate.isAfter(endDate)) {
                        return new javafx.util.Pair<>(startDate, endDate);
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Invalid Date Range", "Please select a valid date range.",
                            "Start date must be before or equal to end date.");
                        return null;
                    }
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Invalid Date", "Please enter valid dates (MM/DD/YYYY)", "");
                    return null;
                }
            }
            return null;
        });
        dialog.showAndWait().ifPresent(pair -> showRangeSearchResults(pair.getKey(), pair.getValue()));
    }
    
    private void showRangeSearchResults(LocalDate startDate, LocalDate endDate) {
        List<Event> events = calendarService.searchByDateRange(startDate, endDate);
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Events from " + startDate + " to " + endDate);
        alert.setHeaderText("Found " + events.size() + " event(s) from " + startDate + " to " + endDate);
        
        Label contentLabel = new Label();
        contentLabel.setStyle("-fx-text-fill: #FFC107; -fx-font-size: 12pt;");
        contentLabel.setWrapText(true);
        
        if (events.isEmpty()) {
            contentLabel.setText("No events found in this date range.");
        } else {
            StringBuilder content = new StringBuilder();
            for (Event event : events) {
                content.append("• ").append(event.getTitle()).append(" - ")
                    .append(event.getStartDateTime().toLocalDate()).append(" ")
                    .append(event.getStartDateTime().toLocalTime()).append(" to ")
                    .append(event.getEndDateTime().toLocalTime());
                if (event.getDescription() != null && !event.getDescription().isEmpty()) {
                    content.append("\n  ").append(event.getDescription());
                }
                content.append("\n\n");
            }
            contentLabel.setText(content.toString());
        }
        
        ScrollPane scrollPane = new ScrollPane(contentLabel);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(300);
        scrollPane.setPrefViewportWidth(550);
        scrollPane.setStyle("-fx-background-color: #000000;");
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        alert.getDialogPane().setContent(scrollPane);
        alert.getDialogPane().setStyle("-fx-background-color: #000000;");
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/com/fop/calendar/styles.css").toExternalForm());
        alert.getDialogPane().getStyleClass().add("dialog-yellow-black");
        alert.setResizable(true);
        alert.getDialogPane().setPrefWidth(600);
        alert.showAndWait();
    }

    private void openSearchDialog() {
        if (calendarService == null) return;
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Advanced Search Events");
        dialog.setHeaderText("Search by Title");
        
        VBox dialogContent = new VBox(10);
        Label headerLabel = new Label("Enter keyword to search:");
        headerLabel.getStyleClass().add("dialog-header-label");
        
        TextField keywordField = new TextField();
        keywordField.setPromptText("Enter event title keyword...");
        keywordField.setPrefWidth(300);
        keywordField.getStyleClass().add("dialog-date-field");
        
        dialogContent.getChildren().addAll(headerLabel, keywordField);
        dialog.getDialogPane().setContent(dialogContent);
        
        ButtonType searchButtonType = new ButtonType("Search", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(searchButtonType, ButtonType.CANCEL);
        
        dialog.getDialogPane().setStyle("-fx-background-color: #000000;");
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/com/fop/calendar/styles.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dialog-yellow-black");
        
        dialog.setResultConverter(buttonType -> {
            if (buttonType == searchButtonType) {
                String keyword = keywordField.getText().trim();
                if (keyword.isEmpty()) {
                    showAlert(Alert.AlertType.WARNING, "Empty Keyword", "Please enter a keyword to search.", "");
                    return null;
                }
                return keyword;
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(keyword -> showSearchResults(calendarService.searchByTitle(keyword)));
    }

    private void showSearchResults(List<Event> results) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Advanced Search Results");
        alert.setHeaderText("Found " + results.size() + " event(s)");
        
        Label contentLabel = new Label();
        contentLabel.setStyle("-fx-text-fill: #FFC107; -fx-font-size: 12pt;");
        contentLabel.setWrapText(true);
        
        StringBuilder content = new StringBuilder();
        if (results.isEmpty()) {
            content.append("No events found matching your search.");
        } else {
            for (Event event : results) {
                content.append("• ").append(event.getTitle())
                    .append(" - ").append(event.getStartDateTime().toLocalDate()).append(" ")
                    .append(event.getStartDateTime().toLocalTime()).append(" to ")
                    .append(event.getEndDateTime().toLocalTime());
                if (event.getDescription() != null && !event.getDescription().isEmpty()) {
                    content.append("\n  ").append(event.getDescription());
                }
                content.append("\n\n");
            }
        }
        contentLabel.setText(content.toString());
        
        ScrollPane scrollPane = new ScrollPane(contentLabel);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(300);
        scrollPane.setPrefViewportWidth(550);
        scrollPane.setStyle("-fx-background-color: #000000;");
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        alert.getDialogPane().setContent(scrollPane);
        alert.getDialogPane().setStyle("-fx-background-color: #000000;");
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/com/fop/calendar/styles.css").toExternalForm());
        alert.getDialogPane().getStyleClass().add("dialog-yellow-black");
        alert.setResizable(true);
        alert.getDialogPane().setPrefWidth(600);
        alert.showAndWait();
    }

    private void showAnalytics() {
        if (calendarService == null) return;
        
        Stage analyticsStage = new Stage();
        analyticsStage.setTitle("Calendar Analytics Dashboard");
        analyticsStage.initModality(Modality.APPLICATION_MODAL);
        
        VBox root = new VBox(20);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: #000000;");
        
        // Title
        Label titleLabel = new Label("Calendar Analytics Dashboard");
        titleLabel.setStyle("-fx-text-fill: #FFC107; -fx-font-size: 24pt; -fx-font-weight: bold;");
        root.getChildren().add(titleLabel);
        
        // Event count cards
        VBox cardsContainer = new VBox(15);
        cardsContainer.setAlignment(Pos.CENTER);
        
        // Total Events Card (centered, red glowing border)
        StackPane totalEventsCard = createStatCard("Total Events", String.valueOf(calendarService.getTotalEvents()), "#FF4444");
        cardsContainer.getChildren().add(totalEventsCard);
        
        // Upcoming and Past Events Cards (side by side)
        HBox bottomCards = new HBox(15);
        bottomCards.setAlignment(Pos.CENTER);
        StackPane upcomingCard = createStatCard("Upcoming Events", String.valueOf(calendarService.getUpcomingEventsCount()), "#FFC107");
        StackPane pastCard = createStatCard("Past Events", String.valueOf(calendarService.getPastEventsCount()), "#FFC107");
        bottomCards.getChildren().addAll(upcomingCard, pastCard);
        cardsContainer.getChildren().add(bottomCards);
        
        root.getChildren().add(cardsContainer);
        
        // Separator line
        Region separator = new Region();
        separator.setPrefHeight(2);
        separator.setStyle("-fx-background-color: #FFFFFF;");
        separator.setMaxWidth(Double.MAX_VALUE);
        root.getChildren().add(separator);
        
        // Category Breakdown
        Label categoryLabel = new Label("Category Breakdown:");
        categoryLabel.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 14pt; -fx-font-weight: bold;");
        root.getChildren().add(categoryLabel);
        
        VBox categoryContainer = new VBox(10);
        categoryContainer.setPadding(new Insets(10));
        categoryContainer.setStyle("-fx-background-color: #000000; -fx-background-radius: 5; -fx-min-height: 150;");
        
        Map<String, Long> categoryBreakdown = calendarService.getCategoryBreakdown();
        if (categoryBreakdown.isEmpty()) {
            Label noCategories = new Label("No categories assigned");
            noCategories.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 12pt;");
            categoryContainer.getChildren().add(noCategories);
        } else {
            for (Map.Entry<String, Long> entry : categoryBreakdown.entrySet()) {
                Label categoryItem = new Label(entry.getKey() + ": " + entry.getValue() + " event(s)");
                categoryItem.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 12pt;");
                categoryContainer.getChildren().add(categoryItem);
            }
        }
        root.getChildren().add(categoryContainer);
        
        // Close Button
        HBox buttonContainer = new HBox();
        buttonContainer.setAlignment(Pos.BOTTOM_RIGHT);
        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-background-color: #FFC107; -fx-text-fill: #000000; -fx-font-size: 12pt; -fx-font-weight: bold; -fx-padding: 10 30; -fx-background-radius: 5;");
        closeButton.setOnAction(e -> analyticsStage.close());
        buttonContainer.getChildren().add(closeButton);
        root.getChildren().add(buttonContainer);
        
        Scene scene = new Scene(root, 600, 500);
        analyticsStage.setScene(scene);
        analyticsStage.show();
    }
    
    private javafx.scene.Node createCalendarIcon() {
        StackPane iconPane = new StackPane();
        iconPane.setPrefSize(24, 24);
        
        // Calendar base (rectangle) - transparent fill, black border
        Rectangle base = new Rectangle(20, 18);
        base.setFill(Color.TRANSPARENT);
        base.setStroke(Color.BLACK);
        base.setStrokeWidth(2);
        
        // Calendar top (binding) - black fill
        Rectangle binding = new Rectangle(20, 4);
        binding.setFill(Color.BLACK);
        binding.setTranslateY(-7);
        
        // Calendar lines (days) - black
        Line line1 = new Line(-6, -2, -6, 6);
        line1.setStroke(Color.BLACK);
        line1.setStrokeWidth(1.5);
        
        Line line2 = new Line(0, -2, 0, 6);
        line2.setStroke(Color.BLACK);
        line2.setStrokeWidth(1.5);
        
        Line line3 = new Line(6, -2, 6, 6);
        line3.setStroke(Color.BLACK);
        line3.setStrokeWidth(1.5);
        
        // Horizontal lines - black
        Line hLine1 = new Line(-8, 1, 8, 1);
        hLine1.setStroke(Color.BLACK);
        hLine1.setStrokeWidth(1.5);
        
        Line hLine2 = new Line(-8, 5, 8, 5);
        hLine2.setStroke(Color.BLACK);
        hLine2.setStrokeWidth(1.5);
        
        iconPane.getChildren().addAll(base, binding, line1, line2, line3, hLine1, hLine2);
        return iconPane;
    }
    
    private StackPane createStatCard(String label, String value, String borderColor) {
        VBox cardContent = new VBox(10);
        cardContent.setAlignment(Pos.CENTER);
        cardContent.setPadding(new Insets(20));
        cardContent.setStyle("-fx-background-color: #000000; -fx-background-radius: 8;");
        
        Label labelText = new Label(label);
        labelText.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 14pt; -fx-font-weight: normal;");
        
        Label valueText = new Label(value);
        valueText.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 48pt; -fx-font-weight: bold; -fx-font-family: 'Arial', 'Helvetica', sans-serif;");
        
        cardContent.getChildren().addAll(labelText, valueText);
        
        StackPane card = new StackPane(cardContent);
        card.setStyle(String.format(
            "-fx-background-color: transparent; -fx-border-color: %s; -fx-border-width: 3; -fx-border-radius: 8;",
            borderColor
        ));
        
        // Add glowing effect
        Glow glow = new Glow(0.8);
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.web(borderColor));
        shadow.setRadius(15);
        shadow.setSpread(0.5);
        glow.setInput(shadow);
        card.setEffect(glow);
        
        // Make Total Events card wider
        if (label.equals("Total Events")) {
            card.setPrefWidth(300);
            card.setPrefHeight(140);
        } else {
            card.setPrefWidth(220);
            card.setPrefHeight(140);
        }
        
        return card;
    }

    private void handleLogout() {
        if (reminderTimeline != null) {
            reminderTimeline.stop();
        }
        shownReminders.clear();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Logout");
        confirm.setHeaderText("Confirm Logout");
        confirm.setContentText("Are you sure you want to logout? Your events are saved automatically.");
        
        confirm.showAndWait().ifPresent(buttonType -> {
            if (buttonType == ButtonType.OK) {
                if (userManager != null) userManager.logout();
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fop/calendar/login.fxml"));
                    Stage stage = (Stage) logoutButton.getScene().getWindow();
                    stage.setScene(new Scene(loader.load(), 400, 350));
                    stage.setTitle("Login");
                } catch (Exception e) { }
            }
        });
    }

    public void refreshCalendar() {
        updateCalendarView();
        updateUpcomingEvents();
    }
    
    private void updateNotifications() {
        if (notificationsContainer == null || calendarService == null) return;
        
        notificationsContainer.getChildren().clear();
        
        List<Event> dueReminders = calendarService.checkIndividualReminders();
        List<Event> upcomingSoon = calendarService.getUpcomingEvents(24); // Events in next 24 hours
        
        if (dueReminders.isEmpty() && upcomingSoon.isEmpty()) {
            Label noNotificationsLabel = new Label("No notifications");
            noNotificationsLabel.getStyleClass().add("notification-item");
            noNotificationsLabel.setStyle("-fx-text-fill: #A0A0A0; -fx-font-size: 10pt;");
            notificationsContainer.getChildren().add(noNotificationsLabel);
            return;
        }
        
        // Show active reminders first
        for (Event event : dueReminders) {
            HBox notificationBox = new HBox(10);
            notificationBox.getStyleClass().add("notification-item");
            notificationBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            notificationBox.setStyle("-fx-padding: 8; -fx-background-color: #FFC107; -fx-background-radius: 5;");
            
            Label iconLabel = new Label("🔔");
            iconLabel.setStyle("-fx-text-fill: #000000; -fx-font-size: 14pt;");
            
            VBox notificationInfo = new VBox(3);
            Label titleLabel = new Label(event.getTitle());
            titleLabel.setStyle("-fx-text-fill: #000000; -fx-font-size: 11pt; -fx-font-weight: bold;");
            
            String timeStr = event.getStartDateTime().toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            Label timeLabel = new Label("Now - " + timeStr);
            timeLabel.setStyle("-fx-text-fill: #000000; -fx-font-size: 9pt;");
            
            notificationInfo.getChildren().addAll(titleLabel, timeLabel);
            notificationBox.getChildren().addAll(iconLabel, notificationInfo);
            
            // Make clickable to open event
            notificationBox.setCursor(Cursor.HAND);
            notificationBox.setOnMouseClicked(e -> {
                if (e.getClickCount() == 1) {
                    openEventForm(event);
                }
            });
            
            notificationsContainer.getChildren().add(notificationBox);
        }
        
        // Show upcoming events soon (within 24 hours)
        for (Event event : upcomingSoon) {
            if (dueReminders.stream().anyMatch(e -> e.getEventId() == event.getEventId())) {
                continue; // Skip if already shown as reminder
            }
            
            HBox notificationBox = new HBox(10);
            notificationBox.getStyleClass().add("notification-item");
            notificationBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            notificationBox.setStyle("-fx-padding: 8; -fx-background-color: #2D2D2D; -fx-background-radius: 5;");
            
            Label iconLabel = new Label("⏰");
            iconLabel.setStyle("-fx-text-fill: #FFC107; -fx-font-size: 14pt;");
            
            VBox notificationInfo = new VBox(3);
            Label titleLabel = new Label(event.getTitle());
            titleLabel.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 11pt; -fx-font-weight: bold;");
            
            long hoursUntil = java.time.Duration.between(LocalDateTime.now(), event.getStartDateTime()).toHours();
            String timeStr = event.getStartDateTime().toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            String timeUntil = hoursUntil > 0 ? "In " + hoursUntil + "h (" + timeStr + ")" : "Soon (" + timeStr + ")";
            Label timeLabel = new Label(timeUntil);
            timeLabel.setStyle("-fx-text-fill: #E0E0E0; -fx-font-size: 9pt;");
            
            notificationInfo.getChildren().addAll(titleLabel, timeLabel);
            notificationBox.getChildren().addAll(iconLabel, notificationInfo);
            
            // Make clickable to open event
            notificationBox.setCursor(Cursor.HAND);
            notificationBox.setOnMouseClicked(e -> {
                if (e.getClickCount() == 1) {
                    openEventForm(event);
                }
            });
            
            notificationsContainer.getChildren().add(notificationBox);
        }
    }
    
    private void updateUpcomingEvents() {
        if (upcomingEventsContainer == null || calendarService == null) return;
        
        upcomingEventsContainer.getChildren().clear();
        
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysLater = today.plusDays(7);
        List<Event> upcomingEvents = calendarService.searchByDateRange(today, sevenDaysLater);
        
        List<Event> futureEvents = upcomingEvents.stream()
            .filter(e -> e.getStartDateTime().isAfter(LocalDateTime.now()))
            .sorted(java.util.Comparator.comparing(Event::getStartDateTime))
            .collect(java.util.stream.Collectors.toList());
        
        if (futureEvents.isEmpty()) {
            Label noEventsLabel = new Label("No upcoming events in the next 7 days.");
            noEventsLabel.getStyleClass().add("upcoming-event-item");
            noEventsLabel.setStyle("-fx-text-fill: #A0A0A0;");
            upcomingEventsContainer.getChildren().add(noEventsLabel);
        } else {
            for (Event event : futureEvents) {
                HBox eventBox = new HBox(10);
                eventBox.getStyleClass().add("upcoming-event-item");
                eventBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                eventBox.setCursor(Cursor.HAND);
                
                // Add hover effect
                eventBox.setOnMouseEntered(e -> {
                    eventBox.setStyle("-fx-background-color: #2D2D2D; -fx-background-radius: 5; -fx-padding: 5;");
                });
                eventBox.setOnMouseExited(e -> {
                    eventBox.setStyle("-fx-background-color: transparent;");
                });
                
                // Add click handler to open event form for editing
                eventBox.setOnMouseClicked(e -> {
                    if (e.getClickCount() == 1) {
                        openEventForm(event);
                    }
                });
                
                Label iconLabel = new Label("•");
                iconLabel.setStyle("-fx-text-fill: #FFEB3B; -fx-font-size: 16pt; -fx-font-weight: bold;");
                
                String dateStr = event.getStartDateTime().toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("MMM d"));
                String eventText = dateStr + " - " + event.getTitle();
                
                Label eventLabel = new Label(eventText);
                eventLabel.setStyle("-fx-text-fill: #E0E0E0; -fx-font-size: 11pt; -fx-font-weight: bold;");
                
                eventBox.getChildren().addAll(iconLabel, eventLabel);
                upcomingEventsContainer.getChildren().add(eventBox);
            }
        }
    }

    private void showEventsForDateDialog(LocalDate date, List<Event> events) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Events for " + date.toString());
        dialog.getDialogPane().setStyle("-fx-background-color: #000000;");
        dialog.getDialogPane().getStyleClass().add("dialog-yellow-black");
        
        try {
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("/com/fop/calendar/styles.css").toExternalForm());
        } catch (Exception e) {
            // CSS file not found, continue without it
        }
        
        VBox content = new VBox(10);
        content.setSpacing(10);
        content.setStyle("-fx-padding: 15;");
        
        Label headerLabel = new Label("Events on " + date.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy")));
        headerLabel.setStyle("-fx-text-fill: #FFC107; -fx-font-size: 16pt; -fx-font-weight: bold;");
        content.getChildren().add(headerLabel);
        
        if (events.isEmpty()) {
            Label noEventsLabel = new Label("No events on this date.");
            noEventsLabel.setStyle("-fx-text-fill: #A0A0A0;");
            content.getChildren().add(noEventsLabel);
        } else {
            ScrollPane scrollPane = new ScrollPane();
            scrollPane.setFitToWidth(true);
            scrollPane.setPrefHeight(300);
            scrollPane.getStyleClass().add("dialog-scroll-pane");
            
            VBox eventsList = new VBox(8);
            eventsList.setStyle("-fx-padding: 10;");
            
            for (int i = 0; i < events.size(); i++) {
                Event event = events.get(i);
                HBox eventRow = new HBox(15);
                eventRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                eventRow.setStyle("-fx-padding: 10; -fx-background-color: #2D2D2D; -fx-background-radius: 5;");
                
                VBox eventInfo = new VBox(5);
                HBox.setHgrow(eventInfo, Priority.ALWAYS);
                
                Label eventTitle = new Label((i + 1) + ". " + event.getTitle());
                eventTitle.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 14pt; -fx-font-weight: bold;");
                
                String timeStr = event.getStartDateTime().toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) + 
                               " - " + event.getEndDateTime().toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                Label eventTime = new Label(timeStr);
                eventTime.setStyle("-fx-text-fill: #E0E0E0; -fx-font-size: 12pt;");
                
                if (event.getDescription() != null && !event.getDescription().isEmpty()) {
                    Label eventDesc = new Label(event.getDescription());
                    eventDesc.setStyle("-fx-text-fill: #A0A0A0; -fx-font-size: 11pt;");
                    eventDesc.setWrapText(true);
                    eventInfo.getChildren().add(eventTitle);
                    eventInfo.getChildren().add(eventTime);
                    eventInfo.getChildren().add(eventDesc);
                } else {
                    eventInfo.getChildren().add(eventTitle);
                    eventInfo.getChildren().add(eventTime);
                }
                
                HBox buttonBox = new HBox(10);
                buttonBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
                
                Button editButton = new Button("Edit");
                editButton.getStyleClass().add("button");
                editButton.setStyle("-fx-background-color: #FFC107; -fx-text-fill: #000000; -fx-font-weight: bold; -fx-padding: 8 15;");
                editButton.setOnAction(e -> {
                    dialog.close();
                    openEventForm(event);
                });
                
                Button deleteButton = new Button("Delete");
                deleteButton.getStyleClass().add("button");
                deleteButton.setStyle("-fx-background-color: #DC3545; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-padding: 8 15;");
                deleteButton.setOnAction(e -> {
                    dialog.close();
                    handleDeleteEvent(event);
                });
                
                buttonBox.getChildren().addAll(editButton, deleteButton);
                eventRow.getChildren().addAll(eventInfo, buttonBox);
                eventsList.getChildren().add(eventRow);
            }
            
            scrollPane.setContent(eventsList);
            content.getChildren().add(scrollPane);
        }
        
        Button closeButton = new Button("Close");
        closeButton.getStyleClass().add("button");
        closeButton.setStyle("-fx-background-color: #555555; -fx-text-fill: #FFFFFF; -fx-padding: 8 20;");
        closeButton.setOnAction(e -> dialog.close());
        
        HBox buttonContainer = new HBox(10);
        buttonContainer.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        buttonContainer.setStyle("-fx-padding: 10 0 0 0;");
        buttonContainer.getChildren().add(closeButton);
        content.getChildren().add(buttonContainer);
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private Optional<ButtonType> showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        return alert.showAndWait();
    }
}
