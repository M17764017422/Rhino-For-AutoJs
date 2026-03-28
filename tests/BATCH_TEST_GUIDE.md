# ES2022 Class Test262 分批测试方案

## 一、分批测试概述

根据测试分类清单，将 ES2022ClassTest262Test 的 ~5000+ 测试分为 **9 个批次**，每个批次可独立运行，避免内存溢出和崩溃。

## 二、批次定义

### Batch 0: Negative Tests (优先级: 特殊)

**描述**: 负面测试 - 期望抛出错误的测试用例
**预计测试数**: ~460
**测试类**: `ES2022ClassNegativeTest262Test`

**测试分类**:

* **Parse Phase**: 解析阶段错误（词法/语法分析）

* **Early Phase**: 早期错误（编译时语义检查）

* **Runtime Phase**: 运行时错误（TDZ、类型错误等）

**负面测试识别规则**:

```yaml
# Test262 元数据格式
negative:
  phase: parse | early | runtime | resolution
  type: SyntaxError | ReferenceError | TypeError | RangeError
```

**Phase 类型说明**:

| Phase        | 含义     | 错误发生时机   |
| ------------ | ------ | -------- |
| `parse`      | 解析阶段错误 | 词法/语法分析时 |
| `early`      | 早期错误   | 编译时语义检查  |
| `runtime`    | 运行时错误  | 脚本执行时    |
| `resolution` | 模块解析错误 | 模块加载时    |

**测试文件模式**:

* `expected-early-error*.js` - 预期早期错误

* `invalid-syntax*.js` - 无效语法

* `syntax-error*.js` - 语法错误

* `*-duplicate-privatenames.js` - 重复私有字段名

* `super-before-super*.js` - super 调用顺序错误

* `use-before-*.js` - TDZ 访问

* `brand-check*.js` - 品牌检查

**运行命令**:

```powershell
.\tests\run-class-batch-tests.ps1 -Batch negative -Timeout 20
```

***

### Batch 1: ES2022 Core (优先级: 最高)

**描述**: ES2022 Class 核心特性测试
**预计测试数**: ~500
**测试类**: `ES2022ClassCoreTest262Test`

**测试特性**:

* `class-fields-public` - 公有字段

* `class-fields-private` - 私有字段

* `class-methods-private` - 私有方法

* `class-static-fields-public` - 静态公有字段

* `class-static-fields-private` - 静态私有字段

* `class-static-methods-private` - 静态私有方法

* `class-static-block` - 静态初始化块

**运行命令**:

```powershell
.\tests\run-class-batch-tests.ps1 -Batch core -Timeout 30
```

***

### Batch 2: ES6 Core (优先级: 高)

**描述**: ES6 Class 核心特性测试
**预计测试数**: ~100
**测试类**: `ES2022ClassES6CoreTest262Test`

**测试目录**:

* `language/statements/class/subclass-builtins/`

* `language/statements/class/super/`

* `language/statements/class/definition/`

**运行命令**:

```powershell
.\tests\run-class-batch-tests.ps1 -Batch es6 -Timeout 15
```

***

### Batch 3: Elements (优先级: 高)

**描述**: Class elements 目录测试
**预计测试数**: ~500
**测试类**: `ES2022ClassElementsTest262Test`

**测试目录**:

* `language/expressions/class/elements/`

* `language/statements/class/elements/`

**排除**: 已在 Batch 1 中测试的核心特性文件

**运行命令**:

```powershell
.\tests\run-class-batch-tests.ps1 -Batch elements -Timeout 30
```

***

### Batch 4: Destructuring (优先级: 中)

**描述**: 解构赋值测试 (ES6 特性)
**预计测试数**: 1281
**测试类**: `ES2022ClassDestructuringTest262Test`
**分片**: 4 片 (每片 ~320 测试)

**分片说明**:

| 分片 | 测试范围                                                | 预计测试数 |
| -- | --------------------------------------------------- | ----- |
| 1  | `meth-*.js`, `meth-static-*.js`                     | ~320  |
| 2  | `gen-meth-*.js`, `gen-meth-static-*.js`             | ~320  |
| 3  | `async-gen-meth-*.js`, `async-gen-meth-static-*.js` | ~320  |
| 4  | `private-*.js`, `async-private-*.js`                | ~320  |

**运行命令**:

