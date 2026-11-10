# Kế hoạch đồ án Slang Dictionary (DAMH#1)

> Giải thích kiến trúc: [ARCHITECTURE.md](ARCHITECTURE.md) · Đề bài: [../DAMH_1.md](../DAMH_1.md)
>
> Ký hiệu: ✅ xong và đã commit · 🟡 xong code, CHƯA commit · ⬜ chưa làm

---

## 0. Trạng thái hiện tại

| | |
|---|---|
| Bước đang ở | **Xong bước 1–11, bước 12 xong code nhưng chưa commit** → commit bước 12 rồi làm bước 13 |
| Test | 232/232 pass (`.\mvnw.cmd test`) |
| Commit đã có | 16 commit trên 6 ngày: 04/11, 05/11, 08/11, 09/11, 10/11, 11/11 → **đã đủ luật commit** (≥ 10 commit, ≥ 5 ngày) |
| Thư mục `docs/` | `PLAN.md` và `ARCHITECTURE.md` đều đã được commit, git theo dõi bình thường |
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
| 4 | `SlangDictionary` + `Tokenizer` | nền F1–F10 | ✅ 10/11 |
| 5 | `AppPaths` + `DictionaryStore` (.dat) + load khi khởi động | yêu cầu chung 3, 4 | ✅ 10/11 |
| 6 | `SearchService` + `BigDataGenerator` + benchmark 100k | F1, F2 (logic) | ✅ 10/11 |
| 7 | Khung UI: menu, sidebar, CardLayout, đổi ngôn ngữ, đổi theme, màn hình loading | yêu cầu chung 2 | ✅ 10/11 |
| 8 | `SearchPanel` | **F1, F2** | ✅ 11/11 |
| 9 | History (service + store + panel) | **F3** | ✅ 11/11 |
| 10 | Add / Edit / Delete (service + dialog) | **F4, F5, F6** | ✅ 11/11 |
| 11 | Reset + Random | **F7, F8** | ✅ 11/11 |
| 12 | Quiz (2 loại) | **F9, F10** | ✅ 11/11 |
| 13 | README, file tự chấm điểm, rà lại, jar cuối | nộp bài | ⬜ |

### Lịch commit đề xuất

| Ngày | Việc | Số commit |
|---|---|---|
| 04/11, 05/11, 08/11 | đã có | 3 |
| 09/11 | bước 1, 2, 3 | 3 ✅ |
| 10/11 | bước 4 → 7 + docs | 6 ✅ |
| **11/11** | bước 8 → 13 | ~6 |
| Tổng | | ~18 commit trên 6 ngày ✓ |

---

## 3. Việc cần làm NGAY

1. ~~Đối chiếu ARCHITECTURE.md với code bước 4–5~~ → đã xong 10/11 (thêm mục 4.6–4.8, bảng test, khái niệm đa luồng).
2. ~~Dùng prompt của bước 6 → 12~~ → xong. Commit bước 12 rồi dùng prompt của **bước 13** (mục 5 bên dưới).
3. ~~Sửa `JAVA_HOME` bị thiếu dấu cách (`C:\ProgramFiles\...`)~~ → đã sửa 10/11 (cấp User = `C:\Program Files\Java\jdk-18.0.2`).

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
  - `save(SlangDictionary, Path)`: ghi header gồm magic number + số phiên bản định dạng **+ CRC32 của payload** (thêm khi làm), sau đó `ObjectOutputStream`, ghi vào `*.tmp` rồi `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` (thử lại vài lần nếu Windows báo AccessDenied)
  - `load(Path)`: sai magic/phiên bản/checksum hoặc dữ liệu hỏng thì ném `DictionaryFormatException`; có `ObjectInputFilter` chỉ cho phép class của app + `java.util`/`java.lang`
  - `loadOrBuild()`: thử đọc `.dat`; lỗi thì build từ file gốc (`data/slang.txt` nếu có, không thì `/data/slang.txt` trong jar) rồi lưu `.dat`. Trả về kèm nguồn đã dùng (DAT / TEXT) và thời gian load
  - `loadOriginal()`: dùng cho Reset
  - Ghi nền an toàn: ~~serialize ra `byte[]` ngay trên luồng gọi~~ → **đã đổi khi làm:** đo được serialize 100k mất ~2 s (UI sẽ đứng sau mỗi add/edit/delete), nên `saveInBackground` trả về ngay, **luồng nền vừa serialize vừa ghi**. Snapshot vẫn nhất quán vì `SlangDictionary.writeObject` và `add/replace/remove` cùng `synchronized` (kiểu `java.util.Vector`). Nhiều lần lưu dồn dập chỉ ghi 1 lần. Cung cấp `flush()` để chờ ghi xong khi thoát app (App gọi qua shutdown hook)
  - Số đo (máy dev): 7,6k entry: build ~50–100 ms, đọc .dat ~45–110 ms, file 2,3 MB · 100k entry: build ~1,6–3,3 s, đọc .dat ~0,8–1,5 s, serialize ~1,5–2,5 s, file 30 MB
  - **Sửa ở bước 10:** luồng ghi giữ khóa từ điển suốt lúc serialize (100k: ~2 s), nên lần tìm lại ngay sau khi thêm/sửa/xóa phải đứng chờ và màn hình hiện "~2000 ms". Đã thêm: `createDefault()` hoãn ghi **1,5 s sau lần sửa cuối** (debounce, constructor 2 tham số vẫn ghi ngay để test cũ giữ nguyên); `flush()` ghi ngay lần đang hẹn giờ; `SearchService` đo giờ **sau khi** lấy được khóa
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

