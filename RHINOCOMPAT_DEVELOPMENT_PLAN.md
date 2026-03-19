# RhinoCompat 兼容层模块开发计划

**创建日期**: 2026年3月18日  
**更新日期**: 2026年3月19日  
**状态**: 设计中  
**目标版本**: Rhino 2.0.0+

---

## 1. 项目目标

创建独立的 `rhino-compat` 模块，提供统一的兼容 API，实现：

| 目标 | 说明 |
|------|------|
| **旧版兼容** | 下游项目使用旧版风格 API，最小化代码修改即可升级 Rhino |
| **新版支持** | 下游项目可以直接使用新版原生 API 享受新特性 |
| **混合使用** | 两套 API 可以在同一项目中混用 |
| **单点维护** | 升级 Rhino 时只需更新兼容层，下游无需改动 |

---

## 1.1 代码验证结果（2026-03-19）

### WrapFactory API 变更验证

**源码位置**: `rhino/src/main/java/org/mozilla/javascript/WrapFactory.java`

| 方法 | 签名 | 修饰符 | 行号 | 说明 |
|------|------|--------|------|------|
| `wrap(..., Class<?>)` | `final Object wrap(Context, Scriptable, Object, Class<?>)` | **final** | L42-44 | 委托给 TypeInfo 版本 |
| `wrap(..., TypeInfo)` | `Object wrap(Context, Scriptable, Object, TypeInfo)` | 可覆写 | L50-75 | 核心实现 |
| `wrapAsJavaObject(..., Class<?>)` | `final Scriptable wrapAsJavaObject(..., Class<?>)` | **final** | L104-106 | 委托给 TypeInfo 版本 |
| `wrapAsJavaObject(..., TypeInfo)` | `Scriptable wrapAsJavaObject(..., TypeInfo)` | 可覆写 | L119-133 | 核心实现 |

**关键代码片段**:
```java
// WrapFactory.java:42-44
public final Object wrap(Context cx, Scriptable scope, Object obj, Class<?> staticType) {
    return wrap(cx, scope, obj, TypeInfoFactory.GLOBAL.create(staticType));
}
```

**死循环风险**:
```
调用链：
wrap(Class) [final]
  → wrap(TypeInfo)  [覆写后调用 wrapCompat]
    → wrapCompat(Class)
      → super.wrap(Class)  ← 死循环！

解决方案：wrapCompat() 内部必须复制核心逻辑，不能调用 super.wrap(..., Class<?>)
```

### 函数类型系统验证

**JSFunction 继承关系** (`JSFunction.java:17-20`):
```java
public class JSFunction extends BaseFunction implements ScriptOrFn<JSFunction> {
    private final JSDescriptor<JSFunction> descriptor;
    private final Scriptable lexicalThis;      // 箭头函数专用
    private final Scriptable homeObject;       // super 调用支持
}
```

**箭头函数检测** (`JSDescriptor.java:163`):
```java
public boolean hasLexicalThis() {
    return (flags & HAS_LEXICAL_THIS_FLAG) != 0;
}
```

**NativeFunction 抽象方法** (`NativeFunction.java`):
| 方法 | 行号 | 说明 |
|------|------|------|
| `getLanguageVersion()` | L94 | 抽象方法 |
| `getParamCount()` | L99 | 抽象方法 |
| `getParamAndVarCount()` | L103 | 抽象方法 |
| `getParamOrVarName(int)` | L109 | 抽象方法 |

**结论**: `NativeFunctionAdapter` 需要实现这 4 个抽象方法。

### TypeInfo 常量验证

**源码位置**: `rhino/src/main/java/org/mozilla/javascript/lc/type/TypeInfo.java`

```java
TypeInfo.NONE              // 无类型
TypeInfo.PRIMITIVE_VOID    // void 类型
TypeInfo.PRIMITIVE_CHAR    // char 类型 (非 PRIMITIVE_CHARACTER!)
TypeInfo.PRIMITIVE_INT     // int 类型
```

