# AUDIT_REPORT: Slang Dictionary (DAMH#1, 24127430)

Ngày audit: 11/11/2026 · commit `1b9e890` (working tree chỉ có `docs/PLAN.md` sửa dở từ trước) · JDK chạy test và repro: 25.0.4.1.
Audit chỉ đọc: không sửa file nào trong repo; file repro tạm nằm ngoài repo và đã xóa.

## Trạng thái sửa (cập nhật 11/11/2026)

Từ mục 1 trở xuống giữ nguyên như lúc audit (commit `1b9e890`): số dòng, số đo và kịch bản tái hiện là của commit đó. Cách sửa và số đo sau khi sửa ghi trong `docs/ARCHITECTURE.md` và `docs/PLAN.md`.

| Lỗi | Trạng thái | Commit | Ghi chú |
|---|---|---|---|
| C1 | Đã sửa | `5d5804d` | Trie ghi phẳng, đọc `.dat` trên luồng stack 16 MB, tên tối đa 100 ký tự, luồng ghi báo cả `Error` |
| C2 | Đã sửa | `551d64b` | Chỉ build lại khi gặp `DictionaryFormatException`; file hỏng giữ lại thành `dictionary.dat.bad` và có dialog cảnh báo; lỗi I/O khác thì không ghi đè |
| H1 | Đã sửa | `77a477d` | Dòng không có `` ` `` thành nghĩa mới của entry trước (không nối vào nghĩa cuối như diff đề xuất: 5 dòng vỡ đều là nghĩa riêng, vd. `AMA` → "Against Medical Advice"). File gốc `c863d3f`: 7.656 entry, 8.959 cặp, khớp `fix_slang.py` |
| M1 | Đã sửa | `7135a94` | Chỉ giữ khóa lúc `snapshot()` (100k: 80–240 ms), serialize ngoài khóa. 100k khi đang ghi: tìm 0–36 ms, add 0 ms |
| M2 | Đã sửa | `7135a94` | Chỉ thêm từ mới vào BK-tree từ vựng, build ba nhóm index song song. Reset 100k: 0,46–0,71 s (1 CPU: 0,8–1,25 s) |
| M3 | Đã sửa | `006d7b0` | Kết quả tìm-khi-gõ được ghi khi ngừng gõ 1,5 s, khi chuyển vào bảng kết quả, hoặc khi rời màn hình Search. Gõ tiếp/xóa ô tìm/Enter thì hủy, nên không ghi chữ gõ dở và không ghi hai lần. Sửa cả dòng gợi ý và màn hình Lịch sử trống. Chạy jar thật, gõ phím tự động (KeyEvent vào hàng đợi sự kiện của app): gõ xong → vào lịch sử 1.640 ms; nhấn ↓ → 47 ms; Ctrl+3 → 153 ms; gõ chậm o-m-g chỉ ghi `omg`; `history.dat` sau Ctrl+Q đủ 5 dòng. Bản trước khi sửa: chờ 5 s vẫn không ghi |
| L1 | Đã sửa | `093f7ef` | Thư mục cạnh jar không tạo/không ghi được (ghi thử một file tạm, không dùng `Files.isWritable`) thì dùng `<home>/.slang-dictionary`, dialog báo vị trí ở lần chạy tạo ra thư mục đó. Thư mục chỉ định bằng `-Dslangdict.data.dir` thì không tự đổi. Test bằng một file chiếm tên `data` (không tạo được thư mục); chưa chạy jar trong thư mục bị khóa quyền ghi thật |
| L2 | Đã sửa | `093f7ef` | `InstanceLock`: `FileChannel.tryLock()` trên `data/app.lock` trước khi đọc dữ liệu; không lấy được thì dialog "đang mở ở một cửa sổ khác" rồi thoát. Test có một JVM con thật bị từ chối khi khóa đang bị giữ. Chạy jar thật hai lần cùng `data/`: lần hai chỉ hiện dialog cảnh báo, không mở cửa sổ chính |
| L5 | Đã sửa | `093f7ef` | Nút "Hiện tất cả" cạnh dòng trạng thái khi kết quả bị cắt ở 1.000 dòng: tìm lại đúng lần tìm đó với `UNLIMITED`, giữ dòng đang chọn, không ghi lịch sử thêm; tìm lại sau thêm/sửa/xóa vẫn hiện đủ, lần tìm khác lại bắt đầu từ 1.000 |
| L3 | Đã sửa | `e7fd3e4` | Nút mặc định của hộp xác nhận xóa là "Hủy". Chạy jar thật: Delete → Enter thì slang vẫn còn; bấm "Xóa" thì mất đúng 1 slang |
| L4 | Đã sửa | `e7fd3e4` | F2 mặc định "Chứa chuỗi" (`laugh` ra 117 kết quả, 78 có `laughing`). Keyword không có chữ/số thì tìm nguyên chuỗi trong nghĩa ở mọi mode (`&`: 14 kết quả, có tô sáng; 100k: 12–27 ms) |

Yêu cầu nộp bài (mục 3), làm ở bước 13 của PLAN:

| ID | Trạng thái | Ghi chú |
|---|---|---|
| IV.6 | Đã làm | Đánh dấu `[THAM KHẢO - CẢ FILE]` / `[THAM KHẢO]`…`[HẾT THAM KHẢO]` trong code, bảng nguồn và cách tính trong `README.md` mục 6: đếm được 928/7.101 dòng code ≈ 13% lúc làm bước 13 (sau các lần sửa về sau: 928/7.176, vẫn ≈ 13%), khai 15% |
| IV.8 | Đã làm phần trong repo | Có `README.md`, `24127430_TuChamDiem.txt` (còn điền link video), jar build lại sau mọi sửa đổi và đã chạy thử ở thư mục khác (lần đầu build từ text, lần sau đọc `.dat`). Hình commit, video, file zip làm ngoài repo |

Mục 6 (tài liệu nói khác code):

| Dòng trong mục 6 | Trạng thái |
|---|---|
| `PLAN.md:13` bước 12 chưa commit | Đã sửa (`093f7ef`): ghi đã commit bước 1–12 và đã sửa các lỗi audit |
| `PLAN.md:15` số commit | Đã sửa (`093f7ef`): 26 commit tính tới commit sửa L1/L2/L5, kèm số commit từng ngày và lệnh đếm lại |
| `ARCHITECTURE.md:120` nhắc tới README | Đã sửa (`093f7ef`): bỏ "và README", ghi FlatLaf khai báo trong `pom.xml`, được shade vào jar, ghi giấy phép ở Giới thiệu. `README.md` đã có ở bước 13 |
| `ARCHITECTURE.md:299` id không dùng lại | Đã sửa code cho khớp tài liệu (`093f7ef`): Reset gọi `reserveIdsBelow(nextId cũ)` nên id của slang đã thêm không được cấp lại, kể cả sau khi mở lại app |
| `ARCHITECTURE.md:336` `loadOrBuild` bắt mọi `IOException` | Đã sửa cùng C2 (`551d64b`) |
| `ARCHITECTURE.md:344` `Error` không được báo | Đã sửa cùng C1 (`5d5804d`) |
| `ARCHITECTURE.md:354`, `PLAN.md:131` số đo `.dat` | Đã đo lại sau C1 (`5d5804d`) và M1/M2 (`7135a94`) |
| `ARCHITECTURE.md:301` "UI chỉ đọc/sửa trên một luồng" | Đã sửa (`5ece2d5`) |

Mục 7 (nghi vấn chưa kiểm chứng), kiểm chứng ngày 11/11/2026 sau commit `f9373d0`. Chương trình thử (ngoài repo) dựng `QuizPanel` thật từ jar và gửi phím qua `KeyboardFocusManager.redispatchEvent`, đúng đường đi của phím khi component giữ focus:

| Nghi vấn | Kết quả | Ghi chú |
|---|---|---|
| Hai instance cùng ghi `dictionary.dat.tmp` | Không còn xảy ra | `InstanceLock` (L2) chỉ cho một process dùng mỗi thư mục `data/` |
| Jar cũ hơn source | **Đúng** | Jar trên đĩa build 02:57, trước mọi commit sửa lỗi, không có `InstanceLock.class`. Đã build lại và chạy thử ở thư mục sạch |
| Ngưỡng tràn stack C1 trên JRE 17 | Không còn liên quan | C1 đã sửa: Trie ghi phẳng, tên tối đa 100 ký tự |
| Quiz: combo số câu giữ focus, nhấn 1–4 | **Đúng, đã sửa** | `JComboBox` nhảy tới mục bắt đầu bằng phím gõ: "1" → 10 câu, "2" → 20 câu, lượt bắt đầu lại, mất câu vừa trả lời. Sửa: `setKeySelectionManager((key, model) -> -1)`, phím 1–4 vẫn chọn đáp án. Test `QuizPanelTest.digitKeysTypedIntoTheRoundLengthComboAnswerInsteadOfStartingANewRound` |
| Slang/nghĩa bắt đầu bằng `<html>` | **Đúng, đã sửa** | `validate` cho qua; `JLabel`, nút gợi ý, ô bảng (kết quả, lịch sử, bảng nghĩa) và tooltip vẽ thành HTML. Sửa: lớp `ui/PlainText` đặt `"html.disable"`, tooltip thoát ký tự. Test `PlainTextTest` + 4 test màn hình |
| Khởi động 100k khoảng 2 s có bị coi là chậm | Không đổi | Không thuộc 10 chức năng; có màn hình "Đang tải…", giao diện không đứng |

Sau khi sửa: `.\mvnw.cmd package` 272/272 test pass, jar build lại lúc 14:24 ngày 11/11.

## Audit cuối (11/11/2026, sau commit `859ef70`)

Đọc lại toàn bộ `src/main/java` và chạy repro (ngoài repo) trên jar thật. Không còn lỗi Critical/High; mọi lỗi ở các bảng trên đã được sửa đúng trong code. Đã kiểm: F2 "Chứa chuỗi" với 3.005 từ khóa so với duyệt thô không sót slang nào; F1 2.000 tên × 4 kiểu khớp không sót; 100k qua store thật: lần đầu 1,65 s, đọc `.dat` 0,5–0,8 s, add 3 ms, mở lại còn slang vừa thêm.

| Lỗi | Mức | Trạng thái | Ghi chú |
|---|---|---|---|
| N1 | Medium | Đã sửa | Một dòng sai định dạng trong `data/slang.txt` (slang không có nghĩa, không có tên, dòng đầu không có `` ` ``) làm app báo lỗi rồi thoát. Sửa: `SlangFileParser.parse` bỏ qua dòng lỗi và ghi ra stderr; chỉ ném `SlangFormatException` khi file không có dòng hợp lệ nào. Chạy lại repro trên jar mới: 4 file lỗi đều mở được |
| N2 | Medium | Đã sửa (tài liệu) | `slang.txt` đặt cạnh jar không được đọc (app chỉ đọc `data/slang.txt`), người chấm thay file đó sẽ không thấy gì đổi. Thêm hướng dẫn vào `24127430_TuChamDiem.txt` và `README.md` mục 1 |
| N3 | Low | Đã sửa | F8: mở màn hình thì focus ở "Random khác", Enter bấm nút đó thay vì "Tra trong từ điển" như tooltip ghi. Sửa: gỡ Enter khỏi input map của ba nút. Test `RandomPanelTest.enterOnTheFocusedRandomButtonLooksUpInsteadOfDrawingAnotherSlang` |
| N4 | Low | Không sửa | Sau "Tra trong từ điển" (F8) hoặc tìm lại từ Lịch sử, F1 giữ kiểu khớp của lần đó (ví dụ "Chính xác") nên gõ không còn ra kết quả ngay; combo vẫn hiện đúng kiểu đang dùng |
| N5 | Low | Đã sửa | `ResetAction` chỉ bắt `Exception`: gặp `Error` (hết bộ nhớ) lúc build thì cửa sổ bị khóa. Sửa: bắt `Exception \| Error`, báo lỗi và mở khóa cửa sổ |
| N6 | Low | Đã sửa | Ở 100k, đóng app rồi mở lại trong khoảng 1 s có thể gặp dialog "đang mở ở cửa sổ khác" (lần ghi cuối mất 1,1 s, khóa còn giữ). Sửa: `App.acquireInstanceLock` thử lại tới khoảng 3 s (15 × 200 ms) trước khi báo |
| N7 | Low | Đã sửa | Tooltip danh sách trong `ConflictDialog` chưa qua `PlainText.tooltip`; dòng header `` Slag`Meaning `` không nằm ở dòng 1 thành một slang tên "Slag". Sửa: tooltip qua `PlainText.tooltip`; header được bỏ qua khi chưa có entry nào (kể cả sau dòng trống) |

Tài liệu lệch code đã sửa cùng lúc: `README.md` (`Set<Integer>` thay cho `List<Integer>`, số test, số dòng code), `docs/PLAN.md` (số commit, giờ build jar, số test), `docs/ARCHITECTURE.md` (parser bỏ qua dòng lỗi, phím Enter ở F8, số test).

Sau khi sửa: `.\mvnw.cmd package` 275/275 test pass, jar build lại lúc 19:05 ngày 11/11. Sửa N5–N7: 275/275 test pass, jar build lại lúc 19:19 ngày 11/11. N4 giữ nguyên vì là hành vi có chủ ý: combo hiện đúng kiểu khớp đang dùng.

## 1. Tóm tắt

**Kiến trúc.** App Swing + FlatLaf, Java 17, Maven, fat-jar `target/slang-dictionary.jar`. Luồng dữ liệu:
`App.main` đọc `settings.properties` và `history.dat` → `SwingWorker` gọi `DictionaryStore.loadOrBuild()` (đọc `data/dictionary.dat`, lỗi thì build từ `slang.txt`) → `SlangDictionary` (HashMap entries + `idList`, `idsByName`, `Trie`, 2 `BKTree`, `InvertedIndex` trên TreeMap) → `MainFrame` (JMenuBar + Sidebar + CardLayout). Tìm kiếm chạy trên luồng `search`, đọc index trong `synchronized (dictionary)`. Add/edit/delete chạy trên EDT qua `DictionaryService`, rồi `DictionaryStore.saveInBackground` ghi nền sau 1,5 s (ghi `.tmp` rồi move). Thoát app: `flush()` cả hai store rồi `System.exit`, có shutdown hook dự phòng. Kiến trúc thực tế khớp với `docs/ARCHITECTURE.md`, trừ các điểm ở mục 6.

**Test.** `.\mvnw.cmd -o test`: 232 chạy, 232 pass, 0 fail, 0 skip, BUILD SUCCESS.

**Số lỗi.** Critical 2 · High 1 · Medium 3 · Low 5.

**Yêu cầu đề bài (26 mục).** Đạt 15 · Đạt một phần 5 · Thiếu 1 · Sai lệch 0 · Không xác định được 5.

**Ba vấn đề nghiêm trọng nhất**

1. **C1.** Thêm/sửa một slang có tên dài từ khoảng 800 ký tự: `.dat` vẫn ghi được, nhưng lần mở app sau ném `StackOverflowError` khi đọc `.dat` và app thoát luôn. Người dùng không mở lại được app cho tới khi tự xóa `dictionary.dat`.
2. **C2.** `dictionary.dat` không đọc được (hỏng, lỗi I/O tạm thời, đổi phiên bản class) thì app lặng lẽ build lại từ `slang.txt` và **ghi đè** `.dat`. Mọi slang đã thêm/sửa/xóa mất, không có dialog, không có bản sao lưu.
3. **M1 + M2.** Ở 100k slang, quy tắc 1 s bị vượt trong ba tình huống đo được: Reset 1,9–2,1 s; tìm kiếm phải chờ khoảng 2 s và add/edit/delete đơ EDT khoảng 1,7 s nếu rơi vào lúc luồng ghi đang serialize (bắt đầu 1,5 s sau mỗi lần sửa, kéo dài khoảng 3 s).

Ngoài code: chưa có `README.md`, file `.txt` tự chấm điểm, và chưa đánh dấu phần tham khảo trong code (mục IV.6, IV.8 của đề, thuộc bước 13).

## 2. Giả định đã đặt ra

- "Slang words gốc" (F7) là `slang.txt` trong repo, tức bản **đã sửa** bằng `tools/fix_slang.py` (sửa chính tả, encoding, gộp 5 dòng vỡ). File của giảng viên nằm ở commit `c863d3f`. Nếu giảng viên so với file của họ thì kết quả Reset sẽ khác vài chục nghĩa.
- F2 "definition có chứa keyword" được hiểu là chứa chuỗi con. Vì vậy mode mặc định EXACT (khớp nguyên từ) bị tính là lệch nhẹ.
- F3 "danh sách các slang word đã tìm kiếm" được hiểu là mọi lần tìm mà người dùng nhìn thấy kết quả, kể cả tìm-khi-gõ.
- Quy tắc 1 s áp cho thời gian người dùng phải chờ (wall-clock), không phải con số "ms" app tự hiện.
- Kịch bản 100k: người chấm đặt file 100k dòng vào `data/slang.txt` (cách duy nhất app hỗ trợ), nên Reset cũng build 100k.
- Mục IV.4 của đề ("dưới 10 commits và trải dài trong 5 ngày") được hiểu là cần ≥ 10 commit trên ≥ 5 ngày.
- Số đo lấy trên máy dev này với JDK 25; ngưỡng `StackOverflowError` và thời gian có thể khác trên JRE 17 của người chấm.

## 3. Đối chiếu yêu cầu đề bài

| ID | Yêu cầu (rút gọn) | Trạng thái | Bằng chứng | Ghi chú |
|---|---|---|---|---|
| IV.6 | Đánh dấu phần tham khảo trong code, khai tổng % tham khảo | **Thiếu** | Không có chú thích tham khảo nào trong `src/main/java` (grep); không có `README.md` | PLAN.md:406 tự ghi "code do AI viết cũng cần khai trung thực" |
| G3b | Tìm kiếm dưới 1 s với 100.000 slang | **Đạt một phần** | `SearchService.java:94-110`; `SearchBenchmarkTest.java:52-85` (mỗi tổ hợp < 1000 ms, tệ nhất 158 ms) | Riêng thuật toán tìm thì đạt. Sau mỗi add/edit/delete ở 100k, lần tìm rơi vào lúc luồng ghi giữ khóa phải chờ khoảng 2 s (lỗi M1) |
| G4 | Lưu danh sách sau add/edit/delete | **Đạt một phần** | `DictionaryService.java:344-348`; `DictionaryStore.java:326-368`; `App.java:83,180-195` | Luồng thường đạt, thoát app trong 1,5 s vẫn flush. Mất dữ liệu ở C1 và C2 |
| F2 | Tìm theo definition, hiện **tất cả** slang có definition chứa keyword | **Đạt một phần** | `SearchService.java:249-321`; `SearchPanel.java:199` (mặc định EXACT), `:104` (tối đa 1.000 dòng) | Mặc định khớp nguyên từ: "laugh" không ra "laughing" (phải đổi sang "Chứa"). Keyword chỉ gồm ký hiệu/emoji không tìm được. Xem L4, L5 |
| F3 | Hiển thị history các slang đã tìm | **Đạt một phần** | `SearchPanel.java:561-573` (chỉ `submit()` ghi), `:201` (tìm-khi-gõ không ghi); `HistoryService.java:65-76`; `HistoryPanel.java` | Mode mặc định của F1 là tìm-khi-gõ nên gõ rồi xem kết quả mà không nhấn Enter thì History trống (M3) |
| IV.8 | Bài nộp: source, jar, file data, hình commit, file txt, đúng tên | **Đạt một phần** | `target/slang-dictionary.jar` (Main-Class đúng, có `data/slang.txt` bên trong); `slang.txt` ở gốc repo; `.gitignore:8` (`/data/`) | Có source, jar, data. Chưa có: hình commit, file txt tự chấm, README. `/data/` bị ignore và chỉ sinh lúc chạy (hiện chỉ có `target/data/` do chạy jar). File data để nộp phải là `slang.txt` ở gốc. Jar build lúc 02:57, commit cuối 03:02: nên build lại trước khi nộp |
| G1 | Dùng Java IO, OOP, Collections; app Swing | Đạt | `SlangFileParser.java:54-90`; `DictionaryStore.java:153-229`; `SlangDictionary.java:58-68` | |
| G2 | Có menu chọn chức năng | Đạt | `MainFrame.java:238-250` (menu "Chức năng" lặp `Feature.values()`, đủ 10 mục + phím tắt); `Feature.java:18-27` | Sidebar dựng từ cùng enum nên không lệch menu |
| G3a | Load file vào cấu trúc dữ liệu đã tổ chức | Đạt | `DictionaryStore.java:280-311`; `SlangDictionary.java:81-91,229-240` | |
| G3c | Lưu lại cấu trúc dữ liệu, lần sau không index lại | Đạt | `DictionaryStore.java:282-285` (đọc `.dat`), `:193-216` (magic, version, CRC kiểm trước khi deserialize) | Ở 100k đọc `.dat` mất 1,8–2,2 s, không nhanh hơn build lại từ text (1,85–2,1 s). Xem mục 4 |
| G5a | Git, ≥ 10 commit, phân bố đều | Đạt | `git log`: 17 commit, 6 ngày (04, 05, 08, 09, 10, 11/11; 1/1/1/3/6/5) | 11/17 commit nằm trong 2 ngày cuối |
| G5b | Hình chụp các lần commit | Không xác định được | ngoài code | |
| F1 | Tìm theo slang | Đạt | `SearchService.java:197-229`; `SearchPanel.java:584-649` | Không phân biệt hoa thường, cắt khoảng trắng, 4 mode |
| F4 | Add; trùng thì hỏi overwrite hay duplicate | Đạt | `DictionaryService.java:175-219`; `ConflictDialog.java:76-111,186-195` | Có thêm lựa chọn "thêm nghĩa". Tên rất dài gây C1 |
| F5 | Edit | Đạt | `DictionaryService.java:227-279`; `EntryActions.java:108-130` | Đổi key cập nhật đủ index (`SlangDictionary.replace` :186-195) |
| F6 | Delete, confirm trước khi xóa | Đạt | `EntryActions.java:149-156,204-218` | Nút mặc định của dialog là "Xóa" (L3) |
| F7 | Reset về danh sách gốc | Đạt | `ResetAction.java:90-125`; `DictionaryService.java:303-324` | Ở 100k mất 1,9–2,1 s (M2). "Gốc" là bản đã sửa (mục 2) |
| F8 | Random 1 slang (On this day) | Đạt | `RandomService.java:48-79`; `RandomPanel.java:150-160` | Từ điển rỗng được xử lý (`Optional`) |
| F9 | Quiz: 1 slang, 4 đáp án | Đạt | `QuizService.java:137-164`; `QuizPanel.java:205-222` | Đáp án nhiễu loại mọi nghĩa của các entry trùng tên |
| F10 | Quiz: 1 definition, 4 slang | Đạt | `QuizService.java:167-191,244-258` | Slang nào có cùng nghĩa với đề không được làm đáp án nhiễu |
| IV.3 | Dùng cấu trúc khác List | Đạt | `Trie.java`, `BKTree.java`, `InvertedIndex.java`, `SlangDictionary.java:58-68` | |
| IV.4 | ≥ 10 commit trên ≥ 5 ngày | Đạt | `git log`: 17 commit / 6 ngày | |
| IV.1 | Tên file `MSSV_Diem.zip` | Không xác định được | ngoài code | |
| IV.2 | Tự chấm điểm thang 100 | Không xác định được | ngoài code (chưa có file txt) | |
| IV.5 | Không copy bài | Không xác định được | ngoài code | |
| IV.7 | Video YouTube | Không xác định được | ngoài code | |

Chức năng có trong code mà đề không yêu cầu, có liên quan tới lỗi: ghi nền hoãn 1,5 s (M1), ưu tiên `data/slang.txt` đặt cạnh jar (H1, M2), tìm-khi-gõ (M3).

## 4. Hiệu năng theo từng chức năng (100.000 slang)

Số đo: bảng tìm kiếm lấy từ `SearchBenchmarkTest` trong lần chạy test này; các dòng còn lại đo bằng repro tạm (dữ liệu `BigDataGenerator.generate(…, 100_000, seed)`, 3 lượt).

| Chức năng | Thao tác tốn nhất | Độ phức tạp | Số đo | Dưới 1 s? |
|---|---|---|---|---|
| F1 | SUBSTRING duyệt mọi tên; sắp xếp kết quả | O(n) + O(m log m) | EXACT 0,04 ms · PREFIX "a" 9,9 ms · SUBSTRING "o" 56,5 ms · FUZZY 1,5–11,7 ms | Có. **Không** (khoảng 2 s) nếu trúng lúc luồng ghi giữ khóa (M1) |
| F2 | SUBSTRING duyệt từ vựng, chấm điểm 88k kết quả | O(V + m log m) | EXACT "the" 12,9 ms · PREFIX "a" 47,8 ms · SUBSTRING "e" 157,7 ms · FUZZY 2,4 ms | Có. Như F1 khi trúng M1 |
| F3 | Vẽ bảng tối đa 1.000 dòng, ghi `history.dat` nền | O(1.000) | không đo, không phụ thuộc số slang | Có |
| F4 | Cập nhật index (BK-tree: O(độ sâu) phép Levenshtein) | O(L · độ sâu) | add 30 ms (lần đầu, chưa JIT) | Có. **Không** (đo 1.724 ms, đơ EDT) nếu trúng M1 |
| F5 | unindex + index | như F4 | 1,6 ms | Có. Như F4 khi trúng M1 |
| F6 | swap-remove + unindex | O(số từ của entry) | 0,35 ms | Có. Như F4 khi trúng M1 |
| F7 | Parse + build lại toàn bộ index trên luồng nền | O(tổng độ dài file) + n lần chèn BK-tree | 2.138 / 1.850 / 1.867 ms | **Không** (M2). Với file gốc 7,6k: 50–100 ms theo ARCHITECTURE |
| F8 | "Hôm nay": duyệt mọi entry; "Random khác": O(1) | O(n) / O(1) | onThisDay 77 ms (lần đầu) | Có |
| F9 | Bốc đáp án nhiễu ngẫu nhiên | O(1) kỳ vọng | câu đầu 0,16 ms, trung bình 0,04 ms | Có |
| F10 | như F9 | O(1) kỳ vọng | câu đầu 0,09 ms, trung bình 0,03 ms | Có |
| Khởi động | Deserialize `.dat` 34 MB trên `SwingWorker` | O(kích thước file) | load 2.214 / 1.843 / 1.906 ms (build từ text: 1,85–2,1 s) | Không, nhưng không thuộc 10 chức năng và EDT không đơ (có màn hình "Đang tải…") |
| Ghi `.dat` | Serialize cả từ điển, giữ khóa từ điển suốt lúc đó | O(kích thước) | save 3.566 / 2.897 / 3.091 ms; flush khi thoát 1.785 ms | Chạy nền, nhưng là nguyên nhân của M1 |

## 5. Danh sách lỗi

### Critical

#### C1. Tên slang rất dài làm app không mở lại được (hoặc không lưu được nữa)

- **Vị trí:** `ds/Trie.java:28,47` (mỗi ký tự của tên là một tầng `Node` → `TreeMap` → `Node`, serialize mặc định nên đệ quy theo độ dài tên); `io/DictionaryStore.java:217-228` (các `catch` không bắt `StackOverflowError`), `:339-355` (`writeIfLatest` chỉ bắt `IOException`/`RuntimeException`); `App.java:100-116` (load trên `SwingWorker` stack mặc định, lỗi nào cũng `fatal` rồi `System.exit(1)`); `service/DictionaryService.java:110-126` (`validate` không giới hạn độ dài).
- **Nguyên nhân gốc:** luồng ghi có stack 16 MB (`DictionaryStore.java:79,109-113`) nên **ghi** được cây sâu, còn luồng **đọc** dùng stack mặc định. File hợp lệ (CRC đúng) nhưng `ObjectInputStream.readObject` tràn stack. `StackOverflowError` là `Error`, lọt qua mọi `catch`, nên cơ chế "hỏng thì build lại từ text" cũng không chạy.
- **Yêu cầu liên quan:** G4, F4, F5.
- **Kịch bản tái hiện:**
  1. Ctrl+4, dán một đoạn văn khoảng 800 ký tự trở lên vào ô tên (`JTextField` tự đổi xuống dòng thành dấu cách), nhập một nghĩa, Lưu. App báo "✓ Đã thêm".
  2. Thoát app, mở lại.
  3. Kết quả sai: dialog "Không tải được dữ liệu slang: null" rồi app thoát; lần nào mở cũng vậy cho tới khi người dùng tự xóa `data/dictionary.dat` (mất mọi thay đổi).
  4. Mong đợi: tên quá dài bị từ chối ngay ở dialog, hoặc app vẫn mở được.
  - Biến thể: tên từ khoảng 20.000 ký tự thì chính luồng ghi tràn stack. `Error` bị `FutureTask` nuốt, `errorListener` không được gọi, `.dat` không được ghi. Từ lúc đó **mọi** lần lưu trong phiên đều thất bại im lặng, vì lần nào cũng serialize cả từ điển.
- **Đã kiểm chứng bằng:** repro tạm. Tên 100–500 ký tự: lưu và đọc lại bình thường. Tên 800 / 1.200 / 2.000 / 5.000: `saved=true`, đọc lại ném `java.lang.StackOverflowError`. Tên 20.000 / 60.000: `saved=false`, `errorsReported=[]`.
- **Hướng sửa đề xuất:**
  ```diff
  // DictionaryService.validate
  +    static final int MAX_NAME_LENGTH = 100;   // slang dài nhất trong file gốc ngắn hơn nhiều
  +    if (slang.strip().length() > MAX_NAME_LENGTH) return Optional.of(Problem.NAME_TOO_LONG);
  // DictionaryStore.deserialize: coi tràn stack là file không đọc được
  -        } catch (RuntimeException e) {
  +        } catch (RuntimeException | StackOverflowError e) {
  // DictionaryStore.writeIfLatest: không để Error bị executor nuốt
  -        } catch (RuntimeException e) {
  +        } catch (RuntimeException | Error e) {
  ```
  Nên thêm: đọc `.dat` trên một `Thread` có stack 16 MB như luồng ghi, để file do bản cũ ghi vẫn đọc được. Triệt để hơn là tự viết `writeObject`/`readObject` cho `Trie` (ghi phẳng danh sách key → value rồi chèn lại).

#### C2. `.dat` không đọc được thì bị ghi đè im lặng, mất toàn bộ thay đổi của người dùng

- **Vị trí:** `io/DictionaryStore.java:282-297` (`catch (IOException e)` ở :286 chỉ in `System.err`, rồi :293 `save(dictionary, dictionaryFile)` ghi đè); `ui/MainFrame.java:113` (chỉ đổi chữ thanh trạng thái thành "tạo index từ slang.txt").
- **Nguyên nhân gốc:** `LoadResult` không phân biệt "lần chạy đầu" với "`.dat` có nhưng đọc lỗi". File lỗi không được giữ lại. `catch` bắt mọi `IOException` chứ không riêng `DictionaryFormatException`, nên lỗi đọc tạm thời (file đang bị process khác giữ) cũng dẫn tới ghi đè.
- **Yêu cầu liên quan:** G4.
- **Kịch bản tái hiện:**
  1. Thêm vài slang, thoát app.
  2. `dictionary.dat` hỏng 1 bit; hoặc chạy bằng jar build lại sau khi đổi field/`serialVersionUID`/`FORMAT_VERSION` của `SlangDictionary`, `Trie`, `BKTree`, `InvertedIndex`, `SlangEntry`; hoặc lần đọc bị `AccessDeniedException`.
  3. Mở app.
  4. Kết quả sai: app mở bình thường với 7.638 slang gốc, không có dialog nào; `.dat` trên đĩa đã là bản gốc, không còn file cũ.
  5. Mong đợi: báo cho người dùng và giữ file cũ (đổi tên thành `dictionary.dat.bad`) trước khi build lại.
- **Đã kiểm chứng bằng:** repro tạm (lật 1 bit giữa file): `source=TEXT size=7638 hasUserEntry=false errorListenerCalls=0`, thư mục chỉ còn `dictionary.dat` mới. Test có sẵn `corruptedDatIsRebuiltFromText` chỉ assert rằng app build lại được, không assert việc báo hay giữ file.
- **Ghi chú mức độ:** xếp Critical theo định nghĩa (mất dữ liệu người dùng). Xác suất thấp khi người chấm chạy jar mới trên thư mục sạch; cao hơn nếu bạn build lại jar sau khi sửa class mà vẫn dùng `data/` cũ.
- **Hướng sửa đề xuất:**
  ```diff
  // DictionaryStore.loadOrBuild
  -            } catch (IOException e) {
  -                System.err.println(...);
  +            } catch (DictionaryFormatException e) {          // lỗi I/O khác thì ném lên, đừng ghi đè
  +                Path bad = dictionaryFile.resolveSibling("dictionary.dat.bad");
  +                Files.move(dictionaryFile, bad, StandardCopyOption.REPLACE_EXISTING);
  +                recoveredFrom = e;                            // đưa vào LoadResult
               }
  // App.done(): nếu loaded.recoveredFrom() != null → JOptionPane cảnh báo, nêu tên file .bad
  ```

### High

#### H1. App không chạy được với `slang.txt` gốc chưa sửa của giảng viên

- **Vị trí:** `io/SlangFileParser.java:73-76` (dòng không có dấu `` ` `` ném `SlangFormatException`); `io/DictionaryStore.java:301-304` (ưu tiên `data/slang.txt`); `App.java:114-116` (`fatal`).
- **Nguyên nhân gốc:** file gốc (commit `c863d3f`, 7.662 dòng) có 5 dòng là phần nối tiếp của nghĩa ở dòng trước, không có dấu phân cách. Parser coi cả file là hỏng thay vì nối vào entry trước hoặc bỏ qua dòng đó. Bản trong repo chạy được vì đã qua `tools/fix_slang.py`.
- **Yêu cầu liên quan:** G3a, F7.
- **Kịch bản tái hiện:**
  1. Đặt `slang.txt` nguyên bản của đề vào `data/slang.txt` cạnh jar (cách app hướng dẫn để dùng dữ liệu khác), xóa `dictionary.dat`.
  2. Mở app → dialog "Không tải được dữ liệu slang: Line 413: missing '`' separator", app thoát.
  3. Nếu `.dat` còn: app mở được, nhưng F7 Reset báo lỗi và không reset.
  4. Mong đợi: đọc được file của đề (nối dòng vỡ vào entry trước, hoặc bỏ qua và báo số dòng bị bỏ).
