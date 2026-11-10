package lab01.slangdict._24127430.ui;

/**
 * Màn hình nào cần biết "vừa được mở bằng chức năng nào" thì cài interface này.
 * Ví dụ (bước 8) màn hình Search mở bằng F1 thì chọn sẵn "tìm theo slang", mở bằng F2 thì "theo definition".
 */
public interface FeatureScreen {

    /** Gọi trên EDT mỗi lần màn hình được hiện qua {@code MainFrame.showFeature}. */
    void onShown(Feature via);
}
