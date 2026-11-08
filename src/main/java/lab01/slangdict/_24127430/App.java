package lab01.slangdict._24127430;

import javax.swing.SwingUtilities;

import lab01.slangdict._24127430.ui.MainFrame;
import lab01.slangdict._24127430.ui.ThemeManager;

/**
 * Điểm khởi động ứng dụng Slang Dictionary.
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        ThemeManager.install();
        // Mọi thao tác Swing phải chạy trên Event Dispatch Thread
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
