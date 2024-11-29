




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

    public StartExitLoader() {}

    public static void main(String[] args) {
        TradeMonitor monitor = null;
        WebDriver driverE = null;
    
        
        
        
        
        try {
            // Initialize configuration
            ConfigurationManagerE configManager = new ConfigurationManagerE("C:\\tmp\\mql5");
            configManager.initializeDirectories();
            CredentialsE credentials = configManager.getOrCreateCredentials();
            // Initialize logger
            LoggerManagerE.initializeLogger(configManager.getLogConfigPath());

           

            // Initialize WebDriver
            WebDriverManagerE webDriverManager = new WebDriverManagerE(configManager.getDownloadPath());
            driverE = webDriverManager.initializeDriver();

         // Get credentials
           
            // Initialize and start trading monitor
            monitor = new TradeMonitor(
            	    driverE,
            	    "c:\\tmp\\mql5\\aktTrades",
            	    // "2235152"  // AI-power
            	    "2018455",    // goden bug
            	    credentials   // Credentials-Objekt vom ConfigurationManagerE
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