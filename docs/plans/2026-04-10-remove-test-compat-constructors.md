# Remove Test-Compat Constructors Plan

## Goal

删除生产代码中为了兼容测试或过渡调用方而保留的便利构造器，只保留真实生产依赖所需的最终构造器，并把测试改成显式适配生产构造路径。

## Phase 1: Inventory And Guardrails

1. 扫描 `agent`、`service`、`config` 下的多构造器类，确认哪些属于依赖注入便利层，哪些属于值对象语义重载。
2. 形成待清理清单，优先覆盖：
   - `SupervisorOrchestrator`
   - `TaskApplicationService`
   - 各类 `*ContextFactory`
   - 其他会在构造器内部 `new` 默认依赖的生产类
3. 为关键热点补充或调整测试，确保后续删除构造器后能通过测试失败定位缺口。

## Phase 2: Remove Orchestrator And Service Compatibility Constructors

1. 先处理 `SupervisorOrchestrator` 这类明确的过渡构造器。
2. 再处理 `TaskApplicationService` 的多重重载，收口到唯一最终构造器。
3. 同步修正受影响测试，改为显式传入 mock、stub 或测试 helper。
4. 运行相关定向测试，确认服务入口行为未变。

## Phase 3: Flatten Context Factory Construction

1. 逐步清理 `ReactAgentContextFactory`、`PlanningAgentContextFactory`、`SupervisorContextFactory` 及各 worker/reflexion factory 的便利构造器。
2. 删除内部自行 new mapper / document service / builder 的路径。
3. 统一由测试或 Spring 配置显式提供完整依赖。
4. 运行 agent/context 相关测试，确保 prompt 组装、记忆压缩和执行上下文行为不变。

## Phase 4: Sweep Remaining Compatibility Constructors

1. 处理剩余生产类中的测试便利构造器，例如压缩器、agent implementation、策略类。
2. 审核 `config` 下属性类和纯值对象，确认仅删除明显的测试兼容层，不误伤语义型便捷构造器。
3. 全局搜索“过渡构造器”“兼容旧测试”等残留文案并清理。

## Phase 5: Verification

1. 运行针对性测试集，覆盖：
   - orchestrator/service
   - context factory / runtime
   - 配置与长期记忆相关测试
2. 运行全量 `mvn test`
3. 复查 git diff，确认没有引入行为变更，只是收紧依赖边界并更新测试装配。

## Risks And Watchpoints

- 删除构造器会造成大量测试同时编译失败，需要按类群推进，避免一次性失控。
- 某些测试可能依赖生产类内部默认值；这些默认值若属于真实业务语义，应转移到测试 helper，而不是简单硬编码到每个测试。
- Spring 真正使用的构造器必须保留，不要把运行时入口和测试便利层混删。
