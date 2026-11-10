package lab01.slangdict._24127430.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import lab01.slangdict._24127430.model.ConflictResolution;
import lab01.slangdict._24127430.model.SlangEntry;

/**
 * Hỏi cách xử lý khi tên slang mới trùng với entry đã có (F4 thêm, F5 đổi tên).
 * <p>
 * {@link DictionaryService} không biết gì về Swing: nó chỉ gọi resolver này. Trên giao diện, App truyền vào hàm mở
 * {@code ConflictDialog}; trong test thì truyền lambda trả lời sẵn, ví dụ
 * {@code conflict -> Optional.of(new Decision(OVERWRITE, conflict.existing().get(0)))}.
 */
@FunctionalInterface
public interface ConflictResolver {

    /**
     * @return cách xử lý người dùng chọn, hoặc {@link Optional#empty()} nếu người dùng hủy
     */
    Optional<Decision> resolve(Conflict conflict);

    /** Thêm mới hay sửa: cùng 3 lựa chọn nhưng ý nghĩa hơi khác (xem {@link DictionaryService}). */
    enum Operation { ADD, EDIT }

    /**
     * Tình huống trùng tên.
     *
     * @param operation thêm hay sửa
     * @param slang     tên mới người dùng nhập
     * @param meanings  các nghĩa người dùng nhập (đã chuẩn hóa)
     * @param existing  các entry khác đang mang tên đó (theo id tăng dần, ít nhất 1)
     * @param editing   entry đang sửa (null khi thêm mới)
     */
    record Conflict(Operation operation, String slang, List<String> meanings, List<SlangEntry> existing,
                    SlangEntry editing) {
        public Conflict {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(slang, "slang");
            meanings = List.copyOf(meanings);
            existing = List.copyOf(existing);
            if (existing.isEmpty()) {
                throw new IllegalArgumentException("A conflict needs at least one existing entry");
            }
            if ((operation == Operation.EDIT) != (editing != null)) {
                throw new IllegalArgumentException("editing must be set exactly when operation is EDIT");
            }
        }
    }

    /**
     * Lựa chọn của người dùng.
     *
     * @param resolution ghi đè / thêm nghĩa / tạo entry riêng
     * @param target     entry trùng tên bị tác động (một trong {@link Conflict#existing()}); với NEW_ENTRY thì
     *                   không dùng tới nhưng vẫn phải là một entry trong danh sách
     */
    record Decision(ConflictResolution resolution, SlangEntry target) {
        public Decision {
            Objects.requireNonNull(resolution, "resolution");
            Objects.requireNonNull(target, "target");
        }
    }
}
