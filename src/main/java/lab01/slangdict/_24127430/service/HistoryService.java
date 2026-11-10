package lab01.slangdict._24127430.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import lab01.slangdict._24127430.io.HistoryStore;
import lab01.slangdict._24127430.model.HistoryItem;

/**
 * Lịch sử tìm kiếm (F3): danh sách {@link HistoryItem}, <b>mới nhất ở đầu</b>, tối đa {@value #MAX_ITEMS} dòng.
 * <p>
 * Mỗi thay đổi (thêm, xóa dòng, xóa hết) làm 2 việc:
 * <ol>
 *   <li>lưu ngay xuống {@code history.dat} qua {@link HistoryStore#saveInBackground} (luồng nền, không đứng UI);</li>
 *   <li>báo cho các listener (mẫu Observer) để màn hình Lịch sử tự vẽ lại, dù đang mở hay không.</li>
 * </ol>
 * Gọi từ một luồng duy nhất (EDT): SearchPanel ghi lịch sử và HistoryPanel đọc/xóa đều trên EDT.
 */
public class HistoryService {

    /** Số dòng tối đa; quá thì bỏ dòng cũ nhất. */
    public static final int MAX_ITEMS = 1000;

    private final HistoryStore store;
    /** Mới nhất ở vị trí 0. */
    private final List<HistoryItem> items = new ArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /**
     * Đọc lịch sử cũ từ store. File hỏng thì bắt đầu với lịch sử rỗng (lịch sử không đáng để app không mở được);
     * lần lưu kế tiếp sẽ ghi đè file hỏng.
     */
    public HistoryService(HistoryStore store) {
        this.store = Objects.requireNonNull(store, "store");
        try {
            List<HistoryItem> loaded = store.load();
            items.addAll(loaded.subList(0, Math.min(loaded.size(), MAX_ITEMS)));
        } catch (IOException e) {
            System.err.println("[HistoryService] " + store.file() + " unreadable, starting with empty history: " + e);
        }
    }

    /** Danh sách hiện tại (chỉ đọc, mới nhất ở đầu). */
    public List<HistoryItem> items() {
        return Collections.unmodifiableList(items);
    }

    public int size() {
        return items.size();
    }

    public HistoryItem get(int index) {
        return items.get(index);
    }

    /**
     * Ghi một lần tìm vào đầu danh sách. Nếu trùng đúng lần tìm mới nhất (cùng loại, kiểu khớp và từ khóa,
     * không phân biệt hoa thường) thì chỉ cập nhật thời gian, tránh nhấn Enter nhiều lần ra nhiều dòng giống nhau.
     */
    public void add(HistoryItem item) {
        Objects.requireNonNull(item, "item");
        if (!items.isEmpty() && sameSearch(items.get(0), item)) {
            items.set(0, item);
        } else {
            items.add(0, item);
            if (items.size() > MAX_ITEMS) {
                items.subList(MAX_ITEMS, items.size()).clear(); // bỏ các dòng cũ nhất ở cuối
            }
        }
        changed();
    }

    static boolean sameSearch(HistoryItem a, HistoryItem b) {
        return a.target() == b.target() && a.mode() == b.mode() && a.query().equalsIgnoreCase(b.query());
    }

    /**
     * Xóa các dòng ở những vị trí đã cho (vị trí trong {@link #items()}), ví dụ những dòng đang chọn trên bảng.
     * Vị trí trùng hoặc ngoài phạm vi bị bỏ qua.
     */
    public void remove(int... indexes) {
        // Xóa từ vị trí lớn về nhỏ: xóa phần tử sau không làm lệch vị trí của phần tử trước
        int[] sorted = Arrays.stream(indexes).filter(i -> i >= 0 && i < items.size()).distinct().sorted().toArray();
        if (sorted.length == 0) {
            return;
        }
        for (int i = sorted.length - 1; i >= 0; i--) {
            items.remove(sorted[i]);
        }
        changed();
    }

    /** Xóa toàn bộ lịch sử (nút "Xóa tất cả", và Reset có chọn "xóa cả lịch sử" ở bước 11). */
    public void clear() {
        if (items.isEmpty()) {
            return;
        }
        items.clear();
        changed();
    }

    /** Listener được gọi (trên luồng vừa thay đổi lịch sử) sau mỗi lần lịch sử thay đổi. */
    public void addChangeListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    private void changed() {
        store.saveInBackground(List.copyOf(items));
        listeners.forEach(Runnable::run);
    }
}