**修正**: 原计划中使用 `TypeInfo.PRIMITIVE_CHARACTER` 是错误的，应为 `TypeInfo.PRIMITIVE_CHAR`。

---

## 2. 探索结果摘要

### 2.1 项目模块结构

```
Rhino-For-AutoJs/
├── rhino/              # 核心引擎 (无外部依赖)
├── rhino-engine/       # ScriptEngine 实现
├── rhino-tools/        # Shell 和调试器
├── rhino-xml/          # E4X/XML 支持
├── rhino-kotlin/       # Kotlin 反射接口
├── rhino-all/          # 一体化 JAR 打包
├── tests/              # 测试模块
├── testutils/          # 测试工具
├── benchmarks/         # 性能基准
├── examples/           # 示例代码
├── it-android/         # Android 集成测试
└── rhino-compat/       # ← 新增：兼容层模块
```

### 2.2 函数类型继承关系

```
Callable (interface)
    └── Function (interface)
            └── BaseFunction (abstract class)
                    ├── NativeFunction (Rhino 1.7.x / 内置函数)
                    ├── JSFunction (Rhino 2.0.0+ JS 函数)
                    ├── BoundFunction
                    └── LambdaFunction
```

**关键发现**:
- `Callable` 是稳定接口，所有函数类型都实现
- `NativeFunction` 和 `JSFunction` 都继承 `BaseFunction`
- Final 方法 (`initScriptFunction`, `decompile`) 不影响适配器模式
- ArrowFunction 不是独立类，通过 `JSFunction.hasLexicalThis()` 实现

### 2.3 下游项目迁移状态（2026-03-19 更新）

| 项目 | Rhino 版本 | WrapFactory 签名 | 状态 | 说明 |
|------|-----------|------------------|------|------|
| **Auto.js** | 2.0.0-SNAPSHOT | `Class<*>` | 🔴 需迁移 | 编译失败，方法签名未更新 |
| **Auto.js.HYB1996** | 2.0.0-SNAPSHOT | `TypeInfo` | ✅ 已完成 | 已迁移到新版 API |
| **AutoJs6** | 2.0.0-SNAPSHOT | `TypeInfo` | ✅ 已完成 | 已迁移到新版 API |
| **AutoX** | 1.8.1 (Maven) | `Class<*>` | 🟡 暂不需要 | 未来升级时需迁移 |
| **AutoX.js** | 1.7.14 (本地 jar) | `Class<*>` | 🟡 暂不需要 | 未来升级时需迁移 |

### 2.4 WrapFactory 继承链结构

**重要发现**：各项目的 WrapFactory 继承链不同：

```
AutoX / AutoX.js（两层继承）:
org.mozilla.javascript.WrapFactory
    └── AndroidContextFactory.WrapFactory     ← 重写 wrap()
        └── RhinoJavaScriptEngine.WrapFactory ← 重写 wrapAsJavaObject()

Auto.js / Auto.js.HYB1996（单层继承）:
org.mozilla.javascript.WrapFactory
    └── RhinoJavaScriptEngine.WrapFactory     ← 重写 wrap() + wrapAsJavaObject()
```

**兼容层优势**：AutoX/AutoX.js 只需将 `AndroidContextFactory.WrapFactory` 改为继承 `WrapFactoryCompat`，子类无需修改。

---

## 3. 开发阶段

### 阶段1: 模块基础设施

**任务1.1**: 创建目录结构

```
rhino-compat/
├── build.gradle
└── src/
    ├── main/java/org/mozilla/javascript/compat/
    │   ├── RhinoCompat.java
    │   ├── FunctionCompat.java
    │   ├── E4XCompat.java
    │   ├── NativeFunctionAdapter.java
    │   └── WrapFactoryCompat.java
    └── test/java/org/mozilla/javascript/compat/
        ├── RhinoCompatTest.java
        ├── FunctionCompatTest.java
        └── WrapFactoryCompatTest.java
```

**任务1.2**: 修改 `settings.gradle`

```groovy
// 添加新模块
include 'rhino-compat'
```

