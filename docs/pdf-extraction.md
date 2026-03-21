# PDF 提取逻辑说明

这份文档不只是列代码位置，而是按“为什么这样做”来解释当前 PDF 提取实现。

如果只看结果，当前流程是：

1. 识别上传文件是不是 PDF
2. 用 PDFBox 把 PDF 里的文本行和坐标提出来
3. 判断这页更像目录、表格、双栏，还是普通正文
4. 按对应规则重排成最终纯文本
5. 如果几乎提不到字，就认为它更像扫描件，提示 OCR 未启用

对应入口代码：

- [`KnowledgeDocumentParser.java`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/KnowledgeDocumentParser.java)
- [`PdfKnowledgeExtractor.java`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java)
- [`PdfTextExtractor.java`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfTextExtractor.java)
- [`PdfTextLine.java`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfTextLine.java)

## 1. 为什么 PDF 不能像 `.txt` 一样直接读

`txt/md` 文件本质上就是线性文本，按 UTF-8 解码就能得到阅读顺序。

PDF 不是这样。PDF 更像一堆“画字指令”：

- 在 `(72, 720)` 画一个字符串
- 在 `(320, 720)` 再画一个字符串
- 在 `(72, 690)` 再画一个字符串

它天然不保证这些指令就是人类阅读顺序。

这也是为什么入口类 [`KnowledgeDocumentParser.java:25-38`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/KnowledgeDocumentParser.java:25) 里：

- `md/txt` 直接 `new String(file.getBytes(), UTF_8)`
- `pdf` 必须走 `pdfKnowledgeExtractor.extract(file.getBytes())`

核心原因不是“PDF 更复杂”这种抽象说法，而是：

- 文本内容和版面位置是分开的
- 阅读顺序需要我们自己重建

## 2. 当前实现到底抽出了什么数据

当前版本没有做很重的块分析，只提取了“文本行 + 几何位置”。

数据结构在 [`PdfTextLine.java:3-19`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfTextLine.java:3)：

- `pageIndex`：第几页
- `pageWidth`：页宽
- `x`：这行起始横坐标
- `y`：这行纵坐标
- `width`：这行大致宽度
- `text`：这行文字

这套信息的意图是：

- `x / width / pageWidth` 用来判断双栏和表格列
- `y` 用来决定同一页里的上下顺序
- `text` 用来做目录和 OCR 的启发式判断

也就是说，这一版不是“理解 PDF”，而是“提取足够做启发式判断的最小版面信息”。

## 3. 文本行是怎么提出来的

实现类在 [`PdfTextExtractor.java:13-60`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfTextExtractor.java:13)。

### 3.1 先保留 PDFBox 的原始输出顺序

[`PdfTextExtractor.java:20-22`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfTextExtractor.java:20)

```java
setSortByPosition(false);
```

这里故意没有让 PDFBox 先帮我们“排好顺序”。

原因是：

- 如果 PDFBox 先做了一层排序，我们后面很难区分“是原 PDF 顺序正确”还是“PDFBox 帮我们修正了”
- 当前需求里双栏顺序是我们自己要负责的逻辑，所以先保留更原始的结果更合适

### 3.2 提取一行文本的几何信息

[`PdfTextExtractor.java:42-58`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfTextExtractor.java:42)

`writeString(...)` 每次会拿到一段文本和它对应的字符位置列表 `List<TextPosition>`。

当前代码做了三件事：

1. 取第一个字符的位置当作整行起点
2. 取最后一个字符的位置估算整行宽度
3. 记录当前页页宽

关键代码：

```java
TextPosition first = textPositions.get(0);
TextPosition last = textPositions.get(textPositions.size() - 1);
float width = Math.max(0, (last.getXDirAdj() + last.getWidthDirAdj()) - first.getXDirAdj());
```

这不是精确排版引擎，只是一个够用的近似：

