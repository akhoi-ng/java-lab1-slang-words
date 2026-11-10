package lab01.slangdict._24127430.ui;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Locale;

import javax.swing.KeyStroke;

/**
 * 10 chức năng của đề bài, đúng thứ tự và đánh số như mục III (dùng chung cho Sidebar và menu "Chức năng",
 * nên hai nơi không bao giờ lệch nhau).
 * <p>
 * Chức năng có {@link Card} là <b>màn hình</b> (CardLayout chuyển sang card đó). Chức năng không có card
 * ({@link #card()} == null) là <b>hành động</b>: mở dialog (Add, Reset) hoặc thao tác trên slang đang chọn
 * ở màn hình Search (Edit, Delete). F1 và F2 dùng chung màn hình Search, chỉ khác target tìm kiếm.
 */
public enum Feature {
    SEARCH_SLANG(1, Section.SEARCH, Card.SEARCH),
    SEARCH_DEFINITION(2, Section.SEARCH, Card.SEARCH),
    HISTORY(3, Section.SEARCH, Card.HISTORY),
    ADD(4, Section.MANAGE, null),
    EDIT(5, Section.MANAGE, null),
    DELETE(6, Section.MANAGE, null),
    RESET(7, Section.MANAGE, null),
    RANDOM(8, Section.EXPLORE, Card.RANDOM),
    QUIZ_SLANG(9, Section.EXPLORE, Card.QUIZ_SLANG),
    QUIZ_DEFINITION(10, Section.EXPLORE, Card.QUIZ_DEFINITION);

    /** Nhóm chức năng, mỗi nhóm có một tiêu đề trên Sidebar. */
    public enum Section {
        SEARCH, MANAGE, EXPLORE;

        public String titleKey() {
            return "section." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Các màn hình trong CardLayout. */
    public enum Card {
        SEARCH, HISTORY, RANDOM, QUIZ_SLANG, QUIZ_DEFINITION
    }

    private final int number;
    private final Section section;
    private final Card card;

    Feature(int number, Section section, Card card) {
        this.number = number;
        this.section = section;
        this.card = card;
    }

    /** Số thứ tự trong đề (1–10). */
    public int number() {
        return number;
    }

    public Section section() {
        return section;
    }

    /** Màn hình của chức năng, hoặc null nếu là hành động (dialog). */
    public Card card() {
        return card;
    }

    public boolean isScreen() {
        return card != null;
    }

    /** Key tên chức năng, ví dụ {@code feature.search_slang.title}. */
    public String titleKey() {
        return "feature." + name().toLowerCase(Locale.ROOT) + ".title";
    }

    /** Key mô tả ngắn của chức năng. */
    public String descriptionKey() {
        return "feature." + name().toLowerCase(Locale.ROOT) + ".desc";
    }

    /** Phím tắt Ctrl+1 … Ctrl+9, chức năng 10 là Ctrl+0. */
    public KeyStroke accelerator() {
        int key = number == 10 ? KeyEvent.VK_0 : KeyEvent.VK_0 + number;
        return KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK);
    }
}
