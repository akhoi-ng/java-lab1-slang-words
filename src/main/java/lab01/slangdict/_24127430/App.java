package lab01.slangdict._24127430;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import lab01.slangdict._24127430.io.AppPaths;
import lab01.slangdict._24127430.io.DictionaryStore;
import lab01.slangdict._24127430.io.SlangFormatException;
import lab01.slangdict._24127430.ui.MainFrame;
import lab01.slangdict._24127430.ui.ThemeManager;

/**
 * Điểm khởi động ứng dụng Slang Dictionary.
 * <p>
 * Hiện tại load dữ liệu ngay trên luồng main trước khi hiện giao diện và in log;
 * bước 7 sẽ chuyển sang SwingWorker kèm màn hình "Đang tải…".
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        ThemeManager.install();

        DictionaryStore store = DictionaryStore.createDefault();
        DictionaryStore.LoadResult loaded;
        try {
            loaded = store.loadOrBuild();
        } catch (IOException | SlangFormatException e) {
            e.printStackTrace();
            showFatalError("Cannot load slang data:\n" + e.getMessage());
            System.exit(1);
            return;
        }
        System.out.printf("[App] Loaded %,d slang words from %s in %d ms (data dir: %s)%n",
                loaded.dictionary().size(),
                loaded.source() == DictionaryStore.Source.DAT ? store.dictionaryFile()
                        : store.originalSourceDescription(),
                loaded.elapsedMillis(), AppPaths.dataDir());

        // Đóng cửa sổ (EXIT_ON_CLOSE → System.exit) vẫn chờ các lần ghi nền ghi xong
        Runtime.getRuntime().addShutdownHook(new Thread(store::close, "dictionary-flush"));

        // Mọi thao tác Swing phải chạy trên Event Dispatch Thread
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }

    private static void showFatalError(String message) {
        try {
            SwingUtilities.invokeAndWait(() ->
                    JOptionPane.showMessageDialog(null, message, "Slang Dictionary", JOptionPane.ERROR_MESSAGE));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException e) {
            // Không hiện được dialog (ví dụ môi trường không có màn hình): lỗi đã in ra stderr
        }
    }
}
