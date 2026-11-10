package lab01.slangdict._24127430.ui.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

import lab01.slangdict._24127430.ui.ThemeManager;

/**
 * Panel nền hơi khác màu nền màn hình, bo góc, viền mảnh, vạch màu nhấn bên trái: làm nổi nội dung chính của màn hình
 * (slang hôm nay ở F8, đề bài ở F9/F10). Màu lấy từ theme lúc vẽ, nên đổi sáng/tối vẫn đúng màu.
 */
class RoundedCard extends JPanel {

    private static final int ARC = 20;

    RoundedCard() {
        super(new BorderLayout());
        setOpaque(false); // tự vẽ nền bo góc, phần góc ngoài để trong suốt
        setBorder(BorderFactory.createEmptyBorder(22, 28, 22, 28));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
            Color base = ThemeManager.color("Panel.background", Color.WHITE);
            g2.setColor(ThemeManager.shade(base, 0.05f));
            g2.fill(shape);
            // Vạch màu nhấn bên trái, cắt theo góc bo của thẻ
            Graphics2D bar = (Graphics2D) g2.create();
            bar.clip(shape);
            bar.setColor(ThemeManager.accentColor());
            bar.fillRect(0, 0, 6, getHeight());
            bar.dispose();
            g2.setColor(ThemeManager.color("Component.borderColor", Color.GRAY));
            g2.draw(shape);
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
