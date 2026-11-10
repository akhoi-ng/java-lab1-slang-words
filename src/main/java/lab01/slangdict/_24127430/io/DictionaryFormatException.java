package lab01.slangdict._24127430.io;

import java.io.IOException;

/**
 * File dictionary.dat không đọc được thành SlangDictionary: sai magic number, sai phiên bản định dạng,
 * bị cắt cụt, hoặc class đã thay đổi so với lúc ghi. Khi gặp lỗi này thì build lại từ slang.txt.
 */
public class DictionaryFormatException extends IOException {

    private static final long serialVersionUID = 1L;

    public DictionaryFormatException(String message) {
        super(message);
    }

    public DictionaryFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
