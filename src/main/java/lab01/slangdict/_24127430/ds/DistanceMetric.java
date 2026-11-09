package lab01.slangdict._24127430.ds;

import java.io.Serializable;

/**
 * Hàm khoảng cách giữa hai phần tử, dùng cho {@link BKTree}.
 * Phải thoả tính chất metric: d(a,b) &gt;= 0, d(a,b) = 0 khi a = b, d(a,b) = d(b,a),
 * và bất đẳng thức tam giác d(a,c) &lt;= d(a,b) + d(b,c).
 * <p>
 * Kế thừa Serializable để cây BK (chứa metric) lưu được xuống file .dat.
 *
 * @param <T> kiểu phần tử
 */
@FunctionalInterface
public interface DistanceMetric<T> extends Serializable {

    int distance(T a, T b);
}
