package monitor;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import com.google.common.base.Function;
import org.openqa.selenium.NoSuchWindowException;

import analyzer.TradeAnalyzer;
import config.CredentialsE;
import browser.WebDriverManagerE;

public class TradeMonitor {
    private static final Logger logger = LogManager.getLogger(TradeMonitor.class);
    private WebDriver driver;
    private final String baseDir;
    private final String providerName;
    private final TradeAnalyzer analyzer;
    private final String signalUrl;
    private static final String SIGNAL_BASE_URL = "https://www.mql5.com/en/signals/%s?source=Site+Signals+Subscriptions";
    private final CredentialsE credentials;
    private WebDriverWait wait;
    private boolean isLoggedIn = false;
    private final WebDriverManagerE webDriverManager;

    public TradeMonitor(WebDriver driver, String baseDir, String signalId, CredentialsE credentials, WebDriverManagerE webDriverManager) {
        this.webDriverManager = webDriverManager;
        this.driver = driver;
        this.baseDir = baseDir;
        this.providerName = signalId;
        this.credentials = credentials;
        this.analyzer = new TradeAnalyzer(baseDir + File.separator + "trades_log.txt", signalId);
        this.signalUrl = String.format(SIGNAL_BASE_URL, signalId);
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(60));
        createDirectories();
    }

    private void createDirectories() {
        File dir = new File(baseDir + File.separator + providerName);
        if (!dir.exists() && !dir.mkdirs()) {
            logger.error("Failed to create directory: " + dir.getPath());
        }
    }

    private void reinitializeDriver() {
        try {
            if (driver != null) {
                driver.quit();
            }
        } catch (Exception e) {
            logger.error("Error closing old driver", e);
        }
        driver = webDriverManager.initializeDriver();
        wait = new WebDriverWait(driver, Duration.ofSeconds(60));
        isLoggedIn = false;
    }

    private void performLogin() {
        logger.info("Starting login process...");
        driver.get("https://www.mql5.com/en/auth_login");

        Function<WebDriver, WebElement> waitFunction = ExpectedConditions.visibilityOfElementLocated(By.id("Login"));
        WebElement usernameField = wait.until(waitFunction);
        WebElement passwordField = driver.findElement(By.id("Password"));

        usernameField.sendKeys(credentials.getUsername());
        passwordField.sendKeys(credentials.getPassword());

        clickLoginButton();
        verifyLogin();
        isLoggedIn = true;
    }

    private void clickLoginButton() {
        WebElement loginButton = findLoginButton();
        if (loginButton != null) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", loginButton);
        } else {
            throw new RuntimeException("Login button could not be found");
        }
    }

    private WebElement findLoginButton() {
        try {
            return driver.findElement(By.id("loginSubmit"));
        } catch (Exception e) {
            try {
                return driver.findElement(By.cssSelector("input.button.button_yellow.qa-submit"));
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private void verifyLogin() {
        try {
            wait.until(ExpectedConditions.urlContains("/en"));
            Thread.sleep(2000);
        } catch (Exception e) {
            throw new RuntimeException("Login verification failed", e);
        }
    }

    public void startMonitoring() {
        try {
            logger.info("Starting monitoring for Signal Provider ID: " + providerName);
            performLogin();
            
            // Führe erste Prüfung sofort aus
            String fileName = saveWebPage();
            analyzer.analyzeHtmlFile(fileName);
            
        } catch (Exception e) {
            logger.error("Initial login failed", e);
            return;
        }
        
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        
        Runnable task = () -> {
            try {
                if (!isLoggedIn) {
                    performLogin();
                }
                String fileName = saveWebPage();
                analyzer.analyzeHtmlFile(fileName);
            } catch (NoSuchWindowException e) {
                logger.error("Browser window was closed, reinitializing...");
                reinitializeDriver();
                try {
                    performLogin();
                } catch (Exception loginE) {
                    logger.error("Failed to reinitialize after window closed", loginE);
                }
            } catch (Exception e) {
                logger.error("Error in monitoring task", e);
                isLoggedIn = false;
            }
        };

        // Berechne Zeit bis zum nächsten definierten Intervall
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun;

        int currentMinute = now.getMinute();
        int[] checkMinutes = {14, 29, 44, 59};
        
        // Finde das nächste Intervall
        int nextMinute = -1;
        for (int checkMinute : checkMinutes) {
            if (currentMinute < checkMinute || 
               (currentMinute == checkMinute && now.getSecond() < 10)) {
                nextMinute = checkMinute;
                break;
            }
        }
        
        // Wenn kein nächstes Intervall in dieser Stunde gefunden wurde,
        // nehme das erste Intervall der nächsten Stunde
        if (nextMinute == -1) {
            nextRun = now.plusHours(1)
                        .withMinute(14)
                        .withSecond(10)
                        .withNano(0);
        } else {
            nextRun = now.withMinute(nextMinute)
                        .withSecond(10)
                        .withNano(0);
        }

        long initialDelay = java.time.Duration.between(now, nextRun).toMillis();
        
        logger.info("Scheduled next check for Signal Provider " + providerName + " at: " + 
            nextRun.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        scheduler.scheduleAtFixedRate(task, initialDelay, 15 * 60 * 1000, TimeUnit.MILLISECONDS);
    }

    private String saveWebPage() {
        try {
            logger.info("Loading URL: " + signalUrl);
            driver.get(signalUrl);
            
            // Warte auf die Tabelle mit den Trade-Daten
            wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("td[data-label='Type']")
            ));
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = baseDir + File.separator + providerName + File.separator + timestamp + ".html";
            
            try (FileWriter writer = new FileWriter(fileName)) {
                writer.write(driver.getPageSource());
            }
            
            logger.info("Webpage saved: " + fileName);
            return fileName;
        } catch (Exception e) {
            logger.error("Error saving webpage", e);
            isLoggedIn = false;
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