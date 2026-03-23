# Rhino ES2022 Class 实现计划完善报告

> 探索日期: 2026-03-23
> 基于计划版本: v1.10
> 探索范围: 字节码、优化编译器、并发安全、TDZ、动态执行、super 基础设施

## 执行摘要

- **计划完备性评分**: 3.5/5
- **主要风险点**:
  1. 字节码指令完全缺失（需新增 5+ Token）
  2. 优化编译器 (Codegen) 无类支持
  3. TDZ 机制未找到实现
  4. 私有字段线程安全未考虑
- **建议优先级**: 高

---

## 详细发现

### 1. 字节码指令完整性

**状态**: ❌ 缺失

**发现**:

#### 1.1 Token.java 现状

```
LAST_TOKEN = OBJECT_REST + 1  // 约等于 163
```

**当前已定义的 super 相关 Token**:
| Token | 值 | 说明 |
|-------|-----|------|
| `SUPER` | ~122 | super 关键字 |
| `GETPROP_SUPER` | ~77 | super 属性获取 |
| `SETPROP_SUPER` | ~79 | super 属性设置 |
| `GETELEM_SUPER` | ~80 | super 元素获取 |
| `SETELEM_SUPER` | ~81 | super 元素设置 |

**缺失的 Token** (需新增):
| Token | 建议值 | 用途 |
|-------|--------|------|
| `CLASS` | `LAST_TOKEN + 1` | 类声明/表达式 |
| `EXTENDS` | `CLASS + 1` | extends 关键字 |
| `CLASS_ELEMENT` | `EXTENDS + 1` | 类元素 AST 节点 |
| `PRIVATE_FIELD` | `CLASS_ELEMENT + 1` | 私有字段 # 前缀 |
| `NEW_CLASS` | `PRIVATE_FIELD + 1` | IR 节点：创建类对象 |
| `STATIC_BLOCK` | `NEW_CLASS + 1` | 静态初始化块 |

#### 1.2 Icode.java 现状

已定义约 70 个解释器专用指令，包括:
- `Icode_CALL_ON_SUPER` - super 方法调用
- `Icode_DELPROP_SUPER` - super 属性删除
- `Icode_SPREAD`, `Icode_OBJECT_REST` - 解构相关

**缺失的 Icode**:
- 无私有字段访问指令 (GET_PRIVATE_FIELD, SET_PRIVATE_FIELD)

#### 1.3 CodeGenerator.java 分析

文件位置: `rhino/src/main/java/org/mozilla/javascript/CodeGenerator.java`

```java
// 现有 switch(node.getType()) 处理:
case Token.FUNCTION:  // ✓ 有
case Token.ARRAYLIT:  // ✓ 有
case Token.OBJECTLIT: // ✓ 有
// case Token.CLASS:   // ❌ 缺失
```

#### 1.4 Interpreter.java 指令注册

文件位置: `rhino/src/main/java/org/mozilla/javascript/Interpreter.java`

现有指令注册模式:
```java
instructionObjs[base + Token.SUPER] = new DoSuper();
instructionObjs[base + Token.GETPROP_SUPER] = new DoGetPropSuper();
instructionObjs[base + Icode_CALL_ON_SUPER] = new DoCallByteCode();
```

**建议**: 新增 `DoClass`, `DoPrivateFieldAccess` 等指令类

#### 1.5 性能影响评估

| 方案 | 性能 | 实现复杂度 | 推荐度 |
|------|------|-----------|--------|
| ScriptRuntime 静态调用 | 中 | 低 | ⭐⭐⭐ 初期推荐 |
| 专用字节码指令 | 高 | 高 | ⭐⭐ 后期优化 |

**建议**: 第一阶段使用 `ScriptRuntime.createClass()` 等静态方法调用，类似现有 `getSuperProp()` 模式。

---

### 2. 优化编译器支持

**状态**: ❌ 缺失

**发现**:

#### 2.1 Codegen.java 分析

文件位置: `rhino/src/main/java/org/mozilla/javascript/optimizer/Codegen.java`