**任务1.3**: 创建 `rhino-compat/build.gradle`

```groovy
plugins {
    id 'rhino.library-conventions'
}

dependencies {
    implementation project(':rhino')
    implementation project(':rhino-xml')
    testImplementation project(':testutils')
}

publishing {
    publications {
        rhinocompat(MavenPublication) {
            from components.java
            artifacts = [jar, sourceJar, javadocJar]
            pom.withXml {
                def root = asNode()
                root.appendNode('name', 'rhino-compat')
                root.appendNode('description', 
                    'Rhino compatibility layer for downstream projects')
            }
        }
    }
}
```

**任务1.4**: 修改 `rhino-all/build.gradle`

```groovy
dependencies {
    implementation project(':rhino')
    implementation project(':rhino-tools')
    implementation project(':rhino-xml')
    implementation project(':rhino-compat')  // 新增
}
```

---

### 阶段2: 核心组件实现

**任务2.1**: `RhinoCompat.java` - 主入口类

```java
package org.mozilla.javascript.compat;

/**
 * Rhino 兼容层主入口
 * 
 * 提供统一的兼容 API，屏蔽版本差异。
 */
public final class RhinoCompat {
    
    // ========== 初始化 ==========
    
    /** 初始化 Rhino 环境（包含 E4X） */
    public static void init(Context cx, Scriptable scope) { ... }
    
    public static synchronized void init(Context cx, Scriptable scope, boolean sealed) { ... }
    
    // ========== 函数类型检查 ==========
    
    /** 检查是否为 JavaScript 函数 */
    public static boolean isFunction(Object obj) {
        if (obj instanceof NativeFunction) return true;
        if (obj instanceof JSFunction) return true;
        return false;
    }
    
    /** 检查是否可调用 */
    public static boolean isCallable(Object obj) {
        return obj instanceof Callable;
    }
    
    // ========== 函数调用 ==========
    
    /** 调用函数 */
    public static Object call(Object fn, Context cx, Scriptable scope, 
                              Scriptable thisObj, Object[] args) {
        if (fn instanceof Callable) {
            return ((Callable) fn).call(cx, scope, thisObj, args);
        }
        throw ScriptRuntime.typeErrorById("msg.isnt.function", ...);
    }
    
    // ========== 类型转换 ==========
    
    /** 包装函数为兼容类型 */
    public static Object wrapFunction(Object fn) {
        return NativeFunctionAdapter.wrap(fn);
    }
    
    // ========== 函数信息 ==========
    
    public static int getParamCount(Object fn) { ... }
    public static String getFunctionName(Object fn) { ... }
}
```

**任务2.2**: `FunctionCompat.java` - 函数类型兼容

```java
package org.mozilla.javascript.compat;

/**
 * 函数类型兼容工具类
 */
public final class FunctionCompat {
    
    /** 检查是否为 JavaScript 函数（不含 Java 方法） */
    public static boolean isJavaScriptFunction(Object obj) {
        return obj instanceof BaseFunction 
            && !(obj instanceof NativeJavaMethod)
            && !(obj instanceof LambdaFunction);
    }
    
    /** 检查是否为箭头函数 */
    public static boolean isArrowFunction(Object obj) {
        if (obj instanceof JSFunction) {
            return ((JSFunction) obj).getDescriptor().hasLexicalThis();
        }
        return false;
    }
    
    /** 安全调用函数 */
    public static Object call(Object fn, Context cx, Scriptable scope,
                              Scriptable thisObj, Object[] args) { ... }
    
    /** 获取函数参数个数 */
    public static int getParamCount(Object fn) { ... }
    
    /** 获取函数名 */
    public static String getFunctionName(Object fn) { ... }
}
```

**任务2.3**: `E4XCompat.java` - E4X 自动兼容

```java
package org.mozilla.javascript.compat;

/**
 * E4X 兼容层
 * 
 * 自动检测并使用正确的初始化方式
 */
final class E4XCompat {
    
    private static Boolean hasDescriptorAPI = null;
    
    /** 初始化 E4X 支持 */
    static void init(Context cx, Scriptable scope, boolean sealed) {
        if (hasDescriptorAPI == null) {
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
            Class.forName("org.mozilla.javascript.ClassDescriptor");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    // ... 初始化方法实现
}
```