**Bổ sung đặc tả (chốt 10/11, trước khi code; đổi được nếu thấy không hợp lý):**
- `SearchOutcome` là record riêng trong package `service`.
- **Phụ thuộc:** `SearchService(Supplier<SlangDictionary>)`, không giữ cứng một object từ điển, vì Reset (bước 11) thay bằng object từ điển mới.
- **`limit` và `totalMatches`:** sắp xếp xong mới cắt còn `limit` kết quả. `totalMatches` đếm số **entry** khớp, không phải số tên. Riêng PREFIX phải lấy hết kết quả từ Trie (`Integer.MAX_VALUE`) rồi mới cắt, nếu không `totalMatches` sẽ sai.
- **Điểm khi tìm SLANG:** tên trùng đúng query: 100 (ở mọi mode); PREFIX còn lại: 50; SUBSTRING: tên bắt đầu bằng query 50, chứa ở giữa 20; FUZZY: 100 − 10 × khoảng cách.
- **Điểm khi tìm DEFINITION:**
  - +100 nếu một nghĩa chứa nguyên cụm query; so trên **dãy token**, nên dấu câu và hoa thường không ảnh hưởng.
  - Với mỗi token của query: +10 nếu entry có đúng từ đó; khớp PREFIX/SUBSTRING mà không trùng đúng thì +5; khớp FUZZY thì +10 − 3 × khoảng cách nhỏ nhất.
- **k cho FUZZY definition (theo từng token):** độ dài ≤ 2 → k = 0, ≤ 4 → k = 1, còn lại k = 2. Nếu không, token như "a" với k = 1 sẽ khớp mọi từ 1–2 chữ.
- **Query đặc biệt:** query rỗng, hoặc DEFINITION mà không có token nào (chỉ gồm dấu câu) → kết quả rỗng; token trùng nhau trong query chỉ tính một lần.
- **`BigDataGenerator`:**
  - `count` là **tổng** số entry trong file, đã gồm 7.638 entry gốc.
  - Có hàm `static List<SlangEntry> generate(List<SlangEntry> base, int total, long seed)` để test dùng trong bộ nhớ, không phải ghi file; seed cố định để chạy lại ra đúng kết quả cũ.
- **Benchmark test:** đo cả **lần gọi đầu tiên** (chưa warm-up) của mỗi tổ hợp; query lấy từ dữ liệu thật, ví dụ slang `lol` / `lo` / `o` / `lool`, definition `laugh out loud` / `lau` / `aug` / `lauhg`.
- **Demo 100k trên app** (để quay video): copy `data/slang-100k.txt` thành `data/slang.txt` rồi xóa `data/dictionary.dat`, app sẽ build lại với 100k. Lúc này Reset cũng về bản 100k. Demo xong thì xóa 2 file đó để quay về dữ liệu gốc.