- 我们并不需要每个字的边界
- 我们只需要知道“这行大概在页面左边还是右边”“这一行有多宽”“两行是不是同一行”

## 4. 主流程怎么串起来

主流程在 [`PdfKnowledgeExtractor.extract():30-61`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:30)。

把它翻成更直白的话，就是：

### Step 1: 先提纯有效文本行

```java
List<PdfTextLine> extracted = textExtractor.extract(bytes).stream()
        .filter(line -> !line.isBlank())
        .toList();
```

空行先丢掉，不让它干扰后面的统计。

### Step 2: 先做“是不是扫描件”的粗判断

[`PdfKnowledgeExtractor.java:34-35`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:34)

如果整份 PDF 可见字符太少，就直接失败：

```java
if (visibleCharacterCount(extracted) < OCR_MIN_VISIBLE_CHARACTERS) {
    throw new IllegalArgumentException("PDF requires OCR, but OCR is not enabled yet");
}
```

原因是：

- 对扫描件来说，后面的双栏、目录、表格逻辑都没有意义
- 因为根本没抽出可分析的文本

### Step 3: 按页分组

[`PdfKnowledgeExtractor.java:38-43`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:38)

后面的噪声过滤和版面判断都是“页级”的，不是整份文档一起算。

这是有意为之，因为真实 PDF 常常是混合布局：

- 第 1 页封面
- 第 2 页目录
- 第 3-10 页双栏
- 第 11 页单栏附录

如果只做整份文档级判断，很容易互相污染。

### Step 4: 逐页清洗和格式化

[`PdfKnowledgeExtractor.java:45-55`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:45)

每页都走：

1. 看是不是噪声页
2. 不是噪声页的话，再判断是表格、双栏还是普通页
3. 最终输出这一页的文本

### Step 5: 拼成整份文档

[`PdfKnowledgeExtractor.java:57-60`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:57)

各页之间用空行拼起来。

如果最后还是空白，仍然按“需要 OCR”失败。这样可以覆盖另一种情况：

- PDF 有少量无意义字符
- 但清洗后没有正文

## 5. OCR 失败判定的原理

这一版没有接 OCR 服务，只做“何时应该让 OCR 接管”的判断。

相关代码：

- 阈值定义：[`PdfKnowledgeExtractor.java:15-18`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:15)
- 字符统计：[`PdfKnowledgeExtractor.java:64-69`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:64)

当前规则很朴素：

- 把每行文本里的空白去掉
- 累加总字符数
- 总数小于 `10`，就认为“文字少到不足以做正常版面分析”

为什么是这种简单规则？

- 这版目标不是准确识别所有扫描件
- 而是先把“明显没有文本层的 PDF”挡出来

它不是 OCR 检测模型，只是一个低成本兜底。

## 6. 目录页为什么能被过滤掉

实现代码在 [`PdfKnowledgeExtractor.isNoisePage():72-79`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:72)。

当前规则是双条件：

1. 页面里出现 `contents` 或 `目录`
2. 同时有行包含 `....`

代码：

```java
return (pageText.contains("contents") || pageText.contains("目录")) && dottedLines > 0;
```

背后的思路不是“目录一定包含 contents”，而是：

- 单看 `contents` 太宽松，正文里也可能出现
- 单看 `....` 也太宽松，别的地方也可能有点线
- 两个信号同时出现时，目录页置信度明显更高

### 一个具体例子

如果一页抽出来是：

```text
Contents
Chapter One........1
Chapter Two........5
```

那它会被删掉，因为：

- 命中了 `contents`
- 有多行目录样式的 `....`

但如果正文里出现：

```text
This section contains examples.
```

它不会被删，因为没有点线目录项。

## 7. 表格为什么能保留结构

这版不追求真正“识别表格对象”，而是追求“不要把表格内容压扁成一坨字”。

### 7.1 先按 y 把文本行聚成行

