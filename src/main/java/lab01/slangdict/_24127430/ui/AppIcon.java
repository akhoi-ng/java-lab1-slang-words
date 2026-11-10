package lab01.slangdict._24127430.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Icon của cửa sổ (thanh tiêu đề, taskbar): chữ "S" trắng trên ô vuông bo góc màu xanh.
 * Vẽ bằng code ở nhiều kích thước để Windows chọn bản rõ nhất, không cần file ảnh.
 */
public final class AppIcon {

    private static final Color BACKGROUND = new Color(0x2675BF);
    private static List<Image> images;

    private AppIcon() {
    }

    public static synchronized List<Image> images() {
        if (images == null) {
            List<Image> list = new ArrayList<>();
            for (int size : new int[] {16, 20, 24, 32, 40, 48, 64, 128}) {
                list.add(render(size));
            }
            images = List.copyOf(list);
        }
        return images;
    }

    /** Một ảnh icon kích thước {@code size}×{@code size} (dùng cho About, màn hình loading). */
    public static BufferedImage render(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(BACKGROUND);
            int arc = Math.max(4, size / 4);
            g.fillRoundRect(0, 0, size, size, arc, arc);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size * 0.72f)));
            FontMetrics metrics = g.getFontMetrics();
            String text = "S";
            int x = (size - metrics.stringWidth(text)) / 2;
            int y = (size - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(text, x, y);
        } finally {
            g.dispose();
        }
        return image;
    }
}