```powershell
# 运行整个批次
.\tests\run-class-batch-tests.ps1 -Batch dstr -Timeout 60

# 运行特定分片 (需要修改测试类支持分片)
# .\tests\run-class-batch-tests.ps1 -Batch dstr -Shard 1 -Timeout 20
```

***

### Batch 5: Generators (优先级: 中)

**描述**: 生成器测试 (ES6 特性)
**预计测试数**: 1156
**测试类**: `ES2022ClassGeneratorsTest262Test`
**分片**: 4 片 (每片 ~290 测试)

**测试目录**:

* `language/statements/class/gen-method/`

* `language/statements/class/gen-method-static/`

* `language/statements/class/dstr/gen-meth-*.js`

**运行命令**:

```powershell
.\tests\run-class-batch-tests.ps1 -Batch gen -Timeout 60
```

***

### Batch 6: Async (优先级: 中)

**描述**: 异步测试 (ES2017/ES2018 特性)
**预计测试数**: ~700
**测试类**: `ES2022ClassAsyncTest262Test`
**分片**: 2 片 (每片 ~350 测试)

**分片说明**:

| 分片 | 测试范围                                            | 特性标签              |
| -- | ----------------------------------------------- | ----------------- |
| 1  | `async-method/`, `async-method-static/`         | `async-functions` |
| 2  | `async-gen-method/`, `async-gen-method-static/` | `async-iteration` |

**运行命令**:

```powershell
.\tests\run-class-batch-tests.ps1 -Batch async -Timeout 45
```

***

### Batch 7: Others (优先级: 低)

**描述**: 其他测试
**预计测试数**: ~500
**测试类**: `ES2022ClassOthersTest262Test`

**测试范围**:

* `computed-property-names` (cpn-*.js)

* `ident-name-method-def-*.js` (标识符转义)

* `scope-*.js` (作用域)

* `params-dflt-*.js` (参数默认值)

* `arguments/` 目录

* `strict-mode/` 目录

* `method/`, `method-static/` 目录

* `name-binding/` 目录

**运行命令**:

```powershell
.\tests\run-class-batch-tests.ps1 -Batch others -Timeout 30
```

***

### Batch 8: ES2023+ Features (优先级: 最低)

**描述**: ES2023+ 特性测试
**预计测试数**: 16
**测试类**: `ES2023ClassTest262Test`

**测试特性**:

* `decorators` - 装饰器 (14 测试)

* `auto-accessors` - 自动存取器 (2 测试)

**运行命令**:

```powershell
.\tests\run-class-batch-tests.ps1 -Batch es2023 -Timeout 10
```

***

## 三、运行全部批次

```powershell
# 按优先级顺序运行所有批次
.\tests\run-class-batch-tests.ps1 -Batch all -Timeout 30

# 仅列出将要运行的测试 (不实际执行)
.\tests\run-class-batch-tests.ps1 -Batch core -ListOnly
```

***

## 四、Gradle 直接运行

如果不使用 PowerShell 脚本，可以直接使用 Gradle：

```powershell
# 设置环境变量
$env:JAVA_HOME = "F:\AIDE\jdk-21.0.9+10"
$env:GRADLE_USER_HOME = "F:\AIDE\.gradle"
$env:TEMP = "F:\AIDE\tmp"
$env:TMP = "F:\AIDE\tmp"

# 运行特定批次
.\gradlew.bat :tests:test --tests "org.mozilla.javascript.tests.ES2022ClassCoreTest262Test" --no-daemon

# 运行完整测试 (原有方式，可能崩溃)
# .\gradlew.bat :tests:test262Class --no-daemon
```

***

## 五、测试文件统计

| 批次                                  | 预计测试数 | 优先级 | 预计耗时   |
| ----------------------------------- | ----- | --- | ------ |
| Batch 0: Negative                   | ~460  | 特殊  | ~5 分钟  |
| Batch 1: ES2022 Core                | ~500  | 最高  | ~10 分钟 |
| Batch 2: ES6 Core                   | ~100  | 高   | ~3 分钟  |
| Batch 3: Elements                   | ~500  | 高   | ~10 分钟 |
| Batch 4: Destructuring              | 1281  | 中   | ~25 分钟 |
| Batch 5: Generators                 | 1156  | 中   | ~20 分钟 |
| Batch 6: Async                      | ~700  | 中   | ~15 分钟 |
| Batch 7: Others                     | ~500  | 低   | ~10 分钟 |
| Batch 8: ES2023+                    | 16    | 最低  | ~1 分钟  |
| **总计** | **~5213** | - | **~99 分钟** |       |     |        |

