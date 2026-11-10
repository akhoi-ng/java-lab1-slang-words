package lab01.slangdict._24127430.ui.dialogs;

import java.awt.Component;
import java.nio.file.Path;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import lab01.slangdict._24127430.i18n.Messages;
import lab01.slangdict._24127430.ui.AppIcon;

/**
 * Hộp thoại Help → About: thông tin sinh viên, môn học, thư viện đã dùng.
 */
public final class AboutDialog {

    public static final String STUDENT_NAME = "Nguyễn Anh Khôi";
    public static final String STUDENT_ID = "24127430";

    private AboutDialog() {
    }

    public static void show(Component parent, String version, Path dataDir) {
        String html = "<html><body style='width:340px'>"
                + "<b style='font-size:140%'>" + Messages.get("app.title") + "</b><br>"
                + Messages.format("about.version", version) + "<br><br>"
                + Messages.get("about.course") + "<br><br>"
                + row(Messages.get("about.student"), STUDENT_NAME)
                + row(Messages.get("about.studentId"), STUDENT_ID)
                + row(Messages.get("about.libraries"), Messages.get("about.libraries.value"))
                + row("Java", System.getProperty("java.version"))
                + row(Messages.get("about.dataDir"), escape(dataDir.toString()))
                + "</body></html>";
        JOptionPane.showMessageDialog(parent, new JLabel(html), Messages.get("about.title"),
                JOptionPane.PLAIN_MESSAGE, new ImageIcon(AppIcon.render(56)));
    }

    private static String row(String label, String value) {
        return "<b>" + label + ":</b> " + value + "<br>";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
