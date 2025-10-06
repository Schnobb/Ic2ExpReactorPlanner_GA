package Ic2ExpReactorPlanner;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A simple static utility class for logging messages to the console and optionally to a file.
 * <p>
 * This logger supports different log levels, formatted messages (like String.format),
 * and configurable file output.
 *
 * <p><b>Example Usage:</b>
 * <pre>
 * // Configure logging to a file in a specific directory
 * Logger.setLogFileFromDirectory("MyCoolApp", Paths.get("logs"));
 *
 * // Log messages
 * Logger.log("Application starting up...");
 * Logger.log(Logger.LogLevel.WARNING, "Could not find config file '%s'", "settings.conf");
 * </pre>
 */
public class Logger {
    private static DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static DateTimeFormatter LOG_FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd - HH-mm-ss");
    private static String logFileName;

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private Logger() {}

    /**
     * Logs a message with the default {@link LogLevel#INFO} level.
     *
     * @param message The format string for the log message (e.g., "Found %d items.").
     * @param args    The arguments to be formatted into the message.
     */
    public static void log(String message, Object... args) {
        log(LogLevel.INFO, message, args);
    }

    /**
     * The main logging method.
     * <p>
     * Formats a message with a timestamp and log level, prints it to the standard console,
     * and appends it to a file if one has been configured via {@link #setLogFileFromPath(String)}
     * or {@link #setLogFileFromDirectory(String, Path)}.
     *
     * @param level   The severity level of the message (e.g., {@code LogLevel.INFO}).
     * @param message The format string for the log message.
     * @param args    The arguments to be formatted into the message.
     */
    public static void log(LogLevel level, String message, Object... args) {
        LocalDateTime now = LocalDateTime.now();

        String formattedDate = now.format(DATE_FORMATTER);

        String formattedMessage = String.format(message, args);
        String logEntry = String.format("[%s]%s %s%n", formattedDate, level.getDisplayString(), formattedMessage);
        System.out.print(logEntry);

        if (logFileName != null && !logFileName.isEmpty()) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFileName, true))) {
                writer.write(logEntry);
            } catch (IOException e) {
                System.err.println("Error: Could not write to log file '" + logFileName + "'.");
                e.printStackTrace();
            }
        }
    }

    /**
     * Sets the exact file path for logging. The caller has full control over the filename and location.
     *
     * @param fullPath The full, absolute or relative path to the log file (e.g., "logs/custom.log").
     * @throws InvalidPathException if the path string contains invalid characters or is malformed.
     */
    public static void setLogFileFromPath(String fullPath) throws InvalidPathException {
        Paths.get(fullPath); // Validates syntax
        logFileName = fullPath;
    }

    /**
     * Configures logging to a file within a specified directory using a default, date-based naming convention.
     * <p>
     * This method will create the directory if it does not exist. The generated filename will be in the
     * format {@code <appName>-yyyyMMdd.log}.
     *
     * @param appName   The name of the application, used as the prefix for the log file.
     * @param directory The target directory where the log file will be created (e.g., {@code Paths.get("logs/")}).
     * @throws IOException if the directory cannot be created.
     */
    public static void setLogFileFromDirectory(String appName, Path directory) throws IOException {
        Files.createDirectories(directory);

        String date = LocalDateTime.now().format(LOG_FILE_DATE_FORMATTER);
        String defaultFileName = String.format("%s - %s.log", appName, date);

        logFileName = directory.resolve(defaultFileName).toString();
    }

    /**
     * Represents the severity levels for log messages.
     */
    public enum LogLevel {
        /** For general informational messages about application progress. */
        INFO("[INFO]"),
        /** For detailed, fine-grained messages useful for debugging. */
        DEBUG("[DEBUG]"),
        /** For potentially harmful situations or warnings that do not prevent execution. */
        WARNING("[WARNING]"),
        /** For error events that might still allow the application to continue running. */
        ERROR("[ERROR]");

        private final String displayString;

        LogLevel(String displayString) {
            this.displayString = displayString;
        }

        /**
         * Gets the formatted string representation of the log level (e.g., "[INFO]").
         *
         * @return The display-ready string for the log level.
         */
        public String getDisplayString() {
            return this.displayString;
        }
    }
}
