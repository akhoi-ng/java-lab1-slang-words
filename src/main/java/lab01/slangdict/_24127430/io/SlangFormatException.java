package lab01.slangdict._24127430.io;

/**
 * Một dòng trong file slang sai định dạng.
 */
public class SlangFormatException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int lineNumber;

    public SlangFormatException(int lineNumber, String message) {
        super("Line " + lineNumber + ": " + message);
        this.lineNumber = lineNumber;
    }

    /** Số thứ tự dòng bị lỗi, bắt đầu từ 1. */
    public int getLineNumber() {
        return lineNumber;
    }
}
