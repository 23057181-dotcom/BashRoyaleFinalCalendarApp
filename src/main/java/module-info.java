module com.fop.calendar {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;

    opens com.fop.calendar to javafx.fxml;
    opens com.fop.calendar.gui to javafx.fxml;
    exports com.fop.calendar;
    exports com.fop.calendar.model;
}
