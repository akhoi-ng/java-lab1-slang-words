package lab01.slangdict._24127430.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * Icon tự vẽ: số thứ tự chức năng (1–10) trong một ô bo tròn màu nhấn.
 * Vẽ bằng Java2D nên không cần file ảnh, và màu luôn lấy theo theme hiện tại lúc vẽ
 * (đổi sáng/tối là đổi màu theo, không phải tạo lại icon).
 */
public class NumberBadgeIcon implements Icon {

    private static final int WIDTH = 24;
    private static final int HEIGHT = 20;

    private final String text;

    public NumberBadgeIcon(int number) {
        this.text = Integer.toString(number);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color background = ThemeManager.accentColor();
            if (c != null && !c.isEnabled()) {
                background = ThemeManager.color("Label.disabledForeground", Color.GRAY);
            }
            g2.setColor(background);
            g2.fillRoundRect(x, y, WIDTH, HEIGHT, 10, 10);

            // Cỡ chữ cố định theo font chuẩn (không theo component): ô có kích thước cố định,
            // nếu label chứa icon dùng chữ to thì số "10" sẽ tràn ra ngoài ô
            Font base = UIManager.getFont("Label.font");
            g2.setFont(base.deriveFont(Font.BOLD, base.getSize2D() - 1f));
            FontMetrics metrics = g2.getFontMetrics();
            int textX = x + (WIDTH - metrics.stringWidth(text)) / 2;
            int textY = y + (HEIGHT - metrics.getHeight()) / 2 + metrics.getAscent();
            g2.setColor(Color.WHITE);
            g2.drawString(text, textX, textY);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return WIDTH;
    }

    @Override
    public int getIconHeight() {
        return HEIGHT;
    }
}
