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
    private static final Logger logger = LogManager.getLogger(ConfigurationManagerE.class);

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

            try (FileWriter writer = new FileWriter(configFile)) {
                props.store(writer, "Login Configuration");
            }

            return new CredentialsE(username, password);
        }
    }

    public String getLogConfigPath() {
        return rootDirPath + "\\log4j2.xml";
    }

    public String getDownloadPath() {
        return downloadPath;
    }
}
