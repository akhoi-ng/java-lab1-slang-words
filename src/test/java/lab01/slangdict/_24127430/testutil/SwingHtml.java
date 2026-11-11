package lab01.slangdict._24127430.testutil;

import java.awt.Component;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.plaf.basic.BasicHTML;

/** Kiểm tra Swing có đang vẽ chữ của một component như HTML hay không. */
public final class SwingHtml {

    private SwingHtml() {
    }

    /** {@code true} nếu Swing đã gắn bộ vẽ HTML cho {@code component} (chữ bắt đầu bằng {@code <html>} và chưa tắt HTML). */
    public static boolean rendersHtml(Component component) {
        return component instanceof JComponent c && c.getClientProperty(BasicHTML.propertyKey) != null;
    }

    /** Như {@link #rendersHtml}, cho ô ({@code row}, {@code column}) của {@code table} đúng như lúc bảng vẽ ô đó. */
    public static boolean cellRendersHtml(JTable table, int row, int column) {
        return rendersHtml(table.prepareRenderer(table.getCellRenderer(row, column), row, column));
    }
}
