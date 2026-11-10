package lab01.slangdict._24127430.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import lab01.slangdict._24127430.ds.BKTree;
import lab01.slangdict._24127430.ds.InvertedIndex;
import lab01.slangdict._24127430.ds.Levenshtein;
import lab01.slangdict._24127430.ds.Trie;
import lab01.slangdict._24127430.model.SlangEntry;

/**
 * Từ điển đã index: giữ toàn bộ entry cùng các index phục vụ tìm kiếm.
 * Cả object (entry + index) được serialize xuống data/dictionary.dat, nên lần chạy sau
 * không phải index lại (yêu cầu chung 3 của đề).
 *
 * <pre>
 * entries         id → entry                         get(id)                 O(1)
 * idList          danh sách id (để random)           randomEntry             O(1)
 * positionOf      id → vị trí trong idList           xoá khỏi idList         O(1)
 * idsByName       tên chuẩn hoá → các id             tìm slang EXACT         O(1)
 * nameTrie        tên chuẩn hoá → các id             tìm slang PREFIX        O(độ dài prefix + số kết quả)
 * nameTree        các tên khác nhau (BK-tree)        tìm slang FUZZY
 * wordIndex       từ trong definition → các id       tìm definition EXACT / PREFIX (TreeMap)
 * vocabularyTree  các từ khác nhau (BK-tree)         tìm definition FUZZY
 * </pre>
 *
 * <b>Bất biến:</b> mọi index luôn khớp với {@code entries}. Chỉ được thay đổi dữ liệu qua
 * {@link #add}, {@link #replace}, {@link #remove}; ba method này luôn gỡ/gắn index theo cặp
 * {@link #unindex}/{@link #index}. Test so sánh với một từ điển build lại từ đầu để kiểm chứng.
 * <p>
 * Không thread-safe: chỉ dùng trên một luồng (EDT); khi lưu nền thì serialize snapshot trước.
 */
