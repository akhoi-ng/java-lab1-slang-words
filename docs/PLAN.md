# Kế hoạch đồ án Slang Dictionary (DAMH#1)

> Giải thích kiến trúc: [ARCHITECTURE.md](ARCHITECTURE.md) · Đề bài: [../DAMH_1.md](../DAMH_1.md)
>
> Ký hiệu: ✅ xong và đã commit · 🟡 xong code, CHƯA commit · ⬜ chưa làm

---

## 0. Trạng thái hiện tại

| | |
|---|---|
| Bước đang ở | **Xong bước 1–3, đã commit ngày 09/11** → tiếp theo là bước 4 (ngày 10/11) |
| Test | 19/19 pass (`.\mvnw.cmd test`) |
| Commit đã có | 6 commit trên 4 ngày: 04/11, 05/11, 08/11, 09/11 (đã push) + commit PLAN.md ngày 10/11 |
| Thư mục `docs/` | `PLAN.md` được commit; `ARCHITECTURE.md` chỉ dùng ở máy local để học, **không commit** (`docs/` nằm trong `.git/info/exclude`) |
| Luật commit của đề | ≥ 10 commit **và** trải trên ≥ 5 ngày, nếu không đạt thì đồ án 0 điểm. **Bắt buộc phải có commit vào ngày 09/11 và 10/11.** Không sửa lùi ngày commit. |
| Deadline | khoảng 10–11/11 (cần xác nhận lại trên Moodle) |

---

## 1. Các quyết định đã chốt

| Mảng | Quyết định |
|---|---|
| Nền tảng | Swing + FlatLaf (light/dark), Maven + wrapper, Java 17, fat-jar `target/slang-dictionary.jar` |
| Package | `lab01.slangdict._24127430` |
| Giao diện | JMenuBar + Sidebar bên trái + CardLayout; song ngữ Anh/Việt **đổi ngay khi đang chạy**, nhớ lựa chọn cho lần sau |
| Mô hình dữ liệu | `SlangEntry` có id riêng, cho phép nhiều entry trùng tên; mỗi entry có nhiều nghĩa |
| Add trùng tên | 3 lựa chọn: ghi đè (OVERWRITE) / thêm nghĩa vào entry cũ (ADD_MEANING) / tạo entry riêng cùng tên (NEW_ENTRY) |
| Edit | Sửa được cả tên lẫn nghĩa; đổi tên trùng thì hỏi lại 3 lựa chọn như khi add |
| Delete | Xóa cả entry, có confirm |
| Search slang | EXACT (HashMap), PREFIX (Trie), SUBSTRING (duyệt tập tên), FUZZY (BKTree) |
| Search definition | EXACT word (InvertedIndex), PREFIX (TreeMap range), SUBSTRING (duyệt từ vựng), FUZZY (BKTree trên từ vựng); nhiều từ khóa thì **AND + xếp hạng** (khớp cả cụm lên đầu) |
| Lưu trữ | Serialize toàn bộ `SlangDictionary` thành `data/dictionary.dat` **sau mỗi thay đổi** (ghi file tạm rồi rename, chạy nền). File `.dat` hỏng thì build lại từ text |
| File gốc | `slang.txt` nhúng trong jar (`/data/slang.txt`, chỉ đọc). Nếu có `data/slang.txt` cạnh jar thì ưu tiên dùng file đó |
| Thư mục data | `data/` nằm **cạnh file jar**, không phụ thuộc thư mục đang chạy lệnh |
| History | Ghi cả search slang lẫn definition (target, mode, query, thời điểm); lưu qua các lần chạy; double-click để search lại; xóa một dòng / xóa tất cả |
| Reset | Dialog xác nhận có checkbox "Xóa cả history" |
| Random | "On this day" chọn theo seed của ngày (cả ngày cùng một từ) + nút "Random khác" |
| Quiz | Đáp án nhiễu không trùng và không mơ hồ; điểm + streak; tô xanh/đỏ ngay sau khi chọn; phím tắt 1–4; chế độ N câu (10/20) có bảng tổng kết |
| Phụ trợ | Tool sinh 100k slang + hiện thời gian search (ms) trên UI; JUnit 5; README + file tự chấm điểm |

