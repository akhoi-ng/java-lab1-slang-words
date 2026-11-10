package lab01.slangdict._24127430;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import lab01.slangdict._24127430.i18n.Messages;
import lab01.slangdict._24127430.io.DictionaryStore;
import lab01.slangdict._24127430.io.HistoryStore;
import lab01.slangdict._24127430.io.SettingsStore;
import lab01.slangdict._24127430.model.QuizType;
import lab01.slangdict._24127430.model.SearchMode;
import lab01.slangdict._24127430.model.SearchTarget;
import lab01.slangdict._24127430.service.DictionaryService;
import lab01.slangdict._24127430.service.HistoryService;
import lab01.slangdict._24127430.service.QuizService;
import lab01.slangdict._24127430.service.RandomService;
import lab01.slangdict._24127430.service.SearchService;
import lab01.slangdict._24127430.ui.Feature;
import lab01.slangdict._24127430.ui.LoadingWindow;
import lab01.slangdict._24127430.ui.MainFrame;
import lab01.slangdict._24127430.ui.SwingLocalizer;
import lab01.slangdict._24127430.ui.ThemeManager;
import lab01.slangdict._24127430.ui.panels.EntryActions;
import lab01.slangdict._24127430.ui.panels.HistoryPanel;
import lab01.slangdict._24127430.ui.panels.QuizPanel;
import lab01.slangdict._24127430.ui.panels.RandomPanel;
import lab01.slangdict._24127430.ui.panels.ResetAction;
import lab01.slangdict._24127430.ui.panels.SearchPanel;

/**
 * Điểm khởi động ứng dụng Slang Dictionary.
 * <pre>
 * main ──► đọc settings (ngôn ngữ, theme) ──► EDT: cài ngôn ngữ + theme, hẹn giờ hiện "Đang tải…"
 *                                              │
 *            SwingWorker (luồng nền): DictionaryStore.loadOrBuild()  ← đọc .dat hoặc build index
 *                                              │ xong
 *                                     EDT: đóng "Đang tải…", hiện MainFrame
 * </pre>
 * Việc tải chạy trên luồng nền để cửa sổ "Đang tải…" vẫn vẽ được (EDT không bị chặn).
 * Khi thoát: ẩn cửa sổ ngay, chờ luồng ghi nền lưu xong .dat rồi mới tắt JVM.
 */
public final class App {

    public static final String VERSION = "1.0.0";

    private static MainFrame mainFrame;
    /** Lỗi ghi file xảy ra trước khi MainFrame kịp hiện (ví dụ lúc lưu .dat lần đầu). */
    private static final List<Runnable> pendingSaveErrors = new ArrayList<>();

    private App() {
    }

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler(App::handleUncaught);

        SettingsStore settings;
        DictionaryStore store;
        HistoryStore historyStore;
        HistoryService history;
        try {
            settings = SettingsStore.createDefault();
            store = DictionaryStore.createDefault();
            historyStore = HistoryStore.createDefault();
            history = new HistoryService(historyStore); // file nhỏ, đọc ngay trên luồng main
        } catch (RuntimeException e) {
            // Ví dụ không tạo được thư mục data/ (ổ đĩa chỉ đọc)
            fatal(e);
            return;
        }
        // Lỗi ghi file đến từ luồng nền → chuyển về EDT rồi mới đụng vào giao diện
        store.setErrorListener(error -> SwingUtilities.invokeLater(() -> reportSaveError("error.saveFailed", error)));
        historyStore.setErrorListener(
                error -> SwingUtilities.invokeLater(() -> reportSaveError("error.historySaveFailed", error)));
        // Lưới an toàn khi JVM bị tắt theo cách khác (Ctrl+C ở console): vẫn chờ ghi xong
        Runtime.getRuntime().addShutdownHook(new Thread(() -> closeAll(store, historyStore), "data-flush"));

