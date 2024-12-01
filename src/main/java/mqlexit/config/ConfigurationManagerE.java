// ConfigurationManagerE.java
package config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;
import java.util.Scanner;
import logging.LoggerManagerE;

public class ConfigurationManagerE {
    private final String rootDirPath;
    private final String configDirPath;
    private final String configFilePath;
    private final String logDirPath;
    private final String downloadPath;
    private final String defaultSignalDirPath;
    private static final String DEFAULT_BASE_URL = "https://www.mql5.com/en/signals";
    private static final String DEFAULT_SIGNAL_ID = "201845";

    public ConfigurationManagerE(String rootDirPath) {
        this.rootDirPath = rootDirPath;
        this.configDirPath = rootDirPath + "\\conf";
        this.configFilePath = configDirPath + "\\conf.txt";
        this.logDirPath = rootDirPath + "\\logs";
        this.downloadPath = rootDirPath + "\\download";
        this.defaultSignalDirPath = rootDirPath + "\\signals";
    }

    public void initializeDirectories() {
        createDirectory(configDirPath);
        createDirectory(logDirPath);
        createDirectory(downloadPath);
    }

    private void createDirectory(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                LoggerManagerE.info("Directory created: " + path);
            } else {
                LoggerManagerE.error("Could not create directory: " + path);
            }
        }
    }

    public CredentialsE getOrCreateCredentials() throws IOException {
        Properties props = new Properties();
        File configFile = new File(configFilePath);

        if (configFile.exists()) {
            props.load(Files.newBufferedReader(configFile.toPath()));
            
            boolean needsUpdate = false;
            
            if (!props.containsKey("Signaldir")) {
                props.setProperty("Signaldir", defaultSignalDirPath);
                needsUpdate = true;
            }
            
            if (!props.containsKey("BaseUrl")) {
                props.setProperty("BaseUrl", DEFAULT_BASE_URL);
                needsUpdate = true;
            }
            
            if (!props.containsKey("SignalId")) {
                props.setProperty("SignalId", DEFAULT_SIGNAL_ID);
                needsUpdate = true;
            }
            
            if (needsUpdate) {
                try (FileWriter writer = new FileWriter(configFile)) {
                    props.store(writer, "Login Configuration");
                }
                LoggerManagerE.info("Updated configuration with default values");
            }
            
            return new CredentialsE(
                props.getProperty("username"),
                props.getProperty("password")
            );
        } else {
            return createNewCredentials(configFile);
        }
    }

    private CredentialsE createNewCredentials(File configFile) throws IOException {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("Username: ");
            String username = scanner.nextLine();
            System.out.print("Password: ");
            String password = scanner.nextLine();

            Properties props = new Properties();
            props.setProperty("username", username);
            props.setProperty("password", password);
            props.setProperty("Signaldir", defaultSignalDirPath);
            props.setProperty("BaseUrl", DEFAULT_BASE_URL);
            props.setProperty("SignalId", DEFAULT_SIGNAL_ID);

            try (FileWriter writer = new FileWriter(configFile)) {
                props.store(writer, "Login Configuration");
            }

            LoggerManagerE.info("Created new config file with default values");
            return new CredentialsE(username, password);
        }
    }

    public String getLogConfigPath() {
        return configDirPath + "\\log4j2.xml";
    }

    public String getDownloadPath() {
        return downloadPath;
    }

    public String getSignalDirPath() {
        try {
            Properties props = new Properties();
            File configFile = new File(configFilePath);
            if (configFile.exists()) {
                props.load(Files.newBufferedReader(configFile.toPath()));
                String signalDir = props.getProperty("Signaldir", defaultSignalDirPath);
                createDirectory(signalDir);
                return signalDir;
            }
        } catch (IOException e) {
            LoggerManagerE.error("Error reading signal directory from config: " + e.getMessage());
        }
        createDirectory(defaultSignalDirPath);
        return defaultSignalDirPath;
    }

    public String getBaseUrl() {
        try {
            Properties props = new Properties();
            File configFile = new File(configFilePath);
            if (configFile.exists()) {
                props.load(Files.newBufferedReader(configFile.toPath()));
                String baseUrl = props.getProperty("BaseUrl", DEFAULT_BASE_URL);
                LoggerManagerE.info("Using base URL from config: " + baseUrl);
                return baseUrl;
            }
        } catch (IOException e) {
            LoggerManagerE.error("Error reading base URL from config: " + e.getMessage());
        }
        return DEFAULT_BASE_URL;
    }

    public String getSignalId() {
        try {
            Properties props = new Properties();
            File configFile = new File(configFilePath);
            if (configFile.exists()) {
                props.load(Files.newBufferedReader(configFile.toPath()));
                String configuredId = props.getProperty("SignalId");
                if (configuredId != null && !configuredId.trim().isEmpty()) {
                    LoggerManagerE.info("Using Signal ID from config file: " + configuredId);
                    return configuredId;
                }
            }
            LoggerManagerE.info("Using default Signal ID: " + DEFAULT_SIGNAL_ID);
            return DEFAULT_SIGNAL_ID;
        } catch (IOException e) {
            LoggerManagerE.error("Error reading signal ID from config: " + e.getMessage());
            return DEFAULT_SIGNAL_ID;
        }
    }

    public String getFullSignalUrl() {
        String signalId = getSignalId();
        String baseUrl = getBaseUrl();
        String fullUrl = baseUrl + "/" + signalId + "?source=Site+Signals+Subscriptions";
        LoggerManagerE.info("Generated full URL: " + fullUrl);
        return fullUrl;
    }
}