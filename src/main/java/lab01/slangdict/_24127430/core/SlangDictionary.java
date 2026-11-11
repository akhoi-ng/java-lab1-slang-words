package lab01.slangdict._24127430.core;

import java.io.IOException;
import java.io.ObjectOutputStream;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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
 * <b>Luồng:</b> đọc và sửa trên một luồng duy nhất (EDT). Ngoại lệ là luồng ghi nền của DictionaryStore: nó gọi
 * {@link #snapshot} để chép từ điển rồi serialize bản chép ở ngoài khóa. Để bản chép luôn nhất quán,
 * {@link #snapshot}, {@link #writeObject} và ba method sửa dữ liệu cùng {@code synchronized} trên chính object này
 * (giống cách {@code java.util.Vector} làm): đang chép thì việc sửa phải chờ (vài chục ms), và ngược lại.
 * Ai đọc từ một luồng khác EDT (ví dụ {@code SearchService} chạy trên luồng tìm kiếm của màn hình Search) thì
 * phải đọc bên trong {@code synchronized (dictionary)} để không gặp lúc EDT đang sửa dở.
 */
public class SlangDictionary implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<Integer, SlangEntry> entries = new HashMap<>();

    private final List<Integer> idList = new ArrayList<>();
    private final Map<Integer, Integer> positionOf = new HashMap<>();

    private final Map<String, Set<Integer>> idsByName = new HashMap<>();
    private final Trie<Integer> nameTrie;
    private final BKTree<String> nameTree;

    private final InvertedIndex<String, Integer> wordIndex;
    private final BKTree<String> vocabularyTree;

    private int nextId;

    /** Từ điển rỗng. */
    public SlangDictionary() {
        this(new Trie<>(), new BKTree<>(Levenshtein.INSTANCE), new InvertedIndex<>(),
                new BKTree<>(Levenshtein.INSTANCE));
    }

    private SlangDictionary(Trie<Integer> nameTrie, BKTree<String> nameTree, InvertedIndex<String, Integer> wordIndex,
                            BKTree<String> vocabularyTree) {
        this.nameTrie = nameTrie;
        this.nameTree = nameTree;
        this.wordIndex = wordIndex;
        this.vocabularyTree = vocabularyTree;
    }

    /**
     * Bản sao độc lập của cả từ điển (entry và mọi index, giữ đúng hình dạng cây), dùng cho luồng ghi .dat.
     * <p>
     * Chỉ giữ khóa trong lúc chép cấu trúc (100k slang: vài chục ms, không tính lại Levenshtein hay tách từ nào),
     * rồi luồng ghi serialize bản sao ở ngoài khóa. Trước đây luồng ghi serialize thẳng object này trong khóa
     * (100k: ~1–3 s): mọi lần tìm và add/edit/delete rơi vào lúc đó phải đứng chờ.
     */
    public synchronized SlangDictionary snapshot() {
        SlangDictionary copy = new SlangDictionary(nameTrie.copy(), nameTree.copy(), wordIndex.copy(),
                vocabularyTree.copy());
        copy.entries.putAll(entries); // SlangEntry bất biến nên dùng chung được
        copy.idList.addAll(idList);
        copy.positionOf.putAll(positionOf);
        for (Map.Entry<String, Set<Integer>> name : idsByName.entrySet()) {
            copy.idsByName.put(name.getKey(), new HashSet<>(name.getValue()));
        }
        copy.nextId = nextId;
        return copy;
    }

    /**
     * Build từ danh sách entry (thường là kết quả của SlangFileParser), giữ nguyên id của từng entry.
     *
     * @throws IllegalArgumentException nếu có hai entry trùng id
     */
    public static SlangDictionary build(List<SlangEntry> source) {
        SlangDictionary dictionary = new SlangDictionary();
        // Ba nhóm index không dùng chung field nào nên được dựng song song (100k slang: ~1,1 s tuần tự → ~0,5 s):
        //   luồng gọi:  entries, idList, positionOf, idsByName, nameTrie
        //   luồng 2:    BK-tree tên (phần tốn nhất: mỗi tên một chuỗi phép Levenshtein)
        //   luồng 3:    wordIndex + BK-tree từ vựng (tách từ mọi definition)
        // Mỗi luồng chèn đúng thứ tự như khi dựng tuần tự, nên kết quả giống hệt. join() ở cuối đảm bảo luồng gọi
        // thấy mọi thứ các luồng kia đã ghi (happens-before).
        Set<String> names = new LinkedHashSet<>();
        for (SlangEntry entry : source) {
            names.add(Tokenizer.normalizeName(entry.getSlang()));
        }
        CompletableFuture<Void> nameTreeTask = CompletableFuture.runAsync(() -> names.forEach(dictionary.nameTree::add));
        CompletableFuture<Void> wordTask = CompletableFuture.runAsync(() -> source.forEach(dictionary::indexWords));
        try {
            for (SlangEntry entry : source) {
                if (dictionary.entries.containsKey(entry.getId())) {
                    throw new IllegalArgumentException("Duplicate id " + entry.getId());
                }
                dictionary.register(entry);
                dictionary.indexName(entry, false);
                dictionary.nextId = Math.max(dictionary.nextId, entry.getId() + 1);
            }
        } finally {
            try {
                CompletableFuture.allOf(nameTreeTask, wordTask).join();
            } catch (CompletionException e) {
                if (e.getCause() instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (e.getCause() instanceof Error error) {
                    throw error;
                }
                throw e;
            }
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
    public synchronized SlangEntry add(String slang, List<String> meanings) {
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
    public synchronized void replace(SlangEntry updated) {
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
    public synchronized SlangEntry remove(int id) {
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

    /** Serialize như mặc định nhưng giữ khoá, để không có thao tác sửa nào chen vào giữa chừng. */
    private synchronized void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }

    private void insert(SlangEntry entry) {
        register(entry);
        index(entry);
    }

    /** Thêm vào entries, idList, positionOf (chưa gắn index). */
    private void register(SlangEntry entry) {
        entries.put(entry.getId(), entry);
        positionOf.put(entry.getId(), idList.size());
        idList.add(entry.getId());
    }

    /** Gắn entry vào mọi index theo tên và theo từ. */
    private void index(SlangEntry entry) {
        indexName(entry, true);
        indexWords(entry);
    }

    /** @param withNameTree false khi {@link #build} đã dựng BK-tree tên trên luồng khác */
    private void indexName(SlangEntry entry, boolean withNameTree) {
        int id = entry.getId();
        String name = Tokenizer.normalizeName(entry.getSlang());
        Set<Integer> sameName = idsByName.get(name);
        if (sameName == null) {
            sameName = new HashSet<>();
            idsByName.put(name, sameName);
            if (withNameTree) {
                nameTree.add(name); // tên mới (tên từng bị xoá mềm thì được khôi phục)
            }
        }
        sameName.add(id);
        nameTrie.put(name, id);
    }

    private void indexWords(SlangEntry entry) {
        int id = entry.getId();
        for (String word : wordsOf(entry)) {
            // Chỉ đưa vào BK-tree khi từ lần đầu xuất hiện: từ đã có thì add chỉ đi một vòng Levenshtein vô ích.
            // 100k slang có hàng trăm nghìn lần xuất hiện nhưng chỉ vài nghìn từ khác nhau (~0,5 s → ~10 ms).
            if (!wordIndex.containsKey(word)) {
                vocabularyTree.add(word);
            }
            wordIndex.add(word, id);
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
