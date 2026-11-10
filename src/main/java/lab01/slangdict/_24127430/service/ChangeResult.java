package lab01.slangdict._24127430.service;

import java.util.List;
import java.util.Objects;

import lab01.slangdict._24127430.model.ConflictResolution;
import lab01.slangdict._24127430.model.SlangEntry;

/**
 * Kết quả của một lần thêm / sửa / xóa trong {@link DictionaryService}, để giao diện báo lại cho người dùng và
 * chọn sẵn entry vừa đổi.
 *
 * @param status     đã đổi, không có gì để đổi, hay người dùng hủy ở bước hỏi trùng tên
 * @param resolution cách xử lý trùng tên đã dùng; null nếu không trùng tên
 * @param entry      entry sau thay đổi (entry vừa thêm/sửa, entry được gộp nghĩa vào, entry vừa xóa);
 *                   null khi {@link Status#CANCELLED}
 * @param removed    các entry bị xóa đi trong lần này (xóa, hoặc gộp/ghi đè khi sửa)
 */
public record ChangeResult(Status status, ConflictResolution resolution, SlangEntry entry, List<SlangEntry> removed) {

    public enum Status {
        /** Từ điển đã thay đổi và đã được yêu cầu lưu. */
        CHANGED,
        /** Dữ liệu mới giống hệt dữ liệu cũ (ví dụ sửa mà không đổi gì, thêm nghĩa đã có): không lưu. */
        UNCHANGED,
        /** Người dùng hủy ở hộp thoại trùng tên: không có gì thay đổi. */
        CANCELLED
    }

    public ChangeResult {
        Objects.requireNonNull(status, "status");
        removed = List.copyOf(removed);
    }

    static ChangeResult changed(ConflictResolution resolution, SlangEntry entry, List<SlangEntry> removed) {
        return new ChangeResult(Status.CHANGED, resolution, entry, removed);
    }

    static ChangeResult unchanged(ConflictResolution resolution, SlangEntry entry) {
        return new ChangeResult(Status.UNCHANGED, resolution, entry, List.of());
    }

    static ChangeResult cancelled() {
        return new ChangeResult(Status.CANCELLED, null, null, List.of());
    }

    public boolean isChanged() {
        return status == Status.CHANGED;
    }
}
