

// LoggerManagerE.java
package logging;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class LoggerManagerE {
    private static FileWriter logWriter;

    public static void initializeLogger(String logConfigPath) {
        try {
            // Bestimme das Root-Verzeichnis (C:\tmp\mql5)
            File rootDir = new File(logConfigPath).getParentFile().getParentFile();
            File logDir = new File(rootDir, "logs");
            logDir.mkdirs();

            // Erstelle den Log-File-Pfad
            File logFile = new File(logDir, "application.log");
            
            // Öffne den FileWriter
            logWriter = new FileWriter(logFile, true);
            
            // Initialer Log-Eintrag
            writeLog("Logger initialized");
            writeLog("Log file path: " + logFile.getAbsolutePath());

        } catch (Exception e) {
            System.err.println("Error initializing logger: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void writeLog(String message) {
        try {
            if (logWriter != null) {
                String timestamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
                String logEntry = String.format("%s - %s%n", timestamp, message);
                logWriter.write(logEntry);
                logWriter.flush();
                System.out.println(logEntry.trim());  // Auch auf Konsole ausgeben
            }
        } catch (IOException e) {
            System.err.println("Error writing to log: " + e.getMessage());
        }
    }

    public static void info(String message) {
        writeLog("[INFO] " + message);
    }

    public static void error(String message) {
        writeLog("[ERROR] " + message);
    }

    public static void debug(String message) {
        writeLog("[DEBUG] " + message);
    }

    public static void warn(String message) {
        writeLog("[WARN] " + message);
    }
    
    public static void shutdown() {
        try {
            if (logWriter != null) {
                logWriter.flush();
                logWriter.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing log writer: " + e.getMessage());
        }
    }
}
