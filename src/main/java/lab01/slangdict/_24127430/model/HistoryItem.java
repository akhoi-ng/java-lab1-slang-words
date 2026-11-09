package lab01.slangdict._24127430.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Một dòng lịch sử tìm kiếm.
 *
 * @param target tìm theo slang hay definition
 * @param mode   kiểu khớp đã dùng
 * @param query  chuỗi người dùng gõ
 * @param time   thời điểm tìm
 */
public record HistoryItem(SearchTarget target, SearchMode mode, String query, LocalDateTime time)
        implements Serializable {

    public HistoryItem {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(time, "time");
    }
}
