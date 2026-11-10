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
import lab01.slangdict._24127430.io.SettingsStore;
import lab01.slangdict._24127430.service.SearchService;
import lab01.slangdict._24127430.ui.Feature;
import lab01.slangdict._24127430.ui.LoadingWindow;
import lab01.slangdict._24127430.ui.MainFrame;
import lab01.slangdict._24127430.ui.SwingLocalizer;
import lab01.slangdict._24127430.ui.ThemeManager;
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
    private static final List<IOException> pendingSaveErrors = new ArrayList<>();

    private App() {
    }

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler(App::handleUncaught);

        SettingsStore settings;
        DictionaryStore store;
        try {
            settings = SettingsStore.createDefault();
            store = DictionaryStore.createDefault();
        } catch (RuntimeException e) {
            // Ví dụ không tạo được thư mục data/ (ổ đĩa chỉ đọc)
            fatal(e);
            return;
        }
        // Lỗi ghi .dat đến từ luồng nền → chuyển về EDT rồi mới đụng vào giao diện
        store.setErrorListener(error -> SwingUtilities.invokeLater(() -> reportSaveError(error)));
        // Lưới an toàn khi JVM bị tắt theo cách khác (Ctrl+C ở console): vẫn chờ ghi xong
        Runtime.getRuntime().addShutdownHook(new Thread(store::close, "dictionary-flush"));

        SwingUtilities.invokeLater(() -> start(settings, store));
    }

    /** Chạy trên EDT. */
    private static void start(SettingsStore settings, DictionaryStore store) {
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
                        () -> exit(store));
                installScreens(mainFrame, loaded);
                mainFrame.setVisible(true);
                pendingSaveErrors.forEach(mainFrame::showSaveError);
                pendingSaveErrors.clear();
            }
        }.execute();
    }

    /** Gắn các màn hình thật vào MainFrame thay cho màn hình tạm (mỗi bước 8–12 thêm một phần). */
    private static void installScreens(MainFrame frame, DictionaryStore.LoadResult loaded) {
        // Bước 10–11 sẽ đổi loaded::dictionary thành nguồn "từ điển hiện tại", vì Reset thay object từ điển
        SearchService searchService = new SearchService(loaded::dictionary);
        SearchPanel searchPanel = new SearchPanel(searchService, frame::showFeature);
        frame.setScreen(Feature.Card.SEARCH, searchPanel);
    }

    private static void reportSaveError(IOException error) {
        System.err.println("[App] save failed: " + error);
        if (mainFrame != null) {
            mainFrame.showSaveError(error);
        } else {
            pendingSaveErrors.add(error);
        }
    }

    /** Ẩn cửa sổ ngay để người dùng không phải chờ, phần chờ ghi file chạy ở luồng riêng. */
    private static void exit(DictionaryStore store) {
        if (mainFrame != null) {
            mainFrame.dispose();
        }
        Thread exiter = new Thread(() -> {
            store.close(); // chờ lần ghi .dat cuối cùng
            System.exit(0);
        }, "app-exit");
        exiter.start();
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
