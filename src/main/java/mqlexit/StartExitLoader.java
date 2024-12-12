// StartExitLoader.java
import logging.LoggerManagerE;
import config.ConfigurationManagerE;
import config.CredentialsE;
import monitor.HttpTradeMonitor;
import monitor.HeadlessChromeTradeMonitor;
import monitor.HybridTradeMonitor;
public class StartExitLoader {
    private static final String BASE_PATH = "C:\\tmp\\mql5";

    public StartExitLoader() {}

    public static void main(String[] args) {
    	HybridTradeMonitor monitor =null;
        
        try {
            LoggerManagerE.info("Starting application...");
            
            LoggerManagerE.info("Initializing configuration...");
            ConfigurationManagerE configManager = new ConfigurationManagerE(BASE_PATH);
            configManager.initializeDirectories();

            LoggerManagerE.info("Initializing logger...");
            LoggerManagerE.initializeLogger(configManager.getLogConfigPath());

            LoggerManagerE.info("Getting credentials...");
            CredentialsE credentials = configManager.getOrCreateCredentials();

            LoggerManagerE.info("Setting up monitor...");
             monitor = new HybridTradeMonitor(
            	    BASE_PATH + "\\aktTrades",
            	    configManager.getSignalId(),
            	    credentials,
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
            LoggerManagerE.shutdown();
        }
    }
}