# Rhino

Rhino is an open source (MPL 2.0) JavaScript engine, implemented in Java 11. The build system used is gradle, via the
gradle wrapper (`./gradlew`). There are no external dependencies, except for JUnit for unit tests.

## Useful commands

1. Build: `./gradlew build`
2. Run tests: `./gradlew test`
3. Format code: `./gradlew spotlessApply`
4. Checks (tests, formatting): `./gradlew check`

## Troubleshooting

### No JavaScript compiler available

如果测试或运行时出现 `IllegalStateException: No JavaScript compiler available`：

**原因**：`Interpreter` 类静态初始化失败，通常是因为 Token 定义问题。

**检查方法**：
```java
// 测试 Codegen 和 Interpreter 是否能正常加载
Class.forName("org.mozilla.javascript.optimizer.Codegen").getDeclaredConstructor().newInstance();
Class.forName("org.mozilla.javascript.Interpreter").getDeclaredConstructor().newInstance();
```

**常见原因**：新增的字节码指令 Token 值大于 `LAST_BYTECODE_TOKEN`，导致 `instructionObjs` 数组越界。

**修复**：确保所有字节码指令 Token（如 `NEW_CLASS`, `GET_PRIVATE_FIELD` 等）定义在 `LAST_BYTECODE_TOKEN` 之前。

### Node.js Heap Out of Memory

如果构建时出现 `FATAL ERROR: Reached heap limit Allocation failed - JavaScript heap out of memory`：

**方案 1：增加 Node.js 堆内存限制**

```powershell
$env:NODE_OPTIONS="--max-old-space-size=4096"
```

**方案 3：分步构建，减少内存压力**

```powershell
# 设置环境变量
$env:JAVA_HOME = "F:\AIDE\jdk-21.0.9+10"
$env:GRADLE_USER_HOME = "F:\AIDE\.gradle"
$env:TEMP = "F:\AIDE\tmp"
$env:TMP = "F:\AIDE\tmp"
$env:NODE_OPTIONS = "--max-old-space-size=4096"

# 先格式化代码
.\gradlew.bat spotlessApply --no-daemon

# 再编译
.\gradlew.bat compileJava compileTestJava --no-daemon

# 最后测试
.\gradlew.bat test --no-daemon
```

## Rules and code style

- Important: try to follow the existing code style and patterns.
- Be restrained in regard to writing code comments.
- Always add unit tests for any new feature or bug fixes. They should go either in `rhino` or `tests`; search for
  existing tests to make a decision on a case-by-case basis.
- New test classes should be written using JUnit 5. Migrating existing tests from JUnit 4 to JUnit 5 is not a goal
  though, unless explicitly requested.
- Code style is enforced via spotless. After every change, reformat the code.

## Code organization

The code base is organized in multiple modules. Most changes will go into the `rhino` or `tests` modules. Refer to
README.md for the full list.

- `rhino`: The primary codebase necessary and sufficient to run JavaScript code
- `rhino-tools`: Contains the shell, debugger, and the "Global" object, which many tests and other Rhino-based tools use
- `tests`: The tests that depend on all of Rhino and also the external tests, including the Mozilla legacy test scripts
  and the test262 tests

## Architecture

Rhino follows a classical architecture:

- [TokenStream](rhino/src/main/java/org/mozilla/javascript/TokenStream.java) is the lexer
- [Parser](rhino/src/main/java/org/mozilla/javascript/Parser.java) is the parser, which produces an AST modeled by the
  subclasses of [AstNode](rhino/src/main/java/org/mozilla/javascript/ast/AstNode.java)
- the [IRFactory](rhino/src/main/java/org/mozilla/javascript/IRFactory.java) will generate a tree IR, modeled
  by [Node](rhino/src/main/java/org/mozilla/javascript/Node.java)
- there are two backends:
    - one which generates java classes, in [Codegen](rhino/src/main/java/org/mozilla/javascript/optimizer/Codegen.java);
    - and one which generates a bytecode for [Interpreter](rhino/src/main/java/org/mozilla/javascript/Interpreter.java),
      in [CodeGenerator](rhino/src/main/java/org/mozilla/javascript/CodeGenerator.java).

