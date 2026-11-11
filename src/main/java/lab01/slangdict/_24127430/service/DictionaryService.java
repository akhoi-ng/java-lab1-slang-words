package lab01.slangdict._24127430.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import lab01.slangdict._24127430.core.SlangDictionary;
import lab01.slangdict._24127430.core.Tokenizer;
import lab01.slangdict._24127430.io.DictionaryStore;
import lab01.slangdict._24127430.io.SlangFileParser;
import lab01.slangdict._24127430.io.SlangFormatException;
import lab01.slangdict._24127430.model.ConflictResolution;
import lab01.slangdict._24127430.model.SlangEntry;
import lab01.slangdict._24127430.service.ConflictResolver.Conflict;
import lab01.slangdict._24127430.service.ConflictResolver.Decision;
import lab01.slangdict._24127430.service.ConflictResolver.Operation;

/**
 * Thêm (F4), sửa (F5), xóa (F6) slang và Reset về dữ liệu gốc (F7). Giữ "từ điển hiện tại" cho cả app: các
 * service khác lấy từ điển qua {@link #current()}, vì Reset thay bằng một object từ điển mới
 * ({@link #replaceDictionary}).
 * <p>
 * <b>Trùng tên</b> (không phân biệt hoa thường) thì hỏi {@link ConflictResolver}, có 3 lựa chọn:
 * <pre>
 *                 THÊM tên đã có (F4)                      ĐỔI TÊN thành tên của entry khác (F5)
 * OVERWRITE       thay nghĩa của entry đích bằng nghĩa mới  xóa entry đích, giữ entry đang sửa
 * ADD_MEANING     nối nghĩa mới vào entry đích (bỏ trùng)   gộp nghĩa vào entry đích, xóa entry đang sửa
 * NEW_ENTRY       thêm entry riêng cùng tên                 giữ cả hai entry
 * </pre>
 * Có nhiều entry cùng tên thì người dùng chọn entry đích ({@link Decision#target()}).
 * Sửa mà không đổi tên (chỉ đổi nghĩa hoặc đổi hoa/thường) thì không hỏi, kể cả khi từ điển vốn đã có
 * entry khác cùng tên.
 * <p>
 * Mỗi thay đổi: lưu nền xuống dictionary.dat ({@link DictionaryStore#saveInBackground}) rồi báo listener.
 * Gọi từ EDT. Các bước sửa của một thao tác (ví dụ xóa entry đích rồi thay entry đang sửa) chạy trong
 * {@code synchronized (dictionary)} nên luồng tìm kiếm không bao giờ thấy trạng thái nửa chừng.
 */
public class DictionaryService {

    /** Lỗi dữ liệu nhập, giao diện đổi thành câu thông báo. */
    public enum Problem {
        /** Tên slang trống. */
        NAME_EMPTY,
        /** Tên chứa dấu ` (ký tự tách tên/nghĩa trong slang.txt) hoặc xuống dòng. */
        NAME_INVALID,
        /** Tên dài quá {@link #MAX_NAME_LENGTH} ký tự (sau khi bỏ khoảng trắng hai đầu). */
        NAME_TOO_LONG,
        /** Không có nghĩa nào (sau khi bỏ các dòng trống). */
        NO_MEANING,
        /** Một nghĩa chứa dấu | (ký tự tách các nghĩa trong slang.txt) hoặc xuống dòng. */
        MEANING_INVALID
    }

    /**
     * Độ dài tối đa của tên slang. Slang dài nhất trong slang.txt gốc chỉ 15 ký tự; giới hạn này chặn việc dán nhầm
     * cả đoạn văn vào ô tên (mỗi ký tự của tên là một tầng trong Trie, tên quá dài làm các thao tác trên cây rất sâu).
     */
    public static final int MAX_NAME_LENGTH = 100;

    private final DictionaryStore store;
    private volatile SlangDictionary dictionary;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public DictionaryService(SlangDictionary dictionary, DictionaryStore store) {
        this.dictionary = Objects.requireNonNull(dictionary, "dictionary");
        this.store = Objects.requireNonNull(store, "store");
    }

    // ===================== Đọc =====================

    /** Từ điển hiện tại. Truyền {@code dictionaryService::current} cho SearchService, RandomService... */
    public SlangDictionary current() {
        return dictionary;
    }

