package com.fop.calendar;

import com.fop.calendar.model.User;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UserManager {
    private static final String USERS_DIR = "data";
    private static final String USERS_FILE = "users.csv";
    private User currentUser;

    public UserManager() { ensureUsersDirectoryExists(); }

    private void ensureUsersDirectoryExists() {
        File dir = new File(USERS_DIR);
        if (!dir.exists()) dir.mkdirs();
    }

    private String getUsersFilePath() { return USERS_DIR + File.separator + USERS_FILE; }

    public boolean signUp(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) return false;
        if (userExists(username)) return false;

        String userId = UUID.randomUUID().toString();
        User newUser = new User(username.trim(), password, userId);

        try {
            File file = new File(getUsersFilePath());
            boolean fileExists = file.exists();
            try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
                if (!fileExists) writer.println("username,password,userId");
                writer.println(newUser.toString());
            }
            createUserDataDirectory(userId);
            return true;
        } catch (IOException e) {
            System.err.println("Error saving user: " + e.getMessage());
            return false;
        }
    }

    public boolean login(String username, String password) {
        List<User> users = readUsers();
        for (User user : users) {
            if (user.getUsername().equals(username.trim()) && user.getPassword().equals(password)) {
                this.currentUser = user;
                return true;
            }
        }
        return false;
    }

    public void logout() { this.currentUser = null; }
    public User getCurrentUser() { return currentUser; }
    public boolean isLoggedIn() { return currentUser != null; }

    private boolean userExists(String username) {
        return readUsers().stream().anyMatch(u -> u.getUsername().equalsIgnoreCase(username.trim()));
    }

    private List<User> readUsers() {
        List<User> users = new ArrayList<>();
        String filePath = getUsersFilePath();
        if (!Files.exists(Paths.get(filePath))) return users;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            if (reader.readLine() == null) return users;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length >= 3) users.add(new User(parts[0].trim(), parts[1].trim(), parts[2].trim()));
            }
        } catch (IOException e) { System.err.println("Error reading users file: " + e.getMessage()); }
        return users;
    }

    private void createUserDataDirectory(String userId) {
        File dir = new File(USERS_DIR + File.separator + "users" + File.separator + userId);
        if (!dir.exists()) dir.mkdirs();
    }
}
