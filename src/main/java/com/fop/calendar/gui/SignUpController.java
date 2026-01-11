package com.fop.calendar.gui;

import com.fop.calendar.UserManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class SignUpController {
    private UserManager userManager;
    
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button signUpButton;
    @FXML private Button backButton;
    @FXML private Label errorLabel;

    public SignUpController() { this.userManager = new UserManager(); }

    @FXML
    public void initialize() {
        signUpButton.setOnAction(e -> handleSignUp());
        backButton.setOnAction(e -> goBackToLogin());
        confirmPasswordField.setOnAction(e -> handleSignUp());
    }

    private void handleSignUp() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) { showError("Please fill in all fields"); return; }
        if (username.length() < 3) { showError("Username must be at least 3 characters"); return; }
        if (password.length() < 4) { showError("Password must be at least 4 characters"); return; }
        if (!password.equals(confirmPassword)) { showError("Passwords do not match"); return; }

        if (userManager.signUp(username, password)) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Success");
            alert.setHeaderText("Account Created");
            alert.setContentText("Your account has been created successfully! Please login.");
            alert.showAndWait();
            goBackToLogin();
        } else { showError("Username already exists. Please choose a different username."); }
    }

    private void goBackToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fop/calendar/login.fxml"));
            Parent root = loader.load();
            
            Stage stage = (Stage) backButton.getScene().getWindow();
            stage.setScene(new Scene(root, 400, 350));
            stage.setTitle("Login");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error loading login page");
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: red;");
    }
}