**Kết quả khi làm (10/11):**
- Thay đổi so với đặc tả: thưởng "nguyên cụm" chỉ áp dụng khi query có từ 2 từ trở lên (với 1 từ thì nó trùng với điểm "khớp đúng nguyên từ"). `loadBundledOriginal()` của generator là public để test benchmark dùng chung.
- Chạy generator: `java -cp target\slang-dictionary.jar lab01.slangdict._24127430.tools.BigDataGenerator [số lượng] [file ra]` → 100.000 entry (7.638 gốc + 92.362 tổng hợp), file khoảng 4 MB, sinh trong khoảng 0,3 s. Chạy từ jar thì file nằm ở `target\data\`, tức cạnh jar.
- Benchmark 100k, đo lần gọi đầu tiên, 3 lần chạy: build index 1,6–2,1 s. Mọi query đều < 20 ms, trừ các query khớp rất nhiều entry: SLANG SUBSTRING `"o"` (18.210 kết quả) 50–70 ms, DEFINITION PREFIX `"a"` (32.854) 40–48 ms, DEFINITION SUBSTRING `"e"` (88.464) 140–220 ms. Tất cả đều dưới xa mức 1 s.
- Bẫy đã gặp: chạy cả bộ test thì có lần query đầu tiên đo ra ~960 ms, do JVM dọn rác (GC) còn dồn lại từ lúc build 100k entry. Test gọi `System.gc()` sau khi build rồi mới đo.

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

**Chốt với bạn trước khi code (10/11):** lần chạy đầu dùng **tiếng Việt** và **theme tối**; có **nút mặt trời/mặt trăng** ở góc phải thanh menu (phím tắt Ctrl+Shift+L) để đổi sáng ↔ tối, ngoài menu Giao diện; Sidebar có **10 mục đánh số đúng như đề**, chia 3 nhóm TÌM KIẾM (1–3) / QUẢN LÝ (4–7) / KHÁM PHÁ (8–10). About ghi: Nguyễn Anh Khôi, MSSV 24127430.

**Kết quả khi làm (10/11):**
- Class mới:
  - `model/Theme`: nằm ở `model` vì `io.SettingsStore` cũng dùng, mà tầng io không được phụ thuộc ngược lên ui.
  - `ui/Feature`: enum 10 chức năng gồm số thứ tự, nhóm, card, key chữ và phím tắt. Sidebar và menu "Chức năng" cùng dựng từ enum này nên không bao giờ lệch nhau.
  - `ui/FeatureScreen`: màn hình nào cần biết mình được mở bằng F1 hay F2 thì cài interface này.
  - `ui/NumberBadgeIcon`, `ui/AppIcon`: icon tự vẽ bằng Java2D, không cần file ảnh.
  - `ui/LoadingWindow`, `ui/SwingLocalizer`, `ui/dialogs/AboutDialog`, `ui/panels/PlaceholderPanel`.
- F1–F3 và F8–F10 là **màn hình** (card). F4–F7 là **hành động** (dialog), nên bấm vào không đổi màn hình. Bước 8–12 gắn phần thật bằng `MainFrame.setScreen(card, panel)` và `MainFrame.setAction(feature, runnable)`, không phải sửa MainFrame. Hiện tại hành động chỉ hiện thông báo "đang xây dựng".
- Phím tắt Ctrl+1 … Ctrl+9, Ctrl+0 (chức năng 10); Ctrl+Q để thoát.
- Menu Tệp có cả Reset (giống mục 7 trong Chức năng) và Thoát. Tên ngôn ngữ trong menu luôn viết bằng chính ngôn ngữ đó ("Tiếng Việt", "English"), và menu ghi "Ngôn ngữ (Language)", để ai lỡ chọn nhầm vẫn tìm được đường quay lại.
- Thanh trạng thái hiện số slang, nguồn và thời gian tải, ví dụ "7.638 slang · đọc index từ dictionary.dat trong 85 ms" (số định dạng theo ngôn ngữ). Lỗi ghi `.dat` hiện màu đỏ ở bên phải; dialog cảnh báo chỉ hiện lần đầu.
- Màn hình "Đang tải…" chỉ hiện nếu việc tải lâu hơn 300 ms. Với 7,6k slang (~0,1 s) nó không hiện, tránh cửa sổ chớp lên rồi tắt; với 100k (~1,6 s) thì có hiện.
- Thoát app: ẩn cửa sổ ngay, chờ luồng ghi nền lưu xong `.dat` trên một luồng riêng rồi mới `System.exit`. Shutdown hook vẫn giữ làm lưới an toàn.
- Nút OK/Hủy/Có/Không của `JOptionPane` cũng được dịch (`SwingLocalizer`), vì Swing không có bản dịch tiếng Việt.
- Exception không ai bắt trên EDT sẽ hiện dialog báo lỗi thay vì im lặng.
- Đã chạy jar thật: đổi ngôn ngữ và theme ngay khi đang chạy, settings được lưu. Lần mở sau tự dùng lại English + Dark và đọc `.dat` (7.638 slang, ~0,25 s). Với 100k thì màn hình loading hiện, tạo index mất ~1,6 s.

### Bước 8: `SearchPanel` (F1, F2)

- Chọn tìm theo Slang / Definition (toggle), chọn mode (combo), ô tìm có placeholder và nút ✕ của FlatLaf
- Enter để tìm; chế độ PREFIX của slang thì tìm ngay khi đang gõ (debounce khoảng 150 ms)
- `JTable`: cột Slang | Meanings, chọn một dòng thì hiện chi tiết; thanh trạng thái "N kết quả · X ms"
- EXACT không ra kết quả thì gợi ý "Có phải bạn muốn tìm: …" bằng FUZZY
- Mỗi lần tìm thật (Enter/nút) thì ghi vào history (bước 9 sẽ nối dây)
- Có sẵn nút Add/Edit/Delete trên toolbar (bước 10 sẽ nối dây)

**Kết quả khi làm (11/11):**
- Bổ sung so với đặc tả:
  - **Tìm trên luồng nền** (một luồng "search" riêng), nên query nặng ở bản 100k (~0,2 s) không làm đứng giao diện. Kết quả cũ về muộn thì bị bỏ nhờ số thứ tự `AtomicLong`. Lâu hơn 120 ms thì hiện "Đang tìm…".
  - Vì tìm chạy trên luồng khác EDT, `SearchService` đọc index trong `synchronized (dictionary)`, cùng khóa với add/replace/remove. **Bước 10 không cần làm gì thêm**: cứ sửa từ điển trên EDT như bình thường.
  - Bảng hiện tối đa **1.000 dòng**; dòng trạng thái vẫn ghi đủ tổng số ("Hiện 1.000 / 88.464 kết quả đầu tiên · 230 ms").
  - Đổi tab Slang ↔ Định nghĩa ngay trên màn hình thì Sidebar và tiêu đề cửa sổ đổi theo F1 ↔ F2.
  - Mỗi target nhớ mode riêng: slang mặc định **Tiền tố**, definition mặc định **Chính xác**. Có dòng giải thích ngắn cho mode đang chọn.
  - Gợi ý "Có phải bạn muốn tìm" dùng cho cả 2 target và mọi mode trừ Gần đúng: slang gợi ý tối đa 5 tên, definition gợi ý 1 câu đã sửa lỗi gõ (`laugh out lound` → `laugh out loud`). Bấm gợi ý là tìm luôn. Logic nằm ở `SearchService.suggestions`.
  - Kết quả tốt nhất được chọn sẵn; khung chi tiết tô màu từ khóa khớp (khi tìm theo definition).
  - Phím: Enter tìm, ↓ xuống bảng, Esc xóa ô tìm, Delete trong bảng = chức năng 6.
- **Điểm nối cho các bước sau** (đã có sẵn trong `SearchPanel`):
  - Bước 9: `setSearchListener(historyService::add)` để ghi lịch sử; `search(target, mode, query)` để bấm đúp một dòng lịch sử là chuyển sang màn hình Search và tìm lại.
  - Bước 10: `selectedEntry()` cho Sửa/Xóa; `refresh()` để tìm lại sau khi dữ liệu đổi. Nút Thêm/Sửa/Xóa đã gọi `mainFrame.showFeature(ADD/EDIT/DELETE)`, nên chỉ cần `mainFrame.setAction(...)`.
  - Bước 10–11: `App.installScreens` đang tạo `new SearchService(loaded::dictionary)`. Khi có `DictionaryService` thì đổi thành nguồn "từ điển hiện tại", vì Reset thay object từ điển.
- Đã chạy jar thật: tìm-khi-gõ, Ctrl+2 sang Định nghĩa, gõ sai thì có gợi ý, tô từ khóa, đổi theme sáng/tối.

### Bước 9: History (F3)

- `HistoryService`: thêm (mới nhất ở đầu, tối đa 1000 dòng), lấy danh sách, xóa một dòng, xóa tất cả; lưu ngay qua `HistoryStore` (serialize `List<HistoryItem>` vào `data/history.dat`)
- `HistoryPanel`: bảng Thời gian | Loại | Mode | Query; double-click thì chuyển sang SearchPanel và chạy lại đúng query; nút "Xóa dòng chọn", "Xóa tất cả" (có confirm)
- Test cho service và store

**Kết quả khi làm (11/11):**
- Bổ sung so với đặc tả:
  - **Lưu nền** giống `DictionaryStore`: `HistoryStore.saveInBackground` trả về ngay, một luồng `history-writer` ghi file (tmp rồi đổi tên), nhiều lần lưu dồn dập chỉ ghi lần mới nhất. Khi thoát, App chờ cả `history.dat` lẫn `dictionary.dat` ghi xong.
  - File `history.dat` có header `HIST` + phiên bản và `ObjectInputFilter` (cho `model`, `java.util`, `java.lang`, `java.time`). Không có CRC vì lịch sử hỏng không làm sai chức năng nào. File hỏng thì bắt đầu với lịch sử rỗng và ghi đè ở lần lưu sau, app vẫn mở được.
  - Tìm lại **đúng lần mới nhất** (cùng loại, mode, query, không phân biệt hoa thường) thì chỉ cập nhật thời gian, không thêm dòng trùng. Trùng một dòng cũ hơn thì vẫn thêm dòng mới ở đầu.
  - Xóa được **nhiều dòng một lúc** (Ctrl/Shift để chọn), phím Delete; Enter hoặc bấm đúp để tìm lại; nút "Tìm lại". Xóa xong thì dòng kế tiếp được chọn sẵn, nên nhấn Delete liên tiếp được.
  - Chưa có lịch sử thì hiện màn hình trống có hướng dẫn (Ctrl+1 / Ctrl+2) thay cho bảng rỗng.
  - Thời gian định dạng theo ngôn ngữ (mẫu nằm trong `.properties`): `11/11/2026  14:30:05` / `Nov 11, 2026  2:30:05 PM`. Tiếng Anh có số ít/số nhiều ("1 search", "2 searches") nhờ `{0,choice,...}` của MessageFormat.
  - Lỗi ghi `history.dat` hiện trên thanh trạng thái, dialog cảnh báo riêng (`error.historySaveFailed`) chỉ hiện lần đầu.
  - Test dùng `testutil/ScratchDir` thay cho `@TempDir`: Windows Defender giữ file vừa ghi làm `@TempDir` dọn dẹp thất bại (đã gặp 2 lần khi chạy cả bộ test).
- **Điểm nối cho các bước sau:** bước 11 (Reset có checkbox "Xóa cả history") chỉ cần gọi `historyService.clear()`; `HistoryService` đang được tạo trong `App.main` và truyền vào `installScreens`.
- Đã chạy jar thật: tìm ở F1/F2 → Ctrl+3 thấy lịch sử (mới nhất ở đầu, dòng đầu được chọn sẵn); Enter tìm lại đúng target/mode/query; Delete xóa dòng; tắt app mở lại vẫn còn lịch sử; đổi sang English và theme sáng; xóa hết thì hiện màn hình trống.

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

**Kết quả khi làm (11/11):**
- Class mới:
  - `service/ConflictResolver`: interface hàm (functional interface) có record `Conflict` (thao tác, tên, nghĩa mới, các entry trùng tên, entry đang sửa) và `Decision` (lựa chọn + entry đích). Service gọi resolver mà không biết gì về Swing; test truyền lambda.
  - `service/ChangeResult`: kết quả `CHANGED` / `UNCHANGED` / `CANCELLED` + lựa chọn đã dùng + entry sau khi đổi + các entry bị xóa, để giao diện báo lại đúng câu.
  - `ui/panels/EntryActions`: nối dialog ↔ `DictionaryService` ↔ `SearchPanel`. Dialog đi qua interface `Prompts`, test thay bằng câu trả lời soạn sẵn.
- `DictionaryService` giữ **từ điển hiện tại** (`current()`): `SearchService` giờ lấy từ điển qua `dictionary::current`. **Bước 11 (Reset) chỉ cần gán object mới vào field `dictionary` rồi báo listener.**
- Bổ sung so với đặc tả:
  - Trùng tên so sánh **không phân biệt hoa thường**. Sửa mà không đổi tên (chỉ đổi nghĩa, hay đổi hoa/thường) thì không hỏi, kể cả khi vốn đã có entry khác cùng tên.
  - Dữ liệu nhập được chuẩn hóa: bỏ khoảng trắng hai đầu, bỏ dòng trống, bỏ nghĩa trùng (không phân biệt hoa thường). Tên không được chứa `` ` ``, nghĩa không được chứa `|` (hai ký tự ngăn cách của `slang.txt`); tên được phép chứa `|` vì file gốc có `:|`.
  - Thêm nghĩa mà mọi nghĩa đã có sẵn, hoặc sửa mà không đổi gì → `UNCHANGED`, không lưu.
  - Dialog trùng tên có nút **"Quay lại sửa"**: mở lại dialog nhập với đúng dữ liệu vừa gõ. Focus mặc định **không** nằm ở "Ghi đè" (nhiều mục → danh sách, một mục → nút quay lại) để lỡ Enter không mất dữ liệu.
  - Dialog nhập báo trước "⚠ Đã có N slang tên này" ngay khi đang gõ tên.
  - Thêm: tên điền sẵn là chữ đang gõ ở ô tìm slang (tìm không thấy → bấm Thêm). Enter ở ô tên: chưa có nghĩa thì chuyển xuống gõ nghĩa, có rồi thì lưu; Ctrl+Enter lưu; Insert/Delete/Alt+↑↓ trên bảng nghĩa.
  - Sửa/Xóa luôn làm trên slang đang chọn ở màn hình Search; gọi từ màn hình khác thì đưa về màn hình Search, chưa chọn thì hiện hướng dẫn. Bấm đúp một dòng kết quả = Sửa.
  - Sau khi thêm/sửa/xóa: dòng thông báo màu xanh ("✓ Đã thêm slang "yeet"."), tìm lại và **chọn sẵn entry vừa đổi**; entry không còn khớp lần tìm đang hiện (ví dụ đổi tên) thì tự tìm theo tên mới. Xóa xong thì chọn dòng kế tiếp. Thanh trạng thái cập nhật số slang.
