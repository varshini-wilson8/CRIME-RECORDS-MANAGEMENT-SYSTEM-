package util;

/**
 * Static utility class for validating raw user input coming from the
 * console before it is handed to model/service classes. Every method
 * throws InvalidInputException on bad input rather than returning a
 * boolean, so Menu.java can catch one exception type around each action.
 */
public class Validation {

    // Prevent instantiation - this is a pure static utility class.
    private Validation() {
    }

    /**
     * Validates that a piece of text is non-null, non-blank, and does
     * not exceed a reasonable length.
     */
    public static String validateText(String input, String fieldName) throws InvalidInputException {
        if (input == null || input.trim().isEmpty()) {
            throw new InvalidInputException(fieldName + " cannot be blank.");
        }
        String trimmed = input.trim();
        if (trimmed.length() > 500) {
            throw new InvalidInputException(fieldName + " is too long (max 500 characters).");
        }
        return trimmed;
    }

    /**
     * Validates and parses a String into an int, throwing
     * InvalidInputException if it is not a valid integer.
     * POLYMORPHISM: overloaded with validateNumber(String, String, int, int).
     */
    public static int validateNumber(String input, String fieldName) throws InvalidInputException {
        if (input == null || input.trim().isEmpty()) {
            throw new InvalidInputException(fieldName + " cannot be blank.");
        }
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            throw new InvalidInputException(fieldName + " must be a valid whole number.", e);
        }
    }

    /**
     * Validates and parses a String into an int within an inclusive range.
     * POLYMORPHISM: overloaded with validateNumber(String, String) above.
     */
    public static int validateNumber(String input, String fieldName, int min, int max)
            throws InvalidInputException {
        int value = validateNumber(input, fieldName);
        if (value < min || value > max) {
            throw new InvalidInputException(
                    fieldName + " must be between " + min + " and " + max + ".");
        }
        return value;
    }

    /**
     * Validates that a menu choice string maps to a valid integer option
     * between 1 and maxOption inclusive.
     */
    public static int validateMenuChoice(String input, int maxOption) throws InvalidInputException {
        return validateNumber(input, "Menu choice", 1, maxOption);
    }
}
