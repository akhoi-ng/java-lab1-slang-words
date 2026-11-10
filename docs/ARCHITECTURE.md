# Kiến trúc đồ án Slang Dictionary

> Tài liệu giải thích toàn bộ cấu trúc project, dùng để ôn lại và chuẩn bị video thuyết trình.
> Kế hoạch và tiến độ xem ở [PLAN.md](PLAN.md).
>
> Ký hiệu: ✅ đã có code thật · ⬜ mới có khung (class rỗng)

---

## 1. Bức tranh tổng thể: app chạy thế nào

```
Double-click slang-dictionary.jar
  │
  ▼
App.main()  ── đọc data/settings.properties (ngôn ngữ, theme) → cài ngôn ngữ + FlatLaf
  │
  ▼  SwingWorker (luồng nền), quá 0,3 s thì hiện cửa sổ "Đang tải…"
Đọc data/dictionary.dat (từ điển ĐÃ INDEX sẵn từ lần chạy trước)
  │   └─ nếu chưa có / bị hỏng → đọc slang.txt gốc bằng SlangFileParser → build index → lưu .dat
  ▼
MainFrame hiện lên: menu + sidebar 10 chức năng + các màn hình (CardLayout) + thanh trạng thái
  │
  ▼  người dùng gõ "lol" và bấm Search
SearchPanel (ui) ─gọi─► SearchService (service) ─tra─► SlangDictionary (core) ─dùng─► Trie / BKTree / InvertedIndex (ds)
  │
  ▼  người dùng add / edit / delete
DictionaryService ─sửa─► SlangDictionary (cập nhật MỌI index) ─► DictionaryStore ghi lại .dat
```

**Ý tưởng chính:** index chỉ build **một lần**, sau đó lưu nguyên cấu trúc xuống file `.dat`.
Những lần chạy sau chỉ việc nạp lên là dùng được ngay. Đề yêu cầu đúng điều này ở mục III.3.

---

## 2. Các file ở gốc repo