public class SlangDictionary implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<Integer, SlangEntry> entries = new HashMap<>();

    private final List<Integer> idList = new ArrayList<>();
    private final Map<Integer, Integer> positionOf = new HashMap<>();

    private final Map<String, Set<Integer>> idsByName = new HashMap<>();
    private final Trie<Integer> nameTrie = new Trie<>();
    private final BKTree<String> nameTree = new BKTree<>(Levenshtein.INSTANCE);

    private final InvertedIndex<String, Integer> wordIndex = new InvertedIndex<>();
    private final BKTree<String> vocabularyTree = new BKTree<>(Levenshtein.INSTANCE);

    private int nextId;

    /** Từ điển rỗng. */
    public SlangDictionary() {
    }

    /**
     * Build từ danh sách entry (thường là kết quả của SlangFileParser), giữ nguyên id của từng entry.
     *
     * @throws IllegalArgumentException nếu có hai entry trùng id
     */
    public static SlangDictionary build(List<SlangEntry> source) {
        SlangDictionary dictionary = new SlangDictionary();
        for (SlangEntry entry : source) {
            if (dictionary.entries.containsKey(entry.getId())) {
                throw new IllegalArgumentException("Duplicate id " + entry.getId());
            }
            dictionary.insert(entry);
            dictionary.nextId = Math.max(dictionary.nextId, entry.getId() + 1);
        }
        return dictionary;
    }

    // ===================== Đọc =====================

    public int size() {
        return entries.size();
    }

    /** Entry theo id, {@code null} nếu không có. */
    public SlangEntry get(int id) {
        return entries.get(id);
    }

    /** Mọi entry (chỉ đọc, không theo thứ tự cố định). */
    public Collection<SlangEntry> all() {
        return Collections.unmodifiableCollection(entries.values());
    }

    /** Id mà lần {@link #add} tiếp theo sẽ cấp. */
    public int nextId() {
        return nextId;
    }

    // ----- theo tên slang -----

    /** Id các entry có đúng tên này, không phân biệt hoa thường (chỉ đọc, rỗng nếu không có). */
    public Set<Integer> idsByName(String slang) {
        Set<Integer> ids = idsByName.get(Tokenizer.normalizeName(slang));
        return ids == null ? Collections.emptySet() : Collections.unmodifiableSet(ids);
    }

    /** Id các entry có tên bắt đầu bằng prefix (không phân biệt hoa thường), theo thứ tự từ điển của tên. */
    public List<Integer> idsWithNamePrefix(String prefix, int limit) {
        return nameTrie.valuesWithPrefix(Tokenizer.normalizeName(prefix), limit);
    }

    /** Các tên khác nhau đã chuẩn hoá (chỉ đọc), dùng cho tìm SUBSTRING. */
    public Set<String> names() {
        return Collections.unmodifiableSet(idsByName.keySet());
    }

    /** Các tên đã chuẩn hoá có khoảng cách Levenshtein ≤ k tới query, gần nhất trước. */
    public List<BKTree.Match<String>> fuzzyNames(String query, int k) {
        return nameTree.search(Tokenizer.normalizeName(query), k);
    }

    // ----- theo từ trong definition -----

    /** Id các entry có definition chứa đúng từ này (một từ, không phân biệt hoa thường). */
    public Set<Integer> idsWithWord(String word) {
        return wordIndex.get(normalizeWord(word));
    }

    /** Các từ trong vocabulary bắt đầu bằng prefix, theo thứ tự tăng dần (chỉ đọc). */
    public NavigableSet<String> wordsWithPrefix(String prefix) {
        String p = normalizeWord(prefix);
        // Mọi chuỗi bắt đầu bằng p đều nằm trong [p, p + '￿'): một khoảng liên tục của TreeMap
        return wordIndex.keysInRange(p, p + Character.MAX_VALUE);
    }

    /** Toàn bộ từ khác nhau xuất hiện trong các definition (chỉ đọc, có thứ tự). */
    public NavigableSet<String> vocabulary() {
        return wordIndex.keys();
    }

    /** Các từ trong vocabulary có khoảng cách Levenshtein ≤ k tới word, gần nhất trước. */
    public List<BKTree.Match<String>> fuzzyWords(String word, int k) {
        return vocabularyTree.search(normalizeWord(word), k);
    }

    // ----- ngẫu nhiên -----

    /** Một entry ngẫu nhiên trong O(1), {@code null} nếu từ điển rỗng. */
    public SlangEntry randomEntry(Random random) {
        if (idList.isEmpty()) {
            return null;
        }
        return entries.get(idList.get(random.nextInt(idList.size())));
    }

    // ===================== Thay đổi dữ liệu =====================

    /** Thêm entry mới với id mới (cho phép trùng tên với entry đã có). */
    public SlangEntry add(String slang, List<String> meanings) {
        SlangEntry entry = new SlangEntry(nextId, slang, meanings); // validate trước khi tăng id
        nextId++;
        insert(entry);
        return entry;
    }

    /**
     * Thay entry cùng id bằng bản mới (đổi tên và/hoặc nghĩa): gỡ index của bản cũ rồi gắn index bản mới.
     *
     * @throws NoSuchElementException nếu không có entry nào mang id đó
     */
    public void replace(SlangEntry updated) {
        Objects.requireNonNull(updated, "updated");
        SlangEntry old = entries.get(updated.getId());
        if (old == null) {
            throw new NoSuchElementException("No entry with id " + updated.getId());
        }
        unindex(old);
        entries.put(updated.getId(), updated);
        index(updated);
    }

    /** Xoá entry theo id; trả về entry đã xoá, {@code null} nếu không có. */
    public SlangEntry remove(int id) {
        SlangEntry removed = entries.remove(id);
        if (removed == null) {
            return null;
        }
        // swap-remove: đưa id cuối vào chỗ trống rồi bỏ phần tử cuối → O(1), không phải dời mảng
        int position = positionOf.remove(id);
        int lastId = idList.remove(idList.size() - 1);
        if (lastId != id) {
            idList.set(position, lastId);
            positionOf.put(lastId, position);
        }
        unindex(removed);
        return removed;
    }

    // ===================== Nội bộ =====================

    private void insert(SlangEntry entry) {
        entries.put(entry.getId(), entry);
        positionOf.put(entry.getId(), idList.size());
        idList.add(entry.getId());
        index(entry);
    }

    /** Gắn entry vào mọi index theo tên và theo từ. */
    private void index(SlangEntry entry) {
        int id = entry.getId();
        String name = Tokenizer.normalizeName(entry.getSlang());
        idsByName.computeIfAbsent(name, k -> new HashSet<>()).add(id);
        nameTrie.put(name, id);
        nameTree.add(name); // tên đã có thì bỏ qua; tên từng bị xoá mềm thì được khôi phục

        for (String word : wordsOf(entry)) {
            wordIndex.add(word, id);
            vocabularyTree.add(word);
        }
    }

    /** Ngược lại với {@link #index}: gỡ entry khỏi mọi index. */
    private void unindex(SlangEntry entry) {
        int id = entry.getId();
        String name = Tokenizer.normalizeName(entry.getSlang());
        Set<Integer> ids = idsByName.get(name);
        ids.remove(id);
        if (ids.isEmpty()) {
            // Không còn entry nào mang tên này → tên biến mất khỏi kết quả fuzzy
            idsByName.remove(name);
            nameTree.remove(name);
        }
        nameTrie.remove(name, id);

        for (String word : wordsOf(entry)) {
            wordIndex.remove(word, id);
            if (!wordIndex.containsKey(word)) {
                vocabularyTree.remove(word);
            }
        }
    }

    /** Các từ khác nhau trong mọi nghĩa của entry. */
    private static Set<String> wordsOf(SlangEntry entry) {
        Set<String> words = new LinkedHashSet<>();
        for (String meaning : entry.getMeanings()) {
            words.addAll(Tokenizer.tokens(meaning));
        }
        return words;
    }

    private static String normalizeWord(String word) {
        return word.strip().toLowerCase(Locale.ROOT);
    }
}
