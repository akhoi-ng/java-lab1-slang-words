package lab01.slangdict._24127430.service;

import java.util.List;
import java.util.Objects;

import lab01.slangdict._24127430.model.SearchResult;

/**
 * Kết quả của một lần tìm kiếm.
 *
 * @param results      các kết quả đã xếp hạng, tối đa {@code limit} phần tử (chỉ đọc)
 * @param totalMatches tổng số entry khớp, kể cả phần bị cắt bởi {@code limit}
 * @param elapsedNanos thời gian tìm (tra index + chấm điểm + sắp xếp), tính bằng nano giây
 */
public record SearchOutcome(List<SearchResult> results, int totalMatches, long elapsedNanos) {

    public SearchOutcome {
        results = List.copyOf(Objects.requireNonNull(results, "results"));
        if (totalMatches < results.size()) {
            throw new IllegalArgumentException(
                    "totalMatches " + totalMatches + " < number of results " + results.size());
        }
    }

    /** Không có kết quả nào (ví dụ query rỗng). */
    public static SearchOutcome empty(long elapsedNanos) {
        return new SearchOutcome(List.of(), 0, elapsedNanos);
    }

    public boolean isEmpty() {
        return results.isEmpty();
    }

    /** true nếu còn kết quả khớp nhưng bị cắt bớt vì {@code limit}. */
    public boolean isTruncated() {
        return totalMatches > results.size();
    }

    /** Thời gian tìm tính bằng mili giây (có phần thập phân, ví dụ 0.42 ms). */
    public double elapsedMillis() {
        return elapsedNanos / 1_000_000.0;
    }
}