---

## 2. Lộ trình 13 bước (mỗi bước là một commit)

| # | Bước | Chức năng đề | Trạng thái |
|---|---|---|---|
| 0 | Khung Maven + wrapper + package | — | ✅ 08/11 |
| 1 | model | — | ✅ 09/11 |
| 2 | Parser `slang.txt` | nền | ✅ 09/11 |
| 3 | Cấu trúc dữ liệu generic (Trie, BKTree, InvertedIndex, Levenshtein) | nền F1, F2 | ✅ 09/11 |
| 4 | `SlangDictionary` + `Tokenizer` | nền F1–F10 | ⬜ |
| 5 | `AppPaths` + `DictionaryStore` (.dat) + load khi khởi động | yêu cầu chung 3, 4 | ⬜ |
| 6 | `SearchService` + `BigDataGenerator` + benchmark 100k | F1, F2 (logic) | ⬜ |
| 7 | Khung UI: menu, sidebar, CardLayout, đổi ngôn ngữ, đổi theme, màn hình loading | yêu cầu chung 2 | ⬜ |
| 8 | `SearchPanel` | **F1, F2** | ⬜ |
| 9 | History (service + store + panel) | **F3** | ⬜ |
| 10 | Add / Edit / Delete (service + dialog) | **F4, F5, F6** | ⬜ |
| 11 | Reset + Random | **F7, F8** | ⬜ |
| 12 | Quiz (2 loại) | **F9, F10** | ⬜ |
| 13 | README, file tự chấm điểm, rà lại, jar cuối | nộp bài | ⬜ |

### Lịch commit đề xuất

| Ngày | Việc | Số commit |
|---|---|---|
| 04/11, 05/11, 08/11 | đã có | 3 |
| 09/11 | bước 1, 2, 3 | 3 ✅ |
| **10/11** | bước 4 → 13 | ~10 |
| Tổng | | ~16 commit trên 5 ngày ✓ |

Nếu deadline là 11/11 thì chuyển bớt các bước 10–13 sang ngày 11/11 để commit rải đều hơn.

---

## 3. Việc cần làm NGAY

1. Đọc lại [ARCHITECTURE.md](ARCHITECTURE.md) mục 4 để nắm phần cấu trúc dữ liệu.
2. Dùng prompt của **bước 4** (mục 5 bên dưới).

---

## 4. Đặc tả chi tiết từng bước còn lại

### Bước 4: `SlangDictionary` + `Tokenizer` (package `core`)

**Tokenizer**
- `List<String> tokens(String text)`: chuyển về chữ thường (`Locale.ROOT`), tách theo ký tự không phải chữ hoặc số (`[^\p{L}\p{N}]+`), bỏ phần rỗng, **giữ nguyên thứ tự** (dùng cho xếp hạng khớp cả cụm).
- `String normalizeName(String slang)`: `strip()` + chuyển chữ thường, dùng làm key khi tra tên slang (không phân biệt hoa thường).

**SlangDictionary** (`Serializable`, có `serialVersionUID`), gồm các field:
- `Map<Integer, SlangEntry> entries`: id → entry
- `List<Integer> idList` + `Map<Integer, Integer> positionOf`: lấy entry ngẫu nhiên trong O(1); khi xóa thì đổi chỗ phần tử cần xóa với phần tử cuối rồi xóa cuối (swap-remove), cũng O(1)
- `Map<String, Set<Integer>> idsByName`: tên đã chuẩn hóa → id (EXACT)
- `Trie<Integer> nameTrie`: tên đã chuẩn hóa → id (PREFIX)
- `BKTree<String> nameTree`: các tên khác nhau (FUZZY); xóa mềm khi không còn entry nào mang tên đó
- `InvertedIndex<String, Integer> wordIndex`: từ trong definition → id
- `BKTree<String> vocabularyTree`: các từ khác nhau trong definition (FUZZY definition)
- `int nextId`

