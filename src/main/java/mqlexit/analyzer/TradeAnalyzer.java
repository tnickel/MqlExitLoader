package analyzer;

// TradeAnalyzer.java


import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import logging.LoggerManagerE;

public class TradeAnalyzer {
    private final String logFilePath;
    private final String providerName;
    private final String signalFilePath;
    
    private static final Pattern FIRST_TBODY_PATTERN = Pattern.compile(
        "<tbody>(.*?)</tbody>",
        Pattern.DOTALL
    );
    
    private static final Pattern TRADE_PATTERN = Pattern.compile(
        "<td data-label=\"Type\">(Buy|Sell|Buy Stop|Sell Stop)</td>",
        Pattern.DOTALL
    );

    public TradeAnalyzer(String logFilePath, String providerName, String signalDir) {
        this.logFilePath = logFilePath;
        this.providerName = providerName;
        this.signalFilePath = signalDir + File.separator + "signal.txt";
        createSignalDirectory(signalDir);
    }

    private void createSignalDirectory(String signalDir) {
        File dir = new File(signalDir);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                LoggerManagerE.info("Created signals directory: " + dir.getAbsolutePath());
            } else {
                LoggerManagerE.error("Failed to create signals directory");
            }
        }
    }

    public void analyzeHtmlFile(String htmlFilePath) {
        try {
            LoggerManagerE.info("Starting analysis of HTML file: " + htmlFilePath);
            String content = readFile(htmlFilePath);
            List<Map<String, String>> allTradeInfo = new ArrayList<>();
            
            Matcher tbodyMatcher = FIRST_TBODY_PATTERN.matcher(content);
            if (!tbodyMatcher.find()) {
                LoggerManagerE.info("No tbody found in HTML content");
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
                LoggerManagerE.info("Found " + allTradeInfo.size() + " trades to process");
                writeSignalFile(allTradeInfo);
            } else {
                LoggerManagerE.info("No trades found in HTML content");
            }
        } catch (IOException e) {
            LoggerManagerE.error("Error analyzing HTML file: " + e.getMessage());
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
            File signalFile = new File(signalFilePath);
            if (signalFile.exists()) {
                signalFile.delete();
                LoggerManagerE.info("Deleted existing signal file");
            }
            
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
                LoggerManagerE.info("Signal file written: " + signalFilePath);
            }
        } catch (IOException e) {
            LoggerManagerE.error("Error writing signal file: " + e.getMessage());
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
            LoggerManagerE.info("Trade logged: " + logEntry.trim());
        } catch (IOException e) {
            LoggerManagerE.error("Error writing to log file: " + e.getMessage());
        }
    }
}