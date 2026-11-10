package lab01.slangdict._24127430.service;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;

import lab01.slangdict._24127430.core.SlangDictionary;
import lab01.slangdict._24127430.model.SlangEntry;

/**
 * F8: slang "On this day" (cả ngày ra cùng một slang) và random một slang bất kỳ.
 * <p>
 * <b>Slang của ngày:</b> mỗi entry được chấm một điểm giả ngẫu nhiên {@code score(ngày, id)}, entry điểm cao nhất
 * là slang của ngày đó (rendezvous hashing / "highest random weight"). So với cách đơn giản
 * {@code new Random(ngày).nextInt(size)}:
 * <ul>
 *   <li>Cách đơn giản phụ thuộc vào số entry và thứ tự của chúng: thêm hay xóa <b>bất kỳ</b> slang nào cũng làm
 *       slang hôm nay đổi sang slang khác.</li>
 *   <li>Ở đây điểm của một entry chỉ phụ thuộc vào ngày và id của chính nó. Thêm một slang chỉ đổi kết quả nếu
 *       slang mới có điểm cao hơn tất cả (xác suất 1/n); xóa chỉ đổi nếu xóa đúng slang hôm nay; sửa nghĩa
 *       (giữ id) không bao giờ đổi. Tắt app mở lại vẫn ra đúng slang đó.</li>
 * </ul>
 * Đổi lại phải duyệt hết các entry: O(n), 100k slang mất vài ms, và chỉ tính lại khi đổi ngày hoặc dữ liệu đổi.
 * <p>
 * <b>Random khác:</b> {@link SlangDictionary#randomEntry} lấy một id ngẫu nhiên trong danh sách id, O(1).
 * <p>
 * Lấy từ điển qua {@code Supplier} (truyền {@code dictionaryService::current}) để sau Reset tự dùng bản mới.
 * Gọi trên EDT, cùng luồng với mọi thao tác sửa từ điển.
 */
public class RandomService {

    private final Supplier<SlangDictionary> dictionary;
    private final Random random;

    public RandomService(Supplier<SlangDictionary> dictionary) {
        this(dictionary, new Random());
    }

    /** Cho test: truyền {@code Random} có seed để kết quả lặp lại được. */
    RandomService(Supplier<SlangDictionary> dictionary, Random random) {
        this.dictionary = Objects.requireNonNull(dictionary, "dictionary");
        this.random = Objects.requireNonNull(random, "random");
    }

    /** Slang của ngày {@code date}: gọi bao nhiêu lần trong cùng ngày cũng ra cùng entry. Rỗng nếu từ điển trống. */
    public Optional<SlangEntry> onThisDay(LocalDate date) {
        long day = date.toEpochDay();
        SlangEntry best = null;
        long bestScore = 0;
        for (SlangEntry entry : dictionary.get().all()) {
            long score = score(day, entry.getId());
            // all() không có thứ tự cố định → hòa điểm thì chọn id nhỏ hơn, để kết quả không phụ thuộc thứ tự duyệt
            if (best == null || score > bestScore || (score == bestScore && entry.getId() < best.getId())) {
                best = entry;
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Một slang ngẫu nhiên bất kỳ. Rỗng nếu từ điển trống. */
    public Optional<SlangEntry> random() {
        return random(-1);
    }

    /**
     * Một slang ngẫu nhiên khác entry {@code excludeId} (đang hiện trên màn hình), để bấm "Random khác" luôn thấy
     * slang mới. Từ điển chỉ có đúng entry đó thì trả về chính nó.
     */
    public Optional<SlangEntry> random(int excludeId) {
        SlangDictionary dict = dictionary.get();
        SlangEntry entry = dict.randomEntry(random);
        // Thử lại khi trúng entry bị loại trừ: với n entry, xác suất phải thử lại chỉ là 1/n
        while (entry != null && entry.getId() == excludeId && dict.size() > 1) {
            entry = dict.randomEntry(random);
        }
        return Optional.ofNullable(entry);
    }

    /** Bản mới nhất của entry (theo id) trong từ điển hiện tại; rỗng nếu đã bị xóa. */
    public Optional<SlangEntry> latest(SlangEntry entry) {
        return Optional.ofNullable(dictionary.get().get(entry.getId()));
    }

    /**
     * Điểm giả ngẫu nhiên của entry {@code id} trong ngày {@code day}: cùng (ngày, id) luôn ra cùng điểm, đổi ngày
     * thì điểm của mọi entry bị "xáo" lại. Dùng hàm trộn bit của SplitMix64 (thuật toán của {@code SplittableRandom}).
     */
    static long score(long day, int id) {
        return mix(mix(day) + id);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
