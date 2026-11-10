package lab01.slangdict._24127430.ui;

import java.awt.Color;
import java.awt.Insets;

import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import lab01.slangdict._24127430.model.Theme;

/**
 * Cài và đổi Look &amp; Feel (FlatLaf sáng/tối).
 * <p>
 * Nếu không nạp được FlatLaf (thiếu thư viện, lỗi khởi tạo) thì dùng L&amp;F của hệ điều hành; khi đó
 * không đổi theme được nữa và menu View bị vô hiệu hóa. App không bao giờ crash chỉ vì giao diện.
 * <p>
 * Đổi theme khi đang chạy: {@link FlatLaf#updateUI()} gọi {@code updateUI()} trên mọi component của mọi
 * cửa sổ đang mở, nên component tự vẽ lại bằng màu mới. Component nào tự tính màu (ví dụ {@link Sidebar})
 * thì override {@code updateUI()} để tính lại.
 */
public final class ThemeManager {

    private static Theme current = Theme.LIGHT;
    private static boolean flatLafActive;

    private ThemeManager() {
    }

    /** Gọi một lần khi khởi động, trước khi tạo bất kỳ component nào. */
    public static void install(Theme theme) {
        applyCustomDefaults();
        try {
            flatLafActive = setup(theme);
        } catch (LinkageError | RuntimeException e) {
            flatLafActive = false; // FlatLaf không có trên classpath hoặc lỗi khởi tạo
        }
        if (flatLafActive) {
            current = theme;
            return;
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Giữ L&F mặc định của Swing (Metal)
        }
    }

    /** Đổi theme khi đang chạy (trên EDT). */
    public static void apply(Theme theme) {
        if (!flatLafActive || theme == current) {
            return;
        }
        if (setup(theme)) {
            current = theme;
            FlatLaf.updateUI();
        }
    }

    public static Theme current() {
        return current;
    }

    /** false khi đang dùng L&amp;F hệ điều hành (FlatLaf lỗi), lúc đó không đổi theme được. */
    public static boolean isThemeSwitchingSupported() {
        return flatLafActive;
    }

    /**
     * Màu theo key của UIManager, thiếu thì dùng {@code fallback} (L&amp;F khác FlatLaf có thể không có key đó).
     * <p>
     * Trả về bản sao kiểu {@link Color} thường, <b>không</b> phải {@code ColorUIResource}: khi đổi theme, Swing
     * coi mọi màu kiểu UIResource là "màu mặc định của theme cũ" và ghi đè bằng màu mặc định mới, nên màu
     * tự đặt (ví dụ chữ xám của tiêu đề nhóm trên Sidebar) sẽ bị mất.
     */
    public static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return new Color((color != null ? color : fallback).getRGB(), true);
    }

    /** Màu nhấn (accent) của theme hiện tại. */
    public static Color accentColor() {
        return color("Component.accentColor", new Color(0x2675BF));
    }

    /** Đẩy màu về phía tương phản một chút: theme sáng thì tối đi, theme tối thì sáng lên. */
    public static Color shade(Color base, float amount) {
        float[] hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
        float brightness = current == Theme.DARK ? hsb[2] + amount : hsb[2] - amount;
        return Color.getHSBColor(hsb[0], hsb[1], Math.max(0f, Math.min(1f, brightness)));
    }

    private static boolean setup(Theme theme) {
        return theme == Theme.DARK ? FlatDarkLaf.setup() : FlatLightLaf.setup();
    }

    /**
     * Tinh chỉnh chung. Đặt bằng {@link UIManager#put} nên được giữ lại qua các lần đổi theme
     * (thuộc lớp "developer defaults", ưu tiên hơn giá trị của L&amp;F).
     */
    private static void applyCustomDefaults() {
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("TitlePane.unifiedBackground", true);
    }
}
