# Record To Lombok Design

**目标**

把项目中 `src/main/java` 和 `src/test/java` 内的所有 Java `record` 统一替换为普通 Java 类，并使用 Lombok 生成样板代码，整体风格切换为常规 JavaBean：`getXxx()/setXxx()`、无参构造 + 全参构造、可序列化/可绑定优先。

**范围**

- 覆盖生产代码中的所有 `record`
- 覆盖测试代码中的所有 `record`
- 覆盖顶层类型、嵌套类型、包级私有类型、`sealed interface` 中的嵌套 `record`
- 同步修改所有调用点，从 `xxx()` 访问器切换为 `getXxx()`

**设计**

1. 统一类型改造规则

- `dto`、`model`、`config`、`agent/v2` 中的 `record` 默认改为普通类
- 优先使用 Lombok `@Data`
- 需要框架绑定或反序列化的类型补 `@NoArgsConstructor`
- 需要兼容现有构造调用的类型补 `@AllArgsConstructor`
- 测试里的私有 `record` 改为私有静态类，保持测试语义不变

2. 保留原有自定义构造逻辑

- 原 `record` 紧凑构造器中的防御逻辑不能丢
- 例如 `ExecutionRequest` 的 `List.copyOf(...)`、`TraceRecord` 的只读 `Map` 包装，继续保留在手写构造器或 setter 内
- 对于有默认值逻辑的类型，优先保留显式构造器，避免 Lombok 自动生成覆盖掉行为

3. `sealed interface` 兼容方案

- 把嵌套 `record` 替换为嵌套静态类
- 继续显式 `implements` 原接口
- 同步更新 `permits` 列表，保证 Java 17 编译通过
- `instanceof` 分支和模式匹配逻辑保持不变，只替换字段访问方式

4. 迁移顺序

- 第一批：`config`、`dto`、普通 `model`
- 第二批：`agent/v2` 中的顶层值对象与工具参数对象
- 第三批：`sealed interface`、内部私有类型、测试辅助类型
- 每批完成后先跑对应测试，再继续下一批

**错误处理**

- 不改变 JSON 字段结构，不主动调整序列化字段名
- 不改变 Spring `@ConfigurationProperties` 前缀与绑定路径
- 发现某个类型依赖 `record` 的不可变语义时，优先通过手写保护逻辑补齐，而不是回退为新的风格分支

**测试策略**

- 先补或调整最小回归测试，再动生产代码
- 重点覆盖：
  - `ConfigurationProperties` 绑定
  - 控制器请求/响应序列化
  - `agent/v2` 状态对象的构造与访问
  - 包含 `equals/hashCode` 断言的测试
  - 原紧凑构造器中的集合防御逻辑
- 分批执行测试，最后跑全量 `mvn test`

**风险**

- 全量切到 JavaBean 后，值对象从隐式不可变变为可变，误修改风险会上升
- 访问器命名切换到 `getXxx()` 后，所有调用点都需要同步替换，否则会有大量编译错误
- `sealed interface` 的嵌套类型如果改造不完整，会直接编译失败
- 构造绑定改为 setter 绑定后，如果缺少无参构造器，Spring 或 Jackson 会在启动或测试阶段失败
