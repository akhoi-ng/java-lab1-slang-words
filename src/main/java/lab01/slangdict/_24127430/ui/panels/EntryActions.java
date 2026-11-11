package lab01.slangdict._24127430.ui.panels;

import java.awt.Component;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

import javax.swing.JLabel;
import javax.swing.JOptionPane;

import lab01.slangdict._24127430.i18n.Messages;
import lab01.slangdict._24127430.model.SlangEntry;
import lab01.slangdict._24127430.service.ChangeResult;
import lab01.slangdict._24127430.service.ConflictResolver.Conflict;
import lab01.slangdict._24127430.service.ConflictResolver.Decision;
import lab01.slangdict._24127430.service.DictionaryService;
import lab01.slangdict._24127430.ui.Feature;
import lab01.slangdict._24127430.ui.dialogs.ConflictDialog;
import lab01.slangdict._24127430.ui.dialogs.EntryEditorDialog;
import lab01.slangdict._24127430.ui.dialogs.EntryEditorDialog.Draft;

/**
 * Ba hành động F4 Thêm, F5 Sửa, F6 Xóa: nối dialog ↔ {@link DictionaryService} ↔ màn hình Search.
 * <pre>
 * Thêm:  EntryEditorDialog ──► service.add(…, resolver) ──trùng tên──► ConflictDialog
 *          ▲                                              │ "Quay lại sửa"
 *          └──────────── mở lại dialog với dữ liệu cũ ◄──────┘
 *        xong ──► SearchPanel.showChange: thông báo + chọn sẵn entry vừa thêm
 *
 * Sửa / Xóa: luôn làm trên slang đang chọn ở màn hình Search (chưa chọn thì hiện hướng dẫn)
 * </pre>
 * MainFrame gọi các hành động này khi bấm Sidebar, menu, phím tắt (Ctrl+4/5/6) hay nút trên màn hình Search.
 * <p>
 * Các dialog đi qua interface {@link Prompts}, nên test thay bằng câu trả lời soạn sẵn mà không mở cửa sổ nào.
 */
public final class EntryActions {

    /** Những chỗ cần hỏi người dùng. */
    interface Prompts {
        /** Dialog nhập tên + nghĩa; rỗng nếu hủy. */
        Optional<Draft> editEntry(Component parent, boolean editing, Draft initial, ToIntFunction<String> sameName);

        /** Dialog trùng tên; rỗng nếu "Quay lại sửa". */
        Optional<Decision> resolveConflict(Component parent, Conflict conflict);

        /** Xác nhận xóa. */
        boolean confirmDelete(Component parent, SlangEntry entry);
    }

    private final DictionaryService service;
    private final SearchPanel searchPanel;
    private final Consumer<Feature> navigator;
    private final Prompts prompts;

    /**
     * @param navigator chuyển màn hình ({@code mainFrame::showFeature}), dùng để đưa người dùng về màn hình Search
     */
    public EntryActions(DictionaryService service, SearchPanel searchPanel, Consumer<Feature> navigator) {
        this(service, searchPanel, navigator, new DialogPrompts());
    }

    EntryActions(DictionaryService service, SearchPanel searchPanel, Consumer<Feature> navigator, Prompts prompts) {
        this.service = Objects.requireNonNull(service, "service");
        this.searchPanel = Objects.requireNonNull(searchPanel, "searchPanel");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.prompts = Objects.requireNonNull(prompts, "prompts");
    }

    // ===================== F4 Thêm =====================

    /** Mở dialog thêm; tên gợi ý sẵn là chữ đang gõ ở ô tìm slang (tìm không thấy → bấm Thêm là có ngay tên). */
    public void add() {
        Draft draft = new Draft(searchPanel.slangQuery(), List.of());
        while (true) {
            Optional<Draft> input = prompts.editEntry(searchPanel, false, draft,
                    name -> service.countOthersNamed(name, -1));
            if (input.isEmpty()) {
                return;
            }
            draft = input.get();
            ChangeResult result = service.add(draft.slang(), draft.meanings(),
                    conflict -> prompts.resolveConflict(searchPanel, conflict));
            if (result.status() != ChangeResult.Status.CANCELLED) {
                showSearchScreen();
                searchPanel.showChange(result.entry(), addNoticeKey(result), result.entry().getSlang());
                return;
            }
            // "Quay lại sửa" ở dialog trùng tên → mở lại dialog nhập với đúng dữ liệu vừa nhập
        }
    }

    static String addNoticeKey(ChangeResult result) {
        if (result.resolution() == null) {
            return "notice.added";
        }
        return switch (result.resolution()) {
            case OVERWRITE -> result.isChanged() ? "notice.overwritten" : "notice.unchanged";
            case ADD_MEANING -> result.isChanged() ? "notice.meaningsAdded" : "notice.unchangedMeanings";
            case NEW_ENTRY -> "notice.addedDuplicate";
        };
    }

