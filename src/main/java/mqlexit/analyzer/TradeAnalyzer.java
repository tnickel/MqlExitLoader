package analyzer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class TradeAnalyzer {
    private static final Logger logger = LogManager.getLogger(TradeAnalyzer.class);
    private final String logFilePath;
    private final String providerName;
    private static final String SIGNAL_FILE_PATH = "C:\\tmp\\mql5\\signals\\signal.txt";
    
    private static final Pattern FIRST_TBODY_PATTERN = Pattern.compile(
        "<tbody>(.*?)</tbody>",
        Pattern.DOTALL
    );
    
    private static final Pattern TRADE_PATTERN = Pattern.compile(
        "<td data-label=\"Type\">(Buy|Sell|Buy Stop|Sell Stop)</td>",
        Pattern.DOTALL
    );

    public TradeAnalyzer(String logFilePath, String providerName) {
        this.logFilePath = logFilePath;
        this.providerName = providerName;
        createSignalDirectory();
    }

    private void createSignalDirectory() {
        File signalDir = new File("C:\\tmp\\mql5\\signals");
        if (!signalDir.exists()) {
            boolean created = signalDir.mkdirs();
            if (created) {
                logger.info("Created signals directory: " + signalDir.getAbsolutePath());
            } else {
                logger.error("Failed to create signals directory");
            }
        }
    }

    public void analyzeHtmlFile(String htmlFilePath) {
        try {
            String content = readFile(htmlFilePath);
            List<Map<String, String>> allTradeInfo = new ArrayList<>();
            
            Matcher tbodyMatcher = FIRST_TBODY_PATTERN.matcher(content);
            if (!tbodyMatcher.find()) {
                logger.info("No tbody found");
                return;
            }
            
            String tableContent = tbodyMatcher.group(1);
            Matcher tradeMatcher = TRADE_PATTERN.matcher(tableContent);
            while (tradeMatcher.find()) {
                String type = tradeMatcher.group(1);
                
                int rowStart = tableContent.lastIndexOf("<tr", tradeMatcher.start());
                int rowEnd = tableContent.indexOf("</tr>", tradeMatcher.end()) + 5;
                if (rowStart >= 0 && rowEnd >= 0) {
                    String rowContent = tableContent.substring(rowStart, rowEnd);
                    Map<String, String> tradeInfo = extractTradeInfo(rowContent);
                    allTradeInfo.add(tradeInfo);
                    logTrade(type, rowContent);
                }
            }
            
            if (!allTradeInfo.isEmpty()) {
                writeSignalFile(allTradeInfo);
            }
        } catch (IOException e) {
            logger.error("Error analyzing HTML file: " + htmlFilePath, e);
        }
    }

    private Map<String, String> extractTradeInfo(String rowContent) {
        Map<String, String> tradeInfo = new LinkedHashMap<>();
        String[] labels = {"Symbol", "Time", "Type", "Volume", "Price", "S/L", "T/P"};
        
        for (String label : labels) {
            Pattern pattern = Pattern.compile("<td data-label=\"" + label + "\"[^>]*>([^<]+)</td>");
            Matcher matcher = pattern.matcher(rowContent);
            if (matcher.find()) {
                String value = matcher.group(1)
                    .replaceAll("&nbsp;", "")
                    .replaceAll("\\s+", " ")
                    .trim();
                tradeInfo.put(label, value);
            }
        }
        
        return tradeInfo;
    }

    private void writeSignalFile(List<Map<String, String>> allTradeInfo) {
        try {
            // Delete existing file if it exists
            File signalFile = new File(SIGNAL_FILE_PATH);
            if (signalFile.exists()) {
                signalFile.delete();
            }
            
            // Write new signals
            try (FileWriter writer = new FileWriter(signalFile)) {
                for (Map<String, String> tradeInfo : allTradeInfo) {
                    StringBuilder line = new StringBuilder();
                    for (String value : tradeInfo.values()) {
                        line.append(value).append(",");
                    }
                    // Remove last comma and add newline
                    if (line.length() > 0) {
                        line.setLength(line.length() - 1);
                    }
                    line.append("\n");
                    writer.write(line.toString());
                }
                logger.info("Signal file written: " + SIGNAL_FILE_PATH);
            }
        } catch (IOException e) {
            logger.error("Error writing signal file", e);
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
        Pattern valuePattern = Pattern.compile("<td[^>]*>([^<]+)</td>");
        Matcher valueMatcher = valuePattern.matcher(rowContent);
        
        StringBuilder values = new StringBuilder();
        while (valueMatcher.find()) {
            String value = valueMatcher.group(1)
                .replaceAll("&nbsp;", "")
                .replaceAll("\\s+", " ")
                .trim();
            values.append(value).append(", ");
        }

        String logEntry = String.format("[%s] [%s] Type: %s, Values: %s%n",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            providerName,
            type, values.toString());
            
        try (FileWriter writer = new FileWriter(logFilePath, true)) {
            writer.write(logEntry);
            logger.info("Trade logged: " + logEntry.trim());
        } catch (IOException e) {
            logger.error("Error writing to log file", e);
        }
    }
}