Các method:
- `static SlangDictionary build(List<SlangEntry>)`, `int size()`, `SlangEntry get(int id)`, `Collection<SlangEntry> all()`
- `Set<Integer> idsByName(String)`, `List<Integer> idsWithNamePrefix(String, int limit)`, `Set<String> names()`, `List<BKTree.Match<String>> fuzzyNames(String, int k)`
- `Set<Integer> idsWithWord(String)`, `NavigableSet<String> wordsWithPrefix(String)`, `Set<String> vocabulary()`, `List<BKTree.Match<String>> fuzzyWords(String, int k)`
- `SlangEntry randomEntry(Random)`
- **Thay đổi dữ liệu (bắt buộc giữ mọi index đồng bộ):** `SlangEntry add(String slang, List<String> meanings)` (cấp id mới), `void replace(SlangEntry updated)` (cùng id: gỡ index cũ rồi gắn index mới), `SlangEntry remove(int id)`

**Test:**
- build từ file gốc: 7.638 entry
- tra tên không phân biệt hoa thường
- tên trùng nhau
- sau một loạt add/replace/remove ngẫu nhiên, mọi index phải **giống hệt** index build lại từ đầu
- serialize rồi đọc lại vẫn đúng

### Bước 5: `AppPaths` + `DictionaryStore` + load khi khởi động

- `AppPaths.dataDir()`:
  - system property `slangdict.data.dir` nếu có (dùng cho test)
  - nếu đang chạy từ jar: thư mục chứa jar + `/data`
  - nếu đang chạy trong IDE (`target/classes`): thư mục làm việc + `/data`
  - tự tạo thư mục nếu chưa có
- `AppPaths.dictionaryFile()`, `historyFile()`, `settingsFile()`, `externalOriginalFile()` (= `data/slang.txt`)
- `DictionaryStore`:
  - `save(SlangDictionary, Path)`: ghi header gồm magic number + số phiên bản định dạng, sau đó `ObjectOutputStream`, ghi vào `*.tmp` rồi `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`
  - `load(Path)`: sai magic/phiên bản hoặc lỗi đọc thì ném exception riêng
  - `loadOrBuild()`: thử đọc `.dat`; lỗi thì build từ file gốc (`data/slang.txt` nếu có, không thì `/data/slang.txt` trong jar) rồi lưu `.dat`. Trả về kèm nguồn đã dùng (DAT / TEXT) và thời gian load
  - `loadOriginal()`: dùng cho Reset
  - Ghi nền an toàn: **serialize ra `byte[]` ngay trên luồng gọi** để có snapshot nhất quán, sau đó mới ghi file trên một `ExecutorService` đơn luồng. Cung cấp `flush()` để chờ ghi xong khi thoát app
- `App`: load dữ liệu trước khi hiện giao diện, tạm in log số entry + thời gian (bước 7 sẽ thay bằng màn hình loading)
- **Test** (dùng `@TempDir`): save rồi load ra đúng dữ liệu; file `.dat` hỏng thì build lại từ text; lần chạy thứ 2 đọc từ DAT

### Bước 6: `SearchService` + `BigDataGenerator`

- `SearchOutcome search(SearchTarget, SearchMode, String query, int limit)` trả về record gồm `List<SearchResult> results`, `int totalMatches`, `long elapsedNanos`
- **SLANG:** chuẩn hóa query
  - EXACT: `idsByName`
  - PREFIX: Trie
  - SUBSTRING: duyệt `names()` với `contains`
  - FUZZY: BKTree, k = 1 nếu độ dài ≤ 4, ngược lại k = 2; điểm cao hơn khi khoảng cách nhỏ hơn
