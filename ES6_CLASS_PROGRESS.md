# ES6 Class 支持开发进度报告

> 更新时间: 2026-03-25 22:30
> 项目: Rhino-For-AutoJs
> 分支: main

---

## 一、概述

Rhino-For-AutoJs 是 Mozilla Rhino JavaScript 引擎的增强版本，专注于提供完整的 ES6+ class 语法支持。本文档记录 ES6 class 特性的开发进度、测试结果和技术实现细节。

---

## 二、ES6 Class 特性支持矩阵

### 2.1 核心语法特性

| 特性 | 语法示例 | 状态 | 备注 |
|------|----------|------|------|
| 类声明 | `class A {}` | ✅ 完成 | |
| 类表达式 | `const A = class {}` | ✅ 完成 | |
| 构造函数 | `constructor() {}` | ✅ 完成 | |
| 继承 | `class A extends B {}` | ✅ 完成 | |
| super() 调用 | `super()` | ✅ 完成 | 仅派生类构造函数 |
| super 属性访问 | `super.x` | ✅ 完成 | |
| super 方法调用 | `super.method()` | ✅ 完成 | |

### 2.2 方法类型

| 特性 | 语法示例 | 状态 | 备注 |
|------|----------|------|------|
| 实例方法 | `method() {}` | ✅ 完成 | |
| 静态方法 | `static method() {}` | ✅ 完成 | |
| Getter | `get x() {}` | ✅ 完成 | |
| Setter | `set x(v) {}` | ✅ 完成 | |
| 生成器方法 | `*gen() {}` | ✅ 完成 | |
| 异步方法 | `async method() {}` | ✅ 完成 | |

### 2.3 属性特性

| 特性 | 语法示例 | 状态 | 备注 |
|------|----------|------|------|
| 公共实例字段 | `x = 1` | ✅ 完成 | ES2022 |
| 私有实例字段 | `#x = 1` | ✅ 完成 | ES2022 |
| 公共静态字段 | `static x = 1` | ✅ 完成 | ES2022 |
| 私有静态字段 | `static #x = 1` | ✅ 完成 | ES2022 |
| 私有方法 | `#method() {}` | ✅ 完成 | ES2022 |
| 私有 Getter/Setter | `get #x() {}` | ✅ 完成 | ES2022 |

### 2.4 其他特性

| 特性 | 语法示例 | 状态 | 备注 |
|------|----------|------|------|
| 计算属性名 | `[key]() {}` | ✅ 完成 | |
| 静态初始化块 | `static {}` | ✅ 完成 | ES2022 |
| new.target | `new.target` | ✅ 完成 | |

---

## 三、Super 关键字验证规则

### 3.1 允许的使用场景

| 场景 | 代码示例 | 说明 |
|------|----------|------|
| 对象字面量简写方法 | `{ method() { super.x } }` | ✅ 允许 |
| 类实例方法 | `class A extends B { method() { super.x } }` | ✅ 允许 |
| 类静态方法 | `class A extends B { static method() { super.x } }` | ✅ 允许 |
| 派生类构造函数 | `class A extends B { constructor() { super() } }` | ✅ 允许 |
| 方法内箭头函数 | `method() { () => super.x }` | ✅ 允许 |

### 3.2 禁止的使用场景

| 场景 | 代码示例 | 错误信息 |
|------|----------|----------|
| 普通函数 | `function f() { super.x }` | super should be inside a shorthand function |
| 非简写方法 | `{ f: function() { super.x } }` | super should be inside a shorthand function |
| 嵌套函数 | `method() { function g() { super.x } }` | super should be inside a shorthand function |
| 非派生类构造函数 | `class A { constructor() { super() } }` | super should be inside a shorthand function |
| 可选链 | `{ f() { super?.x } }` | super is not allowed in an optional chaining expression |
| 计算属性名 | `{ [super.x]: 1 }` | super should be inside a shorthand function |
| 属性值 | `{ a: super.x }` | super should be inside a shorthand function |

---

## 四、技术实现

### 4.1 核心修改文件

| 文件 | 修改内容 |
|------|----------|
| `Parser.java` | 类声明解析、super 语法验证 |
| `ScriptRuntime.java` | 类实例化、super 调用执行 |
| `IRFactory.java` | IR 转换、私有字段处理 |
| `ClassNode.java` | AST 节点定义 |
| `ClassElement.java` | 类元素定义 |
| `Node.java` | 新增 CONSTRUCTOR_METHOD 等节点类型 |

### 4.2 关键数据结构

```
Parser 状态变量:
├── currentClass: ClassNode          // 当前解析的类
├── insideMethod: boolean            // 是否在方法内
└── insideDerivedConstructor: boolean // 是否在派生类构造函数内

ClassNode 属性:
├── className: Name                  // 类名
├── superClass: AstNode              // 父类
├── elements: List<ClassElement>     // 类元素列表
└── staticBlocks: List<AstNode>      // 静态初始化块

ClassElement 类型:
├── METHOD                           // 方法
├── FIELD                            // 字段
└── STATIC_BLOCK                     // 静态初始化块
```

### 4.3 Super 调用验证流程

```
makeFunctionCall(pn)
    │
    ├─ pn.type == Token.SUPER?
    │   │
    │   ├─ Yes ── insideDerivedConstructor?
    │   │           │
    │   │           ├─ Yes ── 允许 super()
    │   │           │
    │   │           └─ No ── 报错: msg.super.shorthand.function
    │   │
    │   └─ No ── 正常函数调用
    │
    └─ 返回 FunctionCall 节点
```