        SwingUtilities.invokeLater(() -> start(settings, store, historyStore, history));
    }

    /** Chạy trên EDT. */
    private static void start(SettingsStore settings, DictionaryStore store, HistoryStore historyStore,
                              HistoryService history) {
        Messages.setLocale(Messages.resolve(settings.language(Messages.DEFAULT_LOCALE)));
        ThemeManager.install(settings.theme());
        SwingLocalizer.install();

        LoadingWindow loading = new LoadingWindow();
        loading.showDelayed();

        new SwingWorker<DictionaryStore.LoadResult, Void>() {
            @Override
            protected DictionaryStore.LoadResult doInBackground() throws Exception {
                return store.loadOrBuild(); // luồng nền
            }

            @Override
            protected void done() { // trở lại EDT
                loading.close();
                DictionaryStore.LoadResult loaded;
                try {
                    loaded = get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fatal(e);
                    return;
                } catch (ExecutionException e) {
                    fatal(e.getCause());
                    return;
                }
                System.out.printf("[App] Loaded %,d slang words (%s) in %d ms%n",
                        loaded.dictionary().size(), loaded.source(), loaded.elapsedMillis());
                mainFrame = new MainFrame(settings, loaded, store.dictionaryFile().getParent(), VERSION,
                        () -> exit(store, historyStore));
                installScreens(mainFrame, new DictionaryService(loaded.dictionary(), store), history);
                mainFrame.setVisible(true);
                pendingSaveErrors.forEach(Runnable::run);
                pendingSaveErrors.clear();
            }
        }.execute();
    }

    /** Gắn các màn hình thật vào MainFrame thay cho màn hình tạm (mỗi bước 8–12 thêm một phần). */
    private static void installScreens(MainFrame frame, DictionaryService dictionary, HistoryService history) {
        // F1, F2. Lấy từ điển qua dictionary::current chứ không giữ cứng một object, vì Reset (F7) thay object mới
        SearchService searchService = new SearchService(dictionary::current);
        SearchPanel searchPanel = new SearchPanel(searchService, frame::showFeature);
        searchPanel.setSearchListener(history::add); // mỗi lần tìm "thật" (Enter/nút Tìm/gợi ý) → một dòng lịch sử
        frame.setScreen(Feature.Card.SEARCH, searchPanel);

        // F3: bấm đúp một dòng → sang màn hình Search đúng F1/F2 và tìm lại
        HistoryPanel historyPanel = new HistoryPanel(history,
                item -> searchPanel.search(item.target(), item.mode(), item.query()));
        frame.setScreen(Feature.Card.HISTORY, historyPanel);

        // F4, F5, F6: dialog thêm/sửa, xác nhận xóa; thao tác trên slang đang chọn ở màn hình Search
        EntryActions entryActions = new EntryActions(dictionary, searchPanel, frame::showFeature);
        frame.setAction(Feature.ADD, entryActions::add);
        frame.setAction(Feature.EDIT, entryActions::edit);
        frame.setAction(Feature.DELETE, entryActions::delete);
        dictionary.addChangeListener(() -> frame.showDictionarySize(dictionary.size()));

        // F7: xác nhận (có "xóa cả lịch sử") → build lại từ slang.txt gốc trên luồng nền → thay từ điển hiện tại
        frame.setAction(Feature.RESET, new ResetAction(dictionary, history, searchPanel, frame));

        // F8: slang hôm nay + random khác; "Tra trong từ điển" = tìm EXACT theo slang ở màn hình Search
        RandomPanel randomPanel = new RandomPanel(new RandomService(dictionary::current),
                slang -> searchPanel.search(SearchTarget.SLANG, SearchMode.EXACT, slang));
        dictionary.addChangeListener(randomPanel::dictionaryChanged);
        frame.setScreen(Feature.Card.RANDOM, randomPanel);

        // F9, F10: hai màn hình đố vui dùng chung một QuizService; từ điển đổi thì câu chưa trả lời được kiểm tra lại
        QuizService quizService = new QuizService(dictionary::current);
        QuizPanel slangQuiz = new QuizPanel(QuizType.SLANG_TO_DEFINITION, quizService);
        QuizPanel definitionQuiz = new QuizPanel(QuizType.DEFINITION_TO_SLANG, quizService);
        dictionary.addChangeListener(slangQuiz::dictionaryChanged);
        dictionary.addChangeListener(definitionQuiz::dictionaryChanged);
        frame.setScreen(Feature.Card.QUIZ_SLANG, slangQuiz);
        frame.setScreen(Feature.Card.QUIZ_DEFINITION, definitionQuiz);
    }

    /** Chạy trên EDT. {@code dialogKey}: chữ của dialog cảnh báo, khác nhau cho từ điển và lịch sử. */
    private static void reportSaveError(String dialogKey, IOException error) {
        System.err.println("[App] save failed: " + error);
        if (mainFrame != null) {
            mainFrame.showSaveError(dialogKey, error);
        } else {
            pendingSaveErrors.add(() -> mainFrame.showSaveError(dialogKey, error));
        }
    }

    /** Ẩn cửa sổ ngay để người dùng không phải chờ, phần chờ ghi file chạy ở luồng riêng. */
    private static void exit(DictionaryStore store, HistoryStore historyStore) {
        if (mainFrame != null) {
            mainFrame.dispose();
        }
        Thread exiter = new Thread(() -> {
            closeAll(store, historyStore);
            System.exit(0);
        }, "app-exit");
        exiter.start();
    }

    /** Chờ lần ghi .dat và history.dat cuối cùng rồi dừng các luồng ghi. Gọi nhiều lần cũng không sao. */
    private static void closeAll(DictionaryStore store, HistoryStore historyStore) {
        historyStore.close();
        store.close();
    }

    /** Lỗi không thể tiếp tục (không tải được dữ liệu): báo rồi thoát. */
    private static void fatal(Throwable error) {
        error.printStackTrace();
        String message = Messages.format("error.load", String.valueOf(error.getMessage()));
        showError(message);
        System.exit(1);
    }

    /** Exception không ai bắt (thường là bug trên EDT): báo cho người dùng thay vì im lặng. */
    private static void handleUncaught(Thread thread, Throwable error) {
        error.printStackTrace();
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        String firstLines = trace.toString().lines().limit(6).reduce((a, b) -> a + "\n" + b).orElse("");
        SwingUtilities.invokeLater(() -> {
            if (showingUncaughtError) {
                return; // lỗi xảy ra ngay trong lúc đang báo lỗi → không mở dialog chồng dialog
            }
            showingUncaughtError = true;
            try {
                showError(Messages.format("error.unexpected", firstLines));
            } finally {
                showingUncaughtError = false;
            }
        });
    }

    private static boolean showingUncaughtError;

    private static void showError(String message) {
        Runnable show = () -> JOptionPane.showMessageDialog(mainFrame, message, Messages.get("error.title"),
                JOptionPane.ERROR_MESSAGE);
        if (SwingUtilities.isEventDispatchThread()) {
            show.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(show);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.lang.reflect.InvocationTargetException | java.awt.HeadlessException e) {
            // Không hiện được dialog (không có màn hình): lỗi đã được in ra stderr
        }
    }
}
