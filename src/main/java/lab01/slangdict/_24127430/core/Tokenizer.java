package lab01.slangdict._24127430.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Chuẩn hoá chuỗi trước khi đưa vào index hoặc đem đi tra.
 * <ul>
 *   <li>{@link #tokens}: tách definition thành các từ (chữ thường, bỏ dấu câu) cho inverted index.</li>
 *   <li>{@link #normalizeName}: key tra tên slang không phân biệt hoa thường.</li>
 * </ul>
 * Dùng {@link Locale#ROOT} để kết quả không đổi theo ngôn ngữ của máy
 * (ví dụ ở locale Thổ Nhĩ Kỳ, "I".toLowerCase() ra "ı" chứ không phải "i").
 */
public final class Tokenizer {

    /** Mọi chuỗi ký tự KHÔNG phải chữ cái (mọi ngôn ngữ) hoặc chữ số đều là dấu phân cách. */
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");

    private Tokenizer() {
    }

    /**
     * Tách text thành các từ chữ thường, giữ nguyên thứ tự xuất hiện (kể cả từ lặp lại).
     * Ví dụ: "Laugh out loud!" → [laugh, out, loud]; "24/7" → [24, 7]; "I don't" → [i, don, t].
     */
    public static List<String> tokens(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        for (String part : SEPARATORS.split(text.toLowerCase(Locale.ROOT))) {
            if (!part.isEmpty()) { // split có thể sinh phần rỗng ở đầu khi text bắt đầu bằng dấu câu
                result.add(part);
            }
        }
        return result;
    }

    /**
     * Key dùng để tra tên slang: bỏ khoảng trắng hai đầu và chuyển chữ thường.
     * Giữ nguyên ký tự đặc biệt vì tên slang có thể chỉ gồm ký hiệu (":|", "$_$").
     */
    public static String normalizeName(String slang) {
        return slang.strip().toLowerCase(Locale.ROOT);
    }
}
