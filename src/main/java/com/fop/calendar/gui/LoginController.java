package com.fop.calendar.gui;

import com.fop.calendar.UserManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {
    private UserManager userManager;
    
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Button signUpButton;
    @FXML private Label errorLabel;

    public LoginController() { this.userManager = new UserManager(); }

    @FXML
    public void initialize() {
        loginButton.setOnAction(e -> handleLogin());
        signUpButton.setOnAction(e -> openSignUp());
        passwordField.setOnAction(e -> handleLogin());
    }

    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) { showError("Please enter both username and password"); return; }

        if (userManager.login(username, password)) {
            try {
                java.net.URL fxmlUrl = getClass().getResource("/com/fop/calendar/main.fxml");
                if (fxmlUrl == null) {
                    showError("FXML file not found: /com/fop/calendar/main.fxml");
                    return;
                }
                
                FXMLLoader loader = new FXMLLoader(fxmlUrl);
                Parent root = loader.load();
                MainController mainController = loader.getController();
                if (mainController == null) {
                    showError("Controller is null - check FXML controller attribute");
                    return;
                }
                mainController.setUserManager(userManager);
                
                Stage stage = (Stage) loginButton.getScene().getWindow();
                Scene scene = new Scene(root, 900, 600);
                try {
                    String cssPath = getClass().getResource("/com/fop/calendar/styles.css").toExternalForm();
                    scene.getStylesheets().add(cssPath);
                } catch (Exception cssEx) {
                    System.err.println("Warning: Could not load CSS: " + cssEx.getMessage());
                }
                stage.setScene(scene);
                stage.setTitle("Calendar - " + username);
            } catch (javafx.fxml.LoadException le) {
                le.printStackTrace();
                StringBuilder errorMsg = new StringBuilder();
                errorMsg.append("LoadException: ");
                if (le.getMessage() != null) {
                    errorMsg.append(le.getMessage());
                }
                Throwable cause = le.getCause();
                if (cause != null) {
                    errorMsg.append(" | Cause: ").append(cause.getClass().getSimpleName());
                    if (cause.getMessage() != null) {
                        errorMsg.append(" - ").append(cause.getMessage());
                    }
                    cause.printStackTrace();
                }
                showError(errorMsg.toString());
            } catch (Exception e) {
                e.printStackTrace();
                StringBuilder errorMsg = new StringBuilder();
                errorMsg.append("Error: ").append(e.getClass().getSimpleName());
                if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                    errorMsg.append(" - ").append(e.getMessage());
                }
                if (e.getCause() != null) {
                    errorMsg.append(" | Cause: ").append(e.getCause().getClass().getSimpleName());
                    if (e.getCause().getMessage() != null) {
                        errorMsg.append(" - ").append(e.getCause().getMessage());
                    }
                }
                showError(errorMsg.toString());
            }
        } else { showError("Invalid username or password"); }
    }

    private void openSignUp() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fop/calendar/signup.fxml"));
            Parent root = loader.load();
            
            Stage stage = (Stage) signUpButton.getScene().getWindow();
            stage.setScene(new Scene(root, 400, 350));
            stage.setTitle("Sign Up");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error loading sign up page");
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: red; -fx-wrap-text: true;");
        errorLabel.setMaxWidth(Double.MAX_VALUE);
        System.err.println("ERROR: " + message);
    }
}
