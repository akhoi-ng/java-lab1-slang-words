package lab01.slangdict._24127430.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import lab01.slangdict._24127430.i18n.Messages;

/**
 * Thanh chức năng bên trái: 10 nút đánh số đúng như đề, chia 3 nhóm (Tìm kiếm / Quản lý / Khám phá).
 * <p>
 * Chức năng là màn hình thì dùng {@link JToggleButton} trong một {@link ButtonGroup} (luôn có đúng một nút
 * được chọn, có vạch màu bên trái); chức năng là hành động (Add, Edit, Delete, Reset) dùng {@link JButton}
 * thường, vì bấm xong chỉ mở dialog chứ không đổi màn hình.
 * Sidebar không tự chuyển màn hình: nó chỉ báo "người dùng chọn chức năng X" qua callback cho MainFrame.
 */
public class Sidebar extends JPanel {

    private final Map<Feature, AbstractButton> buttons = new EnumMap<>(Feature.class);
    private final Map<Feature.Section, JLabel> headers = new EnumMap<>(Feature.Section.class);

    public Sidebar(Consumer<Feature> onSelect) {
        super(new GridBagLayout());
        ButtonGroup group = new ButtonGroup();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        Feature.Section currentSection = null;
        for (Feature feature : Feature.values()) {
            if (feature.section() != currentSection) {
                currentSection = feature.section();
                JLabel header = new JLabel();
                header.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
                headers.put(currentSection, header);
                gbc.insets = new Insets(gbc.gridy == 0 ? 0 : 14, 0, 4, 0);
                add(header, gbc);
                gbc.gridy++;
            }
            AbstractButton button = feature.isScreen() ? new NavToggleButton() : new JButton();
            if (feature.isScreen()) {
                group.add(button);
            }
            button.putClientProperty("JButton.buttonType", "toolBarButton");
            button.setIcon(new NumberBadgeIcon(feature.number()));
            button.setHorizontalAlignment(SwingConstants.LEADING);
            button.setIconTextGap(10);
            button.setMargin(new Insets(7, 10, 7, 14));
            button.addActionListener(e -> onSelect.accept(feature));
            buttons.put(feature, button);
            gbc.insets = new Insets(1, 0, 1, 0);
            add(button, gbc);
            gbc.gridy++;
        }
        // Ô trống cuối cùng "ăn" phần chiều cao còn lại để các nút dồn lên trên
        gbc.weighty = 1;
        add(new JLabel(), gbc);

        applyColors();
        applyTexts();
        Messages.addListener(this::applyTexts);
    }

    /** Đánh dấu chức năng đang hiển thị (chỉ có tác dụng với chức năng là màn hình). */
    public void select(Feature feature) {
        AbstractButton button = buttons.get(feature);
        if (feature.isScreen() && button != null) {
            button.setSelected(true);
        }
    }

    /** Dạng "Ctrl+1" để ghi trong tooltip. */
    public static String acceleratorText(KeyStroke stroke) {
        return InputEvent.getModifiersExText(stroke.getModifiers()) + "+" + KeyEvent.getKeyText(stroke.getKeyCode());
    }

    private void applyTexts() {
        headers.forEach((section, label) -> label.setText(Messages.get(section.titleKey())));
        buttons.forEach((feature, button) -> {
            button.setText(Messages.get(feature.titleKey()));
            button.setToolTipText(Messages.get(feature.descriptionKey())
                    + " (" + acceleratorText(feature.accelerator()) + ")");
        });
        revalidate();
        repaint();
    }

    /** Đổi theme thì FlatLaf gọi updateUI() trên mọi component → tính lại màu tự đặt. */
    @Override
    public void updateUI() {
        super.updateUI();
        if (headers != null) { // updateUI() được gọi lần đầu từ constructor của JPanel, khi field chưa có
            applyColors();
        }
    }

    private void applyColors() {
        Color panel = ThemeManager.color("Panel.background", getBackground());
        setBackground(ThemeManager.shade(panel, 0.03f));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, ThemeManager.color("Component.borderColor", Color.GRAY)),
                BorderFactory.createEmptyBorder(14, 10, 14, 10)));
        Font font = UIManager.getFont("Label.font");
        Color muted = ThemeManager.color("Label.disabledForeground", Color.GRAY);
        for (JLabel header : headers.values()) {
            header.setFont(font.deriveFont(Font.BOLD, font.getSize2D() - 1f));
            header.setForeground(muted);
        }
    }

    /** Nút màn hình: khi được chọn thì vẽ thêm vạch màu nhấn ở mép trái. */
    private static final class NavToggleButton extends JToggleButton {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (isSelected()) {
                g.setColor(ThemeManager.accentColor());
                g.fillRoundRect(0, 6, 4, getHeight() - 12, 4, 4);
            }
        }
    }
}
