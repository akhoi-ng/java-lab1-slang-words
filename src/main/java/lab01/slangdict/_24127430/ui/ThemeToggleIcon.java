package lab01.slangdict._24127430.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

import javax.swing.Icon;

import lab01.slangdict._24127430.model.Theme;

/**
 * Icon của nút đổi theme, tự vẽ bằng Java2D: đang ở theme tối thì vẽ <b>mặt trời</b> (bấm để sang sáng),
 * đang ở theme sáng thì vẽ <b>mặt trăng</b> (bấm để sang tối). Hình được chọn lúc vẽ theo
 * {@link ThemeManager#current()}, nên đổi theme là icon tự đổi, không phải gán lại icon.
 */
public class ThemeToggleIcon implements Icon {

    private static final int SIZE = 18;

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            Color color = c != null ? c.getForeground() : Color.GRAY;
            g2.setColor(color);
            if (ThemeManager.current() == Theme.DARK) {
                paintSun(g2);
            } else {
                paintMoon(g2);
            }
        } finally {
            g2.dispose();
        }
    }

    /** Hình tròn ở giữa + 8 tia sáng. */
    private static void paintSun(Graphics2D g2) {
        double center = SIZE / 2.0;
        g2.fill(new Ellipse2D.Double(center - 4, center - 4, 8, 8));
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI / 4 * i;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            g2.draw(new Line2D.Double(center + cos * 6.5, center + sin * 6.5, center + cos * 8.5, center + sin * 8.5));
        }
    }

    /** Mặt trăng khuyết = hình tròn trừ đi một hình tròn lệch sang phải-trên (phép trừ {@link Area}). */
    private static void paintMoon(Graphics2D g2) {
        Area moon = new Area(new Ellipse2D.Double(2, 2, 14, 14));
        moon.subtract(new Area(new Ellipse2D.Double(7, -1, 13, 13)));
        g2.fill(moon);
    }

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }
}