**任务2.4**: `NativeFunctionAdapter.java` - 适配器

```java
package org.mozilla.javascript.compat;

/**
 * JSFunction 到 NativeFunction 的适配器
 * 
 * 让新版 JSFunction 伪装成旧版 NativeFunction
 */
public final class NativeFunctionAdapter extends NativeFunction {
    
    private final BaseFunction delegate;
    
    private NativeFunctionAdapter(BaseFunction delegate) {
        this.delegate = delegate;
    }
    
    /** 包装函数对象 */
    public static Object wrap(Object obj) {
        if (obj instanceof JSFunction) {
            return new NativeFunctionAdapter((JSFunction) obj);
        }
        return obj;
    }
    
    /** 解包函数对象 */
    public static Object unwrap(Object obj) {
        if (obj instanceof NativeFunctionAdapter) {
            return ((NativeFunctionAdapter) obj).delegate;
        }
        return obj;
    }
    
    // ========== 委托 BaseFunction 方法 ==========
    
    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        return delegate.call(cx, scope, thisObj, args);
    }
    
    @Override
    public int getParamCount() {
        return delegate.getParamCount();
    }
    
    // ... 其他委托方法
}
```

---

### 阶段3: 扩展组件

**任务3.1**: `WrapFactoryCompat.java`（已验证）

⚠️ **重要**：Rhino 2.0.0 中 `wrap(..., Class<?>)` 是 **final** 方法，直接调用 `super.wrap(..., Class<?>)` 会导致死循环。