```
共 1007 行代码
职责: 将 IR 编译为 JVM 字节码
```

**搜索结果**: 
- 未找到 `Token.CLASS`, `visitClass`, `generateClass` 相关代码
- 现有 `transform()` 方法调用 `OptTransformer` 和 `Optimizer`

#### 2.2 BodyCodegen.java 分析

文件位置: `rhino/src/main/java/org/mozilla/javascript/optimizer/BodyCodegen.java`

已有支持:
- `savedHomeObjectLocal` - home object 本地变量存储
- `getHomeObject()` 调用生成

**缺失**:
- 无 `Token.CLASS` 处理
- 无类定义字节码生成

#### 2.3 影响评估

| 优化级别 | 执行方式 | 当前状态 |
|----------|----------|----------|
| -1 / -2 | 解释器 | 需新增 Token 支持 |
| 0-9 | JVM 字节码 | ❌ 完全不支持 |

**测试建议**:
```java
Context cx = Context.enter();
cx.setOptimizationLevel(-1);  // 解释模式
// cx.setOptimizationLevel(9);  // 优化模式 - 会失败
```

**需要补充的代码位置**:

| 文件 | 位置 | 修改内容 |
|------|------|----------|
| `optimizer/Codegen.java` | `transform()` 后 | 添加类节点处理 |
| `optimizer/BodyCodegen.java` | `visitStatement()` | 添加 `case Token.CLASS` |
| `optimizer/Optimizer.java` | 全局 | 类节点优化规则 |

---

### 3. 并发安全与序列化

**状态**: ⚠️ 风险

**发现**:

#### 3.1 NativeWeakMap 参考实现

```java
public class NativeWeakMap extends ScriptableObject {
    private transient WeakHashMap<Object, Object> map = new WeakHashMap<>();
    
    private void readObject(ObjectInputStream stream) 
            throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        map = new WeakHashMap<>();  // 反序列化时重建
    }
}
```

**特点**:
- `transient` 关键字确保 map 不被序列化
- `readObject()` 在反序列化时重建空 map
- **无线程同步** - WeakHashMap 非线程安全

#### 3.2 NativeClass 私有字段存储风险

计划中的实现:
```java
public class NativeClass extends BaseFunction {
    private transient WeakHashMap<Object, Map<String, Object>> privateFieldStorage;
    private final Object classBrand = new Object();  // 序列化问题！
}
```

**问题**:

| 问题 | 风险等级 | 说明 |
|------|----------|------|
| WeakHashMap 线程安全 | 中 | 多线程访问可能数据不一致 |
| classBrand 序列化 | 高 | 每次反序列化生成新对象，brand 检查失效 |
| transient 字段丢失 | 高 | 反序列化后私有字段数据丢失 |

#### 3.3 解决方案建议

```java
public class NativeClass extends BaseFunction {
    // 方案 1: 使用 Collections.synchronizedMap
    private transient Map<Object, Map<String, Object>> privateFieldStorage = 
        Collections.synchronizedMap(new WeakHashMap<>());
    
    // 方案 2: 使用并发集合 (推荐)
    private transient ConcurrentMap<Object, Map<String, Object>> privateFieldStorage =
        new ConcurrentHashMap<>();
    
    // 序列化支持
    private static final long serialVersionUID = 1L;
    private static final Object BRAND_LOCK = new Object();
    
    // 使用 UUID 替代 Object 引用
    private final String classBrand = UUID.randomUUID().toString();
    
    private void readObject(ObjectInputStream stream) 
            throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        // 重建私有字段存储
        privateFieldStorage = new ConcurrentHashMap<>();
    }
}
```

---

### 4. TDZ (Temporal Dead Zone) 实现

**状态**: ❌ 缺失

**发现**:

搜索 `getVarWithTDZCheck`, `TDZ`, `temporal.*dead` 均无结果。

#### 4.1 现有 let/const 支持

Rhino 有 `Token.LET` 和 `Token.CONST` 定义:
```java
// Token.java
LET = SET + 1,
CONST = LET + 1,
SETCONST = CONST + 1,
SETCONSTVAR = SETCONST + 1,
```