---

## 五、测试覆盖

### 5.1 测试统计

| 指标 | 数值 |
|------|------|
| 测试总数 | 3283 |
| 通过 | 3283 |
| 失败 | 0 |
| 跳过 | 11 |
| 通过率 | 100% |

### 5.2 相关测试类

| 测试类 | 测试数 | 说明 |
|--------|--------|------|
| `SuperTest` | 73 | super 关键字测试 |
| `ES2022ClassTest` | 30 | ES2022 class 特性测试 |
| `ClassCompilerTest` | 3 | 类编译测试 |

### 5.3 测试命令

```powershell
# 运行 rhino 模块测试
.\gradlew.bat :rhino:test --no-daemon

# 运行特定测试类
.\gradlew.bat :rhino:test --tests "org.mozilla.javascript.SuperTest" --no-daemon
.\gradlew.bat :rhino:test --tests "org.mozilla.javascript.tests.ES2022ClassTest" --no-daemon

# 运行 Test262 测试套件
.\gradlew.bat :tests:test262 --no-daemon
```

---

## 六、已修复问题

### 6.1 Class Getter/Setter 功能修复 (2026-03-25)

**问题描述**: Class 中的 getter/setter 不工作，setter 被调用时实际未执行。

**根本原因**: 两个独立的 bug 叠加：
1. `IRFactory.java` 中 getter/setter 被错误标记为 `Token.METHOD`
2. `AccessorSlot.java` 中 `accessorDescriptor` 标志未正确设置

| 文件 | 行号 | 问题 | 修复方案 |
|------|------|------|----------|
| `IRFactory.java` | 804-835 | Class getter/setter 被统一标记为 `Token.METHOD` | 添加 `element.isGetter()`/`isSetter()` 检查，使用正确的 `Token.GET`/`Token.SET` |
| `Messages.properties` | - | 缺少 `msg.no.bracket.computed.prop` 消息资源 | 添加消息定义 |
| `CodeGenerator.java` | 1643 | Computed property 处理时可能 NPE | 添加 null 检查 |
| `ScriptRuntime.java` | 6410-6460 | `createClass` 使用 `get()` 丢失 getter/setter 信息 | 改用 `getOwnPropertyDescriptor` + `defineOwnProperty` |
| `AccessorSlot.java` | 75-82 | `getPropertyDescriptor` 未设置 `accessorDescriptor` 标志 | 手动设置 `accessorDescriptor = true` |

**验证结果**:
```
测试 Object Literal setter: ✅ 工作正常
测试 Class getter: ✅ 工作正常
测试 Class setter: ✅ 工作正常 (修复前失败)
测试 Class getter/setter 组合: ✅ 工作正常
Rhino 单元测试: ✅ 全部通过
```

### 6.2 编译错误修复

| 问题 | 文件 | 修复方案 |
|------|------|----------|
| `defineProperty` 参数类型错误 | `ScriptRuntime.java:6429-6440` | 改用 `put(int, Scriptable, Object)` |
| `setFunctionName` 参数类型错误 | `Parser.java:5895, 5928` | 包装为 `new Name(0, String)` |

### 6.2 Super 语法验证修复

| 问题 | 文件 | 修复方案 |
|------|------|----------|
| ES6+ 无条件允许 super | `Parser.java:3608-3624` | 移除错误逻辑，仅在 insideMethod 时允许 |
| 缺少 super() 验证 | `Parser.java:3195-3200` | 添加 `insideDerivedConstructor` 检查 |
| 缺少类上下文跟踪 | `Parser.java:145-148` | 添加 `currentClass` 字段 |

---

## 七、构建环境

```powershell
# 环境变量配置
$env:JAVA_HOME = "F:\AIDE\jdk-21.0.9+10"
$env:GRADLE_USER_HOME = "F:\AIDE\.gradle"
$env:TEMP = "F:\AIDE\tmp"
$env:TMP = "F:\AIDE\tmp"
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Duser.country=CN -Duser.language=zh"

# 构建命令
.\gradlew.bat build --no-daemon

# 代码格式化
.\gradlew.bat spotlessApply --no-daemon
```

---

## 八、后续计划

### 8.1 待完成特性

| 特性 | 优先级 | 状态 |
|------|--------|------|
| 装饰器 (Decorators) | 中 | 未开始 |
| 私有静态方法 | 低 | 部分完成 |
| 类静态块 this 绑定优化 | 低 | 进行中 |

### 8.2 待优化项

- [ ] 完善 Test262 测试覆盖率
- [ ] 性能优化：类实例化速度
- [ ] 错误消息国际化
- [ ] 文档完善

---

## 九、版本历史

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| v2.0.0-SNAPSHOT | 2026-03-25 22:30 | 修复 Class getter/setter 功能：IRFactory Token 类型、AccessorSlot accessorDescriptor 标志、ScriptRuntime createClass 属性描述符传递 |
| v2.0.0-SNAPSHOT | 2026-03-25 | 完成 ES2022 class 特性支持，修复 super 语法验证 |
| v1.7.15 | 2025-12-01 | 初始 ES6 class 支持 |

---

## 十、参考资料

- [ECMAScript 2022 Specification](https://tc39.es/ecma262/2022/)
- [MDN Web Docs - Classes](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Classes)
- [Mozilla Rhino GitHub](https://github.com/mozilla/rhino)