- **DEFINITION:** tách query thành token; với mỗi token, lấy tập id theo mode:
  - EXACT: `idsWithWord`
  - PREFIX: hợp các id của `wordsWithPrefix`
  - SUBSTRING: hợp các id của mọi từ vựng `contains` token
  - FUZZY: hợp các id của `fuzzyWords`
  - Sau đó **giao (AND)** các tập, bắt đầu từ tập nhỏ nhất
  - Chấm điểm: definition chứa nguyên cụm query (không phân biệt hoa thường) +100; mỗi token khớp đúng nguyên từ +10; khớp fuzzy bị trừ theo khoảng cách
- Query rỗng thì trả kết quả rỗng; sắp xếp bằng `Collections.sort` (Comparable của `SearchResult`)
- `BigDataGenerator main(count=100000, out=data/slang-100k.txt)`: lấy dữ liệu gốc, thêm slang tổng hợp (tên ngẫu nhiên, nghĩa ghép từ từ vựng thật), ghi bằng `SlangFileParser.write`
- **Test benchmark:** build từ điển 100k trong bộ nhớ, mỗi tổ hợp target × mode phải chạy **< 1000 ms** (thực tế nên chỉ vài ms); test đúng sai cho từng mode trên dữ liệu nhỏ

### Bước 7: Khung UI

- `Messages`: `setLocale(Locale)`, `addListener(Runnable)` để mọi màn hình tự cập nhật chữ khi đổi ngôn ngữ; thêm đầy đủ key vào cả hai file `.properties`
- `SettingsStore`: lưu ngôn ngữ và theme vào `data/settings.properties`
- `ThemeManager`: `apply(Theme.LIGHT/DARK)` dùng `FlatLaf.updateUI()`
- `MainFrame`:
  - JMenuBar:
    - File: Reset, Exit
    - Chức năng: 10 mục
    - View: Light/Dark
    - Language: English / Tiếng Việt
    - Help: About (MSSV, thư viện dùng)
  - `Sidebar` bên trái: các nút chuyển card
  - `CardLayout` bên phải: placeholder cho từng màn hình
- Load dữ liệu bằng `SwingWorker`, có dialog/progress "Đang tải…"; khi đóng cửa sổ thì gọi `DictionaryStore.flush()`

### Bước 8: `SearchPanel` (F1, F2)

- Chọn tìm theo Slang / Definition (toggle), chọn mode (combo), ô tìm có placeholder và nút ✕ của FlatLaf
- Enter để tìm; chế độ PREFIX của slang thì tìm ngay khi đang gõ (debounce khoảng 150 ms)
- `JTable`: cột Slang | Meanings, chọn một dòng thì hiện chi tiết; thanh trạng thái "N kết quả · X ms"
- EXACT không ra kết quả thì gợi ý "Có phải bạn muốn tìm: …" bằng FUZZY
- Mỗi lần tìm thật (Enter/nút) thì ghi vào history (bước 9 sẽ nối dây)
- Có sẵn nút Add/Edit/Delete trên toolbar (bước 10 sẽ nối dây)

### Bước 9: History (F3)

- `HistoryService`: thêm (mới nhất ở đầu, tối đa 1000 dòng), lấy danh sách, xóa một dòng, xóa tất cả; lưu ngay qua `HistoryStore` (serialize `List<HistoryItem>` vào `data/history.dat`)
- `HistoryPanel`: bảng Thời gian | Loại | Mode | Query; double-click thì chuyển sang SearchPanel và chạy lại đúng query; nút "Xóa dòng chọn", "Xóa tất cả" (có confirm)
- Test cho service và store

### Bước 10: Add / Edit / Delete (F4, F5, F6)

- `DictionaryService`:
  - `add(slang, meanings, ConflictResolver)`: nếu tên đã tồn tại thì hỏi resolver (UI sẽ hiện `ConflictDialog`; nếu có nhiều entry cùng tên thì cho chọn entry đích):
    - OVERWRITE: `replace` entry đích bằng nghĩa mới
    - ADD_MEANING: nối các nghĩa mới vào entry đích (bỏ nghĩa trùng)
    - NEW_ENTRY: `add` thêm một entry cùng tên
  - `edit(id, newSlang, newMeanings, ConflictResolver)`: nếu tên mới trùng với entry **khác**:
    - OVERWRITE: xóa entry kia, giữ entry đang sửa
    - ADD_MEANING: gộp nghĩa vào entry kia rồi xóa entry đang sửa
    - NEW_ENTRY: giữ cả hai
  - `delete(id)`
  - Mỗi thay đổi đều gọi lưu nền
