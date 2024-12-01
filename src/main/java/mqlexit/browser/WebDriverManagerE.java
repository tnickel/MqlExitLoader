// WebDriverManagerE.java
package browser;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import io.github.bonigarcia.wdm.WebDriverManager;
import java.util.HashMap;
import logging.LoggerManagerE;

public class WebDriverManagerE {
    private final String downloadPath;

    public WebDriverManagerE(String downloadPath) {
        this.downloadPath = downloadPath;
    }

    public WebDriver initializeDriver() {
        try {
            LoggerManagerE.info("Setting up WebDriver...");
            WebDriverManager.chromedriver().setup();
            
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            
            HashMap<String, Object> prefs = new HashMap<>();
            prefs.put("download.default_directory", downloadPath);
            options.setExperimentalOption("prefs", prefs);
            
            try {
                LoggerManagerE.info("Initializing ChromeDriver with basic options");
                return new ChromeDriver(options);
            } catch (Exception e) {
                LoggerManagerE.warn("First attempt failed, trying with additional options: " + e.getMessage());
                options.addArguments("--ignore-certificate-errors");
                options.addArguments("--disable-gpu");
                options.addArguments("--window-size=1920,1080");
                return new ChromeDriver(options);
            }
        } catch (Exception e) {
            LoggerManagerE.error("Error initializing WebDriver: " + e.getMessage());
            throw e;
        }
    }
}