- Giới hạn còn lại (chỉ ở 100k): nếu đúng lúc luồng ghi đang serialize (~2 s, bắt đầu 1,5 s sau lần sửa cuối) mà người dùng thêm/sửa/xóa tiếp, thao tác đó chờ tới khi ghi xong. Bỏ hẳn được bằng cách ghi nhật ký thay đổi (journal) thay vì serialize cả từ điển, nhưng phải đổi định dạng file của bước 5.
- Đã chạy jar thật (harness tự bấm, chụp màn hình) ở cả 7,6k và 100k: thêm mới, thêm trùng → thêm nghĩa, thêm bản trùng tên, đổi tên trùng 2 mục → quay lại → hủy, xóa có xác nhận, Sửa khi chưa chọn, theme sáng + English; tắt app rồi đọc lại `dictionary.dat` thấy đúng thay đổi.

### Bước 11: Reset + Random (F7, F8)

- `ResetDialog`: cảnh báo + checkbox "Xóa cả history"; `DictionaryService.reset()` build lại từ file gốc rồi lưu
- `RandomService`:
  - `onThisDay(LocalDate)`: `new Random(date.toEpochDay())` chọn entry, cả ngày ra cùng một kết quả
  - `random()`: chọn ngẫu nhiên bất kỳ
- `RandomPanel`: thẻ lớn hiện ngày, slang và các nghĩa; nút "Random khác", "Về slang hôm nay"
- Test: cùng ngày ra cùng entry; reset trả về đúng 7.638 entry

