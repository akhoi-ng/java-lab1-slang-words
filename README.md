# Slang Dictionary (DAMH#1, CSC13002)

Đồ án #1 môn Lập trình ứng dụng Java: từ điển slang word viết bằng **Java 17 + Swing** (giao diện FlatLaf), build bằng Maven.

- MSSV: **24127430**
- Video demo: xem file `24127430_TuChamDiem.txt`
- Tài liệu giải thích chi tiết: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) (kiến trúc, cấu trúc dữ liệu, số đo),
  [`docs/PLAN.md`](docs/PLAN.md) (kế hoạch, tiến độ), [`AUDIT_REPORT.md`](AUDIT_REPORT.md) (rà soát lỗi và trạng thái sửa)

---

## 1. Chạy chương trình

Cần **Java 17 trở lên** (JRE hoặc JDK).

```
java -jar slang-dictionary.jar
```

hoặc double-click file `slang-dictionary.jar`.

- Lần chạy đầu, app đọc `slang.txt` (đã nhúng sẵn trong jar), dựng các cấu trúc dữ liệu rồi lưu thành
  `data/dictionary.dat` **cạnh file jar**. Các lần chạy sau đọc thẳng `dictionary.dat`, không phải dựng (index) lại.
- Mọi thay đổi (thêm, sửa, xóa, reset) được lưu lại vào `dictionary.dat`; lịch sử tìm kiếm lưu ở `data/history.dat`,
  ngôn ngữ/giao diện lưu ở `data/settings.properties`.
- Nếu thư mục chứa jar không ghi được, dữ liệu được lưu ở `<thư mục người dùng>/.slang-dictionary` (app báo vị trí).
- Muốn dùng file dữ liệu khác: đặt file vào `data/slang.txt` cạnh jar, xóa `data/dictionary.dat`, rồi mở lại app.
- Chỉ mở được một cửa sổ app cho mỗi thư mục `data/` (tránh hai cửa sổ ghi đè dữ liệu của nhau).

## 2. Chức năng

Chọn chức năng bằng **menu "Chức năng"**, **thanh bên trái** hoặc phím tắt **Ctrl+1 … Ctrl+0**.

| # | Chức năng | Ghi chú |
|---|---|---|
| 1 | Tìm theo slang word | 4 kiểu khớp: chính xác, tiền tố (mặc định, tìm ngay khi gõ), chuỗi con, gần đúng (gõ sai vẫn ra) |
| 2 | Tìm theo definition | Hiện mọi slang có definition chứa từ khóa; tô sáng từ khóa trong nghĩa; nhiều từ khóa thì xếp hạng |
| 3 | Lịch sử tìm kiếm | Lưu qua các lần chạy; bấm đúp để tìm lại; xóa từng dòng hoặc xóa hết |
| 4 | Thêm slang | Trùng tên thì hỏi: **ghi đè**, **thêm nghĩa** vào slang cũ, hoặc **tạo bản mới (duplicate)** |
| 5 | Sửa slang | Sửa tên và từng nghĩa; đổi sang tên đã có thì hỏi lại như khi thêm |
| 6 | Xóa slang | Có hộp xác nhận, nút mặc định là "Hủy" |
| 7 | Khôi phục dữ liệu gốc | Đưa về danh sách slang gốc, có tùy chọn xóa luôn lịch sử |
| 8 | Slang hôm nay (random) | Cả ngày cùng một slang ("On this day") + nút random slang khác |
| 9 | Đố vui: slang → nghĩa | 1 slang, 4 nghĩa để chọn (phím 1–4); điểm, chuỗi đúng, lượt 10/20 câu có bảng tổng kết |
| 10 | Đố vui: nghĩa → slang | 1 definition, 4 slang để chọn; đáp án nhiễu không bao giờ cũng đúng |

Thêm: đổi ngôn ngữ Việt/Anh và giao diện sáng/tối ngay khi đang chạy (Ctrl+Shift+L), thoát bằng Ctrl+Q.

## 3. Cấu trúc dữ liệu

