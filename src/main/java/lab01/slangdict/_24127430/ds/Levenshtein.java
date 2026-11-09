package lab01.slangdict._24127430.ds;

/**
 * Khoảng cách chỉnh sửa Levenshtein: số phép chèn / xoá / thay 1 ký tự ít nhất để biến a thành b.
 * Ví dụ: d("lol", "lool") = 1, d("love", "lvoe") = 2.
 * <p>
 * Quy hoạch động O(|a|·|b|) thời gian, chỉ giữ 2 hàng nên tốn O(|b|) bộ nhớ.
 */
public final class Levenshtein implements DistanceMetric<String> {

    private static final long serialVersionUID = 1L;

    public static final Levenshtein INSTANCE = new Levenshtein();

    private Levenshtein() {
    }

    @Override
    public int distance(String a, String b) {
        if (a.equals(b)) {
            return 0;
        }
        if (a.isEmpty()) {
            return b.length();
        }
        if (b.isEmpty()) {
            return a.length();
        }

        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (ca == b.charAt(j - 1) ? 0 : 1);
                int deletion = previous[j] + 1;
                int insertion = current[j - 1] + 1;
                current[j] = Math.min(substitution, Math.min(deletion, insertion));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    /** Giữ đúng một instance khi đọc lại từ file .dat. */
    private Object readResolve() {
        return INSTANCE;
    }
}
