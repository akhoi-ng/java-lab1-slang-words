package lab01.slangdict._24127430.i18n;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Chuỗi giao diện song ngữ, lấy từ {@code i18n/messages_vi.properties} và {@code messages_en.properties}.
 * <p>
 * <b>Đổi ngôn ngữ khi đang chạy:</b> mỗi màn hình đăng ký một listener bằng {@link #addListener(Runnable)}
 * (thường là method {@code applyTexts()} của nó). {@link #setLocale(Locale)} đổi bundle rồi gọi lần lượt
 * mọi listener, nên chữ trên toàn bộ giao diện được cập nhật ngay mà không phải mở lại cửa sổ.
 * Đây là mẫu thiết kế <i>Observer</i>.
 * <p>
 * Mọi method dùng trên EDT (Event Dispatch Thread) của Swing; listener chạy trên luồng gọi {@code setLocale}.
 */
public final class Messages {

    public static final Locale VIETNAMESE = Locale.forLanguageTag("vi");
    public static final Locale ENGLISH = Locale.ENGLISH;
    /** Ngôn ngữ lần chạy đầu tiên (chưa có settings). */
    public static final Locale DEFAULT_LOCALE = VIETNAMESE;

    private static final List<Locale> SUPPORTED = List.of(VIETNAMESE, ENGLISH);

    private static final String BUNDLE = "i18n.messages";
    // NoFallbackControl: không tự rơi về ngôn ngữ của hệ điều hành khi thiếu bundle
    private static final ResourceBundle.Control CONTROL =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    // CopyOnWrite: listener có thể tự gỡ mình (removeListener) ngay trong lúc đang được gọi mà không lỗi
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private static volatile Locale locale = DEFAULT_LOCALE;
    private static volatile ResourceBundle bundle = loadBundle(DEFAULT_LOCALE);

    private Messages() {
    }

    /** Các ngôn ngữ có bản dịch, theo thứ tự hiện trong menu. */
    public static List<Locale> supportedLocales() {
        return SUPPORTED;
    }

    /**
     * Đưa một locale bất kỳ (ví dụ đọc từ file settings) về locale được hỗ trợ: so theo mã ngôn ngữ,
     * nên {@code vi-VN} → {@code vi}; null hoặc ngôn ngữ chưa có bản dịch → {@link #DEFAULT_LOCALE}.
     */
    public static Locale resolve(Locale requested) {
        if (requested != null) {
            for (Locale supported : SUPPORTED) {
                if (supported.getLanguage().equals(requested.getLanguage())) {
                    return supported;
                }
            }
        }
        return DEFAULT_LOCALE;
    }

    public static Locale getLocale() {
        return locale;
    }

    /** Đổi ngôn ngữ và báo cho mọi listener. Chọn lại đúng ngôn ngữ đang dùng thì không làm gì. */
    public static void setLocale(Locale requested) {
        Locale target = resolve(requested);
        if (target.equals(locale)) {
            return;
        }
        bundle = loadBundle(target);
        locale = target;
        for (Runnable listener : LISTENERS) {
            listener.run();
        }
    }

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    /**
     * Chuỗi theo key. Thiếu key thì trả về {@code !key!} thay vì ném exception: giao diện vẫn chạy
     * và lỗi dễ thấy bằng mắt (test {@code MessagesTest} đảm bảo hai file có cùng bộ key).
     */
    public static String get(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }

    // [THAM KHẢO] Đa ngôn ngữ bằng ResourceBundle (.properties) và MessageFormat, theo Oracle Java Tutorials,
    //   "Isolating Locale-Specific Data" https://docs.oracle.com/javase/tutorial/i18n/resbundle/index.html
    //   và "Dealing with Compound Messages" https://docs.oracle.com/javase/tutorial/i18n/format/messageFormat.html
    /**
     * Chuỗi có tham số kiểu {@code {0}}, {@code {1,number,integer}} (định dạng số theo ngôn ngữ:
     * 7.638 trong tiếng Việt, 7,638 trong tiếng Anh). Lưu ý: trong các chuỗi này dấu {@code '}
     * phải viết thành {@code ''} theo quy tắc của {@link MessageFormat}.
     */
    public static String format(String key, Object... args) {
        return new MessageFormat(get(key), locale).format(args);
    }

    /** Toàn bộ key của một ngôn ngữ (dùng cho test). */
    static Set<String> keys(Locale target) {
        return loadBundle(resolve(target)).keySet();
    }

    /** Chuỗi gốc của một key ở một ngôn ngữ cụ thể (dùng cho test). */
    static String raw(Locale target, String key) {
        return loadBundle(resolve(target)).getString(key);
    }

    private static ResourceBundle loadBundle(Locale target) {
        // Java 9+ đọc file .properties của ResourceBundle bằng UTF-8, nên viết thẳng tiếng Việt được
        return ResourceBundle.getBundle(BUNDLE, target, CONTROL);
    }
    // [HẾT THAM KHẢO]
}