- **Đã kiểm chứng bằng:** repro tạm: `SlangFileParser.parse(file gốc)` → `SlangFormatException: Line 413: missing '`' separator`; `loadOrBuild()` với `data/slang.txt` = file gốc → cùng lỗi.
- **Hướng sửa đề xuất:**
  ```diff
  // SlangFileParser.parse(BufferedReader)
  +            if (line.indexOf(SLANG_SEPARATOR) < 0 && !entries.isEmpty()) {
  +                // dòng nối tiếp của nghĩa ở dòng trước (file gốc của đề có 5 dòng như vậy)
  +                SlangEntry last = entries.remove(entries.size() - 1);
  +                List<String> meanings = new ArrayList<>(last.getMeanings());
  +                int lastIndex = meanings.size() - 1;
  +                meanings.set(lastIndex, meanings.get(lastIndex) + " " + line.strip());
  +                entries.add(last.withMeanings(meanings));
  +                continue;
  +            }
               entries.add(parseLine(line, entries.size(), lineNumber));
  ```

### Medium

#### M1. Ở 100k, luồng ghi `.dat` giữ khóa từ điển khoảng 3 s: tìm kiếm chờ khoảng 2 s, add/edit/delete đơ EDT

- **Vị trí:** `core/SlangDictionary.java:217-219` (`writeObject` `synchronized` suốt lúc serialize); `io/DictionaryStore.java:343-348`; `service/SearchService.java:94-97` (đồng hồ bắt đầu sau khi có khóa nên màn hình vẫn hiện vài ms); `core/SlangDictionary.java:174,186,198` (add/replace/remove chờ cùng khóa trên EDT).
- **Nguyên nhân gốc:** mỗi lần lưu serialize toàn bộ từ điển trong một khóa duy nhất. Hoãn 1,5 s chỉ dời thời điểm chứ không bỏ được việc giữ khóa.
- **Yêu cầu liên quan:** G3b; quy tắc 1 s cho F1, F2, F4, F5, F6.
- **Kịch bản tái hiện (dữ liệu 100k):**
  1. Thêm một slang (F4).
  2. Chờ khoảng 2 s, rồi tìm một slang bất kỳ, hoặc thêm/sửa/xóa tiếp.
  3. Kết quả sai: lần tìm treo ở "Đang tìm…" khoảng 2 s rồi hiện "· 7 ms"; thêm/sửa/xóa thì cả cửa sổ đứng khoảng 1,7 s.
  4. Mong đợi: dưới 1 s, giao diện không đứng.
