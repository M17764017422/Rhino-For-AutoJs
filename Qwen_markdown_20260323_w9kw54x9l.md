# 🎯 提示词：Rhino ES2022 Class 代码库核对与文件调整清单生成

## 1. 角色设定
你是一名 Rhino JavaScript 引擎的核心架构师，负责 ES2022 Class 特性的落地实施。你精通 Java 编译原理、ECMAScript 规范以及 Rhino 的内部架构（Token, AST, IR, Bytecode, Runtime）。

## 2. 任务背景
基于以下两份文档，你需要深入 Rhino 代码库进行系统性核对，验证 **v1.11 实施计划** 的可行性，并生成一份可执行的 **文件调整清单**。

**参考文档：**
1. `ES2022_CLASS_IMPLEMENTATION_PLAN.md` (版本 1.11) - 最新实施计划
2. `ES2022_CLASS_EXPLORATION_REPORT.md` - 前期代码探索报告

## 3. 核心任务

### 任务一：计划假设核对 (Plan Verification)
请遍历代码库，验证 v1.11 计划中的关键技术假设是否准确。重点检查以下模块：

| 模块 | 检查点 | 计划假设 | 需验证内容 |
|------|--------|----------|------------|
| **Token** | `Token.java` | `LAST_TOKEN` ≈ 164 | 确认当前实际值，确保新 Token (CLASS=164+) 无冲突 |
| **Super** | `ScriptRuntime.java` | 已有 `getSuperProp` | 确认方法签名及行号，评估复用性 |
| **Super** | `Node.java` | `SUPER_PROPERTY_ACCESS`=31 | 确认属性常量存在性及值 |
| **TDZ** | `UniqueTag.java` | 需新增 `TDZ_VALUE` | 确认现有 UniqueTag 结构，评估新增可行性 |
| **Optimizer** | `optimizer/Codegen.java` | 无 Class 支持 | 确认 `visitStatement` 中是否处理 `Token.CLASS` |
| **Thread** | `NativeWeakMap.java` | 参考其实现 | 确认其线程安全策略（是否使用 ConcurrentHashMap） |
| **Runtime** | `BaseFunction.java` | 继承自 BaseFunction | 确认 `construct` 方法逻辑，评估重写工作量 |

### 任务二：制定核对评估计划 (Assessment Plan)
基于 v1.11 计划的里程碑 (M0-M8)，制定一个详细的代码核对评估计划。
- **阶段划分**：按 M0 (基础设施) 到 M8 (优化器) 划分。
- **验证方法**：指定每个阶段需要运行的 grep 搜索命令或代码检查点。
- **风险标记**：标记出高风险文件（如修改后可能导致回归测试失败的文件）。

### 任务三：生成具体文件调整清单 (File Adjustment Checklist)
这是本任务的核心输出。请基于 v1.11 计划的 **4.2 修改文件** 和 **4.1 新增文件** 章节，结合代码库实际结构，生成一份精确到 **方法级** 的修改清单。

**清单要求：**
1. **文件路径**：必须基于 Rhino 标准目录结构 (e.g., `rhino/src/main/java/...`)。
2. **修改位置**：尽可能提供方法名或行号范围。
3. **修改内容**：简述需要添加/修改的代码逻辑。
4. **优先级**：P0 (阻塞), P1 (核心), P2 (优化), P3 (测试)。
5. **预估代码量**：以行为单位 (LOC)。

## 4. 输出格式要求

请严格按照以下 Markdown 结构输出报告：