- `EntryEditorDialog` (dùng chung cho add và edit): ô tên + danh sách nghĩa (thêm/sửa/xóa/lên/xuống), kiểm tra dữ liệu hợp lệ
- `ConflictDialog`: 3 nút + danh sách entry trùng tên
- Delete: `JOptionPane` xác nhận, hiện tên và nghĩa của entry sắp xóa
- Test service cho cả 3 lựa chọn × add/edit

### Bước 11: Reset + Random (F7, F8)

- `ResetDialog`: cảnh báo + checkbox "Xóa cả history"; `DictionaryService.reset()` build lại từ file gốc rồi lưu
- `RandomService`:
  - `onThisDay(LocalDate)`: `new Random(date.toEpochDay())` chọn entry, cả ngày ra cùng một kết quả
  - `random()`: chọn ngẫu nhiên bất kỳ
- `RandomPanel`: thẻ lớn hiện ngày, slang và các nghĩa; nút "Random khác", "Về slang hôm nay"
- Test: cùng ngày ra cùng entry; reset trả về đúng 7.638 entry

### Bước 12: Quiz (F9, F10)

- `QuizService.next(QuizType)`:
  - SLANG_TO_DEFINITION: đề là tên slang; đáp án đúng là 1 nghĩa ngẫu nhiên của entry; 3 đáp án nhiễu là nghĩa của entry khác, **không** được là nghĩa của bất kỳ entry nào trùng tên với đề, và đôi một khác nhau (không phân biệt hoa thường)
  - DEFINITION_TO_SLANG: đề là 1 nghĩa; đáp án đúng là tên slang; 3 đáp án nhiễu là tên slang khác (không phân biệt hoa thường) và **không** entry nào trong số đó có nghĩa trùng với đề
  - Xáo trộn vị trí đáp án; từ điển có ít hơn 4 lựa chọn hợp lệ thì báo lỗi rõ ràng
- `QuizPanel(QuizType)`: đề bài chữ lớn, 4 nút (phím 1–4), tô xanh/đỏ, nút "Câu tiếp" (Enter), điểm / streak / streak cao nhất; chọn chế độ Vô hạn / 10 / 20 câu, hết lượt thì hiện bảng tổng kết
- Test: 1.000 câu ngẫu nhiên đều hợp lệ (4 đáp án khác nhau, đúng 1 đáp án đúng, không mơ hồ)

### Bước 13: Hoàn thiện và nộp bài

- `README.md`: cách build/chạy, cấu trúc dữ liệu, thư viện dùng (FlatLaf, Apache 2.0), **phần trăm tham khảo**
- File tự chấm điểm (`.txt`): điểm từng chức năng theo mục IV.2 + link video YouTube
- Rà lại toàn bộ UI ở cả hai ngôn ngữ và hai theme; `.\mvnw.cmd package`; chạy thử jar ở một thư mục khác
- Chụp hình lịch sử commit (`git log` hoặc trang commits trên GitHub)
- Đóng gói `24127430_<điểm>.zip` theo mục IV.8 (checklist ở mục 6)

---

## 5. Prompt cho agent ở từng bước

Mỗi prompt đều dùng được cho **phiên làm việc mới**, vì agent sẽ tự đọc PLAN và ARCHITECTURE.

### Mẫu chung (thay `<N>` bằng số bước)