**Kết quả khi làm (11/11):**
- Class mới / thay đổi:
  - `DictionaryService`: `loadOriginal()` (build từ điển mới từ file gốc, gọi được trên luồng nền), `replaceDictionary(mới)` (gán field `volatile`, lưu nền, báo listener), `reset()` = gộp hai bước trên (dùng cho test), `originalSource()`.
  - `ui/panels/ResetAction`: nối `ResetDialog` ↔ luồng nền ↔ `DictionaryService` ↔ `HistoryService` ↔ `SearchPanel`. Giao diện đi qua interface `Ui`, hai `Executor` (luồng nền, EDT) truyền vào, nên test chạy trọn luồng mà không mở cửa sổ.
  - `ui/dialogs/ResetDialog`: nội dung là class `Form` (test được), `JOptionPane` chỉ bọc ngoài.
  - `service/RandomService`, `ui/panels/RandomPanel`.
  - `MainFrame.showDictionaryReset`, `SearchPanel.showReload`, `LoadingWindow(messageKey)`.
- Bổ sung / khác so với đặc tả:
  - **Slang hôm nay dùng rendezvous hashing** thay cho `new Random(ngày)`: entry có `score(ngày, id)` cao nhất thắng. Với `new Random(ngày).nextInt(size)`, thêm/xóa **bất kỳ** slang nào cũng đổi slang hôm nay (lúc quay video sẽ trông như lỗi). Ở đây thêm slang chỉ đổi kết quả với xác suất 1/n, sửa nghĩa không bao giờ đổi, xóa chỉ đổi nếu xóa đúng slang hôm nay. Giá phải trả: O(n) thay vì O(1), 100k vẫn chỉ vài ms. Giải thích ở ARCHITECTURE 4.15.
  - "Random khác" không bao giờ ra lại slang đang hiện. Thêm nút **Tra trong từ điển** (Enter): mở slang đó ở màn hình tìm kiếm (EXACT) để sửa/xóa. Phím R / H / Enter.
  - Reset **build trên luồng nền** (100k: ~1,5 s), cửa sổ bị khóa trong lúc build (không kịp mở Sửa/Xóa trên từ điển sắp bị thay), màn hình "Đang khôi phục…" chỉ hiện nếu lâu hơn 0,3 s. File gốc lỗi → báo lỗi, từ điển và lịch sử giữ nguyên.
  - Reset đọc `data/slang.txt` nếu có (ví dụ đang thử bản 100k), không thì bản nhúng trong jar; dialog ghi rõ đang dùng file nào và hiện số slang hiện có. Nút mặc định là **Hủy**, checkbox "Xóa cả lịch sử" mặc định không chọn, bị tắt khi lịch sử trống.
  - Sau Reset: màn hình Search tìm lại trên dữ liệu mới và hiện "✓ Đã khôi phục từ điển gốc (7.638 slang)"; thanh trạng thái ghi "khôi phục từ slang.txt gốc trong N ms"; dialog báo kết quả (kèm "Đã xóa lịch sử" nếu có).
  - Màn hình Slang hôm nay nghe thay đổi của từ điển: slang đang hiện bị sửa → hiện nghĩa mới; bị xóa → chọn slang khác; qua nửa đêm → slang của ngày mới. Màn hình đang ẩn thì để dành tới lúc được mở.