Không chỉ dùng List: mỗi kiểu tìm có một cấu trúc riêng để không phải duyệt cả từ điển.

| Cấu trúc | Dùng cho | Độ phức tạp |
|---|---|---|
| `HashMap<Integer, SlangEntry>` + `HashMap<String, List<Integer>>` | lấy entry theo id, tra tên chính xác (không phân biệt hoa thường), slang trùng tên | O(1) |
| `Trie<V>` (tự viết, generic, con lưu bằng `TreeMap`) | tìm theo tiền tố tên slang, kết quả theo thứ tự từ điển | O(độ dài tiền tố + số kết quả) |
| `InvertedIndex<K, V>` (tự viết, trên `TreeMap<K, Set<V>>`) | từ → các slang có definition chứa từ đó; tiền tố từ bằng `subMap` | O(log V) + số kết quả |
| `BKTree<T>` + `Levenshtein` (tự viết, generic) | tìm gần đúng tên slang và từ trong definition (gõ sai 1–2 ký tự) | bỏ qua phần lớn cây nhờ bất đẳng thức tam giác |
| `ArrayList<Integer>` (danh sách id) | random 1 slang và chọn đáp án nhiễu cho đố vui | O(1) |

Toàn bộ từ điển kèm index được serialize thành `data/dictionary.dat` (header magic + phiên bản + CRC32, ghi file tạm
rồi đổi tên, ghi ở luồng nền). File hỏng được giữ lại thành `dictionary.dat.bad` và app dựng lại từ `slang.txt`.

**Hiệu năng với 100.000 slang** (lần gọi đầu tiên, máy dev; chi tiết ở `docs/ARCHITECTURE.md` mục 4.10):
tìm theo slang 1–70 ms, tìm theo definition 1–220 ms (trường hợp tệ nhất: chữ `e` khớp 88% từ điển),
thêm/sửa/xóa vài ms, reset 0,45–0,71 s, đố vui dưới 1 ms. Mọi chức năng đều dưới 1 giây.

Thử với 100k slang:

```
java -cp slang-dictionary.jar lab01.slangdict._24127430.tools.BigDataGenerator
```

Lệnh trên sinh `data/slang-100k.txt`. Copy file đó thành `data/slang.txt`, xóa `data/dictionary.dat`, rồi mở app.
Xóa hai file đó để quay về dữ liệu gốc.

## 4. Build từ mã nguồn

Không cần cài Maven (đã có Maven Wrapper):

```
.\mvnw.cmd package        (Windows)
./mvnw package            (Linux/macOS)
```

Lệnh này biên dịch, chạy 262 unit test (JUnit 5) và tạo `target/slang-dictionary.jar` (đã gộp FlatLaf, chạy độc lập).

Cấu trúc mã nguồn (package `lab01.slangdict._24127430`):

| Package | Nội dung |
|---|---|
| `model` | `SlangEntry`, `SearchResult`, `HistoryItem`, `QuizQuestion`, ... |
| `ds` | `Trie`, `InvertedIndex`, `BKTree`, `Levenshtein` (generic, dùng lại được) |
| `core` | `SlangDictionary` (từ điển + mọi index, luôn đồng bộ), `Tokenizer` |
| `io` | đọc `slang.txt`, lưu `.dat`, lịch sử, cấu hình, đường dẫn thư mục data |
| `service` | logic từng chức năng: tìm kiếm, thêm/sửa/xóa, lịch sử, random, đố vui |
| `ui` | giao diện Swing: cửa sổ chính, menu, thanh bên, các màn hình và hộp thoại |
| `tools` | `BigDataGenerator` sinh dữ liệu 100k để đo tốc độ |

`tools/*.py` là hai script Python đã dùng để sửa lỗi và kiểm tra `slang.txt`, không thuộc app.

## 5. Thư viện sử dụng

