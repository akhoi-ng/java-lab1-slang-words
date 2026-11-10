package lab01.slangdict._24127430.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.WindowConstants;

import lab01.slangdict._24127430.i18n.Messages;
import lab01.slangdict._24127430.io.DictionaryStore;
import lab01.slangdict._24127430.io.SettingsStore;
import lab01.slangdict._24127430.model.Theme;
import lab01.slangdict._24127430.ui.dialogs.AboutDialog;
import lab01.slangdict._24127430.ui.panels.PlaceholderPanel;

/**
 * Cửa sổ chính.
 * <pre>
 * ┌──────────────────────────────────────────────────────────┐
 * │ JMenuBar: Tệp | Chức năng | Giao diện | Ngôn ngữ | Trợ giúp│
 * ├────────────┬─────────────────────────────────────────────┤
 * │ Sidebar    │ CardLayout: mỗi màn hình là một "card",     │
 * │ (10 chức   │ chỉ card đang chọn được hiện                 │
 * │  năng)     │                                              │
 * ├────────────┴─────────────────────────────────────────────┤
 * │ Thanh trạng thái: số slang, nguồn và thời gian tải        │
 * └──────────────────────────────────────────────────────────┘
 * </pre>
 * Menu "Chức năng" và Sidebar cùng gọi {@link #showFeature(Feature)}, nên chọn ở đâu cũng ra cùng kết quả.
 * Các bước sau gắn màn hình/hành động thật bằng {@link #setScreen} và {@link #setAction}.
 */
public class MainFrame extends JFrame {

