package lab01.slangdict._24127430.model;

import java.util.Objects;

/**
 * Một kết quả tìm kiếm kèm điểm xếp hạng.
 * Thứ tự tự nhiên: điểm cao trước, cùng điểm thì theo tên slang (không phân biệt hoa thường), rồi theo id.
 *
 * @param entry entry tìm được
 * @param score điểm xếp hạng, càng cao càng liên quan
 */
public record SearchResult(SlangEntry entry, int score) implements Comparable<SearchResult> {

    public SearchResult {
        Objects.requireNonNull(entry, "entry");
    }

    @Override
    public int compareTo(SearchResult other) {
        int byScore = Integer.compare(other.score, score);
        if (byScore != 0) {
            return byScore;
        }
        int bySlang = String.CASE_INSENSITIVE_ORDER.compare(entry.getSlang(), other.entry.getSlang());
        if (bySlang != 0) {
            return bySlang;
        }
        return Integer.compare(entry.getId(), other.entry.getId());
    }
}