| Thư viện | Phiên bản | Giấy phép | Dùng để |
|---|---|---|---|
| [FlatLaf](https://www.formdev.com/flatlaf/) | 3.8 | Apache 2.0 | Look & Feel cho Swing (sáng/tối); được gộp vào jar |
| [JUnit 5](https://junit.org/junit5/) | 5.14 | EPL 2.0 | Unit test, chỉ dùng lúc build, không có trong jar |

## 6. Phần tham khảo

**Tổng phần trăm tham khảo: 15%** (tính trên mã nguồn Java trong `src/main/java`).

Mọi chỗ tham khảo đều được đánh dấu trong code, tìm bằng từ khóa `THAM KHẢO`:

- `// [THAM KHẢO - CẢ FILE] ...` ở đầu file: thuật toán hoặc khung chính của cả file lấy từ nguồn, ghi rõ phần
  lấy từ nguồn và phần tự viết thêm.
- `// [THAM KHẢO] ...` … `// [HẾT THAM KHẢO]`: một đoạn trong file.

| File / đoạn | Nguồn tham khảo |
|---|---|
| `ds/Levenshtein.java` | Wikipedia, "Levenshtein distance" (quy hoạch động hai hàng) |
| `ds/BKTree.java` | Burkhard & Keller (1973); Wikipedia "BK-tree"; N. Johnson, "Damn Cool Algorithms, Part 1: BK-Trees" |
| `ds/Trie.java` | Sedgewick & Wayne, *Algorithms* 4th ed., mục 5.2 (`TrieST`) |
| `ds/InvertedIndex.java` | Manning, Raghavan, Schütze, *Introduction to Information Retrieval*, chương 1 |
| `io/InstanceLock.java` | StackOverflow "How to implement a single instance Java application"; Javadoc `FileChannel.tryLock` |
| `io/DictionaryStore.java` (3 đoạn) | Oracle "Serialization Filtering"; Javadoc `Thread` (stack size), `Files.move` (ATOMIC_MOVE), `FileChannel.force` |
| `io/AppPaths.java` (`codeLocation`) | StackOverflow "How to get the path of a running JAR file?" |
| `io/SettingsStore.java` (`save`, `load`) | Oracle Java Tutorials, "Properties" |
| `i18n/Messages.java` (`format`, `loadBundle`) | Oracle Java Tutorials, ResourceBundle và MessageFormat |
| `ui/ThemeManager.java` | Tài liệu FlatLaf ("How to use", "Customizing") |
| `ui/MainFrame.java` (menu, CardLayout) | Oracle Java Tutorials, "How to Use Menus", "How to Use CardLayout" |
| `ui/LoadingWindow.java` | Oracle Java Tutorials, "How to Use Progress Bars", "How to Use Swing Timers" |
| `App.java` (SwingWorker tải dữ liệu) | Oracle Java Tutorials, "Worker Threads and SwingWorker" |
| `ui/panels/SearchPanel.java` (3 đoạn), `HistoryPanel.java`, `QuizPanel.java` | Oracle Java Tutorials: Document Listener, Text Component Features (Highlighter), How to Use Tables, Key Bindings |
| `ui/panels/WidthTrackingPanel.java` | Oracle Java Tutorials, "How to Use Scroll Panes" (Scrollable) |
| `ui/panels/RoundedCard.java` | Oracle Java Tutorials, "2D Graphics" |
| `pom.xml` (maven-shade-plugin) | Tài liệu maven-shade-plugin, "Executable JAR" |

**Cách tính:** đếm dòng code (không tính dòng trống và chú thích) nằm trong các file/đoạn đánh dấu ở trên, chia cho
tổng số dòng code của `src/main/java`: 928 / 7.101 ≈ 13%. Làm tròn lên **15%** để tính cả phần cấu hình `pom.xml`
và các chỗ dùng API theo Javadoc không đánh dấu từng dòng. Phần còn lại (logic từ điển, tìm kiếm và xếp hạng, lưu
trữ, thêm/sửa/xóa, lịch sử, random, đố vui, giao diện các màn hình, unit test) là phần tự làm.

Trong quá trình làm có dùng trợ lý AI để hỗ trợ viết và giải thích code, theo quy định được giảng viên cho phép.
