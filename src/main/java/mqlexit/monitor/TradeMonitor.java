package monitor;

import java.io.File;
import java.io.FileWriter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.google.common.base.Function;

import analyzer.TradeAnalyzer;
import browser.WebDriverManagerE;
import config.CredentialsE;

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
    private boolean initialLoginDone = false;

    public TradeMonitor(WebDriver driver, String baseDir, String signalId, 
            CredentialsE credentials, WebDriverManagerE webDriverManager,
            String signalDir) {
        this.webDriverManager = webDriverManager;
        this.driver = driver;
        this.baseDir = baseDir;
        this.providerName = signalId;
        this.credentials = credentials;
        this.analyzer = new TradeAnalyzer(
           baseDir + File.separator + "trades_log.txt", 
           signalId,
           signalDir
        );
        this.signalUrl = String.format(SIGNAL_BASE_URL, signalId);
        
        // Setze erweiterte Timeouts
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));
        
        // Längerer Timeout für explizite Wartezeiten
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(90));
        
        createDirectories();
    }

    private void createDirectories() {
        File dir = new File(baseDir + File.separator + providerName);
        if (!dir.exists() && !dir.mkdirs()) {
            logger.error("Failed to create directory: " + dir.getPath());
        }
    }

    private void performLogin() {
        logger.info("Starting login process...");
        try {
            driver.get("https://www.mql5.com/en/auth_login");
            
            // Warte auf das Login-Formular
            Function<WebDriver, WebElement> waitFunction = ExpectedConditions.visibilityOfElementLocated(By.id("Login"));
            WebElement usernameField = wait.until(waitFunction);
            WebElement passwordField = driver.findElement(By.id("Password"));

            // Eingabefelder leeren und neu befüllen
            usernameField.clear();
            passwordField.clear();
            usernameField.sendKeys(credentials.getUsername());
            passwordField.sendKeys(credentials.getPassword());

            // Warte kurz vor dem Klick
            Thread.sleep(1000);
            
            clickLoginButton();
            verifyLogin();
            isLoggedIn = true;
            
        } catch (Exception e) {
            logger.error("Login process failed", e);
            isLoggedIn = false;
            throw new RuntimeException("Login failed", e);
        }
    }

    private void clickLoginButton() {
        WebElement loginButton = findLoginButton();
        if (loginButton != null) {
            try {
                wait.until(ExpectedConditions.elementToBeClickable(loginButton));
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", loginButton);
            } catch (Exception e) {
                throw new RuntimeException("Failed to click login button", e);
            }
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
            
            // Zusätzliche Überprüfung des Login-Status
            if (driver.getCurrentUrl().contains("auth_login")) {
                throw new RuntimeException("Still on login page after login attempt");
            }
        } catch (Exception e) {
            throw new RuntimeException("Login verification failed", e);
        }
    }

    private String saveWebPage() {
        int maxRetries = 3;
        int currentTry = 0;
        
        while (currentTry < maxRetries) {
            try {
                logger.info("Loading URL: " + signalUrl + " (Attempt " + (currentTry + 1) + " of " + maxRetries + ")");
                
                // Überprüfe Login-Status vor dem Laden der Seite
                if (!isLoggedIn) {
                    logger.info("User not logged in, performing login first...");
                    performLogin();
                }
                
                // Cache leeren und neu laden erzwingen
                ((JavascriptExecutor) driver).executeScript("window.localStorage.clear();");
                ((JavascriptExecutor) driver).executeScript("window.sessionStorage.clear();");
                
                // Setze Cache-Control Header
                String urlWithTimestamp = signalUrl + (signalUrl.contains("?") ? "&" : "?") + "nocache=" + System.currentTimeMillis();
                
                // Füge kleine Verzögerung hinzu
                Thread.sleep(2000);
                
                // Setze Page Load Timeout
                driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
                
                // Lade die Seite
                driver.get(urlWithTimestamp);
                
                // Warte auf die Tabelle mit den Trade-Daten mit erhöhtem Timeout
                wait = new WebDriverWait(driver, Duration.ofSeconds(90));
                wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("td[data-label='Type']")
                ));
                
                // Zusätzliche Wartezeit für dynamische Inhalte
                Thread.sleep(3000);
                
                // Prüfe auf Trade-Signale
                String pageSource = driver.getPageSource();
                boolean hasSignals = containsTradeSignal(pageSource);
                if (!hasSignals) {
                    logger.info("No trade signals found - page not saved");
                    return null;
                }
                logger.info("Trade signals found - saving page...");
                
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String fileName = baseDir + File.separator + providerName + File.separator + timestamp + ".html";
                
                // Speichere die Seite
                try (FileWriter writer = new FileWriter(fileName)) {
                    writer.write(pageSource);
                }
                
                logger.info("Webpage saved successfully: " + fileName);
                return fileName;
                
            } catch (Exception e) {
                currentTry++;
                logger.error("Error saving webpage (Attempt " + currentTry + " of " + maxRetries + ")", e);
                
                if (currentTry >= maxRetries) {
                    throw new RuntimeException("Failed to save webpage after " + maxRetries + " attempts", e);
                }
                
                // Wenn der Fehler aufgrund eines Login-Problems auftritt, setze isLoggedIn zurück
                if (e.getMessage().contains("auth_login") || 
                    (driver != null && driver.getCurrentUrl().contains("auth_login"))) {
                    isLoggedIn = false;
                }
                
                try {
                    // Warte vor dem nächsten Versuch
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        throw new RuntimeException("Failed to save webpage after " + maxRetries + " attempts");
    }

    private boolean containsTradeSignal(String content) {
        Pattern pattern = Pattern.compile("<td[^>]*data-label=\"Type\"[^>]*>(Buy|Sell|Buy Stop|Sell Stop)</td>");
        Matcher matcher = pattern.matcher(content);
        return matcher.find();
    }

    public void startMonitoring() {
        try {
            if (!initialLoginDone) {
                logger.info("Starting monitoring for Signal Provider ID: " + providerName);
                performLogin();
                initialLoginDone = true;
                
                String fileName = saveWebPage();
                if (fileName != null) {
                    analyzer.analyzeHtmlFile(fileName);
                }
            }
            
        } catch (Exception e) {
            logger.error("Initial login failed", e);
            showErrorDialog("Login fehlgeschlagen", 
                "Der Login konnte nicht durchgeführt werden. Bitte überprüfen Sie Ihre Zugangsdaten und starten Sie das Programm neu.");
            return;
        }
        
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        
        Runnable task = () -> {
            try {
                String fileName = saveWebPage();
                if (fileName != null) {
                    analyzer.analyzeHtmlFile(fileName);
                }
            } catch (NoSuchWindowException e) {
                logger.error("Browser window was closed", e);
                logger.info("Please restart the application to perform a new login");
                stopMonitoring();
            } catch (Exception e) {
                logger.error("Error in monitoring task", e);
                // Versuche einen erneuten Login beim nächsten Durchlauf
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
                (currentMinute == checkMinute && now.getSecond() < 45)) {
                nextMinute = checkMinute;
                break;
            }
        }
        
        // Wenn kein nächstes Intervall in dieser Stunde gefunden wurde,
        // nehme das erste Intervall der nächsten Stunde
        if (nextMinute == -1) {
            nextRun = now.plusHours(1)
                        .withMinute(14)
                        .withSecond(45)
                        .withNano(0);
        } else {
            nextRun = now.withMinute(nextMinute)
                        .withSecond(45)
                        .withNano(0);
        }

        long initialDelay = java.time.Duration.between(now, nextRun).toMillis();
        
        logger.info("Scheduled next check for Signal Provider " + providerName + " at: " + 
            nextRun.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        scheduler.scheduleAtFixedRate(task, initialDelay, 15 * 60 * 1000, TimeUnit.MILLISECONDS);
    }
    
    private void showErrorDialog(String title, String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null,
                message,
                title,
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        });
    }
    
    public void stopMonitoring() {
        if (driver != null) {
            try {
                logger.info("Closing WebDriver...");
                driver.quit();
                logger.info("WebDriver closed successfully");
            } catch (Exception e) {
                logger.error("Error closing WebDriver", e);
            }
        }
    }
}