但**未找到** TDZ 检查实现。

#### 4.2 TDZ 规范要求

```javascript
// ES6 规范要求的行为:
let x = x;  // ReferenceError: x is not defined (TDZ)

{
    console.log(x);  // ReferenceError (TDZ)
    let x = 1;
}
```

#### 4.3 实现建议

```java
// ScriptRuntime.java 新增
public static Object getVarWithTDZCheck(Scriptable scope, String name, Context cx) {
    // 检查变量是否在 TDZ 状态
    Object value = scope.get(name, scope);
    if (value == Scriptable.NOT_FOUND || value == UniqueTag.UNDEFINED_VALUE) {
        // 需要区分"未声明"和"TDZ"
        throw referenceErrorById("msg.tdz.access", name);
    }
    return value;
}
```

#### 4.4 类声明 TDZ 特殊性

```javascript
// 类声明有"提升但未初始化"行为:
let x = class {};  // OK
console.log(x);    // class {}

{
    console.log(MyClass);  // ReferenceError (TDZ)
    class MyClass {}
}
```

**需要补充**:
- `Parser.java`: 类声明时标记 TDZ 状态
- `ScriptRuntime.java`: `checkTDZ()` 方法
- `Node.java`: `TDZ_CHECK_PROP` 属性

---

### 5. 动态代码执行支持

**状态**: ⚠️ 需验证

#### 5.1 eval() 行为

```javascript
// 预期行为
eval("class A {}");  // 在当前作用域创建 A
typeof A;  // "function"

// 私有字段限制
eval("class B { #x = 1; }");  // #x 在 eval 上下文有效
```

#### 5.2 new Function() 行为

```javascript
// 预期行为
var Fn = new Function("class A {} return A;");
typeof Fn();  // "function"

// 私有字段限制
var Fn2 = new Function("class B { #x = 1; } return B;");
// #x 应该在每次调用返回的类中有效
```

#### 5.3 验证清单

| 场景 | 测试代码 | 预期结果 |
|------|----------|----------|
| eval 类声明 | `eval("class A{}"); typeof A` | "function" |
| eval 类表达式 | `var C = eval("(class {})"); typeof C` | "function" |
| eval 私有字段 | `eval("class A{#x=1;get(){return this.#x}}")` | 可访问 |
| new Function 类 | `new Function("return class {}")()` | 返回类 |
| 动态继承 | `eval("class A extends {} {}")` | 正确继承 |

#### 5.4 潜在问题

- 私有字段的 `classBrand` 在 eval 环境中如何保持一致性？
- 多次 eval 相同代码是否共享私有字段访问权限？

---

### 6. 现有 super 基础设施复用

**状态**: ✅ 完整可复用

#### 6.1 可复用功能清单

| 功能 | 文件 | 行号 | 状态 |
|------|------|------|------|
| `Token.SUPER` | Token.java | ~122 | ✅ 已实现 |
| `SUPER_PROPERTY_ACCESS` | Node.java | 70 | ✅ 已实现 |
| `getSuperProp()` | ScriptRuntime.java | 1970 | ✅ 已实现 |
| `getSuperElem()` | ScriptRuntime.java | 1885 | ✅ 已实现 |
| `setSuperProp()` | ScriptRuntime.java | 2157 | ✅ 已实现 |
| `setSuperElem()` | ScriptRuntime.java | ~2095 | ✅ 已实现 |
| `Icode_CALL_ON_SUPER` | Icode.java | 160 | ✅ 已实现 |
| `DoSuper` 指令 | Interpreter.java | ~4070 | ✅ 已实现 |
| `homeObject` 机制 | BaseFunction.java | 800-827 | ✅ 已实现 |

#### 6.2 super 相关 Token 完整列表

```java
// Token.java 中已定义
GETPROP_SUPER = GETPROPNOWARN + 1,
GETPROPNOWARN_SUPER = GETPROP_SUPER + 1,
SETPROP_SUPER = SETPROP + 1,
GETELEM_SUPER = GETELEM + 1,
SETELEM_SUPER = SETELEM + 1,
SUPER = YIELD + 1,  // ES6 super keyword
```

