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
        "<td[^>]*data-label=\"Time\">([^<]+)</td>\\s*" +
        "<td[^>]*data-label=\"Type\">(Buy|Sell|Buy Stop|Sell Stop)</td>",
        Pattern.DOTALL
    );

    private LocalDateTime lastUpdateTime = null;

    public TradeAnalyzer(String logFilePath) {
        this.logFilePath = logFilePath;
    }

    public void analyzeHtmlFile(String htmlFilePath) {
        try {
            String content = readFile(htmlFilePath);
            Matcher matcher = TRADE_PATTERN.matcher(content);
            
            while (matcher.find()) {
                String timeStr = matcher.group(1).trim();
                String type = matcher.group(2);
                
                // Parse trade time
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
                LocalDateTime tradeTime = LocalDateTime.parse(timeStr, formatter);
                
                // Only log if this is a new trade or first run
                if (lastUpdateTime == null || tradeTime.isAfter(lastUpdateTime)) {
                    String subsequentContent = content.substring(matcher.end(), content.indexOf("</tr>", matcher.end()));
                    logTrade(type, subsequentContent);
                }
            }
            
            // Update lastUpdateTime
            lastUpdateTime = LocalDateTime.now();
            
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

    private void logTrade(String type, String rowContent) {
        // Extrahiere die Werte aus den nachfolgenden TD-Elementen
        Pattern valuePattern = Pattern.compile("<td[^>]*>([^<]+)</td>");
        Matcher valueMatcher = valuePattern.matcher(rowContent);
        
        StringBuilder values = new StringBuilder();
        while (valueMatcher.find()) {
            String value = valueMatcher.group(1)
                .replaceAll("&nbsp;", "")
                .replaceAll("\\s+", "");
            values.append(value).append(", ");
        }

        String logEntry = String.format("[%s] Type: %s, Values: %s%n",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            type, values.toString());
            
        try (FileWriter writer = new FileWriter(logFilePath, true)) {
            writer.write(logEntry);
            logger.info("Trade logged: " + logEntry.trim());
        } catch (IOException e) {
            logger.error("Error writing to log file", e);
        }
    }
}