```markdown
# Rhino ES2022 Class 代码库核对与文件调整清单

## 1. 执行摘要
- **计划版本**: v1.11
- **核对日期**: [当前日期]
- **假设验证通过率**: [X]%
- **主要风险**: [列出 Top 3 风险]

## 2. 计划假设核对结果

| 模块 | 检查项 | 计划假设 | 实际发现 | 状态 | 备注 |
|------|--------|----------|----------|------|------|
| Token | LAST_TOKEN 值 | 164 | [实际值] | ✅/❌ | [说明] |
| Super | getSuperProp 方法 | 存在 | [存在/不存在] | ✅/❌ | [行号] |
| ... | ... | ... | ... | ... | ... |

## 3. 核对评估计划

### 3.1 阶段划分
| 阶段 | 对应里程碑 | 重点验证文件 | 验证命令/方法 |
|------|------------|--------------|---------------|
| 基础设施 | M0 | Token.java, UniqueTag.java | grep "LAST_TOKEN" |
| 解析层 | M1-M3 | Parser.java, TokenStream.java | grep "statement()" |
| 转换层 | M4 | IRFactory.java | grep "transformFunction" |
| 运行时 | M5 | NativeClass, ScriptRuntime | 检查继承链 |
| 优化器 | M8 | optimizer/Codegen.java | 检查 switch case |

### 3.2 高风险文件预警
| 文件 | 风险等级 | 原因 | 缓解措施 |
|------|----------|------|----------|
| Parser.java | 高 | 核心解析逻辑，易影响现有语法 | 增加回归测试覆盖 |
| ... | ... | ... | ... |

## 4. 具体文件调整清单 (File Adjustment Checklist)

### 4.1 新增文件
| 优先级 | 文件路径 | 用途 | 预估代码量 | 依赖模块 |
|--------|----------|------|------------|----------|
| P0 | `.../ast/ClassNode.java` | Class AST 节点 | 250 LOC | Token.java |
| P0 | `.../ast/ClassElement.java` | Class 元素节点 | 180 LOC | ClassNode |
| P0 | `.../NativeClass.java` | 运行时类对象 | 300 LOC | BaseFunction |
| P1 | `.../UninitializedObject.java` | 派生类 this 占位 | 80 LOC | NativeClass |
| P2 | `.../tests/es2022/ClassTest.java` | 单元测试 | 200 LOC | JUnit |

### 4.2 修改文件 (精确到方法)
| 优先级 | 文件路径 | 方法/位置 | 修改内容 | 预估代码量 | 风险等级 |
|--------|----------|-----------|----------|------------|----------|
| P0 | `Token.java` | 常量定义区 | 新增 CLASS, EXTENDS 等 6 个 Token | 20 LOC | 低 |
| P0 | `Token.java` | `typeToName()` | 添加新 Token 名称映射 | 10 LOC | 低 |
| P0 | `TokenStream.java` | `getToken()` | 添加 `#` 私有字段识别逻辑 | 30 LOC | 中 |
| P0 | `TokenStream.java` | `stringToKeywordForES()` | 修改 class/extends 返回值 | 5 LOC | 低 |
| P0 | `Parser.java` | `statement()` | 添加 Token.CLASS 分支 | 10 LOC | 中 |
| P0 | `Parser.java` | 新增方法 | 实现 `parseClassDefinition()` | 150 LOC | 高 |
| P0 | `Parser.java` | 新增方法 | 实现 `parseClassBody()` | 100 LOC | 高 |
| P1 | `Node.java` | 常量定义区 | 新增 CLASS_NAME_PROP 等属性 | 15 LOC | 低 |
| P1 | `IRFactory.java` | `transform()` | 添加 Token.CLASS 处理分支 | 20 LOC | 高 |
| P1 | `IRFactory.java` | 新增方法 | 实现 `transformClass()` | 200 LOC | 高 |
| P1 | `IRFactory.java` | 新增方法 | 实现 `injectFieldInitializers()` | 50 LOC | 中 |
| P1 | `ScriptRuntime.java` | 新增方法 | 实现 `createClass()` | 100 LOC | 高 |
| P1 | `ScriptRuntime.java` | 新增方法 | 实现 `getPrivateField()` | 50 LOC | 中 |
| P2 | `CodeGenerator.java` | `switch` 语句 | 添加新 Token 的 case 分支 | 50 LOC | 中 |
| P2 | `optimizer/Codegen.java` | `transform()` | 添加类节点降级或支持逻辑 | 50 LOC | 高 |
| P3 | `Messages.properties` | 文件末尾 | 新增 40+ 条错误消息 | 45 LOC | 低 |
| P3 | `test262.properties` | 排除列表 | 移除 class 测试排除标记 | 2 LOC | 低 |

## 5. Git 提交策略建议

### 5.1 提交分组
| 提交组 | 包含文件 | 说明 |
|--------|----------|------|
| group-01 | Token.java, Node.java | 基础常量定义，无逻辑依赖 |
| group-02 | ClassNode.java, ClassElement.java | AST 节点定义，独立编译 |
| group-03 | TokenStream.java, Parser.java | 解析逻辑，影响语法树生成 |
| group-04 | IRFactory.java, CodeGenerator.java | 转换与字节码，影响执行 |
| group-05 | NativeClass.java, ScriptRuntime.java | 运行时逻辑，核心功能 |
| group-06 | Tests, Properties | 测试与资源配置 |

### 5.2 回滚计划
- 若 **group-03** 导致解析失败，立即回滚并检查 Token 定义。
- 若 **group-05** 导致运行时错误，检查 `super()` 调用链和原型链设置。

## 6. 结论与下一步
- **计划可行性**: [可行/需调整/不可行]
- **主要阻碍**: [列出]
- **建议行动**: [立即启动 M0 / 需先修复某 Bug / ...]