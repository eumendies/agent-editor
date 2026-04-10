# Remove Test-Compat Constructors Design

## Context

当前代码中存在一批“为了让测试更容易 new 生产类”而保留的便利构造器。这些构造器主要出现在 Spring bean、service、orchestrator、runtime context factory 等生产入口类里，典型特征是：

- 少参数重载内部偷偷补默认依赖
- 保留旧入参形态，内部再适配到新依赖模型
- 注释直接写明“兼容旧测试/调用方的过渡构造器”

这会把测试便利性反向写进生产代码，使生产对象的依赖边界变得模糊，也让未来重构必须同时顾及一批并不存在于真实运行链路里的“假构造路径”。

## Goal

删除生产代码中为了兼容测试或过渡调用方而存在的便利构造器，只保留真实生产依赖所需的最终构造器，让测试显式构造依赖并适配生产代码。

## Non-Goals

- 不重做 Spring 装配方式
- 不修改运行时行为或业务语义
- 不清扫所有多构造器值对象；只有当便捷构造器明显只是测试便利层时才处理
- 不引入新的 builder/fixture 框架

## Scope

本次重点处理以下几类生产类：

1. orchestrator / runtime 入口
   - `SupervisorOrchestrator`
   - 其他存在“旧依赖 -> 新依赖”适配构造器的编排类

2. service / application service
   - `TaskApplicationService`
   - 其他通过重载构造器补 `null`、默认 assembler、默认 repository 的服务类

3. context factory / agent factory
   - `ReactAgentContextFactory`
   - `PlanningAgentContextFactory`
   - `SupervisorContextFactory`
   - `ResearcherAgentContextFactory`
   - `GroundedWriterAgentContextFactory`
   - `EvidenceReviewerAgentContextFactory`
   - `MemoryAgentContextFactory`
   - `ReflexionActorContextFactory`
   - `ReflexionCriticContextFactory`
   - 其他同模式类

4. 明显只是为测试或手动 `new` 保底的配置/实现类
   - 如 `ModelBasedMemoryCompressor`
   - 如 `PlanningAgentImpl`
   - 仅在便利构造器不表达独立领域语义时处理

## Out Of Scope

以下类型默认不作为这轮主目标，除非检查后能确认它们的重载构造器同样只是测试兼容层：

- `ExecutionRequest`
- `TaskRequest`
- `ExecutionResult`
- `TaskResult`
- `ChatTranscriptMemory`
- `ToolContext`
- 其他纯值对象、DTO、异常类

这些类的少参数构造器更可能是在表达领域默认值或简化真实调用语义，而不是为了掩盖依赖注入。

## Design Rules

### 1. 生产 bean 只保留一个最终构造器

对 service、orchestrator、context factory、agent implementation、策略类，保留唯一一个能够完整显式表达依赖的构造器。

禁止继续存在以下模式：

- `new X()` 后内部自己 new 依赖
- `new X(a)` 内部补 `new Mapper()`、`new Properties()`、`new Service()`
- `new X(oldPolicy)` 内部转成 `newPolicy`
- 通过多个重载构造器传 `null` 跳过可选依赖

### 2. 测试改成适配生产构造器

测试如果需要构造复杂对象，应在测试侧完成：

- 显式传入 mock / fake / helper 依赖
- 在测试文件中抽出小型 factory/helper
- 若多个测试共享同一装配方式，提炼测试专用 fixture 方法

不允许继续通过生产代码里的重载构造器隐藏依赖。

### 3. 值对象按语义判断

如果某个多构造器类只是承载数据，并且少参数构造器表达的是稳定、真实的语义简化，例如“只给最终消息”或“只给基础任务字段”，则可以保留。

如果它的便利构造器本质是在模拟依赖注入或掩盖非空协作对象，则删除。

## Initial Targets

### `SupervisorOrchestrator`

当前存在两条明确的过渡构造器：

- 接收 `DocumentToolAccessPolicy` + `ExecutionToolAccessPolicy`，但只转调最终构造器
- 接收 `DocumentToolAccessPolicy`，内部再 new `ExecutionToolAccessPolicy`

这两条都应删除，只保留直接依赖 `ExecutionToolAccessPolicy` 的最终构造器。

### `TaskApplicationService`

当前保留多组重载构造器，用于：

- 从 `ObjectProvider` 取可选依赖
- 传 `null` 跳过长期记忆依赖
- 内部 new `UserProfilePromptAssembler`
- 为测试减少构造参数

目标是只保留 Spring 真正使用的最终构造器。测试显式提供 `LongTermMemoryWriteService`、`LongTermMemoryRepository`、`UserProfilePromptAssembler` 等依赖；若某些依赖在业务上允许缺省，则通过 mock/stub 明确表达，而不是靠不同构造器分叉。

### `*ContextFactory`

这组类常见模式是：

- 单参构造器只收 `MemoryCompressor`
- 双参构造器只收 mapper + compressor
- 三参构造器才是真正完整依赖

这里应统一改成只保留完整构造器，避免生产类自己 new `ExecutionMemoryChatMessageMapper`、`StructuredDocumentService`、`MarkdownSectionTreeBuilder` 等协作对象。

## Implementation Strategy

1. 先用搜索结果建立“必须删的便利构造器清单”
2. 对每一组类先补或调整测试，使测试显式构造完整依赖
3. 再删除对应生产构造器
4. 每完成一组就运行该组相关测试，最后跑全量 `mvn test`

## Risks

### 测试编译面大

删构造器后，大量直接 `new` 生产类的测试会同时失败。这是预期现象，需要按类群批量修正。

### Spring 装配和测试装配混在一起

某些 `@Autowired` 构造器可能同时承担了“给 Spring 用”和“给测试偷懒用”的双重职责。处理时要保留真正生产装配所需的最终构造器，不要误删运行路径。

### 值对象误伤

如果把纯值对象的语义型便捷构造器也一并删掉，会徒增调用噪音且不一定有收益。因此实现中要明确区分“依赖注入便利层”和“数据语义简写”。

## Verification

- 目标类不再存在测试兼容型便利构造器
- 相关测试改为显式构造真实依赖
- 全量 `mvn test` 通过
- 全局搜索不再出现“过渡构造器”“兼容旧测试”等残留注释
