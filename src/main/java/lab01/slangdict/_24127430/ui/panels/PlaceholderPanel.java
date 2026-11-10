package lab01.slangdict._24127430.ui.panels;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import lab01.slangdict._24127430.i18n.Messages;
import lab01.slangdict._24127430.ui.Feature;
import lab01.slangdict._24127430.ui.NumberBadgeIcon;
import lab01.slangdict._24127430.ui.ThemeManager;

/**
 * Màn hình tạm cho các chức năng chưa làm: hiện tên, mô tả và ghi chú "đang xây dựng".
 * Các bước 8–12 sẽ thay từng placeholder bằng màn hình thật qua {@code MainFrame.setScreen}.
 */
public class PlaceholderPanel extends JPanel {

    private final List<Feature> features;
    private final JLabel title = new JLabel();
    private final JLabel description = new JLabel();
    private final JLabel note = new JLabel();

    /** @param features các chức năng dùng chung màn hình này (ví dụ F1 và F2 cùng là màn hình Search) */
    public PlaceholderPanel(List<Feature> features) {
        super(new GridBagLayout());
        this.features = List.copyOf(features);

        title.setIcon(new NumberBadgeIcon(features.get(0).number()));
        title.setIconTextGap(12);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        description.setHorizontalAlignment(SwingConstants.CENTER);
        note.setHorizontalAlignment(SwingConstants.CENTER);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.insets = new Insets(6, 24, 6, 24);
        add(title, gbc);
        add(description, gbc);
        gbc.insets = new Insets(18, 24, 6, 24);
        add(note, gbc);

        applyStyles();
        applyTexts();
        Messages.addListener(this::applyTexts);
    }

    private void applyTexts() {
        StringBuilder titles = new StringBuilder();
        StringBuilder descriptions = new StringBuilder("<html><div style='text-align:center'>");
        for (Feature feature : features) {
            if (titles.length() > 0) {
                titles.append("  ·  ");
                descriptions.append("<br>");
            }
            titles.append(Messages.get(feature.titleKey()));
            descriptions.append(Messages.get(feature.descriptionKey()));
        }
        title.setText(titles.toString());
        description.setText(descriptions.append("</div></html>").toString());
        note.setText(Messages.get("placeholder.note"));
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (title != null) {
            applyStyles();
        }
    }

    private void applyStyles() {
        Font font = UIManager.getFont("Label.font");
        title.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 9f));
        description.setFont(font.deriveFont(font.getSize2D() + 1f));
        note.setFont(font.deriveFont(Font.ITALIC));
        note.setForeground(ThemeManager.color("Label.disabledForeground", Color.GRAY));
    }
}