    private final SettingsStore settings;
    private final DictionaryStore.LoadResult loaded;
    private final Path dataDir;
    private final String version;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);
    private final Map<Feature.Card, JComponent> screens = new EnumMap<>(Feature.Card.class);
    private final Map<Feature, Runnable> actions = new EnumMap<>(Feature.class);
    private final Sidebar sidebar;
    private Feature currentFeature = Feature.SEARCH_SLANG;

    // Menu: giữ tham chiếu để cập nhật chữ khi đổi ngôn ngữ
    private final JMenu fileMenu = new JMenu();
    private final JMenu functionsMenu = new JMenu();
    private final JMenu viewMenu = new JMenu();
    private final JMenu languageMenu = new JMenu();
    private final JMenu helpMenu = new JMenu();
    private final JMenuItem resetItem = new JMenuItem();
    private final JMenuItem exitItem = new JMenuItem();
    private final JMenuItem aboutItem = new JMenuItem();
    private final JRadioButtonMenuItem lightItem = new JRadioButtonMenuItem();
    private final JRadioButtonMenuItem darkItem = new JRadioButtonMenuItem();
    private final Map<Feature, JMenuItem> featureItems = new EnumMap<>(Feature.class);
    /** Nút mặt trời/mặt trăng ở góc phải thanh menu: bấm một lần là đổi sáng ↔ tối. */
    private final JButton themeToggle = new JButton(new ThemeToggleIcon());
    private static final KeyStroke THEME_TOGGLE_KEY =
            KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);

    private final JLabel statusInfo = new JLabel();
    /** Số slang hiện trên thanh trạng thái; đổi theo mỗi lần thêm/xóa ({@link #showDictionarySize}). */
    private int dictionarySize;
    private final JLabel statusMessage = new JLabel();
    private String statusMessageKey;
    private Object[] statusMessageArgs = {};
    private final Set<String> shownSaveErrorDialogs = new HashSet<>();

    /**
     * @param exitHandler chạy khi người dùng thoát (nút X hoặc menu Thoát): App lo việc chờ ghi file rồi tắt
     */
    public MainFrame(SettingsStore settings, DictionaryStore.LoadResult loaded, Path dataDir,
                     String version, Runnable exitHandler) {
        this.settings = settings;
        this.loaded = loaded;
        this.dataDir = dataDir;
        this.version = version;
        this.dictionarySize = loaded.dictionary().size();

        setIconImages(AppIcon.images());
        setJMenuBar(buildMenuBar());

        sidebar = new Sidebar(this::showFeature);
        JScrollPane sidebarScroll = new JScrollPane(sidebar,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sidebarScroll.setBorder(BorderFactory.createEmptyBorder());

        buildPlaceholderScreens();

        JPanel content = new JPanel(new BorderLayout());
        content.add(sidebarScroll, BorderLayout.WEST);
        content.add(cardPanel, BorderLayout.CENTER);
        content.add(buildStatusBar(), BorderLayout.SOUTH);
        setContentPane(content);

        // Không để Swing tự thoát: App cần chờ luồng ghi nền lưu xong .dat rồi mới tắt
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitHandler.run();
            }
        });
        exitItem.addActionListener(e -> exitHandler.run());

        applyTexts();
        Messages.addListener(this::applyTexts);

        setMinimumSize(new Dimension(900, 600));
        setSize(1120, 720);
        setLocationRelativeTo(null);
        showFeature(Feature.SEARCH_SLANG);
    }

    // ===================== API cho các bước sau =====================

    /** Chuyển tới một chức năng: màn hình thì đổi card, hành động thì chạy hành động đó. */
    public void showFeature(Feature feature) {
        if (!feature.isScreen()) {
            actions.getOrDefault(feature, () -> showActionPlaceholder(feature)).run();
            sidebar.select(currentFeature); // hành động không đổi màn hình → giữ nút đang chọn
            return;
        }
        currentFeature = feature;
        cardLayout.show(cardPanel, feature.card().name());
        sidebar.select(feature);
        updateTitle();
        JComponent screen = screens.get(feature.card());
        if (screen instanceof FeatureScreen featureScreen) {
            featureScreen.onShown(feature);
        }
    }

    /** Thay màn hình tạm của một card bằng màn hình thật. */
    public void setScreen(Feature.Card card, JComponent screen) {
        JComponent old = screens.put(card, screen);
        if (old != null) {
            cardPanel.remove(old);
        }
        cardPanel.add(screen, card.name());
        if (currentFeature.card() == card) {
            showFeature(currentFeature);
        }
    }

    /** Gắn hành động thật cho chức năng dạng dialog (Add, Edit, Delete, Reset). */
    public void setAction(Feature feature, Runnable action) {
        if (feature.isScreen()) {
            throw new IllegalArgumentException(feature + " is a screen, use setScreen");
        }
        actions.put(feature, action);
    }

    public Feature currentFeature() {
        return currentFeature;
    }

    /** Cập nhật số slang trên thanh trạng thái (App gọi sau mỗi lần từ điển thay đổi). */
    public void showDictionarySize(int size) {
        dictionarySize = size;
        updateStatusInfo();
    }

    /** Lỗi ghi dictionary.dat từ luồng nền (đã được chuyển về EDT). */
    public void showSaveError(IOException error) {
        showSaveError("error.saveFailed", error);
    }

    /**
     * Lỗi ghi file từ luồng nền (đã được chuyển về EDT): báo trên thanh trạng thái; dialog của mỗi loại lỗi
     * ({@code error.saveFailed} cho từ điển, {@code error.historySaveFailed} cho lịch sử) chỉ hiện lần đầu.
     */
    public void showSaveError(String dialogKey, IOException error) {
        setStatusMessage("status.saveFailed", error.getMessage());
        if (shownSaveErrorDialogs.add(dialogKey)) {
            JOptionPane.showMessageDialog(this, Messages.format(dialogKey, error.getMessage()),
                    Messages.get("error.title"), JOptionPane.WARNING_MESSAGE);
        }
    }

    // ===================== Dựng giao diện =====================

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        // Tệp
        resetItem.addActionListener(e -> showFeature(Feature.RESET));
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
        fileMenu.add(resetItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        // Chức năng: đủ 10 mục, ngăn cách theo nhóm giống Sidebar
        Feature.Section section = null;
        for (Feature feature : Feature.values()) {
            if (section != null && feature.section() != section) {
                functionsMenu.addSeparator();
            }
            section = feature.section();
            JMenuItem item = new JMenuItem();
            item.setIcon(new NumberBadgeIcon(feature.number()));
            item.setAccelerator(feature.accelerator());
            item.addActionListener(e -> showFeature(feature));
            featureItems.put(feature, item);
            functionsMenu.add(item);
        }

        // Giao diện sáng/tối
        ButtonGroup themes = new ButtonGroup();
        themes.add(lightItem);
        themes.add(darkItem);
        lightItem.addActionListener(e -> changeTheme(Theme.LIGHT));
        darkItem.addActionListener(e -> changeTheme(Theme.DARK));
        (ThemeManager.current() == Theme.DARK ? darkItem : lightItem).setSelected(true);
        viewMenu.add(lightItem);
        viewMenu.add(darkItem);
        viewMenu.setEnabled(ThemeManager.isThemeSwitchingSupported());

        // Ngôn ngữ: tên mỗi ngôn ngữ luôn viết bằng chính ngôn ngữ đó, để ai cũng tìm được đường quay lại
        ButtonGroup languages = new ButtonGroup();
        Map<Locale, String> names = new LinkedHashMap<>();
        names.put(Messages.VIETNAMESE, "Tiếng Việt");
        names.put(Messages.ENGLISH, "English");
        names.forEach((locale, name) -> {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(name, locale.equals(Messages.getLocale()));
            item.addActionListener(e -> changeLanguage(locale));
            languages.add(item);
            languageMenu.add(item);
        });

        // Trợ giúp
        aboutItem.addActionListener(e -> AboutDialog.show(this, version, dataDir));
        helpMenu.add(aboutItem);

        bar.add(fileMenu);
        bar.add(functionsMenu);
        bar.add(viewMenu);
        bar.add(languageMenu);
        bar.add(helpMenu);

        // Nút đổi theme: "glue" đẩy nút sang sát mép phải thanh menu
        if (ThemeManager.isThemeSwitchingSupported()) {
            themeToggle.putClientProperty("JButton.buttonType", "toolBarButton");
            themeToggle.setFocusable(false);
            themeToggle.addActionListener(e -> toggleTheme());
            bar.add(Box.createHorizontalGlue());
            bar.add(themeToggle);
            // Phím tắt dùng được ở mọi nơi trong cửa sổ
            getRootPane().registerKeyboardAction(e -> toggleTheme(), THEME_TOGGLE_KEY,
                    JComponent.WHEN_IN_FOCUSED_WINDOW);
        }
        return bar;
    }

    private void buildPlaceholderScreens() {
        for (Feature.Card card : Feature.Card.values()) {
            List<Feature> features = new ArrayList<>();
            for (Feature feature : Feature.values()) {
                if (feature.card() == card) {
                    features.add(feature);
                }
            }
            PlaceholderPanel placeholder = new PlaceholderPanel(features);
            screens.put(card, placeholder);
            cardPanel.add(placeholder, card.name());
        }
    }

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(16, 0)) {
            @Override
            public void updateUI() {
                super.updateUI();
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeManager.color("Component.borderColor", Color.GRAY)),
                        BorderFactory.createEmptyBorder(5, 12, 5, 12)));
            }
        };
        bar.add(statusInfo, BorderLayout.WEST);
        bar.add(statusMessage, BorderLayout.EAST);
        return bar;
    }

    // ===================== Ngôn ngữ / theme =====================

    private void applyTexts() {
        fileMenu.setText(Messages.get("menu.file"));
        resetItem.setText(Messages.get(Feature.RESET.titleKey()) + "…");
        exitItem.setText(Messages.get("menu.file.exit"));
        functionsMenu.setText(Messages.get("menu.functions"));
        featureItems.forEach((feature, item) -> {
            item.setText(Messages.get(feature.titleKey()));
            item.setToolTipText(Messages.get(feature.descriptionKey()));
        });
        viewMenu.setText(Messages.get("menu.view"));
        lightItem.setText(Messages.get("menu.view.light"));
        darkItem.setText(Messages.get("menu.view.dark"));
        updateThemeToggleTooltip();
        languageMenu.setText(Messages.get("menu.language"));
        helpMenu.setText(Messages.get("menu.help"));
        aboutItem.setText(Messages.get("menu.help.about") + "…");

        updateStatusInfo();
        statusInfo.setToolTipText(Messages.format("status.dataDir", dataDir));
        statusMessage.setText(statusMessageKey == null ? "" : Messages.format(statusMessageKey, statusMessageArgs));
        updateTitle();
    }

    private void updateStatusInfo() {
        String sourceKey = loaded.source() == DictionaryStore.Source.DAT ? "status.loaded.dat" : "status.loaded.text";
        statusInfo.setText(Messages.format(sourceKey, dictionarySize, loaded.elapsedMillis()));
    }

    private void updateTitle() {
        setTitle(Messages.get("app.title") + " — " + currentFeature.number() + ". "
                + Messages.get(currentFeature.titleKey()));
    }

    private void changeLanguage(Locale locale) {
        Messages.setLocale(locale); // → mọi listener (menu, sidebar, các màn hình) tự cập nhật chữ
        settings.setLanguage(Messages.getLocale());
        persistSettings();
    }

    private void toggleTheme() {
        changeTheme(ThemeManager.current() == Theme.DARK ? Theme.LIGHT : Theme.DARK);
    }

    /** Dùng chung cho menu Giao diện và nút đổi theme, nên hai nơi luôn khớp nhau. */
    private void changeTheme(Theme theme) {
        ThemeManager.apply(theme);
        (theme == Theme.DARK ? darkItem : lightItem).setSelected(true);
        updateThemeToggleTooltip();
        settings.setTheme(theme);
        persistSettings();
    }

    private void updateThemeToggleTooltip() {
        String key = ThemeManager.current() == Theme.DARK ? "theme.toggle.toLight" : "theme.toggle.toDark";
        themeToggle.setToolTipText(Messages.get(key) + " (" + Sidebar.acceleratorText(THEME_TOGGLE_KEY) + ")");
    }

    private void persistSettings() {
        try {
            settings.save();
        } catch (IOException e) {
            setStatusMessage("error.settings", e.getMessage());
        }
    }

    private void setStatusMessage(String key, Object... args) {
        statusMessageKey = key;
        statusMessageArgs = args;
        statusMessage.setText(Messages.format(key, args));
        statusMessage.setForeground(ThemeManager.color("Actions.Red", Color.RED));
    }

    private void showActionPlaceholder(Feature feature) {
        JOptionPane.showMessageDialog(this,
                Messages.format("placeholder.action", feature.number(), Messages.get(feature.titleKey())),
                Messages.get(feature.titleKey()), JOptionPane.INFORMATION_MESSAGE);
    }
}
