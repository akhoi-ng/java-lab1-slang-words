package lab01.slangdict._24127430.ui.panels;

import java.awt.Cursor;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import lab01.slangdict._24127430.core.SlangDictionary;
import lab01.slangdict._24127430.i18n.Messages;
import lab01.slangdict._24127430.service.DictionaryService;
import lab01.slangdict._24127430.service.HistoryService;
import lab01.slangdict._24127430.ui.LoadingWindow;
import lab01.slangdict._24127430.ui.MainFrame;
import lab01.slangdict._24127430.ui.dialogs.ResetDialog;

/**
 * Hành động F7 Reset: đưa từ điển về danh sách slang gốc.
 * <pre>
 * EDT:        ResetDialog (xác nhận + "xóa cả lịch sử") ── Hủy ──► thôi
 *               │ Khôi phục
 *             khóa cửa sổ (con trỏ chờ, "Đang khôi phục…" nếu lâu hơn 0,3 s)
 * luồng nền:  DictionaryService.loadOriginal()   ← đọc + index slang.txt gốc (7,6k: ~0,1 s; 100k: ~0,5–0,7 s)
 * EDT:        replaceDictionary(mới)  → lưu .dat nền, listener: thanh trạng thái, màn hình Slang hôm nay
 *             (nếu chọn) history.clear() → màn hình Lịch sử tự trống
 *             SearchPanel.showReload: tìm lại trên dữ liệu mới
 *             mở khóa cửa sổ, báo "Đã khôi phục N slang"
 * </pre>
 * Từ điển cũ không bị sửa trong lúc build, nên lỗi đọc file gốc (thiếu file, sai định dạng) không làm mất gì:
 * chỉ báo lỗi, từ điển giữ nguyên. Cửa sổ bị khóa trong lúc build để người dùng không kịp mở dialog Sửa/Xóa trên
 * từ điển sắp bị thay.
 * <p>
 * Phần giao diện đi qua interface {@link Ui}; test thay bằng bản giả và chạy cả hai "luồng" ngay trên luồng gọi.
 */
public final class ResetAction implements Runnable {

    /** Những chỗ cần đến giao diện. */
    interface Ui {
        /** Dialog xác nhận; rỗng nếu hủy, ngược lại là có xóa cả lịch sử không. */
        Optional<Boolean> confirm(int dictionarySize, int historySize, String source);

        /** Khóa/mở khóa cửa sổ trong lúc build từ điển gốc. */
        void setBusy(boolean busy);

        /** Reset xong: {@code size} slang, build mất {@code elapsedMillis} ms. */
        void showDone(int size, long elapsedMillis, boolean historyCleared);

        /** Không đọc được file gốc; từ điển giữ nguyên. */
        void showError(String message);
    }

    private final DictionaryService service;
    private final HistoryService history;
    private final SearchPanel searchPanel;
    private final Ui ui;
    private final Executor worker;
    private final Executor edt;
    /** Đang reset (chặn bấm Reset lần nữa qua phím tắt trước khi cửa sổ kịp khóa). */
    private boolean running;

    public ResetAction(DictionaryService service, HistoryService history, SearchPanel searchPanel, MainFrame frame) {
        this(service, history, searchPanel, new SwingUi(frame), ResetAction::startThread, SwingUtilities::invokeLater);
    }

    /**
     * @param worker chạy phần build từ điển (app: một luồng nền mới; test: {@code Runnable::run})
     * @param edt    đưa kết quả về EDT (app: {@code SwingUtilities::invokeLater}; test: {@code Runnable::run})
     */
    ResetAction(DictionaryService service, HistoryService history, SearchPanel searchPanel, Ui ui,
                Executor worker, Executor edt) {
        this.service = Objects.requireNonNull(service, "service");
        this.history = Objects.requireNonNull(history, "history");
        this.searchPanel = Objects.requireNonNull(searchPanel, "searchPanel");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.edt = Objects.requireNonNull(edt, "edt");
    }

    private static void startThread(Runnable task) {
        Thread thread = new Thread(task, "reset");
        thread.setDaemon(true);
        thread.start();
    }

    /** Gọi trên EDT (Sidebar, menu Tệp/Chức năng, Ctrl+7). */
    @Override
    public void run() {
        if (running) {
            return;
        }
        Optional<Boolean> choice = ui.confirm(service.size(), history.size(), service.originalSource());
        if (choice.isEmpty()) {
            return;
        }
        boolean clearHistory = choice.get();
        running = true;
        ui.setBusy(true);
        worker.execute(() -> {
            long start = System.nanoTime();
            try {
                SlangDictionary fresh = service.loadOriginal();
                long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                edt.execute(() -> finish(fresh, elapsed, clearHistory));
            } catch (Exception e) { // IOException, SlangFormatException, hay lỗi bất ngờ khi build
                edt.execute(() -> fail(e));
            }
        });
    }

    /** Trên EDT: thay từ điển, xóa lịch sử nếu được chọn, cập nhật các màn hình. */
    private void finish(SlangDictionary fresh, long elapsedMillis, boolean clearHistory) {
        try {
            service.replaceDictionary(fresh);
            if (clearHistory) {
                history.clear();
            }
            searchPanel.showReload("notice.reset", service.size());
        } finally {
            running = false;
            ui.setBusy(false);
        }
        ui.showDone(service.size(), elapsedMillis, clearHistory);
    }

    private void fail(Exception error) {
        System.err.println("[ResetAction] cannot load the original dictionary: " + error);
        running = false;
        ui.setBusy(false);
        ui.showError(Messages.format("reset.failed", String.valueOf(error.getMessage())));
    }

    // ===================== Giao diện thật =====================

    private static final class SwingUi implements Ui {

        private final MainFrame frame;
        private LoadingWindow loading;

        SwingUi(MainFrame frame) {
            this.frame = Objects.requireNonNull(frame, "frame");
        }

        @Override
        public Optional<Boolean> confirm(int dictionarySize, int historySize, String source) {
            return ResetDialog.show(frame, dictionarySize, historySize, source);
        }

        @Override
        public void setBusy(boolean busy) {
            if (busy) {
                // Cửa sổ bị tắt thì không nhận chuột, phím, kể cả phím tắt menu
                frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                frame.setEnabled(false);
                loading = new LoadingWindow("reset.loading");
                loading.showDelayed(); // chỉ hiện nếu lâu hơn 0,3 s (file 100k), 7,6k slang thì không nháy lên
            } else {
                if (loading != null) {
                    loading.close();
                    loading = null;
                }
                frame.setEnabled(true);
                frame.setCursor(Cursor.getDefaultCursor());
                frame.toFront(); // Windows không tự trả focus cho cửa sổ vừa được bật lại
            }
        }

        @Override
        public void showDone(int size, long elapsedMillis, boolean historyCleared) {
            frame.showDictionaryReset(size, elapsedMillis);
            String message = Messages.format("reset.done", size, elapsedMillis);
            if (historyCleared) {
                message += "\n" + Messages.get("reset.done.historyCleared");
            }
            JOptionPane.showMessageDialog(frame, message, Messages.get("reset.title"),
                    JOptionPane.INFORMATION_MESSAGE);
        }

        @Override
        public void showError(String message) {
            JOptionPane.showMessageDialog(frame, message, Messages.get("error.title"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
