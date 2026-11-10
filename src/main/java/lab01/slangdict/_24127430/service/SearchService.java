package lab01.slangdict._24127430.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import lab01.slangdict._24127430.core.SlangDictionary;
import lab01.slangdict._24127430.core.Tokenizer;
import lab01.slangdict._24127430.ds.BKTree;
import lab01.slangdict._24127430.model.SearchMode;
import lab01.slangdict._24127430.model.SearchResult;
import lab01.slangdict._24127430.model.SearchTarget;
import lab01.slangdict._24127430.model.SlangEntry;

/**
 * Tìm theo tên slang (F1) hoặc theo definition (F2) với 4 mode EXACT, PREFIX, SUBSTRING, FUZZY.
 *
 * <pre>
 *                 SLANG (tên)                          DEFINITION (từng từ của query)
 * EXACT           HashMap idsByName                    InvertedIndex: từ → id
 * PREFIX          Trie                                 khoảng [p, p + MAX_VALUE) của TreeMap từ vựng
 * SUBSTRING       duyệt tập tên khác nhau              duyệt tập từ vựng khác nhau, rồi tra InvertedIndex
 * FUZZY           BK-tree trên tên                     BK-tree trên từ vựng, rồi tra InvertedIndex
 * </pre>
 *
 * Definition nhiều từ: mỗi từ cho một tập id, lấy <b>giao</b> (AND) các tập, bắt đầu từ tập nhỏ nhất.
 * Kết quả xếp hạng theo điểm (xem các hằng SCORE_*), sắp xếp bằng {@link SearchResult#compareTo}.
 * <p>
 * Service không giữ cứng một object từ điển mà lấy qua {@link Supplier} mỗi lần tìm,
 * vì Reset (F7) thay toàn bộ từ điển bằng một object mới.
 */
public class SearchService {

    /** Truyền vào {@code limit} để lấy toàn bộ kết quả. */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    // ----- điểm khi tìm theo tên slang -----
    /** Tên trùng đúng query (mọi mode). */
    static final int SCORE_NAME_EXACT = 100;
    /** Tên bắt đầu bằng query. */
    static final int SCORE_NAME_PREFIX = 50;
    /** Tên chứa query ở giữa. */
    static final int SCORE_NAME_INSIDE = 20;
    /** FUZZY: điểm = 100 − 10 × khoảng cách. */
    static final int PENALTY_NAME_PER_EDIT = 10;

    // ----- điểm khi tìm theo definition -----
    /** Một nghĩa chứa nguyên cụm query (query từ 2 từ trở lên). */
    static final int SCORE_PHRASE = 100;
    /** Mỗi từ của query có trong definition đúng nguyên từ. */
    static final int SCORE_WORD_EXACT = 10;
    /** Mỗi từ của query chỉ khớp một phần (PREFIX/SUBSTRING), ví dụ "laugh" khớp "laughing". */
    static final int SCORE_WORD_PARTIAL = 5;
    /** FUZZY: điểm của từ = 10 − 3 × khoảng cách. */
    static final int PENALTY_WORD_PER_EDIT = 3;

    private final Supplier<SlangDictionary> dictionarySource;

    /** @param dictionarySource trả về từ điển hiện tại (sau Reset sẽ là object mới) */
    public SearchService(Supplier<SlangDictionary> dictionarySource) {
        this.dictionarySource = Objects.requireNonNull(dictionarySource, "dictionarySource");
    }

