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
    private static final String DEFAULT_SIGNAL_ID = "201845";
    private static final String DEFAULT_BASE_URL = "https://www.mql5.com/en/signals";

    public ConfigurationManagerE(String rootDirPath) {
        this.rootDirPath = rootDirPath;
        this.configDirPath = rootDirPath + "\\conf";
        this.configFilePath = configDirPath + "\\conf.txt";
        this.logDirPath = rootDirPath + "\\logs";
        this.downloadPath = rootDirPath + "\\download";
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
                props.setProperty("Signaldir", getSignalDirPath());
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
            props.setProperty("Signaldir", getSignalDirPath());
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
                // Überprüfe erst, ob ein benutzerdefinierter Pfad gesetzt ist
                String signalDir = props.getProperty("Signaldir");
                
                if (signalDir != null && !signalDir.trim().isEmpty()) {
                    LoggerManagerE.info("Using configured signal directory: " + signalDir);
                    createDirectory(signalDir);
                    return signalDir;
                }
            }

            // Wenn kein Pfad konfiguriert ist, suche nach MetaTrader Verzeichnis
            String appDataPath = System.getenv("APPDATA");
            String mt4Path = appDataPath + "\\MetaQuotes\\Terminal";
            File mt4Dir = new File(mt4Path);
            
            if (mt4Dir.exists() && mt4Dir.isDirectory()) {
                File[] terminals = mt4Dir.listFiles(File::isDirectory);
                if (terminals != null && terminals.length > 0) {
                    // Nimm das erste gefundene Terminal-Verzeichnis
                    String mql4Files = terminals[0].getAbsolutePath() + "\\MQL4\\Files";
                    LoggerManagerE.info("Using MetaTrader directory: " + mql4Files);
                    createDirectory(mql4Files);
                    return mql4Files;
                }
            }
            
            // Fallback auf Default-Pfad wenn kein MetaTrader gefunden wurde
            String defaultPath = "C:\\tmp\\mql5\\signals";
            LoggerManagerE.warn("No MetaTrader directory found, using default path: " + defaultPath);
            createDirectory(defaultPath);
            return defaultPath;
            
        } catch (IOException e) {
            LoggerManagerE.error("Error reading signal directory from config: " + e.getMessage());
            String defaultPath = "C:\\tmp\\mql5\\signals";
            createDirectory(defaultPath);
            return defaultPath;
        }
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
}