- **Đã kiểm chứng bằng:** repro tạm (store hoãn 1.500 ms, đo 1,7 s sau lần sửa): `search` wall 1.994 ms, `elapsedNanos` báo 7 ms; `add` wall 1.724 ms. PLAN.md:289 đã ghi giới hạn này cho add/edit/delete, chưa ghi cho tìm kiếm.
- **Hướng sửa đề xuất:** chỉ giữ khóa để chụp danh sách entry (O(n), vài ms), rồi build một bản sao và serialize bản sao **ngoài** khóa:
  ```diff
  // DictionaryStore.writeIfLatest
  -            writeAtomically(serialize(dictionary), dictionaryFile);
  +            SlangDictionary copy = dictionary.snapshot();   // synchronized: copy entries + nextId, rồi build() ngoài khóa
  +            writeAtomically(serialize(copy), dictionaryFile);
  ```
  Tốn thêm khoảng 2 s CPU trên luồng nền nhưng không ai phải chờ. Cách khác là ghi nhật ký thay đổi (journal) như PLAN đã nêu.

#### M2. Reset (F7) với dữ liệu 100k mất 1,9–2,1 s

- **Vị trí:** `ui/panels/ResetAction.java:101-106`; `core/SlangDictionary.java:81-91` (build lại mọi index, kể cả 2 BK-tree).
- **Yêu cầu liên quan:** quy tắc 1 s cho F7.
- **Kịch bản tái hiện:** `data/slang.txt` có 100.000 dòng → Ctrl+7 → Khôi phục. Màn hình "Đang khôi phục…" hiện khoảng 2 s (giao diện không đứng, nhưng chức năng chậm quá 1 s). Với file gốc 7,6k thì không vấn đề.
- **Đã kiểm chứng bằng:** repro tạm: parse + build 2.138 / 1.850 / 1.867 ms. `SearchBenchmarkTest` in "build index 2,182 ms".
- **Hướng sửa đề xuất:** chưa profile nên chưa biết phần nào chiếm nhiều nhất. Hướng khả thi: hoãn build hai `BKTree` tới lần tìm FUZZY/gợi ý đầu tiên, hoặc build chúng trên luồng nền sau khi đã thay từ điển, để Reset chỉ còn parse + HashMap + Trie + InvertedIndex. Nếu không sửa, ghi rõ trong file tự chấm rằng F7 chỉ vượt 1 s khi file gốc là 100k.