#### 6.3 待实现功能

| 功能 | 复杂度 | 说明 |
|------|--------|------|
| `super()` 构造器调用 | 高 | 需新增 `SUPER_CONSTRUCTOR_CALL` Token |
| home object 自动绑定 | 中 | 在 IRFactory 中设置 |
| 派生类构造器约束 | 高 | UninitializedObject 实现 |

#### 6.4 建议实现方案

**super() 调用转换**:
```javascript
// 源代码
class A extends B {
    constructor(x) {
        super(x);  // 需要转换
    }
}

// 转换后 (概念)
class A extends B {
    constructor(x) {
        // super(x) -> ScriptRuntime.superConstructorCall(this, B, [x])
        ScriptRuntime.superConstructorCall(this, B, [x]);
    }
}
```

**需要新增的 ScriptRuntime 方法**:
```java
public static Scriptable superConstructorCall(
    Context cx, Scriptable scope,
    Scriptable thisObj,    // UninitializedObject
    Scriptable superClass, // 父类构造器
    Object[] args
);
```

---

## 需要补充的代码位置

| 文件 | 行号范围 | 修改内容 | 优先级 |
|------|----------|----------|--------|
| `Token.java` | L164+ | 新增 CLASS, EXTENDS, PRIVATE_FIELD 等 Token | 高 |
| `Token.java` | `typeToName()` | 添加新 Token 名称映射 | 高 |
| `Icode.java` | L172+ | 新增私有字段相关 Icode | 中 |
| `CodeGenerator.java` | `visitStatement()` | 添加 `case Token.CLASS` | 高 |
| `Interpreter.java` | L1500+ | 新增 DoClass 等指令类 | 高 |
| `optimizer/Codegen.java` | `transform()` | 类节点 JVM 字节码生成 | 中 |
| `optimizer/BodyCodegen.java` | `visitStatement()` | 添加 `case Token.CLASS` | 中 |
| `ScriptRuntime.java` | 新增 | `createClass()`, `superConstructorCall()` | 高 |
| `ScriptRuntime.java` | 新增 | `getVarWithTDZCheck()` TDZ 检查 | 高 |
| `Node.java` | L70+ | 新增 CLASS_PROP, PRIVATE_FIELDS_PROP | 高 |
| `NativeClass.java` | 新文件 | 类构造器运行时对象 | 高 |

---

## 新增测试用例建议

### 边界测试

```javascript
// 1. TDZ 测试
test("class TDZ in block", () => {
    expect(() => {
        {
            console.log(A);  // ReferenceError
            class A {}
        }
    }).toThrow(ReferenceError);
});

// 2. 私有字段跨实例访问
test("private field cross-instance", () => {
    class A {
        #x = 1;
        getX(other) { return other.#x; }  // 同类实例可访问
    }
    const a1 = new A();
    const a2 = new A();
    expect(a1.getX(a2)).toBe(1);
});

// 3. 私有字段跨类访问 (应失败)
test("private field cross-class", () => {
    class A { #x = 1; }
    class B { 
        getX(a) { return a.#x; }  // TypeError
    }
    expect(() => new B().getX(new A())).toThrow(TypeError);
});

// 4. eval 中的类
test("class in eval", () => {
    eval("class EvalClass { #private = 42; }");
    expect(typeof EvalClass).toBe("function");
});

// 5. 优化模式一致性
test("class works in optimized mode", () => {
    // 需要在优化级别 9 下测试
    class A { x = 1; }
    expect(new A().x).toBe(1);
});

// 6. super() 构造器
test("super() required in derived class", () => {
    class Parent {}
    class Child extends Parent {
        constructor() {}  // 缺少 super()
    }
    expect(() => new Child()).toThrow(ReferenceError);
});

// 7. this before super
test("this before super throws", () => {
    class Parent {}
    class Child extends Parent {
        constructor() {
            this.x = 1;  // 错误
            super();
        }
    }
    expect(() => new Child()).toThrow(ReferenceError);
});

// 8. 静态块执行顺序
test("static block execution order", () => {
    const order = [];
    class A {
        static { order.push(1); }
        static x = order.push(2);
        static { order.push(3); }
    }
    expect(order).toEqual([1, 2, 3]);
});

// 9. 私有静态字段
test("private static field", () => {
    class Counter {
        static #count = 0;
        static increment() { return ++Counter.#count; }
    }
    expect(Counter.increment()).toBe(1);
    expect(Counter.increment()).toBe(2);
});

// 10. 类作为值传递
test("class as value", () => {
    const classes = [class A {}, class B {}];
    expect(new classes[0]()).toBeInstanceOf(classes[0]);
});
```