```java
package org.mozilla.javascript.compat;

import org.mozilla.javascript.*;
import org.mozilla.javascript.lc.type.TypeInfo;
import org.mozilla.javascript.lc.type.TypeInfoFactory;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * WrapFactory 兼容基类
 * 
 * 让下游项目可以继续使用 Class<?> 参数签名，内部自动转换为 TypeInfo。
 * 
 * <h2>调用链（重要）</h2>
 * <pre>
 * caller.wrap(cx, scope, obj, Class)           [final - WrapFactory]
 *   → WrapFactory.wrap(cx, scope, obj, TypeInfo)  [覆写]
 *     → WrapFactoryCompat.wrap(TypeInfo)          [覆写，调用 wrapCompat]
 *       → wrapCompat(cx, scope, obj, Class)       [子类实现]
 * </pre>
 * 
 * <h2>使用方式</h2>
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
    
    // ========== wrap 方法链 ==========
    
    /**
     * 覆写 TypeInfo 版本，将 TypeInfo 转换为 Class 并调用 wrapCompat
     * 
     * ⚠️ 子类不应重写此方法，应重写 wrapCompat
     */
    @Override
    public Object wrap(Context cx, Scriptable scope, 
                       Object obj, TypeInfo staticType) {
        Class<?> staticClass = (staticType != null && staticType != TypeInfo.NONE) 
            ? staticType.asClass() 
            : null;
        return wrapCompat(cx, scope, obj, staticClass);
    }
    
    /**
     * 子类重写此方法，使用 Class 参数（旧版 API 风格）
     * 
     * ⚠️ 警告：不要调用 super.wrap(..., Class)，会导致死循环！
     * 
     * 默认实现：复制自 WrapFactory.wrap() 核心逻辑
     */
    protected Object wrapCompat(Context cx, Scriptable scope, 
                                Object obj, Class<?> staticType) {
        // === 复制自 WrapFactory.wrap() 核心逻辑 ===
        // 注意：不能调用 super.wrap(..., Class)，会死循环！
        
        if (obj == null || obj == Undefined.instance || obj instanceof Scriptable) {
            return obj;
        }
        
        // 处理原始类型（使用 TypeInfo 常量判断）
        if (staticType != null && staticType.isPrimitive()) {
            if (staticType == Void.TYPE) {
                return Undefined.instance;
            } else if (staticType == Character.TYPE) {
                return (int) (Character) obj;
            }
            return obj;
        }
        
        // 处理 Java 原始类型包装
        if (!isJavaPrimitiveWrap()) {
            if (obj instanceof String 
                    || obj instanceof Boolean
                    || obj instanceof Integer
                    || obj instanceof Byte
                    || obj instanceof Short
                    || obj instanceof Long
                    || obj instanceof Float
                    || obj instanceof Double
                    || obj instanceof BigInteger) {
                return obj;
            } else if (obj instanceof Character) {
                return String.valueOf(((Character) obj).charValue());
            }
        }
        
        return wrapAsJavaObjectCompat(cx, scope, obj, staticType);
    }
    
    // ========== wrapAsJavaObject 方法链 ==========
    
    /**
     * 覆写 TypeInfo 版本，调用 wrapAsJavaObjectCompat
     * 
     * ⚠️ 子类不应重写此方法，应重写 wrapAsJavaObjectCompat
     */
    @Override
    public Scriptable wrapAsJavaObject(Context cx, Scriptable scope, 
                                       Object javaObject, TypeInfo staticType) {
        Class<?> staticClass = (staticType != null && staticType != TypeInfo.NONE)
            ? staticType.asClass()
            : null;
        return wrapAsJavaObjectCompat(cx, scope, javaObject, staticClass);
    }
    
    /**
     * 子类重写此方法，使用 Class 参数（旧版 API 风格）
     * 
     * 默认实现：复制自 WrapFactory.wrapAsJavaObject() 核心逻辑
     */
    protected Scriptable wrapAsJavaObjectCompat(Context cx, Scriptable scope, 
                                                Object javaObject, Class<?> staticType) {
        // === 复制自 WrapFactory.wrapAsJavaObject() 核心逻辑 ===
        
        // 获取实际类型
        TypeInfoFactory factory = TypeInfoFactory.getOrElse(scope, TypeInfoFactory.GLOBAL);
        TypeInfo actualType = (staticType != null) 
            ? factory.create(staticType) 
            : TypeInfo.NONE;
            
        if (actualType.shouldReplace() && javaObject != null) {
            actualType = factory.create(javaObject.getClass());
            staticType = javaObject.getClass();
        }
        
        // 根据类型选择包装器
        if (staticType != null) {
            if (List.class.isAssignableFrom(staticType)) {
                return new NativeJavaList(scope, javaObject, actualType);
            } else if (Map.class.isAssignableFrom(staticType)) {
                return new NativeJavaMap(scope, javaObject, actualType);
            } else if (staticType.isArray()) {
                return new NativeJavaArray(scope, javaObject, actualType);
            }
        }
        
        return new NativeJavaObject(scope, javaObject, actualType);
    }
    
    // ========== wrapNewObject 保持不变 ==========
    
    /**
     * wrapNewObject 通常不需要重写
     * 如需自定义，重写此方法
     */
    @Override
    public Scriptable wrapNewObject(Context cx, Scriptable scope, Object obj) {
        if (obj instanceof Scriptable) {
            return (Scriptable) obj;
        }
        return wrapAsJavaObjectCompat(cx, scope, obj, null);
    }
}
```

**任务3.2**: 单元测试

- `RhinoCompatTest.java` - 测试主入口
- `FunctionCompatTest.java` - 测试函数兼容
- `E4XCompatTest.java` - 测试 E4X 初始化
- `NativeFunctionAdapterTest.java` - 测试适配器
- `WrapFactoryCompatTest.java` - 测试 WrapFactory 兼容（重点测试不死循环）

---

### 阶段4: 文档和发布

**任务4.1**: 更新 `TECHNICAL_ANALYSIS_REPORT.md`

- 添加 rhino-compat 模块说明
- 更新模块列表和依赖图

**任务4.2**: 更新 `README.md`

- 添加 rhino-compat 使用说明
- 添加迁移指南

**任务4.3**: Maven 发布配置