#### M3. Tìm-khi-gõ (mode mặc định của F1) không được ghi vào History

- **Vị trí:** `ui/panels/SearchPanel.java:198` (slang mặc định PREFIX), `:201` (`liveTimer` gọi `runSearch` trực tiếp), `:561-573` (chỉ `submit()` gọi `searchListener`).
- **Yêu cầu liên quan:** F3.
- **Kịch bản tái hiện:** mở app → gõ `lol` ở F1 → kết quả hiện ngay, xem xong → Ctrl+3. Kết quả: màn hình Lịch sử trống. Mong đợi: có dòng `lol`. Dòng gợi ý dưới ô tìm có ghi "nhấn Enter để lưu vào lịch sử", nhưng người chấm thử F1 rồi F3 theo thói quen sẽ dễ kết luận F3 không chạy.
- **Đã kiểm chứng bằng:** đọc code; đây là hành vi cố ý (ARCHITECTURE.md mục 4.12).
- **Hướng sửa đề xuất:** ghi lịch sử khi kết quả tìm-khi-gõ đã đứng yên một lúc (ví dụ 1,5 s không gõ tiếp), hoặc khi người dùng chọn một dòng kết quả / rời màn hình Search:
  ```diff
  +        historyTimer = new Timer(1500, e -> recordIfShown());   // restart trong onQueryEdited khi isLiveMode()
  ```
  `HistoryService.add` đã gộp lần tìm trùng với dòng mới nhất nên không sinh dòng lặp.