    // ===================== F5 Sửa =====================

    /** Sửa slang đang chọn ở màn hình Search. */
    public void edit() {
        Optional<SlangEntry> entry = currentSelection("notice.selectToEdit");
        if (entry.isEmpty()) {
            return;
        }
        SlangEntry original = entry.get();
        int id = original.getId();
        Draft draft = new Draft(original.getSlang(), original.getMeanings());
        while (true) {
            Optional<Draft> input = prompts.editEntry(searchPanel, true, draft,
                    name -> service.countOthersNamed(name, id));
            if (input.isEmpty()) {
                return;
            }
            draft = input.get();
            ChangeResult result = service.edit(id, draft.slang(), draft.meanings(),
                    conflict -> prompts.resolveConflict(searchPanel, conflict));
            if (result.status() != ChangeResult.Status.CANCELLED) {
                searchPanel.showChange(result.entry(), editNoticeKey(result), result.entry().getSlang());
                return;
            }
        }
    }

    static String editNoticeKey(ChangeResult result) {
        if (!result.isChanged()) {
            return "notice.unchanged";
        }
        if (result.resolution() == null) {
            return "notice.updated";
        }
        return switch (result.resolution()) {
            case OVERWRITE -> "notice.replacedOther";
            case ADD_MEANING -> "notice.merged";
            case NEW_ENTRY -> "notice.updated";
        };
    }

    // ===================== F6 Xóa =====================

    /** Xóa slang đang chọn ở màn hình Search, sau khi xác nhận. */
    public void delete() {
        Optional<SlangEntry> entry = currentSelection("notice.selectToDelete");
        if (entry.isEmpty() || !prompts.confirmDelete(searchPanel, entry.get())) {
            return;
        }
        ChangeResult result = service.delete(entry.get().getId());
        searchPanel.showRemoval("notice.deleted", result.entry().getSlang());
    }

    // ===================== Chung =====================

    /**
     * Đưa người dùng về màn hình Search (nếu đang ở màn hình khác) rồi lấy slang đang chọn, đọc lại bản mới nhất
     * theo id. Chưa chọn gì thì hiện hướng dẫn {@code hintKey}.
     */
    private Optional<SlangEntry> currentSelection(String hintKey) {
        showSearchScreen();
        Optional<SlangEntry> selected = searchPanel.selectedEntry();
        if (selected.isEmpty()) {
            searchPanel.showHint(hintKey);
            return Optional.empty();
        }
        SlangEntry fresh = service.get(selected.get().getId());
        if (fresh == null) {
            // Bảng còn hiện bản cũ trong lúc chờ tìm lại sau một lần xóa
            searchPanel.refresh();
            searchPanel.showHint("notice.gone");
            return Optional.empty();
        }
        return Optional.of(fresh);
    }

    private void showSearchScreen() {
        if (!searchPanel.isShowing()) {
            navigator.accept(searchPanel.currentFeature());
        }
    }

    // ===================== Dialog thật =====================

    /** Mở dialog Swing thật. */
    private static final class DialogPrompts implements Prompts {

        @Override
        public Optional<Draft> editEntry(Component parent, boolean editing, Draft initial,
                                         ToIntFunction<String> sameName) {
            return EntryEditorDialog.show(parent, editing, initial, sameName);
        }

        @Override
        public Optional<Decision> resolveConflict(Component parent, Conflict conflict) {
            return ConflictDialog.show(parent, conflict);
        }

        /**
         * Hộp xác nhận hiện tên và các nghĩa sắp bị xóa. Nút mặc định (focus, Enter) là "Hủy": xóa không hoàn tác
         * được, nên lỡ nhấn Delete rồi Enter cũng không mất slang. Muốn xóa phải bấm "Xóa" (hoặc Tab rồi Enter).
         */
        @Override
        public boolean confirmDelete(Component parent, SlangEntry entry) {
            StringBuilder html = new StringBuilder("<html><body style='width:380px'>")
                    .append(escape(Messages.get("delete.confirm.question"))).append("<br><br>")
                    .append("<b style='font-size:120%'>").append(escape(entry.getSlang())).append("</b><ol>");
            for (String meaning : entry.getMeanings()) {
                html.append("<li>").append(escape(meaning)).append("</li>");
            }
            html.append("</ol></body></html>");
            Object[] options = {Messages.get("delete.button"), Messages.get("common.cancel")};
            int choice = JOptionPane.showOptionDialog(parent, new JLabel(html.toString()),
                    Messages.get("delete.confirm.title"), JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[1]);
            return choice == 0;
        }

        private static String escape(String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