- 配置发布到 local-maven-repo
- 更新版本号

---

## 4. 下游项目迁移指南

### 4.1 Auto.js（当前需要迁移）

**修改前**：
```kotlin
private inner class WrapFactory : org.mozilla.javascript.WrapFactory() {
    override fun wrap(cx: Context, scope: Scriptable, obj: Any?, staticType: Class<*>?): Any? {
        // ...
    }
    override fun wrapAsJavaObject(cx: Context?, scope: Scriptable, javaObject: Any?, staticType: Class<*>?): Scriptable? {
        // ...
    }
}
```

**修改后（使用兼容层）**：
```kotlin
private inner class WrapFactory : org.mozilla.javascript.compat.WrapFactoryCompat() {
    override fun wrapCompat(cx: Context, scope: Scriptable, obj: Any?, staticType: Class<*>?): Any? {
        // ... 业务逻辑不变
    }
    override fun wrapAsJavaObjectCompat(cx: Context?, scope: Scriptable, javaObject: Any?, staticType: Class<*>?): Scriptable? {
        // ... 业务逻辑不变
    }
}
```

### 4.2 AutoX / AutoX.js（未来升级时）

**只需修改基类**：
```kotlin
// AndroidContextFactory.kt
// 修改前
open class WrapFactory : org.mozilla.javascript.WrapFactory() {

// 修改后
open class WrapFactory : org.mozilla.javascript.compat.WrapFactoryCompat() {
```

RhinoJavaScriptEngine 中的子类无需修改，因为它继承的是 AndroidContextFactory.WrapFactory。

### 4.3 迁移工作量对比

| 项目 | 无兼容层 | 有兼容层 |
|------|----------|----------|
| **Auto.js** | 改方法签名（2 个方法） | 改继承 + 方法名（3 处） |
| **AutoX** (未来) | 改 2 个类的签名（4 个方法） | **改 1 处继承** |
| **AutoX.js** (未来) | 改 2 个类的签名（4 个方法） | **改 1 处继承** |

**结论**：兼容层对 AutoX/AutoX.js 这类多层继承的项目收益最大。

---

## 4.4 下游项目代码对比（2026-03-19 验证）

### Auto.js（编译失败）

**文件**: `K:\msys64\home\ms900\Auto.js\autojs\src\main\java\com\stardust\autojs\engine\RhinoJavaScriptEngine.kt:207-237`

```kotlin
private inner class WrapFactory : org.mozilla.javascript.WrapFactory() {
    init {
        isJavaPrimitiveWrap = false
    }

    // ❌ 使用 Class<*> 参数 - Rhino 2.0.0 中此方法为 final，无法覆写
    override fun wrap(cx: Context, scope: Scriptable, obj: Any?, staticType: Class<*>?): Any? {
        return when {
            staticType == UiObjectCollection::class.java -> runtime.bridges.asArray(obj)
            else -> {
                if (scope is TopLevelScope) {
                    if (scope.isRecycled) throw ScriptInterruptedException()
                }
                super.wrap(cx, scope, obj, staticType)  // ❌ 调用 final 方法
            }
        }
    }

    // ❌ 使用 Class<*> 参数 - final 方法无法覆写
    override fun wrapAsJavaObject(
        cx: Context?, scope: Scriptable, javaObject: Any?, staticType: Class<*>?
    ): Scriptable? {
        return if (javaObject is View) {
            ViewExtras.getNativeView(scope, javaObject, staticType, runtime)
        } else {
            super.wrapAsJavaObject(cx, scope, javaObject, staticType)  // ❌ 调用 final 方法
        }
    }
}
```

**编译错误**: `wrap(..., Class<?>)` 和 `wrapAsJavaObject(..., Class<?>)` 是 final 方法，无法覆写。

### Auto.js.HYB1996（已迁移）

**文件**: `K:\msys64\home\ms900\Auto.js.HYB1996\autojs\src\main\java\com\stardust\autojs\engine\RhinoJavaScriptEngine.kt:158-180`