代码在 [`PdfKnowledgeExtractor.groupRows():153-172`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:153)。

如果两段文本的 `y` 差值足够小，就认为它们属于同一行：

```java
if (Math.abs(currentRow.get(0).y() - line.y()) <= ROW_TOLERANCE) {
    currentRow.add(line);
}
```

这里的 `ROW_TOLERANCE = 8.0f` 不是随便写的数学常数，它的含义是：

- 同一行不同列在 PDF 里，y 往往不会完全一样
- 但会非常接近

### 7.2 再看是不是“连续多行多列”

代码在 [`PdfKnowledgeExtractor.looksLikeTable():92-97`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:92)。

当前条件是：

- 至少两行
- 每行至少三列

这是为了压低误判率。

因为普通正文也可能偶尔出现两段并排短文本，但连续多行三列的概率就小很多。

### 7.3 输出时按列顺序拼成 `|` 分隔

代码在 [`PdfKnowledgeExtractor.formatTable():99-105`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:99)。

比如原始行可能是：

```text
x=72   Name
x=220  Score
x=340  Rank
```

会被格式化成：

```text
Name | Score | Rank
```

下一行：

```text
Alice | 95 | 1
```

这就是“保结构”的含义。

不是把 PDF 变成真正二维表格对象，而是尽量保留列关系。

## 8. 双栏为什么能重排

双栏的核心问题是：

- PDF 里的写字顺序不一定等于阅读顺序
- 人类阅读顺序是“先完整读左列，再完整读右列”

### 8.1 先按页面中线把文本分成左右两组

代码在 [`PdfKnowledgeExtractor.looksLikeDoubleColumn():108-127`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:108)。

它用：

```java
float midpoint = pageWidth / 2.0f;
```

然后基于 `centerX()` 判断一行更靠左还是更靠右：

```java
line.centerX() < midpoint
```

这不是完美版面分析，但对常见“左右两栏、中央留白明显”的 PDF 很有效。

### 8.2 为什么还要检查中间空白

关键代码：

```java
float leftMax = left.stream().map(PdfTextLine::x).max(Float::compare).orElse(0.0f);
float rightMin = right.stream().map(PdfTextLine::x).min(Float::compare).orElse(pageWidth);
return rightMin - leftMax > COLUMN_GAP;
```

这里不是只看“左右两边都有文本”，还要看中间有没有足够的空白。

原因是：

- 普通单栏正文里，左边和右边也可能都有短行
- 如果没有明显中缝，就不应该贸然按双栏重排

也就是说，`COLUMN_GAP` 的作用是降低误判。

### 8.3 重排时为什么是“左列全部，再右列全部”

代码在 [`PdfKnowledgeExtractor.formatDoubleColumn():129-143`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:129)。

这段逻辑本质上是：

1. 左列内部按上到下排序
2. 右列内部按上到下排序
3. 最终拼接 `left + right`

举个具体例子。

假设抽取后是这四行：

```text
(72, 720)  Left Column A
(320, 720) Right Column A
(72, 690)  Left Column B
(320, 690) Right Column B
```

如果直接按原顺序输出，可能得到：

```text
Left Column A
Right Column A
Left Column B
Right Column B
```

这对人类阅读是错的。

当前实现会重排成：

```text
Left Column A
Left Column B
Right Column A
Right Column B
```

这才是符合阅读顺序的版本。

## 9. 为什么先判表格，再判双栏

这个顺序在 [`PdfKnowledgeExtractor.formatPage():81-89`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:81)。

看起来只是一个 `if` 顺序，实际上很关键。

原因是表格和双栏在几何上有相似性：

- 都可能表现为页面左边有文本、右边也有文本
- 如果先按双栏处理，表格每一行的多列会被误拆成左列和右列

所以当前顺序是：

- 先问“这是不是表格”
- 不是表格，再问“这是不是双栏”

这是为了优先保护行内列关系。

## 10. 一个完整的走读例子

