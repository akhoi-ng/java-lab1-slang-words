package lab01.slangdict._24127430.ds;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;

/**
 * Inverted index generic: mỗi key (ví dụ một từ trong definition) trỏ tới tập value
 * (ví dụ id các entry có chứa từ đó).
 * <p>
 * Key bị chặn {@code K extends Comparable<? super K>} vì index dùng TreeMap: key được giữ
 * có thứ tự, nhờ vậy ngoài tra chính xác O(log n) còn lấy được cả một khoảng key
 * (dùng cho tìm theo tiền tố: mọi từ bắt đầu bằng "lov").
 *
 * @param <K> kiểu key, phải so sánh được
 * @param <V> kiểu value
 */
public class InvertedIndex<K extends Comparable<? super K>, V> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final TreeMap<K, Set<V>> postings = new TreeMap<>();

    /** Gắn value vào key. Trả về true nếu trước đó key chưa chứa value này. */
    public boolean add(K key, V value) {
        return postings.computeIfAbsent(key, k -> new HashSet<>()).add(value);
    }

    /** Gỡ value khỏi key; key không còn value nào thì bị xoá khỏi index. */
    public boolean remove(K key, V value) {
        Set<V> values = postings.get(key);
        if (values == null || !values.remove(value)) {
            return false;
        }
        if (values.isEmpty()) {
            postings.remove(key);
        }
        return true;
    }

    /** Tập value của key (chỉ đọc, rỗng nếu không có). */
    public Set<V> get(K key) {
        Set<V> values = postings.get(key);
        return values == null ? Collections.emptySet() : Collections.unmodifiableSet(values);
    }

    public boolean containsKey(K key) {
        return postings.containsKey(key);
    }

    /** Toàn bộ key theo thứ tự tăng dần (chỉ đọc). */
    public NavigableSet<K> keys() {
        return Collections.unmodifiableNavigableSet(postings.navigableKeySet());
    }

    /** Các key trong khoảng [from, to) theo thứ tự tăng dần (chỉ đọc). */
    public NavigableSet<K> keysInRange(K fromInclusive, K toExclusive) {
        return Collections.unmodifiableNavigableSet(
                postings.navigableKeySet().subSet(fromInclusive, true, toExclusive, false));
    }

    /** Số key khác nhau. */
    public int keyCount() {
        return postings.size();
    }

    public void clear() {
        postings.clear();
    }

    /** Bản sao độc lập (mỗi tập value được chép riêng), dùng chung key và value. O(tổng số value). */
    public InvertedIndex<K, V> copy() {
        InvertedIndex<K, V> copy = new InvertedIndex<>();
        copy.postings.putAll(postings); // từ một SortedMap cùng thứ tự vào TreeMap rỗng: dựng cây O(n), không so sánh
        copy.postings.replaceAll((key, values) -> new HashSet<>(values));
        return copy;
    }
}
