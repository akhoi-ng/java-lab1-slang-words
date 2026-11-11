package lab01.slangdict._24127430.ds;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * BK-tree (Burkhard–Keller) generic: tìm mọi phần tử có khoảng cách ≤ k tới truy vấn (fuzzy search).
 * <p>
 * Mỗi nút con được gắn nhãn bằng khoảng cách d tới nút cha. Khi tìm với truy vấn q và ngưỡng k,
 * gọi D = d(q, nút); theo bất đẳng thức tam giác, chỉ cần xuống các con có nhãn trong [D-k, D+k].
 * Nhờ vậy bỏ qua được phần lớn cây thay vì so với mọi phần tử.
 * <p>
 * BK-tree không xoá nút được nên dùng "xoá mềm": đánh dấu deleted, thêm lại thì bỏ đánh dấu.
 *
 * @param <T> kiểu phần tử
 */
public class BKTree<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Một kết quả fuzzy: phần tử và khoảng cách tới truy vấn. */
    public record Match<T>(T item, int distance) {
    }

    private static final class Node<T> implements Serializable {
        private static final long serialVersionUID = 1L;

        private final T item;
        private boolean deleted;
        private Map<Integer, Node<T>> children;

        Node(T item) {
            this.item = item;
        }
    }

    private final DistanceMetric<? super T> metric;
    private Node<T> root;
    private int size;

    public BKTree(DistanceMetric<? super T> metric) {
        this.metric = Objects.requireNonNull(metric, "metric");
    }

    /** Thêm phần tử. Trả về false nếu đã có sẵn. */
    public boolean add(T item) {
        Objects.requireNonNull(item, "item");
        if (root == null) {
            root = new Node<>(item);
            size++;
            return true;
        }
        Node<T> node = root;
        while (true) {
            int d = metric.distance(item, node.item);
            if (d == 0) {
                if (!node.deleted) {
                    return false;
                }
                node.deleted = false;
                size++;
                return true;
            }
            if (node.children == null) {
                node.children = new HashMap<>();
            }
            Node<T> child = node.children.get(d);
            if (child == null) {
                node.children.put(d, new Node<>(item));
                size++;
                return true;
            }
            node = child;
        }
    }

    /** Xoá mềm phần tử. Trả về false nếu không có. */
    public boolean remove(T item) {
        Node<T> node = root;
        while (node != null) {
            int d = metric.distance(item, node.item);
            if (d == 0) {
                if (node.deleted) {
                    return false;
                }
                node.deleted = true;
                size--;
                return true;
            }
            node = node.children == null ? null : node.children.get(d);
        }
        return false;
    }

    public boolean contains(T item) {
        return !search(item, 0).isEmpty();
    }

    /** Mọi phần tử có khoảng cách ≤ maxDistance, sắp theo khoảng cách tăng dần. */
    public List<Match<T>> search(T query, int maxDistance) {
        List<Match<T>> matches = new ArrayList<>();
        if (root == null || maxDistance < 0) {
            return matches;
        }
        Deque<Node<T>> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Node<T> node = stack.pop();
            int d = metric.distance(query, node.item);
            if (d <= maxDistance && !node.deleted) {
                matches.add(new Match<>(node.item, d));
            }
            if (node.children != null) {
                for (int label = Math.max(1, d - maxDistance); label <= d + maxDistance; label++) {
                    Node<T> child = node.children.get(label);
                    if (child != null) {
                        stack.push(child);
                    }
                }
            }
        }
        matches.sort(Comparator.comparingInt(Match::distance));
        return matches;
    }

    /** Số phần tử (không tính phần tử đã xoá mềm). */
    public int size() {
        return size;
    }

    /**
     * Bản sao độc lập có đúng hình dạng cây (kể cả nút xoá mềm), dùng chung các phần tử (nên bất biến, ví dụ String).
     * Chỉ chép cấu trúc, không tính lại khoảng cách nào: O(số nút). Duyệt bằng ngăn xếp, không đệ quy.
     */
    public BKTree<T> copy() {
        BKTree<T> copy = new BKTree<>(metric);
        copy.size = size;
        if (root == null) {
            return copy;
        }
        copy.root = copyNode(root);
        Deque<Node<T>[]> stack = new ArrayDeque<>(); // cặp {nút gốc, nút bản sao}
        stack.push(pair(root, copy.root));
        while (!stack.isEmpty()) {
            Node<T>[] pair = stack.pop();
            if (pair[0].children == null) {
                continue;
            }
            pair[1].children = new HashMap<>(Math.max(4, pair[0].children.size() * 4 / 3 + 1));
            for (Map.Entry<Integer, Node<T>> child : pair[0].children.entrySet()) {
                Node<T> childCopy = copyNode(child.getValue());
                pair[1].children.put(child.getKey(), childCopy);
                stack.push(pair(child.getValue(), childCopy));
            }
        }
        return copy;
    }

    private static <T> Node<T> copyNode(Node<T> node) {
        Node<T> copy = new Node<>(node.item);
        copy.deleted = node.deleted;
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static <T> Node<T>[] pair(Node<T> source, Node<T> copy) {
        return new Node[] {source, copy};
    }
}
