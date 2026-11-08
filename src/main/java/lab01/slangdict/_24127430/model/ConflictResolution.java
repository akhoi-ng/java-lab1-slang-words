package lab01.slangdict._24127430.model;

/**
 * Cách xử lý khi add/rename trùng tên slang đã có.
 */
public enum ConflictResolution {
    /** Thay thế entry cũ. */
    OVERWRITE,
    /** Thêm nghĩa mới vào entry đã có. */
    ADD_MEANING,
    /** Tạo entry riêng cùng tên. */
    NEW_ENTRY
}
