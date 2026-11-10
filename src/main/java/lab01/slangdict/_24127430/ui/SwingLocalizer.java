package lab01.slangdict._24127430.ui;

import javax.swing.JComponent;
import javax.swing.UIManager;

import lab01.slangdict._24127430.i18n.Messages;

/**
 * Dịch các nút có sẵn của Swing (OK / Cancel / Yes / No trong {@code JOptionPane}).
 * Swing không có bản dịch tiếng Việt, nếu không làm bước này thì dialog tiếng Việt vẫn hiện nút "Cancel".
 * Giá trị đặt bằng {@link UIManager#put} được giữ qua các lần đổi theme.
 */
public final class SwingLocalizer {

    private SwingLocalizer() {
    }

    /** Áp dụng ngay và tự áp dụng lại mỗi khi đổi ngôn ngữ. */
    public static void install() {
        apply();
        Messages.addListener(SwingLocalizer::apply);
    }

    private static void apply() {
        JComponent.setDefaultLocale(Messages.getLocale());
        UIManager.put("OptionPane.okButtonText", Messages.get("common.ok"));
        UIManager.put("OptionPane.cancelButtonText", Messages.get("common.cancel"));
        UIManager.put("OptionPane.yesButtonText", Messages.get("common.yes"));
        UIManager.put("OptionPane.noButtonText", Messages.get("common.no"));
    }
}
