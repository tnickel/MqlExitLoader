// StartExitLoader.java
import org.openqa.selenium.WebDriver;
import browser.WebDriverManagerE;
import config.ConfigurationManagerE;
import config.CredentialsE;
import logging.LoggerManagerE;
import monitor.TradeMonitor;

public class StartExitLoader {
    private static final String BASE_PATH = "C:\\tmp\\mql5";

    public StartExitLoader() {}

    public static void main(String[] args) {
        TradeMonitor monitor = null;
        WebDriver driverE = null;
        WebDriverManagerE webDriverManager = null;
        
        try {
            LoggerManagerE.info("Starting application...");
            
            LoggerManagerE.info("Initializing configuration...");
            ConfigurationManagerE configManager = new ConfigurationManagerE(BASE_PATH);
            configManager.initializeDirectories();

            LoggerManagerE.info("Initializing logger...");
            LoggerManagerE.initializeLogger(configManager.getLogConfigPath());

            LoggerManagerE.info("Getting credentials...");
            CredentialsE credentials = configManager.getOrCreateCredentials();

            LoggerManagerE.info("Initializing WebDriver...");
            webDriverManager = new WebDriverManagerE(configManager.getDownloadPath());
            driverE = webDriverManager.initializeDriver();

            LoggerManagerE.info("Setting up monitor...");
            monitor = new TradeMonitor(
                driverE,
                BASE_PATH + "\\aktTrades",
                configManager.getSignalId(),
                credentials,
                webDriverManager,
                configManager.getSignalDirPath()
            );
            
            LoggerManagerE.info("Starting monitoring...");
            monitor.startMonitoring();
            
            LoggerManagerE.info("Application running. Press Ctrl+C to stop.");
            Thread.sleep(Long.MAX_VALUE);

        } catch (Exception e) {
            LoggerManagerE.error("Error in main process: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (monitor != null) {
                LoggerManagerE.info("Stopping monitor...");
                monitor.stopMonitoring();
            }
            if (driverE != null) {
                try {
                    LoggerManagerE.info("Closing WebDriver...");
                    driverE.quit();
                } catch (Exception e) {
                    LoggerManagerE.error("Error closing WebDriver: " + e.getMessage());
                }
            }
            LoggerManagerE.shutdown();
        }
    }
}