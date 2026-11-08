package lab01.slangdict._24127430.i18n;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Chuỗi giao diện song ngữ (i18n/messages_en, messages_vi).
 * Sau này: thêm setLocale + listener để đổi ngôn ngữ ngay khi đang chạy.
 */
public final class Messages {

    public static final Locale ENGLISH = Locale.ENGLISH;
    public static final Locale VIETNAMESE = Locale.forLanguageTag("vi");

    private static final String BUNDLE = "i18n.messages";
    // NoFallbackControl: không tự rơi về ngôn ngữ của hệ điều hành khi thiếu bundle
    private static final ResourceBundle.Control CONTROL =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private static ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, ENGLISH, CONTROL);

    private Messages() {
    }

    public static String get(String key) {
        return bundle.getString(key);
    }
}