    public int size() {
        return dictionary.size();
    }

    /** Entry theo id, null nếu không có (ví dụ đã bị xóa). */
    public SlangEntry get(int id) {
        return dictionary.get(id);
    }

    /** Các entry mang tên này (không phân biệt hoa thường), theo id tăng dần. */
    public List<SlangEntry> entriesNamed(String slang) {
        SlangDictionary dict = dictionary;
        List<SlangEntry> found = new ArrayList<>();
        for (int id : dict.idsByName(slang)) {
            found.add(dict.get(id));
        }
        found.sort(Comparator.comparingInt(SlangEntry::getId));
        return found;
    }

    /**
     * Số entry khác (không tính {@code excludeId}) đang mang tên này. Dialog nhập liệu dùng để báo trước
     * "tên đã có" trong lúc người dùng gõ. Truyền -1 khi thêm mới.
     */
    public int countOthersNamed(String slang, int excludeId) {
        if (slang == null || slang.isBlank()) {
            return 0;
        }
        return (int) dictionary.idsByName(slang).stream().filter(id -> id != excludeId).count();
    }

    // ===================== Kiểm tra dữ liệu nhập =====================

    /** Lỗi đầu tiên của dữ liệu nhập, rỗng nếu hợp lệ. Giao diện gọi trước để báo lỗi ngay trong dialog. */
    public static Optional<Problem> validate(String slang, List<String> meanings) {
        if (slang == null || slang.isBlank()) {
            return Optional.of(Problem.NAME_EMPTY);
        }
        if (!isValidName(slang)) {
            return Optional.of(Problem.NAME_INVALID);
        }
        if (slang.strip().length() > MAX_NAME_LENGTH) {
            return Optional.of(Problem.NAME_TOO_LONG);
        }
        for (String meaning : meanings) {
            if (meaning != null && !isValidMeaning(meaning)) {
                return Optional.of(Problem.MEANING_INVALID);
            }
        }
        if (cleanMeanings(meanings).isEmpty()) {
            return Optional.of(Problem.NO_MEANING);
        }
        return Optional.empty();
    }

    /** Tên không được chứa ` hay xuống dòng, nếu không sẽ không ghi lại được ra định dạng slang.txt. */
    public static boolean isValidName(String slang) {
        return slang.indexOf(SlangFileParser.SLANG_SEPARATOR) < 0 && !hasLineBreak(slang);
    }

    /** Nghĩa không được chứa | hay xuống dòng (| là ký tự tách các nghĩa trong slang.txt). */
    public static boolean isValidMeaning(String meaning) {
        return meaning.indexOf('|') < 0 && !hasLineBreak(meaning);
    }

    private static boolean hasLineBreak(String text) {
        return text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
    }

