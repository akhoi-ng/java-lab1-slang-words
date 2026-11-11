package lab01.slangdict._24127430.io;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * Chỉ cho một cửa sổ app dùng một thư mục data/ tại một thời điểm.
 * <p>
 * Mỗi process giữ một bản từ điển riêng trong bộ nhớ và mỗi lần lưu ghi <b>cả</b> từ điển, nên hai cửa sổ cùng chạy
 * thì cửa sổ lưu sau ghi đè mất thay đổi của cửa sổ kia (history.dat cũng vậy). Vì vậy lúc khởi động app khóa file
 * {@value #LOCK_FILE} trong data/; process thứ hai không lấy được khóa thì báo rồi thoát.
 * <p>
 * Khóa của hệ điều hành ({@link FileChannel#tryLock}) tự nhả khi process kết thúc, kể cả khi bị tắt ngang (crash,
 * Task Manager), nên không bao giờ bị kẹt khóa. File {@value #LOCK_FILE} được để lại trên đĩa: xóa nó lúc thoát có
 * thể làm process khác vừa mở cùng file đó khóa nhầm lên một file đã bị xóa.
 */
public final class InstanceLock implements AutoCloseable {

    static final String LOCK_FILE = "app.lock";

    private final FileChannel channel;
    private final FileLock lock;

    private InstanceLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Thử khóa {@code dataDir}/{@value #LOCK_FILE}.
     *
     * @return khóa nếu lấy được (giữ nó cho tới khi thoát app); rỗng nếu một process khác (hoặc chính process này)
     *         đang giữ
     * @throws IOException không mở được file khóa
     */
    public static Optional<InstanceLock> tryAcquire(Path dataDir) throws IOException {
        FileChannel channel = FileChannel.open(dataDir.resolve(LOCK_FILE), StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) { // process khác đang giữ
                channel.close();
                return Optional.empty();
            }
            return Optional.of(new InstanceLock(channel, lock));
        } catch (OverlappingFileLockException e) { // chính JVM này đang giữ (chỉ gặp trong test)
            channel.close();
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    /** Nhả khóa. Thoát app thì không cần gọi: hệ điều hành tự nhả. */
    @Override
    public void close() throws IOException {
        try {
            lock.release();
        } finally {
            channel.close();
        }
    }
}
