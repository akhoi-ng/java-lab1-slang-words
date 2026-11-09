package lab01.slangdict._24127430.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Một mục slang: id duy nhất, tên slang và danh sách nghĩa. Nhiều entry có thể trùng tên slang.
 * <p>
 * Bất biến (immutable): muốn sửa thì tạo bản mới bằng {@link #withSlang} / {@link #withMeanings}
 * rồi đưa cho SlangDictionary thay thế, nhờ đó các index luôn được cập nhật đồng bộ.
 */
public final class SlangEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int id;
    private final String slang;
    private final List<String> meanings;

    public SlangEntry(int id, String slang, List<String> meanings) {
        this.id = id;
        this.slang = requireText(slang, "slang");
        Objects.requireNonNull(meanings, "meanings");

        List<String> cleaned = new ArrayList<>(meanings.size());
        for (String meaning : meanings) {
            cleaned.add(requireText(meaning, "meaning"));
        }
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("A slang word needs at least one meaning");
        }
        this.meanings = Collections.unmodifiableList(cleaned);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }

    public int getId() {
        return id;
    }

    public String getSlang() {
        return slang;
    }

    /** Danh sách nghĩa, chỉ đọc. */
    public List<String> getMeanings() {
        return meanings;
    }

    /** Các nghĩa nối lại để hiển thị, ví dụ "Dollar | money". */
    public String getMeaningText() {
        return String.join(" | ", meanings);
    }

    public SlangEntry withSlang(String newSlang) {
        return new SlangEntry(id, newSlang, meanings);
    }

    public SlangEntry withMeanings(List<String> newMeanings) {
        return new SlangEntry(id, slang, newMeanings);
    }

    /** Hai entry bằng nhau khi cùng id (cùng một mục trong từ điển). */
    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof SlangEntry other && id == other.id);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return "#" + id + " " + slang + " = " + getMeaningText();
    }
}