| File / thư mục | Công dụng |
|---|---|
| `pom.xml` | "Bản mô tả project" cho Maven: Java 17, cần thư viện gì (FlatLaf, JUnit), đóng gói jar ra sao. Có 3 phần cấu hình đáng chú ý:<br>• **copy-resources**: mỗi lần build sẽ copy `slang.txt` vào bên trong jar tại `data/slang.txt`. Bản này chỉ đọc, dùng cho chức năng Reset.<br>• **shade**: gộp code của mình và FlatLaf vào **một** file jar duy nhất, đồng thời ghi `Main-Class` để double-click là chạy.<br>• **JUnit** có `scope test`: chỉ dùng lúc test, không bị đóng gói vào jar. |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/` | Maven Wrapper. Lần đầu chạy `mvnw.cmd`, nó tự tải đúng Maven 3.9.16, nên không ai phải cài Maven. `mvnw` dành cho Linux/Mac, `mvnw.cmd` dành cho Windows. |
| `.gitignore` | Những thứ không đưa lên git: `target/` (sản phẩm build, sinh lại được), `/data/` (dữ liệu runtime trên máy) và file cấu hình riêng của IDE. |
| `.gitattributes` | Giữ đúng kiểu xuống dòng cho mvnw (LF) và mvnw.cmd (CRLF). Sai kiểu xuống dòng thì script sẽ không chạy. |
| `slang.txt` | Dữ liệu gốc của thầy, đã sửa lỗi. Đây là **nguồn sự thật duy nhất**: không copy tay đi chỗ khác, để Maven copy khi build. |
| `tools/*.py` | Hai script Python đã dùng để sửa và kiểm tra `slang.txt`. Không thuộc app Java. |
| `docs/` | Tài liệu: file này và PLAN.md, cả hai đều được commit. `docs/` nằm trong `.git/info/exclude` nên mỗi file phải `git add -f` lần đầu; sau đó git theo dõi thay đổi bình thường. |
| `target/` | Maven sinh ra khi build: các file `.class`, báo cáo test, `slang-dictionary.jar`. Xóa đi lúc nào cũng được. |

### Quy trình build

```
*.java ──javac──► *.class ──jar + shade──► target/slang-dictionary.jar ──► java -jar ...
```

| Lệnh | Làm gì |
|---|---|
| `.\mvnw.cmd compile` | Biên dịch, kiểm tra lỗi cú pháp |
| `.\mvnw.cmd test` | Biên dịch rồi chạy toàn bộ unit test |
| `.\mvnw.cmd package` | compile → test → đóng gói `target\slang-dictionary.jar`. Có test fail thì **dừng, không tạo jar** |
| `java -jar target\slang-dictionary.jar` | Chạy app từ jar |

Lúc đang code thì mở project bằng VS Code và bấm **Run** trên hàm `main` của `App.java`.

---

## 3. Cây mã nguồn và vai trò từng package

```
src/main/java/        code chính của app
src/main/resources/   file đi kèm (i18n/messages_en.properties, messages_vi.properties)
src/test/java/        unit test: kiểm tra code đúng, KHÔNG nằm trong jar
```

Package gốc: `lab01.slangdict._24127430`. Phần tên package không được bắt đầu bằng chữ số, nên MSSV có thêm `_` phía trước, theo quy ước của đặc tả Java (JLS §6.1).

| Package | Trách nhiệm | Class | Trạng thái |
|---|---|---|---|
| `model` | **Đối tượng dữ liệu**: chỉ chứa dữ liệu, không có logic phức tạp | `SlangEntry`, `SearchResult`, `HistoryItem`, `QuizQuestion` | ✅ |
| | | enum `SearchMode` (EXACT/PREFIX/SUBSTRING/FUZZY), `SearchTarget` (SLANG/DEFINITION), `QuizType`, `ConflictResolution` (OVERWRITE/ADD_MEANING/NEW_ENTRY), `Theme` (LIGHT/DARK) | ✅ |
| `ds` | **Cấu trúc dữ liệu generic tự viết**, không biết gì về "slang" | `Trie<V>`, `BKTree<T>`, `InvertedIndex<K,V>`, `DistanceMetric<T>`, `Levenshtein` | ✅ |
| `io` | **Đọc/ghi file** | `SlangFileParser`, `SlangFormatException` | ✅ |
| | | `AppPaths` (thư mục data/ cạnh jar), `DictionaryStore` (.dat), `DictionaryFormatException` | ✅ |
| | | `SettingsStore` (ngôn ngữ, theme → `settings.properties`) | ✅ |
| | | `HistoryStore` (lịch sử tìm kiếm → `history.dat`, ghi nền) | ✅ |
| `core` | **Từ điển đã index**: gộp các cấu trúc ở `ds`, giữ chúng luôn đồng bộ | `SlangDictionary`, `Tokenizer` | ✅ |
| `service` | **Nghiệp vụ** của 10 chức năng | `SearchService`, `SearchOutcome` | ✅ |
| | | `HistoryService` | ✅ |
| | | `DictionaryService`, `ConflictResolver`, `ChangeResult` (thêm/sửa/xóa) | ✅ |
| | | `RandomService` (slang hôm nay + random) | ✅ |
| | | `QuizService` | ⬜ |
| `ui` | **Giao diện Swing** | Khung: `MainFrame`, `Sidebar`, `Feature`, `FeatureScreen`, `ThemeManager`, `LoadingWindow`, `SwingLocalizer`, `NumberBadgeIcon`, `AppIcon`, `dialogs/AboutDialog`, `panels/PlaceholderPanel` | ✅ |
| | | `panels/SearchPanel` (F1, F2), `panels/HistoryPanel` (F3) | ✅ |
| | | `panels/EntryActions`, `dialogs/EntryEditorDialog`, `dialogs/ConflictDialog` (F4, F5, F6) | ✅ |
| | | `panels/ResetAction`, `dialogs/ResetDialog` (F7), `panels/RandomPanel` (F8) | ✅ |
| | | `panels/QuizPanel` | ⬜ |
| `i18n` | Chữ song ngữ Anh/Việt, đổi ngay khi đang chạy | `Messages` + `messages_vi/en.properties` | ✅ |
| `tools` | Sinh 100k slang để benchmark | `BigDataGenerator` | ✅ |

### Quy tắc phụ thuộc

```
ui  ──►  service  ──►  core / io  ──►  ds / model
```

Tầng trên chỉ gọi tầng dưới, không bao giờ gọi ngược lên. Nhờ vậy:
- `ds` test được độc lập: `Trie` không cần biết đến `SlangEntry`.
- Muốn đổi Swing sang Console thì chỉ phải viết lại `ui`, các tầng khác giữ nguyên.

---

## 4. Giải thích chi tiết phần đã có code

### 4.1 Khởi động: `App`, `ThemeManager`, `MainFrame`, `Messages`

> Phần này là ý tưởng nền. Chi tiết khung giao diện của bước 7 xem **mục 4.11**.

- `App.main()` dùng `SwingUtilities.invokeLater(...)` để cài ngôn ngữ, skin và tạo cửa sổ trên EDT.
  **Lý do:** Swing quy định mọi thao tác với giao diện phải chạy trên một luồng riêng tên là *Event Dispatch Thread* (EDT). Làm sai quy tắc này dễ gây lỗi hiển thị ngẫu nhiên.
- `ThemeManager.install()` thử dùng FlatLaf trước. Nếu không được (bắt `LinkageError` hoặc `RuntimeException`, ví dụ thiếu thư viện FlatLaf) thì chuyển sang giao diện của hệ điều hành (`UIManager.getSystemLookAndFeelClassName()`, trên Windows là giao diện Windows), và nếu vẫn lỗi thì giữ giao diện mặc định. App không bao giờ crash chỉ vì skin.
- **FlatLaf là gì:** Swing được thiết kế theo kiến trúc *Pluggable Look & Feel*, tách phần **logic** của component (JButton, JTable...) khỏi phần **vẽ**. FlatLaf chỉ thay phần vẽ, còn toàn bộ code giao diện vẫn là Swing thuần. Thư viện được khai báo trong pom.xml và README.
- `Messages.get("app.title")` lấy chữ hiển thị từ file `.properties` theo ngôn ngữ đang chọn. `NoFallbackControl` dùng để chặn Java tự động chuyển sang ngôn ngữ của hệ điều hành.

### 4.2 `model`: đối tượng dữ liệu

**`SlangEntry`** đại diện cho một mục trong từ điển: `id`, `slang` và `meanings` (danh sách nghĩa).

- **Mỗi entry có id riêng.** Đây là cách đáp ứng lựa chọn "tạo entry mới cùng tên": hai entry `LOL` (id 5 và id 7900) vẫn là hai mục tách biệt. Hàm `equals`/`hashCode` so sánh theo id.
- **Bất biến (immutable):** mọi field đều `final` và danh sách nghĩa không sửa được (`unmodifiableList`). Muốn sửa thì gọi `entry.withSlang("LMAO")` để tạo bản mới cùng id.
  **Lý do:** từ điển giữ entry ở nhiều index cùng lúc. Nếu cho sửa thẳng tên của một entry, Trie vẫn giữ nó dưới tên cũ và index bị sai. Với thiết kế bất biến, mọi thay đổi bắt buộc phải đi qua `SlangDictionary`, nơi cập nhật tất cả index cùng lúc.
- Constructor tự kiểm tra dữ liệu: tên không được rỗng, phải có ít nhất 1 nghĩa, khoảng trắng thừa bị cắt bỏ. Dữ liệu sai bị chặn ngay từ khi tạo đối tượng.
- `implements Serializable` để có thể ghi entry vào file `.dat`.

**`SearchResult`, `HistoryItem`, `QuizQuestion`** là các **`record`** (có từ Java 16 trở đi). Một record là class chỉ chứa dữ liệu; Java tự sinh constructor, getter, `equals` và `toString`. Phần `public SearchResult { ... }` (*compact constructor*) là chỗ kiểm tra dữ liệu đầu vào.

- `SearchResult implements Comparable<SearchResult>` định nghĩa thứ tự sắp xếp kết quả: **điểm cao lên trước**, cùng điểm thì xếp theo tên (không phân biệt hoa thường), cuối cùng xếp theo id. Chỉ cần gọi `Collections.sort(list)` là kết quả được xếp hạng. Đây là phần *Comparable* trong file tài liệu thầy đưa.
- `QuizQuestion` bắt buộc có đúng 4 đáp án và `correctIndex` nằm trong khoảng 0..3.

### 4.3 `io/SlangFileParser`: đọc file

Định dạng file gốc: `` slang`nghĩa1| nghĩa2 ``

Thuật toán xử lý mỗi dòng:
1. Tìm dấu `` ` `` **đầu tiên**. Phần trước nó là tên slang, phần sau là các nghĩa.
2. Cắt phần nghĩa theo `|`, cắt khoảng trắng ở hai đầu, bỏ phần rỗng.

> **Bẫy trong dữ liệu:** có những slang như `:|`, `8-|`, `|O|` chứa sẵn dấu `|` trong tên.
> Nếu cắt theo `|` ngay từ đầu thì những slang này sẽ hỏng. Vì vậy thứ tự xử lý ở trên là bắt buộc.

Những điểm khác:
- Đọc file bằng **UTF-8**, vì có các slang như `` ELNS`Eller noe sånt `` và `®`.
- Bỏ qua dòng header `` Slag`Meaning ``, dòng trống và ký tự BOM (một ký tự ẩn mà Notepad hay thêm vào đầu file).
- Dòng sai định dạng ném **`SlangFormatException`** kèm số dòng, ví dụ *"Line 3: missing '`' separator"*. Đây là **checked exception** (kế thừa `Exception`), nên người gọi bắt buộc phải xử lý. Phần này đáp ứng chuẩn đầu ra G5.8 về quản lý ngoại lệ.
- `try-with-resources` (`try (BufferedReader r = ...)`) tự đóng file kể cả khi có lỗi xảy ra.
- `formatLine` / `write` làm chiều ngược lại: biến entry thành dòng text. Dùng để sinh file 100k slang.
- id được đánh theo thứ tự dòng, bắt đầu từ 0.

Số liệu của file gốc: **7.638 entry, 8.941 cặp slang–nghĩa**.

### 4.4 `ds`: cấu trúc dữ liệu generic (phần chính của video)

Đề nói rõ: nếu chỉ dùng `List` thì điểm tối đa là 70. Tìm trong `List` phải duyệt từng phần tử, độ phức tạp O(n).
Bốn cấu trúc dưới đây cho phép tìm mà **không phải duyệt cả từ điển**.

#### a) `Trie<V>`: tìm theo tiền tố (prefix / autocomplete)

Mỗi cạnh của cây là một ký tự. Ví dụ với các slang `l` (id 4), `lol` (2 entry, id 1 và 2), `lmao` (id 3):

```
(root)
  └─ l  {id 4}
      ├─ m ─ a ─ o  {id 3}
      └─ o ─ l  {id 1, id 2}      ← một nút giữ được NHIỀU id vì cho phép trùng tên
```

- Gõ prefix `lo`: đi 2 bước xuống nút `l→o`, rồi lấy mọi id trong nhánh con. Chi phí phụ thuộc **độ dài chữ gõ vào**, không phụ thuộc từ điển có 7 nghìn hay 100 nghìn slang.
- Con của mỗi nút được giữ trong `TreeMap<Character, Node>`, nên duyệt cây ra kết quả **đúng thứ tự từ điển**.
- Tham số `limit` cho phép dừng sớm. Autocomplete chỉ cần khoảng 20 gợi ý thì không phải duyệt hết nhánh.
- Khi xóa, nút nào không còn dùng sẽ bị cắt bỏ để cây không phình to vô ích.

#### b) `InvertedIndex<K extends Comparable<? super K>, V>`: tìm theo definition

Đây là kỹ thuật mà các công cụ tìm kiếm như Google dùng: thay vì lưu "entry → các từ có trong definition", ta lưu **"từ → các entry có chứa từ đó"**:

```
"laugh" → {id 5, id 812, id 3301}
"love"  → {id 77, id 1203}
"money" → {id 1, id 2}
```

Khi gõ `love`, ta lấy ngay `{77, 1203}` mà không phải đọc definition nào.

- Bên trong dùng **TreeMap**, nên các key (từ) luôn được sắp xếp. Nhờ vậy lấy được cả một khoảng: `keysInRange("lov", "lov￿")` trả về `love`, `lovely`, `lover`... Đây là cách làm chế độ **prefix** khi tìm theo definition.
- **Bounded type** `K extends Comparable<? super K>`: TreeMap phải so sánh được các key, nên kiểu `K` bắt buộc phải "so sánh được".
  Viết `InvertedIndex<String, Integer>` thì hợp lệ vì `String` là Comparable. Viết `InvertedIndex<Object, Integer>` thì **compiler báo lỗi**.
  Đây đúng là ví dụ `TreeMap<Key extends Comparable<Key>, Data>` trong file `Type Parameter.txt` của thầy.
  Phần `? super K` giúp ràng buộc linh hoạt hơn: chấp nhận cả kiểu có `compareTo` được kế thừa từ lớp cha.
- Khi xóa id cuối cùng của một từ, từ đó bị gỡ khỏi index.

#### c) `Levenshtein`: đo mức độ "gõ sai"

Levenshtein là số phép **chèn, xóa hoặc thay** một ký tự ít nhất để biến chuỗi a thành chuỗi b.

| a | b | khoảng cách |
|---|---|---|
| lol | lool | 1 (chèn o) |
| lol | lul | 1 (thay o→u) |
| love | lvoe | 2 |
| kitten | sitting | 3 |

- Tính bằng **quy hoạch động**: một bảng `|a|×|b|`, mỗi ô lấy giá trị nhỏ nhất trong 3 lựa chọn (thay, xóa, chèn). Code chỉ giữ **2 hàng** của bảng thay vì cả bảng nên tốn ít bộ nhớ.
- Class này cài `DistanceMetric<String>`, nghĩa là nó là một "thước đo khoảng cách" giữa hai chuỗi.

#### d) `BKTree<T>` + `DistanceMetric<T>`: tìm gần đúng (fuzzy)

**Vấn đề:** người dùng gõ `lil` muốn tìm ra `lol`. Cách ngây thơ là tính Levenshtein với **mọi** slang, tức O(n) lần tính.
BK-tree giúp bỏ qua phần lớn số từ.

**Cách xây cây:** mỗi cạnh được gắn nhãn bằng **khoảng cách** giữa nút con và nút cha.
Khi thêm `lol`, `lool`, `lul`, `rofl`, `lmao` (theo thứ tự đó):

```
              lol
      1 /    2 |    3 \
     lool     rofl    lmao
   2 /
   lul
```
(`lul` cách `lol` 1 → nhánh 1 đã có `lool` → `lul` cách `lool` 2 → gắn dưới `lool` ở nhánh 2)

**Cách tìm** với truy vấn `lil`, cho sai tối đa k = 1:
1. Ở gốc, tính D = d(`lil`, `lol`) = 1. Vì D ≤ 1 nên `lol` là **kết quả**.
2. **Bất đẳng thức tam giác** đảm bảo: các từ thỏa mãn chỉ có thể nằm ở những nhánh có nhãn trong khoảng **[D−k, D+k] = [0, 2]**.
   Vậy chỉ cần đi xuống nhánh 1 và nhánh 2, **bỏ qua hoàn toàn nhánh 3** (`lmao` và mọi thứ bên dưới nó).
3. Tại `lool`: D = 2 > 1 nên không lấy. Xét các nhánh có nhãn [1, 3] thì gặp `lul`: D = 1, lấy.
4. Tại `rofl`: D = 3, không lấy.

Kết quả là `lol` và `lul`. Với từ điển lớn, phần cây bị bỏ qua rất nhiều.

Các điểm thiết kế:
- **Generic với wildcard:** constructor nhận `DistanceMetric<? super T>`, tức một thước đo dùng được cho `T` hoặc kiểu cha của `T`. Cây không gắn chặt với chuỗi hay Levenshtein; đổi sang thước đo khác thì cây vẫn dùng được.
- **Xóa mềm:** BK-tree không xóa nút được, vì xóa sẽ làm sai nhãn khoảng cách của các nút con. Thay vào đó, nút bị đánh dấu `deleted`; thêm lại từ đó thì gỡ đánh dấu.
- `DistanceMetric` **kế thừa `Serializable`** vì BK-tree chứa metric bên trong, và cả cây phải ghi được xuống `.dat`. `Levenshtein.readResolve()` đảm bảo sau khi đọc lại từ file vẫn chỉ có đúng một instance.
- `@FunctionalInterface`: có thể truyền vào một lambda, ví dụ `new BKTree<String>((a, b) -> ...)`.

### 4.5 Các cấu trúc ghép vào nhau ra sao (bước 4–6 của PLAN)

| Chức năng | Cấu trúc dùng | Chi phí |
|---|---|---|
| F1 tìm slang, exact | `HashMap<tên viết thường, Set<id>>` | O(1) |
| F1 prefix | `Trie` | O(độ dài chữ gõ + số kết quả) |
| F1 fuzzy | `BKTree` chứa tên slang | chỉ duyệt một phần cây |
| F1 substring | duyệt tập tên (100k: vài ms, query khớp rất nhiều như `o` mất ~60 ms, xem 4.10) | O(n), nhanh trong thực tế |
| F2 tìm definition, exact | `InvertedIndex` từ → id | O(log V) |
| F2 prefix | `InvertedIndex.keysInRange` | O(log V + số kết quả) |
| F2 substring / fuzzy | duyệt hoặc BK-tree trên **tập từ vựng** (số từ khác nhau ít hơn rất nhiều so với tổng số chữ trong mọi definition), rồi tra InvertedIndex | |
| F2 nhiều từ khóa | **giao** các tập id (AND), sắp xếp bằng `SearchResult.compareTo` | |
| F8 random khác / Quiz | danh sách id cho phép lấy phần tử ngẫu nhiên trong O(1) | O(1) |
| F8 slang hôm nay | duyệt mọi entry, lấy entry có điểm `score(ngày, id)` cao nhất (xem 4.15) | O(n), 100k: vài ms |
| F7 reset | build lại `SlangDictionary` mới từ `slang.txt` gốc, thay object cũ | O(tổng độ dài file) |
| Mọi entry | `HashMap<id, SlangEntry>` | O(1) |

Tất cả được gói trong `SlangDictionary` và serialize cùng nhau thành **một** file `.dat`.

### 4.6 `core/Tokenizer`: chuẩn hóa chuỗi

- `tokens("Laugh out loud!")` → `[laugh, out, loud]`: chuyển chữ thường rồi tách theo mọi ký tự **không phải chữ hoặc số** (regex `[^\p{L}\p{N}]+`). `\p{L}` nhận chữ của mọi ngôn ngữ (có cả `å` trong `sånt`), nên tiếng nước ngoài không bị cắt vụn.
  Thứ tự từ được **giữ nguyên** vì bước 6 cần kiểm tra "khớp nguyên cụm" để xếp hạng.
- `normalizeName("  LoL ")` → `"lol"`: key để tra tên slang không phân biệt hoa thường. **Không** bỏ ký hiệu, vì có slang chỉ gồm ký hiệu như `:|`.
- Luôn dùng `Locale.ROOT`. **Lý do:** `toLowerCase()` phụ thuộc ngôn ngữ của máy; ở máy cài tiếng Thổ Nhĩ Kỳ, `"I"` thành `"ı"` (i không chấm) và tra `LOL` sẽ không ra. Có test riêng cho trường hợp này.
- Index và truy vấn **phải đi qua cùng một hàm chuẩn hóa**, nếu không thì dữ liệu lưu một kiểu, tra một kiểu, sẽ không bao giờ khớp.

### 4.7 `core/SlangDictionary`: từ điển đã index

Đây là nơi ghép các cấu trúc ở mục 4.4 lại với nhau:

| Field | Kiểu | Dùng cho |
|---|---|---|
| `entries` | `HashMap<id, SlangEntry>` | lấy entry theo id, O(1) |
| `idList` + `positionOf` | `ArrayList<id>` + `HashMap<id, vị trí>` | random O(1), xóa O(1) |
| `idsByName` | `HashMap<tên thường, Set<id>>` | tìm slang EXACT |
| `nameTrie` | `Trie<Integer>` | tìm slang PREFIX |
| `nameTree` | `BKTree<String>` (các tên khác nhau) | tìm slang FUZZY |
| `wordIndex` | `InvertedIndex<String, Integer>` | tìm definition EXACT, PREFIX |
| `vocabularyTree` | `BKTree<String>` (các từ khác nhau trong definition) | tìm definition FUZZY |

**Bất biến quan trọng nhất: mọi index luôn khớp với `entries`.**
- Bên ngoài chỉ sửa được dữ liệu qua 3 method `add`, `replace`, `remove`.
- Cả 3 đều dùng cặp `index(entry)` / `unindex(entry)`: `replace` = gỡ index của bản cũ rồi gắn index của bản mới. Vì `SlangEntry` bất biến (mục 4.2) nên không ai sửa lén được tên của entry đang nằm trong Trie.
- Khi tên cuối cùng biến mất thì gỡ khỏi `idsByName` và xóa mềm khỏi `nameTree`; khi từ cuối cùng biến mất khỏi `wordIndex` thì xóa mềm khỏi `vocabularyTree`. Nhờ vậy fuzzy không gợi ý những từ đã không còn.
- **Cách kiểm chứng:** test `randomMutationsKeepEveryIndexInSync` thực hiện hàng nghìn thao tác add/replace/remove ngẫu nhiên, sau đó so **từng index** với một từ điển build lại từ đầu. Hai bên phải giống hệt nhau.

**Random O(1) và swap-remove:** muốn lấy entry ngẫu nhiên thì cần một mảng để chọn `idList.get(random.nextInt(n))`. Nhưng xóa giữa `ArrayList` tốn O(n) vì phải dời các phần tử phía sau. Mẹo: đưa phần tử **cuối** vào chỗ trống rồi xóa phần tử cuối (O(1)), `positionOf` nhớ mỗi id đang ở vị trí nào.

```
idList = [5, 8, 2, 9]   xóa 8 (vị trí 1)
       → [5, 9, 2]       9 (phần tử cuối) nhảy vào vị trí 1
```

**Id:** `build` giữ nguyên id từ file (0, 1, 2, ...); `add` cấp `nextId` rồi tăng lên. Id không bao giờ dùng lại, nên lịch sử hay entry đang chọn trên UI không bao giờ trỏ nhầm sang entry khác.

**Đa luồng (`synchronized`):** UI chỉ đọc/sửa trên một luồng (EDT), nhưng luồng ghi nền (mục 4.8) lại serialize từ điển cùng lúc. Nếu đang serialize mà EDT xóa một entry thì file có thể chứa trạng thái "nửa vời" (đã xóa khỏi `entries` nhưng vẫn còn trong Trie). Cách giải quyết: `writeObject` và `add/replace/remove` cùng `synchronized` trên chính object từ điển, giống cách `java.util.Vector` làm. Đang ghi thì thao tác sửa phải chờ, và ngược lại. Test `savingWhileEditingNeverWritesHalfUpdatedData` kiểm tra đúng tình huống này.

### 4.8 `io/AppPaths` và `io/DictionaryStore`: lưu index xuống file

**`AppPaths.dataDir()`** chọn thư mục `data/` theo thứ tự ưu tiên:
1. System property `slangdict.data.dir` (test dùng để ghi vào thư mục tạm).
2. Đang chạy từ jar: thư mục **chứa file jar** + `/data`. Double-click jar ở đâu thì data nằm cạnh đó, không phụ thuộc thư mục đang mở terminal.
3. Đang chạy trong IDE (`target/classes`): thư mục làm việc + `/data`.

Phần quyết định được tách thành hàm thuần `resolveDataDir(...)` để test cả 3 trường hợp mà không cần build jar thật. Đường dẫn được lấy qua `toURI()` để xử lý đúng thư mục có dấu cách hoặc tiếng Việt (URL mã hóa thành `%20`).

**Định dạng `dictionary.dat`:**

```
[MAGIC "SLNG" 4 byte][FORMAT_VERSION 4 byte][CRC32 8 byte][payload = SlangDictionary đã serialize]
```

| Phần | Để làm gì |
|---|---|
| Magic number | Nhận ra ngay "đây không phải file của mình" (ví dụ ai đó đổi tên file khác thành `.dat`) |
| Format version | Khi đổi cấu trúc class thì tăng số này, file cũ bị coi là không hợp lệ và app tự build lại |
| CRC32 | Phát hiện file **hỏng âm thầm**: đổi vài byte mà vẫn deserialize được (ví dụ một id trong Trie bị sai). Không có checksum thì từ điển hỏng sẽ được nạp vào và gây lỗi khó hiểu về sau. Test `everyDamagedByteIsDetected` đổi **từng byte** của file và kiểm tra lần nào cũng bị phát hiện |

**Khởi động `loadOrBuild()`:**

```
có dictionary.dat? ──có──► load ──OK──► dùng luôn (Source.DAT)
        │                    │
       không             lỗi (DictionaryFormatException)
        ▼                    ▼
   build từ slang.txt gốc (data/slang.txt nếu có, không thì bản nhúng trong jar)
        ▼
   lưu dictionary.dat cho lần sau (Source.TEXT)
```

`DictionaryFormatException` kế thừa `IOException`: mọi kiểu hỏng (sai magic, sai version, sai CRC, bị cắt cụt, class không khớp, dữ liệu rác) đều quy về **một** loại lỗi, và `loadOrBuild` chỉ cần bắt một chỗ để build lại.

**Ghi an toàn (atomic):** ghi ra `dictionary.dat.tmp`, `force(true)` để chắc chắn dữ liệu đã xuống đĩa, rồi `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` đè lên file thật. Nếu app bị tắt giữa chừng thì chỉ hỏng file `.tmp`, file `.dat` cũ vẫn nguyên vẹn. Trên Windows, trình diệt virus có thể giữ file trong chốc lát nên việc đổi tên được thử lại tối đa 5 lần.

**Ghi nền (`saveInBackground`):**
- Đo thực tế: serialize 100k slang mất khoảng 2 giây. Nếu ghi trên EDT thì mỗi lần add/edit/delete giao diện sẽ đứng 2 giây. Vì vậy có **một** luồng nền riêng (`ExecutorService` một luồng) lo việc serialize và ghi; `saveInBackground` trả về ngay.
- **Gộp các lần lưu dồn dập:** mỗi yêu cầu nhận một số thứ tự (`AtomicLong`). Khi đến lượt chạy, nếu đã có yêu cầu mới hơn thì bỏ qua và để lần mới nhất ghi. Xóa 10 entry liên tiếp chỉ ghi 1–2 lần chứ không phải 10.
- **Hoãn ghi 1,5 s (thêm ở bước 10):** trong lúc serialize, luồng ghi giữ khóa của từ điển. Nếu ghi ngay sau mỗi lần sửa thì lần tìm lại ngay sau đó (để hiện entry vừa sửa) phải đứng chờ khoảng 2 s ở bản 100k. Vì vậy app dùng `ScheduledThreadPoolExecutor`: mỗi yêu cầu hẹn ghi sau 1,5 s, yêu cầu mới thì dời giờ (debounce). Lần tìm lại xong trước, việc ghi rơi vào lúc người dùng đang đọc kết quả. Constructor 2 tham số vẫn ghi ngay (test dùng).
- Lỗi ghi được báo qua `errorListener` (bước 7 sẽ cho hiện thông báo trên UI) thay vì làm sập app.
- Khi thoát, shutdown hook gọi `close()` → `flush()`: **ghi ngay** lần đang hẹn giờ (không chờ hết 1,5 s) rồi chờ ghi xong.

**Bảo mật khi đọc (`ObjectInputFilter`):** deserialize trong Java có thể khởi tạo bất kỳ class nào ghi trong file, đây là lỗ hổng kinh điển. Filter chỉ cho phép class của app, `java.util.*` và `java.lang.*`, đồng thời giới hạn kích thước mảng để file hỏng không làm tràn bộ nhớ.

**Số đo trên máy dev:**

| | 7,6k slang (file gốc) | 100k slang |
|---|---|---|
| Build index từ text | 50–100 ms | 1,6–3,3 s |
| Đọc `.dat` | 45–110 ms | 0,8–1,5 s |
| Kích thước `.dat` | 2,3 MB | 30 MB |

→ Lần chạy thứ hai trở đi chỉ đọc `.dat`, đáp ứng yêu cầu chung 3 của đề (không index lại mỗi lần mở app).

### 4.9 `service/SearchService`: tìm kiếm F1, F2

Một hàm duy nhất: `search(target, mode, query, limit)` → `SearchOutcome(results, totalMatches, elapsedNanos)`.

**Mỗi tổ hợp dùng cấu trúc nào:**

| | SLANG (tên) | DEFINITION (từng từ của query) |
|---|---|---|
| EXACT | `HashMap` tên → id | `InvertedIndex` từ → id |
| PREFIX | `Trie` | khoảng `[p, p+'￿')` của `TreeMap` từ vựng |
| SUBSTRING | duyệt các **tên khác nhau** | duyệt các **từ khác nhau** (từ vựng), rồi tra `InvertedIndex` |
| FUZZY | `BKTree` trên tên | `BKTree` trên từ vựng, rồi tra `InvertedIndex` |

**Vì sao SUBSTRING không cần index riêng:** tìm "aug" trong `laughing` không dùng được Trie hay HashMap, vì cả hai chỉ tra được từ đầu chuỗi. Nhưng thay vì duyệt **từng definition** (100k câu dài), ta duyệt **tập từ vựng** (vài chục nghìn từ ngắn, mỗi từ chỉ một lần), rồi dùng InvertedIndex để ra id. Nhanh hơn nhiều mà không tốn thêm bộ nhớ.

**Definition nhiều từ, ví dụ `laugh out loud`:**
1. Tách query thành từ: `[laugh, out, loud]`. Từ lặp lại chỉ tính một lần.
2. Mỗi từ cho ra một tập id (kèm điểm), theo mode đang chọn.
3. **Giao (AND)** các tập, bắt đầu từ **tập nhỏ nhất**: số phần tử phải xét không bao giờ vượt quá tập nhỏ nhất. Ví dụ `the` có 9.000 id, `loud` có 20 id → chỉ cần kiểm tra 20 id xem có nằm trong tập của `the` không. Một từ không khớp gì thì dừng ngay, trả kết quả rỗng.
4. Entry có một nghĩa chứa **nguyên cụm** (các từ liền nhau đúng thứ tự) được +100. So trên dãy từ nên `Laughing out, LOUD!` vẫn khớp.

**Điểm xếp hạng:**

| | Điểm |
|---|---|
| Tên trùng đúng query | 100 |
| Tên bắt đầu bằng query | 50 |
| Tên chứa query ở giữa | 20 |
| Tên FUZZY | 100 − 10 × số lỗi gõ |
| Definition có nguyên cụm query (≥ 2 từ) | +100 |
| Mỗi từ khớp đúng nguyên từ | +10 |
| Mỗi từ chỉ khớp một phần (`laugh` → `laughing`) | +5 |
| Mỗi từ khớp FUZZY | +10 − 3 × số lỗi gõ |

Sau đó gọi `Collections.sort(results)`, và `SearchResult.compareTo` lo phần sắp xếp: điểm cao trước, cùng điểm thì theo tên. Cuối cùng mới cắt còn `limit` kết quả, còn `totalMatches` vẫn đếm đủ (UI sẽ hiện "Hiển thị 1.000 / 32.854 kết quả").

**Số lỗi gõ cho phép (k của BK-tree):**
- Tên slang: dài ≤ 4 ký tự → k = 1, dài hơn → k = 2.
- Từ trong definition: ≤ 2 ký tự → k = 0, vì nếu không thì `a` sẽ khớp mọi từ 1–2 chữ; ≤ 4 → k = 1; còn lại k = 2.

**`Supplier<SlangDictionary>` thay vì giữ cứng từ điển:** Reset (F7) tạo ra một object từ điển **mới**. Nếu SearchService giữ object cũ thì sau Reset vẫn tìm trên dữ liệu cũ. Truyền vào một *hàm trả về từ điển hiện tại* (`() -> currentDictionary`) thì lần tìm nào cũng lấy đúng bản mới nhất. Đây là một functional interface có sẵn của Java.

**Cách kiểm chứng:** test `indexedSearchMatchesBruteForceOnOriginalData` chọn ngẫu nhiên 60 query trên file gốc, chạy cả 8 tổ hợp, rồi so tập id với cách **duyệt từng entry** (O(n), chắc chắn đúng). Hai bên phải giống hệt nhau.

### 4.10 `tools/BigDataGenerator` và benchmark 100k

- Tạo 100.000 entry = 7.638 entry gốc + 92.362 entry tổng hợp. Tên tổng hợp là 2–8 ký tự in hoa (đôi khi có số, như `GR8`), không trùng tên nào đã có. Mỗi entry có 1–3 nghĩa, mỗi nghĩa ghép 2–7 từ lấy từ definition thật.
- **Chọn từ theo tần suất thật:** danh sách từ giữ cả từ lặp lại, nên `you` (xuất hiện rất nhiều trong dữ liệu gốc) cũng được chọn nhiều. Phân bố từ vựng vì vậy giống dữ liệu thật, và benchmark phản ánh đúng thực tế. Nếu chọn đều mọi từ thì kết quả sẽ "đẹp" hơn thật.
- **Seed cố định** (MSSV `24127430`): chạy lại luôn ra đúng file cũ, nên số đo lặp lại được.
- Cách chạy: `java -cp target\slang-dictionary.jar lab01.slangdict._24127430.tools.BigDataGenerator [số lượng] [file ra]`.

**Kết quả đo (100k slang, lần gọi đầu tiên, máy dev):** build index khoảng 1,6–2,1 s (chỉ một lần, sau đó đọc `.dat`).

| Target | Mode | Query | Số kết quả | Thời gian |
|---|---|---|---|---|
| SLANG | EXACT | `lol` | 1 | ~1 ms |
| SLANG | PREFIX | `lo` / `a` | 157 / 3.903 | ~1 / ~15 ms |
| SLANG | SUBSTRING | `o` | 18.210 | 50–70 ms |
| SLANG | FUZZY | `lool` / `lmaoo` | 19 / 41 | ~2 / ~15 ms |
| DEFINITION | EXACT | `laugh out loud` / `the` | 5 / 8.948 | ~2 / ~13 ms |
| DEFINITION | PREFIX | `lau` / `a` | 2.673 / 32.854 | ~4 / 40–48 ms |
| DEFINITION | SUBSTRING | `aug` / `e` | 2.725 / 88.464 | ~4 / 140–220 ms |
| DEFINITION | FUZZY | `lauhg` / `laugh out lound` | 700 / 10 | ~1 / ~3 ms |

→ Trường hợp tệ nhất là `e`: khớp 88% từ điển mà vẫn chỉ mất khoảng 0,2 s, dưới xa mức 1 s của đề. Phần lớn thời gian khi đó dành cho chấm điểm và sắp xếp 88k kết quả, chứ không phải tra index.

**Bẫy khi đo thời gian:** chạy cả bộ test thì có lần query đầu tiên đo ra khoảng 960 ms, trong khi chạy riêng chỉ khoảng 2 ms. Nguyên nhân là **garbage collector** dừng chương trình để dọn đống rác còn lại sau khi build 100k entry, và lần dọn đó rơi đúng vào lúc đang đo. Test vì vậy gọi `System.gc()` sau khi build rồi mới đo. Bài học: khi benchmark trong Java phải để ý tới GC và JIT, không thể chỉ đọc một con số duy nhất.

### 4.11 Khung giao diện (bước 7)

**Bố cục cửa sổ (`MainFrame`):**

```
┌─[S] Tệp  Chức năng  Giao diện  Ngôn ngữ (Language)  Trợ giúp ── Từ điển Slang — 1. Tìm theo slang ─┐
│ TÌM KIẾM          │                                                       │
│ [1] Tìm theo slang│                                                       │
│ [2] Tìm theo đ.n. │        CardLayout: chỉ hiện card đang chọn            │
│ [3] Lịch sử       │        (SEARCH, HISTORY, RANDOM, QUIZ_SLANG,          │
│ QUẢN LÝ           │         QUIZ_DEFINITION)                              │
│ [4]…[7]           │                                                       │
│ KHÁM PHÁ          │                                                       │
│ [8]…[10]          │                                                       │
├───────────────────┴───────────────────────────────────────────────────────┤
│ 7.638 slang · đọc index từ dictionary.dat trong 85 ms        (lỗi lưu: đỏ) │
└────────────────────────────────────────────────────────────────────────────┘
```

Đây là phần đáp ứng **yêu cầu chung 2** của đề ("phải có menu để chọn chức năng"). Có **hai** cách chọn chức năng: menu "Chức năng" và Sidebar, cộng thêm phím tắt Ctrl+1 … Ctrl+0.

**`Feature`: một enum cho cả 10 chức năng.** Mỗi hằng số mang theo số thứ tự trong đề, nhóm (TÌM KIẾM / QUẢN LÝ / KHÁM PHÁ), card màn hình, key chữ và phím tắt. Sidebar và menu đều dựng ra từ vòng `for (Feature f : Feature.values())`, nên hai nơi luôn khớp nhau; thêm hay đổi một chức năng chỉ cần sửa một chỗ.
- Chức năng có card là **màn hình**: F1, F2 (cùng card SEARCH, khác target tìm kiếm), F3, F8, F9, F10.
- Chức năng không có card (`card() == null`) là **hành động**: F4 Add, F5 Edit, F6 Delete, F7 Reset. Bấm vào thì mở dialog, màn hình đang xem giữ nguyên.

**`CardLayout`:** một panel chứa nhiều "card" chồng lên nhau, mỗi lúc chỉ hiện một card (`cardLayout.show(panel, "SEARCH")`). Chuyển chức năng chỉ là đổi card đang hiện, không tạo lại màn hình, nên những gì đang gõ dở ở màn hình khác vẫn còn.

**Điểm mở rộng cho các bước sau:** bước 7 đặt màn hình tạm (`PlaceholderPanel`) vào mọi card. Bước 8–12 chỉ cần gọi `mainFrame.setScreen(Card.SEARCH, new SearchPanel(...))` hoặc `mainFrame.setAction(Feature.ADD, ...)`, không phải sửa logic điều hướng. Màn hình nào cần biết mình được mở bằng F1 hay F2 thì cài interface `FeatureScreen`; MainFrame dùng *pattern matching* `if (screen instanceof FeatureScreen fs)` để gọi `fs.onShown(feature)`.

**`Sidebar`:**
- Nút màn hình là `JToggleButton` trong một `ButtonGroup`, nên luôn có đúng một nút được chọn; nút đang chọn có vạch màu nhấn ở mép trái (tự vẽ trong `paintComponent`).
- Nút hành động là `JButton` thường.
- Sidebar không tự đổi màn hình mà chỉ báo "người dùng chọn chức năng X" qua callback `Consumer<Feature>` (truyền vào `this::showFeature`). Nhờ vậy Sidebar không phụ thuộc vào MainFrame.
- Bố cục `GridBagLayout`: các nút kéo dãn hết chiều ngang, một ô trống cuối cùng (`weighty = 1`) đẩy các nút dồn lên trên.

**Đổi ngôn ngữ ngay khi đang chạy (mẫu *Observer*):**

```
người dùng chọn "English"
  → Messages.setLocale(ENGLISH)     đổi ResourceBundle
  → gọi lần lượt mọi listener       Sidebar.applyTexts(), MainFrame.applyTexts(), PlaceholderPanel.applyTexts(), SwingLocalizer...
  → SettingsStore lưu language=en
```

- Mỗi component tự đăng ký `Messages.addListener(this::applyTexts)` và tự đặt lại chữ của mình. `Messages` không cần biết có những màn hình nào, nên thêm màn hình mới không phải sửa `Messages`.
- Danh sách listener là `CopyOnWriteArrayList`: listener có thể tự gỡ mình ngay trong lúc đang được gọi mà không gây `ConcurrentModificationException`.
- `Messages.format(key, args)` dùng `MessageFormat`: `{0,number,integer}` định dạng số theo ngôn ngữ (**7.638** trong tiếng Việt, **7,638** trong tiếng Anh). Trong các chuỗi này dấu `'` phải viết là `''`, nếu không MessageFormat sẽ nuốt mất phần chữ phía sau; `MessagesTest` có kiểm tra điều này.
- Thiếu key thì hiện `!key!` thay vì crash: lỗi lộ ra bằng mắt mà app vẫn chạy. Test đảm bảo hai file `.properties` có **đúng cùng bộ key**.
- `SwingLocalizer` dịch luôn nút OK/Hủy/Có/Không của `JOptionPane`, vì Swing không có sẵn bản dịch tiếng Việt.
- Menu ngôn ngữ luôn ghi "Tiếng Việt" / "English" bằng chính ngôn ngữ đó, và tiêu đề menu là "Ngôn ngữ (Language)", để ai lỡ chọn nhầm vẫn tìm được đường quay lại.

**Đổi theme sáng/tối:** có hai cách, cả hai cùng gọi `MainFrame.changeTheme(...)` nên luôn khớp nhau:
- Nút mặt trời/mặt trăng ở góc phải thanh menu (`ThemeToggleIcon`, tự vẽ bằng Java2D), phím tắt **Ctrl+Shift+L**. Đang tối thì nút vẽ mặt trời (bấm để sang sáng); đang sáng thì vẽ mặt trăng. Mặt trăng khuyết là một hình tròn **trừ** đi một hình tròn lệch (`java.awt.geom.Area.subtract`). `Box.createHorizontalGlue()` đẩy nút sát mép phải.
- Menu Giao diện → Sáng / Tối.

Bên trong: `FlatDarkLaf.setup()` rồi `FlatLaf.updateUI()`. Hàm này gọi `updateUI()` trên mọi component của mọi cửa sổ, nên tất cả tự vẽ lại bằng màu mới. Component nào tự tính màu (nền Sidebar, đường viền thanh trạng thái) thì override `updateUI()` để tính lại.
> **Bẫy `UIResource`:** màu lấy từ `UIManager` có kiểu `ColorUIResource`. Khi đổi theme, Swing coi mọi màu kiểu UIResource là "màu mặc định của theme cũ" và ghi đè bằng màu mặc định mới. Đã gặp thật: đổi tối → sáng thì tiêu đề nhóm trên Sidebar bị đổi từ xám sang đen. Cách sửa: `ThemeManager.color()` trả về bản sao `new Color(...)` thường, nên Swing hiểu đó là màu do mình tự đặt và không động vào.
> **Bẫy:** `updateUI()` được gọi lần đầu ngay trong constructor của `JPanel`, **trước** khi các field của lớp con được khởi tạo, nên phải kiểm tra `if (headers != null)`.

Các tinh chỉnh chung (bo góc 8 px, thanh cuộn bo tròn) đặt bằng `UIManager.put`, nên vẫn giữ nguyên sau khi đổi theme.

**`SettingsStore`:** lưu `language=vi`, `theme=DARK` vào `data/settings.properties` (`java.util.Properties`), ghi file tạm rồi đổi tên giống `.dat`. File thiếu, hỏng hay có giá trị lạ thì dùng mặc định (tiếng Việt, **tối**), nên **cài đặt không bao giờ làm app không mở được**. Enum `Theme` nằm ở `model` chứ không ở `ui`, vì tầng `io` không được phụ thuộc ngược lên `ui` (quy tắc ở mục 3).

**Khởi động bằng `SwingWorker`:**

```
EDT:        cài ngôn ngữ + theme → hẹn giờ 300 ms hiện LoadingWindow → worker.execute() → (EDT rảnh, vẫn vẽ được)
luồng nền:                                                  doInBackground(): store.loadOrBuild()
EDT:        done(): đóng LoadingWindow → tạo MainFrame → hiện
```

- `doInBackground()` chạy trên luồng nền; `done()` tự quay về EDT nên được phép tạo cửa sổ.
- Nếu tải ngay trên EDT thì cửa sổ "Đang tải…" sẽ đứng hình, không vẽ được, vì EDT đang bận.
- Cửa sổ loading chỉ hiện khi tải lâu hơn **300 ms** (dùng `javax.swing.Timer`). Với 7,6k slang (~0,1 s) nó không hiện, tránh cảnh cửa sổ chớp lên rồi tắt; với 100k slang (~1,6 s) thì có hiện.
- Không tải được dữ liệu (ví dụ `slang.txt` sai định dạng) thì hiện dialog lỗi có số dòng rồi thoát.

**Thoát app an toàn:** cửa sổ dùng `DO_NOTHING_ON_CLOSE`. Khi bấm X (hoặc Ctrl+Q), app ẩn cửa sổ ngay, rồi một luồng riêng gọi `store.close()` (chờ luồng ghi nền lưu xong `.dat`) rồi mới `System.exit(0)`. Người dùng không phải nhìn một cửa sổ đứng hình 2 giây trong lúc từ điển 100k đang được lưu.

**Báo lỗi:**
- Lỗi ghi `.dat` xảy ra trên luồng ghi nền, nên được chuyển về EDT bằng `invokeLater` rồi mới hiện lên thanh trạng thái (màu đỏ). Dialog cảnh báo chỉ hiện lần đầu để không làm phiền. Lỗi xảy ra trước khi MainFrame kịp hiện thì được giữ lại và báo sau.
- `Thread.setDefaultUncaughtExceptionHandler`: exception không ai bắt (bug) sẽ hiện thành dialog, thay vì âm thầm làm hỏng một thao tác.

**Icon tự vẽ (Java2D):** `NumberBadgeIcon` (số 1–10 trong ô bo tròn) và `AppIcon` (chữ S) được vẽ bằng `Graphics2D`, không cần file ảnh. Màu lấy theo theme **lúc vẽ**, nên đổi sáng/tối là icon đổi màu theo.

**About (Trợ giúp → Giới thiệu):** tên app, phiên bản, môn học, **Nguyễn Anh Khôi – MSSV 24127430**, thư viện (FlatLaf – Apache License 2.0, JUnit 5), phiên bản Java và thư mục dữ liệu.


### 4.12 Màn hình tìm kiếm `SearchPanel` (bước 8, F1 + F2)

**Bố cục:**

```
┌──────────────────────────────────────────────────────────────────────────┐
│ [ Slang ] [ Định nghĩa ]                       ← target: F1 / F2           │
│ [🔍 laugh out lound ..............  ✕]  Kiểu khớp: [Chính xác ▾]  [Tìm]   │
│ Nghĩa có đủ mọi từ khóa (đúng nguyên từ)…      ← giải thích mode đang chọn │
├──────────────────────────────────────────────────────────────────────────┤
│ Không tìm thấy kết quả nào · 0,26 ms   Có phải bạn muốn tìm: laugh out loud │
│                                                    [4 Thêm] [5 Sửa] [6 Xóa] │
│ ┌ Slang ─┬ Nghĩa ───────────────────┐  ┌ LOLL ────────────────────────────┐ │
│ │ BLOL   │ Big Laugh Out Loud       │  │ 1 nghĩa                          │ │
│ │ LOLL   │ Laugh Out Loud Literally │  │ 1. [Laugh] [Out] [Loud] Literally│ │
│ └────────┴──────────────────────────┘  └──────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────┘
```

- **Một màn hình cho cả F1 và F2.** `SearchPanel` cài `FeatureScreen`: mở bằng F1 (Sidebar, menu, Ctrl+1) thì chọn tab Slang, F2 thì tab Định nghĩa. Ngược lại, người dùng bấm tab ngay trên màn hình thì panel gọi callback `navigator` (= `mainFrame::showFeature`), nên Sidebar và tiêu đề cửa sổ đổi theo F1 ↔ F2. Hai chiều không gọi nhau mãi vì `onShown` thấy target đã khớp thì không làm gì.
- **Mỗi target nhớ mode riêng** (`EnumMap<SearchTarget, SearchMode>`): slang mặc định **Tiền tố** (gõ tới đâu ra tới đó), definition mặc định **Chính xác** (đúng nghĩa "definition có chứa keyword" của đề).
- **Tìm khi đang gõ** chỉ ở Slang + Tiền tố: mỗi lần gõ thì khởi động lại một `javax.swing.Timer` 150 ms, ngừng gõ đủ 150 ms mới tìm (*debounce*), nên gõ nhanh "lmao" chỉ tìm 1 lần chứ không 4 lần. Các mode khác tìm khi nhấn Enter hoặc nút Tìm.
- **Ghi lịch sử:** chỉ lần tìm "thật" (Enter, nút Tìm, bấm gợi ý) mới gọi `searchListener` với một `HistoryItem`; tìm-khi-gõ thì không, nếu không lịch sử sẽ đầy "l", "lo", "lol". Bước 9 chỉ cần `searchPanel.setSearchListener(historyService::add)`.
- **Bảng kết quả:** `JTable` + `AbstractTableModel` tự viết (2 cột, dữ liệu là `List<SearchResult>` đã xếp hạng). Không cho bấm tiêu đề để sắp xếp lại, vì thứ tự chính là thứ hạng của `SearchService`. Bảng hiện tối đa **1.000 dòng**; dòng trạng thái vẫn ghi đủ tổng số, ví dụ "Hiện 1.000 / 88.464 kết quả đầu tiên · 230 ms". Ô bị cắt "…" thì rê chuột vào thấy đủ nội dung.
- **Khung chi tiết:** kết quả tốt nhất được chọn sẵn, nên tìm "lol" là thấy ngay nghĩa của LOL. Khi tìm theo definition, các từ khóa khớp được tô màu bằng `Highlighter` của `JTextArea`. Vị trí cần tô do `matchRanges` tính bằng regex theo đúng kiểu khớp: EXACT `(?<![\p{L}\p{N}])the(?![\p{L}\p{N}])` (nguyên từ, *lookbehind/lookahead*), PREFIX chỉ cần đầu từ, SUBSTRING ở bất kỳ đâu; từ khóa đi qua `Pattern.quote` nên ký tự như `+` không làm hỏng regex.
- **"Có phải bạn muốn tìm…"** khi không có kết quả (trừ mode Gần đúng), dùng `SearchService.suggestions` (BK-tree):
  - Slang: tối đa 5 tên gần nhất, ví dụ `lool` → LOL. Tên trong index đã chuyển chữ thường, nên lấy cách viết gốc của entry.
  - Definition: sửa từng từ không có trong từ vựng thành từ gần nhất (hòa thì lấy từ xuất hiện trong nhiều slang hơn): `laugh out lound` → `laugh out loud`.
  - Bấm vào gợi ý là tìm luôn.
- **Bàn phím:** Enter tìm, ↓ từ ô tìm xuống bảng, Esc xóa ô tìm, Delete trong bảng = chức năng 6. Nút Thêm/Sửa/Xóa gọi chức năng 4/5/6 của MainFrame (bước 10 gắn dialog thật qua `setAction`); Sửa/Xóa chỉ bấm được khi đang chọn một dòng (`selectedEntry()`).

**Tìm trên luồng nền và bỏ kết quả cũ:**

```
EDT:   gõ / Enter → ticket = generation.incrementAndGet() → executor.execute(...)   (EDT rảnh ngay)
luồng "search":  ticket còn mới nhất? → service.search(...) (+ suggestions nếu rỗng)
                 → SwingUtilities.invokeLater(...)
EDT:   ticket vẫn là mới nhất? → đổ vào bảng; không thì bỏ (người dùng đã gõ tiếp)
```

- Query nặng nhất ở bản 100k (definition chứa `e`, ~0,2 s) không làm đứng giao diện; nếu lâu hơn 120 ms thì dòng trạng thái hiện "Đang tìm…".
- `generation` là `AtomicLong`, vì được đọc ở cả hai luồng. Nhờ nó, kết quả của lần tìm cũ về muộn không bao giờ đè lên kết quả mới.
- **Khóa:** add/edit/delete (bước 10, mục 4.14) chạy trên EDT, còn tìm kiếm chạy trên luồng "search". `SearchService` vì vậy đọc index bên trong `synchronized (dictionary)`, cùng khóa với `add/replace/remove` của `SlangDictionary`. Nếu không có khóa, luồng tìm có thể đọc `HashMap` đúng lúc EDT đang sửa dở (`ConcurrentModificationException` hoặc kết quả sai).
- Luồng "search" là *daemon thread*, nên không giữ JVM lại khi thoát app.

**Test không cần màn hình:** `SearchPanelTest` truyền executor `Runnable::run` (tìm ngay trên luồng gọi) qua constructor package-private, tạo panel trên EDT bằng `invokeAndWait`, rồi chờ EDT xử lý xong `invokeLater` trước khi kiểm tra bảng.

### 4.13 Lịch sử tìm kiếm (bước 9, F3): `HistoryStore`, `HistoryService`, `HistoryPanel`

**Luồng dữ liệu:**

```
SearchPanel: Enter / nút Tìm / bấm gợi ý
  → searchListener.accept(HistoryItem)          (App nối: searchPanel.setSearchListener(history::add))
  → HistoryService.add(item)                    thêm vào ĐẦU danh sách (mới nhất ở đầu), tối đa 1.000 dòng
       ├─ HistoryStore.saveInBackground(bản sao) luồng "history-writer" ghi history.dat (tmp → đổi tên)
       └─ báo các listener                       HistoryPanel vẽ lại bảng (kể cả khi đang ẩn)

HistoryPanel: bấm đúp / Enter / nút "Tìm lại"
  → replayer.accept(item)                       (App nối: item -> searchPanel.search(target, mode, query))
  → SearchPanel chuyển sang F1 hoặc F2, đặt đúng mode + query rồi tìm như nhấn Enter
```

**Một lần tìm là gì:** `HistoryItem` là record gồm `target` (slang/definition), `mode`, `query`, `time`. Lưu cả mode để tìm lại ra **đúng** kết quả cũ: `lol` theo Tiền tố khác `lol` theo Gần đúng. Chỉ lần tìm "thật" mới được ghi (tìm-khi-gõ thì không, nếu không lịch sử sẽ đầy "l", "lo", "lol").

**`HistoryService`** (nghiệp vụ, không biết gì về Swing):
- `ArrayList` mới nhất ở vị trí 0; quá 1.000 dòng thì `subList(1000, size).clear()` bỏ các dòng cũ nhất ở cuối.
- Tìm lại **đúng lần mới nhất** (cùng loại, mode, query, không phân biệt hoa thường) thì chỉ cập nhật thời gian, tránh nhấn Enter 3 lần ra 3 dòng giống hệt. Trùng một dòng cũ hơn thì vẫn thêm dòng mới, vì đó là một lần tìm mới.
- `remove(int... indexes)`: varargs, xóa được nhiều dòng một lúc. Xóa **từ vị trí lớn về nhỏ**, vì xóa phần tử ở vị trí 1 trước thì phần tử ở vị trí 3 sẽ dịch thành 2 và xóa nhầm. Vị trí trùng hoặc ngoài phạm vi bị bỏ qua.
- `items()` trả về `Collections.unmodifiableList(items)`: bên ngoài đọc được mà không sửa lén được (sửa thì `UnsupportedOperationException`), mọi thay đổi bắt buộc đi qua `add/remove/clear` để chắc chắn được lưu và báo listener.
- Mỗi thay đổi gọi `changed()`: lưu nền + báo listener (mẫu **Observer**, giống `Messages.addListener`). Nhờ vậy `HistoryPanel` không cần "hỏi lại" service, và Reset (bước 11) chỉ cần `history.clear()` là màn hình tự cập nhật.
- File hỏng thì bắt đầu với lịch sử rỗng: lịch sử không đáng để app không mở được. Lần lưu kế tiếp sẽ ghi đè file hỏng.

**`HistoryStore`** (đọc/ghi file), cùng cách làm với `DictionaryStore` (mục 4.8) nhưng gọn hơn:

| | `dictionary.dat` | `history.dat` |
|---|---|---|
| Header | `SLNG` + phiên bản + **CRC32** | `HIST` + phiên bản |
| Ghi | tmp rồi đổi tên, luồng nền, gộp lần lưu dồn dập | giống hệt (dùng lại `DictionaryStore.writeAtomically`) |
| `ObjectInputFilter` | app + `java.util` + `java.lang` | `model` + `java.util` + `java.lang` + **`java.time`** |
| File hỏng | build lại từ `slang.txt` | bắt đầu với lịch sử rỗng |

- Không có CRC vì lịch sử chỉ là thông tin phụ: hỏng một ký tự trong query cũng không làm sai chức năng nào.
- Cần `java.time` vì `LocalDateTime` được serialize qua class trung gian `java.time.Ser`. Test `classesOutsideTheAllowListAreNeverInstantiated` ghi vào file một class lạ có `readObject` và kiểm tra filter chặn nó **trước khi** nó kịp chạy.
- Đọc lên là `List<?>` vì **type erasure**: lúc chạy Java không biết đó là `List<HistoryItem>`, nên phải kiểm tra `instanceof HistoryItem` từng phần tử.
- **Record + serialization:** khi đọc lại, Java gọi đúng constructor của record, nên compact constructor (kiểm tra null) vẫn chạy. File sửa tay cho `query = null` sẽ bị từ chối chứ không lọt vào app.
- `saveInBackground` nhận **bản sao** (`List.copyOf`) chứ không phải danh sách gốc: luồng ghi đọc danh sách sau, trong lúc EDT có thể đang thêm dòng mới; dùng chung một `ArrayList` giữa hai luồng là lỗi đa luồng.

**`HistoryPanel`** (F3):

```
┌──────────────────────────────────────────────────────────────────────────┐
│ [3] Lịch sử tìm kiếm                  [Tìm lại] [Xóa dòng chọn] [Xóa tất cả] │
│ 3 lượt tìm · bấm đúp hoặc nhấn Enter ở một dòng để tìm lại · Delete để xóa │
│ ┌ Thời gian ───────────┬ Loại ──────┬ Kiểu khớp ┬ Từ khóa ───────────────┐ │
│ │ 11/11/2026  00:58:55 │ Định nghĩa │ Chính xác │ laugh out lound        │ │
│ │ 11/11/2026  00:58:01 │ Slang      │ Tiền tố   │ :|                     │ │
│ └──────────────────────┴────────────┴───────────┴────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────┘
```

- `AbstractTableModel` **đọc thẳng từ service** (`getRowCount() = service.size()`), không giữ bản sao, nên không bao giờ lệch. Khi service báo thay đổi thì `fireTableDataChanged()`.
- Cột Loại, Kiểu khớp và Thời gian được dịch **lúc vẽ** (`getValueAt`), nên đổi ngôn ngữ là cả bảng đổi theo. Mẫu thời gian nằm trong `.properties` (`dd/MM/yyyy  HH:mm:ss` / `MMM d, yyyy  h:mm:ss a`) và đi qua `DateTimeFormatter.ofPattern(pattern, locale)`.
- Tiếng Anh có số ít/số nhiều: `{0,choice,1#1 search|1<{0,number,integer} searches}` (phần `choice` của MessageFormat). Tiếng Việt không cần.
- Bàn phím: Enter tìm lại, Delete xóa dòng chọn. Enter mặc định của `JTable` là "xuống dòng dưới" (đăng ký ở `WHEN_ANCESTOR_OF_FOCUSED_COMPONENT`); phím gắn ở `WHEN_FOCUSED` được ưu tiên hơn nên ghi đè được.
- Chọn nhiều dòng (Ctrl/Shift) để xóa một lúc. Xóa xong thì chọn sẵn dòng vừa dịch lên chỗ đó, nên nhấn Delete liên tiếp được. Xóa từng dòng không hỏi lại (chỉ là lịch sử); "Xóa tất cả" thì có hộp xác nhận.
- Mở màn hình thì dòng mới nhất được chọn sẵn và bảng có focus (`FeatureScreen.onShown`), nên Ctrl+3 rồi Enter là tìm lại ngay lần vừa tìm.
- Chưa có lịch sử: một `CardLayout` nhỏ bên trong đổi bảng thành màn hình trống có hướng dẫn "tìm ở Ctrl+1 / Ctrl+2".
- `replay(row)` lấy `HistoryItem` **trước** khi gọi `replayer`, vì tìm lại sẽ ghi thêm một dòng vào đầu lịch sử làm các dòng dịch xuống.
- **Test được mà không mở dialog:** hộp xác nhận "Xóa tất cả" đi qua field `Predicate<String> confirmer` (mặc định là `JOptionPane`); test thay bằng `message -> true/false`.

**Lỗi ghi `history.dat`:** báo qua `errorListener` → `invokeLater` về EDT → thanh trạng thái màu đỏ. Dialog cảnh báo dùng câu riêng (`error.historySaveFailed`), và mỗi loại lỗi (từ điển / lịch sử) chỉ hiện dialog một lần (`MainFrame.shownSaveErrorDialogs` là một `Set`).

**Thoát app:** `App.closeAll` chờ cả hai luồng ghi (`history.dat`, `dictionary.dat`) xong rồi mới `System.exit`. Shutdown hook cũng gọi `closeAll`, gọi hai lần không sao vì `close()` của executor đã đóng thì bỏ qua.

### 4.14 Thêm / Sửa / Xóa (bước 10, F4–F6): `DictionaryService`, `EntryActions`, hai dialog

**Luồng một lần thêm:**

```
Ctrl+4 / Sidebar / nút Thêm
  → EntryActions.add()
  → EntryEditorDialog (tên + bảng nghĩa)  ── Hủy ──► thôi
  → DictionaryService.add(tên, nghĩa, resolver)
       ├─ tên chưa có ──────────────────► SlangDictionary.add  (cập nhật MỌI index)
       └─ tên đã có ─► resolver = ConflictDialog: Ghi đè / Thêm nghĩa / Tạo bản mới / Quay lại sửa
                                                                     └─► mở lại EntryEditorDialog với dữ liệu cũ
  → DictionaryStore.saveInBackground (ghi .dat sau 1,5 s) + báo listener (thanh trạng thái: số slang)
  → SearchPanel.showChange: "✓ Đã thêm …", tìm lại, chọn sẵn entry vừa thêm
```

**3 lựa chọn khi trùng tên** (so sánh không phân biệt hoa thường, giống tìm EXACT):

| | Thêm tên đã có (F4) | Đổi tên thành tên của entry khác (F5) |
|---|---|---|
| Ghi đè (`OVERWRITE`) | thay toàn bộ nghĩa của entry đích, giữ id | xóa entry đích, giữ entry đang sửa |
| Thêm nghĩa (`ADD_MEANING`) | nối nghĩa mới vào entry đích, bỏ nghĩa đã có | gộp nghĩa vào entry đích, xóa entry đang sửa |
| Tạo bản mới (`NEW_ENTRY`) | thêm entry riêng cùng tên (đề gọi là "duplicate") | giữ cả hai |

Có nhiều entry cùng tên thì người dùng chọn **entry đích** trong danh sách. Đây là lý do `SlangEntry` có id riêng (mục 4.2): hai entry "gg" khác nhau vẫn phân biệt được. Sửa mà không đổi tên (chỉ đổi nghĩa hay hoa/thường) thì không hỏi.

**Tách nghiệp vụ khỏi giao diện:**
- `DictionaryService` không import gì của Swing. Khi trùng tên nó gọi `ConflictResolver`, một **functional interface**: app truyền hàm mở `ConflictDialog`, test truyền lambda (`conflict -> Optional.of(new Decision(OVERWRITE, …))`). Đây là **mẫu Strategy**: cách quyết định được truyền từ ngoài vào.
- Kết quả trả về là record `ChangeResult` (`CHANGED` / `UNCHANGED` / `CANCELLED`, lựa chọn đã dùng, entry sau khi đổi, các entry bị xóa). `EntryActions` dựa vào đó chọn câu thông báo và entry cần chọn sẵn.
- `DictionaryService.validate` dùng chung cho dialog (báo lỗi màu đỏ ngay trong dialog) và service (ném `IllegalArgumentException` nếu ai gọi sai). Tên không được chứa `` ` ``, nghĩa không được chứa `|`, vì đó là hai ký tự ngăn cách trong `slang.txt`; tên thì được chứa `|` vì file gốc có slang `:|`.
- Chuẩn hóa nghĩa bằng `LinkedHashMap<khóa chữ thường, nghĩa>`: bỏ trùng không phân biệt hoa thường mà **giữ thứ tự** và cách viết của lần xuất hiện đầu.
- Các bước sửa của một thao tác (ví dụ OVERWRITE khi đổi tên = xóa entry đích rồi thay entry đang sửa) nằm trong `synchronized (dict)`, nên luồng tìm kiếm không bao giờ thấy trạng thái nửa chừng.
- `DictionaryService` giữ **từ điển hiện tại** (`volatile`). `SearchService` lấy qua `dictionaryService::current`, nên Reset (bước 11) chỉ cần thay field này.

**`EntryEditorDialog`:**
- Bảng nghĩa là `JTable` 1 cột sửa trực tiếp (cột # tự tính lại khi xóa/đổi chỗ). Nút Thêm nghĩa / Xóa nghĩa / Lên / Xuống; phím Insert, Delete, Alt+↑/↓.
- `terminateEditOnFocusLost` + `stopCellEditing()` trước khi kiểm tra: bấm Lưu khi một ô còn đang gõ dở thì chữ đó vẫn được tính (lỗi kinh điển của `JTable` nếu quên).
- Đang gõ tên thì báo trước "⚠ Đã có N slang tên này" (đếm qua `countOthersNamed`, không tính chính entry đang sửa).
- Enter ở ô tên: chưa có nghĩa thì chuyển xuống ô nghĩa, có rồi thì lưu. Ctrl+Enter lưu, Esc hủy (khi không có ô nào đang sửa dở: Esc lúc đó chỉ hủy ô đó, vì phím gắn ở component có focus được ưu tiên hơn phím gắn ở cửa sổ).
- Phần nội dung là class `Form extends JPanel`, `JDialog` chỉ bọc bên ngoài, nên test được mà không mở cửa sổ.

**`ConflictDialog`:** mỗi lựa chọn là một nút lớn có mô tả (bấm là xong, không phải chọn rồi bấm OK); chữ mô tả khác nhau giữa thêm và sửa. Focus mặc định **không** đặt ở "Ghi đè": lỡ nhấn Enter thì không mất dữ liệu. Tên/nghĩa được thoát ký tự HTML (`<3`, `&` hiện đúng nguyên văn).

**`EntryActions`** (nối dialog ↔ service ↔ màn hình Search):
- Sửa/Xóa luôn làm trên slang đang chọn ở màn hình Search; đang ở màn hình khác thì chuyển về; chưa chọn thì hiện hướng dẫn. Entry được đọc lại theo id trước khi sửa (bảng có thể đang hiện bản cũ).
- Thêm: tên điền sẵn là chữ đang gõ ở ô tìm slang, vì trường hợp hay gặp là "tìm không thấy → thêm luôn".
- Dialog đi qua interface `Prompts`; `EntryActionsTest` thay bằng câu trả lời soạn sẵn và chạy trọn luồng thêm/sửa/xóa mà không mở cửa sổ nào.

**`SearchPanel` sau khi dữ liệu đổi:** `showChange(entry, …)` tìm lại lần tìm đang hiện và chọn sẵn entry theo **id**; nếu entry không còn khớp (ví dụ vừa đổi tên) thì tự tìm theo tên mới. `showRemoval` chọn dòng ở đúng chỗ vừa xóa. Dòng chọn được truyền kèm yêu cầu tìm (record `Selection`) chứ không để trong field, vì kết quả về trễ trên luồng khác. Thông báo lưu dạng key + tham số nên đổi ngôn ngữ vẫn dịch lại được, và tự mất khi người dùng tìm tiếp.

**Thời gian tìm hiển thị:** `SearchService` bắt đầu đo **sau khi** lấy được khóa từ điển. Nếu luồng ghi `.dat` đang giữ khóa thì lần tìm phải chờ, nhưng con số trên màn hình vẫn là thời gian tìm thật (test `elapsedTimeDoesNotCountWaitingForTheSaveLock`).

**Giới hạn còn lại (chỉ ở 100k):** nếu đúng lúc luồng ghi đang serialize (~2 s) mà người dùng thêm/sửa/xóa tiếp, thao tác đó chờ ghi xong. Muốn bỏ hẳn thì phải ghi nhật ký thay đổi (journal) thay vì serialize cả từ điển mỗi lần.

### 4.15 Reset và Slang hôm nay (bước 11, F7 + F8): `ResetAction`, `ResetDialog`, `RandomService`, `RandomPanel`

**Luồng Reset:**

```
Ctrl+7 / Sidebar / menu Tệp → ResetAction.run()                       (EDT)
  → ResetDialog: "Đưa từ điển về danh sách gốc?" + [ ] Xóa cả lịch sử ── Hủy ──► thôi
  → khóa cửa sổ (con trỏ chờ; "Đang khôi phục…" nếu lâu hơn 0,3 s)
  → DictionaryService.loadOriginal()                                   (luồng nền "reset")
       đọc data/slang.txt nếu có, không thì slang.txt nhúng trong jar → SlangDictionary MỚI
  → DictionaryService.replaceDictionary(mới)                           (EDT)
       gán field volatile + lưu .dat nền + báo listener (thanh trạng thái, màn hình Slang hôm nay)
  → (nếu chọn) HistoryService.clear() → màn hình Lịch sử tự trống (Observer, mục 4.13)
  → SearchPanel.showReload: tìm lại lần đang hiện trên dữ liệu mới
  → mở khóa cửa sổ, báo "Đã khôi phục 7.638 slang (tạo index trong 120 ms)"
```

- **Reset = thay cả object**, không xóa từng entry rồi thêm lại. Mọi nơi đọc từ điển đều đi qua `dictionaryService::current` (một `Supplier`, mục 4.9), nên chỉ cần gán lại **một field**. Field là `volatile` để luồng tìm kiếm thấy ngay object mới. Lần tìm đang chạy dở trên object cũ vẫn xong bình thường vì object cũ không bị ai sửa.
- **Build trên luồng nền:** 7,6k slang mất ~0,1 s nhưng file 100k mất ~1,5 s, làm trên EDT thì cửa sổ đứng. Từ điển cũ không bị đụng tới trong lúc build, nên file gốc lỗi (thiếu, sai định dạng) chỉ làm hiện thông báo lỗi; từ điển và lịch sử giữ nguyên.
- **Khóa cửa sổ** (`frame.setEnabled(false)`) trong lúc build: nếu không, người dùng có thể kịp mở dialog Sửa trên entry sắp biến mất.
- Dialog: nút mặc định (Enter) là **Hủy**, checkbox "Xóa cả lịch sử" mặc định **không** chọn (lịch sử trống thì checkbox bị tắt). Dialog ghi rõ đang có bao nhiêu slang và file gốc nằm ở đâu.
- `ResetAction` nhận hai `Executor` (luồng nền, EDT) và một interface `Ui`. App truyền luồng mới + `SwingUtilities::invokeLater` + dialog thật; test truyền `Runnable::run` + giao diện giả nên chạy hết luồng mà không mở cửa sổ nào.

**Slang hôm nay ("On this day"):** đề yêu cầu cả ngày ra cùng một slang. Cách đơn giản `new Random(ngày).nextInt(size)` có nhược điểm: kết quả phụ thuộc số entry và thứ tự của chúng, nên thêm hay xóa **bất kỳ** slang nào cũng làm slang hôm nay đổi sang từ khác. Ở đây dùng **rendezvous hashing** (highest random weight):

```
score(ngày, id) = mix(mix(ngày) + id)      ← mix = hàm trộn bit của SplitMix64 (thuật toán của SplittableRandom)
slang hôm nay   = entry có score cao nhất  (hòa thì id nhỏ hơn)
```

- Điểm của mỗi entry chỉ phụ thuộc **ngày và id của chính nó**. Thêm một slang chỉ đổi kết quả nếu slang mới thắng tất cả (xác suất 1/n); xóa chỉ đổi kết quả nếu xóa đúng slang hôm nay; sửa nghĩa (giữ id) không bao giờ đổi. Tắt app mở lại vẫn ra đúng slang đó vì id được lưu trong `.dat`.
- Sang ngày khác thì điểm của mọi entry bị "xáo" lại, mỗi entry có cơ hội như nhau (test chạy 5.000 ngày trên 10 entry, mỗi entry trúng khoảng 500 lần).
- Cái giá: phải duyệt hết O(n) thay vì O(1). 100k slang mất vài ms, và màn hình chỉ tính lại khi được mở mà dữ liệu đã đổi hoặc đã sang ngày mới.

**Random khác:** `SlangDictionary.randomEntry` lấy phần tử ngẫu nhiên của danh sách id trong O(1) (swap-remove, mục 4.7). `RandomService.random(idĐangHiện)` bốc lại nếu trúng đúng slang đang hiện, nên bấm "Random khác" luôn ra slang mới.

**`RandomPanel`:**
- Thẻ bo góc tự vẽ bằng Java2D (`RoundRectangle2D`, vạch màu nhấn bên trái được `clip` theo góc bo), màu lấy từ theme nên đổi sáng/tối vẫn đẹp. Thẻ cao vừa nội dung; slang nhiều nghĩa thì cả vùng cuộn được (panel cài `Scrollable` để luôn rộng bằng khung nhìn, chữ tự xuống dòng).
- Nút: **Random khác** (R), **Về slang hôm nay** (H), **Tra trong từ điển** (Enter): mở slang đó ở màn hình tìm kiếm (EXACT) để xem, sửa hoặc xóa.
- Ngày hiện theo ngôn ngữ (`EEEE, dd/MM/yyyy` → "Thứ Tư, 11/11/2026"; `EEEE, MMMM d, yyyy` → "Wednesday, November 11, 2026").
- Nghe `DictionaryService.addChangeListener`: slang đang hiện bị sửa thì hiện bản mới (đọc lại theo id), bị xóa thì chọn slang khác, sau Reset thì tính lại trên từ điển mới. Màn hình đang ẩn thì chỉ đánh dấu "cần tính lại" (`stale`), để dành tới lúc được mở.

**Thanh trạng thái** sau Reset ghi "7.638 slang · khôi phục từ slang.txt gốc trong 120 ms" thay cho nguồn lúc khởi động.

---

## 5. Unit test

| File test | Kiểm tra gì |
|---|---|
| `io/OriginalDataResourceTest` | `slang.txt` có trong jar và đủ 7.638 entry |
| `io/SlangFileParserTest` | Đọc đủ **7.638 entry và 8.941 cặp slang–nghĩa**, đối chiếu với cách tính độc lập. Slang chứa `\|`, UTF-8, BOM, dòng lỗi báo đúng số dòng, ghi ra rồi đọc lại phải giống hệt |
| `model/ModelTest` | Kiểm tra dữ liệu đầu vào, so sánh bằng nhau theo id, thứ tự sắp xếp kết quả, quiz bắt buộc có 4 đáp án |
| `ds/DataStructuresTest` | Levenshtein ra đúng giá trị đã biết. Trie và BK-tree được **so với cách duyệt tuần tự** trên 2.000–3.000 từ ngẫu nhiên và phải ra kết quả giống hệt. Lưu xuống `.dat` rồi đọc lại vẫn đúng |
| `core/TokenizerTest` | Tách từ đúng và giữ thứ tự, giữ chữ Unicode, không phụ thuộc locale của máy (thử với locale Thổ Nhĩ Kỳ), chuẩn hóa tên giữ nguyên ký hiệu |
| `core/SlangDictionaryTest` | Build từ file gốc đủ 7.638 entry, tra tên không phân biệt hoa thường, tên trùng là các entry riêng, **hàng nghìn thao tác ngẫu nhiên xong mọi index vẫn giống hệt từ điển build lại từ đầu**, serialize rồi đọc lại vẫn đúng |
| `io/AppPathsTest` | 3 trường hợp chọn thư mục data (system property / chạy từ jar / chạy trong IDE), tự tạo thư mục |
| `io/DictionaryStoreTest` | Lưu rồi đọc lại đúng; file sai magic/version/bị cắt cụt/chứa class lạ đều bị từ chối; **đổi bất kỳ byte nào cũng bị CRC phát hiện**; lần đầu build từ text, lần sau đọc `.dat`; `.dat` hỏng thì build lại; lưu dồn dập ra đúng trạng thái cuối; vừa sửa vừa lưu không ra file "nửa vời"; lỗi ghi nền được báo qua listener |
| `service/SearchServiceTest` | Từng mode × target trên từ điển nhỏ (kể cả ký hiệu `:\|`, tên trùng, AND, thưởng nguyên cụm, k = 0 cho từ ngắn); `limit` cắt đúng mà `totalMatches` vẫn đủ; luôn dùng từ điển hiện tại sau khi đổi (giống Reset); **so với cách duyệt tuần tự trên file gốc** cho cả 8 tổ hợp; gợi ý "Có phải bạn muốn tìm" (giữ cách viết gốc, không gợi ý lại chính query, sắp theo khoảng cách, chỉ sửa từ không có trong từ vựng) |
| `service/SearchBenchmarkTest` | Build 100k, mỗi tổ hợp (cả query "nặng" như `e`, `a`) phải < 1 s; in bảng thời gian ra console |
| `tools/BigDataGeneratorTest` | Giữ nguyên entry gốc, id không trùng, tên tổng hợp không trùng và đúng dạng, cùng seed thì cùng dữ liệu, file ghi ra đọc lại được, `main` ghi đúng số lượng |
| `i18n/MessagesTest` | Hai file `.properties` có **đúng cùng bộ key**, không có giá trị rỗng, chuỗi có tham số không chứa dấu `'` đơn lẻ; tiếng Việt đọc đúng UTF-8; đổi ngôn ngữ thì chữ đổi và listener được gọi đúng một lần (chọn lại ngôn ngữ đang dùng thì không gọi); `vi-VN` → `vi`, ngôn ngữ lạ → mặc định; số định dạng theo ngôn ngữ (7.638 / 7,638) |
| `io/SettingsStoreTest` | Chưa có file thì dùng mặc định; lưu rồi đọc lại đúng (tự tạo thư mục, không sót file `.tmp`); giá trị lạ/rỗng → mặc định; file hỏng không làm app lỗi và vẫn ghi đè được |
| `ui/panels/SearchPanelTest` | Enter → bảng đúng thứ hạng, dòng đầu được chọn, ghi đúng 1 dòng lịch sử; xóa ô tìm thì xóa kết quả; mở bằng F2 thì sang Định nghĩa mà không gọi ngược MainFrame, bấm tab thì có gọi; mỗi target nhớ mode riêng; EXACT không ra kết quả thì có gợi ý, bấm gợi ý thì tìm; tìm từ lịch sử; đổi ngôn ngữ thì dòng trạng thái và tiêu đề cột đổi theo; vị trí tô từ khóa đúng theo từng mode. Không mở cửa sổ |
| `io/HistoryStoreTest` | Chưa có file → rỗng; lưu rồi đọc lại đúng mọi field và thứ tự (kể cả `:\|`, UTF-8), không sót `.tmp`; sai magic/phiên bản, cắt cụt, rác, file rỗng đều bị từ chối; class lạ bị filter chặn **trước khi** khởi tạo; danh sách chứa thứ khác `HistoryItem` bị từ chối; 200 lần lưu nền dồn dập ra đúng bản cuối; lỗi ghi nền và lưu sau khi `close` được báo qua listener |
| `service/HistoryServiceTest` | Mới nhất ở đầu và còn nguyên sau khi "mở lại app"; mỗi thay đổi báo listener đúng một lần; lặp lại lần tìm mới nhất chỉ cập nhật thời gian (khác mode hoặc trùng dòng cũ thì vẫn thêm); giữ tối đa 1.000 dòng, bỏ dòng cũ nhất; xóa nhiều vị trí (bỏ qua trùng/ngoài phạm vi, không có gì để xóa thì không lưu); `clear` cả trên đĩa; `items()` chỉ đọc; file hỏng → rỗng rồi ghi đè; file quá 1.000 dòng bị cắt khi đọc |
| `ui/panels/HistoryPanelTest` | Màn hình trống khi chưa có lịch sử; cột thời gian/loại/kiểu khớp đúng ngôn ngữ (cả tiêu đề cột, số ít/số nhiều tiếng Anh); tìm lại đúng dòng chọn; xóa nhiều dòng rồi chọn dòng kế tiếp; "Xóa tất cả" hỏi trước (Không → giữ, Có → xóa); **nối dây giống App**: tìm ở SearchPanel → có lịch sử → tìm lại từ lịch sử → SearchPanel sang F2, đúng query, đúng kết quả |
| `service/DictionaryServiceTest` | (bước 11) Reset: về đúng 7.638 slang, slang vừa thêm biến mất, slang gốc bị sửa/xóa trở lại như cũ, chỉ báo listener một lần, đã lưu `.dat`, là object mới (object cũ không bị sửa), ưu tiên `data/slang.txt`, file gốc lỗi thì giữ nguyên từ điển. (bước 10) Thêm tên mới không hỏi và có lưu xuống `.dat`; trùng tên thì hỏi với đúng các entry cùng tên; **3 lựa chọn × thêm/đổi tên** (ghi đè giữ id, thêm nghĩa bỏ nghĩa đã có, tạo bản mới; đổi tên ghi đè xóa entry kia, gộp nghĩa xóa entry đang sửa); nhiều entry cùng tên thì tác động đúng entry được chọn; hủy thì không đổi gì; sửa không đổi tên thì không hỏi; không đổi gì thì không lưu; index tên và index nghĩa được cập nhật (tìm EXACT/FUZZY sau khi đổi tên); dữ liệu sai bị từ chối; resolver không được chọn entry lạ |
| `ui/panels/EntryActionsTest` | Trọn luồng như trong app với dialog giả: thêm → màn hình Search hiện và chọn sẵn entry mới, có thông báo; tên điền sẵn từ ô tìm; "Quay lại sửa" mở lại dialog với dữ liệu cũ; thêm nghĩa giữ nguyên lần tìm đang xem; đổi tên ra khỏi kết quả thì tự tìm tên mới; đổi tên trùng → ghi đè; xóa hỏi trước, chọn dòng kế tiếp, đã lưu xuống `.dat`; chưa chọn gì thì hiện hướng dẫn |
| `ui/dialogs/EntryDialogsTest` | Dialog nhập: dòng nghĩa trống sẵn khi thêm, báo đúng lỗi (tên trống, không có nghĩa, nghĩa chứa `\|`) và đưa con trỏ tới dòng sai, cảnh báo trùng tên theo chữ đang gõ (sửa mà không đổi tên thì không báo), thêm/xóa/lên/xuống nghĩa, ô đang gõ dở vẫn được tính khi Lưu. Dialog trùng tên: trả về đúng lựa chọn + entry đích, chữ mô tả theo thêm/sửa, thoát ký tự HTML |
| `service/RandomServiceTest` | Cùng ngày luôn ra cùng slang, kể cả "mở lại app" (build lại từ file); 60 ngày ra ≥ 55 slang khác nhau; 5.000 ngày trên 10 entry thì entry nào cũng trúng ~500 lần; **thêm một slang thì một ngày chỉ có thể đổi sang chính slang mới** (365 ngày, đổi ≤ 3 ngày); sửa nghĩa giữ nguyên slang của ngày; xóa chỉ đổi ngày có slang bị xóa; random không lặp lại slang đang hiện và đi tới mọi entry; từ điển trống/1 entry; luôn dùng từ điển hiện tại (giống sau Reset) |
| `ui/panels/ResetActionTest` | Hủy thì không đổi gì và không khóa cửa sổ; dialog nhận đúng số slang/số lịch sử/file gốc; reset giữ lịch sử nếu không chọn, xóa nếu chọn; màn hình Search tìm lại trên dữ liệu gốc (slang người dùng thêm biến mất) và hiện thông báo; khóa rồi mở khóa cửa sổ; đã lưu xuống `.dat`; file gốc lỗi → báo lỗi, từ điển và lịch sử giữ nguyên, sửa file xong thì reset lại được |
| `ui/panels/RandomPanelTest` | Mở màn hình ra slang của ngày (đúng chữ, đúng nghĩa đánh số, nút "Về hôm nay" tắt); Random khác rồi quay về hôm nay; qua nửa đêm thì đổi sang slang của ngày mới; slang đang hiện bị sửa thì hiện nghĩa mới; slang random bị xóa thì chọn slang khác mà vẫn ở chế độ random; sau Reset lấy từ từ điển mới; "Tra trong từ điển"; từ điển trống; đổi ngôn ngữ thì ngày đổi theo ("Wednesday, November 11, 2026") |
| `ui/dialogs/ResetDialogTest` | Checkbox "xóa cả lịch sử" mặc định không chọn, bị tắt khi lịch sử trống; hiện số slang theo định dạng ngôn ngữ; file gốc trong jar ghi thành chữ dễ hiểu, đường dẫn ngoài được thoát ký tự HTML |
| `ui/FeatureTest` | Đúng 10 chức năng đánh số 1–10; mọi card đều có chức năng, F4–F7 là hành động; phím tắt không trùng (Ctrl+1 … Ctrl+0); mọi chức năng và nhóm có chữ ở cả hai ngôn ngữ. Không tạo cửa sổ nên chạy được trên máy không có màn hình |

Bước 10 cũng thêm vào `DictionaryStoreTest` (hoãn ghi thì chưa ghi ngay nhưng hết giờ là ghi; `close()` ghi ngay lần đang hẹn giờ 60 s) và `SearchServiceTest` (thời gian tìm không tính lúc chờ khóa).

Tổng cộng **204 test** (tới hết bước 11). Phần giao diện được kiểm tra thêm bằng cách chạy jar thật: đổi ngôn ngữ/theme, mở lại app thấy cài đặt được nhớ, chạy với 100k slang thấy màn hình loading; bước 8 chạy thử tìm-khi-gõ, Ctrl+2, gợi ý khi gõ sai, tô từ khóa ở cả theme sáng và tối; bước 9 chạy thử ghi lịch sử, Enter tìm lại, Delete xóa dòng, tắt app mở lại vẫn còn lịch sử, đổi ngôn ngữ/theme, màn hình trống; bước 10 chạy jar thật ở 7,6k và 100k (thêm, thêm trùng → thêm nghĩa / tạo bản mới, đổi tên trùng 2 mục → quay lại, xóa, theme sáng + English) rồi đọc lại `dictionary.dat` sau khi thoát; bước 11 chạy app thật (harness tự bấm, chụp màn hình): slang hôm nay, Random khác, slang 14 nghĩa dài (xuống dòng + cuộn), theme sáng + English, Tra trong từ điển, dialog Reset có tick "xóa cả lịch sử", thông báo kết quả, thanh trạng thái, Lịch sử trống sau Reset.

Test có ghi file dùng `testutil/ScratchDir` thay cho `@TempDir`: trên Windows, trình diệt virus đôi khi còn giữ file vừa ghi, khiến `@TempDir` dọn dẹp thất bại và đánh fail cả test dù test đúng.

Chạy bằng `.\mvnw.cmd test` hoặc bấm ▶ cạnh tên test trong VS Code.

---

## 6. Khái niệm Java dùng trong project (để trả lời khi bị hỏi)

| Khái niệm | Ở đâu |
|---|---|
| OOP, đóng gói, immutable | `SlangEntry` (field `final`, `withSlang`/`withMeanings`) |
| `record`, compact constructor | `SearchResult`, `HistoryItem`, `QuizQuestion` (record được serialize thì khi đọc lại compact constructor vẫn chạy) |
| `enum` | `SearchMode`, `SearchTarget`, `QuizType`, `ConflictResolution` |
| Generics, type parameter | `Trie<V>`, `BKTree<T>`, `InvertedIndex<K,V>` |
| Bounded type, wildcard | `K extends Comparable<? super K>`, `DistanceMetric<? super T>` |
| Interface, functional interface | `DistanceMetric<T>` |
| Comparable / Comparator | `SearchResult.compareTo`, `Comparator.comparingInt(Match::distance)` |
| Collections | `HashMap`, `TreeMap`, `HashSet`, `LinkedHashSet`, `ArrayList`, `ArrayDeque` |
| Java IO, NIO | `BufferedReader`, `Files.newBufferedReader`, `ObjectOutputStream` (serialize), `FileChannel.force`, `Files.move(ATOMIC_MOVE)` |
| Exception (checked) | `SlangFormatException`, `DictionaryFormatException extends IOException`, `try-with-resources` |
| Serialization | `Serializable`, `serialVersionUID`, `readResolve`, `writeObject` tùy biến, `ObjectInputFilter` (`DictionaryStore`, `HistoryStore`) |
| Đa luồng | `synchronized` (`SlangDictionary`, khối `synchronized (dictionary)` trong `SearchService`), `ExecutorService` một luồng (`DictionaryStore`, `SearchPanel`), daemon thread, `AtomicLong`, `volatile`, shutdown hook (`DictionaryStore`, `App`) |
| `AutoCloseable` | `DictionaryStore.close()` |
| `Supplier<T>` (functional interface có sẵn), method reference | `SearchService(Supplier<SlangDictionary>)`, `Math::max`, `Map::size` |
| `switch` dạng mũi tên (Java 14+) | `SearchService.searchNames` / `matchWord` |
| `Map.merge`, `Iterator.remove`, `Collections.indexOfSubList` | `SearchService` (gộp điểm, giao tập, khớp nguyên cụm) |
| Checksum | `java.util.zip.CRC32` |
| Swing, EDT | `SwingUtilities.invokeLater`, `JFrame`, `JWindow`, `JMenuBar`/`JMenu`/`JRadioButtonMenuItem`, phím tắt `KeyStroke` |
| Layout manager | `BorderLayout` (MainFrame), `CardLayout` (đổi màn hình), `GridBagLayout` (Sidebar, placeholder) |
| `SwingWorker`, `javax.swing.Timer` | `App.start` (tải dữ liệu ở luồng nền), `LoadingWindow` (hiện sau 300 ms) |
| Mẫu Observer, callback | `Messages.addListener` (đổi ngôn ngữ), `HistoryService.addChangeListener`, `Sidebar(Consumer<Feature>)`, `HistoryPanel(…, Consumer<HistoryItem> replayer)`, `DictionaryStore`/`HistoryStore.setErrorListener` |
| `enum` có field + method | `Feature` (số, nhóm, card, phím tắt), enum lồng `Feature.Section`, `Feature.Card` |
| `EnumMap`, `CopyOnWriteArrayList` | `MainFrame`, `Sidebar` / danh sách listener của `Messages` |
| Pattern matching `instanceof` (Java 16+) | `MainFrame.showFeature` (`screen instanceof FeatureScreen fs`) |
| Lớp nội, override | `Sidebar.NavToggleButton` (`paintComponent`), `updateUI()` để tính lại màu khi đổi theme |
| Java2D | `NumberBadgeIcon`, `AppIcon` (`Graphics2D`, antialiasing) |
| `JTable` + `AbstractTableModel`, `JSplitPane` | `SearchPanel` (bảng kết quả, khung chi tiết) |
| `DocumentListener` + `Timer` (debounce) | `SearchPanel` tìm khi đang gõ |
| Regex (`Pattern`, lookbehind/lookahead, `Pattern.quote`), `Highlighter` | `SearchPanel.matchRanges` (tô từ khóa trong nghĩa) |
| i18n | `ResourceBundle`, `messages_en/vi.properties`, `MessageFormat`, `Locale` |
| `Properties` | `SettingsStore` (`data/settings.properties`) |
| Varargs, type erasure | `HistoryService.remove(int... indexes)`; `HistoryStore.deserialize` kiểm tra `instanceof` từng phần tử của `List<?>` |
| `Collections.unmodifiableList`, `List.copyOf` | `HistoryService.items()` (xem không sửa), bản sao gửi cho luồng ghi |
| `java.time`, `DateTimeFormatter` | `HistoryItem.time` (`LocalDateTime`), cột Thời gian của `HistoryPanel` |
| `MessageFormat` choice | `{0,choice,1#1 search\|1<… searches}` (số ít/số nhiều tiếng Anh) |
| `MouseAdapter`, `Predicate<T>` | `HistoryPanel` (bấm đúp; hộp xác nhận thay được khi test) |
| Functional interface tự định nghĩa, mẫu Strategy | `ConflictResolver` (app truyền dialog, test truyền lambda), `EntryActions.Prompts` |
| Record lồng trong interface, `Optional<T>` | `ConflictResolver.Conflict` / `Decision`; `Optional.empty()` = người dùng hủy |
| `switch` trên enum | `DictionaryService.add/edit` (3 lựa chọn `ConflictResolution`) |
| `LinkedHashMap.putIfAbsent` | `DictionaryService.cleanMeanings` (bỏ trùng mà giữ thứ tự) |
| `ScheduledThreadPoolExecutor` (debounce) | `DictionaryStore.saveInBackground` hoãn ghi 1,5 s |
| `JDialog` modal, `JTable` sửa trực tiếp, `TableCellEditor` | `EntryEditorDialog` (bảng nghĩa), `ConflictDialog` |
| Hashing, hàm trộn bit (SplitMix64), rendezvous hashing | `RandomService.onThisDay` / `score` |
| `java.time.LocalDate.toEpochDay`, `Supplier<LocalDate>` (giả ngày khi test) | `RandomService`, `RandomPanel` |
| `Executor` (truyền vào để test chạy đồng bộ), thay object qua field `volatile` | `ResetAction`, `DictionaryService.replaceDictionary` |
| `Scrollable`, `RoundRectangle2D` + `clip` | `RandomPanel` (nội dung luôn rộng bằng khung nhìn, thẻ bo góc) |

---

## 7. Gợi ý dàn ý video (phần cấu trúc dữ liệu, 2–3 phút)

1. Sơ đồ các tầng (mục 1 và mục 3).
2. `SlangEntry` có id riêng và bất biến, vì sao lại thiết kế như vậy.
3. Bảng ở mục 4.5: mỗi chức năng tìm kiếm dùng cấu trúc nào.
4. Vẽ ví dụ Trie và BK-tree (`lil` → `lol`, `lul`, bỏ qua nhánh `lmao`).
5. Generics: `InvertedIndex<K extends Comparable<? super K>, V>`, `BKTree<T>` nhận `DistanceMetric<? super T>`.
6. Demo thời gian search với 100k slang.
7. Phần trăm tham khảo.
8. Demo trọn vẹn một chức năng.