    /**
     * @param query chuỗi người dùng gõ; null hoặc rỗng thì trả kết quả rỗng
     * @param limit số kết quả tối đa trả về (≥ 1); {@link #UNLIMITED} để lấy hết
     */
    public SearchOutcome search(SearchTarget target, SearchMode mode, String query, int limit) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(mode, "mode");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1: " + limit);
        }
        long start = System.nanoTime();
        SlangDictionary dictionary = Objects.requireNonNull(dictionarySource.get(), "dictionary");

        Map<Integer, Integer> scoreById = target == SearchTarget.SLANG
                ? searchNames(dictionary, mode, query == null ? "" : query)
                : searchDefinitions(dictionary, mode, query == null ? "" : query);

        List<SearchResult> results = new ArrayList<>(scoreById.size());
        for (Map.Entry<Integer, Integer> match : scoreById.entrySet()) {
            results.add(new SearchResult(dictionary.get(match.getKey()), match.getValue()));
        }
        Collections.sort(results); // điểm cao trước, cùng điểm thì theo tên, rồi theo id
        int total = results.size();
        List<SearchResult> page = total > limit ? results.subList(0, limit) : results;
        return new SearchOutcome(page, total, System.nanoTime() - start);
    }

    // ===================== SLANG =====================

    private static Map<Integer, Integer> searchNames(SlangDictionary dictionary, SearchMode mode, String query) {
        String q = Tokenizer.normalizeName(query);
        Map<Integer, Integer> scores = new HashMap<>();
        if (q.isEmpty()) {
            return scores;
        }
        switch (mode) {
            case EXACT -> addAll(scores, dictionary.idsByName(q), SCORE_NAME_EXACT);
            case PREFIX -> {
                // Lấy HẾT từ Trie (không truyền limit) để totalMatches đúng; cắt sau khi xếp hạng
                for (int id : dictionary.idsWithNamePrefix(q, UNLIMITED)) {
                    String name = Tokenizer.normalizeName(dictionary.get(id).getSlang());
                    scores.put(id, containmentScore(name, q));
                }
            }
            case SUBSTRING -> {
                // Duyệt các tên KHÁC NHAU (ít hơn số entry), mỗi tên khớp mới tra ra các id
                for (String name : dictionary.names()) {
                    int score = containmentScore(name, q);
                    if (score > 0) {
                        addAll(scores, dictionary.idsByName(name), score);
                    }
                }
            }
            case FUZZY -> {
                for (BKTree.Match<String> match : dictionary.fuzzyNames(q, nameFuzzyDistance(q))) {
                    int score = SCORE_NAME_EXACT - PENALTY_NAME_PER_EDIT * match.distance();
                    addAll(scores, dictionary.idsByName(match.item()), score);
                }
            }
        }
        return scores;
    }

    /** 100 nếu trùng đúng, 50 nếu bắt đầu bằng query, 20 nếu chứa ở giữa, 0 nếu không chứa. */
    static int containmentScore(String name, String query) {
        if (name.equals(query)) {
            return SCORE_NAME_EXACT;
        }
        if (name.startsWith(query)) {
            return SCORE_NAME_PREFIX;
        }
        return name.contains(query) ? SCORE_NAME_INSIDE : 0;
    }

    /** Số lỗi gõ cho phép khi tìm tên: tên ngắn (≤ 4 ký tự) chỉ cho sai 1, dài hơn cho sai 2. */
    static int nameFuzzyDistance(String normalizedQuery) {
        return normalizedQuery.length() <= 4 ? 1 : 2;
    }

    // ===================== DEFINITION =====================

    private static Map<Integer, Integer> searchDefinitions(SlangDictionary dictionary, SearchMode mode,
            String query) {
        List<String> queryTokens = Tokenizer.tokens(query); // giữ thứ tự để so khớp cả cụm
        Set<String> distinctTokens = new LinkedHashSet<>(queryTokens); // "love love" chỉ tính "love" một lần
        if (distinctTokens.isEmpty()) {
            return new HashMap<>();
        }

        // 1) Mỗi từ của query → (id → điểm của từ đó)
        List<Map<Integer, Integer>> perToken = new ArrayList<>(distinctTokens.size());
        for (String token : distinctTokens) {
            Map<Integer, Integer> matches = matchWord(dictionary, mode, token);
            if (matches.isEmpty()) {
                return new HashMap<>(); // AND: một từ không khớp gì thì cả query không có kết quả
            }
            perToken.add(matches);
        }

        // 2) Giao các tập, bắt đầu từ tập NHỎ NHẤT: số phần tử phải xét không bao giờ vượt quá tập nhỏ nhất
        perToken.sort(Comparator.comparingInt(Map::size));
        Map<Integer, Integer> scores = new HashMap<>(perToken.get(0));
        for (int i = 1; i < perToken.size() && !scores.isEmpty(); i++) {
            Map<Integer, Integer> other = perToken.get(i);
            Iterator<Map.Entry<Integer, Integer>> it = scores.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Integer> candidate = it.next();
                Integer tokenScore = other.get(candidate.getKey());
                if (tokenScore == null) {
                    it.remove();
                } else {
                    candidate.setValue(candidate.getValue() + tokenScore);
                }
            }
        }

        // 3) Thưởng cho entry có nghĩa chứa nguyên cụm query (chỉ có ý nghĩa khi query từ 2 từ trở lên)
        if (queryTokens.size() >= 2) {
            for (Map.Entry<Integer, Integer> candidate : scores.entrySet()) {
                if (containsPhrase(dictionary.get(candidate.getKey()), queryTokens)) {
                    candidate.setValue(candidate.getValue() + SCORE_PHRASE);
                }
            }
        }
        return scores;
    }

    /** Các entry có definition khớp một từ của query theo mode, kèm điểm của từ đó. */
    private static Map<Integer, Integer> matchWord(SlangDictionary dictionary, SearchMode mode, String token) {
        Map<Integer, Integer> scores = new HashMap<>();
        switch (mode) {
            case EXACT -> addAll(scores, dictionary.idsWithWord(token), SCORE_WORD_EXACT);
            case PREFIX -> {
                for (String word : dictionary.wordsWithPrefix(token)) {
                    addAll(scores, dictionary.idsWithWord(word), wordScore(word, token));
                }
            }
            case SUBSTRING -> {
                // Duyệt TỪ VỰNG (các từ khác nhau), không duyệt từng definition
                for (String word : dictionary.vocabulary()) {
                    if (word.contains(token)) {
                        addAll(scores, dictionary.idsWithWord(word), wordScore(word, token));
                    }
                }
            }
            case FUZZY -> {
                for (BKTree.Match<String> match : dictionary.fuzzyWords(token, wordFuzzyDistance(token))) {
                    int score = SCORE_WORD_EXACT - PENALTY_WORD_PER_EDIT * match.distance();
                    addAll(scores, dictionary.idsWithWord(match.item()), score);
                }
            }
        }
        return scores;
    }

    private static int wordScore(String word, String token) {
        return word.equals(token) ? SCORE_WORD_EXACT : SCORE_WORD_PARTIAL;
    }

    /**
     * Số lỗi gõ cho phép với một từ trong definition: ≤ 2 ký tự thì phải đúng hẳn
     * (nếu không "a" sẽ khớp mọi từ 1–2 chữ), ≤ 4 ký tự cho sai 1, dài hơn cho sai 2.
     */
    static int wordFuzzyDistance(String token) {
        if (token.length() <= 2) {
            return 0;
        }
        return token.length() <= 4 ? 1 : 2;
    }

    /** Có nghĩa nào chứa dãy từ {@code phrase} liên tiếp không (bỏ qua dấu câu và hoa thường). */
    static boolean containsPhrase(SlangEntry entry, List<String> phrase) {
        String firstWord = phrase.get(0);
        for (String meaning : entry.getMeanings()) {
            // Lọc nhanh bằng contains trước khi tách từ (tách từ bằng regex tốn hơn nhiều)
            if (meaning.toLowerCase(Locale.ROOT).contains(firstWord)
                    && Collections.indexOfSubList(Tokenizer.tokens(meaning), phrase) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** Gán điểm cho các id; id đã có điểm thì giữ điểm cao hơn. */
    private static void addAll(Map<Integer, Integer> scores, Collection<Integer> ids, int score) {
        for (int id : ids) {
            scores.merge(id, score, Math::max);
        }
    }
}
