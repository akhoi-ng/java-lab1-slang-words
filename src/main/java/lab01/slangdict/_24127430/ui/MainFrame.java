package lab01.slangdict._24127430.ui;

import java.awt.BorderLayout;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import lab01.slangdict._24127430.i18n.Messages;

/**
 * Cửa sổ chính: JMenuBar + Sidebar bên trái + CardLayout chứa các màn hình chức năng.
 */
public class MainFrame extends JFrame {

    public MainFrame() {
        super(Messages.get("app.title"));
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1000, 680);
        setLocationRelativeTo(null);

        // Placeholder cho đến khi có Sidebar và các panel
        add(new JLabel(Messages.get("app.placeholder"), SwingConstants.CENTER), BorderLayout.CENTER);
    }
}