### Low

#### L1. Jar đặt trong thư mục không ghi được thì app không khởi động

- **Vị trí:** `io/AppPaths.java:36-41` (`UncheckedIOException`); `App.java:73-77` (`fatal`).
- **Mô tả:** không tạo được `data/` cạnh jar (thư mục chỉ đọc, `C:\Program Files`, USB khóa ghi) thì app báo lỗi rồi thoát, không thử thư mục khác.
- **Kịch bản:** copy jar vào thư mục không có quyền ghi → double-click → dialog "Không tải được dữ liệu slang: Cannot create data directory …" → thoát. Mong đợi: dùng `%USERPROFILE%\.slang-dictionary\` thay thế và báo vị trí.
- **Đã kiểm chứng bằng:** đọc code.
- **Hướng sửa:** trong `dataDir()`, khi `createDirectories` lỗi hoặc `!Files.isWritable(dir)` thì chuyển sang `Path.of(System.getProperty("user.home"), ".slang-dictionary")`.

#### L2. Mở hai cửa sổ app cùng lúc: thay đổi của cửa sổ này bị cửa sổ kia ghi đè

- **Vị trí:** `io/DictionaryStore.java:232-243` (cùng một tên `dictionary.dat.tmp`); `io/HistoryStore.java:99-101`; không có khóa một-instance ở `App.java:61-86`.
- **Mô tả:** mỗi process giữ một bản từ điển riêng và ghi **cả** từ điển. Process nào ghi sau thì thắng; thay đổi của process kia mất mà không báo. `history.dat` cũng vậy.
- **Kịch bản:** double-click jar hai lần → thêm `aaa` ở cửa sổ A, thêm `bbb` ở cửa sổ B → đóng cả hai → mở lại: chỉ còn một trong hai.
- **Đã kiểm chứng bằng:** đọc code (chưa chạy hai process thật).
- **Hướng sửa:** khi khởi động, `FileChannel.tryLock()` trên `data/app.lock`; không lấy được thì báo "ứng dụng đang chạy" rồi thoát.

#### L3. Dialog xác nhận xóa đặt nút mặc định là "Xóa"

- **Vị trí:** `ui/panels/EntryActions.java:213-216` (`options[0]` là giá trị khởi đầu).
- **Mô tả:** chọn một dòng → Delete → Enter là xóa xong, không hoàn tác được. Các dialog khác của app (Reset, trùng tên) đều cố ý không đặt focus vào nút phá hủy.
- **Đã kiểm chứng bằng:** đọc code.
- **Hướng sửa:** đổi tham số cuối thành `options[1]` (Hủy).

#### L4. F2: mode mặc định khớp nguyên từ; keyword chỉ gồm ký hiệu hoặc emoji không bao giờ ra kết quả

- **Vị trí:** `ui/panels/SearchPanel.java:199`; `core/Tokenizer.java:20`; `service/SearchService.java:251-255`.
- **Mô tả:** (a) mặc định EXACT nên `laugh` không trả về entry chỉ có `laughing`, phải tự đổi combo sang "Chứa". (b) mọi mode của F2 tách keyword theo `[^\p{L}\p{N}]+`, nên keyword như `&`, `:)`, `😂` cho tập token rỗng → 0 kết quả, dù có definition chứa chúng. Entry có nghĩa chỉ gồm ký hiệu/emoji không tìm được bằng F2.
- **Kịch bản:** F2 → gõ `&` → Enter → "Không có kết quả" (file gốc có nghĩa chứa `&`).
- **Đã kiểm chứng bằng:** đọc code.
- **Hướng sửa:** đặt mặc định của DEFINITION là SUBSTRING; khi keyword không có token nào thì duyệt thẳng `meaning.toLowerCase().contains(query)` trên mọi entry (O(n), 100k vẫn dưới 1 s).

#### L5. Bảng kết quả cắt ở 1.000 dòng, không có cách xem phần còn lại

- **Vị trí:** `ui/panels/SearchPanel.java:104` (`RESULT_LIMIT`), `:599`.
- **Mô tả:** đề ghi "hiển thị ra tất cả". Với file gốc, các keyword nguyên từ phổ biến đều dưới 1.000 (`you` 691, `of` 467, `the` 402), nên chỉ gặp khi tìm chuỗi con rất ngắn (`e`: 5.919 dòng). Dòng trạng thái có báo "Hiện 1.000 / N".
- **Đã kiểm chứng bằng:** đọc code + đếm trên `slang.txt`.
- **Hướng sửa:** thêm nút "Hiện tất cả" gọi lại `service.search(…, SearchService.UNLIMITED)`; `JTable` vẽ ảo nên 100k dòng vẫn chạy được.

## 6. Tài liệu nói khác code

- `docs/PLAN.md:13` ghi "bước 12 xong code nhưng chưa commit", nhưng `git log` đã có `1b9e890 feat(quiz)…` và chính `PLAN.md:62` đánh ✅.
- `docs/PLAN.md:15` ghi "16 commit trên 6 ngày"; `git rev-list --count HEAD` = 17.
- `docs/ARCHITECTURE.md:120` nói thư viện "được khai báo trong pom.xml và README"; repo chưa có `README.md`.
- `docs/ARCHITECTURE.md:299` nói "Id không bao giờ dùng lại"; sau Reset, `SlangDictionary.build` (`SlangDictionary.java:81-91`) đặt lại `nextId` nên id từ 7638 trở đi được cấp lại. Chưa thấy gây lỗi: `RandomPanel` và `QuizPanel` đối chiếu lại theo nội dung.
- `docs/ARCHITECTURE.md:336` nói `loadOrBuild` bắt `DictionaryFormatException`; code bắt mọi `IOException` (`DictionaryStore.java:286`). Xem C2.
- `docs/ARCHITECTURE.md:344` nói lỗi ghi "được báo qua `errorListener` thay vì làm sập app"; `Error` (tràn stack) không được báo (`DictionaryStore.java:347-354`). Xem C1.
- `docs/ARCHITECTURE.md:354` và `docs/PLAN.md:131`: đọc `.dat` 100k "0,8–1,5 s", serialize "~1,5–2,5 s"; đo lần này: đọc 1,8–2,2 s, ghi 2,9–3,6 s, file 34 MB (tài liệu ghi 30 MB).
- `docs/ARCHITECTURE.md:301` nói "UI chỉ đọc/sửa trên một luồng (EDT)", trong khi `SearchPanel.java:591-614` tìm trên luồng `search` (mục 4.12 của cùng tài liệu đã mô tả đúng).

## 7. Nghi vấn chưa kiểm chứng được

- **Hai instance ghi `dictionary.dat.tmp` cùng lúc có thể tạo file hỏng**, dẫn thẳng tới C2. Tin cậy: thấp–trung bình. Cần chạy hai process thật và cho cả hai lưu trong cùng khoảng 100 ms.
- **`target/slang-dictionary.jar` có thể cũ hơn source.** Jar ghi lúc 02:57, commit cuối lúc 03:02 (có thể chỉ khác tài liệu). Tin cậy: trung bình. Cần `.\mvnw.cmd package` lại rồi chạy thử jar ở thư mục khác.
- **Ngưỡng tràn stack của C1 trên JRE 17.** Repro chạy JDK 25 (ngưỡng giữa 500 và 800 ký tự). Trên JRE 17 hoặc `-Xss` khác thì ngưỡng khác, bản chất lỗi không đổi. Tin cậy lỗi tồn tại: cao.
- **Quiz: combo "số câu" đang giữ focus mà nhấn phím 1–4.** Combo có thể vừa nhảy mục (10/20 câu, làm bắt đầu lượt mới) vừa chọn đáp án (`QuizPanel.java:161-162`, binding `WHEN_ANCESTOR_OF_FOCUSED_COMPONENT`). Tin cậy: thấp. Cần chạy GUI.
- **Slang hoặc nghĩa bắt đầu bằng `<html>`** sẽ được `JLabel`/renderer của bảng vẽ như HTML (`SearchPanel.java:1040-1049`, `RandomPanel.java:243`). Tin cậy: trung bình, nhưng dữ liệu như vậy khó gặp.
- **Người chấm coi thời gian khởi động 100k (khoảng 2 s) là "chậm"?** Phụ thuộc cách chấm; đề chỉ nêu tìm kiếm và từng chức năng.

## 8. Những phần đã rà soát và thấy ổn

- **Nhất quán index:** `add`/`replace`/`remove` luôn đi qua cặp `index`/`unindex` (`SlangDictionary.java:229-261`); đổi key khi edit, slang trùng tên, nhiều nghĩa đều đúng; test `randomMutationsKeepEveryIndexInSync` so với bản build lại.
- **BKTree:** xóa mềm bằng cờ `deleted`, `search` bỏ qua nút đã xóa (`BKTree.java:117`), thêm lại thì gỡ cờ (`:64`); tìm gần đúng không trả entry đã xóa.
- **Header `.dat`:** magic, version, CRC32 được kiểm trước khi deserialize; có `ObjectInputFilter` (`DictionaryStore.java:74-75,196-211`).
- **Debounce và thoát app:** nút X, Ctrl+Q, menu Thoát đều qua `App.exit` → `closeAll` → `flush()` ghi ngay lần đang hẹn giờ (`DictionaryStore.java:358-368`); có shutdown hook; `history.dat` ghi ngay không hoãn; `settings.properties` ghi đồng bộ khi đổi.
- **Ghi atomic:** `.tmp` + `force(true)` + `ATOMIC_MOVE`, thử lại khi `AccessDeniedException` (`DictionaryStore.java:232-269`).
- **Đồng thời:** luồng tìm đọc trong `synchronized (dictionary)`; thao tác nhiều bước của edit nằm trong cùng khóa; EDT đọc không khóa chỉ song song với luồng chỉ-đọc; không thấy đường deadlock (không luồng nào giữ khóa mà chờ luồng khác).
- **Kết quả tìm cũ không đè kết quả mới:** vé `generation` được kiểm ở cả luồng tìm và EDT (`SearchPanel.java:589-613`).
- **Reset:** build trên luồng nền, khóa cửa sổ, thay object qua field `volatile`, lần lưu đang chờ của từ điển cũ bị thay bằng bản mới; lỗi file gốc thì giữ nguyên dữ liệu.
- **Parser với file trong repo:** header, dòng trống, BOM, khoảng trắng quanh `|`, nhiều dấu `` ` `` (chỉ tách ở dấu đầu), slang chứa `|`, slang trùng (thành hai entry), UTF-8.
- **Đường dẫn `data/`:** lấy qua `toURI()` nên xử lý được dấu cách và tiếng Việt; property → cạnh jar → working dir đúng thứ tự (`AppPaths.java:70-78`).
- **Threading Swing:** UI khởi tạo trong `invokeLater`; load, reset, tìm kiếm, ghi file đều ngoài EDT; cập nhật UI từ luồng nền đều qua `invokeLater`/`done()`; chỉ dùng `javax.swing.Timer`.
- **Model/view:** `ResultTableModel` và `HistoryTableModel` gọi `fireTableDataChanged`; không bảng nào có sorter nên không cần `convertRowIndexToModel`; sau khi xóa, dòng kế tiếp được chọn lại hợp lệ.
- **Search-as-you-type:** một `DocumentListener` đăng ký một lần, debounce 150 ms, không sửa document trong listener (cờ `syncing`).
- **Sửa/xóa entry cũ:** `EntryActions.currentSelection` đọc lại theo id, entry đã mất thì báo "không còn trong từ điển" (`EntryActions.java:164-179`).
- **Validate form:** trim, tên/nghĩa rỗng, backtick trong tên, `|` và xuống dòng trong nghĩa, nghĩa trùng; Enter không kích hoạt lưu hai lần.
- **Random và Quiz:** từ điển rỗng hoặc thiếu dữ liệu có màn hình riêng; 4 đáp án đôi một khác nhau, đúng một đáp án đúng kể cả khi nhiều slang chung nghĩa hoặc trùng tên; câu chưa trả lời được kiểm lại sau khi từ điển đổi.
- **FlatLaf:** cài trước khi tạo component, lỗi thì lùi về L&F hệ thống; đổi theme gọi `FlatLaf.updateUI()`; màu tính lại trong `updateUI()`.
- **i18n:** hai file `.properties` không có dấu `'` đơn lẻ làm hỏng `MessageFormat`; thiếu key hiện `!key!`.
- **Lỗi Java điển hình:** `SlangEntry` bất biến, `equals`/`hashCode` theo id; không so sánh String bằng `==`; regex tô sáng dùng `Pattern.quote`; không sửa collection khi đang duyệt (dùng `Iterator.remove`).
- **Menu:** `JMenuBar` dẫn tới đủ 10 chức năng, không chức năng nào chỉ có trên sidebar.
