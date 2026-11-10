package lab01.slangdict._24127430.ui.panels;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;

import javax.swing.JPanel;
import javax.swing.Scrollable;

/**
 * Nội dung bên trong JScrollPane: luôn rộng đúng bằng khung nhìn (để chữ dài tự xuống dòng thay vì sinh thanh cuộn
 * ngang), cao theo nội dung (dài quá thì cuộn dọc). Dùng ở F8 (slang nhiều nghĩa) và F9/F10 (đáp án dài).
 */
class WidthTrackingPanel extends JPanel implements Scrollable {

    WidthTrackingPanel() {
        super(new BorderLayout());
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(16, visible.height - 32);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
