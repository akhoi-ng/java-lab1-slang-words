package lab01.slangdict._24127430.ui;

import javax.swing.UIManager;

import com.formdev.flatlaf.FlatLightLaf;

/**
 * Cài Look &amp; Feel (FlatLaf light/dark). Nếu không nạp được FlatLaf thì dùng L&amp;F của hệ điều hành.
 */
public final class ThemeManager {

    private ThemeManager() {
    }

    public static void install() {
        try {
            if (FlatLightLaf.setup()) {
                return;
            }
        } catch (LinkageError | RuntimeException e) {
            // FlatLaf không có trên classpath hoặc lỗi khởi tạo → dùng L&F hệ thống
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Giữ L&F mặc định của Swing (Metal)
        }
    }
}
