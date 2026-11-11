package lab01.slangdict._24127430.ui;

import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Hiện dữ liệu người dùng (tên slang, nghĩa, từ khóa đã tìm) đúng nguyên văn.
 *
 * <p>Swing vẽ mọi chuỗi bắt đầu bằng {@code <html>} trên {@code JLabel}, {@code JButton}, ô của {@code JTable} và
 * tooltip như HTML. Không có lớp này, một slang tên {@code <html><font size=7>hi} sẽ hiện thành chữ "hi" thật to
 * thay vì đúng tên đã nhập. Client property {@code "html.disable"} của Swing tắt việc đó cho từng component.
 */
public final class PlainText {

    /** Client property Swing đọc trong {@code BasicHTML.updateRenderer}. */
    private static final String HTML_DISABLE = "html.disable";

    private PlainText() {
    }

    /**
     * Tắt HTML cho {@code component} rồi trả lại chính nó. Phải gọi <b>trước</b> {@code setText}: Swing chỉ xét
     * property này khi chữ (hoặc giao diện) thay đổi.
     */
    public static <T extends JComponent> T of(T component) {
        component.putClientProperty(HTML_DISABLE, Boolean.TRUE);
        return component;
    }

    /** Renderer ô bảng hiện chữ nguyên văn, dùng thay renderer mặc định của {@code JTable}. */
    public static DefaultTableCellRenderer cellRenderer() {
        return of(new DefaultTableCellRenderer());
    }

    /**
     * Chữ cho {@code setToolTipText} để tooltip hiện đúng {@code text}. Tooltip là một {@code JToolTip} riêng, không
     * đọc "html.disable" của component sinh ra nó, nên chuỗi trông như HTML được bọc lại thành HTML đã thoát ký tự.
     */
    public static String tooltip(String text) {
        return text != null && BasicHTML.isHTMLString(text) ? "<html>" + escape(text) + "</html>" : text;
    }

    /** Thoát ký tự đặc biệt của HTML. */
    public static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
