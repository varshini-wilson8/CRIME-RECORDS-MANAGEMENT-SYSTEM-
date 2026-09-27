package util;

/**
 * Custom checked exception thrown whenever user input fails validation
 * (blank text, malformed numbers, out-of-range values, etc.).
 * Kept as a checked exception so every call site is forced to handle it,
 * which is what lets Menu.java guarantee that bad input never crashes
 * the program.
 */
public class InvalidInputException extends Exception {

    public InvalidInputException(String message) {
        super(message);
    }

    public InvalidInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
