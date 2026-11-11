package lab01.slangdict._24127430.ds;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

// [THAM KHẢO - CẢ FILE] Trie theo R. Sedgewick, K. Wayne, "Algorithms" 4th ed., mục 5.2 Tries (lớp TrieST)
//   https://algs4.cs.princeton.edu/52trie/TrieST.java.html
//   Phần lấy từ nguồn: put/get đi theo từng ký tự, gom mọi key dưới nút của tiền tố (collect).
//   Tự viết: con lưu bằng TreeMap thay cho mảng R phần tử, mỗi nút giữ tập value (slang trùng tên), remove có
//   cắt nhánh rỗng, giới hạn số kết quả, copy(), ghi .dat dạng phẳng (writeReplace/readResolve) để tránh tràn stack.

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
        // Vòng lặp thay cho đệ quy: độ sâu bằng độ dài key, key dài thì đệ quy sẽ tràn stack
        @SuppressWarnings("unchecked")
        Node<V>[] path = (Node<V>[]) new Node[key.length() + 1];
        path[0] = root;
        for (int i = 0; i < key.length(); i++) {
            path[i + 1] = path[i].child(key.charAt(i));
            if (path[i + 1] == null) {
                return false;
            }
        }
        Node<V> node = path[key.length()];
        if (node.values == null || !node.values.remove(value)) {
            return false;
        }
        if (node.values.isEmpty()) {
            node.values = null;
            keyCount--;
        }
        // Cắt các nút không còn dùng, từ dưới lên
        for (int depth = key.length(); depth > 0 && path[depth].isEmpty(); depth--) {
            path[depth - 1].children.remove(key.charAt(depth - 1));
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

    /** DFS theo thứ tự trước (nút, rồi các con theo thứ tự ký tự), dùng ngăn xếp tường minh thay cho đệ quy. */
    private void collect(Node<V> start, List<V> out, int limit) {
        Deque<Iterator<Node<V>>> stack = new ArrayDeque<>();
        Node<V> node = start;
        while (true) {
            if (node.values != null) {
                for (V v : node.values) {
                    if (out.size() >= limit) {
                        return;
                    }
                    out.add(v);
                }
            }
            if (node.children != null && !node.children.isEmpty()) {
                stack.push(node.children.values().iterator());
            }
            while (!stack.isEmpty() && !stack.peek().hasNext()) {
                stack.pop();
            }
            if (stack.isEmpty() || out.size() >= limit) {
                return;
            }
            node = stack.peek().next();
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

    /**
     * Bản sao độc lập (giữ đúng thứ tự value trong từng key), dùng chung các value (nên bất biến, ví dụ Integer).
     * O(số nút), duyệt bằng ngăn xếp nên key dài bao nhiêu cũng không tràn stack.
     */
    public Trie<V> copy() {
        Trie<V> copy = new Trie<>();
        copy.keyCount = keyCount;
        Deque<Node<V>> sources = new ArrayDeque<>();
        Deque<Node<V>> copies = new ArrayDeque<>();
        sources.push(root);
        copies.push(copy.root);
        while (!sources.isEmpty()) {
            Node<V> source = sources.pop();
            Node<V> target = copies.pop();
            if (source.values != null) {
                target.values = new LinkedHashSet<>(source.values);
            }
            if (source.children != null) {
                TreeMap<Character, Node<V>> children = new TreeMap<>();
                for (Map.Entry<Character, Node<V>> child : source.children.entrySet()) {
                    Node<V> childCopy = new Node<>();
                    children.put(child.getKey(), childCopy);
                    sources.push(child.getValue());
                    copies.push(childCopy);
                }
                target.children = children;
            }
        }
        return copy;
    }

    // ===================== Serialize =====================

    /*
     * Serialize mặc định đi theo từng tầng Node → TreeMap → Node, tức đệ quy theo độ dài key: một tên slang
     * dài vài trăm ký tự đủ làm ObjectInputStream tràn stack khi đọc lại, và app không mở lại được nữa.
     * Vì vậy Trie được ghi dưới dạng phẳng (danh sách key → value, duyệt bằng vòng lặp) rồi chèn lại khi đọc.
     * File .dat cũ (ghi theo dạng mặc định) vẫn đọc được vì Trie và Node giữ nguyên field và serialVersionUID.
     */

    /** Ghi {@link SerializedForm} thay cho chính object này. */
    private Object writeReplace() {
        List<String> keys = new ArrayList<>(keyCount);
        List<Integer> counts = new ArrayList<>(keyCount);
        List<Object> values = new ArrayList<>();
        // DFS bằng ngăn xếp tường minh (không đệ quy), theo thứ tự từ điển của key
        Deque<Iterator<Map.Entry<Character, Node<V>>>> stack = new ArrayDeque<>();
        StringBuilder path = new StringBuilder();
        addKey(root, path, keys, counts, values);
        stack.push(childrenOf(root));
        while (!stack.isEmpty()) {
            Iterator<Map.Entry<Character, Node<V>>> children = stack.peek();
            if (!children.hasNext()) {
                stack.pop();
                if (!stack.isEmpty()) {
                    path.setLength(path.length() - 1); // rời nút con, quay về nút cha
                }
                continue;
            }
            Map.Entry<Character, Node<V>> child = children.next();
            path.append(child.getKey().charValue());
            addKey(child.getValue(), path, keys, counts, values);
            stack.push(childrenOf(child.getValue()));
        }
        return new SerializedForm(keys.toArray(new String[0]),
                counts.stream().mapToInt(Integer::intValue).toArray(), values.toArray());
    }

    private static <V> void addKey(Node<V> node, StringBuilder path, List<String> keys, List<Integer> counts,
                                   List<Object> values) {
        if (node.values != null && !node.values.isEmpty()) {
            keys.add(path.toString());
            counts.add(node.values.size());
            values.addAll(node.values);
        }
    }

    private static <V> Iterator<Map.Entry<Character, Node<V>>> childrenOf(Node<V> node) {
        return node.children == null ? Collections.emptyIterator() : node.children.entrySet().iterator();
    }

    /**
     * Dạng lưu xuống file của Trie: key thứ i có {@code counts[i]} value, nằm liên tiếp trong {@code values}
     * (giữ đúng thứ tự cũ của các value trong cùng một key).
     */
    private static final class SerializedForm implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String[] keys;
        private final int[] counts;
        private final Object[] values;

        SerializedForm(String[] keys, int[] counts, Object[] values) {
            this.keys = keys;
            this.counts = counts;
            this.values = values;
        }

        private Object readResolve() throws ObjectStreamException {
            if (keys == null || counts == null || values == null || keys.length != counts.length) {
                throw new InvalidObjectException("Malformed trie data");
            }
            Trie<Object> trie = new Trie<>();
            int next = 0;
            for (int i = 0; i < keys.length; i++) {
                if (keys[i] == null || counts[i] <= 0 || counts[i] > values.length - next) {
                    throw new InvalidObjectException("Malformed trie data");
                }
                for (int j = 0; j < counts[i]; j++) {
                    trie.put(keys[i], values[next++]);
                }
            }
            if (next != values.length) {
                throw new InvalidObjectException("Malformed trie data");
            }
            return trie;
        }
    }
}
