package lab01.slangdict._24127430.ds;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Trie (cây tiền tố) generic: mỗi cạnh là một ký tự, mỗi nút có thể giữ nhiều value
 * (vì nhiều entry có thể trùng tên slang).
 * <p>
 * Tra chính xác và tìm theo tiền tố tốn O(độ dài key), không phụ thuộc số lượng key.
 * Con của mỗi nút được giữ trong TreeMap nên kết quả theo tiền tố ra đúng thứ tự từ điển.
 *
 * @param <V> kiểu value gắn với mỗi key
 */
public class Trie<V> implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final class Node<V> implements Serializable {
        private static final long serialVersionUID = 1L;

        private Map<Character, Node<V>> children; // tạo khi cần để tiết kiệm bộ nhớ
        private Set<V> values;                    // null nếu không có key nào kết thúc tại đây

        Node<V> child(char c) {
            return children == null ? null : children.get(c);
        }

        Node<V> childOrCreate(char c) {
            if (children == null) {
                children = new TreeMap<>();
            }
            return children.computeIfAbsent(c, k -> new Node<>());
        }

        boolean isEmpty() {
            return (values == null || values.isEmpty()) && (children == null || children.isEmpty());
        }
    }

    private final Node<V> root = new Node<>();
    private int keyCount;

    /** Gắn value vào key. Trả về true nếu trước đó key chưa chứa value này. */
    public boolean put(String key, V value) {
        Node<V> node = root;
        for (int i = 0; i < key.length(); i++) {
            node = node.childOrCreate(key.charAt(i));
        }
        if (node.values == null) {
            node.values = new LinkedHashSet<>();
        }
        if (node.values.isEmpty()) {
            keyCount++;
        }
        return node.values.add(value);
    }

    /** Gỡ value khỏi key, đồng thời cắt bỏ các nút không còn dùng. */
    public boolean remove(String key, V value) {
        return remove(root, key, 0, value);
    }

    private boolean remove(Node<V> node, String key, int depth, V value) {
        if (depth == key.length()) {
            if (node.values == null || !node.values.remove(value)) {
                return false;
            }
            if (node.values.isEmpty()) {
                node.values = null;
                keyCount--;
            }
            return true;
        }
        char c = key.charAt(depth);
        Node<V> child = node.child(c);
        if (child == null || !remove(child, key, depth + 1, value)) {
            return false;
        }
        if (child.isEmpty()) {
            node.children.remove(c);
        }
        return true;
    }

    /** Các value của đúng key này (chỉ đọc, rỗng nếu không có). */
    public Set<V> get(String key) {
        Node<V> node = find(key);
        return node == null || node.values == null ? Collections.emptySet() : Collections.unmodifiableSet(node.values);
    }

    /**
     * Value của mọi key bắt đầu bằng prefix, theo thứ tự từ điển của key, tối đa {@code limit} value.
     * Duyệt DFS nên dừng ngay khi đủ, không phải duyệt hết cây.
     */
    public List<V> valuesWithPrefix(String prefix, int limit) {
        List<V> result = new ArrayList<>();
        Node<V> start = find(prefix);
        if (start != null && limit > 0) {
            collect(start, result, limit);
        }
        return result;
    }

    private void collect(Node<V> node, List<V> out, int limit) {
        if (node.values != null) {
            for (V v : node.values) {
                if (out.size() >= limit) {
                    return;
                }
                out.add(v);
            }
        }
        if (node.children != null) {
            for (Node<V> child : node.children.values()) {
                if (out.size() >= limit) {
                    return;
                }
                collect(child, out, limit);
            }
        }
    }

    private Node<V> find(String key) {
        Node<V> node = root;
        for (int i = 0; i < key.length() && node != null; i++) {
            node = node.child(key.charAt(i));
        }
        return node;
    }

    /** Số key khác nhau đang có value. */
    public int keyCount() {
        return keyCount;
    }
}
