package monitor;




import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;

import analyzer.TradeAnalyzer;

public class TradeMonitor {
    private static final Logger logger = LogManager.getLogger(TradeMonitor.class);
    private final WebDriver driver;
    private final String baseDir;
    private final String providerName;
    private final TradeAnalyzer analyzer;
    private final String SIGNAL_URL = "https://www.mql5.com/en/signals/2235152?source=Site+Signals+Subscriptions";

    public TradeMonitor(WebDriver driver, String baseDir, String providerName) {
        this.driver = driver;
        this.baseDir = baseDir;
        this.providerName = providerName;
        this.analyzer = new TradeAnalyzer(baseDir + File.separator + "trades_log.txt");
        createDirectories();
    }

    private void createDirectories() {
        File dir = new File(baseDir + File.separator + providerName);
        if (!dir.exists() && !dir.mkdirs()) {
            logger.error("Failed to create directory: " + dir.getPath());
        }
    }

    public void startMonitoring() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        
        Runnable task = () -> {
            try {
                String fileName = saveWebPage();
                analyzer.analyzeHtmlFile(fileName);
            } catch (Exception e) {
                logger.error("Error in monitoring task", e);
            }
        };

        // Schedule the task to run at specific times
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.withMinute((now.getMinute() / 15) * 15).withSecond(10);
        if (nextRun.isBefore(now)) {
            nextRun = nextRun.plusMinutes(15);
        }

        long initialDelay = java.time.Duration.between(now, nextRun).toMillis();
        scheduler.scheduleAtFixedRate(task, initialDelay, 15 * 60 * 1000, TimeUnit.MILLISECONDS);
    }

    private String saveWebPage() {
        try {
            driver.get(SIGNAL_URL);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = baseDir + File.separator + providerName + File.separator + timestamp + ".html";
            
            try (FileWriter writer = new FileWriter(fileName)) {
                writer.write(driver.getPageSource());
            }
            
            logger.info("Webpage saved: " + fileName);
            return fileName;
        } catch (Exception e) {
            logger.error("Error saving webpage", e);
            throw new RuntimeException("Failed to save webpage", e);
        }
    }
    
    public void stopMonitoring() {
        if (driver != null) {
            try {
                driver.quit();
                logger.info("WebDriver closed successfully");
            } catch (Exception e) {
                logger.error("Error closing WebDriver", e);
            }
        }
    }
}