package config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;
import java.util.Scanner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConfigurationManagerE {
    private final String rootDirPath;
    private final String configDirPath;
    private final String configFilePath;
    private final String logDirPath;
    private final String downloadPath;
    private final String defaultSignalDirPath;
    private static final Logger logger = LogManager.getLogger(ConfigurationManagerE.class);

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
                logger.info("Directory created: " + path);
            } else {
                logger.error("Could not create directory: " + path);
            }
        }
    }

    public CredentialsE getOrCreateCredentials() throws IOException {
        Properties props = new Properties();
        File configFile = new File(configFilePath);

        if (configFile.exists()) {
            props.load(Files.newBufferedReader(configFile.toPath()));
            
            // Prüfe ob Signaldir existiert, wenn nicht, füge es hinzu
            if (!props.containsKey("Signaldir")) {
                props.setProperty("Signaldir", defaultSignalDirPath);
                try (FileWriter writer = new FileWriter(configFile)) {
                    props.store(writer, "Login Configuration");
                }
                logger.info("Added default signal directory to config: " + defaultSignalDirPath);
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

            try (FileWriter writer = new FileWriter(configFile)) {
                props.store(writer, "Login Configuration");
            }

            logger.info("Created new config file with default signal directory: " + defaultSignalDirPath);
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
                createDirectory(signalDir); // Stelle sicher, dass das Verzeichnis existiert
                return signalDir;
            }
        } catch (IOException e) {
            logger.error("Error reading signal directory from config", e);
        }
        createDirectory(defaultSignalDirPath);
        return defaultSignalDirPath;
    }
}