    /**
     * Chuẩn hóa danh sách nghĩa: bỏ khoảng trắng hai đầu, bỏ dòng trống, bỏ nghĩa trùng (không phân biệt hoa
     * thường, giữ lần xuất hiện đầu tiên), giữ nguyên thứ tự.
     */
    public static List<String> cleanMeanings(List<String> meanings) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String meaning : meanings) {
            if (meaning != null && !meaning.isBlank()) {
                String stripped = meaning.strip();
                unique.putIfAbsent(meaningKey(stripped), stripped);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static String meaningKey(String meaning) {
        return meaning.strip().toLowerCase(Locale.ROOT);
    }

    /** Nghĩa cũ + các nghĩa mới chưa có (không phân biệt hoa thường). */
    static List<String> mergeMeanings(List<String> current, List<String> added) {
        List<String> merged = new ArrayList<>(current);
        merged.addAll(added);
        return cleanMeanings(merged);
    }

    // ===================== Thay đổi dữ liệu =====================

    /**
     * Thêm slang mới (F4). Tên đã có thì hỏi resolver.
     *
     * @throws IllegalArgumentException dữ liệu không hợp lệ (xem {@link #validate})
     */
    public ChangeResult add(String slang, List<String> meanings, ConflictResolver resolver) {
        Objects.requireNonNull(resolver, "resolver");
        requireValid(slang, meanings);
        String name = slang.strip();
        List<String> cleaned = cleanMeanings(meanings);

        List<SlangEntry> existing = entriesNamed(name);
        if (existing.isEmpty()) {
            return committed(null, dictionary.add(name, cleaned), List.of());
        }
        Optional<Decision> decision = resolver.resolve(new Conflict(Operation.ADD, name, cleaned, existing, null));
        if (decision.isEmpty()) {
            return ChangeResult.cancelled();
        }
        SlangEntry target = requireTarget(decision.get(), existing);
        ConflictResolution resolution = decision.get().resolution();

        SlangDictionary dict = dictionary;
        synchronized (dict) {
            switch (resolution) {
                case OVERWRITE -> {
                    // Giữ id của entry đích, thay tên (theo cách viết mới) và toàn bộ nghĩa
                    SlangEntry updated = new SlangEntry(target.getId(), name, cleaned);
                    if (sameContent(target, updated)) {
                        return ChangeResult.unchanged(resolution, target);
                    }
                    dict.replace(updated);
                    return committed(resolution, updated, List.of());
                }
                case ADD_MEANING -> {
                    List<String> merged = mergeMeanings(target.getMeanings(), cleaned);
                    if (merged.size() == target.getMeanings().size()) {
                        return ChangeResult.unchanged(resolution, target); // mọi nghĩa mới đều đã có
                    }
                    SlangEntry updated = target.withMeanings(merged);
                    dict.replace(updated);
                    return committed(resolution, updated, List.of());
                }
                case NEW_ENTRY -> {
                    return committed(resolution, dict.add(name, cleaned), List.of());
                }
                default -> throw new IllegalStateException("Unknown resolution " + resolution);
            }
        }
    }

    /**
     * Sửa tên và/hoặc nghĩa của entry {@code id} (F5). Đổi sang tên của entry khác thì hỏi resolver.
     *
     * @throws NoSuchElementException   không có entry mang id này
     * @throws IllegalArgumentException dữ liệu không hợp lệ (xem {@link #validate})
     */
    public ChangeResult edit(int id, String newSlang, List<String> newMeanings, ConflictResolver resolver) {
        Objects.requireNonNull(resolver, "resolver");
        SlangEntry original = requireEntry(id);
        requireValid(newSlang, newMeanings);
        String name = newSlang.strip();
        List<String> cleaned = cleanMeanings(newMeanings);
        SlangEntry updated = new SlangEntry(id, name, cleaned);

        boolean renamed = !Tokenizer.normalizeName(name).equals(Tokenizer.normalizeName(original.getSlang()));
        List<SlangEntry> others = renamed ? entriesNamed(name) : List.of();
        SlangDictionary dict = dictionary;
        if (others.isEmpty()) {
            if (sameContent(original, updated)) {
                return ChangeResult.unchanged(null, original);
            }
            dict.replace(updated);
            return committed(null, updated, List.of());
        }

        Optional<Decision> decision =
                resolver.resolve(new Conflict(Operation.EDIT, name, cleaned, others, original));
        if (decision.isEmpty()) {
            return ChangeResult.cancelled();
        }
        SlangEntry target = requireTarget(decision.get(), others);
        ConflictResolution resolution = decision.get().resolution();

        synchronized (dict) {
            switch (resolution) {
                case OVERWRITE -> {
                    // Entry đang sửa "chiếm" tên: entry đích bị xóa
                    dict.remove(target.getId());
                    dict.replace(updated);
                    return committed(resolution, updated, List.of(target));
                }
                case ADD_MEANING -> {
                    // Gộp nghĩa vào entry đích (giữ tên của entry đích), entry đang sửa biến mất
                    List<String> merged = mergeMeanings(target.getMeanings(), cleaned);
                    SlangEntry into = target.withMeanings(merged);
                    if (merged.size() != target.getMeanings().size()) {
                        dict.replace(into);
                    }
                    dict.remove(id);
                    return committed(resolution, into, List.of(original));
                }
                case NEW_ENTRY -> {
                    dict.replace(updated);
                    return committed(resolution, updated, List.of());
                }
                default -> throw new IllegalStateException("Unknown resolution " + resolution);
            }
        }
    }

    /**
     * Xóa entry {@code id} (F6). Việc hỏi xác nhận là của giao diện.
     *
     * @throws NoSuchElementException không có entry mang id này
     */
    public ChangeResult delete(int id) {
        SlangEntry removed = dictionary.remove(id);
        if (removed == null) {
            throw new NoSuchElementException("No entry with id " + id);
        }
        return committed(null, removed, List.of(removed));
    }

    // ===================== Reset (F7) =====================

    /**
     * Build một từ điển mới từ slang.txt gốc (data/slang.txt nếu có, không thì bản nhúng trong jar). Không đụng tới
     * từ điển hiện tại, nên gọi được trên luồng nền (7,6k slang: ~0,1 s; file 100k: ~0,5–0,7 s).
     *
     * @throws SlangFormatException file gốc sai định dạng (kèm số dòng)
     * @throws IOException          không đọc được file gốc
     */
    public SlangDictionary loadOriginal() throws IOException, SlangFormatException {
        return store.loadOriginal();
    }

    /** File gốc mà {@link #loadOriginal} sẽ đọc (để hiện trong dialog xác nhận). */
    public String originalSource() {
        return store.originalSourceDescription();
    }

    /**
     * Thay <b>cả</b> từ điển hiện tại bằng {@code fresh} (thường là kết quả của {@link #loadOriginal}), lưu nền rồi
     * báo listener. Gọi trên EDT.
     * <p>
     * Chỉ cần gán lại một field: SearchService, RandomService... đều lấy từ điển qua {@link #current()}, nên lần đọc
     * kế tiếp tự dùng bản mới. Field là {@code volatile} nên luồng tìm kiếm thấy ngay object mới. Lần tìm đang chạy dở
     * trên object cũ vẫn xong bình thường (object cũ không bị sửa), giao diện tìm lại sau đó.
     */
    public void replaceDictionary(SlangDictionary fresh) {
        dictionary = Objects.requireNonNull(fresh, "fresh");
        store.saveInBackground(fresh); // lần lưu đang chờ của từ điển cũ (nếu có) bị thay bằng bản này
        listeners.forEach(Runnable::run);
    }

    /**
     * F7: đưa từ điển về danh sách gốc ({@link #loadOriginal} rồi {@link #replaceDictionary}) ngay trên luồng gọi.
     * Giao diện tách hai bước để phần build chạy trên luồng nền.
     *
     * @return số slang sau khi reset
     */
    public int reset() throws IOException, SlangFormatException {
        replaceDictionary(loadOriginal());
        return size();
    }

    /** Listener được gọi (trên luồng vừa sửa, tức EDT) sau mỗi thay đổi thật sự của từ điển, kể cả Reset. */
    public void addChangeListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    // ===================== Nội bộ =====================

    private ChangeResult committed(ConflictResolution resolution, SlangEntry entry, List<SlangEntry> removed) {
        store.saveInBackground(dictionary);
        listeners.forEach(Runnable::run);
        return ChangeResult.changed(resolution, entry, removed);
    }

    private SlangEntry requireEntry(int id) {
        SlangEntry entry = dictionary.get(id);
        if (entry == null) {
            throw new NoSuchElementException("No entry with id " + id);
        }
        return entry;
    }

    private static void requireValid(String slang, List<String> meanings) {
        Objects.requireNonNull(meanings, "meanings");
        Optional<Problem> problem = validate(slang, meanings);
        if (problem.isPresent()) {
            throw new IllegalArgumentException("Invalid input: " + problem.get());
        }
    }

    /** Entry đích phải là một trong các entry trùng tên đang có (resolver không được trả về entry lạ). */
    private static SlangEntry requireTarget(Decision decision, List<SlangEntry> candidates) {
        for (SlangEntry candidate : candidates) {
            if (candidate.getId() == decision.target().getId()) {
                return candidate; // bản trong từ điển, không phải bản resolver giữ (có thể đã cũ)
            }
        }
        throw new IllegalArgumentException("Target #" + decision.target().getId() + " is not a conflicting entry");
    }

    /** Cùng tên (kể cả hoa/thường) và cùng danh sách nghĩa theo đúng thứ tự. */
    private static boolean sameContent(SlangEntry a, SlangEntry b) {
        return a.getSlang().equals(b.getSlang()) && a.getMeanings().equals(b.getMeanings());
    }
}