```kotlin
private inner class WrapFactory : org.mozilla.javascript.WrapFactory() {

    // ✅ 使用 TypeInfo 参数 - 正确
    override fun wrap(cx: Context, scope: Scriptable, obj: Any?, staticType: TypeInfo): Any? {
        return when {
            obj is String -> runtime.bridges.toString(obj.toString())
            staticType.`is`(UiObjectCollection::class.java) -> runtime.bridges.asArray(obj)
            else -> super.wrap(cx, scope, obj, staticType)  // ✅ 调用可覆写方法
        }
    }

    // ✅ 使用 TypeInfo 参数 - 正确
    override fun wrapAsJavaObject(
        cx: Context?, scope: Scriptable, javaObject: Any?, staticType: TypeInfo
    ): Scriptable? {
        return if (javaObject is View) {
            ViewExtras.getNativeView(scope, javaObject, staticType.asClass(), runtime)
        } else {
            super.wrapAsJavaObject(cx, scope, javaObject, staticType)  // ✅ 调用可覆写方法
        }
    }
}
```

**关键差异**:
| 项目 | Auto.js | Auto.js.HYB1996 |
|------|---------|-----------------|
| 参数类型 | `Class<*>?` ❌ | `TypeInfo` ✅ |
| 类型比较 | `staticType == Xxx::class.java` | `staticType.is(Xxx::class.java)` |
| 类型转换 | 直接使用 | `staticType.asClass()` |
| super 调用 | 调用 final 方法 ❌ | 调用可覆写方法 ✅ |

---

## 5. 待确认问题

| 问题 | 决定 | 说明 |
|------|------|------|
| 是否需要支持 Rhino 1.7.x？ | 否 | 只需支持 2.0.0+ |
| 是否需要 ContextFactoryCompat？ | 否 | 暂未发现需求 |
| 是否需要发布到 Maven Central？ | 先本地 | 发布到 local-maven-repo |
| 单元测试覆盖率目标？ | 80%+ | 核心功能必须覆盖 |

---

## 6. 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| WrapFactoryCompat 死循环 | 高 | ✅ 已修正：不调用 super.wrap(Class) |
| API 不稳定 | 高 | 基于 Callable 等稳定接口 |
| 性能开销 | 低 | 仅一层方法转发，可忽略 |
| 维护成本 | 中 | 独立模块，易于更新 |
| 文档不完整 | 低 | 详细注释和使用示例 |

---

## 7. 实施优先级

| 阶段 | 内容 | 优先级 |
|------|------|--------|
| 阶段1 | 模块基础设施 | 高 |
| 阶段3 任务3.1 | WrapFactoryCompat | **高**（Auto.js 立即需要） |
| 阶段3 任务3.2 | WrapFactoryCompatTest | 高 |
| 阶段2 | 核心组件 | 中 |
| 阶段4 | 文档和发布 | 低 |

---

## 8. 参考文档

- `TECHNICAL_ANALYSIS_REPORT.md` - 技术分析报告
- `RHINO_UPGRADE_DEBUG_PROGRESS.md` (HYB1996) - 升级调试记录
- `RHINO_UPGRADE_REPORT.md` (HYB1996) - 升级报告
- `AGENTS.md` - 项目说明文档

---

## 9. 进度跟踪

| 任务 | 状态 | 完成日期 |
|------|------|----------|
| 计划文档编写 | ✅ 完成 | 2026-03-18 |
| 代码验证 | ✅ 完成 | 2026-03-19 |
| 文档更新（整合验证结果） | ✅ 完成 | 2026-03-19 |
| WrapFactoryCompat 实现 | ⏳ 待开始 | - |
| 单元测试 | ⏳ 待开始 | - |
| 发布到 local-maven-repo | ⏳ 待开始 | - |

---

**下一步行动**:
1. ✅ 审查完成
2. ✅ 文档更新完成
3. ⏳ 创建模块目录结构
4. ⏳ 实现 WrapFactoryCompat
5. ⏳ 编写单元测试验证不死循环
6. ⏳ 发布到 local-maven-repo