[ScriptRuntime](rhino/src/main/java/org/mozilla/javascript/ScriptRuntime.java) is the main class for all runtime
methods, shared between compiled classes and interpreter.

Builtins such as `Object` or `Array` are implemented in
[NativeObject](rhino/src/main/java/org/mozilla/javascript/NativeObject.java),
[NativeArray](rhino/src/main/java/org/mozilla/javascript/NativeArray.java), etc.

## Branch Management

This repository maintains 3 branches for different purposes:

| Branch | Tracks | Purpose |
|--------|--------|---------|
| `main` | `origin/main` | Primary branch with Android compatibility fixes + latest mozilla features |
| `autojs6` | `autojs6/master` | AutoJs6 version (SuperMonster003/Rhino-For-AutoJs6) |
| `mozilla` | `official/master` | Mozilla official rhino (mozilla/rhino) |

### Remote Repositories

| Remote | URL |
|--------|-----|
| `origin` | https://github.com/M17764017422/Rhino-For-AutoJs |
| `autojs6` | https://github.com/SuperMonster003/Rhino-For-AutoJs6 |
| `official` | https://github.com/mozilla/rhino |

### Sync Commands

```powershell
# Sync autojs6 branch (fast-forward)
git checkout autojs6
git fetch autojs6
git merge autojs6/master --ff-only
git push origin autojs6

# Sync mozilla branch (merge)
git checkout mozilla
git fetch official
git merge official/master --no-ff
git push origin mozilla

# Update main branch (merge mozilla changes)
git checkout main
git merge mozilla --no-ff
git push origin main
```

### Main Branch Features

The `main` branch includes:
- Android D8 compatibility fixes (JLine FFM exclusion, META-INF services handling)
- AutoJs6 compatibility features
- Latest mozilla/rhino updates

## CI Optimization Plans

### Current Implementation (Plan A)

Tests are split into:
- `unit-tests`: Fast unit tests (~3 min), excludes Test262
- `test262`: Test262 suite independently (~12 min)
- `matrix-test`: Multi-JDK verification without Test262

Run locally:
```bash
./gradlew :tests:testWithoutTest262  # Unit tests only
./gradlew :tests:test262              # Test262 only
```

### Backup Plan B: Module-Level Split

If faster feedback is needed, implement finer-grained splitting:

```yaml
jobs:
  build:               # Compile check (~2 min)
    ./gradlew compileJava compileTestJava
    
  unit-rhino:           # rhino module (~2 min)
    ./gradlew :rhino:test
    
  unit-tests:           # tests module without Test262 (~2 min)
    ./gradlew :tests:testWithoutTest262
    
  test262:              # Test262 independent (~12 min)
    ./gradlew :tests:test262
    
  matrix:               # Multi-JDK without Test262 (~2 min each)
    ./gradlew test -x :tests:test
```

| Job | Time | Runner Minutes |
|-----|------|----------------|
| build | ~2 min | 2 |
| unit-rhino | ~2 min | 2 |
| unit-tests | ~2 min | 2 |
| test262 | ~12 min | 12 |
| matrix × 4 | ~2 min × 4 | 8 |
| **Total** | Parallel ~12 min | **26 min** |

Savings: 75 → 26 min (-65%)

### Plan C: Test262 Sharding (Maximum Parallelism)

Split Test262 into 4 parallel shards for ~3 min total:

```yaml
test262:
  strategy:
    matrix:
      shard: [1, 2, 3, 4]
  steps:
    - run: ./gradlew :tests:test262 -Dtest262.shard=${{ matrix.shard }} -Dtest262.totalShards=4
```

Requires implementing sharding logic in Test262SuiteTest.java.

| Metric | Value |
|--------|-------|
| Total Runner Minutes | 23 min |
| Feedback Time | ~3 min |
| Complexity | High |
