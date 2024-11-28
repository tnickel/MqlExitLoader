package analyzer;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TradeAnalyzer {
    private static final Logger logger = LogManager.getLogger(TradeAnalyzer.class);
    private final String logFilePath;
    private static final Pattern TRADE_PATTERN = Pattern.compile(
        "(Buy Stop|Sell Stop).*?S/L:\\s*(\\d+\\.\\d+).*?T/P:\\s*(\\d+\\.\\d+)",
        Pattern.DOTALL
    );

    public TradeAnalyzer(String logFilePath) {
        this.logFilePath = logFilePath;
    }

    public void analyzeHtmlFile(String htmlFilePath) {
        try {
            String content = readFile(htmlFilePath);
            Matcher matcher = TRADE_PATTERN.matcher(content);
            
            while (matcher.find()) {
                String tradeType = matcher.group(1);
                String stopLoss = matcher.group(2);
                String takeProfit = matcher.group(3);
                
                logTrade(tradeType, stopLoss, takeProfit);
            }
        } catch (IOException e) {
            logger.error("Error analyzing HTML file: " + htmlFilePath, e);
        }
    }

    private String readFile(String filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private void logTrade(String type, String stopLoss, String takeProfit) {
        String logEntry = String.format("[%s] Typ: %s, S/L: %s, T/P: %s%n",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            type, stopLoss, takeProfit);
            
        try (FileWriter writer = new FileWriter(logFilePath, true)) {
            writer.write(logEntry);
            logger.info("Trade logged: " + logEntry.trim());
        } catch (IOException e) {
            logger.error("Error writing to log file", e);
        }
    }
}