---

## 计划版本更新建议

建议将计划从 **v1.10** 升级到 **v1.11**，补充以下内容：

### 1. 字节码指令详细规范

```
### 5.1.6 新增 Token 完整定义

```java
// Token.java - 在 LAST_TOKEN 定义之前添加
// ===== ES2022 Class 支持 =====
CLASS = LAST_TOKEN,                    // 164
EXTENDS = CLASS + 1,                   // 165  
CLASS_ELEMENT = EXTENDS + 1,           // 166
PRIVATE_FIELD = CLASS_ELEMENT + 1,     // 167
NEW_CLASS = PRIVATE_FIELD + 1,         // 168
STATIC_BLOCK = NEW_CLASS + 1,          // 169
// 更新
LAST_TOKEN = STATIC_BLOCK + 1;         // 170
```

### 2. 优化编译器支持章节

```
### 5.8 优化编译器支持 (新增)

#### 5.8.1 BodyCodegen.java 修改

需要在 `generateStatement()` 方法中添加:
```java
case Token.CLASS:
    generateClass(node);
    break;
```

#### 5.8.2 实现优先级

1. **第一阶段**: 仅支持解释模式 (优化级别 -1, -2)
2. **第二阶段**: 支持优化模式 (优化级别 0-9)
```

### 3. 线程安全章节

```
### 5.7.10 线程安全与序列化 (新增)

#### 并发访问

NativeClass 私有字段存储必须使用线程安全集合:
```java
private transient ConcurrentMap<Object, Map<String, Object>> privateFieldStorage =
    new ConcurrentHashMap<>();
```

#### 序列化

1. 使用 UUID 替代 Object 作为 classBrand
2. 在 readObject() 中重建 transient 字段
```

### 4. TDZ 实现章节

```
### 5.5.6 TDZ 检查实现 (新增)

#### 类声明的 TDZ 行为

类声明具有"提升但未初始化"特性:
- 在声明之前访问类名会抛出 ReferenceError
- 这与 let/const 行为一致

#### 实现位置

- Parser.java: 在类声明处标记 TDZ 开始
- ScriptRuntime.java: 新增 `checkTDZ()` 方法
```

### 5. 风险评估更新

```
### 9.1 技术风险 (更新)

| 风险 | 等级 | 影响 | 缓解措施 |
|------|------|------|----------|
| 优化编译器不支持 | **高** | 优化模式下类定义会失败 | 第一阶段仅支持解释模式 |
| TDZ 未实现 | **中** | 类声明提升行为不符合规范 | 新增 TDZ 检查方法 |
| 私有字段线程安全 | **中** | 多线程环境数据不一致 | 使用 ConcurrentHashMap |
| 序列化 brand 丢失 | **中** | 反序列化后私有字段访问失败 | 使用 UUID 作为 brand |
```

---

## 结论

当前 ES2022 Class 实现计划 (v1.10) 是一份详尽的技术文档，但在以下方面需要补充：

1. **字节码指令**: 需要明确新增 Token 和 Icode 的完整定义
2. **优化编译器**: 需要补充 JVM 字节码生成的详细方案
3. **线程安全**: 需要考虑并发访问和序列化问题
4. **TDZ 实现**: 需要补充类声明的时间死区检查

建议按照上述更新建议完善计划文档后，再开始具体实现工作。

---

*报告生成时间: 2026-03-23*
*探索工具: iFlow CLI*