```
Làm bước <N> trong docs/PLAN.md. Đọc docs/PLAN.md (mục 1 và đặc tả bước <N> ở mục 4)
và docs/ARCHITECTURE.md trước khi code.
- Viết code + unit test đúng đặc tả, chạy .\mvnw.cmd test cho tới khi pass hết.
- Bước có UI thì chạy app để kiểm tra.
- Cập nhật trạng thái bước <N> trong PLAN.md (mục 0 và mục 2) và cập nhật ARCHITECTURE.md
  (đổi ⬜ thành ✅, thêm phần giải thích cho class mới).
- Giải thích ngắn các điểm chính để mình thuyết trình được.
- KHÔNG tự commit: đưa mình lệnh git add/commit cho riêng bước này.
```

### Prompt cụ thể

| Bước | Prompt |
|---|---|
| 4 | `Làm bước 4 trong docs/PLAN.md: SlangDictionary + Tokenizer. (theo mẫu chung)` |
| 5 | `Làm bước 5 trong docs/PLAN.md: AppPaths + DictionaryStore, App load dữ liệu khi khởi động. (theo mẫu chung)` |
| 6 | `Làm bước 6 trong docs/PLAN.md: SearchService 4 mode × 2 target, BigDataGenerator, test benchmark 100k < 1s. Báo mình thời gian đo được. (theo mẫu chung)` |
| 7 | `Làm bước 7 trong docs/PLAN.md: khung UI (menu, sidebar, CardLayout, đổi ngôn ngữ/theme, loading). Chạy app và chụp màn hình cho mình xem. (theo mẫu chung)` |
| 8 | `Làm bước 8 trong docs/PLAN.md: SearchPanel cho F1, F2. (theo mẫu chung)` |
| 9 | `Làm bước 9 trong docs/PLAN.md: History F3. (theo mẫu chung)` |
| 10 | `Làm bước 10 trong docs/PLAN.md: Add/Edit/Delete F4–F6 với 3 lựa chọn khi trùng tên. (theo mẫu chung)` |
| 11 | `Làm bước 11 trong docs/PLAN.md: Reset F7 + Random F8. (theo mẫu chung)` |
| 12 | `Làm bước 12 trong docs/PLAN.md: Quiz F9, F10. (theo mẫu chung)` |
| 13 | `Làm bước 13 trong docs/PLAN.md: README, file tự chấm điểm, rà lại toàn bộ, build jar cuối, liệt kê checklist nộp bài còn thiếu. (theo mẫu chung)` |

### Prompt tiện ích

| Mục đích | Prompt |
|---|---|
| Hỏi lại tiến độ | `Đọc docs/PLAN.md và git log, cho mình biết đang ở bước nào và việc cần làm tiếp theo.` |
| Hiểu một phần code | `Giải thích <tên class/method> như đang chuẩn bị thuyết trình, kèm ví dụ cụ thể.` |
| Ôn trước khi quay video | `Dựa vào docs/ARCHITECTURE.md, hỏi mình 10 câu thầy có thể hỏi về cấu trúc dữ liệu và chấm câu trả lời của mình.` |
| Kiểm tra trước khi nộp | `Đối chiếu project với mục IV của DAMH_1.md và checklist mục 6 trong docs/PLAN.md, liệt kê những gì còn thiếu.` |

---

## 6. Checklist nộp bài (mục IV của đề)

- [ ] ≥ 10 commit trải trên ≥ 5 ngày
- [ ] 10 chức năng chạy đúng, mỗi chức năng < 1s với 100k slang
- [ ] `target/slang-dictionary.jar` chạy được bằng double-click / `java -jar`
- [ ] File data (`slang.txt`)
- [ ] Hình chụp lịch sử commit
- [ ] File `.txt` tự chấm điểm từng chức năng + link video YouTube
- [ ] Video YouTube: cấu trúc dữ liệu, % tham khảo, demo trọn một chức năng
- [ ] Đánh dấu phần tham khảo trong code + khai tổng % tham khảo (code do AI viết cũng cần khai trung thực)
- [ ] Đặt tên file nộp `24127430_<điểm>.zip`, gồm source code, jar, data, hình commit, file txt
