package lab01.slangdict._24127430.ds;

/**
 * Hàm khoảng cách giữa hai phần tử, dùng cho BKTree.
 */
public interface DistanceMetric<T> {
    int distance(T a, T b);
}
