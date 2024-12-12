package monitor;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import logging.LoggerManagerE;
import config.CredentialsE;
import analyzer.TradeAnalyzer;

import org.openqa.selenium.By;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class HybridTradeMonitor {
    private final String baseDir;
    private final String providerName;
    private final CredentialsE credentials;
    private final TradeAnalyzer analyzer;
    private final String signalUrl;
    private ChromeDriver driver;  
    private final HttpClient httpClient;
    private String sessionCookie;
    private ScheduledExecutorService scheduler;
    private static final String LOGIN_URL = "https://www.mql5.com/en/auth_login";
    private static final String SIGNAL_BASE_URL = "https://www.mql5.com/en/signals/%s?source=Site+Signals+Subscriptions";
    
    public HybridTradeMonitor(String baseDir, String signalId, 
            CredentialsE credentials, String signalDir) {
        this.baseDir = baseDir;
        this.providerName = signalId;
        this.credentials = credentials;
        this.analyzer = new TradeAnalyzer(
            baseDir + File.separator + "trades_log.txt", 
            signalId,
            signalDir
        );
        this.signalUrl = String.format(SIGNAL_BASE_URL, signalId);
        this.httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    }

    private void performInitialLogin() {
        try {
            LoggerManagerE.info("Starting initial Selenium login process...");
            
            ChromeOptions options = new ChromeOptions();
            // Browser-Erkennung umgehen
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--start-maximized");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-blink-features=AutomationControlled");
            options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
            
            // WebDriver Manager Setup
            io.github.bonigarcia.wdm.WebDriverManager.chromedriver().setup();
            driver = new ChromeDriver(options);
            
            LoggerManagerE.info("Opening login page...");
            driver.get(LOGIN_URL);
            
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            LoggerManagerE.info("Waiting for login form...");
            
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("Login")));
            LoggerManagerE.info("Login form found, entering credentials...");
            
            driver.findElement(By.id("Login")).sendKeys(credentials.getUsername());
            driver.findElement(By.id("Password")).sendKeys(credentials.getPassword());
            Thread.sleep(1000);
            
            LoggerManagerE.info("Clicking login button...");
            driver.findElement(By.id("loginSubmit")).click();
            Thread.sleep(2000);
            
            // Extrahiere Cookies für spätere HTTP-Requests
            LoggerManagerE.info("Extracting session cookies...");
            var cookies = driver.manage().getCookies();
            StringBuilder cookieString = new StringBuilder();
            for (var cookie : cookies) {
                cookieString.append(cookie.getName()).append("=").append(cookie.getValue()).append("; ");
            }
            sessionCookie = cookieString.toString();
            
            LoggerManagerE.info("Login successful, session cookie extracted");
            
        } catch (Exception e) {
            LoggerManagerE.error("Initial login failed: " + e.getMessage());
            throw new RuntimeException("Initial login failed", e);
        } finally {
            if (driver != null) {
                driver.quit();
                driver = null;
            }
        }
    }

    private String fetchPageWithHttp() {
        try {
            String urlWithTimestamp = signalUrl + "&nocache=" + System.currentTimeMillis();
            LoggerManagerE.info("Fetching page: " + urlWithTimestamp);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlWithTimestamp))
                .header("Cookie", sessionCookie)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .GET()
                .build();

            LoggerManagerE.info("Sending HTTP request with cookies: " + sessionCookie);
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
                
            String content = response.body();
            
            // Prüfe ob die Session noch gültig ist
            if (content.contains("To see trades in realtime, please log in")) {
                LoggerManagerE.info("Session expired, performing new login...");
                performInitialLogin();
                return fetchPageWithHttp();  // Retry nach neuem Login
            }

            // Speichere die Seite
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = baseDir + File.separator + providerName + File.separator + timestamp + ".html";
            File dir = new File(baseDir + File.separator + providerName);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            try (FileWriter writer = new FileWriter(fileName)) {
                writer.write(content);
                LoggerManagerE.info("Page saved to: " + fileName);
            }
            
            return content;

        } catch (Exception e) {
            LoggerManagerE.error("Error fetching page via HTTP: " + e.getMessage());
            
            // Versuche ein Re-Login wenn die Session abgelaufen sein könnte
            LoggerManagerE.info("Attempting re-login due to error...");
            try {
                performInitialLogin();
                return fetchPageWithHttp();
            } catch (Exception loginError) {
                LoggerManagerE.error("Re-login also failed: " + loginError.getMessage());
                return null;
            }
        }
    }

    public void startMonitoring() {
        try {
            LoggerManagerE.info("Starting monitoring for Signal Provider ID: " + providerName);
            
            // Initiales Login mit Selenium
            performInitialLogin();
            
            // Erster Abruf
            String content = fetchPageWithHttp();
            if (content != null) {
                analyzer.analyzeHtmlContent(content);
            }
            
            // Schedule für regelmäßige HTTP-Abrufe
            scheduler = Executors.newScheduledThreadPool(1);
            
            // Berechne Zeit bis zur nächsten Viertelstunde minus 20 Sekunden
            LocalDateTime now = LocalDateTime.now();
            
            int currentMinute = now.getMinute();
            int[] checkMinutes = {14, 29, 44, 59};  // Viertelstunden - 1, damit wir 20 Sekunden davor sind
            
            // Finde die nächste Viertelstunde
            int nextMinute = -1;
            for (int checkMinute : checkMinutes) {
                if (currentMinute < checkMinute || 
                    (currentMinute == checkMinute && now.getSecond() < 40)) {
                    nextMinute = checkMinute;
                    break;
                }
            }
            
            // Wenn keine nächste Viertelstunde in dieser Stunde, nimm die erste der nächsten Stunde
            if (nextMinute == -1) {
                nextMinute = checkMinutes[0];
                now = now.plusHours(1);
            }
            
            // Setze die Zeit auf die berechnete Minute und 40 Sekunden
            LocalDateTime nextRun = now
                .withMinute(nextMinute)
                .withSecond(40)
                .withNano(0);
            
            // Wenn die berechnete Zeit in der Vergangenheit liegt, füge eine Stunde hinzu
            if (nextRun.isBefore(LocalDateTime.now())) {
                nextRun = nextRun.plusHours(1);
            }
            
            // Berechne die Verzögerung bis zur ersten Ausführung
            long initialDelay = Duration.between(LocalDateTime.now(), nextRun).toMillis();
            
            LoggerManagerE.info("Next page fetch scheduled for: " + 
                nextRun.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            
            // Schedule mit fester Rate von 15 Minuten
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    LocalDateTime fetchTime = LocalDateTime.now();
                    LoggerManagerE.info("Fetching page at: " + 
                        fetchTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    
                    String pageContent = fetchPageWithHttp();
                    if (pageContent != null) {
                        analyzer.analyzeHtmlContent(pageContent);
                    }
                    
                    // Berechne nächsten Ausführungszeitpunkt
                    LocalDateTime next = fetchTime.plusMinutes(15);
                    next = next.withSecond(40);
                    LoggerManagerE.info("Next page fetch scheduled for: " + 
                        next.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                } catch (Exception e) {
                    LoggerManagerE.error("Error in monitoring task: " + e.getMessage());
                }
            }, initialDelay, 15 * 60 * 1000, TimeUnit.MILLISECONDS);
            
            LoggerManagerE.info("Monitoring scheduled successfully");
            
        } catch (Exception e) {
            LoggerManagerE.error("Error starting monitor: " + e.getMessage());
            throw new RuntimeException("Failed to start monitoring", e);
        }
    }

    public void stopMonitoring() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                LoggerManagerE.info("Monitoring stopped");
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}