下面用一个简化例子把整条链路串起来。

### 输入 PDF

第 1 页：

```text
Contents
Chapter One........1
Chapter Two........5
```

第 2 页：

```text
(72, 720)  Left A
(320, 720) Right A
(72, 690)  Left B
(320, 690) Right B
```

### Step 1: `PdfTextExtractor` 输出 `PdfTextLine`

得到类似：

```text
page=0, x=72,  y=720, text=Contents
page=0, x=72,  y=690, text=Chapter One........1
page=1, x=72,  y=720, text=Left A
page=1, x=320, y=720, text=Right A
page=1, x=72,  y=690, text=Left B
page=1, x=320, y=690, text=Right B
```

### Step 2: `PdfKnowledgeExtractor` 按页分组

第 1 页一组，第 2 页一组。

### Step 3: 第 1 页命中目录页规则

因为：

- 含 `Contents`
- 含 `....`

所以第 1 页整页丢弃。

### Step 4: 第 2 页命中双栏规则

因为：

- 左右两边都有多行
- 中间留白明显

所以按“左列全部，再右列全部”输出。

### 最终结果

```text
Left A
Left B
Right A
Right B
```

这就是当前实现从“PDF 原始画字指令”走到“适合知识库切分的纯文本”的完整过程。

## 11. 当前实现的边界

理解原理时，边界同样重要。当前实现不是一个通用 PDF 理解器。

它能做的是：

- 文本 PDF 抽取
- 简单双栏重排
- 高置信度目录页过滤
- 简单表格保形
- 扫描件失败提示

它还不能做的是：

- 真正 OCR
- 复杂封面识别
- 页眉页脚去重
- 跨页大表格
- 图表、公式、图片语义理解

所以你可以把它理解成：

“一个为知识库导入服务的、轻量启发式 PDF 文本整理器”

而不是：

“一个通用、高精度的文档版面分析系统”

## 12. 代码与测试怎么对应

如果你想从行为直接回看实现，可以按这组映射看。

### 基础 PDF 提取

- 测试：[`KnowledgeDocumentParserTest.java:45`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java:45)
- 主逻辑：[`KnowledgeDocumentParser.java:35`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/KnowledgeDocumentParser.java:35)

### 双栏顺序

- 测试：[`KnowledgeDocumentParserTest.java:76`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java:76)
- 判定：[`PdfKnowledgeExtractor.java:108`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:108)
- 重排：[`PdfKnowledgeExtractor.java:129`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:129)

### 目录清洗

- 测试：[`KnowledgeDocumentParserTest.java:93`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java:93)
- 判定：[`PdfKnowledgeExtractor.java:72`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:72)

### 表格保形

- 测试：[`KnowledgeDocumentParserTest.java:110`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java:110)
- 行分组：[`PdfKnowledgeExtractor.java:153`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:153)
- 输出：[`PdfKnowledgeExtractor.java:99`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:99)

### OCR 失败

- 测试：[`KnowledgeDocumentParserTest.java:126`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java:126)
- 判定：[`PdfKnowledgeExtractor.java:34`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:34)

## 13. 如果下一版继续增强，最应该先改哪里

如果你后面还想继续演进，这几个点最自然：

1. 在 [`PdfKnowledgeExtractor.extract()`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:30) 里把 OCR 失败分支接到真实 OCR 组件
2. 在 [`PdfKnowledgeExtractor.isNoisePage()`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:72) 附近增加封面、页眉、页脚规则
3. 在 [`PdfKnowledgeExtractor.looksLikeTable()`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:92) 和 [`formatTable()`](/Users/eumendies/code/java/learn/agent-editor/.worktrees/pdf-knowledge-parser/src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java:99) 上增强复杂表格支持

如果你愿意，我下一步可以继续把这份文档再补一版“带示意图的版本”，比如用 ASCII 图把双栏和表格的坐标变化画出来。  