***

## 六、负面测试详细分类

### 负面测试判定逻辑

```java
// 测试框架中的负面测试识别
String expectedError = null;
boolean isEarly = false;
if (metadata.containsKey("negative")) {
    Map<String, String> negative = (Map<String, String>) metadata.get("negative");
    expectedError = negative.get("type");
    isEarly = "early".equals(negative.get("phase"));
}
```

### 负面测试结果分析

| 错误类型             | Phase       | 典型场景          |
| ---------------- | ----------- | ------------- |
| `SyntaxError`    | parse/early | 重复私有字段名、非法标识符 |
| `ReferenceError` | runtime     | TDZ 访问、未定义变量  |
| `TypeError`      | runtime     | 类型错误、非法操作     |
| `RangeError`     | runtime     | 数值超出范围        |

### 负面测试与实现问题的区分

| 场景                 | 判定   | 说明      |
| ------------------ | ---- | ------- |
| 测试期望错误，Rhino 抛出错误  | ✅ 通过 | 负面测试通过  |
| 测试期望错误，Rhino 未抛出   | ❌ 失败 | 实现缺失检查  |
| 测试不期望错误，Rhino 抛出错误 | ❌ 失败 | 实现有 bug |
| 测试不期望错误，Rhino 未抛出  | ✅ 通过 | 正面测试通过  |

***

## 六、内存配置建议

为避免内存溢出，建议在 `tests/build.gradle` 中配置：

```groovy
test {
    maxHeapSize = "4g"
    jvmArgs = [
        "-XX:+UseParallelGC",
        "-Xms2g",
        "-Xmx4g"
    ]
    forkEvery = 1
}
```

***

## 七、故障排除

### 问题: StackOverflowError

**原因**: 深度递归测试（如生成器解构）

**解决方案**:

1. 增加栈内存: `-Xss4m`

2. 使用分片测试，减少单次测试量

### 问题: OutOfMemoryError

**原因**: 测试用例过多，内存不足

**解决方案**:

1. 使用分批测试

2. 增加 JVM 堆内存

3. 设置 `forkEvery = 1`

### 问题: 测试超时

**原因**: 测试运行时间过长

**解决方案**:

1. 增加超时时间

2. 使用分片减少单次测试量

***

## 八、文件清单

| 文件                                                            | 说明                  |
| ------------------------------------------------------------- | ------------------- |
| `tests/testsrc/test262-class-batch.properties`                | 分批测试配置文件            |
| `tests/src/test/java/.../ES2022ClassCoreTest262Test.java`     | Batch 1 测试类         |
| `tests/src/test/java/.../ES2022ClassNegativeTest262Test.java` | Batch 0 负面测试类 (待创建) |
| `tests/run-class-batch-tests.ps1`                             | PowerShell 运行脚本     |
| `tests/BATCH_TEST_GUIDE.md`                                   | 本文档                 |
| `ES2022_CLASS_FIX_PROGRESS.md`                                | 修复进度跟踪              |

***

## 九、失败测试分类体系

### 分类标准

基于错误信息和测试元数据，将失败测试分为以下类别：

| 类别                   | 描述                   | 处理策略         |
| -------------------- | -------------------- | ------------ |
| **NEGATIVE_TEST**    | 负面测试，期望抛出错误          | 单独统计，不计入实现问题 |
| **RUNTIME_ERROR**    | 运行时错误（私有字段、方法、super） | 优先修复         |
| **SYNTAX_ERROR**     | 语法解析错误               | 高优先级修复       |
| **BYTECODE_ERROR**   | 字节码验证错误（仅编译模式）       | 中优先级修复       |
| **CODEGEN_ERROR**    | 内部编译器错误              | 中优先级修复       |
| **EXPECTED_FEATURE** | 缺少特性支持               | 评估后决定        |
| **OTHER**            | 其他原因                 | 按具体情况处理      |

### 分类脚本

```powershell
# 分析失败测试并分类
.\tests\analyze-test-failures.ps1 -ReportDir tests/build/reports/tests/test
```

