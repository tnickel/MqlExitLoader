package mqlexit.loader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;

import browser.WebDriverManagerE;
import config.ConfigurationManagerE;
import config.CredentialsE;
import logging.LoggerManagerE;

public class StartExitLoader {
    private static final Logger logger = LogManager.getLogger(StartExitLoader.class);

    public StartExitLoader() {}

    public static void main(String[] args) {
        try {
            // Initialize configuration
            ConfigurationManagerE configManager = new ConfigurationManagerE("C:\\tmp\\mql5");
            configManager.initializeDirectories();

            // Initialize logger
            LoggerManagerE.initializeLogger(configManager.getLogConfigPath());

            // Get credentials
            CredentialsE credentials = configManager.getOrCreateCredentials();

            // Initialize WebDriver
            WebDriverManagerE webDriverManager = new WebDriverManagerE(configManager.getDownloadPath());
            WebDriver driverE = webDriverManager.initializeDriver();

        } catch (Exception e) {
            logger.error("Error in main process", e);
        }
    }
}