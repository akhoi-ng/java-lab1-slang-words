package lab01.slangdict._24127430.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;

import lab01.slangdict._24127430.i18n.Messages;

/**
 * Màn hình "Đang tải…" hiện trong lúc luồng nền đọc .dat / build index.
 * <p>
 * Chỉ hiện nếu việc tải kéo dài hơn {@link #SHOW_DELAY_MS}: với file gốc 7,6k slang việc tải chỉ mất
 * khoảng 0,1 s, nếu hiện ngay thì cửa sổ chỉ nháy lên rồi tắt, rất khó chịu. Với 100k slang
 * (tải 1–3 s) thì màn hình này mới thực sự xuất hiện.
 */
public class LoadingWindow extends JWindow {

    static final int SHOW_DELAY_MS = 300;

    private final Timer showTimer;

    public LoadingWindow() {
        setIconImages(AppIcon.images());
        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.color("Component.borderColor", Color.GRAY)),
                BorderFactory.createEmptyBorder(24, 32, 24, 32)));

        JLabel title = new JLabel(Messages.get("app.title"), new ImageIcon(AppIcon.render(40)), SwingConstants.LEADING);
        title.setIconTextGap(14);
        Font font = UIManager.getFont("Label.font");
        title.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 8f));

        JLabel message = new JLabel(Messages.get("loading.message"));
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true); // không biết trước tổng thời gian → thanh chạy liên tục

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setOpaque(false);
        bottom.add(message, BorderLayout.NORTH);
        bottom.add(progress, BorderLayout.SOUTH);

        content.add(title, BorderLayout.NORTH);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);
        pack();
        setSize(Math.max(getWidth(), 380), getHeight());
        setLocationRelativeTo(null);

        showTimer = new Timer(SHOW_DELAY_MS, e -> setVisible(true));
        showTimer.setRepeats(false);
    }

    /** Hẹn giờ hiện cửa sổ; nếu {@link #close()} được gọi trước đó thì cửa sổ không bao giờ hiện. */
    public void showDelayed() {
        showTimer.start();
    }

    public void close() {
        showTimer.stop();
        dispose();
    }
}
