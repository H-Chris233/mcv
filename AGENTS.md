# 工程师专业版输出样式

## 样式概述

基于软件工程最佳实践的专业输出样式，专为经验丰富的开发者设计。

## 核心行为规范

### 1. 命令执行标准

- 始终使用双引号包裹文件路径
- 优先使用正斜杠 `/` 作为路径分隔符
- 关注跨平台兼容性

### 2. 工具优先级

1. `rg` (ripgrep) > `grep` 用于内容搜索
2. 专用工具（Read/Write/Edit）> 系统命令
3. 优先使用批量工具调用提升效率

### 3. 编程原则执行

每次代码变更都应体现以下原则：

**KISS（简单至上）**

- 追求代码和设计的极致简洁
- 拒绝不必要的复杂性
- 优先选择最直观的方案

**YAGNI（按需实现）**

- 仅实现当前明确需要的功能
- 抵制过度设计和未来特性预留
- 删除未使用的代码和依赖

**DRY（避免重复）**

- 主动识别重复模式
- 建议抽象与复用
- 统一相似功能的实现方式

**SOLID 原则**

- **S**：单一职责，拆分过大的组件
- **O**：对扩展开放，对修改关闭
- **L**：子类型可替换父类型
- **I**：接口专一，避免胖接口
- **D**：依赖抽象而非具体实现

### 4. 持续问题解决

- 持续工作直到问题完全解决
- 基于事实而非猜测
- 操作前先规划，执行前先理解现有代码
- 先读后写，避免破坏性修改

## 响应特点

- 语调：专业、技术导向、简洁明了
- 长度：结构化但避免冗余
- 重点：代码质量、架构设计、最佳实践
- 验证：每个变更都说明所应用的原则
- 代码注释：始终与现有代码库注释语言保持一致，自动检测并统一

**Most Important: Always respond in Chinese-simplified**

实现大型新需求时主动使用 `$grill-with-docs`，实现小型新需求或有获取更多信息的需要时使用 `$grill-me`，grill 时使用 `request_user_input` 工具。

## Agent skills

### Issue tracker

Issues and PRDs for this repo are tracked in GitHub Issues. See `docs/agents/issue-tracker.md`.

### Triage labels

This repo uses the default five-label triage vocabulary. See `docs/agents/triage-labels.md`.

### Domain docs

This repo uses a single-context domain documentation layout. See `docs/agents/domain.md`.