- Đã chạy app thật (harness tự bấm, chụp màn hình): slang hôm nay ở theme tối/tiếng Việt, Random khác, slang 14 nghĩa dài (xuống dòng, cuộn), theme sáng + English (ngày "Wednesday, November 11, 2026"), Tra trong từ điển, dialog Reset có tick xóa lịch sử, thông báo "Đã khôi phục 7.638 slang", Lịch sử trống, thanh trạng thái đúng.

### Bước 12: Quiz (F9, F10)

- `QuizService.next(QuizType)`:
  - SLANG_TO_DEFINITION: đề là tên slang; đáp án đúng là 1 nghĩa ngẫu nhiên của entry; 3 đáp án nhiễu là nghĩa của entry khác, **không** được là nghĩa của bất kỳ entry nào trùng tên với đề, và đôi một khác nhau (không phân biệt hoa thường)
  - DEFINITION_TO_SLANG: đề là 1 nghĩa; đáp án đúng là tên slang; 3 đáp án nhiễu là tên slang khác (không phân biệt hoa thường) và **không** entry nào trong số đó có nghĩa trùng với đề
  - Xáo trộn vị trí đáp án; từ điển có ít hơn 4 lựa chọn hợp lệ thì báo lỗi rõ ràng
- `QuizPanel(QuizType)`: đề bài chữ lớn, 4 nút (phím 1–4), tô xanh/đỏ, nút "Câu tiếp" (Enter), điểm / streak / streak cao nhất; chọn chế độ Vô hạn / 10 / 20 câu, hết lượt thì hiện bảng tổng kết
- Test: 1.000 câu ngẫu nhiên đều hợp lệ (4 đáp án khác nhau, đúng 1 đáp án đúng, không mơ hồ)

