package lab01.slangdict._24127430.model;

/**
 * Giao diện sáng / tối. Nằm ở {@code model} (không phải {@code ui}) vì {@code io.SettingsStore}
 * cũng cần lưu nó, mà tầng io không được phụ thuộc ngược lên tầng ui.
 */
public enum Theme {
    LIGHT, DARK
}
