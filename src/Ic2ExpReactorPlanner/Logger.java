package Ic2ExpReactorPlanner;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private static DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public static void log(String message, Object... args) {
        log(LogLevel.INFO, message, args);
    }

    public static void log(LogLevel level, String message, Object... args) {
        LocalDateTime now = LocalDateTime.now();

        String formattedDate = now.format(DATE_FORMATTER);

        String formattedMessage = String.format(message, args);
        System.out.printf("[%s]%s %s%n", formattedDate, level.getDisplayString(), formattedMessage);
    }

    public enum LogLevel {
        INFO("[INFO]"),
        DEBUG("[DEBUG]"),
        WARNING("[WARNING]"),
        ERROR("[ERROR]");

        private final String displayString;

        // Enum constructor
        LogLevel(String displayString) {
            this.displayString = displayString;
        }

        // Getter for the display string
        public String getDisplayString() {
            return this.displayString;
        }
    }
}