**Kết quả khi làm (11/11):**
- Class mới / thay đổi:
  - `service/QuizService`: `next(type)`, `next(type, avoidId)` (đề không lấy lại slang của câu vừa hỏi), `isValid(question)` (câu còn đúng với từ điển hiện tại không), `NotEnoughDataException`.
  - `ui/panels/QuizPanel`: một class cho cả F9 và F10, App tạo hai object. Có `dictionaryChanged()` nối vào `DictionaryService.addChangeListener`.
  - `ui/panels/RoundedCard`, `ui/panels/WidthTrackingPanel`: tách từ `RandomPanel` ra để F8 và F9/F10 dùng chung.
- Bổ sung / khác so với đặc tả:
  - **Hai nghĩa "trùng nhau"** khi giống nhau sau khi bỏ hoa thường và dấu câu (`meaningKey`: "Laugh out loud!" = "laugh out loud"), vì hai đáp án gần như y hệt trên màn hình cũng là mơ hồ. File gốc có hơn 100 nghĩa dùng chung cho nhiều slang khác tên, nên luật "đáp án nhiễu không được có nghĩa của đề" thật sự cần.
  - **Chọn đáp án nhiễu:** bốc ngẫu nhiên O(1) (`randomEntry`), bỏ entry không hợp lệ; bốc 64 lần chưa đủ thì duyệt hết từ điển. Thử tối đa 20 entry làm đề; từ điển ≤ 20 entry thì thử hết, nên báo "không đủ dữ liệu" là chắc chắn. 100k slang: câu đầu ~0,5 ms, trung bình ~0,04 ms.
  - Từ điển đổi (thêm/sửa/xóa/Reset) thì câu **chưa trả lời** được kiểm tra lại bằng `isValid`: chỉ đổi câu khi đáp án đúng không còn đúng hoặc một đáp án nhiễu thành đúng, không tính điểm. Câu đã trả lời và bảng tổng kết giữ nguyên.
  - Nút đáp án là `JButton` chứa `JTextArea` (đáp án dài tự xuống dòng); `JTextArea` trả `contains = false` để cú bấm rơi xuống nút. Nút đáp án không nhận focus, nên Enter không vô tình chọn đáp án; chọn bằng chuột hoặc phím 1–4 (cả bàn phím số).
  - Sau khi trả lời: đáp án đúng tô xanh ✓, đáp án chọn sai tô đỏ ✗, các đáp án còn lại mờ đi; slang có nhiều nghĩa thì liệt kê đủ các nghĩa. Từ câu đúng thứ 3 liên tiếp thì khen chuỗi đúng.
  - Thống kê: CÂU (n hoặc n/10), ĐÚNG (đúng/đã trả lời), CHUỖI ĐÚNG, KỶ LỤC (giữ suốt lúc app chạy, không mất khi chơi lại). Đổi số câu = bắt đầu lượt mới.
  - Bảng tổng kết: điểm, %, chuỗi đúng dài nhất của lượt, lời nhận xét theo %, danh sách câu sai ("đề → đáp án đúng (bạn chọn: …)"). Enter = chơi lượt mới.
  - Từ điển không đủ dữ liệu (ví dụ xóa gần hết) thì hiện hướng dẫn thêm slang (Ctrl+4) / khôi phục (Ctrl+7); thêm đủ slang thì tự có câu hỏi.
- Lỗi tìm ra khi chạy app thật: đổi theme lúc phần thân đang ẩn (bảng tổng kết) thì lần hiện sau còn màu theme cũ, vì FlatLaf chỉ cập nhật component đang nằm trong cửa sổ. Đã sửa: `QuizPanel.updateUI` tự cập nhật các phần thân đang ẩn.
- Sửa thêm: `SettingsStoreTest` chuyển từ `@TempDir` sang `ScratchDir` (lần chạy cả bộ test bị lỗi dọn thư mục tạm do Windows Defender, đúng lỗi đã ghi ở bước 9).
- Đã chạy jar thật (harness tự bấm, chụp màn hình): F9 theme tối/tiếng Việt (câu hỏi, chọn sai, chọn đúng), F10 theme sáng/English, lượt 10 câu ra bảng tổng kết, đổi lại theme tối/tiếng Việt trên bảng tổng kết.

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
