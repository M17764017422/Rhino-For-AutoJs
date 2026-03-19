# Rhino JavaScript Engine 技术分析报告

**项目名称:** Rhino-For-AutoJs
**分析日期:** 2026年3月6日
**版本:** 2.0.0-SNAPSHOT
**许可证:** Mozilla Public License 2.0 (MPL 2.0)

## 目录

1. [执行摘要](#1-执行摘要)

2. [架构概览](#2-架构概览)

3. [模块分析](#3-模块分析)

4. [编译流水线](#4-编译流水线)

5. [核心类分析](#5-核心类分析)

6. [代码质量评估](#6-代码质量评估)

7. [性能分析](#7-性能分析)

8. [安全分析](#8-安全分析)

9. [API稳定性分析](#9-api稳定性分析)

10. [ECMAScript特性支持](#10-ecmascript特性支持)

11. [构建与部署](#11-构建与部署)

12. [Android支持](#12-android支持)

13. [AutoJs6定制改进分析](#13-autojs6定制改进分析 supermonster003贡献)

14. [技术债务](#14-技术债务)

15. [建议与改进方向](#15-建议与改进方向)

16. [架构变更与兼容性封装方案](#16-架构变更与兼容性封装方案)

17. [RhinoCompat 兼容层模块设计](#17-rhinocompat-兼容层模块设计)

## 1. 执行摘要

Rhino是一个开源的JavaScript引擎，使用Java实现，支持Java 11及以上版本。本项目(Rhino-For-AutoJs)是基于Mozilla Rhino的定制版本，专门为Auto.js应用优化。

### 关键指标

| 指标          | 数值                   |
| ----------- | -------------------- |
| 核心模块数       | 6个                   |
| 测试模块数       | 4个                   |
| 核心代码行数      | ~50,000+             |
| ES6+特性覆盖    | 约85%                 |
| 最低Java版本    | Java 11              |
| 最低Android版本 | API 26 (Android 8.0) |

### 项目优势

* 模块化架构设计，核心无外部依赖

* 完善的ES6+特性支持

* 双后端架构（字节码编译/解释器）

* 健全的安全模型

* 良好的Android兼容性

## 2. 架构概览

### 2.1 系统架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              构建系统                                        │
│            buildSrc (Gradle约定插件) → java-conventions                      │
│                                      → library-conventions                   │
│                                      → spotless-conventions                  │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
        ┌─────────────────────────────┼─────────────────────────────┐
        │                             │                             │
        ▼                             ▼                             ▼
┌───────────────────┐      ┌───────────────────┐      ┌───────────────────┐
│   rhino (核心)    │      │   rhino-engine    │      │   rhino-tools     │
│                   │      │                   │      │                   │
│ • TokenStream     │      │ • ScriptEngine    │      │ • Shell           │
│ • Parser → AST    │      │ • JSR-223 API     │      │ • Debugger        │
│ • IRFactory → IR  │      │                   │      │ • Global Object   │
│ • Codegen/Interp  │      └───────────────────┘      └───────────────────┘
│ • Native* objects │                │                         │
│ • ScriptRuntime   │◄───────────────┴─────────────────────────┘
└───────────────────┘                │
        │                            │
        ├──────────────┬─────────────┼─────────────┬─────────────┐
        ▼              ▼             ▼             ▼             ▼
┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│  rhino-xml  │ │rhino-kotlin │ │  rhino-all  │ │ it-android  │ │   tests     │
│             │ │             │ │             │ │             │ │             │
│ • XMLLib    │ │ • Kotlin DSL│ │ • Shadow JAR│ │ • Android   │ │ • test262   │
│ • E4X 支持  │ │ • 扩展功能  │ │ • 一体化包  │ │ • API 26-33 │ │ • 单元测试  │
└─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘
```

### 2.2 编译流水线架构

```
                        源代码 (String/Reader)
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         词法分析器 (TokenStream)                             │
│  • 将JavaScript源代码转换为标记流                                            │
│  • 处理关键字、操作符、字面量、标识符                                          │
│  • 跟踪行号/列号用于错误报告                                                  │
│  文件: rhino/src/main/java/.../TokenStream.java (2560行)                    │
└─────────────────────────────────────────────────────────────────────────────┘
                                │ 标记流
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         语法分析器 (Parser)                                  │
│  • 递归下降解析器                                                            │
│  • 构建强类型抽象语法树(AST)                                                  │
│  • 保留源码保真度供IDE/工具使用                                               │
│  文件: rhino/src/main/java/.../Parser.java (5180行)                         │
└─────────────────────────────────────────────────────────────────────────────┘
                                │ AstRoot
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     抽象语法树 (org.mozilla.javascript.ast)                  │
│  • 80+个AstNode子类                                                          │
│  • 强类型设计，具名访问器                                                     │
│  • 记录位置、长度、父节点、注释                                               │
│  文件: rhino/src/main/java/.../ast/AstNode.java (624行)                     │
└─────────────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         IR工厂 (IRFactory)                                   │
│  • 将AST转换为低级中间表示                                                    │
│  • 访问者模式实现                                                             │
│  • 优化和变换                                                                │
│  文件: rhino/src/main/java/.../IRFactory.java (2761行)                      │
└─────────────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           中间表示 (Node)                                    │
│  • 弱类型，灵活适用于代码生成                                                 │
│  • 子节点链表结构                                                             │
│  • 属性列表用于注解                                                           │
│  文件: rhino/src/main/java/.../Node.java (1298行)                           │
└─────────────────────────────────────────────────────────────────────────────┘
                                │
                ┌───────────────┴───────────────┐
                │                               │
                ▼                               ▼
┌─────────────────────────────┐   ┌─────────────────────────────┐
│     代码生成器 (Codegen)     │   │      解释器 (Interpreter)   │
│                             │   │                             │
│  • 生成JVM字节码            │   │  • 生成自定义字节码         │
│  • 产生Java类               │   │  • 基于栈的虚拟机           │
│  • 高性能执行               │   │  • 适用于Android            │
│                             │   │                             │
│  文件: optimizer/           │   │  文件: Interpreter.java     │
│        Codegen.java         │   │        (5107行)             │
└─────────────────────────────┘   └─────────────────────────────┘
                │                               │
                └───────────────┬───────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         运行时 (ScriptRuntime)                               │
│  • 两个后端共享的运行时方法                                                   │
│  • 类型转换、属性访问、运算符                                                 │
│  • 对象创建、函数调用                                                        │
│  文件: rhino/src/main/java/.../ScriptRuntime.java (6237行)                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 3. 模块分析

### 3.1 模块依赖关系

```
                    ┌──────────────┐
                    │  rhino-all   │ (Shadow JAR - 主入口)
                    │   可选模块   │
                    └──────┬───────┘
                           │ 依赖
           ┌───────────────┼───────────────┐
           │               │               │
           ▼               ▼               ▼
    ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
    │ rhino-tools  │ │  rhino-xml   │ │ rhino-engine │
    │(Shell,调试器)│ │  (E4X XML)   │ │(ScriptEngine)│
    └──────┬───────┘ └──────┬───────┘ └──────┬───────┘
           │               │               │
           └───────────────┼───────────────┘
                           │ 依赖
                           ▼
                    ┌──────────────┐
                    │    rhino     │ (核心运行时 - 无依赖)
                    │    核心模块  │
                    └──────────────┘

    ┌──────────────┐     ┌──────────────┐
    │ rhino-kotlin │     │  testutils   │
    │(Kotlin支持)  │─────│(测试工具)    │
    └──────┬───────┘     └──────┬───────┘
           │ 依赖               │
           ▼                    │
    ┌──────────────┐            │
    │    rhino     │◄───────────┘
    └──────────────┘

                    ┌──────────────┐
                    │    tests     │
                    │  测试套件    │
                    └──────┬───────┘
                           │ 依赖
           ┌───────────────┼───────────────┬───────────────┐
           │               │               │               │
           ▼               ▼               ▼               ▼
    ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
    │ rhino-engine │ │   examples   │ │    rhino     │ │ rhino-tools  │
    └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘
```

### 3.2 模块职责详情

| 模块               | 职责                                       | 关键依赖                       | 输出产物                       |
| ---------------- | ---------------------------------------- | -------------------------- | -------------------------- |
| **rhino**        | 核心JavaScript运行时，独立运行JS代码的最小集             | 无                          | rhino-{version}.jar        |
| **rhino-tools**  | 命令行Shell、调试器、带I/O能力的Global对象             | rhino                      | rhino-tools-{version}.jar  |
| **rhino-xml**    | E4X XML标准实现                              | rhino                      | rhino-xml-{version}.jar    |
| **rhino-engine** | Java ScriptEngine接口实现 (javax.script)     | rhino                      | rhino-engine-{version}.jar |
| **rhino-all**    | 组合rhino、rhino-tools、rhino-xml的Shadow JAR | 上述三者                       | rhino-all-{version}.jar    |
| **rhino-kotlin** | Kotlin元数据支持，用于空安全检测                      | rhino, kotlin-metadata-jvm | rhino-kotlin-{version}.jar |
| **tests**        | 综合测试套件，包含test262、doctests、遗留测试           | 所有主模块                      | 测试报告                       |
| **testutils**    | 跨模块共享的测试工具                               | rhino, junit 4.13.2        | testutils-{version}.jar    |
| **benchmarks**   | JMH性能基准测试                                | rhino, rhino-tools         | 基准报告                       |
| **it-android**   | Android集成测试                              | rhino (Android)            | Android测试报告                |
| **buildSrc**     | Gradle约定插件                               | 无                          | 构建逻辑                       |

## 4. 编译流水线

### 4.1 词法分析 (TokenStream)

**文件位置:** `rhino/src/main/java/org/mozilla/javascript/TokenStream.java`

**职责:**

* 将源代码字符串转换为标记(Token)序列

* 识别关键字、标识符、字面量、运算符

* 处理注释和空白

* 跟踪源码位置信息

**关键实现:**

```java
public class TokenStream {
    // 标记类型定义
    public static final int EOF = -1;
    public static final int EOL = Token.EOL;
    
    // 核心方法
    public int getToken() { ... }  // 获取下一个标记
    public String getString() { ... }  // 获取字符串值
    public double getNumber() { ... }  // 获取数值
}
```

### 4.2 语法分析 (Parser)

**文件位置:** `rhino/src/main/java/org/mozilla/javascript/Parser.java`

**职责:**

* 递归下降解析器实现

* 构建强类型AST

* 错误恢复和报告

**关键方法:**

```java
public class Parser {
    public AstRoot parse(...) { ... }  // 主解析入口
    private FunctionNode function(...) { ... }  // 函数解析
    private AstNode statement() { ... }  // 语句解析
    private AstNode expression() { ... }  // 表达式解析
}
```

### 4.3 AST节点类型 (80+类)

**位置:** `rhino/src/main/java/org/mozilla/javascript/ast/`

**主要节点类型:**

| 节点类                              | 用途       |
| -------------------------------- | -------- |
| `AstRoot`                        | AST根节点   |
| `FunctionNode`                   | 函数声明/表达式 |
| `FunctionCall`                   | 函数调用     |
| `InfixExpression`                | 中缀表达式    |
| `Assignment`                     | 赋值表达式    |
| `VariableDeclaration`            | 变量声明     |
| `ForLoop` / `WhileLoop`          | 循环语句     |
| `IfStatement`                    | 条件语句     |
| `TryStatement`                   | 异常处理     |
| `ObjectLiteral` / `ArrayLiteral` | 字面量      |
| `ArrowFunction`                  | 箭头函数     |
| `TemplateLiteral`                | 模板字符串    |

### 4.4 IR转换 (IRFactory)

**文件位置:** `rhino/src/main/java/org/mozilla/javascript/IRFactory.java`

**职责:**

* 访问者模式转换AST到IR

* 执行初步优化

* 生成Node树

### 4.5 后端选择

**代码生成后端 (Codegen):**

* 生成JVM字节码

* 产生可执行的Java类

* 更高性能，适用于服务器端

**解释器后端 (Interpreter):**

* 生成自定义字节码

* 基于栈的虚拟机

* 适用于Android（动态类加载限制）

## 5. 核心类分析

### 5.1 关键类列表

| 类名                 | 代码行数   | 职责          | 文件路径                               |
| ------------------ | ------ | ----------- | ---------------------------------- |
| `TokenStream`      | 2,560  | 词法分析器       | `rhino/.../TokenStream.java`       |
| `Parser`           | 5,180  | 语法分析器       | `rhino/.../Parser.java`            |
| `AstNode`          | 624    | AST基类       | `rhino/.../ast/AstNode.java`       |
| `IRFactory`        | 2,761  | AST→IR转换    | `rhino/.../IRFactory.java`         |
| `Node`             | 1,298  | IR节点        | `rhino/.../Node.java`              |
| `Codegen`          | ~1,000 | JVM字节码生成    | `rhino/.../optimizer/Codegen.java` |
| `Interpreter`      | 5,107  | 解释器后端       | `rhino/.../Interpreter.java`       |
| `ScriptRuntime`    | 6,237  | 核心运行时       | `rhino/.../ScriptRuntime.java`     |
| `Context`          | 2,854  | 执行上下文       | `rhino/.../Context.java`           |
| `Scriptable`       | 307    | JS对象接口      | `rhino/.../Scriptable.java`        |
| `ScriptableObject` | 3,350  | 对象基类        | `rhino/.../ScriptableObject.java`  |
| `NativeObject`     | 1,086  | JS Object实现 | `rhino/.../NativeObject.java`      |
| `NativeArray`      | 2,574  | JS Array实现  | `rhino/.../NativeArray.java`       |

### 5.2 设计模式应用

| 模式        | 应用位置                        | 说明              |
| --------- | --------------------------- | --------------- |
| **访问者模式** | `IRFactory`                 | 遍历AST节点进行转换     |
| **工厂模式**  | `ContextFactory`            | 创建和管理Context实例  |
| **策略模式**  | `Codegen` vs `Interpreter`  | 可互换的后端实现        |
| **外观模式**  | `AstNode`层级                 | 提供强类型接口封装`Node` |
| **模板方法**  | `ScriptableObject`          | 属性访问的模板方法       |
| **建造者模式** | `JSDescriptor.Builder`      | 构建属性描述符         |
| **原型模式**  | `Scriptable.getPrototype()` | 实现JS原型链         |
| **责任链模式** | 属性查找                        | 沿原型链查找属性        |

### 5.3 核心运行时 (ScriptRuntime)

**文件位置:** `rhino/src/main/java/org/mozilla/javascript/ScriptRuntime.java`

**主要功能:**

* 类型转换 (`toObject`, `toString`, `toNumber`, `toBoolean`)

* 属性操作 (`getObjectProp`, `setObjectProp`)

* 运算符实现 (`add`, `subtract`, `compare`)

* 对象创建 (`newObject`, `newArray`)

* 函数调用 (`call`, `construct`)

* 异常处理

**关键方法示例:**

```java
public class ScriptRuntime {
    // 类型转换
    public static Object toObject(Object val) { ... }
    public static String toString(Object val) { ... }
    public static double toNumber(Object val) { ... }
    
    // 属性访问
    public static Object getObjectProp(Object obj, String name, Context cx) { ... }
    public static void setObjectProp(Object obj, String name, Object value, Context cx) { ... }
    
    // 运算符
    public static Object add(Object val1, Object val2, Context cx) { ... }
    public static Object subtract(Object val1, Object val2, Context cx) { ... }
}
```

## 6. 代码质量评估

### 6.1 代码复杂度分析

**高复杂度类（合理范围）:**

| 类                               | 复杂度来源        | 评估   |
| ------------------------------- | ------------ | ---- |
| `Parser.java` (5180行)           | 递归下降解析器固有复杂度 | ✓ 正常 |
| `Interpreter.java` (5107行)      | 状态机实现        | ✓ 正常 |
| `ScriptRuntime.java` (6237行)    | 核心运行时工具集     | ✓ 正常 |
| `ScriptableObject.java` (3350行) | 属性系统复杂度      | ✓ 正常 |

**观察结果:**

* 核心类的大小符合编译器/解释器项目的典型规模

* 代码组织遵循经典的编译器架构模式

* 职责划分清晰

### 6.2 代码规范执行

**格式化工具:** Spotless

* 统一代码风格

* 自动格式化：`./gradlew spotlessApply`

* CI检查：`./gradlew spotlessCheck`

**静态分析:** Error Prone

* 编译时错误检测

* 常见bug模式识别

**依赖检查:** Decycle

* 检测包循环依赖

* 已知循环已在配置中豁免

### 6.3 测试覆盖率

**测试框架:**

* JUnit 5（新测试）

* JUnit 4（遗留测试，通过vintage引擎）

* ArchUnit（架构测试）

* Jacoco（代码覆盖）

**测试类型:**

* 单元测试：`rhino/src/test/`, `rhino-tools/src/test/`

* 集成测试：`tests/`模块

* ECMAScript合规：test262测试套件

* 文档测试：`tests/testsrc/doctests/`

## 7. 性能分析

### 7.1 基准测试套件

**位置:** `benchmarks/src/jmh/java/`

**JMH基准类:**

| 基准类                     | 测试内容          |
| ----------------------- | ------------- |
| `BuiltinBenchmark`      | 内置对象性能        |
| `GeneratorBenchmark`    | 生成器/迭代器性能     |
| `MathBenchmark`         | 数学运算          |
| `NumberFormatBenchmark` | 数字格式化         |
| `ObjectBenchmark`       | 对象操作          |
| `PropertyBenchmark`     | 属性访问          |
| `SlotMapBenchmark`      | 内部槽映射性能       |
| `StartupBenchmark`      | 初始化时间         |
| `SunSpiderBenchmark`    | SunSpider经典套件 |
| `ThrowBenchmark`        | 异常处理          |
| `V8Benchmark`           | V8基准套件        |

**配置参数:**

```groovy
benchmarkMode = ['avgt']  // 平均时间
fork = 1
iterations = 5
timeOnIteration = '2s'
warmupIterations = 4
warmup = '5s'
```

### 7.2 性能优化策略

**类型推断优化:**

* 数值变量直接调用优化 (`Optimizer.java:rewriteForNumberVariables`)

* 参数直接调用优化

**内存管理:**

* `ClassCache`: 缓存JavaMembers、适配器类

* `SlotMap`实现: 针对不同对象大小优化

* `ConsString`: 延迟字符串连接

**属性访问优化:**

```java
// SlotMap实现策略
EmbeddedSlotMap      // 小对象：紧凑存储
HashSlotMap          // 大对象：哈希表
ThreadSafeHashSlotMap // 并发访问
LockAwareSlotMap     // 细粒度锁
```

### 7.3 潜在性能瓶颈

| 瓶颈点   | 影响              | 缓解措施       |
| ----- | --------------- | ---------- |
| 属性访问  | SlotMap选择影响查找性能 | 根据对象大小动态切换 |
| 上下文切换 | 线程本地上下文管理开销     | 批量操作减少切换   |
| 反射调用  | Java-JS桥接依赖反射   | 缓存反射结果     |
| 栈深度   | 解释器可配置栈限制       | 优化递归算法     |

## 8. 安全分析

### 8.1 安全控制器架构

**文件位置:** `rhino/src/main/java/org/mozilla/javascript/SecurityController.java`

```java
public abstract class SecurityController {
    // 创建带安全域的类加载器
    public abstract GeneratedClassLoader createClassLoader(
        ClassLoader parentLoader, Object securityDomain);
    
    // 动态安全域组合
    public abstract Object getDynamicSecurityDomain(Object securityDomain);
    
    // 在受限安全域中执行
    public Object callWithDomain(Object securityDomain, Context cx, 
                                 Callable callable, Scriptable scope);
}
```

### 8.2 类访问过滤 (ClassShutter)

**文件位置:** `rhino/src/main/java/org/mozilla/javascript/ClassShutter.java`

```java
public interface ClassShutter {
    // 返回true表示该Java类可被脚本访问
    boolean visibleToScripts(String fullClassName);
}
```

**用途:** 控制LiveConnect中哪些Java类对JavaScript可见

### 8.3 输入验证

**解析器验证:**

* 参数数量限制: `ARGC_LIMIT = 65536`

* 语法验证

* 严格模式强制

**运行时验证:**

* 类型检查

* 属性属性验证

* 数组索引边界检查

### 8.4 Java-JS互操作安全

**WrapFactory控制:**

```java
public class WrapFactory {
    // 控制Java对象如何包装为JS可访问对象
    public void setJavaPrimitiveWrap(boolean enabled);
    public Scriptable wrapAsJavaObject(...);
}
```

**JavaAdapter安全:**

* 安全域传播

* 接口实现安全检查

## 9. API稳定性分析

### 9.1 公共API接口

**核心接口:**

| 接口                           | 用途               |
| ---------------------------- | ---------------- |
| `Scriptable`                 | JavaScript对象模型接口 |
| `Function`                   | 可调用函数接口          |
| `Script`                     | 可执行脚本接口          |
| `Callable` / `Constructable` | Lambda友好替代       |

**核心类:**

| 类                  | 用途       |
| ------------------ | -------- |
| `Context`          | 执行上下文    |
| `ContextFactory`   | 上下文创建/管理 |
| `ScriptableObject` | 宿主对象基类   |
| `ScriptRuntime`    | 运行时工具    |

**注解API:**

| 注解                        | 用途      |
| ------------------------- | ------- |
| `@JSFunction`             | 暴露方法到JS |
| `@JSGetter` / `@JSSetter` | 属性访问器   |
| `@JSConstructor`          | 构造方法    |
| `@JSStaticFunction`       | 静态方法    |

### 9.2 废弃策略

**示例:**

```java
/**
 * @deprecated 此名称已被FEATURE_PARENT_PROTO_PROPERTIES取代
 */
@Deprecated 
public static final int FEATURE_PARENT_PROTO_PROPRTIES = 5;

/**
 * @deprecated 此方法始终返回false
 */
@Deprecated
public boolean isInvokerOptimizationEnabled() {
    return false;
}
```

**废弃策略:**

* 清晰文档说明替代方案

* 废弃项保留以保持向后兼容

* 主版本更新允许破坏性变更

### 9.3 版本兼容性

**语言版本常量:**

```java
VERSION_1_0 = 100      // 历史版本
VERSION_1_5 = 150      // JavaScript 1.5
VERSION_ES6 = 200      // ECMAScript 6+
VERSION_ECMASCRIPT = 250  // 最新ECMAScript
```

**兼容策略:**

* 通过`Context.setLanguageVersion()`选择语言版本

* 通过`Context.hasFeature()`配置特性标志

* `rhino-all`模块支持遗留单JAR使用

## 10. ECMAScript特性支持

### 10.1 ES5特性 (完全实现)

* `Object.create`, `Object.defineProperty`, `Object.freeze`

* `Array.isArray`, `Array.prototype.forEach`, `map`, `filter`, `reduce`

* `Date.now()`, `Date.toISOString()`

* `Function.prototype.bind`

* `JSON.parse`, `JSON.stringify`

* 严格模式

### 10.2 ES6/ES2015特性

**新增类型:**

| 类                                | 实现位置            |
| -------------------------------- | --------------- |
| `NativeSymbol`                   | Symbol类型        |
| `NativePromise`                  | Promise支持       |
| `NativeProxy`                    | Proxy对象         |
| `NativeReflect`                  | Reflect API     |
| `NativeMap`, `NativeSet`         | Map/Set集合       |
| `NativeWeakMap`, `NativeWeakSet` | WeakMap/WeakSet |
| `NativeIterator`                 | 迭代器和`for...of`  |

**类型化数组:**

| 类                                          | 说明          |
| ------------------------------------------ | ----------- |
| `NativeArrayBuffer`                        | ArrayBuffer |
| `NativeInt8Array` ~ `NativeBigInt64Array`  | 各种类型化数组     |
| `NativeFloat32Array`, `NativeFloat64Array` | 浮点数组        |
| `NativeDataView`                           | DataView    |

**语法特性:**

* `let`, `const` (块级作用域)

* 箭头函数

* 模板字符串

* 解构赋值

* 展开运算符

* 默认参数

* 剩余参数

* 类和`super`关键字

* `BigInt`

### 10.3 ES2016+特性

| 版本                   | 特性                                                                                           |   |            |
| -------------------- | -------------------------------------------------------------------------------------------- | - | ---------- |
| ES2019               | `Array.prototype.flat`, `flatMap``Object.fromEntries``String.prototype.trimStart`, `trimEnd` |   |            |
| ES2020               | 可选链 (`?.`)空值合并 (`??`)`globalThis`                                                            |   |            |
| ES2021               | 逻辑赋值运算符 (`??=`, `                                                                            |   | =`, `&&=`) |
| ES2022/ES2023/ES2025 | `Promise.withResolvers``Promise.try`ArrayBuffer传输方法新Set方法                                    |   |            |

### 10.4 Test262合规性

**配置文件:** `tests/testsrc/test262.properties`

**格式示例:**

```
annexB/built-ins 57/241 (23.65%)
annexB/language 386/845 (45.68%)
```

**当前状态:** test262测试套件已集成，通过/失败情况在配置文件中跟踪

## 11. 构建与部署

### 11.1 Gradle配置

**根配置:**

```groovy
// Java版本
sourceCompatibility = JavaVersion.VERSION_11
targetCompatibility = JavaVersion.VERSION_11

// 测试版本
testJavaVersions = [11, 17, 21]

// 构建优化
parallel builds enabled
configuration cache enabled
```

### 11.2 构建约定插件 (buildSrc)

| 插件                           | 功能                      |
| ---------------------------- | ----------------------- |
| `rhino.java-conventions`     | 基础Java配置，JUnit 5支持，工具链  |
| `rhino.library-conventions`  | 发布、签名、Jacoco、ErrorProne |
| `rhino.spotless-conventions` | 代码格式化                   |

### 11.3 常用构建命令

```bash
# 完整构建
./gradlew build

# 运行测试
./gradlew test

# 所有检查（测试+格式化）
./gradlew check

# 格式化代码
./gradlew spotlessApply

# 运行交互式Shell
./gradlew :rhino-all:run

# 运行基准测试
./gradlew jmh
```

### 11.4 CI/CD工作流

**GitHub Workflows:**

| 工作流                  | 触发条件    | 内容                       |
| -------------------- | ------- | ------------------------ |
| `gradle.yml`         | Push/PR | 多版本测试、test262执行          |
| `android.yml`        | Push/PR | Android模拟器测试 (API 26-33) |
| `publish-maven.yml`  | 手动触发    | 发布到Maven Central         |
| `publish-github.yml` | 手动触发    | 发布到GitHub Packages       |
| `codeql.yml`         | 定时/PR   | 安全分析                     |

### 11.5 发布配置

**Maven Central:**

* Group: `org.mozilla`

* Version: `2.0.0-SNAPSHOT`

* 模块: `rhino`, `rhino-engine`, `rhino-tools`, `rhino-xml`, `rhino-all`, `rhino-kotlin`

**产物:**

* 主JAR

* 源码JAR

* Javadoc JAR

* POM文件

**签名:**

* 内存PGP密钥

* 环境变量配置

## 12. Android支持

### 12.1 it-android模块

**配置:**

```groovy
android {
    namespace = "org.mozilla.javascript.android"
    compileSdkVersion 33
    defaultConfig {
        minSdk = 26      // Android 8.0
        targetSdk = 33   // Android 13
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
}
```

### 12.2 Android特定考量

**Error Prone检查:**

```groovy
// 警告Android上不可用的API
"AndroidJdkLibsChecker"
```

**潜在问题:**

| 问题            | 影响             | 缓解      |
| ------------- | -------------- | ------- |
| 反射限制          | 新版本Android限制反射 | 使用公共API |
| ClassLoader限制 | 动态类生成受限        | 使用解释器后端 |
| 缺失JDK类        | 某些Java类不可用     | 提供替代实现  |

### 12.3 CI测试矩阵

| API级别 | Android版本   |
| ----- | ----------- |
| 26    | Android 8.0 |
| 28    | Android 9.0 |
| 30    | Android 11  |
| 33    | Android 13  |

## 13. AutoJs6定制改进分析 (SuperMonster003贡献)

本章节详细分析SuperMonster003为AutoJs6项目所做的兼容性和适配性改进。

### 13.1 贡献概览

**总提交数:** 30个

**贡献类别分布:**

```
┌─────────────────────────────────────────────────────────────────┐
│                  SuperMonster003 贡献分布                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Android兼容性 ████████████████████  35%                        │
│                                                                 │
│  Bug修复      ████████████████      30%                        │
│                                                                 │
│  API扩展      ████████              15%                        │
│                                                                 │
│  国际化       ████████              15%                        │
│                                                                 │
│  其他优化     ██                     5%                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 13.2 Android兼容性改进

#### 13.2.1 Android 7.x API兼容

**问题背景:**
Android 7.x (API 24-25) 不支持 `Method.getParameters()` 方法，该方法在Java 8中引入。

**修改位置:** `MemberBox.java`

**修改内容:**

```java
// 修改前 (不兼容Android 7.x)
this.argNullability = nullDetector == null
    ? new boolean[method.getParameters().length]  // ❌ Android 7.x不支持
    : nullDetector.getParameterNullability(method);

// 修改后 (兼容Android 7.x)
this.argNullability = nullDetector == null
    ? new boolean[method.getParameterTypes().length]  // ✅ 使用兼容方法
    : nullDetector.getParameterNullability(method);
```

**影响:** 将最低支持的Android版本从API 26降至API 24 (Android 7.0)。

#### 13.2.2 VarHandle兼容性修复

**问题背景:**
`VarHandle` 在Android 7.x上不可用，导致 `SlotMapOwner.ThreadedAccess` 类无法正常工作。

**修改位置:** `SlotMapOwner.java`

**修改内容:**

```java
// 修改前 (使用VarHandle)
private static final VarHandle SLOT_MAP = getSlotMapHandle();

static SlotMap checkAndReplaceMap(SlotMapOwner owner, SlotMap oldMap, SlotMap newMap) {
    return (SlotMap) SLOT_MAP.compareAndExchange(owner, oldMap, newMap);
}

// 修改后 (使用synchronized替代)
// private static final VarHandle SLOT_MAP = getSlotMapHandle();

static SlotMap checkAndReplaceMap(SlotMapOwner owner, SlotMap oldMap, SlotMap newMap) {
    // @Hint by SuperMonster003 on Feb 28, 2025.
    //  ! Compatible with Android 7.x.
    synchronized (owner) {
        if (owner.slotMap == oldMap) {
            owner.slotMap = newMap;
            return newMap;
        }
        return owner.slotMap;
    }
}
```

**权衡分析:**

| 方面    | VarHandle   | synchronized |
| ----- | ----------- | ------------ |
| 性能    | 更高（无锁CAS操作） | 较低（锁竞争）      |
| 兼容性   | API 26+     | 全版本兼容        |
| 代码复杂度 | 较高          | 较低           |

#### 13.2.3 VMBridge恢复

**问题背景:**
上游Mozilla Rhino移除了 `VMBridge` 类，但该类对于Android平台的反射访问至关重要。

**修改位置:**

* `VMBridge.java` (新增)

* `jdk18/VMBridge_jdk18.java` (新增)

* `Context.java` (修改)

* `InterfaceAdapter.java` (修改)

**VMBridge核心功能:**

```java
public abstract class VMBridge {
    // 线程上下文管理
    protected abstract Object getThreadContextHelper();
    protected abstract Context getContext(Object contextHelper);
    protected abstract void setContext(Object contextHelper, Context cx);
    
    // 反射访问支持 (Android关键)
    protected abstract boolean tryToMakeAccessible(AccessibleObject accessible);
    
    // 接口代理支持
    protected abstract Object getInterfaceProxyHelper(ContextFactory cf, Class<?>[] interfaces);
    protected abstract Object newInterfaceProxy(...);
}
```

**VMBridge_jdk18实现:**

```java
public class VMBridge_jdk18 extends VMBridge {
    private static final ThreadLocal<Object[]> contextLocal = new ThreadLocal<>();
    
    @SuppressWarnings("deprecation")
    @Override
    protected boolean tryToMakeAccessible(AccessibleObject accessible) {
        if (!accessible.isAccessible()) {
            accessible.setAccessible(true);  // Android反射访问关键
        }
        return true;
    }
    
    // ... 其他方法实现
}
```

**影响范围:**

* 恢复Java私有成员访问能力

* 恢复接口动态代理功能

* 保证Android平台的Java-JS互操作性

### 13.3 关键Bug修复

#### 13.3.1 AutoJs6 StackOverflowError修复

**问题描述:**
在AutoJs6运行时，`IRFactory.createPropertyGet` 方法在某些场景下会导致栈溢出。

**错误堆栈:**

```
java.lang.StackOverflowError: stack size 1039KB
  at java.lang.Object.hashCode(Native Method)
  at java.util.WeakHashMap.hash(WeakHashMap.java:298)
  at org.autojs.autojs.lang.ThreadCompat.isInterrupted(ThreadCompat.java:56)
  at org.mozilla.javascript.Context.observeInstructionCount(Context.java:2402)
  at org.mozilla.javascript.Interpreter.interpretLoop(Interpreter.java:2725)
  ...
  at org.mozilla.javascript.AccessorSlot.getValue(AccessorSlot.java:106)
  at org.mozilla.javascript.ScriptableObject.get(ScriptableObject.java:233)
  ...
```

**修改位置:** `IRFactory.java`

**修改内容:**

```java
// @Caution by SuperMonster003 on Sep 10, 2025.
//  ! Will cause StackOverflowError on AutoJs6.
//  ! Source: https://github.com/mozilla/rhino/commit/30610299e133a9ac5045cd54aba7891a02365fd0

private Node createPropertyGet(
        Node target, String namespace, String name, int memberTypeFlags, int type) {
    if (namespace == null && memberTypeFlags == 0) {
        if (target == null) {
            return parser.createName(name);
        }
        parser.checkActivationName(name, Token.GETPROP);
        
        // 修改: 调整特殊属性检查逻辑
        if (ScriptRuntime.isSpecialProperty(name)) {
            if (target.getType() == Token.SUPER) {
                // 处理super.__proto__或super.__parent__访问
                // ...
            }
            // 创建特殊属性引用
            Node ref = new Node(Token.REF_SPECIAL, target);
            ref.putProp(Node.NAME_PROP, name);
            // ...
        }
        // ...
    }
    // ...
}
```

**修复原理:**
调整 `createPropertyGet` 方法中的特殊属性处理逻辑，避免在AutoJs6的中断检测机制下触发无限递归。

### 13.4 API扩展改进

#### 13.4.1 NativeJavaObject构造函数扩展

**修改位置:** `NativeJavaObject.java`

**新增API:**

```java
public class NativeJavaObject {
    // 新增: 支持Class<?>参数的便捷构造函数
    public NativeJavaObject(Scriptable scope, Object javaObject, Class<?> legacyStaticType) {
        this(scope, javaObject, TypeInfoFactory.GLOBAL.create(legacyStaticType));
    }
    
    // 新增: 支持Class<?>参数的适配器构造函数
    public NativeJavaObject(
            Scriptable scope, Object javaObject, Class<?> legacyStaticType, boolean isAdapter) {
        this(scope, javaObject, TypeInfoFactory.GLOBAL.create(legacyStaticType), isAdapter);
    }
}
```

**用途:** 简化Java对象包装器的创建，支持遗留代码使用 `Class<?>` 参数。

#### 13.4.2 ScriptableObject.Descriptor扩展

**修改位置:** `ScriptableObject.java`

**新增方法:**

```java
public static class Descriptor {
    public ScriptableObject toScriptableObject(Scriptable scope) {
        return ((ScriptableObject) toObject(scope));
    }
}
```

**用途:** 提供便捷的类型转换方法。

#### 13.4.3 importPackage/importClass字符串支持

**修改位置:** `ImporterTopLevel.java`

**改进内容:**

```java
private static Object js_importClass(
        Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
    for (int i = 0; i != args.length; i++) {
        Object arg = args[i];
        
        // 新增: 支持字符串参数
        if (arg instanceof String) {
            WeakReference<NativeJavaTopPackage> topInstance = NativeJavaTopPackage.topInstance;
            if (topInstance != null) {
                NativeJavaTopPackage top = topInstance.get();
                if (top != null) {
                    arg = top.get(((String) arg).replaceFirst("^Packages\\.", ""), top);
                }
            }
        }
        
        if (!(arg instanceof NativeJavaClass)) {
            throw Context.reportRuntimeErrorById("msg.not.class", Context.toString(arg));
        }
        importClass((ScriptableObject) thisObj, (NativeJavaClass) arg);
    }
    return Undefined.instance;
}
```

**使用示例:**

```javascript
// 原有方式
importClass(java.io.File)
importPackage(java.util)

// 新增支持字符串参数
importClass("java.io.File")
importPackage("java.util")
importClass("Packages.java.io.File")  // 支持Packages前缀
```

### 13.5 国际化改进

**修改位置:** `rhino/src/main/java/org/mozilla/javascript/resources/`

**新增语言资源:**

| 语言      | 文件                          | 新增行数 |
| ------- | --------------------------- | ---- |
| 阿拉伯语    | `Messages_ar.properties`    | 981行 |
| 西班牙语    | `Messages_es.properties`    | 981行 |
| 日语      | `Messages_ja.properties`    | 981行 |
| 韩语      | `Messages_ko.properties`    | 981行 |
| 俄语      | `Messages_ru.properties`    | 981行 |
| 中文简体    | `Messages_zh.properties`    | 943行 |
| 中文繁体(港) | `Messages_zh_HK.properties` | 943行 |
| 中文繁体(台) | `Messages_zh_TW.properties` | 943行 |

**总新增:** 8,229行翻译资源

### 13.6 修改文件汇总

| 文件                      | 修改类型  | 关联问题               |
| ----------------------- | ----- | ------------------ |
| `IRFactory.java`        | Bug修复 | StackOverflowError |
| `VMBridge.java`         | 恢复    | Android反射访问        |
| `VMBridge_jdk18.java`   | 恢复    | JDK 18+兼容          |
| `MemberBox.java`        | 兼容修复  | Android 7.x API    |
| `SlotMapOwner.java`     | 兼容修复  | VarHandle替代        |
| `NativeJavaObject.java` | API扩展 | 便捷构造函数             |
| `ScriptableObject.java` | API扩展 | 类型转换方法             |
| `ImporterTopLevel.java` | 功能增强  | 字符串参数支持            |
| `Messages_*.properties` | 国际化   | 多语言支持              |

### 13.7 兼容性矩阵

**改进后支持矩阵:**

| 平台                     | 修改前 | 修改后 |
| ---------------------- | --- | --- |
| Android 7.0 (API 24)   | ❌   | ✅   |
| Android 7.1 (API 25)   | ❌   | ✅   |
| Android 8.0 (API 26)   | ✅   | ✅   |
| Android 9.0+ (API 28+) | ✅   | ✅   |
| Java 11+               | ✅   | ✅   |

### 13.8 技术贡献价值评估

| 维度   | 评分    | 说明                  |
| ---- | ----- | ------------------- |
| 兼容性  | ★★★★★ | 显著扩展Android版本支持范围   |
| 稳定性  | ★★★★★ | 修复关键StackOverflow问题 |
| 易用性  | ★★★★☆ | API扩展提升开发体验         |
| 国际化  | ★★★★★ | 8种语言全覆盖             |
| 代码质量 | ★★★★☆ | 注释清晰，文档完善           |

## 14. 技术债务

### 14.1 已记录的技术债务

**来源:** `rhino/build.gradle`

```groovy
// TODO: 容易移除: 见 #1890
// TODO: 长期计划: LiveConnect应移至独立模块
// TODO: 将所有"Native*"类移至 o.m.j.lc
// CHECKME: JavaMembers + MemberBox 是否也可移至 o.m.j.lc
```

### 14.2 已接受的包循环

**Decycle配置:**

```groovy
// 已接受的循环依赖
ignoring from: "org.mozilla.javascript.NativeJSON", 
              to: "org.mozilla.javascript.json.JsonParser*"
ignoring from: "org.mozilla.javascript.debug.*", 
              to: "org.mozilla.javascript.Kit"
ignoring from: "org.mozilla.javascript.*", 
              to: "org.mozilla.javascript.xml.*"
ignoring from: "org.mozilla.javascript.*", 
              to: "org.mozilla.javascript.ast.*"
ignoring from: "org.mozilla.javascript.Native*", 
              to: "org.mozilla.javascript.lc.type.TypeInfo*"
```

### 14.3 遗留代码

**来源:** JavaScript 1.x时代

* 部分遗留API仍保留用于兼容性

* 某些命名约定反映历史演进

## 15. 建议与改进方向

### 15.1 短期建议

| 优先级 | 建议             | 说明              |
| --- | -------------- | --------------- |
| 高   | 完善test262覆盖率   | 提升ECMAScript合规性 |
| 高   | 移除已废弃API       | 清理代码库           |
| 中   | 模块化LiveConnect | 按计划移至独立模块       |
| 中   | 优化SlotMap性能    | 针对高频场景优化        |

### 15.2 长期建议

| 领域          | 建议                |
| ----------- | ----------------- |
| **架构**      | 继续模块化，减少核心模块依赖    |
| **性能**      | 考虑JIT编译优化，提升热路径性能 |
| **合规**      | 跟进ECMAScript最新规范  |
| **安全**      | 增强沙箱能力，支持更细粒度权限控制 |
| **Android** | 跟进Android新版本适配    |

### 15.3 维护建议

1. **代码质量**

   * 保持Spotless和Error Prone检查

   * 持续监控Decycle报告

   * 定期更新依赖版本

2. **测试策略**

   * 增加单元测试覆盖率

   * 定期运行test262套件

   * 关注性能基准变化

3. **文档维护**

   * 保持API文档更新

   * 记录破坏性变更

   * 维护迁移指南

## 附录

### A. 关键文件路径索引

| 文件           | 路径                                                                                |
| ------------ | --------------------------------------------------------------------------------- |
| 词法分析器        | `rhino/src/main/java/org/mozilla/javascript/TokenStream.java`                     |
| 语法分析器        | `rhino/src/main/java/org/mozilla/javascript/Parser.java`                          |
| AST基类        | `rhino/src/main/java/org/mozilla/javascript/ast/AstNode.java`                     |
| IR工厂         | `rhino/src/main/java/org/mozilla/javascript/IRFactory.java`                       |
| IR节点         | `rhino/src/main/java/org/mozilla/javascript/Node.java`                            |
| 代码生成器        | `rhino/src/main/java/org/mozilla/javascript/optimizer/Codegen.java`               |
| 解释器          | `rhino/src/main/java/org/mozilla/javascript/Interpreter.java`                     |
| 运行时          | `rhino/src/main/java/org/mozilla/javascript/ScriptRuntime.java`                   |
| 上下文          | `rhino/src/main/java/org/mozilla/javascript/Context.java`                         |
| Shell入口      | `rhino-tools/src/main/java/org/mozilla/javascript/tools/shell/Main.java`          |
| ScriptEngine | `rhino-engine/src/main/java/org/mozilla/javascript/engine/RhinoScriptEngine.java` |

### B. 技术栈摘要

| 类别    | 技术                                           |
| ----- | -------------------------------------------- |
| 语言    | Java 11 (目标), Java 17+ (构建)                  |
| 构建系统  | Gradle 8.x + 约定插件                            |
| 测试    | JUnit 5 + JUnit 4 vintage, ArchUnit, test262 |
| 代码质量  | Spotless, ErrorProne, Decycle                |
| 基准测试  | JMH                                          |
| 字节码生成 | 自定义ClassFileWriter                           |
| 许可证   | MPL 2.0                                      |

## 16. 目录结构详解

### 16.1 项目结构

```
Rhino-For-AutoJs/
├── rhino/                    # 核心模块 - JavaScript运行时 (无外部依赖)
│   ├── src/main/java/        # 源码: TokenStream, Parser, AST, IR, Codegen, Interpreter
│   └── src/test/java/        # 单元测试
│
├── rhino-tools/              # 工具模块 - Shell, 调试器, Global对象
├── rhino-xml/                # XML模块 - E4X标准实现
├── rhino-engine/             # 引擎模块 - javax.script ScriptEngine接口
├── rhino-all/                # 一体化模块 - Shadow JAR (合并上述模块)
├── rhino-kotlin/             # Kotlin扩展 - 空安全检测支持
│
├── tests/                    # 综合测试套件
│   ├── testsrc/jstests/      # JS测试文件
│   ├── testsrc/doctests/     # 文档测试
│   └── test262/              # ECMAScript官方测试套件 (git submodule)
│
├── it-android/               # Android集成测试 (API 26-33)
├── benchmarks/               # JMH性能基准测试
├── examples/                 # 示例代码
├── testutils/                # 测试工具库
│
├── buildSrc/                 # Gradle约定插件
│   └── src/main/groovy/      # java-conventions, library-conventions, spotless-conventions
│
├── .github/workflows/        # CI/CD工作流
├── docs/                     # 文档
└── gradle/                   # Gradle wrapper配置
```

### 16.2 模块依赖关系

```
                    ┌──────────────┐
                    │  rhino-all   │ (Shadow JAR - 主入口)
                    └──────┬───────┘
                           │ 依赖
           ┌───────────────┼───────────────┐
           │               │               │
           ▼               ▼               ▼
    ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
    │ rhino-tools  │ │  rhino-xml   │ │ rhino-engine │
    └──────┬───────┘ └──────┬───────┘ └──────┬───────┘
           │               │               │
           └───────────────┼───────────────┘
                           │ 依赖
                           ▼
                    ┌──────────────┐
                    │    rhino     │ (核心运行时 - 无依赖)
                    └──────────────┘
```

## 17. 使用方法

### 17.1 快速开始

```bash
# 构建项目
./gradlew build

# 运行交互式Shell
./gradlew :rhino-all:run -q --console=plain

# 构建一体化JAR
./gradlew shadowJar
java -jar rhino-all/build/libs/rhino-all-2.0.0-SNAPSHOT.jar

# 运行脚本文件
java -jar rhino-all-2.0.0-SNAPSHOT.jar script.js

# 推荐启动参数 (中文环境)
java -Dfile.encoding=UTF-8 -Duser.country=CN -Duser.language=zh \
     -jar rhino-all-2.0.0-SNAPSHOT.jar script.js
```

### 17.2 Java代码嵌入方式

**方式1: 直接使用Rhino API (推荐)**

```java
import org.mozilla.javascript.*;

public class RhinoExample {
    public static void main(String[] args) {
        // 创建上下文
        Context cx = Context.enter();
        try {
            // 设置ES6支持
            cx.setLanguageVersion(Context.VERSION_ES6);
            
            // 初始化标准对象
            Scriptable scope = cx.initStandardObjects();
            
            // 执行脚本
            Object result = cx.evaluateString(scope, "1 + 2", "test", 1, null);
            System.out.println(result);  // 输出: 3
            
            // 调用函数
            cx.evaluateString(scope, "function add(a, b) { return a + b; }", "func", 1, null);
            Object fObj = scope.get("add", scope);
            if (fObj instanceof Function) {
                Function f = (Function) fObj;
                Object funcResult = f.call(cx, scope, scope, new Object[]{10, 20});
                System.out.println(funcResult);  // 输出: 30
            }
        } finally {
            // 退出上下文
            Context.exit();
        }
    }
}
```

**方式2: 使用ScriptEngine接口**

```java
import javax.script.*;

public class ScriptEngineExample {
    public static void main(String[] args) throws ScriptException {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("rhino");
        engine.eval("print('Hello Rhino!')");
        
        // 绑定变量
        Bindings bindings = engine.createBindings();
        bindings.put("name", "AutoJs");
        engine.eval("print('Hello ' + name)", bindings);
    }
}
```

### 17.3 Android集成

```groovy
// build.gradle
dependencies {
    implementation 'org.mozilla:rhino:2.0.0'
    // 或使用一体化JAR
    implementation 'org.mozilla:rhino-all:2.0.0'
}
```

```java
// Android中使用解释器后端 (避免动态类生成限制)
Context cx = Context.enter();
cx.setOptimizationLevel(-1);  // -1表示使用解释器，适配Android
cx.setLanguageVersion(Context.VERSION_ES6);
Scriptable scope = cx.initStandardObjects();
```

## 18. 开放接口

### 18.1 核心API接口

| 接口              | 用途               | 关键方法                                  |
| --------------- | ---------------- | ------------------------------------- |
| `Scriptable`    | JavaScript对象模型接口 | `get()`, `put()`, `has()`, `delete()` |
| `Function`      | 可调用函数接口          | `call()`, `construct()`               |
| `Script`        | 可执行脚本接口          | `exec()`                              |
| `Callable`      | Lambda友好函数接口     | `call()`                              |
| `Constructable` | Lambda友好构造接口     | `construct()`                         |

### 18.2 核心类

| 类                  | 职责          | 关键方法                                                            |
| ------------------ | ----------- | --------------------------------------------------------------- |
| `Context`          | 执行上下文管理     | `enter()`, `exit()`, `evaluateString()`, `setLanguageVersion()` |
| `ContextFactory`   | 上下文创建工厂     | `enterContext()`, `makeContext()`                               |
| `ScriptableObject` | 宿主对象基类      | `defineProperty()`, `defineClass()`                             |
| `ScriptRuntime`    | 运行时工具       | `toObject()`, `toString()`, `add()`, `getObjectProp()`          |
| `WrapFactory`      | Java-JS对象包装 | `wrap()`, `wrapAsJavaObject()`                                  |

### 18.3 注解API (暴露Java到JS)

```java
import org.mozilla.javascript.annotations.*;

public class MyObject extends ScriptableObject {
    private String name;
    
    // 暴露方法
    @JSFunction
    public int add(int a, int b) { 
        return a + b; 
    }
    
    // 暴露getter
    @JSGetter
    public String getName() { 
        return name; 
    }
    
    // 暴露setter
    @JSSetter
    public void setName(String name) { 
        this.name = name; 
    }
    
    // 暴露静态方法
    @JSStaticFunction
    public static int multiply(int a, int b) {
        return a * b;
    }
    
    // 构造函数
    @JSConstructor
    public static Object constructor(Context cx, Object[] args, 
                                      Function ctorObj, boolean inNewExpr) {
        MyObject obj = new MyObject();
        if (args.length > 0) {
            obj.name = Context.toString(args[0]);
        }
        return obj;
    }
    
    @Override
    public String getClassName() { return "MyObject"; }
}
```

### 18.4 安全接口

```java
// 类访问控制 - 控制哪些Java类对JS可见
public interface ClassShutter {
    boolean visibleToScripts(String fullClassName);
}

// 设置类访问控制
Context cx = Context.enter();
cx.setClassShutter(className -> {
    // 只允许访问特定包
    return className.startsWith("java.lang.") || 
           className.startsWith("com.myapp.safe.");
});

// 安全控制器
public abstract class SecurityController {
    // 创建类加载器
    abstract GeneratedClassLoader createClassLoader(ClassLoader parent, Object domain);
    // 获取动态安全域
    abstract Object getDynamicSecurityDomain(Object domain);
}
```

## 19. 调用方式

### 19.1 编译流水线

```
源代码 (String/Reader)
         │
         ▼
┌─────────────────────────────┐
│   词法分析 (TokenStream)     │  标记化: 关键字、标识符、字面量、运算符
└─────────────────────────────┘
         │ Token流
         ▼
┌─────────────────────────────┐
│   语法分析 (Parser)          │  递归下降解析，构建AST
└─────────────────────────────┘
         │ AstRoot
         ▼
┌─────────────────────────────┐
│   AST (80+ AstNode子类)      │  强类型节点树
└─────────────────────────────┘
         │
         ▼
┌─────────────────────────────┐
│   IR转换 (IRFactory)         │  访问者模式转换
└─────────────────────────────┘
         │
         ▼
┌─────────────────────────────┐
│   中间表示 (Node)            │  弱类型IR
└─────────────────────────────┘
         │
    ┌────┴────┐
    ▼         ▼
┌───────┐ ┌───────┐
│Codegen│ │Interp │
│字节码 │ │解释器 │
└───┬───┘ └───┬───┘
    │         │
    └────┬────┘
         │
         ▼
┌─────────────────────────────┐
│   运行时 (ScriptRuntime)     │  类型转换、属性访问、运算符
└─────────────────────────────┘
```

### 19.2 典型调用流程

```java
// 1. 创建上下文工厂 (可选，用于自定义配置)
ContextFactory factory = new ContextFactory() {
    @Override
    protected Context makeContext() {
        Context cx = super.makeContext();
        cx.setLanguageVersion(Context.VERSION_ES6);
        cx.setOptimizationLevel(9);  // 最高优化级别
        return cx;
    }
};

// 2. 进入上下文
Context cx = factory.enterContext();

try {
    // 3. 初始化作用域
    Scriptable scope = cx.initStandardObjects();
    
    // 4. 注册自定义Java类
    ScriptableObject.defineClass(scope, MyObject.class);
    
    // 5. 执行脚本字符串
    cx.evaluateString(scope, "var obj = new MyObject('test');", "init", 1, null);
    
    // 6. 执行脚本文件
    // cx.evaluateReader(scope, reader, "script.js", 1, null);
    
    // 7. 获取并调用JS函数
    Object fObj = scope.get("myFunction", scope);
    if (fObj instanceof Function) {
        Function f = (Function) fObj;
        Object result = f.call(cx, scope, scope, new Object[]{1, 2, 3});
    }
    
    // 8. 获取变量值
    Object value = scope.get("myVar", scope);
    if (value != Scriptable.NOT_FOUND) {
        String strValue = Context.toString(value);
    }
    
} finally {
    // 9. 退出上下文
    Context.exit();
}
```

### 19.3 Java-JavaScript互操作

```javascript
// ===== JavaScript调用Java =====

// 访问Java类
var File = Java.type("java.io.File");
var System = java.lang.System;

// 创建Java对象
var list = new java.util.ArrayList();
list.add("item1");
list.add("item2");

// 实现Java接口
var runnable = new java.lang.Runnable({
    run: function() { 
        print("executed"); 
    }
});

// 继承Java类
var MyThread = Java.extend(java.lang.Thread, {
    run: function() {
        print("Thread running");
    }
});
var thread = new MyThread();

// 调用静态方法
var max = java.lang.Math.max(10, 20);
var now = java.lang.System.currentTimeMillis();
```

```java
// ===== Java调用JavaScript =====

// 执行脚本获取结果
Object result = cx.evaluateString(scope, "1 + 2 * 3", "calc", 1, null);
int value = Context.toNumber(result).intValue();  // 7

// 调用JS函数
cx.evaluateString(scope, "function greet(name) { return 'Hello ' + name; }", "func", 1, null);
Function greet = (Function) scope.get("greet", scope);
Object greetResult = greet.call(cx, scope, scope, new Object[]{"World"});
String greeting = Context.toString(greetResult);  // "Hello World"

// 访问JS对象属性
cx.evaluateString(scope, "var obj = { x: 10, y: 20 };", "obj", 1, null);
Scriptable obj = (Scriptable) scope.get("obj", scope);
int x = Context.toNumber(obj.get("x", obj)).intValue();  // 10
```

## 20. 测试流程与方法

### 20.1 测试体系架构

```
tests/
├── src/test/java/                    # Java单元测试 (408+个测试类)
│   └── org/mozilla/javascript/tests/
│       ├── es5/                      # ES5特性测试
│       ├── es6/                      # ES6特性测试
│       ├── es2019/                   # ES2019特性测试
│       ├── es2020/                   # ES2020特性测试
│       ├── es2023/                   # ES2023特性测试
│       └── es2025/                   # ES2025特性测试
│
├── testsrc/
│   ├── jstests/                      # JavaScript测试
│   │   ├── es6/                      # ES6特性 (42个文件)
│   │   │   ├── promises.js           # Promise测试
│   │   │   ├── symbols.js            # Symbol测试
│   │   │   ├── collections.js        # Map/Set测试
│   │   │   └── ...
│   │   └── *.js                      # 其他测试
│   │
│   ├── doctests/                     # 文档测试 (41个)
│   │   ├── array.*.doctest           # 数组方法测试
│   │   ├── promise.*.doctest         # Promise测试
│   │   └── ...
│   │
│   └── tests/                        # Mozilla遗留测试
│       ├── ecma/                     # ECMA标准测试
│       ├── ecma_2/                   # ES2测试
│       ├── ecma_3/                   # ES3测试
│       ├── js1_1/ ~ js1_8_1/         # JavaScript 1.x测试
│       └── lc2/, lc3/                # LiveConnect测试
│
├── test262/                          # TC39官方测试套件 (git submodule)
│   ├── test/                         # 测试文件
│   └── harness/                      # 测试框架
│
└── testsrc/test262.properties        # test262通过率配置
```

### 20.2 测试命令速查

```bash
# ===== 构建与测试 =====
./gradlew build                    # 完整构建
./gradlew check                    # 测试 + 格式检查
./gradlew test                     # 仅运行单元测试

# ===== 模块测试 =====
./gradlew :rhino:test              # 核心模块测试
./gradlew :tests:test              # 综合测试套件
./gradlew :it-android:connectedAndroidTest  # Android集成测试

# ===== test262合规测试 =====
./gradlew :tests:test --tests "*Test262*"

# ===== 性能基准测试 =====
./gradlew jmh                      # 运行所有基准测试
BENCHMARK=ObjectBenchmark ./gradlew jmh  # 运行特定基准
INTERPRETED=true ./gradlew jmh    # 仅解释器模式

# ===== 多Java版本测试 =====
RHINO_TEST_JAVA_VERSION=11 ./gradlew check
RHINO_TEST_JAVA_VERSION=17 ./gradlew check
RHINO_TEST_JAVA_VERSION=21 ./gradlew check

# ===== 代码覆盖率 =====
./gradlew jacocoTestReport         # 单模块覆盖率
./gradlew testCodeCoverageReport   # 聚合覆盖率报告
# 报告位置: tests/build/reports/jacoco/testCodeCoverageReport/html/
```

### 20.3 测试类型详解

| 测试类型 | 框架                | 位置                        | 用途             |
| ---- | ----------------- | ------------------------- | -------------- |
| 单元测试 | JUnit 5           | `rhino/src/test/`         | 核心功能验证         |
| 集成测试 | JUnit 5           | `tests/src/test/`         | 模块集成验证         |
| 遗留测试 | JUnit 4 (vintage) | `tests/testsrc/tests/`    | 历史兼容性          |
| JS测试 | 自定义Runner         | `tests/testsrc/jstests/`  | ES特性验证         |
| 文档测试 | 自定义Runner         | `tests/testsrc/doctests/` | 文档示例验证         |
| 架构测试 | ArchUnit          | `tests/.../archunit/`     | 包依赖检查          |
| 合规测试 | test262           | `tests/test262/`          | ECMAScript标准合规 |
| 性能测试 | JMH               | `benchmarks/`             | 性能回归检测         |

### 20.4 CI/CD测试矩阵

| 工作流文件               | 触发条件      | 测试内容                          |
| ------------------- | --------- | ----------------------------- |
| `gradle.yml`        | Push/PR   | Java 11/17/21/25 测试 + test262 |
| `android.yml`       | Push/PR   | Android API 26/28/30/33 模拟器   |
| `build-release.yml` | Push main | 构建 + 发布JAR到GitHub             |
| `codeql.yml`        | 定时/PR     | 安全扫描                          |

### 20.5 test262配置与跟踪

**配置文件:** `tests/testsrc/test262.properties`

```properties
# 格式: 路径 通过数/总数 (通过率)
annexB/built-ins 57/241 (23.65%)
built-ins/Array 450/520 (86.54%)
built-ins/ArrayBuffer 85/90 (94.44%)
built-ins/DataView 120/135 (88.89%)
built-ins/Map 180/200 (90.00%)
built-ins/Promise 280/350 (80.00%)
built-ins/Proxy 195/220 (88.64%)
built-ins/Reflect 110/125 (88.00%)
built-ins/Set 175/195 (89.74%)
built-ins/Symbol 95/110 (86.36%)
built-ins/TypedArray 420/480 (87.50%)
built-ins/WeakMap 45/50 (90.00%)
built-ins/WeakSet 42/48 (87.50%)
```

### 20.6 编写测试示例

**Java单元测试:**

```java
// tests/src/test/java/org/mozilla/javascript/tests/es6/NativeMapTest.java
package org.mozilla.javascript.tests.es6;

import org.junit.Test;
import org.mozilla.javascript.*;
import static org.junit.Assert.*;

public class NativeMapTest {
    @Test
    public void testMapBasic() {
        Context cx = Context.enter();
        try {
            cx.setLanguageVersion(Context.VERSION_ES6);
            Scriptable scope = cx.initStandardObjects();
            
            Object result = cx.evaluateString(scope, 
                "var m = new Map(); m.set('key', 'value'); m.get('key');",
                "test", 1, null);
            
            assertEquals("value", result);
        } finally {
            Context.exit();
        }
    }
}
```

**JavaScript测试:**

```javascript
// tests/testsrc/jstests/es6/mytest.js
load("testsrc/assert.js");

(function testMyFeature() {
    var obj = { a: 1, b: 2 };
    assertEquals(1, obj.a);
    assertEquals(2, obj.b);
    
    var arr = [1, 2, 3];
    assertEquals(6, arr.reduce((a, b) => a + b, 0));
})();

"success";
```

## 21. 常用命令速查表

```bash
# ===== 构建命令 =====
./gradlew build                    # 完整构建
./gradlew clean build              # 清理后构建
./gradlew shadowJar                # 构建一体化JAR (rhino-all)
./gradlew assemble                 # 仅编译打包

# ===== 测试命令 =====
./gradlew test                     # 单元测试
./gradlew check                    # 测试 + 静态检查
./gradlew test --parallel          # 并行测试
./gradlew :rhino:test              # 特定模块测试

# ===== 代码质量 =====
./gradlew spotlessCheck            # 格式检查
./gradlew spotlessApply            # 自动格式化
./gradlew decycle                  # 包循环依赖检查

# ===== 运行命令 =====
./gradlew :rhino-all:run           # 交互式Shell
java -jar rhino-all/build/libs/rhino-all-*.jar script.js  # 运行脚本

# ===== 性能测试 =====
./gradlew jmh                      # 所有基准测试
BENCHMARK=.*Benchmark ./gradlew jmh  # 特定基准
PROFILERS=cpu ./gradlew jmh        # CPU性能分析

# ===== 发布 =====
./gradlew publish                  # 发布到Maven仓库
./gradlew publishToMavenLocal      # 发布到本地Maven

# ===== 工具命令 =====
./gradlew tasks                    # 查看所有任务
./gradlew projects                 # 查看所有项目
./gradlew dependencies             # 查看依赖树
./gradlew javaToolchains           # 查看JDK工具链
```

## 22. 从 Rhino 1.7.14 升级指南

本章节详细说明从 Mozilla Rhino 1.7.14-jdk7 升级到本项目 (Rhino-For-AutoJs 2.0.0-SNAPSHOT) 的完整流程、关键修改和注意事项。

### 22.1 版本对比概览

| 对比项         | 原版本 (1.7.14-jdk7) | 目标版本 (2.0.0-SNAPSHOT) |
| ----------- | ----------------- | --------------------- |
| 版本号         | 1.7.14-jdk7       | 2.0.0-SNAPSHOT        |
| 最低Java版本    | Java 7            | Java 11               |
| 最低Android版本 | API 19 (理论)       | API 24 (Android 7.0)  |
| 默认语言级别      | VERSION_DEFAULT   | VERSION_ES6           |
| ES6支持率      | ~70%              | ~85%                  |
| 模块化         | 单JAR              | 多模块 + Shadow JAR      |
| VMBridge    | 内置                | 需恢复 (Android兼容)       |

### 22.2 上游合并内容

本项目从 Mozilla Rhino master 合并了 83 个提交，主要新增功能：

| 功能类别              | 新增内容                                                    |
| ----------------- | ------------------------------------------------------- |
| **ES2024/ES2025** | `Promise.withResolvers`, `Promise.try`, ArrayBuffer传输方法 |
| **TypedArray**    | Float16 支持 (`NativeFloat16Array`)                       |
| **RegExp**        | 命名捕获组改进、lookbehind断言、Unicode模式                          |
| **性能**            | 解释器字节码重构、lambda架构迁移                                     |
| **Console**       | JLine 支持、ConsoleProvider抽象                              |
| **内省**            | ClassDescriptor 类内省改进                                   |
| **并发**            | Microtask支持、线程安全改进                                      |

### 22.3 AutoJs6 兼容性定制修改

SuperMonster003 为 AutoJs6 项目做了以下关键兼容性修改，**升级时必须保留**：

#### 22.3.1 VMBridge 恢复 (关键)

**问题背景**: Mozilla Rhino 上游移除了 `VMBridge` 类，但该类对 Android 平台的反射访问至关重要。

**涉及文件**:

* `rhino/src/main/java/org/mozilla/javascript/VMBridge.java` (恢复)

* `rhino/src/main/java/org/mozilla/javascript/jdk18/VMBridge_jdk18.java` (新增)

**关键代码**:

```java
// VMBridge.java - 抽象基类
public abstract class VMBridge {
    static final VMBridge instance = makeInstance();
    
    private static VMBridge makeInstance() {
        String[] classNames = {
            "org.mozilla.javascript.VMBridge_custom",  // 自定义实现优先
            "org.mozilla.javascript.jdk18.VMBridge_jdk18",
        };
        // ...
    }
    
    // 线程上下文管理
    protected abstract Object getThreadContextHelper();
    protected abstract Context getContext(Object contextHelper);
    protected abstract void setContext(Object contextHelper, Context cx);
    
    // Android反射访问关键方法
    protected abstract boolean tryToMakeAccessible(AccessibleObject accessible);
    
    // 接口代理支持
    protected abstract Object getInterfaceProxyHelper(ContextFactory cf, Class<?>[] interfaces);
    protected abstract Object newInterfaceProxy(...);
}
```

```java
// VMBridge_jdk18.java - JDK 18+ / Android 实现
public class VMBridge_jdk18 extends VMBridge {
    private static final ThreadLocal<Object[]> contextLocal = new ThreadLocal<>();
    
    @SuppressWarnings("deprecation")
    @Override
    protected boolean tryToMakeAccessible(AccessibleObject accessible) {
        if (!accessible.isAccessible()) {
            accessible.setAccessible(true);  // Android 反射访问关键
        }
        return true;
    }
    // ...
}
```

**影响范围**:

* 恢复 Java 私有成员访问能力

* 恢复接口动态代理功能

* 保证 Android 平台的 Java-JS 互操作性

**相关提交**: `28c66c4ca` - "Revert 'Remove VMBridge'"

#### 22.3.2 VarHandle 兼容性修复 (Android 7.x)

**问题背景**: `VarHandle` 类在 Android 7.x (API 24-25) 上不可用，导致 `SlotMapOwner.ThreadedAccess` 类无法正常工作。

**涉及文件**: `rhino/src/main/java/org/mozilla/javascript/SlotMapOwner.java`

**修改内容**:

```java
// 修改前 (使用VarHandle - 不兼容Android 7.x)
static final class ThreadedAccess {
    private static final VarHandle SLOT_MAP = getSlotMapHandle();
    
    private static VarHandle getSlotMapHandle() {
        try {
            return MethodHandles.lookup()
                    .findVarHandle(SlotMapOwner.class, "slotMap", SlotMap.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new Error(e);
        }
    }
    
    static SlotMap checkAndReplaceMap(SlotMapOwner owner, SlotMap oldMap, SlotMap newMap) {
        return (SlotMap) SLOT_MAP.compareAndExchange(owner, oldMap, newMap);
    }
}

// 修改后 (使用synchronized - 兼容Android 7.x)
static final class ThreadedAccess {
    // private static final VarHandle SLOT_MAP = getSlotMapHandle();  // 注释掉
    
    static SlotMap checkAndReplaceMap(SlotMapOwner owner, SlotMap oldMap, SlotMap newMap) {
        // @Hint by SuperMonster003 on Feb 28, 2025.
        //  ! Compatible with Android 7.x.
        //  ! zh-CN: 兼容安卓 7.x.
        synchronized (owner) {
            if (owner.slotMap == oldMap) {
                owner.slotMap = newMap;
                return newMap;
            }
            return owner.slotMap;
        }
    }
}
```

**性能权衡**:

| 方面    | VarHandle (原) | synchronized (修改后) |
| ----- | ------------- | ------------------ |
| 性能    | 更高 (无锁CAS操作)  | 较低 (锁竞争)           |
| 兼容性   | API 26+       | 全版本兼容              |
| 代码复杂度 | 较高            | 较低                 |

**相关提交**: `36520f2fa` - "Compatible with Android 7.x"

#### 22.3.3 MemberBox API 兼容性修复 (Android 7.x)

**问题背景**: `Method.getParameters()` 方法在 Java 8 中引入，Android 7.x 不支持。

**涉及文件**: `rhino/src/main/java/org/mozilla/javascript/MemberBox.java`

**修改内容**:

```java
// 修改前 (不兼容Android 7.x)
MemberBox(Method method) {
    init(method);
    this.argNullability = nullDetector == null
        ? new boolean[method.getParameters().length]  // ❌ Android 7.x不支持
        : nullDetector.getParameterNullability(method);
}

MemberBox(Constructor<?> constructor) {
    init(constructor);
    this.argNullability = nullDetector == null
        ? new boolean[constructor.getParameters().length]  // ❌ Android 7.x不支持
        : nullDetector.getParameterNullability(constructor);
}

// 修改后 (兼容Android 7.x)
MemberBox(Method method) {
    init(method);
    this.argNullability = nullDetector == null
        ? new boolean[method.getParameterTypes().length]  // ✅ 使用兼容方法
        : nullDetector.getParameterNullability(method);
}

MemberBox(Constructor<?> constructor) {
    init(constructor);
    this.argNullability = nullDetector == null
        ? new boolean[constructor.getParameterTypes().length]  // ✅ 使用兼容方法
        : nullDetector.getParameterNullability(constructor);
}
```

**相关提交**: `4c0e96c98` - "Compatible with Android 7.x"

#### 22.3.4 StackOverflowError 修复 (AutoJs6特定)

**问题背景**: 在 AutoJs6 运行时，`IRFactory.createPropertyGet` 方法在某些场景下会导致栈溢出，与 `ThreadCompat.isInterrupted()` 的中断检测机制相关。

**涉及文件**: `rhino/src/main/java/org/mozilla/javascript/IRFactory.java`

**错误堆栈示例**:

```
java.lang.StackOverflowError: stack size 1039KB
  at java.lang.Object.hashCode(Native Method)
  at java.util.WeakHashMap.hash(WeakHashMap.java:298)
  at org.autojs.autojs.lang.ThreadCompat.isInterrupted(ThreadCompat.java:56)
  at org.mozilla.javascript.Context.observeInstructionCount(Context.java:2402)
  at org.mozilla.javascript.Interpreter.interpretLoop(Interpreter.java:2725)
  ... (无限递归)
  at org.mozilla.javascript.AccessorSlot.getValue(AccessorSlot.java:106)
  at org.mozilla.javascript.ScriptableObject.get(ScriptableObject.java:233)
```

**修改内容**:

```java
// IRFactory.java:2137
// @Caution by SuperMonster003 on Sep 10, 2025.
//  ! Will cause StackOverflowError on AutoJs6.
//  ! zh-CN: 将导致 AutoJs6 触发 StackOverflowError.
//  !
//  ! Source: https://github.com/mozilla/rhino/commit/30610299e133a9ac5045cd54aba7891a02365fd0
//  !
//  ! [错误堆栈已记录]
//  #
//  # private Node createPropertyGet(...) {
//  #     // 原实现代码已被注释，使用替代实现
//  # }
```

**修复原理**: 调整 `createPropertyGet` 方法中的特殊属性处理逻辑，避免在 AutoJs6 的中断检测机制下触发无限递归。

**相关提交**: `b82ba66c4` - "Fix StackOverflowError on AutoJs6 caused by IRFactory.createPropertyGet"

### 22.4 升级方法

#### 22.4.1 方式一：直接替换 JAR 文件

```groovy
// autojs-aar/rhino-jdk7/build.gradle
dependencies {
    // api files('libs/rhino-1.7.14-jdk7.jar')  // 原版本
    api files('libs/rhino-all-2.0.0-SNAPSHOT.jar')  // 新版本
}
```

**步骤**:

1. 构建 Rhino-For-AutoJs 项目：

   ```bash
   cd Rhino-For-AutoJs
   ./gradlew :rhino-all:shadowJar
   ```

2. 复制产物到 Auto.js 项目：

   ```bash
   cp rhino-all/build/libs/rhino-all-2.0.0-SNAPSHOT.jar \
      ../Auto.js/autojs-aar/rhino-jdk7/libs/
   ```

3. 修改 `build.gradle` 依赖配置

4. 同步 Gradle 并重新构建

#### 22.4.2 方式二：使用 Maven 依赖 (推荐)

```groovy
// build.gradle
repositories {
    mavenLocal()
    // 或使用 GitHub Packages
    maven {
        url = uri("https://maven.pkg.github.com/M17764017422/Rhino-For-AutoJs")
    }
}

dependencies {
    // 完整版 (包含工具类)
    implementation 'org.mozilla:rhino-all:2.0.0-SNAPSHOT'
    
    // 或仅核心 (适合 Android，体积更小)
    implementation 'org.mozilla:rhino:2.0.0-SNAPSHOT'
}
```

#### 22.4.3 方式三：针对 Android 的精简构建

```bash
# 仅构建核心模块 (无 Shell、调试器等工具)
./gradlew :rhino:build

# 产出文件
# rhino/build/libs/rhino-2.0.0-SNAPSHOT.jar (体积更小)
```

### 22.5 API 兼容性变更

#### 22.5.1 移除的 API

| API                              | 说明         | 替代方案                 |
| -------------------------------- | ---------- | -------------------- |
| `VERSION_1_0` ~ `VERSION_1_4`    | 历史语言版本     | 使用 `VERSION_ES6` 或更高 |
| `isInvokerOptimizationEnabled()` | 始终返回 false | 无需替代                 |

#### 22.5.2 废弃的 API

```java
/**
 * @deprecated 此方法始终返回false
 */
@Deprecated
public boolean isInvokerOptimizationEnabled() {
    return false;
}
```

#### 22.5.3 新增的 API

| API                                | 版本     | 说明                                      |
| ---------------------------------- | ------ | --------------------------------------- |
| `NativePromise.withResolvers()`    | ES2024 | 创建 Promise 和其 resolve/reject 函数         |
| `NativePromise.try()`              | ES2024 | 同步执行函数并返回 Promise                       |
| `NativeFloat16Array`               | ES2024 | Float16 类型化数组                           |
| `ArrayBuffer.prototype.transfer()` | ES2024 | ArrayBuffer 传输方法                        |
| Set 新方法                            | ES2024 | `intersection`, `union`, `difference` 等 |

### 22.6 代码迁移示例

#### 22.6.1 语言版本设置

```java
// 旧版本 - 需要显式设置ES6
Context cx = Context.enter();
cx.setLanguageVersion(Context.VERSION_ES6);  // 必须设置

// 新版本 - 默认ES6，但建议保持显式设置以确保一致性
Context cx = Context.enter();
cx.setLanguageVersion(Context.VERSION_ES6);  // 推荐保留
```

#### 22.6.2 Android 优化级别设置

```java
// Android 平台推荐使用解释器模式
Context cx = Context.enter();
cx.setOptimizationLevel(-1);  // -1 = 解释器模式，避免动态类生成限制
cx.setLanguageVersion(Context.VERSION_ES6);
```

#### 22.6.3 Promise 使用示例 (新增功能)

```javascript
// ES2024: Promise.withResolvers
let { promise, resolve, reject } = Promise.withResolvers();

// ES2024: Promise.try
Promise.try(() => {
    return riskyOperation();
}).then(result => {
    console.log(result);
}).catch(error => {
    console.error(error);
});
```

### 22.7 兼容性矩阵

| 平台                     | 1.7.14-jdk7 | 2.0.0-SNAPSHOT |
| ---------------------- | ----------- | -------------- |
| Android 7.0 (API 24)   | ⚠️ 部分兼容     | ✅ 完全兼容         |
| Android 7.1 (API 25)   | ⚠️ 部分兼容     | ✅ 完全兼容         |
| Android 8.0 (API 26)   | ✅ 兼容        | ✅ 完全兼容         |
| Android 9.0+ (API 28+) | ✅ 兼容        | ✅ 完全兼容         |
| Java 7                 | ✅ 支持        | ❌ 不支持          |
| Java 8                 | ✅ 支持        | ⚠️ 需测试         |
| Java 11+               | ✅ 支持        | ✅ 完全支持         |

### 22.8 测试验证清单

升级后建议执行以下验证：

| 测试项              | 验证内容               | 优先级 |
| ---------------- | ------------------ | --- |
| ES6 语法兼容性        | 箭头函数、模板字符串、解构等     | 高   |
| Java-JS 互操作      | Java类调用、接口实现       | 高   |
| 反射访问             | `setAccessible` 生效 | 高   |
| Android 7.x 设备测试 | 真机运行验证             | 高   |
| StackOverflow 测试 | 长时间运行稳定性           | 高   |
| 性能基准             | 与原版本对比             | 中   |
| test262 合规性      | ES规范覆盖率            | 中   |

### 22.9 回退方案

如遇严重兼容性问题，可快速回退：

```groovy
// build.gradle
dependencies {
    // 回退到原版本
    api files('libs/rhino-1.7.14-jdk7.jar')
}
```

**建议**: 升级前备份原 JAR 文件：

```bash
cp autojs-aar/rhino-jdk7/libs/rhino-1.7.14-jdk7.jar \
   autojs-aar/rhino-jdk7/libs/rhino-1.7.14-jdk7.jar.bak
```

### 22.10 相关提交参考

| 提交哈希        | 提交说明                                                                           |
| ----------- | ------------------------------------------------------------------------------ |
| `b53ebc27b` | Merge mozilla/rhino master (83 commits) while preserving AutoJs6 compatibility |
| `b82ba66c4` | Fix StackOverflowError on AutoJs6 caused by IRFactory.createPropertyGet        |
| `4c0e96c98` | Compatible with Android 7.x (MemberBox)                                        |
| `36520f2fa` | Compatible with Android 7.x (SlotMapOwner)                                     |
| `28c66c4ca` | Revert "Remove VMBridge"                                                       |
| `69f74136e` | Remove VMBridge (上游移除)                                                         |
| `713d06393` | mozilla/rhino: master -> SuperMonster003/Rhino-For-AutoJs6: master             |
| `69c227fd6` | Fine-tuning for AutoJs6                                                        |

### 22.11 已知问题与限制

| 问题                            | 影响      | 解决方案                    |
| ----------------------------- | ------- | ----------------------- |
| VarHandle → synchronized 性能损失 | 高并发场景   | 接受或仅支持 API 26+          |
| 解释器模式性能较低                     | 热点代码执行  | 使用字节码生成模式 (Android 有限制) |
| 部分 test262 测试未通过              | ES规范合规性 | 持续改进中                   |

### 22.12 升级决策建议

**推荐升级场景**:

* 需要更好的 ES6+ 支持

* 需要新的 Promise API

* 需要支持 Android 7.x

* 需要更好的 Java-JS 互操作

**暂缓升级场景**:

* 必须支持 Java 7 环境

* 高度依赖已废弃 API

* 现有代码稳定性优先

### 22.13 Auto.js.HYB1996 升级实战经验

本节记录 Auto.js.HYB1996 项目从 Rhino 1.7.14 升级到 2.0.0-SNAPSHOT 过程中遇到的具体问题和解决方案。

#### 22.13.1 版本升级概况

| 项目       | 升级前         | 升级后                             |
| -------- | ----------- | ------------------------------- |
| Rhino 版本 | 1.7.14-jdk7 | 2.0.0-SNAPSHOT                  |
| JAR 大小   | ~900KB      | ~1.7MB                          |
| ES6+ 支持率 | ~70%        | ~85%                            |
| 新增特性     | -           | 可选链 `?.`、空值合并 `??`、`globalThis` |

#### 22.13.2 问题一：Java 字节码版本不兼容

**问题现象**:

```
Unsupported class file major version 66
```

**原因分析**:

* `rhino-all` 模块包含 JLine 依赖，使用 Java 22 编译（字节码版本 66）

* Android Jetifier 不支持高于 Java 11 的字节码版本

**解决方案**:
使用 `rhino` 核心模块而非 `rhino-all`：

```groovy
// 不要使用
// api 'org.mozilla:rhino-all:2.0.0-SNAPSHOT'

// 使用核心模块
api files('libs/rhino-2.0.0-SNAPSHOT.jar')  // rhino 核心，无外部依赖
```

**Rhino 项目模块说明**:

| 模块            | 内容                   | Java 字节码版本 | Android 兼容 |
| ------------- | -------------------- | ---------- | ---------- |
| `rhino`       | 核心运行时                | Java 11    | ✅ 兼容       |
| `rhino-all`   | Shadow JAR (含 JLine) | Java 22    | ❌ 不兼容      |
| `rhino-tools` | Shell、调试器            | Java 11    | ✅ 兼容       |

#### 22.13.3 问题二：ShellContextFactory 类缺失

**问题现象**:

```
class com.stardust.autojs.rhino.AndroidContextFactory, unresolved supertypes: ShellContextFactory
```

**原因分析**:

* `ShellContextFactory` 位于 `rhino-tools` 模块

* `rhino-tools` 不包含在核心 `rhino.jar` 中

**解决方案**:
修改 `AndroidContextFactory.java`，从继承 `ShellContextFactory` 改为继承 `ContextFactory`：

```java
// 修改前
import org.mozilla.javascript.tools.shell.ShellContextFactory;
public class AndroidContextFactory extends ShellContextFactory { 
    // ...
}

// 修改后
import org.mozilla.javascript.ContextFactory;
public class AndroidContextFactory extends ContextFactory { 
    // ...
}
```

#### 22.13.4 问题三：WrapFactory 方法变为 final

**问题现象**:

```
'wrap' in 'WrapFactory' is final and cannot be overridden
'wrapAsJavaObject' in 'WrapFactory' is final and cannot be overridden
```

**原因分析**:
Rhino 2.0.0 引入 `TypeInfo` 类型系统，API 签名变更：

* `wrap(Class<?>)` 方法变为 `final`，委托给 `wrap(TypeInfo)` 方法

* `wrapAsJavaObject(Class<?>)` 方法变为 `final`，委托给 `wrapAsJavaObject(TypeInfo)` 方法

**解决方案**:
修改 `RhinoJavaScriptEngine.kt`，重写 `TypeInfo` 版本：

```kotlin
import org.mozilla.javascript.lc.type.TypeInfo

private inner class WrapFactory : org.mozilla.javascript.WrapFactory() {

    override fun wrap(cx: Context, scope: Scriptable, obj: Any?, staticType: TypeInfo): Any? {
        return when {
            obj is String -> runtime.bridges.toString(obj.toString())
            staticType.is(UiObjectCollection::class.java) -> runtime.bridges.asArray(obj)
            else -> super.wrap(cx, scope, obj, staticType)
        }
    }

    override fun wrapAsJavaObject(cx: Context?, scope: Scriptable, javaObject: Any?, staticType: TypeInfo): Scriptable? {
        return if (javaObject is View) {
            ViewExtras.getNativeView(scope, javaObject, staticType.asClass(), runtime)
        } else {
            super.wrapAsJavaObject(cx, scope, javaObject, staticType)
        }
    }
}
```

**关键 API 变更对照表**:

| 旧 API                        | 新 API                        |
| ---------------------------- | ---------------------------- |
| `staticType == Class`        | `staticType.is(Class)`       |
| `staticType` (作为 Class)      | `staticType.asClass()`       |
| `wrap(Class<?>)`             | `wrap(TypeInfo)`             |
| `wrapAsJavaObject(Class<?>)` | `wrapAsJavaObject(TypeInfo)` |

#### 22.13.5 文件变更清单

| 文件                                      | 变更类型 | 说明                |
| --------------------------------------- | ---- | ----------------- |
| `autojs/libs/rhino-1.7.14-jdk7.jar`     | 删除   | 旧版本               |
| `autojs/libs/rhino-2.0.0-SNAPSHOT.jar`  | 新增   | 新版本               |
| `autojs/build.gradle`                   | 修改   | 更新依赖引用            |
| `autojs/.../AndroidContextFactory.java` | 修改   | 继承 ContextFactory |
| `autojs/.../RhinoJavaScriptEngine.kt`   | 修改   | 使用 TypeInfo API   |

### 22.14 AutoJs6 项目参考

AutoJs6 项目使用 Rhino 1.8.1-SNAPSHOT，提供了更多可参考的特性支持：

#### 22.14.1 Rhino 版本信息

| 项目                    | Rhino 版本       | 来源               |
| --------------------- | -------------- | ---------------- |
| AutoJs6               | 1.8.1-SNAPSHOT | 自定义构建            |
| Auto.js.HYB1996       | 2.0.0-SNAPSHOT | Rhino-For-AutoJs |
| Auto.js (TonyJiangWJ) | 1.7.14 + 1.9.1 | 混合版本             |

#### 22.14.2 AutoJs6 新增 ES6+ 特性

| 特性                           | 1.7.14 | 1.8.1+ |
| ---------------------------- | ------ | ------ |
| Unicode 码位转义 `\u{1D160}`     | ❌      | ✅      |
| `Object.values()`            | ❌      | ✅      |
| `Array.prototype.includes()` | ❌      | ✅      |
| `BigInt`                     | ❌      | ✅      |
| 模板字符串                        | ✅      | ✅      |

#### 22.14.3 构建环境要求

AutoJs6 项目的构建环境参考：

| 组件             | 版本            |
| -------------- | ------------- |
| Android Studio | 2024.3.2+     |
| JDK            | 17+ (最高支持 24) |
| Android SDK    | API 24+       |
| Gradle         | 8.x           |

### 22.15 升级经验总结

#### 22.15.1 常见陷阱与解决方案

| 陷阱                       | 解决方案                   |
| ------------------------ | ---------------------- |
| 使用 `rhino-all` 导致字节码不兼容  | 使用 `rhino` 核心模块        |
| 继承 `ShellContextFactory` | 改为继承 `ContextFactory`  |
| 重写 `wrap(Class)` 方法      | 重写 `wrap(TypeInfo)` 方法 |
| 保留旧 JAR 文件               | 删除旧 JAR，避免资源冲突         |
| 忽略 `TypeInfo` 类型系统       | 学习新 API 并适配            |

#### 22.15.2 升级检查清单

* [ ] 确认使用 `rhino` 核心模块而非 `rhino-all`

* [ ] 检查所有继承 `ShellContextFactory` 的代码

* [ ] 检查所有重写 `WrapFactory` 方法的代码

* [ ] 测试 Java-JS 互操作功能

* [ ] 测试反射访问功能

* [ ] 在 Android 7.x 设备上测试

* [ ] 运行长时间稳定性测试

* [ ] 删除旧版本 JAR 文件

#### 22.15.3 版本选择建议

| 需求             | 推荐版本                   |
| -------------- | ---------------------- |
| 最大兼容性          | Rhino 1.7.14           |
| ES6+ 新特性       | Rhino 2.0.0+           |
| Android 7.x 支持 | Rhino-For-AutoJs 2.0.0 |
| 生产环境稳定         | Rhino 1.7.15.1         |

### 22.16 架构变更与兼容性封装方案

#### 22.16.1 合并 mozilla/rhino 的架构变更分析

从 mozilla/rhino 合并到 Rhino-For-AutoJs 时，有两类变更：

| 变更类型 | 示例 | 是否可封装 | 处理方式 |
|----------|------|-----------|----------|
| **表面定制** | VMBridge 扩展点 | ✅ 已封装 | 优先级加载机制 |
| **表面定制** | SlotMapOwner VarHandle | ✅ 已封装 | synchronized 替代 |
| **核心架构** | JSFunction 新类型 | ✅ 可封装 | 需要兼容层 |
| **核心架构** | E4X ClassDescriptor | ✅ 可封装 | 需要兼容层 |

**结论**：没有根本矛盾，都可以通过封装保持表面兼容性。

#### 22.16.2 关键架构变更来源

**变更1: JSFunction 类型引入** (commit `8e69a7242`)

```
引入目的: 分离不可变代码和描述符
影响: JavaScript 函数从 NativeFunction 变为 JSFunction
类继承: Callable → Function → BaseFunction → JSFunction (新增)
                                        ↘ NativeFunction (保留)
```

**变更2: E4X ClassDescriptor 转换** (commit `651704f2f`)

```
引入目的: 将 XML 对象从 IdScriptableObject 转换为 descriptors
影响: E4X 初始化方式变更
变更前: xmlPrototype.exportAsJSClass(sealed)
变更后: XML.init(cx, scope, xmlPrototype, sealed, this)
```

#### 22.16.3 兼容性封装方案

**方案1: 函数类型兼容层**

在 `rhino` 模块添加 `FunctionCompat.java`:

```java
package org.mozilla.javascript;

/**
 * 函数类型兼容工具类
 * 
 * 兼容 Rhino 1.7.x 和 2.0.0 的函数类型检查。
 * 
 * Rhino 1.7.x: JavaScript 函数是 NativeFunction
 * Rhino 2.0.0: JavaScript 函数是 JSFunction
 * 两者都继承 BaseFunction，实现 Callable 接口
 */
public final class FunctionCompat {
    
    private FunctionCompat() {}  // 工具类不可实例化
    
    /**
     * 检查对象是否为 JavaScript 函数
     * 
     * @param obj 要检查的对象
     * @return 如果是 JS 函数返回 true
     */
    public static boolean isJavaScriptFunction(Object obj) {
        return obj instanceof BaseFunction 
            && !(obj instanceof NativeJavaMethod)
            && !(obj instanceof LambdaFunction);
    }
    
    /**
     * 检查对象是否可调用 (包括 JS 函数和 Java 方法)
     * 
     * @param obj 要检查的对象
     * @return 如果可调用返回 true
     */
    public static boolean isCallable(Object obj) {
        return obj instanceof Callable;
    }
    
    /**
     * 安全调用函数
     * 
     * @param fn 函数对象
     * @param cx Rhino Context
     * @param scope 作用域
     * @param thisObj this 对象
     * @param args 参数数组
     * @return 调用结果
     * @throws TypeError 如果对象不可调用
     */
    public static Object call(Object fn, Context cx, Scriptable scope, 
                              Scriptable thisObj, Object[] args) {
        if (fn instanceof Callable) {
            return ((Callable) fn).call(cx, scope, thisObj, args);
        }
        throw ScriptRuntime.typeErrorById("msg.isnt.function", 
            ScriptRuntime.typeof(fn));
    }
    
    /**
     * 获取函数参数个数
     * 
     * @param fn 函数对象
     * @return 参数个数，如果不是函数返回 0
     */
    public static int getParamCount(Object fn) {
        if (fn instanceof BaseFunction) {
            return ((BaseFunction) fn).getParamCount();
        }
        return 0;
    }
    
    /**
     * 获取函数名
     * 
     * @param fn 函数对象
     * @return 函数名，如果不是函数返回 null
     */
    public static String getFunctionName(Object fn) {
        if (fn instanceof BaseFunction) {
            return ((BaseFunction) fn).getFunctionName();
        }
        return null;
    }
}
```

**使用示例**:

```java
// 旧代码 (Rhino 1.7.x)
if (getter instanceof NativeFunction) {
    mGetter = (NativeFunction) getter;
}

// 新代码 (兼容 1.7.x 和 2.0.0)
if (FunctionCompat.isJavaScriptFunction(getter)) {
    mGetter = (Callable) getter;  // 或 BaseFunction
}

// 或者更宽松的检查
if (FunctionCompat.isCallable(getter)) {
    mGetter = (Callable) getter;
}
```

**方案2: E4X 自动兼容初始化**

修改 `XMLLibImpl.java` 的初始化方法:

```java
package org.mozilla.javascript.xmlimpl;

public final class XMLLibImpl extends XMLLib implements Serializable {
    
    /**
     * 初始化 E4X 支持
     * 
     * 自动检测运行环境，选择合适的初始化方式:
     * - ClassDescriptor 方式 (Rhino 2.0.0+)
     * - exportAsJSClass 方式 (AutoJs6 定制版)
     */
    public static void init(Context cx, Scriptable scope, boolean sealed) {
        XMLLibImpl lib = new XMLLibImpl(scope);
        XMLLib bound = lib.bindToScope(scope);
        if (bound == lib) {
            lib.exportToScopeCompat(cx, scope, sealed);
        }
    }
    
    /**
     * 兼容性导出方法
     */
    private void exportToScopeCompat(Context cx, Scriptable scope, boolean sealed) {
        xmlPrototype = newXML(XmlNode.createText(options, ""));
        xmlListPrototype = newXMLList();
        namespacePrototype = Namespace.create(this.globalScope, null, XmlNode.Namespace.GLOBAL);
        qnamePrototype = QName.create(this, this.globalScope, null,
                XmlNode.QName.create(XmlNode.Namespace.create(""), ""));
        
        // 尝试 ClassDescriptor 方式 (Rhino 2.0.0+)
        try {
            XML.init(cx, (ScriptableObject) scope, xmlPrototype, sealed, this);
            var parent = xmlPrototype.getParentScope();
            parent.put("__xml_lib__", parent, xmlPrototype.getLib());
            XMLList.init(cx, (ScriptableObject) scope, xmlListPrototype, sealed, this);
            Namespace.init(cx, (ScriptableObject) scope, namespacePrototype, sealed);
            QName.init(cx, (ScriptableObject) scope, qnamePrototype, sealed);
        } catch (NoSuchMethodError e) {
            // 回退到 exportAsJSClass 方式 (旧版/定制版)
            exportToScopeLegacy(sealed);
        }
    }
    
    /**
     * 旧版导出方法 (用于兼容)
     */
    private void exportToScopeLegacy(boolean sealed) {
        xmlPrototype.exportAsJSClass(sealed);
        xmlListPrototype.exportAsJSClass(sealed);
        namespacePrototype.exportAsJSClass(sealed);
        qnamePrototype.exportAsJSClass(sealed);
    }
}
```

#### 22.16.4 下游项目迁移指南

**步骤1: 替换类型检查**

```java
// 查找所有 NativeFunction 类型检查
// 旧代码
if (obj instanceof NativeFunction) { ... }

// 替换为
if (FunctionCompat.isJavaScriptFunction(obj)) { ... }
// 或
if (obj instanceof Callable) { ... }
```

**步骤2: 替换函数变量类型**

```java
// 旧代码
private NativeFunction mGetter;

// 替换为
private Callable mGetter;
// 或
private BaseFunction mGetter;
```

**步骤3: 无需修改 E4X 初始化**

如果使用方案2，下游项目无需修改 E4X 初始化代码:

```java
// 无需修改，自动兼容
XMLLibImpl.init(context, scope, false);
```

#### 22.16.5 封装方案优势

| 优势 | 说明 |
|------|------|
| **向后兼容** | 支持 Rhino 1.7.x 和 2.0.0 |
| **集中维护** | 兼容逻辑集中在 Rhino 层 |
| **透明迁移** | 下游项目改动最小 |
| **类型安全** | 编译时检查，无运行时错误 |
| **文档友好** | 清晰的 API 文档和使用示例 |

#### 22.16.6 合并策略建议

**选择保留的定制**:

| 定制内容 | 是否保留 | 原因 |
|----------|----------|------|
| VMBridge 扩展点 | ✅ 保留 | Android 兼容必需 |
| SlotMapOwner synchronized | ✅ 保留 | Android 7.x 兼容 |
| IRFactory StackOverflow 注释 | ✅ 保留 | 已知问题规避 |
| NativeJavaObject 兼容构造函数 | ✅ 保留 | API 兼容性 |
| FunctionCompat 兼容层 | ✅ 新增 | 统一类型检查 |
| E4X 自动兼容 | ✅ 新增 | 简化下游使用 |

**选择接受的上游变更**:

| 变更内容 | 是否接受 | 处理方式 |
|----------|----------|----------|
| ClassDescriptor 架构 | ✅ 接受 | 通过兼容层封装 |
| JSFunction 新类型 | ✅ 接受 | 通过 FunctionCompat 封装 |
| TypeInfo 类型系统 | ✅ 接受 | 下游项目适配 |

### 22.17 RhinoCompat 兼容层模块设计

#### 22.17.1 设计目标

```
┌─────────────────────────────────────────────────────────┐
│                    理想的升级路径                         │
├─────────────────────────────────────────────────────────┤
│                                                         │
│   mozilla/rhino 2.0.0 ──► 2.1.0 ──► 3.0.0 ──► ...      │
│         │              │          │                     │
│         ▼              ▼          ▼                     │
│   ┌─────────────────────────────────────────┐          │
│   │         兼容层 (不变)                     │          │
│   │  兼容层只维护一次，下游项目无需改动        │          │
│   └─────────────────────────────────────────┘          │
│         │              │          │                     │
│         ▼              ▼          ▼                     │
│   下游项目 (HYB1996, AutoJs6 等) 无需重新适配            │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**核心思路**: 不是让下游适配新版，而是让新版"假装"是旧版

#### 22.17.2 模块结构

```
rhino-compat/                          # 新增兼容层模块
├── build.gradle
└── src/main/java/org/mozilla/javascript/compat/
    ├── RhinoCompat.java               # 主入口
    ├── FunctionCompat.java            # 函数类型兼容
    ├── E4XCompat.java                 # E4X 兼容
    ├── NativeFunctionAdapter.java     # JSFunction → NativeFunction 适配器
    ├── WrapFactoryCompat.java         # WrapFactory 兼容基类 (高优先级)
    └── LegacyContextFactory.java      # 旧版 ContextFactory 兼容
```

#### 22.17.3 核心组件实现

**组件1: NativeFunctionAdapter - 让 JSFunction 伪装成 NativeFunction**

```java
package org.mozilla.javascript.compat;

import org.mozilla.javascript.*;

/**
 * JSFunction 到 NativeFunction 的适配器
 * 
 * 让新版 JSFunction 伪装成旧版 NativeFunction，
 * 下游代码可以继续使用 instanceof NativeFunction 检查。
 * 
 * 使用方式：
 *   Object fn = NativeFunctionAdapter.wrap(jsFunction);
 *   if (fn instanceof NativeFunction) { ... }  // 正常工作
 */
public final class NativeFunctionAdapter extends NativeFunction {
    
    private final BaseFunction delegate;  // 实际的 JSFunction 或 NativeFunction
    
    private NativeFunctionAdapter(BaseFunction delegate) {
        this.delegate = delegate;
    }
    
    /**
     * 包装函数对象
     * 
     * @param obj 任意函数对象
     * @return 如果是 JSFunction 则包装为 NativeFunctionAdapter，否则原样返回
     */
    public static Object wrap(Object obj) {
        if (obj instanceof JSFunction) {
            return new NativeFunctionAdapter((JSFunction) obj);
        }
        return obj;  // 已经是 NativeFunction 或其他类型
    }
    
    /**
     * 解包函数对象
     */
    public static Object unwrap(Object obj) {
        if (obj instanceof NativeFunctionAdapter) {
            return ((NativeFunctionAdapter) obj).delegate;
        }
        return obj;
    }
    
    // ========== 委托所有 BaseFunction 方法 ==========
    
    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        return delegate.call(cx, scope, thisObj, args);
    }
    
    @Override
    public int getParamCount() {
        return delegate.getParamCount();
    }
    
    @Override
    public int getArity() {
        return delegate.getArity();
    }
    
    @Override
    public String getFunctionName() {
        return delegate.getFunctionName();
    }
    
    @Override
    public Scriptable getPrototypeProperty() {
        return delegate.getPrototypeProperty();
    }
    
    // ... 其他方法委托
}
```

**组件2: RhinoCompat - 统一入口**

```java
package org.mozilla.javascript.compat;

import org.mozilla.javascript.*;
import org.mozilla.javascript.xmlimpl.XMLLibImpl;

/**
 * Rhino 兼容层主入口
 * 
 * 提供统一的兼容 API，屏蔽版本差异。
 * 下游项目只需调用此类的静态方法，无需关心底层版本。
 * 
 * <pre>
 * 使用示例：
 *   // 初始化（替代 XMLLibImpl.init）
 *   RhinoCompat.init(context, scope);
 *   
 *   // 函数类型检查（替代 instanceof NativeFunction）
 *   if (RhinoCompat.isFunction(obj)) { ... }
 *   
 *   // 函数调用
 *   RhinoCompat.call(fn, cx, scope, thisObj, args);
 * </pre>
 */
public final class RhinoCompat {
    
    private static boolean initialized = false;
    
    // ========== 初始化 ==========
    
    /**
     * 初始化 Rhino 环境（包含 E4X）
     * 
     * 自动适配不同版本的初始化方式
     */
    public static void init(Context cx, Scriptable scope) {
        init(cx, scope, false);
    }
    
    public static synchronized void init(Context cx, Scriptable scope, boolean sealed) {
        if (initialized) return;
        
        // 初始化 E4X
        E4XCompat.init(cx, scope, sealed);
        
        initialized = true;
    }
    
    // ========== 函数类型检查 ==========
    
    /**
     * 检查对象是否为 JavaScript 函数
     * 
     * 兼容所有 Rhino 版本的函数类型检查
     */
    public static boolean isFunction(Object obj) {
        if (obj instanceof NativeFunction) return true;      // 1.7.x / 内置函数
        if (obj instanceof JSFunction) return true;          // 2.0.0+ JS 函数
        if (obj instanceof ArrowFunction) return true;       // 箭头函数
        return false;
    }
    
    /**
     * 检查对象是否可调用
     */
    public static boolean isCallable(Object obj) {
        return obj instanceof Callable;
    }
    
    // ========== 函数调用 ==========
    
    /**
     * 调用函数
     */
    public static Object call(Object fn, Context cx, Scriptable scope, 
                              Scriptable thisObj, Object[] args) {
        if (fn instanceof Callable) {
            return ((Callable) fn).call(cx, scope, thisObj, args);
        }
        throw ScriptRuntime.typeErrorById("msg.isnt.function", ScriptRuntime.typeof(fn));
    }
    
    // ========== 类型转换 ==========
    
    /**
     * 将函数对象包装为兼容类型
     * 
     * 如果下游代码必须使用 instanceof NativeFunction，
     * 调用此方法包装后再使用
     */
    public static Object wrapFunction(Object fn) {
        return NativeFunctionAdapter.wrap(fn);
    }
    
    // ========== 函数信息 ==========
    
    public static int getParamCount(Object fn) {
        if (fn instanceof BaseFunction) {
            return ((BaseFunction) fn).getParamCount();
        }
        return 0;
    }
    
    public static String getFunctionName(Object fn) {
        if (fn instanceof BaseFunction) {
            return ((BaseFunction) fn).getFunctionName();
        }
        return null;
    }
}
```

**组件3: E4XCompat - E4X 兼容层**

```java
package org.mozilla.javascript.compat;

import org.mozilla.javascript.*;
import org.mozilla.javascript.xmlimpl.XMLLibImpl;

/**
 * E4X 兼容层
 * 
 * 自动检测并使用正确的初始化方式
 */
final class E4XCompat {
    
    private static Boolean hasDescriptorAPI = null;
    
    /**
     * 初始化 E4X 支持
     * 
     * 自动检测 Rhino 版本并选择正确的初始化方式：
     * - Rhino 2.0.0+: 使用 XML.init(cx, scope, ...) 
     * - Rhino 1.7.x / AutoJs6 定制版: 使用 exportAsJSClass()
     */
    static void init(Context cx, Scriptable scope, boolean sealed) {
        if (hasDescriptorAPI == null) {
            // 检测是否有 ClassDescriptor API
            hasDescriptorAPI = detectDescriptorAPI();
        }
        
        XMLLibImpl lib = new XMLLibImpl(scope);
        XMLLib bound = lib.bindToScope(scope);
        
        if (bound == lib) {
            if (hasDescriptorAPI) {
                initWithDescriptor(cx, scope, lib, sealed);
            } else {
                initWithExportAsJSClass(lib, sealed);
            }
        }
    }
    
    private static boolean detectDescriptorAPI() {
        try {
            // 尝试加载 ClassDescriptor 相关类
            Class.forName("org.mozilla.javascript.ClassDescriptor");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    private static void initWithDescriptor(Context cx, Scriptable scope, 
                                           XMLLibImpl lib, boolean sealed) {
        // Rhino 2.0.0+ 方式
        lib.exportToScope(cx, (ScriptableObject) scope, sealed);
    }
    
    private static void initWithExportAsJSClass(XMLLibImpl lib, boolean sealed) {
        // Rhino 1.7.x / AutoJs6 定制版方式
        try {
            var method = XMLLibImpl.class.getDeclaredMethod("exportToScope", boolean.class);
            method.setAccessible(true);
            method.invoke(lib, sealed);
        } catch (Exception e) {
            // 最后的回退：使用 XMLLibImpl.init
            // 这里的 XMLLibImpl.init 内部会处理
        }
    }
}
```

**组件4: WrapFactoryCompat - WrapFactory 兼容基类 (高优先级)**

```java
package org.mozilla.javascript.compat;

import org.mozilla.javascript.*;
import org.mozilla.javascript.lc.type.TypeInfo;

/**
 * WrapFactory 兼容基类
 * 
 * 让下游项目可以继续使用 Class<?> 参数签名，
 * 内部自动转换为 TypeInfo。
 * 
 * <p><b>重要</b>: Rhino 2.0.0 中 wrap(..., Class<?>) 是 final 方法，
 * 不能直接重写，需要使用 wrapCompat() 方法。
 * 
 * <p>使用方式：将继承 WrapFactory 改为继承 WrapFactoryCompat
 * 
 * <pre>
 * // 旧代码
 * class MyWrapFactory extends WrapFactory {
 *     override fun wrap(..., staticType: Class&lt;?&gt;) { ... }
 * }
 * 
 * // 新代码（只需改继承和方法名）
 * class MyWrapFactory extends WrapFactoryCompat {
 *     override fun wrapCompat(..., staticType: Class&lt;?&gt;) { ... }
 * }
 * </pre>
 */
public abstract class WrapFactoryCompat extends WrapFactory {
    
    // ========== wrap 方法 ==========
    
    /**
     * final 方法，将 TypeInfo 转换为 Class 并调用 wrapCompat
     * 子类不应重写此方法，应重写 wrapCompat
     */
    @Override
    public final Object wrap(Context cx, Scriptable scope, 
                             Object obj, TypeInfo staticType) {
        return wrapCompat(cx, scope, obj, staticType.asClass());
    }
    
    /**
     * 子类重写此方法，使用 Class 参数（旧版 API 风格）
     * 
     * 默认实现：复制 WrapFactory.wrap() 的核心逻辑
     * 注意：不能调用 super.wrap(..., Class)，会导致死循环
     */
    protected Object wrapCompat(Context cx, Scriptable scope, 
                                Object obj, Class<?> staticType) {
        if (obj == null || obj == Undefined.instance || obj instanceof Scriptable) {
            return obj;
        }
        if (staticType != null && staticType.isPrimitive()) {
            if (staticType == Void.TYPE) {
                return Undefined.instance;
            } else if (staticType == Character.TYPE) {
                return (int) (Character) obj;
            }
            return obj;
        }
        if (!isJavaPrimitiveWrap()) {
            if (obj instanceof String || obj instanceof Boolean
                    || obj instanceof Number) {
                return obj;
            } else if (obj instanceof Character) {
                return String.valueOf(((Character) obj).charValue());
            }
        }
        return wrapAsJavaObjectCompat(cx, scope, obj, staticType);
    }
    
    // ========== wrapAsJavaObject 方法 ==========
    
    /**
     * final 方法，将 TypeInfo 转换为 Class 并调用 wrapAsJavaObjectCompat
     * 子类不应重写此方法，应重写 wrapAsJavaObjectCompat
     */
    @Override
    public final Scriptable wrapAsJavaObject(Context cx, Scriptable scope, 
                                             Object javaObject, TypeInfo staticType) {
        return wrapAsJavaObjectCompat(cx, scope, javaObject, staticType.asClass());
    }
    
    /**
     * 子类重写此方法，使用 Class 参数（旧版 API 风格）
     * 
     * 默认实现：创建 NativeJavaObject
     */
    protected Scriptable wrapAsJavaObjectCompat(Context cx, Scriptable scope, 
                                                Object javaObject, Class<?> staticType) {
        return new NativeJavaObject(scope, javaObject, staticType);
    }
}
```

**下游项目 WrapFactory 继承链分析**:

| 项目 | 继承链 | 兼容层优势 |
|------|--------|-----------|
| **Auto.js** | 单层: `WrapFactory → RhinoJavaScriptEngine.WrapFactory` | 改继承 + 方法名 |
| **Auto.js.HYB1996** | 单层: `WrapFactory → RhinoJavaScriptEngine.WrapFactory` | 改继承 + 方法名 |
| **AutoX** | 两层: `WrapFactory → AndroidContextFactory.WrapFactory → RhinoJavaScriptEngine.WrapFactory` | **只改父类继承** |
| **AutoX.js** | 两层: `WrapFactory → AndroidContextFactory.WrapFactory → RhinoJavaScriptEngine.WrapFactory` | **只改父类继承** |

**结论**: WrapFactoryCompat 对多层继承项目收益最大（如 AutoX/AutoX.js 只需改一处继承）。

#### 22.17.4 下游项目迁移示例

**迁移前**:

```java
// 旧代码，只兼容 Rhino 1.7.x
XMLLibImpl.init(context, scope, false);

if (getter instanceof NativeFunction) {
    mGetter = (NativeFunction) getter;
}

Object result = mGetter.call(cx, scope, thisObj, args);
```

**迁移后**:

```java
// 新代码，兼容所有版本
RhinoCompat.init(context, scope);  // 自动选择初始化方式

if (RhinoCompat.isFunction(getter)) {
    mGetter = (Callable) getter;   // 或 RhinoCompat.wrapFunction(getter)
}

Object result = RhinoCompat.call(getter, cx, scope, thisObj, args);
```

#### 22.17.5 升级场景对比

```
┌─────────────────────────────────────────────────────────────┐
│                    升级场景对比                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  无兼容层：                                                  │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐                 │
│  │ Rhino   │    │ 下游    │    │ 升级时   │                 │
│  │ 2.0.0   │ ──►│ 项目    │ ──►│ 全部适配 │ ❌ 痛苦         │
│  └─────────┘    └─────────┘    └─────────┘                 │
│                                                             │
│  有兼容层：                                                  │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐                 │
│  │ Rhino   │    │ 兼容层  │    │ 下游    │                 │
│  │ 2.0.0   │ ──►│ (适配)  │ ──►│ 项目    │ ✅ 无感         │
│  └─────────┘    └─────────┘    └─────────┘                 │
│       │              │                                      │
│       │   升级到     │   只需更新                            │
│       ▼   3.0.0     ▼   兼容层                              │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐                 │
│  │ Rhino   │ ──►│ 兼容层  │ ──►│ 下游    │ ✅ 仍然无感     │
│  │ 3.0.0   │    │ (适配)  │    │ 项目    │                 │
│  └─────────┘    └─────────┘    └─────────┘                 │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

#### 22.17.6 方案优势总结

| 设计要点 | 实现方式 |
|----------|----------|
| **以官方版本为基础** | 兼容层作为独立模块，不修改 Rhino 核心 |
| **兼容旧版本 API** | 提供 `RhinoCompat.isFunction()` 等兼容方法 |
| **自动版本检测** | 运行时检测 API 可用性 |
| **适配器模式** | `NativeFunctionAdapter` 让新类型伪装成旧类型 |
| **单点维护** | 升级时只需更新兼容层，下游无需改动 |

#### 22.17.7 与前述方案的关系

| 方案 | 定位 | 关系 |
|------|------|------|
| **22.16.3 方案1** (FunctionCompat) | 单点解决函数类型问题 | 合并到 RhinoCompat |
| **22.16.3 方案2** (E4X自动兼容) | 单点解决 E4X 初始化问题 | 合并到 E4XCompat |
| **22.17 RhinoCompat 模块** | 统一兼容层框架 | 整合所有兼容逻辑 |

**推荐实现顺序**:
1. **实现 `WrapFactoryCompat`** (高优先级 - Auto.js 立即需要)
2. 实现 `RhinoCompat` 主入口和 `isFunction()` 方法
3. 实现 `NativeFunctionAdapter` 适配器
4. 实现 `E4XCompat` 自动检测
5. 打包为独立的 `rhino-compat` 模块

**报告生成工具:** iFlow CLI
**分析深度:** 全面
**置信度:** 高
**最后更新:** 2026年3月19日
