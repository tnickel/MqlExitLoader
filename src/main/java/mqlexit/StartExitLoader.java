import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;

import browser.WebDriverManagerE;
import config.ConfigurationManagerE;
import config.CredentialsE;
import logging.LoggerManagerE;
import monitor.TradeMonitor;

public class StartExitLoader {
    private static final Logger logger = LogManager.getLogger(StartExitLoader.class);
    private static final String BASE_PATH = "C:\\tmp\\mql5";

    public StartExitLoader() {}

    public static void main(String[] args) {
        TradeMonitor monitor = null;
        WebDriver driverE = null;
        WebDriverManagerE webDriverManager = null;
        
        try {
            // Initialize configuration
            ConfigurationManagerE configManager = new ConfigurationManagerE(BASE_PATH);
            configManager.initializeDirectories();

            // Initialize logger
            LoggerManagerE.initializeLogger(configManager.getLogConfigPath());

            // Get credentials
            CredentialsE credentials = configManager.getOrCreateCredentials();

            // Initialize WebDriver
            webDriverManager = new WebDriverManagerE(configManager.getDownloadPath());
            driverE = webDriverManager.initializeDriver();

            // Initialize and start trading monitor
            monitor = new TradeMonitor(
                driverE,
                BASE_PATH + "\\aktTrades",
                "2018455",  // Signal ID
                credentials,
                webDriverManager
            );
            
            // Start the monitoring process
            monitor.startMonitoring();
            
            // Keep the application running
            logger.info("Application started successfully. Press Ctrl+C to stop.");
            Thread.sleep(Long.MAX_VALUE);

        } catch (Exception e) {
            logger.error("Error in main process", e);
        } finally {
            // Ensure proper cleanup on shutdown
            if (monitor != null) {
                monitor.stopMonitoring();
            }
            if (driverE != null) {
                try {
                    driverE.quit();
                    logger.info("WebDriver closed successfully");
                } catch (Exception e) {
                    logger.error("Error closing WebDriver", e);
                }
            }
        }
    }
}