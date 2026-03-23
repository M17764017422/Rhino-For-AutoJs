# Rhino ES2022 Class 支持开发计划

> 版本: 1.12
> 日期: 2026-03-23
> 状态: 详细规划阶段
> 变更说明: v1.12 - 同时支持解释模式和优化模式，新增并行开发流程、自动降级机制、双模式验证方案

## 一、项目概述

为 Rhino JavaScript 引擎添加 ES2022 `class` 语法支持，包括：

- ES2015 基础 class 语法
- ES2022 扩展特性（私有字段、静态块、公有静态字段）

## 二、架构总览

```
Source Code
     │
     ▼
TokenStream (Lexer) ──> Token 序列
     │
     ▼
Parser ──> AST (AstNode 层次结构)
     │
     ▼
IRFactory ──> IR (Node 层次结构)
     │
     ▼
Codegen/Interpreter ──> Bytecode/解释执行
     │
     ▼
ScriptRuntime ──> 运行时对象 (NativeObject, BaseFunction 等)
```

## 三、ECMAScript 规范对照

| ECMAScript 规范 | Rhino 实现 | 说明 |
|-----------------|-----------|------|
| ClassDeclaration | `ClassNode` | 类声明 |
| ClassExpression | `ClassNode` | 类表达式 |
| ClassBody | `ClassNode.elements` | 类体 |
| ClassElement | `ClassElement` | 类元素 |
| MethodDefinition | `ClassElement(METHOD)` | 方法定义 |
| FieldDefinition | `ClassElement(FIELD)` | 字段定义 |
| ClassStaticBlock | `ClassElement(STATIC_BLOCK)` | 静态块 |
| SuperProperty | 已有 `Token.SUPER` | super 访问 |
| HomeObject | `Node.HOME_OBJECT_PROP` | super 调用的 home object |

## 四、文件变更清单

### 4.1 新增文件

| 文件路径 | 用途 | 预估代码量 |
|----------|------|------------|
| `rhino/src/main/java/org/mozilla/javascript/ast/ClassNode.java` | 类声明/表达式 AST 节点 | ~250 行 |
| `rhino/src/main/java/org/mozilla/javascript/ast/ClassElement.java` | 类元素 AST 节点（方法、字段、静态块） | ~180 行 |
| `rhino/src/main/java/org/mozilla/javascript/NativeClass.java` | 类构造器运行时对象（继承 BaseFunction） | ~300 行 |
| `rhino/src/main/java/org/mozilla/javascript/UninitializedObject.java` | 派生类构造器未初始化 this 对象 | ~80 行 |
| `tests/src/test/java/org/mozilla/javascript/tests/es2022/ClassTest.java` | Java 单元测试 | ~200 行 |
| `tests/testsrc/jstests/es2022/class.js` | JavaScript 测试脚本 | ~300 行 |

### 4.2 修改文件

| 文件路径 | 修改内容 | 预估代码量 |
|----------|----------|------------|
| `rhino/src/main/java/org/mozilla/javascript/Token.java` | 新增 Token 类型 + typeToName/keywordToName | ~40 行 |
| `rhino/src/main/java/org/mozilla/javascript/TokenStream.java` | `#` 私有字段词法分析（~40 行新增） | ~40 行 |
| `rhino/src/main/java/org/mozilla/javascript/Parser.java` | 类解析逻辑（parseClass, parseClassBody, parseClassElement） | ~400 行 |
| `rhino/src/main/java/org/mozilla/javascript/IRFactory.java` | IR 转换逻辑（transformClass, 字段注入） | ~300 行 |
| `rhino/src/main/java/org/mozilla/javascript/Node.java` | 新增属性常量（CLASS_PROP, PRIVATE_FIELDS_PROP） | ~15 行 |
| `rhino/src/main/java/org/mozilla/javascript/ScriptRuntime.java` | 类创建辅助方法 + 私有字段访问 | ~150 行 |
| `rhino/src/main/java/org/mozilla/javascript/ast/AstNode.java` | visit() 方法添加 ClassNode 分支 | ~5 行 |
| `rhino/src/main/java/org/mozilla/javascript/resources/Messages.properties` | 错误消息（40+ 条） | ~45 行 |
| `tests/testsrc/test262.properties` | 移除 class 测试的排除标记 | ~2 行（删除） |

**总计：约 2307 行代码**

### 4.3 文件路径说明

根据代码库实际结构：

| 模块 | 基础路径 | 说明 |
|------|----------|------|
| rhino 核心 | `rhino/src/main/java/org/mozilla/javascript/` | 核心引擎代码 |
| AST 节点 | `rhino/src/main/java/org/mozilla/javascript/ast/` | AST 节点定义 |
| 测试代码 | `tests/src/test/java/org/mozilla/javascript/tests/` | Java 测试 |
| JS 测试脚本 | `tests/testsrc/jstests/` | JavaScript 测试脚本 |
| 资源文件 | `rhino/src/main/java/org/mozilla/javascript/resources/` | 消息配置 |

## 五、详细实现方案

### 5.1 Token 新增 (`Token.java`)

#### 5.1.1 当前 Token 结构分析

**Token 分区结构：**

```
Token 值范围：
├── ERROR = -1 (FIRST_TOKEN)
├── EOF = 0
├── EOL = 1
├── FIRST_BYTECODE_TOKEN = 3
│   └── [解释器字节码 Token: 3-94]
├── LAST_BYTECODE_TOKEN = BIGINT = 94
├── [语法 Token: 95-163]
│   ├── TRY = 95
│   ├── FUNCTION = 112
│   ├── EXPORT = 113
│   ├── IMPORT = 114
│   ├── SUPER = 141
│   ├── METHOD = 155
│   ├── ARROW = 156
│   ├── DOTDOTDOT = 160
│   ├── NULLISH_COALESCING = 161
│   ├── QUESTION_DOT = 162
│   └── OBJECT_REST = 163
└── LAST_TOKEN = 164 (当前值)
```

**关键发现：**
- 计划中 `CLASS = YIELD + 2` 是**错误的**（YIELD=84，会与 BIGINT 等冲突）
- 新 Token 必须从 `LAST_TOKEN` 位置开始追加
- 需要同时更新 `typeToName()` 方法添加新 Token 的名称映射

#### 5.1.2 安全的 Token 分配

```java
// 在 Token.java 中修改，替换原有的 LAST_TOKEN 定义

// ===== ES2022 Class 支持 =====
CLASS = LAST_TOKEN,             // 164 - class 关键字
EXTENDS = CLASS + 1,            // 165 - extends 关键字
CLASS_ELEMENT = EXTENDS + 1,    // 166 - 类元素 AST 节点类型
PRIVATE_FIELD = CLASS_ELEMENT + 1,  // 167 - 私有字段 # 前缀
NEW_CLASS = PRIVATE_FIELD + 1,  // 168 - IR 节点：创建类对象
// 更新 LAST_TOKEN
LAST_TOKEN = NEW_CLASS + 1;     // 169
```

#### 5.1.3 typeToName() 方法更新

```java
// 在 Token.typeToName() 方法的 switch 中添加
case CLASS:
    return "CLASS";
case EXTENDS:
    return "EXTENDS";
case CLASS_ELEMENT:
    return "CLASS_ELEMENT";
case PRIVATE_FIELD:
    return "PRIVATE_FIELD";
case NEW_CLASS:
    return "NEW_CLASS";
```

#### 5.1.4 keywordToName() 方法更新

```java
// 在 Token.keywordToName() 方法中添加（用于 FEATURE_RESERVED_KEYWORD_AS_IDENTIFIER）
case Token.CLASS:
    return "class";
case Token.EXTENDS:
    return "extends";
```

#### 5.1.5 Token 分类说明

| Token 类型 | 值 | 分类 | 说明 |
|-----------|-----|------|------|
| `CLASS` | 164 | 关键字 | 类声明/表达式关键字，需在 Parser 中识别 |
| `EXTENDS` | 165 | 关键字 | 继承关键字，需在 Parser 中识别 |
| `CLASS_ELEMENT` | 166 | AST 节点 | 仅用于 AST 构造，不由 Scanner 返回 |
| `PRIVATE_FIELD` | 167 | 词法单元 | 私有字段 `#` 前缀，需在 Scanner 中识别 |
| `NEW_CLASS` | 168 | IR 节点 | 仅用于 IR 构造，不由 Scanner 返回 |
| `STATIC_BLOCK` | 169 | AST 节点 | 静态初始化块，仅用于 AST 构造 |

#### 5.1.6 兼容性检查

| 检查项 | 结果 |
|--------|------|
| 与现有字节码冲突 | ✅ 无冲突（94 以下为字节码区） |
| 与现有语法 Token 冲突 | ✅ 无冲突（从 164 开始） |
| isValidToken() 方法 | ✅ 自动生效（范围检查用 LAST_TOKEN） |
| printTrees 调试输出 | ✅ 需添加 typeToName 映射 |

#### 5.1.7 字节码指令详细规范（v1.11 新增）

##### 5.1.7.1 Icode 指令定义

解释器需要新增专用的字节码指令来支持私有字段访问和类对象创建。

```java
// Icode.java - 在 MIN_ICODE 定义之前添加
// ===== ES2022 Class 私有字段支持 =====
Icode_GET_PRIVATE_FIELD = MIN_ICODE - 1,    // 获取私有字段值
Icode_SET_PRIVATE_FIELD = Icode_GET_PRIVATE_FIELD - 1,  // 设置私有字段值
Icode_NEW_CLASS = Icode_SET_PRIVATE_FIELD - 1,  // 创建类对象
// 更新
MIN_ICODE = Icode_NEW_CLASS;
```

**Icode 指令说明**：

| Icode | 值 | 栈操作 | 说明 |
|-------|-----|--------|------|
| `Icode_GET_PRIVATE_FIELD` | -71 | `[instance, fieldName] → value` | 获取实例的私有字段值 |
| `Icode_SET_PRIVATE_FIELD` | -72 | `[instance, fieldName, value] → value` | 设置实例的私有字段值 |
| `Icode_NEW_CLASS` | -73 | `[superClass, methods] → classObject` | 创建类对象 |

##### 5.1.7.2 Interpreter 指令注册

```java
// Interpreter.java - 在指令初始化代码中添加
instructionObjs[base + Icode_GET_PRIVATE_FIELD] = new DoGetPrivateField();
instructionObjs[base + Icode_SET_PRIVATE_FIELD] = new DoSetPrivateField();
instructionObjs[base + Icode_NEW_CLASS] = new DoNewClass();
```

##### 5.1.7.3 指令实现类

```java
// Interpreter.java 内部类

/**
 * 获取私有字段值
 * Stack: [instance, fieldName] → value
 */
private static class DoGetPrivateField extends InstructionClass {
    @Override
    public void execute(Context cx, CallFrame frame, int op) {
        String fieldName = (String) frame.stack[state.stackTop--];
        Object instance = frame.stack[state.stackTop--];
        
        // 通过 NativeClass 访问私有字段
        Object value = ScriptRuntime.getPrivateField(
            instance, fieldName, frame.fnOrScript, cx);
        
        frame.stack[++state.stackTop] = value;
    }
}

/**
 * 设置私有字段值
 * Stack: [instance, fieldName, value] → value
 */
private static class DoSetPrivateField extends InstructionClass {
    @Override
    public void execute(Context cx, CallFrame frame, int op) {
        Object value = frame.stack[state.stackTop--];
        String fieldName = (String) frame.stack[state.stackTop--];
        Object instance = frame.stack[state.stackTop--];
        
        // 通过 NativeClass 设置私有字段
        ScriptRuntime.setPrivateField(
            instance, fieldName, value, frame.fnOrScript, cx);
        
        frame.stack[++state.stackTop] = value;
    }
}

/**
 * 创建类对象
 * Stack: [superClass, constructor, protoMethods, staticMethods] → classObject
 */
private static class DoNewClass extends InstructionClass {
    @Override
    public void execute(Context cx, CallFrame frame, int op) {
        // 从栈上获取参数
        Scriptable staticMethods = (Scriptable) frame.stack[state.stackTop--];
        Scriptable protoMethods = (Scriptable) frame.stack[state.stackTop--];
        Function constructor = (Function) frame.stack[state.stackTop--];
        Scriptable superClass = (Scriptable) frame.stack[state.stackTop--];
        
        // 创建类对象
        NativeClass classObj = ScriptRuntime.createClass(
            cx, frame.scope, constructor, superClass, 
            protoMethods, staticMethods);
        
        frame.stack[++state.stackTop] = classObj;
    }
}
```

##### 5.1.7.4 性能影响评估

| 方案 | 性能 | 实现复杂度 | 推荐度 |
|------|------|-----------|--------|
| ScriptRuntime 静态调用 | 中 | 低 | ⭐⭐⭐ 初期推荐 |
| 专用字节码指令 | 高 | 高 | ⭐⭐ 后期优化 |

**建议**: 第一阶段使用 `ScriptRuntime.createClass()` 等静态方法调用，类似现有 `getSuperProp()` 模式。

### 5.2 Node 属性新增 (`Node.java`)

```java
// 在 Node 类中添加属性常量
public static final int
    CLASS_NAME_PROP = LAST_PROP + 1,        // 类名
    PROTOTYPE_METHODS_PROP = CLASS_NAME_PROP + 1,  // 原型方法
    STATIC_METHODS_PROP = PROTOTYPE_METHODS_PROP + 1,  // 静态方法
    CONSTRUCTOR_METHOD = STATIC_METHODS_PROP + 1,    // 构造方法标记
    DERIVED_CONSTRUCTOR = CONSTRUCTOR_METHOD + 1,    // 派生类构造器标记
    HOME_OBJECT_PROP = DERIVED_CONSTRUCTOR + 1,      // super 的 home object
    LAST_PROP = HOME_OBJECT_PROP + 1;
```

### 5.3 AST 节点设计

#### 5.3.0 继承层次分析与选择

**Rhino AST 继承层次：**

```
Node (基类)
└── AstNode (抽象类，提供位置、父节点、toSource、visit 等)
    ├── Jump (代码生成跳转节点)
    │   └── Scope (词法作用域，符号表管理)
    │       └── ScriptNode (脚本/函数基类，收集函数、正则等)
    │           ├── AstRoot (根节点)
    │           └── FunctionNode (函数节点)
    ├── ObjectLiteral ← 直接继承 AstNode
    ├── ArrayLiteral
    └── ... 其他节点
```

**ClassNode 继承选择分析：**

| 方案 | 优点 | 缺点 |
|------|------|------|
| 继承 `AstNode` | 简单清晰，与 ObjectLiteral 一致；Class 不是执行单元 | 需手动处理作用域 |
| 继承 `Scope` | 内置符号表支持；类体有作用域语义 | 语义混乱（Scope 继承 Jump，为代码生成设计） |
| 继承 `ScriptNode` | 可收集嵌套函数 | 不合适，Class 不是脚本 |

**最终选择：继承 `AstNode`**

理由：
1. Class 本身不是执行单元，不像函数有执行上下文
2. 与 ObjectLiteral 结构类似（都是成员容器）
3. 私有字段名在编译时确定，不需要运行时符号查找
4. Scope 的符号表用于变量查找，而类成员访问语义不同
5. 如果需要作用域支持，可在 IRFactory 中创建临时 Scope

#### 5.3.1 ClassNode

**对比 FunctionNode 和 ObjectLiteral 的设计：**

| 特性 | FunctionNode | ObjectLiteral | ClassNode |
|------|--------------|---------------|-----------|
| 父类 | ScriptNode | AstNode | **AstNode** |
| 空列表常量 | NO_PARAMS | NO_ELEMS | **NO_ELEMENTS** |
| hasSideEffects | 未重写 | 未重写 | **返回 true** |
| 便捷方法 | isMethod(), isGetterMethod() | isDestructuring() | **getConstructor(), getMethods()** |

**Rhino 代码风格要求：**
- 所有源文件必须以 MPL 2.0 许可证头开头
- 使用实例初始化块 `{ type = Token.XXX; }` 设置节点类型
- 使用 `assertNotNull()` 进行参数校验
- 空列表使用 `Collections.unmodifiableList(new ArrayList<>())`

```java
/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.mozilla.javascript.Node;
import org.mozilla.javascript.Token;

/**
 * AST node representing an ES6+ class declaration or expression.
 *
 * <p>Node type is {@link Token#CLASS}.
 *
 * <pre>
 * ClassDeclaration:
 *     class Identifier [extends LeftHandSideExpression] { ClassBody }
 * ClassExpression:
 *     class Identifier? [extends LeftHandSideExpression] { ClassBody }
 * ClassBody:
 *     ClassElementList?
 * ClassElementList:
 *     ClassElement
 *     ClassElementList ClassElement
 * ClassElement:
 *     MethodDefinition
 *     static MethodDefinition
 *     FieldDefinition ;
 *     static FieldDefinition ;
 *     ClassStaticBlock
 *     ;  (empty element)
 * </pre>
 *
 * <p>This node type resembles {@link ObjectLiteral} in that it contains
 * a collection of elements, but differs in that it supports inheritance
 * and has special semantics for constructor methods.
 *
 * @see ClassElement
 * @see FunctionNode
 * @see ObjectLiteral
 */
public class ClassNode extends AstNode {

    /** Class declaration (statement context) */
    public static final int CLASS_STATEMENT = 1;

    /** Class expression (expression context) */
    public static final int CLASS_EXPRESSION = 2;

    /** Immutable empty list for classes with no elements */
    private static final List<ClassElement> NO_ELEMENTS =
            Collections.unmodifiableList(new ArrayList<>());

    private Name className;
    private AstNode superClass;           // extends clause (optional)
    private List<ClassElement> elements;   // methods, fields, static blocks
    private int classType;
    private int extendsPosition = -1;
    private int lcPosition = -1;           // left curly position
    private int rcPosition = -1;           // right curly position

    // Cached references for quick access
    private ClassElement constructorElement;

    {
        type = Token.CLASS;
    }

    public ClassNode() {}

    public ClassNode(int pos) {
        super(pos);
    }

    public ClassNode(int pos, int len) {
        super(pos, len);
    }

    // ===== Getters and Setters =====

    public Name getClassName() {
        return className;
    }

    public void setClassName(Name className) {
        this.className = className;
        if (className != null) {
            className.setParent(this);
        }
    }

    /**
     * Returns the class name as a string.
     * @return the class name, or empty string for anonymous class expressions
     */
    public String getName() {
        return className != null ? className.getIdentifier() : "";
    }

    public AstNode getSuperClass() {
        return superClass;
    }

    public void setSuperClass(AstNode superClass) {
        this.superClass = superClass;
        if (superClass != null) {
            superClass.setParent(this);
        }
    }

    /** Returns true if this class has an extends clause */
    public boolean hasSuperClass() {
        return superClass != null;
    }

    /**
     * Returns the element list. Returns an immutable empty list if there are no elements.
     * @return the element list, never null
     */
    public List<ClassElement> getElements() {
        return elements != null ? elements : NO_ELEMENTS;
    }

    /**
     * Sets the element list, and updates the parent of each element.
     * Replaces any existing elements.
     *
     * @param elements the element list. Can be {@code null}.
     */
    public void setElements(List<ClassElement> elements) {
        if (elements == null) {
            this.elements = null;
        } else {
            if (this.elements != null) this.elements.clear();
            for (ClassElement element : elements) {
                addElement(element);
            }
        }
    }

    /**
     * Adds an element to the list, and sets its parent to this node.
     *
     * @param element the class element to append to the end of the list
     * @throws IllegalArgumentException if element is {@code null}
     */
    public void addElement(ClassElement element) {
        assertNotNull(element);
        if (elements == null) {
            elements = new ArrayList<>();
        }
        elements.add(element);
        element.setParent(this);
        
        // Cache constructor reference
        if (element.isConstructor()) {
            constructorElement = element;
        }
    }

    public int getClassType() {
        return classType;
    }

    public void setClassType(int classType) {
        this.classType = classType;
    }

    public boolean isClassStatement() {
        return classType == CLASS_STATEMENT;
    }

    public boolean isClassExpression() {
        return classType == CLASS_EXPRESSION;
    }

    public int getExtendsPosition() {
        return extendsPosition;
    }

    public void setExtendsPosition(int extendsPosition) {
        this.extendsPosition = extendsPosition;
    }

    /** Returns left curly brace position, -1 if missing */
    public int getLcPosition() {
        return lcPosition;
    }

    public void setLcPosition(int lcPosition) {
        this.lcPosition = lcPosition;
    }

    /** Returns right curly brace position, -1 if missing */
    public int getRcPosition() {
        return rcPosition;
    }

    public void setRcPosition(int rcPosition) {
        this.rcPosition = rcPosition;
    }

    // ===== Convenience Methods =====

    /**
     * Returns the constructor element, or null if there is no explicit constructor.
     * @return the constructor ClassElement, or null
     */
    public ClassElement getConstructor() {
        if (constructorElement != null) {
            return constructorElement;
        }
        // Search if not cached
        for (ClassElement element : getElements()) {
            if (element.isConstructor()) {
                constructorElement = element;
                return element;
            }
        }
        return null;
    }

    /**
     * Returns all method elements (including constructor).
     * @return list of method elements
     */
    public List<ClassElement> getMethods() {
        List<ClassElement> methods = new ArrayList<>();
        for (ClassElement element : getElements()) {
            if (element.isMethod()) {
                methods.add(element);
            }
        }
        return methods;
    }

    /**
     * Returns all static method elements.
     * @return list of static method elements
     */
    public List<ClassElement> getStaticMethods() {
        List<ClassElement> methods = new ArrayList<>();
        for (ClassElement element : getElements()) {
            if (element.isMethod() && element.isStatic()) {
                methods.add(element);
            }
        }
        return methods;
    }

    /**
     * Returns all instance method elements (excluding constructor).
     * @return list of instance method elements
     */
    public List<ClassElement> getInstanceMethods() {
        List<ClassElement> methods = new ArrayList<>();
        for (ClassElement element : getElements()) {
            if (element.isMethod() && !element.isStatic() && !element.isConstructor()) {
                methods.add(element);
            }
        }
        return methods;
    }

    /**
     * Returns all field elements.
     * @return list of field elements
     */
    public List<ClassElement> getFields() {
        List<ClassElement> fields = new ArrayList<>();
        for (ClassElement element : getElements()) {
            if (element.isField()) {
                fields.add(element);
            }
        }
        return fields;
    }

    /**
     * Returns all static field elements.
     * @return list of static field elements
     */
    public List<ClassElement> getStaticFields() {
        List<ClassElement> fields = new ArrayList<>();
        for (ClassElement element : getElements()) {
            if (element.isField() && element.isStatic()) {
                fields.add(element);
            }
        }
        return fields;
    }

    /**
     * Returns all instance field elements.
     * @return list of instance field elements
     */
    public List<ClassElement> getInstanceFields() {
        List<ClassElement> fields = new ArrayList<>();
        for (ClassElement element : getElements()) {
            if (element.isField() && !element.isStatic()) {
                fields.add(element);
            }
        }
        return fields;
    }

    /**
     * Returns all static block elements.
     * @return list of static block elements
     */
    public List<ClassElement> getStaticBlocks() {
        List<ClassElement> blocks = new ArrayList<>();
        for (ClassElement element : getElements()) {
            if (element.isStaticBlock()) {
                blocks.add(element);
            }
        }
        return blocks;
    }

    /**
     * Returns true if this class is a derived class (has extends clause).
     * @return true if derived
     */
    public boolean isDerived() {
        return hasSuperClass();
    }

    // ===== hasSideEffects =====

    /**
     * Class declarations and expressions always have side effects
     * because they create a new constructor function and modify
     * the scope chain.
     */
    @Override
    public boolean hasSideEffects() {
        return true;
    }

    // ===== toSource and visit =====

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append("class");
        if (className != null) {
            sb.append(" ");
            sb.append(className.toSource(0));
        }
        if (superClass != null) {
            sb.append(" extends ");
            sb.append(superClass.toSource(0));
        }
        sb.append(" {\n");
        for (ClassElement element : getElements()) {
            sb.append(element.toSource(depth + 1));
            sb.append("\n");
        }
        sb.append(makeIndent(depth));
        sb.append("}");
        return sb.toString();
    }

    /**
     * Visits this node, the class name (if present), the super class (if present),
     * and each class element in source order.
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            if (className != null) {
                className.visit(v);
            }
            if (superClass != null) {
                superClass.visit(v);
            }
            for (ClassElement element : getElements()) {
                element.visit(v);
            }
        }
    }
}
```

#### 5.3.2 ClassElement

**对比 FunctionNode.Form 和 ObjectProperty 的设计：**

| 特性 | FunctionNode.Form | ObjectProperty | ClassElement |
|------|-------------------|----------------|--------------|
| 类型常量 | FUNCTION, GETTER, SETTER, METHOD | - | **METHOD, FIELD, STATIC_BLOCK** |
| 静态标记 | - | - | **isStatic** |
| 私有标记 | - | - | **isPrivate** |
| hasSideEffects | 父类处理 | 父类处理 | **返回 true** |

```java
package org.mozilla.javascript.ast;

import org.mozilla.javascript.Node;
import org.mozilla.javascript.Token;

/**
 * Represents an element within a class body: method, field, or static block.
 *
 * <p>Node type is {@link Token#CLASS_ELEMENT}.
 *
 * <pre>
 * ClassElement:
 *     MethodDefinition
 *     static MethodDefinition
 *     FieldDefinition ;
 *     static FieldDefinition ;
 *     ClassStaticBlock
 *     ;  (empty element, ignored)
 *
 * MethodDefinition:
 *     ClassElementName ( StrictFormalParameters ) { FunctionBody }
 *     * ClassElementName ( StrictFormalParameters ) { GeneratorBody }
 *     get ClassElementName ( ) { FunctionBody }
 *     set ClassElementName ( PropertySetParameterList ) { FunctionBody }
 *
 * FieldDefinition:
 *     ClassElementName Initializer?
 *
 * ClassStaticBlock:
 *     static { ClassStaticBlockBody }
 * </pre>
 *
 * @see ClassNode
 * @see FunctionNode
 */
public class ClassElement extends AstNode {

    /** Element is a method (including getter/setter/constructor) */
    public static final int METHOD = 1;

    /** Element is a field definition */
    public static final int FIELD = 2;

    /** Element is a static initialization block */
    public static final int STATIC_BLOCK = 3;

    private int elementType;
    private boolean isStatic;
    private boolean isPrivate;     // ES2022 私有字段/方法
    private boolean isComputed;    // Computed property key [expr]
    private AstNode key;           // property name (Name, StringLiteral, NumberLiteral, or expression for computed)
    private FunctionNode method;   // for methods (including getter/setter)
    private AstNode fieldValue;    // for fields
    private Block staticBlock;     // for static blocks

    {
        type = Token.CLASS_ELEMENT;
    }

    public ClassElement() {}

    public ClassElement(int pos) {
        super(pos);
    }

    public ClassElement(int pos, int len) {
        super(pos, len);
    }

    // ===== Getters and Setters =====

    public int getElementType() {
        return elementType;
    }

    public void setElementType(int elementType) {
        this.elementType = elementType;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public void setStatic(boolean isStatic) {
        this.isStatic = isStatic;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    public boolean isComputed() {
        return isComputed;
    }

    public void setComputed(boolean isComputed) {
        this.isComputed = isComputed;
    }

    public boolean isMethod() {
        return elementType == METHOD;
    }

    public boolean isField() {
        return elementType == FIELD;
    }

    public boolean isStaticBlock() {
        return elementType == STATIC_BLOCK;
    }

    /**
     * Returns true if this element is the constructor method.
     * A constructor is a method named "constructor" that is not static.
     */
    public boolean isConstructor() {
        return isMethod() && !isStatic && method != null 
            && method.getIntProp(Node.CONSTRUCTOR_METHOD, 0) == 1;
    }

    /**
     * Returns true if this method is a getter.
     */
    public boolean isGetter() {
        return isMethod() && method != null && method.isGetterMethod();
    }

    /**
     * Returns true if this method is a setter.
     */
    public boolean isSetter() {
        return isMethod() && method != null && method.isSetterMethod();
    }

    /**
     * Returns true if this method is a generator (prefixed with *).
     */
    public boolean isGenerator() {
        return isMethod() && method != null && method.isGenerator();
    }

    /**
     * Returns true if this method is async.
     */
    public boolean isAsync() {
        return isMethod() && method != null && method.isAsync();
    }

    /**
     * Returns the property key as a string, or null if it's a computed key.
     * For computed keys, use getKey() to get the expression.
     */
    public String getKeyString() {
        if (isComputed || key == null) {
            return null;
        }
        if (key instanceof Name) {
            return ((Name) key).getIdentifier();
        } else if (key instanceof StringLiteral) {
            return ((StringLiteral) key).getValue();
        } else if (key instanceof NumberLiteral) {
            return String.valueOf(((NumberLiteral) key).getNumber());
        }
        return null;
    }

    public AstNode getKey() {
        return key;
    }

    public void setKey(AstNode key) {
        this.key = key;
        if (key != null) {
            key.setParent(this);
        }
    }

    public FunctionNode getMethod() {
        return method;
    }

    public void setMethod(FunctionNode method) {
        this.method = method;
        if (method != null) {
            method.setParent(this);
        }
    }

    public AstNode getFieldValue() {
        return fieldValue;
    }

    public void setFieldValue(AstNode fieldValue) {
        this.fieldValue = fieldValue;
        if (fieldValue != null) {
            fieldValue.setParent(this);
        }
    }

    public Block getStaticBlock() {
        return staticBlock;
    }

    public void setStaticBlock(Block staticBlock) {
        this.staticBlock = staticBlock;
        if (staticBlock != null) {
            staticBlock.setParent(this);
        }
    }

    // ===== hasSideEffects =====

    /**
     * Class elements always have side effects.
     * - Methods and fields modify the class structure
     * - Static blocks execute code
     */
    @Override
    public boolean hasSideEffects() {
        return true;
    }

    // ===== toSource and visit =====

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        
        // Static keyword (except for static blocks which have their own formatting)
        if (isStatic && elementType != STATIC_BLOCK) {
            sb.append("static ");
        }
        
        switch (elementType) {
            case METHOD:
                appendMethodSource(sb);
                break;
            case FIELD:
                appendFieldSource(sb);
                break;
            case STATIC_BLOCK:
                appendStaticBlockSource(sb);
                break;
        }
        return sb.toString();
    }

    private void appendMethodSource(StringBuilder sb) {
        // Getter/setter prefix
        if (method.isGetterMethod()) {
            sb.append("get ");
        } else if (method.isSetterMethod()) {
            sb.append("set ");
        }
        
        // Generator prefix
        if (method.isGenerator()) {
            sb.append("*");
        }
        
        // Private prefix
        if (isPrivate) {
            sb.append("#");
        }
        
        // Key
        if (isComputed && key != null) {
            sb.append("[");
            sb.append(key.toSource(0));
            sb.append("]");
        } else if (key != null) {
            sb.append(key.toSource(0));
        }
        
        // Parameters and body
        sb.append(method.toSource(0).replaceFirst("^function\\s*", ""));
    }

    private void appendFieldSource(StringBuilder sb) {
        // Private prefix
        if (isPrivate) {
            sb.append("#");
        }
        
        // Key
        if (isComputed && key != null) {
            sb.append("[");
            sb.append(key.toSource(0));
            sb.append("]");
        } else if (key != null) {
            sb.append(key.toSource(0));
        }
        
        // Initializer
        if (fieldValue != null) {
            sb.append(" = ");
            sb.append(fieldValue.toSource(0));
        }
        sb.append(";");
    }

    private void appendStaticBlockSource(StringBuilder sb) {
        sb.append("static ");
        if (staticBlock != null) {
            sb.append(staticBlock.toSource(0));
        } else {
            sb.append("{}");
        }
    }

    /**
     * Visits this node, then the key, and then the appropriate child
     * (method, fieldValue, or staticBlock) depending on element type.
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            if (key != null) {
                key.visit(v);
            }
            if (method != null) {
                method.visit(v);
            }
            if (fieldValue != null) {
                fieldValue.visit(v);
            }
            if (staticBlock != null) {
                staticBlock.visit(v);
            }
        }
    }
}
```

### 5.4 词法分析器修改 (`TokenStream.java`)

#### 5.4.1 现有标识符识别逻辑分析

**当前标识符开始判断（第710行）：**
```java
identifierStart = Character.isUnicodeIdentifierStart(c) || c == '$' || c == '_';
```

**问题**：`#` 字符不被识别为标识符开始，ES2022 私有字段 `#field` 无法被正确解析。

#### 5.4.2 需要修改的内容

**1. 在 `getToken()` 方法中添加 `#` 处理**

```java
// 在标识符识别之前添加（约第693行，@ 符号处理之后）
if (c == '@') return Token.XMLATTR;

// ===== 新增：私有字段 # 处理 =====
if (c == '#') {
    // 检查是否在类体内部（由 Parser 上下文决定）
    stringBufferTop = 0;
    addToString('#');
    c = getChar();
    
    // # 后面必须跟着有效的标识符开始字符
    if (Character.isUnicodeIdentifierStart(c) || c == '_' || c == '$') {
        addToString(c);
        for (;;) {
            c = getChar();
            if (c == EOF_CHAR
                    || c == BYTE_ORDER_MARK
                    || !(Character.isUnicodeIdentifierPart(c) || c == '$')) {
                break;
            }
            addToString(c);
        }
        ungetChar(c);
        this.string = getStringFromBuffer();
        return Token.PRIVATE_FIELD;
    } else {
        // 单独的 # 是语法错误
        parser.reportError("msg.invalid.private.field");
        return Token.ERROR;
    }
}
// ===== 私有字段处理结束 =====

// identifier/keyword/instanceof?
// watch out for starting with a <backslash>
boolean identifierStart;
```

**2. 添加上下文检查（可选，用于严格模式验证）**

```java
// 在 TokenStream 类中添加字段
private boolean inClassBody = false;

// 添加 setter 方法
void setInClassBody(boolean inClassBody) {
    this.inClassBody = inClassBody;
}
```

**3. 需要添加的错误消息**

```properties
# messages.properties
msg.invalid.private.field=Invalid private field name
msg.private.field.outside.class=Private field '#%s' must be declared in an enclosing class
```

#### 5.4.3 工作量评估

| 修改项 | 代码量 | 复杂度 |
|--------|--------|--------|
| `#` 字符识别 | ~25 行 | 低 |
| 上下文检查（可选） | ~10 行 | 低 |
| 错误消息 | ~2 行 | 低 |
| 测试用例 | ~50 行 | 中 |
| **合计** | **~87 行** | **低** |

**注意**：私有字段的词法分析相对简单，主要复杂度在 Parser 和运行时的访问控制。

---

### 5.5 Parser 解析方法 (`Parser.java`)

#### 5.5.1 关键字现状分析

**当前 `class` 和 `extends` 关键字状态（来自 `TokenStream.java`）：**

```java
// stringToKeywordForES 方法中（第500-510行）
Id_class = Token.RESERVED,    // class 当前是保留字
Id_extends = Token.RESERVED,  // extends 当前是保留字
```

**结论：**
- ✅ `class` 和 `extends` 当前仅为保留字，**未被占用**
- ✅ 添加新 Token 后可直接使用，无需担心冲突
- ⚠️ 需要将 `Id_class` 和 `Id_extends` 的返回值从 `Token.RESERVED` 改为新的 Token 类型

**修改 `TokenStream.java` 中的 `stringToKeywordForES` 方法：**

```java
// 修改前
Id_class = Token.RESERVED,
Id_extends = Token.RESERVED,

// 修改后
Id_class = Token.CLASS,
Id_extends = Token.EXTENDS,
```

#### 5.5.2 作用域管理逻辑复用

**现有作用域管理方法（来自 `Parser.java`）：**

| 方法 | 位置 | 用途 |
|------|------|------|
| `pushScope(Scope scope)` | L493 | 进入新作用域 |
| `popScope()` | L505 | 离开当前作用域 |
| `defineSymbol(int declType, String name)` | L2539 | 在当前作用域定义符号 |
| `inUseStrictDirective` | L141 | 严格模式标志 |

**ClassNode 需要复用的逻辑：**

```java
// 类声明时定义类名（类似函数声明）
if (classType == ClassNode.CLASS_STATEMENT && className != null) {
    defineSymbol(Token.LET, className.getIdentifier());
}

// 类体不需要新的词法作用域（与函数不同）
// 但构造函数和方法内部需要各自的作用域
```

**与 FunctionNode 的对比：**

| 特性 | FunctionNode | ClassNode |
|------|--------------|-----------|
| 类名作用域 | 函数声明时定义 | 类声明时定义（LET） |
| 函数体作用域 | 需要新的词法作用域 | 不需要（类体不是执行单元） |
| 方法体作用域 | - | 每个方法各自的作用域 |

#### 5.5.3 严格模式处理

**ES6 规范要求：类体始终在严格模式下执行**

> ECMAScript 2022, 15.7.2 Static Semantics: IsStrict
> - ClassBody : ClassElementList
> - Return true.

**实现方案：**

```java
private ClassNode classDefinition(int classType) throws IOException {
    // ... 省略前面代码 ...
    
    // 类体始终在严格模式下
    boolean savedStrictMode = inUseStrictDirective;
    inUseStrictDirective = true;  // 类体强制严格模式
    
    try {
        mustMatchToken(Token.LC, "msg.no.brace.class", true);
        parseClassBody(classNode);
        mustMatchToken(Token.RC, "msg.no.brace.after.class", true);
    } finally {
        inUseStrictDirective = savedStrictMode;  // 恢复外部严格模式状态
    }
    
    return classNode;
}
```

**注意事项：**
- 构造函数和方法继承类体的严格模式
- 静态块同样在严格模式下执行
- 私有字段名称检查（不能是 `#constructor`）需要在严格模式下进行

#### 5.5.4 错误消息命名规范

**现有规范（来自 `Messages.properties`）：**

| 消息键模式 | 示例 |
|------------|------|
| `msg.no.<expect>` | `msg.no.brace.body`, `msg.no.paren.parms` |
| `msg.bad.<context>` | `msg.bad.prop`, `msg.bad.var` |
| `msg.<context>.<action>` | `msg.class.not.new` (计划中) |

**计划中新增的错误消息：**

```properties
# Class-related error messages
msg.no.brace.class=Expected '{' after class declaration
msg.no.brace.after.class=Expected '}' at end of class body
msg.no.brace.static.block=Expected '}' at end of static block
msg.bad.class.element=Invalid class element
msg.bad.getter.setter=Invalid getter or setter definition
msg.constructor.static=Class constructor may not be static
msg.constructor.generator=Class constructor may not be a generator
msg.constructor.getter.setter=Class constructor may not be a getter or setter
msg.class.not.new=Class constructor cannot be called without 'new'
msg.super.not.called=Must call super() in derived constructor before accessing 'this'
msg.super.already.called=super() has already been called
msg.private.field.access=Cannot access private field '{0}' from outside class
msg.duplicate.constructor=Duplicate constructor in class
msg.private.constructor=Private field '#constructor' is not allowed
msg.invalid.private.field=Invalid private field name
```

**命名规范检查：** ✅ 所有消息键符合现有规范

#### 5.5.5 Parser 修改风险点

| 风险点 | 影响 | 缓解措施 |
|--------|------|----------|
| `class`/`extends` 关键字修改 | 低 - 已是保留字 | 仅需更改返回的 Token 类型 |
| 严格模式状态管理 | 中 - 需正确保存/恢复 | 使用 try-finally 确保恢复 |
| `super` 上下文检查 | 中 - 需追踪是否在类方法内 | 使用 `insideMethod` 标志 |
| 私有字段访问控制 | 高 - 需在编译时检查 | 在 Parser 和 IRFactory 两层检查 |
| 默认构造器生成 | 中 - 需在 IRFactory 处理 | 自动生成调用 `super(...args)` |

#### 5.5.6 解析方法框架

```java
/**
 * Parse a class declaration or expression.
 * 
 * <pre>
 * ClassDeclaration: class Identifier [extends LeftHandSideExpression] { ClassBody }
 * ClassExpression: class Identifier? [extends LeftHandSideExpression] { ClassBody }
 * </pre>
 */
private ClassNode classDefinition(int classType) throws IOException {
    int pos = ts.tokenBeg;
    int lineno = lineNumber(), column = columnNumber();
    
    ClassNode classNode = new ClassNode(pos);
    classNode.setClassType(classType);
    classNode.setLineColumnNumber(lineno, column);
    
    // Parse optional class name
    if (peekToken() == Token.NAME) {
        consumeToken();
        Name name = createNameNode();
        classNode.setClassName(name);
        
        // For class declarations, define the class name in the enclosing scope
        if (classType == ClassNode.CLASS_STATEMENT) {
            defineSymbol(Token.LET, name.getIdentifier());
        }
    }
    
    // Parse optional extends clause
    if (matchToken(Token.EXTENDS, true)) {
        classNode.setExtendsPosition(ts.tokenBeg - pos);
        AstNode superClass = memberExpr(false);
        classNode.setSuperClass(superClass);
    }
    
    // Class body is always in strict mode (ES6 spec)
    boolean savedStrictMode = inUseStrictDirective;
    inUseStrictDirective = true;
    
    try {
        // Parse class body
        mustMatchToken(Token.LC, "msg.no.brace.class", true);
        classNode.setLcPosition(ts.tokenBeg - pos);
        
        parseClassBody(classNode);
        
        mustMatchToken(Token.RC, "msg.no.brace.after.class", true);
        classNode.setRcPosition(ts.tokenBeg - pos);
        classNode.setLength(ts.tokenEnd - pos);
    } finally {
        inUseStrictDirective = savedStrictMode;
    }
    
    return classNode;
}

/**
 * Parse the body of a class.
 */
private void parseClassBody(ClassNode classNode) throws IOException {
    boolean hasConstructor = false;
    
    while (peekToken() != Token.RC && peekToken() != Token.EOF) {
        // Skip semicolons (empty class elements are allowed)
        if (matchToken(Token.SEMI, true)) {
            continue;
        }
        
        ClassElement element = parseClassElement(classNode);
        if (element != null) {
            // Check for duplicate constructor
            if (element.isConstructor()) {
                if (hasConstructor) {
                    reportError("msg.duplicate.constructor");
                }
                hasConstructor = true;
            }
            classNode.addElement(element);
        }
    }
}

/**
 * Parse a single class element (method, field, or static block).
 */
private ClassElement parseClassElement(ClassNode classNode) throws IOException {
    int pos = ts.tokenBeg;
    boolean isStatic = false;
    boolean isGenerator = false;
    boolean isAsync = false;
    boolean isPrivate = false;
    
    // Check for 'static' keyword
    if (peekToken() == Token.NAME && "static".equals(ts.getString())) {
        int savedToken = currentFlaggedToken;
        consumeToken();
        int next = peekToken();
        
        if (next == Token.LC) {
            // static { ... } - static initialization block
            return parseStaticBlock(pos);
        }
        
        // Check for async static method
        if (next == Token.NAME && "async".equals(ts.getString())) {
            isAsync = true;
            consumeToken();
        }
        
        isStatic = true;
    }
    
    // Check for async method (non-static)
    if (!isStatic && peekToken() == Token.NAME && "async".equals(ts.getString())) {
        isAsync = true;
        consumeToken();
    }
    
    // Check for generator method (*)
    if (peekToken() == Token.MUL) {
        consumeToken();
        isGenerator = true;
    }
    
    // Check for private field/method (#)
    if (peekToken() == Token.PRIVATE_FIELD) {
        isPrivate = true;
        consumeToken();
    }
    
    // Parse property key
    AstNode key = parseClassElementKey();
    if (key == null) {
        reportError("msg.bad.class.element");
        return null;
    }
    
    // Check for private field named 'constructor'
    if (isPrivate && key instanceof Name 
            && "constructor".equals(((Name) key).getIdentifier())) {
        reportError("msg.private.constructor");
    }
    
    // Determine element type based on what follows
    int next = peekToken();
    
    if (next == Token.LP) {
        // Method definition
        return parseClassMethod(pos, key, isStatic, isGenerator, isAsync, 
                                isPrivate, classNode);
    } else if (next == Token.ASSIGN) {
        // Field definition with initializer
        return parseClassField(pos, key, isStatic, isPrivate);
    } else if (next == Token.SEMI || next == Token.RC) {
        // Field definition without initializer
        return parseClassField(pos, key, isStatic, isPrivate);
    } else if (key instanceof Name) {
        String keyName = ((Name) key).getIdentifier();
        if ("get".equals(keyName) || "set".equals(keyName)) {
            // Getter or setter
            return parseGetterSetter(pos, keyName, isStatic, isPrivate, classNode);
        }
    }
    
    reportError("msg.bad.class.element");
    return null;
}

在 `Parser.java` 中添加以下方法：

```java
/**
 * Parse a class declaration or expression.
 * 
 * <pre>
 * ClassDeclaration: class Identifier [extends LeftHandSideExpression] { ClassBody }
 * ClassExpression: class Identifier? [extends LeftHandSideExpression] { ClassBody }
 * </pre>
 */
private ClassNode classDefinition(int classType) throws IOException {
    int pos = ts.tokenBeg;
    int lineno = lineNumber(), column = columnNumber();
    
    ClassNode classNode = new ClassNode(pos);
    classNode.setClassType(classType);
    classNode.setLineColumnNumber(lineno, column);
    
    // Parse optional class name
    if (peekToken() == Token.NAME) {
        consumeToken();
        Name name = createNameNode();
        classNode.setClassName(name);
        
        // For class declarations, define the class name in the enclosing scope
        if (classType == ClassNode.CLASS_STATEMENT) {
            defineSymbol(Token.LET, name.getIdentifier());
        }
    }
    
    // Parse optional extends clause
    if (matchToken(Token.EXTENDS, true)) {
        classNode.setExtendsPosition(ts.tokenBeg - pos);
        AstNode superClass = memberExpr(false);
        classNode.setSuperClass(superClass);
    }
    
    // Parse class body
    mustMatchToken(Token.LC, "msg.no.brace.class", true);
    classNode.setLcPosition(ts.tokenBeg - pos);
    
    parseClassBody(classNode);
    
    mustMatchToken(Token.RC, "msg.no.brace.after.class", true);
    classNode.setRcPosition(ts.tokenBeg - pos);
    classNode.setLength(ts.tokenEnd - pos);
    
    return classNode;
}

/**
 * Parse the body of a class.
 */
private void parseClassBody(ClassNode classNode) throws IOException {
    while (peekToken() != Token.RC && peekToken() != Token.EOF) {
        // Skip semicolons (empty class elements are allowed)
        if (matchToken(Token.SEMI, true)) {
            continue;
        }
        
        ClassElement element = parseClassElement(classNode);
        if (element != null) {
            classNode.addElement(element);
        }
    }
}

/**
 * Parse a single class element (method, field, or static block).
 */
private ClassElement parseClassElement(ClassNode classNode) throws IOException {
    int pos = ts.tokenBeg;
    boolean isStatic = false;
    boolean isGenerator = false;
    boolean isAsync = false;
    boolean isPrivate = false;
    
    // Check for 'static' keyword
    if (peekToken() == Token.NAME && "static".equals(ts.getString())) {
        // Lookahead to distinguish static method/field from static block
        int savedToken = currentFlaggedToken;
        consumeToken();
        int next = peekToken();
        
        if (next == Token.LC) {
            // static { ... } - static initialization block
            return parseStaticBlock(pos);
        }
        
        // Check for async static method
        if (next == Token.NAME && "async".equals(ts.getString())) {
            isAsync = true;
            consumeToken();
        }
        
        isStatic = true;
    }
    
    // Check for async method (non-static)
    if (!isStatic && peekToken() == Token.NAME && "async".equals(ts.getString())) {
        isAsync = true;
        consumeToken();
    }
    
    // Check for generator method (*)
    if (peekToken() == Token.MUL) {
        consumeToken();
        isGenerator = true;
    }
    
    // Check for private field/method (#)
    if (peekToken() == Token.PRIVATE_FIELD || 
        (peekToken() == Token.NAME && ts.getString().startsWith("#"))) {
        isPrivate = true;
        if (peekToken() == Token.PRIVATE_FIELD) {
            consumeToken();
        } else {
            // Handle # as part of identifier
        }
    }
    
    // Parse property key
    AstNode key = parseClassElementKey();
    if (key == null) {
        reportError("msg.bad.class.element");
        return null;
    }
    
    // Determine element type based on what follows
    int next = peekToken();
    
    if (next == Token.LP) {
        // Method definition
        return parseClassMethod(pos, key, isStatic, isGenerator, isAsync, 
                                isPrivate, classNode);
    } else if (next == Token.ASSIGN) {
        // Field definition with initializer
        return parseClassField(pos, key, isStatic, isPrivate);
    } else if (next == Token.SEMI || next == Token.RC) {
        // Field definition without initializer
        return parseClassField(pos, key, isStatic, isPrivate);
    } else if (key instanceof Name) {
        String keyName = ((Name) key).getIdentifier();
        if ("get".equals(keyName) || "set".equals(keyName)) {
            // Getter or setter
            return parseGetterSetter(pos, keyName, isStatic, isPrivate, classNode);
        }
    }
    
    reportError("msg.bad.class.element");
    return null;
}

/**
 * Parse property key for class element.
 */
private AstNode parseClassElementKey() throws IOException {
    int tt = peekToken();
    
    switch (tt) {
        case Token.NAME:
            consumeToken();
            return createNameNode();
        case Token.STRING:
            consumeToken();
            return createStringLiteral();
        case Token.NUMBER:
            consumeToken();
            return createNumberLiteral();
        case Token.LB:
            // Computed property key [expression]
            consumeToken();
            AstNode expr = assignExpr();
            mustMatchToken(Token.RB, "msg.no.bracket.property", true);
            return expr;
        default:
            return null;
    }
}

/**
 * Parse a method in a class body.
 */
private ClassElement parseClassMethod(int pos, AstNode key, boolean isStatic, 
        boolean isGenerator, boolean isAsync, boolean isPrivate, 
        ClassNode classNode) throws IOException {
    
    ClassElement element = new ClassElement(pos);
    element.setElementType(ClassElement.METHOD);
    element.setStatic(isStatic);
    element.setPrivate(isPrivate);
    element.setKey(key);
    
    // Create function node for the method
    FunctionNode method = function(FunctionNode.FUNCTION_EXPRESSION, true);
    
    if (isGenerator) {
        method.setIsES6Generator();
    }
    if (isAsync) {
        method.setIsAsync();
    }
    
    method.setFunctionIsNormalMethod();
    
    // Check for constructor
    if (key instanceof Name && "constructor".equals(((Name) key).getIdentifier())) {
        if (isStatic) {
            reportError("msg.constructor.static");
        }
        if (isGenerator) {
            reportError("msg.constructor.generator");
        }
        method.putIntProp(Node.CONSTRUCTOR_METHOD, 1);
        
        // If class has extends, mark constructor as derived
        if (classNode.hasSuperClass()) {
            method.putIntProp(Node.DERIVED_CONSTRUCTOR, 1);
        }
    }
    
    element.setMethod(method);
    element.setLength(ts.tokenEnd - pos);
    
    return element;
}

/**
 * Parse a field definition in a class body.
 */
private ClassElement parseClassField(int pos, AstNode key, boolean isStatic, 
        boolean isPrivate) throws IOException {
    
    ClassElement element = new ClassElement(pos);
    element.setElementType(ClassElement.FIELD);
    element.setStatic(isStatic);
    element.setPrivate(isPrivate);
    element.setKey(key);
    
    if (matchToken(Token.ASSIGN, true)) {
        AstNode value = assignExpr();
        element.setFieldValue(value);
    }
    
    // Semicolon is optional but recommended
    matchToken(Token.SEMI, true);
    
    element.setLength(ts.tokenEnd - pos);
    return element;
}

/**
 * Parse a getter or setter.
 */
private ClassElement parseGetterSetter(int pos, String kind, boolean isStatic, 
        boolean isPrivate, ClassNode classNode) throws IOException {
    
    ClassElement element = new ClassElement(pos);
    element.setElementType(ClassElement.METHOD);
    element.setStatic(isStatic);
    element.setPrivate(isPrivate);
    
    // Parse the actual property key
    AstNode key = parseClassElementKey();
    if (key == null) {
        reportError("msg.bad.getter.setter");
        return null;
    }
    element.setKey(key);
    
    // Parse the function
    FunctionNode method = function(FunctionNode.FUNCTION_EXPRESSION, true);
    
    if ("get".equals(kind)) {
        method.setFunctionIsGetterMethod();
        element.setMethod(method);
    } else {
        method.setFunctionIsSetterMethod();
        element.setMethod(method);
    }
    
    element.setLength(ts.tokenEnd - pos);
    return element;
}

/**
 * Parse a static initialization block.
 */
private ClassElement parseStaticBlock(int pos) throws IOException {
    ClassElement element = new ClassElement(pos);
    element.setElementType(ClassElement.STATIC_BLOCK);
    element.setStatic(true);
    
    consumeToken();  // consume '{'
    Block block = new Block(ts.tokenBeg);
    pushScope(block);
    try {
        statements(block);
        mustMatchToken(Token.RC, "msg.no.brace.static.block", true);
    } finally {
        popScope();
    }
    
    element.setStaticBlock(block);
    element.setLength(ts.tokenEnd - pos);
    return element;
}
```

#### 5.5.6 TDZ 检查实现（v1.11 新增）

##### 5.5.6.1 类声明的 TDZ 行为

类声明具有"提升但未初始化"特性，与 `let`/`const` 行为一致：

```javascript
// TDZ 示例
{
    console.log(MyClass);  // ReferenceError: Cannot access 'MyClass' before initialization
    class MyClass {}
}

// 类名在类内部可用
class A {
    static create() { return new A(); }  // OK
}
```

##### 5.5.6.2 实现位置

| 文件 | 方法 | 说明 |
|------|------|------|
| `Parser.java` | `classDefinition()` | 标记 TDZ 开始位置 |
| `ScriptRuntime.java` | `getVarWithTDZCheck()` | TDZ 检查方法 |
| `Node.java` | `TDZ_CHECK_PROP` | TDZ 标记属性 |

##### 5.5.6.3 TDZ 检查方法实现

```java
// ScriptRuntime.java 新增

/**
 * 检查变量是否在 TDZ (Temporal Dead Zone) 状态，并返回其值。
 * 
 * @param scope 当前作用域
 * @param name 变量名
 * @param cx 当前上下文
 * @return 变量值
 * @throws EcmaError 如果变量在 TDZ 状态
 */
public static Object getVarWithTDZCheck(Scriptable scope, String name, Context cx) {
    // 使用 UniqueTag 标记 TDZ 状态
    Object value = scope.get(name, scope);
    
    // 检查是否为 TDZ 标记值
    if (value == UniqueTag.NOT_FOUND) {
        // 变量未声明
        throw ScriptRuntime.notFoundError(scope, name);
    }
    
    // TDZ 标记值：使用特定的 UniqueTag 标记
    if (value == UniqueTag.TDZ_VALUE) {
        throw ScriptRuntime.constructError(
            "ReferenceError", 
            ScriptRuntime.getMessageById("msg.tdz.access", name));
    }
    
    return value;
}

/**
 * 标记变量进入 TDZ 状态（在声明前）。
 * 在作用域中创建变量槽位但标记为不可访问。
 */
public static void markTDZSlot(Scriptable scope, String name) {
    // 使用特殊的 UniqueTag 标记 TDZ 状态
    scope.put(name, scope, UniqueTag.TDZ_VALUE);
}

/**
 * 清除 TDZ 标记，变量变为可访问。
 */
public static void clearTDZSlot(Scriptable scope, String name, Object value) {
    scope.put(name, scope, value);
}
```

##### 5.5.6.4 Node 属性定义

```java
// Node.java 新增属性
public static final int
    // ... 现有属性 ...
    TDZ_START_PROP = LAST_PROP + 1,     // TDZ 开始位置（行号）
    LAST_PROP = TDZ_START_PROP + 1;
```

##### 5.5.6.5 UniqueTag 扩展

```java
// UniqueTag.java 新增
public static final UniqueTag TDZ_VALUE = new UniqueTag("TDZ_VALUE");
```

##### 5.5.6.6 Parser 中标记 TDZ

```java
// Parser.java - classDefinition() 方法开头

private ClassNode classDefinition(boolean isExpr) throws IOException {
    int pos = ts.tokenBeg;
    String className = null;
    
    // 解析类名（如果有）
    if (ts.matchToken(Token.NAME)) {
        className = ts.getString();
        
        // 标记 TDZ 开始 - 类名在声明前不可访问
        if (!isExpr || className != null) {
            currentScope.put(className, currentScope, UniqueTag.TDZ_VALUE);
        }
    }
    
    // ... 其余解析逻辑 ...
    
    // 类定义完成后，清除 TDZ 标记
    if (className != null) {
        currentScope.put(className, currentScope, classNode);
    }
    
    return classNode;
}
```

### 5.6 IRFactory 转换逻辑与实现分析 (`IRFactory.java`)

本节包含两部分：
1. **转换逻辑实现方案**：如何将 ClassNode 转换为 IR
2. **现有 super 支持分析**：Rhino 已有的 super 基础设施

#### 5.6.1 转换逻辑实现方案

```java
/**
 * Transform a class declaration/expression to IR.
 * 
 * <p>A class is desugared into:
 * <ol>
 *   <li>A constructor function</li>
 *   <li>Prototype methods attached to Constructor.prototype</li>
 *   <li>Static methods attached directly to Constructor</li>
 *   <li>Field initializers injected into constructor</li>
 *   <li>Static field initializers executed immediately</li>
 *   <li>Static blocks executed immediately</li>
 * </ol>
 */
private Node transformClass(ClassNode classNode) {
    int lineno = classNode.getLineno();
    
    // Create a scope for the class transformation
    Scope classScope = parser.createScopeNode(Token.CLASS, lineno, 0);
    parser.pushScope(classScope);
    
    try {
        String className = classNode.getName();
        
        // Step 1: Create parent class reference (for extends)
        Node superClassRef = null;
        if (classNode.hasSuperClass()) {
            superClassRef = transform(classNode.getSuperClass());
        }
        
        // Step 2: Find or create the constructor
        FunctionNode constructor = findOrCreateConstructor(classNode);
        
        // Step 3: Process class elements
        Node prototypeMethods = new Node(Token.OBJECTLIT);
        Node staticMethods = new Node(Token.OBJECTLIT);
        List<Node> fieldInitializers = new ArrayList<>();
        List<Node> staticFieldInitializers = new ArrayList<>();
        List<Node> staticBlocks = new ArrayList<>();
        
        for (ClassElement element : classNode.getElements()) {
            switch (element.getElementType()) {
                case ClassElement.METHOD:
                    Node methodNode = transformClassMethod(element);
                    if (element.isStatic()) {
                        staticMethods.addChildToBack(methodNode);
                    } else {
                        prototypeMethods.addChildToBack(methodNode);
                    }
                    break;
                    
                case ClassElement.FIELD:
                    Node fieldInit = transformClassField(element, className);
                    if (element.isStatic()) {
                        staticFieldInitializers.add(fieldInit);
                    } else {
                        fieldInitializers.add(fieldInit);
                    }
                    break;
                    
                case ClassElement.STATIC_BLOCK:
                    staticBlocks.add(transform(element.getStaticBlock()));
                    break;
            }
        }
        
        // Step 4: Inject field initializers into constructor
        if (!fieldInitializers.isEmpty()) {
            injectFieldInitializers(constructor, fieldInitializers, classNode);
        }
        
        // Step 5: Create the class object
        Node classObj = new Node(Token.NEW_CLASS);
        classObj.putProp(Node.CLASS_NAME_PROP, className);
        
        if (superClassRef != null) {
            classObj.addChildToBack(superClassRef);
        }
        
        classObj.putProp(Node.PROTOTYPE_METHODS_PROP, prototypeMethods);
        classObj.putProp(Node.STATIC_METHODS_PROP, staticMethods);
        classObj.addChildToBack(transformFunction(constructor));
        
        classScope.addChildToBack(classObj);
        
        // Step 6: Execute static field initializers and blocks
        for (Node init : staticFieldInitializers) {
            classScope.addChildToBack(init);
        }
        for (Node block : staticBlocks) {
            classScope.addChildToBack(block);
        }
        
        return classScope;
        
    } finally {
        parser.popScope();
    }
}

/**
 * Find the constructor method or create a default one.
 */
private FunctionNode findOrCreateConstructor(ClassNode classNode) {
    for (ClassElement element : classNode.getElements()) {
        if (element.isConstructor()) {
            return element.getMethod();
        }
    }
    
    // Create default constructor
    FunctionNode constructor = new FunctionNode(0);
    constructor.setFunctionName("constructor");
    constructor.setFunctionType(FunctionNode.FUNCTION_EXPRESSION);
    constructor.setFunctionIsNormalMethod();
    constructor.putIntProp(Node.CONSTRUCTOR_METHOD, 1);
    
    // For derived classes, default constructor calls super(...args)
    if (classNode.hasSuperClass()) {
        constructor.putIntProp(Node.DERIVED_CONSTRUCTOR, 1);
        // Create body with super(...args) call
        Block body = new Block(0);
        // TODO: Add super(...args) call
        constructor.setBody(body);
    }
    
    return constructor;
}

/**
 * Transform a class method to IR.
 */
private Node transformClassMethod(ClassElement element) {
    FunctionNode method = element.getMethod();
    Node methodNode = transformFunction(method);
    
    // Create property assignment node
    Node propNode = new Node(Token.COLON);
    propNode.addChildToBack(new Node(Token.STRING, element.getKey().getString()));
    propNode.addChildToBack(methodNode);
    
    return propNode;
}

/**
 * Transform a class field to IR.
 */
private Node transformClassField(ClassElement element, String className) {
    // this.field = value
    Node assign = new Node(Token.ASSIGN);
    
    Node thisNode = new Node(Token.THIS);
    Node fieldAccess = new Node(Token.GETPROP);
    fieldAccess.addChildToBack(thisNode);
    fieldAccess.addChildToBack(new Node(Token.STRING, element.getKey().getString()));
    
    assign.addChildToBack(fieldAccess);
    
    if (element.getFieldValue() != null) {
        assign.addChildToBack(transform(element.getFieldValue()));
    } else {
        assign.addChildToBack(new Node(Token.UNDEFINED));
    }
    
    return assign;
}

/**
 * Inject field initializers into constructor body.
 * For derived classes, initializers go after super() call.
 */
private void injectFieldInitializers(FunctionNode constructor, 
        List<Node> initializers, ClassNode classNode) {
    Node body = constructor.getBody();
    
    if (classNode.hasSuperClass()) {
        // For derived classes, find super() call and insert after
        int superCallIndex = findSuperCall(body);
        if (superCallIndex >= 0) {
            Node superCall = body.getChildAtIndex(superCallIndex);
            for (Node init : initializers) {
                body.addChildAfter(init, superCall);
            }
            return;
        }
        // If no super() found, it will be caught at runtime
    }
    
    // For non-derived classes, insert at beginning
    for (int i = initializers.size() - 1; i >= 0; i--) {
        body.addChildToFront(initializers.get(i));
    }
}

/**
 * Find the super() call in a constructor body.
 */
private int findSuperCall(Node body) {
    int index = 0;
    Node child = body.getFirstChild();
    while (child != null) {
        if (isSuperCall(child)) {
            return index;
        }
        child = child.getNext();
        index++;
    }
    return -1;
}

/**
 * Check if a node is a super() call.
 */
private boolean isSuperCall(Node node) {
    if (node.getType() == Token.EXPR_RESULT || node.getType() == Token.EXPR_VOID) {
        Node expr = node.getFirstChild();
        if (expr != null && expr.getType() == Token.CALL) {
            Node target = expr.getFirstChild();
            return target != null && target.getType() == Token.SUPER;
        }
    }
    return false;
}
```

#### 5.6.2 Node 节点类型分析

**现有属性常量（来自 `Node.java`）：**

```java
// 属性常量定义（第 22-74 行）
FUNCTION_PROP = 1,
LOCAL_PROP = 2,
// ... 省略中间 ...
SUPER_PROPERTY_ACCESS = 31,    // ✅ 已存在
NUMBER_OF_SPREAD = 32,
OBJECT_REST_PROP = 33,
LAST_PROP = OBJECT_REST_PROP;  // 最后一个属性

// 节点类型使用 Token 常量
public Node(int nodeType) {
    type = nodeType;  // 直接使用 Token 类型
}
```

**关于 NEW_CLASS：**
- ❌ `NEW_CLASS` 不是 Node 常量，也不需要单独定义
- ✅ Node 使用 `Token.NEW_CLASS` 作为节点类型
- ✅ 创建方式：`new Node(Token.NEW_CLASS, ...)`

**需要添加的 Token 常量（已在 5.3 节规划）：**
```java
// Token.java 新增
CLASS = 164,
EXTENDS = 165,
CLASS_ELEMENT = 166,
PRIVATE_FIELD = 167,
NEW_CLASS = 168,      // 用于类表达式创建实例
STATIC_BLOCK = 169,
```

#### 5.6.3 函数体操作安全性分析

**IRFactory.transformFunction() 现有机制（L640-730）：**

```java
private Node transformFunction(FunctionNode fn) {
    // ... 省略头部处理 ...
    
    Node body = transform(fn.getBody());
    
    /* ✅ 默认参数注入 - 已有类似机制 */
    List<Object> defaultParams = fn.getDefaultParams();
    if (defaultParams != null) {
        for (int i = defaultParams.size() - 1; i > 0; ) {
            // 创建参数初始化节点
            Node paramInit = createIf(...);
            if (fn.isGenerator()) {
                paramInitBlock.addChildToFront(paramInit);
            } else {
                body.addChildToFront(paramInit);  // ✅ 直接添加到函数体开头
            }
        }
    }
    
    /* ✅ 解构参数注入 - 已有类似机制 */
    if (destructuring != null) {
        body.addChildToFront(new Node(Token.EXPR_VOID, destructuring));
    }
    
    return initFunction(fn, index, body, syntheticType);
}
```

**字段注入实现方案：**

```java
// 在 transformClass() 中调用，注入字段初始化
private void injectFieldInitializers(FunctionNode constructor, 
                                      List<ClassElement> fields) {
    Node body = constructor.getBody();
    int lineno = body.getLineno();
    int column = body.getColumn();
    
    // 收集字段初始化器
    List<Node> initializers = new ArrayList<>();
    for (ClassElement field : fields) {
        if (!field.isStatic() && field.getValue() != null) {
            // this.fieldName = value
            Node thisNode = new Node(Token.THIS);
            Node fieldName = Node.newString(field.getKey().getString());
            Node value = transform(field.getValue());
            Node assign = createAssignment(Token.ASSIGN, 
                new Node(Token.GETPROP, thisNode, fieldName), 
                value);
            initializers.add(new Node(Token.EXPR_VOID, assign, lineno, column));
        }
    }
    
    // 对于派生类，在 super() 调用之后插入
    // 对于非派生类，在函数体开头插入
    if (hasSuperClass) {
        int superIndex = findSuperCall(body);
        if (superIndex >= 0) {
            // 在 super() 之后插入
            for (Node init : initializers) {
                body.addChildAfter(init, body.getChildAtIndex(superIndex));
            }
        }
    } else {
        // 在开头插入（逆序以保持原始顺序）
        for (int i = initializers.size() - 1; i >= 0; i--) {
            body.addChildToFront(initializers.get(i));
        }
    }
}
```

**安全性评估：**

| 方面 | 评估 | 说明 |
|------|------|------|
| 优化器影响 | ✅ 安全 | 注入发生在 IR 转换阶段，优化器在之后运行 |
| 行号信息 | ⚠️ 需注意 | 使用字段声明位置的行号 |
| 调试体验 | ✅ 良好 | 字段初始化显示为独立语句 |
| 现有模式 | ✅ 兼容 | 与默认参数、解构参数使用相同机制 |

#### 5.6.4 现有 super 支持 - 重大发现！

**Rhino 已经完整支持 ES6 super 关键字！**

这意味着实现类继承的复杂度大幅降低。

##### Token 层支持（Token.java）

```java
// L76-83：super 相关操作 Token
GETPROP_SUPER = GETPROPNOWARN + 1,
GETPROPNOWARN_SUPER = GETPROP_SUPER + 1,
SETPROP_SUPER = SETPROP + 1,
GETELEM_SUPER = GETELEM + 1,
SETELEM_SUPER = SETELEM + 1,

// L121：super 关键字
SUPER = YIELD + 1,  // ES6 super keyword
```

##### Node 层支持（Node.java）

```java
// L70：super 属性访问标记
SUPER_PROPERTY_ACCESS = 31,
```

##### IRFactory 层处理（IRFactory.java）

| 位置 | 功能 |
|------|------|
| L593-594 | 元素访问中检测 super：`getElem.putIntProp(Node.SUPER_PROPERTY_ACCESS, 1)` |
| L751-752 | 调用中传播 super 标记 |
| L1088-1089 | 可选链调用中传播 super 标记 |
| L2047-2048 | 赋值中传播 super 标记 |
| L2272-2274 | 属性访问中检测 super |
| L2479, L2588-2590 | `propagateSuperFromLhs()` 方法统一处理 super 传播 |

```java
// IRFactory.java L2272-2274
if (target.getType() == Token.SUPER) {
    node.putIntProp(Node.SUPER_PROPERTY_ACCESS, 1);
}
```

##### CodeGenerator 层处理（CodeGenerator.java）

| 位置 | 功能 |
|------|------|
| L678-679 | `Icode_CALL_ON_SUPER` - super 方法调用 |
| L772-774 | `GETPROP_SUPER` / `GETPROPNOWARN_SUPER` |
| L786-787 | `Icode_DELPROP_SUPER` - super 属性删除 |
| L817-819 | `GETELEM_SUPER` - super 元素获取 |
| L916-917 | `SETPROP_SUPER` - super 属性设置 |
| L940-941 | `SETELEM_SUPER` - super 元素设置 |
| L1053 | `Token.SUPER` 处理 |
| L1334-1443 | `visitSuperIncDec()` - super 自增/自减处理 |

##### Interpreter 层实现（Interpreter.java）

```java
// 指令注册（L1447-1501）
instructionObjs[base + Icode_DELPROP_SUPER] = new DoDelPropSuper();
instructionObjs[base + Token.GETPROP_SUPER] = new DoGetPropSuper();
instructionObjs[base + Token.SETPROP_SUPER] = new DoSetPropSuper();
instructionObjs[base + Token.GETELEM_SUPER] = new DoGetElemSuper();
instructionObjs[base + Token.SETELEM_SUPER] = new DoSetElemSuper();
instructionObjs[base + Icode_CALL_ON_SUPER] = new DoCallByteCode();
instructionObjs[base + Token.SUPER] = new DoSuper();

// 指令实现类
private static class DoSuper extends InstructionClass { ... }          // L4061
private static class DoGetPropSuper extends InstructionClass { ... }   // L2943
private static class DoSetPropSuper extends InstructionClass { ... }   // L2976
private static class DoGetElemSuper extends InstructionClass { ... }   // L3036
private static class DoSetElemSuper extends InstructionClass { ... }   // L3083
private static class DoDelPropSuper extends InstructionClass { ... }   // L2906
```

##### ScriptRuntime 层支持（ScriptRuntime.java）

```java
// L1885-1912：super 元素访问
public static Object getSuperElem(
    Object superObject, Object elem, Context cx, Scriptable scope, Object thisObject)

public static Object getSuperElem(
    Object elem, Scriptable superScriptable, Scriptable thisScriptable)

// L1970-2027：super 属性访问
public static Object getSuperProp(
    Object superObject, String name, Context cx, Scriptable scope, Object thisObject)

// super 设置方法
public static Object setSuperElem(...)
public static Object setSuperProp(...)
```

#### 5.6.5 实现类继承的剩余工作

**已支持（无需实现）：**
- ✅ `super.property` 读取
- ✅ `super.property = value` 写入
- ✅ `super[expr]` 元素访问
- ✅ `super.method()` 调用
- ✅ `super` 作为表达式

**需要实现：**
- ⚠️ `super()` 构造器调用（检测和验证）
- ⚠️ home object 绑定（方法中的 super 上下文）
- ⚠️ 派生类构造器约束（调用 super() 前不能访问 this）

**super() 调用实现方案：**

```java
// IRFactory.java
private Node transformSuperCall(Node superNode, FunctionNode constructor) {
    // super() 只允许在派生类构造器中使用
    if (!isDerivedConstructor(constructor)) {
        parser.reportError("msg.super.call.not.derived");
    }
    
    // 检查是否在 this 访问之前
    if (hasThisAccessBeforeSuper(constructor.getBody())) {
        parser.reportError("msg.this.before.super");
    }
    
    // 转换为父类构造器调用
    // super(...args) → SuperClass.prototype.constructor.call(this, ...args)
    return transformSuperConstructorCall(superNode);
}
```

#### 5.6.6 IR 转换实现复杂度评估

| 绎象级 | 复杂度 | 说明 |
|--------|--------|------|
| transformClass() | 中 | 创建类 IR，处理构造器、方法、字段 |
| 字段初始化注入 | 低 | 复用现有 body.addChildToFront() 模式 |
| super() 调用 | 中 | 需要上下文检查和转换 |
| 静态块处理 | 低 | 转换为立即执行的函数 |
| 私有字段检查 | 中 | 需要在 AST 和 IR 两层检查访问权限 |

**总体复杂度：中等偏低**

得益于现有的 super 支持基础设施，主要工作是：
1. 解析 class 语法（Parser 层）
2. 创建类运行时对象（NativeClass）
3. 处理 super() 构造器调用（IRFactory + ScriptRuntime）

#### 5.6.7 transformClass() 完整实现代码

```java
/**
 * Transform a class declaration/expression to IR.
 * 
 * <p>Class desugaring follows these steps:
 * <ol>
 *   <li>Create constructor function (or use explicit constructor)</li>
 *   <li>Collect prototype methods and static methods</li>
 *   <li>Inject instance field initializers into constructor</li>
 *   <li>Create class object via ScriptRuntime.createClass()</li>
 *   <li>Execute static field initializers and static blocks</li>
 * </ol>
 * 
 * <p>Example desugaring:
 * <pre>
 * class A extends B {
 *     x = 1;
 *     constructor() { super(); }
 *     method() { return this.x; }
 *     static staticMethod() { return 42; }
 * }
 * 
 * // Desugars to:
 * var A = (function() {
 *     var _class = ScriptRuntime.createClass("A", B, {
 *         method: function() { return this.x; }
 *     }, {
 *         staticMethod: function() { return 42; }
 *     });
 *     var _ctor = function() {
 *         super();           // if extends
 *         this.x = 1;        // field initializers
 *     };
 *     return _class;
 * })();
 * </pre>
 */
private Node transformClass(ClassNode classNode) {
    int lineno = classNode.getLineno();
    int column = classNode.getColumn();
    String className = classNode.getName();
    boolean hasSuper = classNode.hasSuperClass();
    
    // Step 1: Create class scope (for let/const in class body)
    Node classScope = new Node(Token.BLOCK, lineno, column);
    
    // Step 2: Transform super class expression (if extends)
    Node superClassNode = null;
    if (hasSuper) {
        superClassNode = transform(classNode.getSuperClass());
    }
    
    // Step 3: Process class elements
    List<ClassElement> elements = classNode.getElements();
    FunctionNode constructorFn = null;
    List<Node> prototypeMethods = new ArrayList<>();
    List<Node> staticMethods = new ArrayList<>();
    List<Node> instanceFieldInits = new ArrayList<>();
    List<Node> staticFieldInits = new ArrayList<>();
    List<Node> staticBlocks = new ArrayList<>();
    
    // Collect private field names for access checking
    Set<String> privateFields = new HashSet<>();
    Set<String> privateMethods = new HashSet<>();
    
    for (ClassElement element : elements) {
        // Collect private member names
        if (element.isPrivate()) {
            String name = element.getKey().getString();
            if (element.isMethod()) {
                privateMethods.add(name);
            } else {
                privateFields.add(name);
            }
        }
        
        switch (element.getElementType()) {
            case ClassElement.CONSTRUCTOR:
                constructorFn = element.getMethod();
                // Mark as constructor for IRFactory
                constructorFn.putIntProp(Node.CONSTRUCTOR_PROP, 1);
                if (hasSuper) {
                    constructorFn.putIntProp(Node.DERIVED_CONSTRUCTOR_PROP, 1);
                }
                break;
                
            case ClassElement.METHOD:
                Node methodIR = transformClassMethod(element, classNode);
                if (element.isStatic()) {
                    staticMethods.add(methodIR);
                } else {
                    prototypeMethods.add(methodIR);
                }
                break;
                
            case ClassElement.FIELD:
                Node fieldIR = transformClassField(element, hasSuper);
                if (element.isStatic()) {
                    staticFieldInits.add(fieldIR);
                } else {
                    instanceFieldInits.add(fieldIR);
                }
                break;
                
            case ClassElement.STATIC_BLOCK:
                Node blockIR = transformStaticBlock(element, className);
                staticBlocks.add(blockIR);
                break;
        }
    }
    
    // Step 4: Create default constructor if not provided
    if (constructorFn == null) {
        constructorFn = createDefaultConstructor(classNode, lineno, column);
    }
    
    // Step 5: Inject field initializers into constructor
    if (!instanceFieldInits.isEmpty()) {
        injectFieldInitializers(constructorFn, instanceFieldInits, hasSuper);
    }
    
    // Step 6: Create class object call
    Node createClassCall = createClassConstructionCall(
        className, superClassNode, constructorFn, 
        prototypeMethods, staticMethods, privateFields, privateMethods,
        lineno, column
    );
    
    // Step 7: Handle class name binding
    Node result;
    if (classNode.isClassStatement() && !className.isEmpty()) {
        // class A {} -> var A = ...
        Node assign = createAssignment(Token.ASSIGN, 
            parser.createName(className), createClassCall);
        result = new Node(Token.EXPR_RESULT, assign, lineno, column);
    } else {
        // class expression: (class {})
        result = createClassCall;
    }
    
    classScope.addChildToBack(result);
    
    // Step 8: Execute static field initializers and static blocks
    for (Node init : staticFieldInits) {
        classScope.addChildToBack(init);
    }
    for (Node block : staticBlocks) {
        classScope.addChildToBack(block);
    }
    
    return classScope;
}

/**
 * Transform a class method to IR.
 * Sets up home object for super access.
 */
private Node transformClassMethod(ClassElement element, ClassNode classNode) {
    FunctionNode method = element.getMethod();
    
    // Mark method type
    if (element.isGetter()) {
        method.setFunctionForm(FunctionNode.Form.GETTER);
    } else if (element.isSetter()) {
        method.setFunctionForm(FunctionNode.Form.SETTER);
    } else {
        method.setFunctionForm(FunctionNode.Form.METHOD);
    }
    
    // Transform to IR
    Node methodIR = transformFunction(method);
    
    // Set home object for super access
    // Home object is the prototype object for instance methods
    if (!element.isStatic() && classNode.hasSuperClass()) {
        methodIR.putIntProp(Node.HOME_OBJECT_PROP, 1);
    }
    
    // Create property entry: { methodName: function }
    Node propEntry = new Node(Token.COLON);
    propEntry.addChildToBack(Node.newString(element.getKey().getString()));
    propEntry.addChildToBack(methodIR);
    
    return propEntry;
}

/**
 * Transform a class field to IR.
 * Returns an assignment node: this.fieldName = initialValue
 */
private Node transformClassField(ClassElement element, boolean isDerivedClass) {
    Name key = element.getKey();
    boolean isPrivate = element.isPrivate();
    AstNode valueNode = element.getFieldValue();
    
    // Create this.fieldName or this.#fieldName access
    Node lhs;
    Node thisNode = new Node(Token.THIS);
    
    if (isPrivate) {
        // Private field access uses special token
        lhs = new Node(Token.GET_PRIVATE_FIELD, thisNode);
        lhs.putProp(Node.PRIVATE_FIELD_NAME_PROP, key.getString());
    } else {
        lhs = new Node(Token.GETPROP, thisNode, Node.newString(key.getString()));
    }
    
    // Create value expression
    Node rhs;
    if (valueNode != null) {
        rhs = transform(valueNode);
    } else {
        rhs = new Node(Token.UNDEFINED);
    }
    
    // this.field = value
    Node assign = createAssignment(Token.ASSIGN, lhs, rhs);
    
    return new Node(Token.EXPR_VOID, assign);
}

/**
 * Create a default constructor.
 * For base classes: function() {}
 * For derived classes: function(...args) { super(...args); }
 */
private FunctionNode createDefaultConstructor(ClassNode classNode, int lineno, int column) {
    FunctionNode ctor = new FunctionNode(lineno);
    ctor.setFunctionName("constructor");
    ctor.setFunctionType(FunctionNode.FUNCTION_EXPRESSION);
    ctor.setFunctionForm(FunctionNode.Form.METHOD);
    ctor.putIntProp(Node.CONSTRUCTOR_PROP, 1);
    
    // Create empty body
    Block body = new Block(lineno);
    
    if (classNode.hasSuperClass()) {
        // Derived class: add super(...args) call
        ctor.putIntProp(Node.DERIVED_CONSTRUCTOR_PROP, 1);
        
        // Create super() call node
        Node superCall = createSuperConstructorCall(lineno, column);
        body.addStatement(new ExpressionStatement(superCall, lineno));
    }
    
    ctor.setBody(body);
    return ctor;
}

/**
 * Create a super() constructor call node.
 * super(...args) -> SuperClass.apply(this, arguments)
 */
private Node createSuperConstructorCall(int lineno, int column) {
    // Create CALL node with SUPER as target
    Node superNode = new Node(Token.SUPER, lineno, column);
    Node call = new Node(Token.CALL, superNode, lineno, column);
    call.putIntProp(Node.SUPER_CONSTRUCTOR_CALL, 1);
    
    return new Node(Token.EXPR_VOID, call, lineno, column);
}
```

#### 5.6.8 super() 构造器调用检测与转换

```java
// ===== super() 调用检测与转换 =====

/**
 * Check if a node is a super() constructor call.
 * Valid: super(), super(arg1, arg2)
 * Invalid: super.method(), super['prop']
 */
private boolean isSuperConstructorCall(Node node) {
    if (node.getType() == Token.EXPR_VOID || node.getType() == Token.EXPR_RESULT) {
        Node expr = node.getFirstChild();
        if (expr != null && expr.getType() == Token.CALL) {
            Node target = expr.getFirstChild();
            // super() but not super.method() or super['prop']
            if (target != null && target.getType() == Token.SUPER) {
                // Check it's not a property access
                return target.getNext() == null;
            }
        }
    }
    return false;
}

/**
 * Validate super() usage in constructor.
 * Must be:
 * 1. Inside a derived class constructor
 * 2. Called exactly once (or not at all in base class)
 * 3. Called before any this access
 * 
 * @throws ParserException if validation fails
 */
private void validateSuperConstructorUsage(ClassNode classNode, FunctionNode constructor) {
    if (!classNode.hasSuperClass()) {
        // Base class: super() is not allowed
        if (containsSuperConstructorCall(constructor)) {
            parser.reportError("msg.super.ctor.base", constructor.getLineno());
        }
        return;
    }
    
    // Derived class: check super() placement
    Node body = constructor.getBody();
    if (body == null) return;
    
    int superCallIndex = -1;
    int thisAccessIndex = -1;
    int index = 0;
    
    Node child = body.getFirstChild();
    while (child != null) {
        if (isSuperConstructorCall(child)) {
            if (superCallIndex >= 0) {
                // Duplicate super() call
                parser.reportError("msg.super.ctor.duplicate", child.getLineno());
            }
            superCallIndex = index;
        } else if (containsThisAccess(child) && thisAccessIndex < 0) {
            thisAccessIndex = index;
        }
        child = child.getNext();
        index++;
    }
    
    // Check: this access before super() call
    if (thisAccessIndex >= 0 && (superCallIndex < 0 || thisAccessIndex < superCallIndex)) {
        parser.reportError("msg.this.before.super", body.getLineno());
    }
    
    // Mark constructor as having/not having super() call
    if (superCallIndex >= 0) {
        constructor.putIntProp(Node.HAS_SUPER_CALL_PROP, 1);
    }
}

/**
 * Check if a node tree contains a super() constructor call.
 */
private boolean containsSuperConstructorCall(Node node) {
    if (node == null) return false;
    if (isSuperConstructorCall(node)) return true;
    
    Node child = node.getFirstChild();
    while (child != null) {
        if (containsSuperConstructorCall(child)) return true;
        child = child.getNext();
    }
    return false;
}

/**
 * Check if a node contains 'this' access.
 */
private boolean containsThisAccess(Node node) {
    if (node == null) return false;
    if (node.getType() == Token.THIS) return true;
    if (node.getType() == Token.GETPROP || node.getType() == Token.GETELEM) {
        Node target = node.getFirstChild();
        if (target != null && target.getType() == Token.THIS) {
            return true;
        }
    }
    
    Node child = node.getFirstChild();
    while (child != null) {
        if (containsThisAccess(child)) return true;
        child = child.getNext();
    }
    return false;
}

/**
 * Transform super() constructor call.
 * super(...args) -> ScriptRuntime.superCall(this, superClass, args)
 */
private Node transformSuperConstructorCall(Node superCall, Node superClassRef) {
    // Extract arguments from CALL node
    Node callNode = superCall.getFirstChild(); // EXPR_VOID -> CALL
    if (callNode.getType() != Token.CALL) {
        return superCall;
    }
    
    // Create ScriptRuntime.superCall() invocation
    Node runtimeCall = new Node(Token.CALL);
    
    // Target: ScriptRuntime.superCall
    Node target = new Node(Token.GETPROP);
    target.addChildToBack(parser.createName("ScriptRuntime"));
    target.addChildToBack(Node.newString("superCall"));
    runtimeCall.addChildToBack(target);
    
    // Arguments: this, superClass, ...args
    runtimeCall.addChildToBack(new Node(Token.THIS));  // this
    runtimeCall.addChildToBack(superClassRef);           // superClass
    
    // Add original arguments
    Node arg = callNode.getFirstChild().getNext(); // skip SUPER node
    while (arg != null) {
        runtimeCall.addChildToBack(arg);
        arg = arg.getNext();
    }
    
    return new Node(Token.EXPR_VOID, runtimeCall);
}

/**
 * Inject field initializers into constructor body.
 * For derived classes, insert after super() call.
 * For base classes, insert at beginning.
 */
private void injectFieldInitializers(FunctionNode constructor, 
        List<Node> initializers, boolean isDerived) {
    Node body = constructor.getBody();
    if (body == null) {
        body = new Node(Token.BLOCK);
        constructor.setBody(body);
    }
    
    if (isDerived) {
        // Find super() call position
        int superIndex = findSuperCallIndex(body);
        if (superIndex >= 0) {
            // Insert after super() call
            Node superCall = getNodeAtIndex(body, superIndex);
            for (Node init : initializers) {
                body.addChildAfter(init, superCall);
                superCall = init; // Chain insertions
            }
        } else {
            // No super() - will be caught by validation
            // Still insert at beginning for partial execution
            for (int i = initializers.size() - 1; i >= 0; i--) {
                body.addChildToFront(initializers.get(i));
            }
        }
    } else {
        // Base class: insert at beginning
        for (int i = initializers.size() - 1; i >= 0; i--) {
            body.addChildToFront(initializers.get(i));
        }
    }
}

/**
 * Find the index of super() call in body.
 */
private int findSuperCallIndex(Node body) {
    int index = 0;
    Node child = body.getFirstChild();
    while (child != null) {
        if (isSuperConstructorCall(child)) {
            return index;
        }
        child = child.getNext();
        index++;
    }
    return -1;
}

/**
 * Get node at specific index in a block.
 */
private Node getNodeAtIndex(Node block, int index) {
    int i = 0;
    Node child = block.getFirstChild();
    while (child != null && i < index) {
        child = child.getNext();
        i++;
    }
    return child;
}
```

#### 5.6.9 私有字段访问的编译时检查

```java
// ===== 私有字段访问检查 =====

/**
 * Context for private field access validation.
 * Tracks current class and its private members.
 */
private static class PrivateFieldContext {
    final String className;
    final Set<String> privateFields;
    final Set<String> privateMethods;
    final Set<String> privateStaticFields;
    final Set<String> privateStaticMethods;
    
    PrivateFieldContext(ClassNode classNode) {
        this.className = classNode.getName();
        this.privateFields = new HashSet<>();
        this.privateMethods = new HashSet<>();
        this.privateStaticFields = new HashSet<>();
        this.privateStaticMethods = new HashSet<>();
        
        // Collect all private members
        for (ClassElement element : classNode.getElements()) {
            if (!element.isPrivate()) continue;
            
            String name = element.getKey().getString();
            boolean isStatic = element.isStatic();
            
            if (element.isMethod()) {
                if (isStatic) {
                    privateStaticMethods.add(name);
                } else {
                    privateMethods.add(name);
                }
            } else {
                if (isStatic) {
                    privateStaticFields.add(name);
                } else {
                    privateFields.add(name);
                }
            }
        }
    }
    
    /**
     * Check if a private field name is accessible in current context.
     * Private fields can only be accessed:
     * 1. Within the class that declares them
     * 2. On instances of the same class (including this)
     */
    boolean isPrivateFieldAccessible(String name, boolean isStaticContext) {
        if (isStaticContext) {
            return privateStaticFields.contains(name);
        }
        return privateFields.contains(name) || privateStaticFields.contains(name);
    }
    
    boolean isPrivateMethodAccessible(String name, boolean isStaticContext) {
        if (isStaticContext) {
            return privateStaticMethods.contains(name);
        }
        return privateMethods.contains(name) || privateStaticMethods.contains(name);
    }
    
    boolean hasPrivateField(String name) {
        return privateFields.contains(name) || privateStaticFields.contains(name);
    }
    
    boolean hasPrivateMethod(String name) {
        return privateMethods.contains(name) || privateStaticMethods.contains(name);
    }
}

/**
 * Validate private field access in class body.
 * Called during AST traversal before IR transformation.
 * 
 * @param classNode the class being validated
 * @throws ParserException if illegal private field access is detected
 */
private void validatePrivateFieldAccess(ClassNode classNode) {
    PrivateFieldContext ctx = new PrivateFieldContext(classNode);
    
    for (ClassElement element : classNode.getElements()) {
        if (element.isStaticBlock()) {
            validatePrivateAccessInNode(element.getStaticBlock(), ctx, true);
        } else if (element.isMethod()) {
            FunctionNode method = element.getMethod();
            validatePrivateAccessInFunction(method, ctx, element.isStatic());
        } else if (element.isField() && element.getFieldValue() != null) {
            validatePrivateAccessInNode(element.getFieldValue(), ctx, element.isStatic());
        }
    }
}

/**
 * Validate private access in a function node.
 */
private void validatePrivateAccessInFunction(FunctionNode fn, 
        PrivateFieldContext ctx, boolean isStaticContext) {
    // Check function body
    if (fn.getBody() != null) {
        validatePrivateAccessInNode(fn.getBody(), ctx, isStaticContext);
    }
    
    // Check default parameter values
    List<Object> defaultParams = fn.getDefaultParams();
    if (defaultParams != null) {
        for (Object param : defaultParams) {
            if (param instanceof AstNode) {
                validatePrivateAccessInNode((AstNode) param, ctx, isStaticContext);
            }
        }
    }
}

/**
 * Recursively validate private field access in an AST node.
 */
private void validatePrivateAccessInNode(AstNode node, 
        PrivateFieldContext ctx, boolean isStaticContext) {
    if (node == null) return;
    
    // Check for private field access: this.#field or obj.#field
    if (node instanceof PropertyGet) {
        PropertyGet propGet = (PropertyGet) node;
        String propName = propGet.getProperty().getString();
        
        if (propName.startsWith("#")) {
            String privateName = propName.substring(1);
            if (!ctx.isPrivateFieldAccessible(privateName, isStaticContext)) {
                parser.reportError("msg.private.field.access", 
                    privateName, node.getLineno());
            }
        }
    }
    
    // Check for private method call: this.#method() or obj.#method()
    if (node instanceof FunctionCall) {
        FunctionCall call = (FunctionCall) node;
        if (call.getTarget() instanceof PropertyGet) {
            PropertyGet target = (PropertyGet) call.getTarget();
            String methodName = target.getProperty().getString();
            
            if (methodName.startsWith("#")) {
                String privateName = methodName.substring(1);
                if (!ctx.isPrivateMethodAccessible(privateName, isStaticContext)) {
                    parser.reportError("msg.private.method.access", 
                        privateName, node.getLineno());
                }
            }
        }
    }
    
    // Check for private field assignment: this.#field = value
    if (node instanceof Assignment) {
        Assignment assign = (Assignment) node;
        validatePrivateAccessInNode(assign.getLeft(), ctx, isStaticContext);
        validatePrivateAccessInNode(assign.getRight(), ctx, isStaticContext);
    }
    
    // Recursively check children
    for (AstNode child : node) {
        validatePrivateAccessInNode(child, ctx, isStaticContext);
    }
}

/**
 * Check if a private field reference is valid across class boundaries.
 * Private fields from different classes cannot access each other's private members.
 * 
 * <p>Example of invalid access:
 * <pre>
 * class A {
 *     #x = 1;
 *     getX(other) {
 *         return other.#x;  // Error if other is instance of B
 *     }
 * }
 * class B {
 *     #x = 2;
 * }
 * </pre>
 * 
 * Note: This is primarily a runtime check, but we can detect some cases statically.
 */
private void validateCrossClassPrivateAccess(AstNode node, PrivateFieldContext ctx) {
    // Static analysis for obvious cross-class violations
    // Most cases require runtime checks via WeakMap storage
}

/**
 * Transform private field access to IR.
 * this.#field -> ScriptRuntime.getPrivateField(this, "#field", classIdentity)
 */
private Node transformPrivateFieldAccess(Node thisNode, String privateName, 
        PrivateFieldContext ctx) {
    // Create runtime call for private field access
    Node call = new Node(Token.CALL);
    
    // ScriptRuntime.getPrivateField
    Node target = new Node(Token.GETPROP);
    target.addChildToBack(parser.createName("ScriptRuntime"));
    target.addChildToBack(Node.newString("getPrivateField"));
    call.addChildToBack(target);
    
    // Arguments: this, fieldName, classToken
    call.addChildToBack(thisNode);
    call.addChildToBack(Node.newString(privateName));
    call.addChildToBack(new Node(Token.THIS)); // Class identity token
    
    return call;
}

/**
 * Transform private field assignment to IR.
 * this.#field = value -> ScriptRuntime.setPrivateField(this, "#field", value, classIdentity)
 */
private Node transformPrivateFieldAssignment(Node thisNode, String privateName, 
        Node value, PrivateFieldContext ctx) {
    Node call = new Node(Token.CALL);
    
    // ScriptRuntime.setPrivateField
    Node target = new Node(Token.GETPROP);
    target.addChildToBack(parser.createName("ScriptRuntime"));
    target.addChildToBack(Node.newString("setPrivateField"));
    call.addChildToBack(target);
    
    // Arguments: this, fieldName, value, classToken
    call.addChildToBack(thisNode);
    call.addChildToBack(Node.newString(privateName));
    call.addChildToBack(value);
    call.addChildToBack(new Node(Token.THIS));
    
    return call;
}
```

#### 5.6.10 静态块处理

```java
/**
 * Transform a static block to IR.
 * Static blocks are executed immediately when the class is defined.
 * 
 * <p>Example:
 * <pre>
 * class A {
 *     static {
 *         console.log("Class A initialized");
 *     }
 * }
 * // Transformed to:
 * (function() {
 *     console.log("Class A initialized");
 * }).call(A);
 * </pre>
 */
private Node transformStaticBlock(ClassElement element, String className) {
    AstNode block = element.getStaticBlock();
    
    // Create an IIFE (Immediately Invoked Function Expression)
    // (function() { ... block content ... }).call(ClassName)
    
    // Create function node
    FunctionNode fn = new FunctionNode(block.getLineno());
    fn.setFunctionName("");  // Anonymous
    fn.setFunctionType(FunctionNode.FUNCTION_EXPRESSION);
    fn.setBody(block);
    
    // Transform function
    Node fnIR = transformFunction(fn);
    
    // Create .call(ClassName)
    Node call = new Node(Token.CALL);
    
    // Target: (function(){}).call
    Node callMethod = new Node(Token.GETPROP);
    callMethod.addChildToBack(fnIR);
    callMethod.addChildToBack(Node.newString("call"));
    call.addChildToBack(callMethod);
    
    // Argument: ClassName (as this value)
    call.addChildToBack(parser.createName(className));
    
    return new Node(Token.EXPR_VOID, call);
}
```

### 5.7 运行时支持

#### 5.7.1 BaseFunction 分析与 NativeClass 继承选择

**BaseFunction 关键方法分析：**

```java
// BaseFunction.java L537-542
@Override
public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
    return Undefined.instance;  // 默认返回 undefined
}

@Override
public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
    // ES6+ 检查：有 homeObject 的方法不能作为构造器
    if (cx.getLanguageVersion() >= Context.VERSION_ES6 && this.getHomeObject() != null) {
        throw ScriptRuntime.typeErrorById("msg.not.ctor", getFunctionName());
    }
    
    // 创建实例并调用 call()
    Scriptable result = createObject(cx, scope);
    Object val = call(cx, scope, result, args);
    // ... 处理返回值
}
```

**NativeClass 继承方案：**

| 方案 | 优点 | 缺点 |
|------|------|------|
| 继承 `BaseFunction` | ✅ 符合 ES 规范（类是特殊的函数） | 需要重写多个方法 |
| 继承 `JSFunction` | 复用更多逻辑 | ❌ JSFunction 与编译器紧密耦合 |

**结论：继承 `BaseFunction`**

**与 JSFunction 的对比：**

| 特性 | JSFunction | NativeClass |
|------|------------|-------------|
| `call()` | 执行字节码 | ❌ 抛出 "Class constructor cannot be invoked without 'new'" |
| `construct()` | 调用 descriptor.getConstructor() | 调用内部构造器方法 |
| `homeObject` | ✅ 支持（用于 super） | ✅ 需要支持（方法需要） |
| `prototype` 属性 | ✅ 函数原型 | ✅ 类实例原型 |
| `[[Constructor]]` | 通过 descriptor | 通过内部构造器方法 |

#### 5.7.2 NativeClass 实现方案 (`NativeClass.java`)

```java
package org.mozilla.javascript;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Represents a JavaScript class constructor created via class syntax.
 * Extends BaseFunction with class-specific features.
 * 
 * <p>Per ES6 specification, classes are special kinds of functions that:
 * <ul>
 *   <li>Cannot be called without 'new' (throw TypeError)</li>
 *   <li>Have a prototype property for instance creation</li>
 *   <li>Support extends via prototype chain</li>
 *   <li>Support private fields via internal WeakMap storage</li>
 * </ul>
 */
public class NativeClass extends BaseFunction {
    private static final long serialVersionUID = 1L;

    private static final Object CLASS_TAG = "Class";

    // The prototype object for instances created by this class
    private Scriptable classPrototype;
    
    // The super class (for extends)
    private Scriptable superClass;
    
    // Whether this is a derived class (has extends clause)
    private boolean isDerived;
    
    // The constructor method (FunctionNode transformed to callable)
    private Callable constructorMethod;
    
    // Private field storage: WeakHashMap<instance, Map<fieldName, value>>
    // Using WeakHashMap ensures instances can be garbage collected
    private transient WeakHashMap<Object, Map<String, Object>> privateFieldStorage;
    
    // Private field names defined in this class (for validation)
    private Map<String, PrivateFieldInfo> privateFields;

    public NativeClass() {
        super();
        this.privateFieldStorage = new WeakHashMap<>();
        this.privateFields = new HashMap<>();
    }

    public NativeClass(Scriptable scope, Scriptable prototype) {
        super(scope, prototype);
        ScriptRuntime.setBuiltinProtoAndParent(this, scope, TopLevel.Builtins.Function);
        this.privateFieldStorage = new WeakHashMap<>();
        this.privateFields = new HashMap<>();
    }

    @Override
    public String getClassName() {
        return "Function";  // Per ES spec, classes are functions
    }

    /**
     * ES6: Class constructors cannot be invoked as a function.
     * Throws TypeError when called without 'new'.
     */
    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        // ES6 9.2.1: [[Call]] for class constructors
        // "If NewTarget is undefined, throw a TypeError exception."
        throw ScriptRuntime.typeErrorById("msg.class.not.new", getFunctionName());
    }

    /**
     * ES6: [[Construct]] internal method for class constructors.
     */
    @Override
    public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
        if (isDerived) {
            // Derived class: create uninitialized object
            // super() must be called before accessing 'this'
            return constructDerived(cx, scope, args);
        } else {
            // Base class: create and initialize object directly
            return constructBase(cx, scope, args);
        }
    }
    
    /**
     * Construct instance for base class (no extends).
     */
    private Scriptable constructBase(Context cx, Scriptable scope, Object[] args) {
        // Create new instance with correct prototype
        Scriptable newInstance = createObject(cx, scope);
        newInstance.setPrototype(classPrototype);
        newInstance.setParentScope(scope);
        
        // Initialize private field storage for this instance
        if (!privateFields.isEmpty()) {
            privateFieldStorage.put(newInstance, new HashMap<>());
        }
        
        // Call the constructor method
        if (constructorMethod != null) {
            constructorMethod.call(cx, scope, newInstance, args);
        }
        
        return newInstance;
    }
    
    /**
     * Construct instance for derived class (has extends).
     * The object is not fully initialized until super() is called.
     */
    private Scriptable constructDerived(Context cx, Scriptable scope, Object[] args) {
        // Create uninitialized object - super() will initialize it
        UninitializedObject uninitialized = new UninitializedObject(scope, classPrototype);
        
        // Store private field storage reference for super() to complete
        if (!privateFields.isEmpty()) {
            uninitialized.setPrivateFieldStorage(privateFieldStorage);
            uninitialized.setPrivateFields(privateFields);
        }
        
        // Call the constructor method (must call super())
        if (constructorMethod != null) {
            constructorMethod.call(cx, scope, uninitialized, args);
        }
        
        // Verify super() was called
        if (!uninitialized.isInitialized()) {
            throw ScriptRuntime.typeErrorById("msg.super.not.called");
        }
        
        return uninitialized.getInitializedObject();
    }

    /**
     * Access a private field on an instance.
     * Throws TypeError if accessed from outside the class.
     */
    public Object getPrivateField(Object instance, String fieldName) {
        // Validate that the instance belongs to this class
        Map<String, Object> fields = privateFieldStorage.get(instance);
        if (fields == null) {
            throw ScriptRuntime.typeErrorById("msg.private.field.access", fieldName);
        }
        
        PrivateFieldInfo info = privateFields.get(fieldName);
        if (info == null) {
            throw ScriptRuntime.typeErrorById("msg.private.field.not.found", fieldName);
        }
        
        return fields.get(fieldName);
    }
    
    /**
     * Set a private field on an instance.
     */
    public void setPrivateField(Object instance, String fieldName, Object value) {
        Map<String, Object> fields = privateFieldStorage.get(instance);
        if (fields == null) {
            throw ScriptRuntime.typeErrorById("msg.private.field.access", fieldName);
        }
        
        PrivateFieldInfo info = privateFields.get(fieldName);
        if (info == null) {
            throw ScriptRuntime.typeErrorById("msg.private.field.not.found", fieldName);
        }
        
        fields.put(fieldName, value);
    }
    
    /**
     * Check if a private field exists on this class.
     */
    public boolean hasPrivateField(String fieldName) {
        return privateFields.containsKey(fieldName);
    }
    
    /**
     * Add a private field definition to this class.
     */
    public void definePrivateField(String name, boolean isStatic) {
        privateFields.put(name, new PrivateFieldInfo(name, isStatic));
    }

    // Getters and setters

    public Scriptable getClassPrototype() {
        return classPrototype;
    }

    public void setClassPrototype(Scriptable classPrototype) {
        this.classPrototype = classPrototype;
        // Also set as the prototype property for Function behavior
        setPrototypeProperty(classPrototype);
    }

    public Scriptable getSuperClass() {
        return superClass;
    }

    public void setSuperClass(Scriptable superClass) {
        this.superClass = superClass;
        this.isDerived = (superClass != null);
    }

    public boolean isDerived() {
        return isDerived;
    }

    public Callable getConstructorMethod() {
        return constructorMethod;
    }

    public void setConstructorMethod(Callable constructorMethod) {
        this.constructorMethod = constructorMethod;
    }
    
    /**
     * Get the home object for method super access.
     * For class methods, the home object is the class prototype.
     */
    @Override
    public Scriptable getHomeObject() {
        return classPrototype;
    }
    
    // Serialization support
    private void readObject(java.io.ObjectInputStream stream) 
            throws java.io.IOException, ClassNotFoundException {
        stream.defaultReadObject();
        privateFieldStorage = new WeakHashMap<>();
    }
    
    /**
     * Internal class for private field metadata.
     */
    private static class PrivateFieldInfo {
        final String name;
        final boolean isStatic;
        
        PrivateFieldInfo(String name, boolean isStatic) {
            this.name = name;
            this.isStatic = isStatic;
        }
    }
    
    /**
     * Represents an uninitialized object during derived class construction.
     * This is used to track whether super() has been called.
     */
    public static class UninitializedObject implements Scriptable {
        private Scriptable prototype;
        private Scriptable parentScope;
        private boolean initialized = false;
        private Scriptable initializedObject;
        private WeakHashMap<Object, Map<String, Object>> privateFieldStorage;
        private Map<String, PrivateFieldInfo> privateFields;
        
        UninitializedObject(Scriptable scope, Scriptable prototype) {
            this.parentScope = scope;
            this.prototype = prototype;
        }
        
        /**
         * Mark this object as initialized by super().
         */
        public void markInitialized(Scriptable obj) {
            this.initialized = true;
            this.initializedObject = obj;
            
            // Initialize private field storage
            if (privateFieldStorage != null && privateFields != null) {
                Map<String, Object> fields = new HashMap<>();
                privateFieldStorage.put(this, fields);
            }
        }
        
        public boolean isInitialized() {
            return initialized;
        }
        
        public Scriptable getInitializedObject() {
            return initializedObject != null ? initializedObject : this;
        }
        
        // Delegate Scriptable methods to prototype
        @Override public String getClassName() { return "Object"; }
        @Override public Object get(String name, Scriptable start) {
            return prototype.get(name, start);
        }
        @Override public Object get(int index, Scriptable start) {
            return prototype.get(index, start);
        }
        @Override public boolean has(String name, Scriptable start) {
            return prototype.has(name, start);
        }
        @Override public boolean has(int index, Scriptable start) {
            return prototype.has(index, start);
        }
        @Override public void put(String name, Scriptable start, Object value) {
            prototype.put(name, start, value);
        }
        @Override public void put(int index, Scriptable start, Object value) {
            prototype.put(index, start, value);
        }
        @Override public void delete(String name) { prototype.delete(name); }
        @Override public void delete(int index) { prototype.delete(index); }
        @Override public Scriptable getPrototype() { return prototype; }
        @Override public void setPrototype(Scriptable p) { this.prototype = p; }
        @Override public Scriptable getParentScope() { return parentScope; }
        @Override public void setParentScope(Scriptable p) { this.parentScope = p; }
        @Override public Object[] getIds() { return prototype.getIds(); }
        @Override public Object getDefaultValue(Class<?> hint) {
            return prototype.getDefaultValue(hint);
        }
        @Override public boolean hasInstance(Scriptable instance) {
            return prototype.hasInstance(instance);
        }
        
        // Getters and setters for private field storage
        void setPrivateFieldStorage(WeakHashMap<Object, Map<String, Object>> storage) {
            this.privateFieldStorage = storage;
        }
        void setPrivateFields(Map<String, PrivateFieldInfo> fields) {
            this.privateFields = fields;
        }
    }
}

    public Scriptable getSuperClass() {
        return superClass;
    }

    public void setSuperClass(Scriptable superClass) {
        this.superClass = superClass;
        this.isDerived = superClass != null;
    }

    public boolean isDerived() {
        return isDerived;
    }

    @Override
    public boolean hasInstance(Object instance) {
        // instanceof check for classes
        if (instance instanceof Scriptable) {
            Scriptable proto = getClassPrototype();
            Scriptable obj = ((Scriptable) instance).getPrototype();
            while (obj != null) {
                if (proto == obj) return true;
                obj = obj.getPrototype();
            }
        }
        return false;
    }
}
```

#### 5.7.3 原型链处理分析

**ES6 原型链结构：**

```
class A extends B {}

// 实例原型链
instanceA.__proto__ === A.prototype
A.prototype.__proto__ === B.prototype
B.prototype.__proto__ === Object.prototype

// 构造器原型链
A.__proto__ === B  // 类继承自父类构造器
A.__proto__.prototype === B.prototype
```

**现有代码中的原型处理（ScriptRuntime.java）：**

```java
// L5454-5462：设置内置原型
public static void setBuiltinProtoAndParent(
        ScriptableObject object, Scriptable scope, TopLevel.Builtins type) {
    scope = ScriptableObject.getTopLevelScope(scope);
    object.setParentScope(scope);
    object.setPrototype(TopLevel.getBuiltinPrototype(scope, type));
}

// L5450-5451：获取类原型
Scriptable proto = ScriptableObject.getClassPrototype(scope, object.getClassName());
object.setPrototype(proto);
```

**extends 原型链实现方案：**

```java
// 在 createClass() 中
if (superClass != null) {
    // 1. 设置实例原型链：prototype.__proto__ = superClass.prototype
    Scriptable superProto;
    if (superClass instanceof BaseFunction) {
        superProto = (Scriptable) ((BaseFunction) superClass).getPrototypeProperty();
    } else {
        superProto = (Scriptable) ScriptRuntime.getObjectPrototype(superClass);
    }
    prototype.setPrototype(superProto);
    
    // 2. 设置构造器原型链：classObj.__proto__ = superClass
    // 这是 ES6 的关键：子类构造器继承自父类构造器
    classObj.setPrototype((Scriptable) superClass);
    
    classObj.setSuperClass((Scriptable) superClass);
}
```

**验证用例：**

```javascript
class A {}
class B extends A {}

// 实例原型链
B.prototype.__proto__ === A.prototype  // true
new B().__proto__.__proto__ === A.prototype  // true

// 构造器原型链
B.__proto__ === A  // true
B.__proto__.__proto__ === Function.prototype  // true (A 是基类)
```

#### 5.7.4 私有字段存储机制分析

**现有机制：NativeWeakMap**

Rhino 已经实现了 ES6 `WeakMap`（`NativeWeakMap.java`）：

```java
public class NativeWeakMap extends ScriptableObject {
    // 使用 Java WeakHashMap 作为底层存储
    private transient WeakHashMap<Object, Object> map = new WeakHashMap<>();
    
    // 只允许 Object 作为 key
    private static boolean isValidKey(Object key) {
        return ScriptRuntime.isUnregisteredSymbol(key) || ScriptRuntime.isObject(key);
    }
}
```

**私有字段存储方案对比：**

| 方案 | 优点 | 缺点 | 推荐 |
|------|------|------|------|
| **内部 WeakHashMap** | ✅ 自动 GC、性能好、隔离性好 | 需要在 NativeClass 内维护 | ⭐ **推荐** |
| 名称修饰 (如 `__priv_name`) | 简单 | ❌ 可被外部访问、不安全 | ❌ 不推荐 |
| Symbol 存储 | 标准化 | ❌ Symbol 可被枚举、不安全 | ❌ 不推荐 |
| 独立 WeakMap 实例 | 标准化 | 需要额外管理实例映射 | 可选 |

**推荐方案：NativeClass 内部 WeakHashMap**

```java
public class NativeClass extends BaseFunction {
    // 私有字段存储：instance -> Map<fieldName, value>
    private transient WeakHashMap<Object, Map<String, Object>> privateFieldStorage;
    
    // 私有字段元数据
    private Map<String, PrivateFieldInfo> privateFields;
    
    // 访问私有字段（只能在类内部调用）
    public Object getPrivateField(Object instance, String fieldName) {
        Map<String, Object> fields = privateFieldStorage.get(instance);
        if (fields == null) {
            throw ScriptRuntime.typeErrorById("msg.private.field.access", fieldName);
        }
        return fields.get(fieldName);
    }
    
    public void setPrivateField(Object instance, String fieldName, Object value) {
        Map<String, Object> fields = privateFieldStorage.get(instance);
        if (fields != null && privateFields.containsKey(fieldName)) {
            fields.put(fieldName, value);
        }
    }
}
```

**私有字段访问编译转换：**

```javascript
// 源代码
class A {
    #x = 1;
    getX() { return this.#x; }
}

// 编译后（概念）
class A {
    constructor() {
        // 初始化私有字段存储
        __privateStorage.set(this, { #x: 1 });
    }
    getX() {
        // 通过类对象访问私有字段
        return A.__getPrivateField(this, "x");
    }
}
```

**私有字段 Brand 检查（ES2022 规范）：**

```java
// 在访问私有字段前检查对象是否属于该类
private void checkBrand(Object instance) {
    // ES2022: PrivateFieldBrandCheck
    // 对象必须有对应的私有字段存储
    if (!privateFieldStorage.containsKey(instance)) {
        throw ScriptRuntime.typeErrorById(
            "msg.private.field.invalid.receiver", 
            fieldName);
    }
}
```

#### 5.7.5 ScriptRuntime 辅助方法 (`ScriptRuntime.java`)

```java
/**
 * Create a new class constructor from class definition.
 * 
 * @param cx the context
 * @param scope the scope
 * @param className the class name
 * @param superClass the super class (null for base classes)
 * @param prototypeMethods prototype methods as an object
 * @param staticMethods static methods as an object
 * @return the created class constructor
 */
public static Scriptable createClass(
        Context cx, Scriptable scope, 
        String className, 
        Scriptable superClass,
        Scriptable prototypeMethods,
        Scriptable staticMethods) {
    
    // Create the class object
    NativeClass classObj = new NativeClass(scope, 
        ScriptableObject.getFunctionPrototype(scope));
    classObj.setFunctionName(className);
    
    // Set up prototype chain
    Scriptable prototype = cx.newObject(scope);
    
    if (superClass != null) {
        // Set prototype.__proto__ = superClass.prototype
        Scriptable superProto = ((Scriptable) superClass).getPrototypeProperty();
        prototype.setPrototype(superProto);
        classObj.setSuperClass((Scriptable) superClass);
    } else {
        // Base class: prototype.__proto__ = Object.prototype
        prototype.setPrototype(ScriptableObject.getObjectPrototype(scope));
    }
    
    // Add prototype methods
    if (prototypeMethods != null) {
        for (Object id : prototypeMethods.getIds()) {
            String name = id.toString();
            Object method = prototypeMethods.get(name, prototypeMethods);
            prototype.put(name, prototype, method);
            
            // Set home object for super access in methods
            if (method instanceof BaseFunction) {
                setHomeObject((BaseFunction) method, prototype);
            }
        }
    }
    
    // Add constructor reference to prototype
    prototype.put("constructor", prototype, classObj);
    classObj.setPrototypeProperty(prototype);
    
    // Add static methods
    if (staticMethods != null) {
        for (Object id : staticMethods.getIds()) {
            String name = id.toString();
            classObj.put(name, classObj, staticMethods.get(name, staticMethods));
        }
    }
    
    // Define the class name in scope
    if (className != null && !className.isEmpty()) {
        scope.put(className, scope, classObj);
    }
    
    return classObj;
}

/**
 * Set the home object for a method (used for super access).
 */
private static void setHomeObject(BaseFunction method, Scriptable homeObject) {
    // Store home object reference for super property access
    method.put("_homeObject", method, homeObject);
}

/**
 * Get the home object for a method.
 */
public static Scriptable getHomeObject(Scriptable method) {
    if (method instanceof Scriptable) {
        Object home = ((Scriptable) method).get("_homeObject", method);
        if (home instanceof Scriptable) {
            return (Scriptable) home;
        }
    }
    return null;
}

/**
 * Ensure super() has been called in derived constructor.
 * Throws ReferenceError if this is uninitialized.
 */
public static void checkSuperCalled(Scriptable thisObj) {
    if (thisObj instanceof Scriptable) {
        Object uninit = thisObj.get("__uninitialized__", thisObj);
        if (Boolean.TRUE.equals(uninit)) {
            throw ScriptRuntime.referenceErrorById("msg.super.not.called");
        }
    }
}

/**
 * Mark this as initialized after super() call.
 */
public static void markThisInitialized(Scriptable thisObj) {
    if (thisObj instanceof Scriptable) {
        thisObj.delete("__uninitialized__");
    }
}
```

#### 5.7.6 new.target 支持

**ES6 规范要求：**
- `new.target` 在普通函数调用中返回 `undefined`
- `new.target` 在构造器中返回实际被调用的类（即使是继承链中的父类构造器）

**实现方案：**

```java
// ===== ScriptRuntime.java 新增 =====

/**
 * Get new.target value for current execution context.
 * Returns the constructor that was actually invoked with 'new'.
 */
public static Object getNewTarget(Context cx) {
    // Context 维护一个构造器调用栈
    return cx.getCurrentNewTarget();
}

/**
 * Set new.target for constructor invocation.
 * Called when entering a constructor via 'new'.
 */
public static void setNewTarget(Context cx, Scriptable constructor) {
    cx.pushNewTarget(constructor);
}

/**
 * Clear new.target when exiting constructor.
 */
public static void clearNewTarget(Context cx) {
    cx.popNewTarget();
}

// ===== Context.java 新增字段和方法 =====

public class Context {
    // ... 现有字段 ...
    
    // Stack of new.target values (for nested constructor calls)
    private transient ArrayDeque<Scriptable> newTargetStack;
    
    /**
     * Get the current new.target value.
     */
    public Object getCurrentNewTarget() {
        if (newTargetStack == null || newTargetStack.isEmpty()) {
            return Undefined.instance;
        }
        return newTargetStack.peek();
    }
    
    /**
     * Push a new new.target value when entering a constructor.
     */
    public void pushNewTarget(Scriptable constructor) {
        if (newTargetStack == null) {
            newTargetStack = new ArrayDeque<>();
        }
        newTargetStack.push(constructor);
    }
    
    /**
     * Pop new.target when exiting a constructor.
     */
    public void popNewTarget() {
        if (newTargetStack != null && !newTargetStack.isEmpty()) {
            newTargetStack.pop();
        }
    }
}
```

**IRFactory 转换：**

```java
// IRFactory.java - 处理 new.target 引用

/**
 * Transform new.target expression.
 * new.target is a meta-property that returns the constructor
 * that was invoked with 'new'.
 */
private Node transformNewTarget(AstNode node) {
    // 创建对 ScriptRuntime.getNewTarget() 的调用
    Node call = new Node(Token.CALL);
    
    Node target = new Node(Token.GETPROP);
    target.addChildToBack(parser.createName("ScriptRuntime"));
    target.addChildToBack(Node.newString("getNewTarget"));
    call.addChildToBack(target);
    
    // 无参数
    return call;
}
```

**NativeClass 构造器调用修改：**

```java
// NativeClass.java

@Override
public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
    // 类不能作为普通函数调用
    throw ScriptRuntime.typeErrorById("msg.class.not.new", getFunctionName());
}

@Override
public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
    // 设置 new.target
    ScriptRuntime.setNewTarget(cx, this);
    
    try {
        if (isDerived) {
            // 派生类：创建 UninitializedObject，由 super() 初始化
            UninitializedObject instance = new UninitializedObject();
            instance.setPrototype(classPrototype);
            instance.setParentScope(scope);
            instance.setUninitialized(true);
            
            // 调用构造器
            Object result = constructorMethod.call(cx, scope, instance, args);
            
            // 检查是否已初始化
            if (instance.isUninitialized()) {
                throw ScriptRuntime.referenceErrorById("msg.super.not.called");
            }
            
            // 处理返回值
            return processConstructorResult(result, instance);
        } else {
            // 基类：创建实例并调用构造器
            Scriptable instance = cx.newObject(scope);
            instance.setPrototype(classPrototype);
            
            Object result = constructorMethod.call(cx, scope, instance, args);
            return processConstructorResult(result, instance);
        }
    } finally {
        ScriptRuntime.clearNewTarget(cx);
    }
}
```

#### 5.7.7 super() 返回值处理

**ES6 规范要求：**
- `super()` 可以返回任意对象
- 如果 `super()` 返回对象，则该对象成为 `this` 的最终值
- 如果 `super()` 返回非对象（或无返回），则使用当前 `this`

**实现方案：**

```java
// ===== ScriptRuntime.java =====

/**
 * Handle super() constructor call with proper this binding.
 * 
 * <p>Per ES6 spec:
 * <ul>
 *   <li>If super() returns an object, that object becomes 'this'</li>
 *   <li>If super() returns non-object or undefined, the original 'this' is used</li>
 * </ul>
 * 
 * @param cx the context
 * @param thisObj the current this (UninitializedObject for derived class)
 * @param superClass the super class constructor
 * @param args arguments to pass to super constructor
 * @return the object to use as 'this' (may be different from thisObj)
 */
public static Scriptable superConstructorCall(
        Context cx, Scriptable scope, 
        Scriptable thisObj, 
        Scriptable superClass,
        Object[] args) {
    
    // 检查 superClass 是否可构造
    if (!(superClass instanceof Callable)) {
        throw ScriptRuntime.typeErrorById("msg.not.ctor", 
            ScriptRuntime.toString(superClass));
    }
    
    // 调用父类构造器
    Object result;
    if (superClass instanceof BaseFunction) {
        result = ((BaseFunction) superClass).constructAsSuper(cx, scope, args, thisObj);
    } else {
        // 内置构造器（如 Array）
        result = ((Callable) superClass).call(cx, scope, thisObj, args);
    }
    
    // 处理返回值
    Scriptable finalThis;
    if (result instanceof Scriptable) {
        // super() 返回对象，使用该对象作为 this
        finalThis = (Scriptable) result;
    } else {
        // super() 返回非对象或 undefined，使用原始 this
        finalThis = thisObj;
    }
    
    // 标记 this 已初始化
    if (thisObj instanceof UninitializedObject) {
        ((UninitializedObject) thisObj).setUninitialized(false);
        // 绑定实际使用的 this
        ((UninitializedObject) thisObj).setBoundThis(finalThis);
    }
    
    return finalThis;
}

/**
 * Check if super() was called (for derived class constructor validation).
 */
public static boolean wasSuperCalled(Scriptable thisObj) {
    if (thisObj instanceof UninitializedObject) {
        return !((UninitializedObject) thisObj).isUninitialized();
    }
    return true;  // 非 UninitializedObject 说明已初始化
}
```

**UninitializedObject 增强：**

```java
/**
 * Represents an uninitialized 'this' in derived class constructor.
 * Created before super() is called, replaced after super() returns.
 */
public class UninitializedObject extends ScriptableObject {
    private static final long serialVersionUID = 1L;
    
    private boolean uninitialized = true;
    private Scriptable boundThis;  // super() 返回后的实际 this
    
    public UninitializedObject() {
        super();
    }
    
    public boolean isUninitialized() {
        return uninitialized;
    }
    
    public void setUninitialized(boolean uninitialized) {
        this.uninitialized = uninitialized;
    }
    
    public Scriptable getBoundThis() {
        return boundThis;
    }
    
    public void setBoundThis(Scriptable boundThis) {
        this.boundThis = boundThis;
    }
    
    @Override
    public Object get(String name, Scriptable start) {
        if (uninitialized) {
            throw ScriptRuntime.referenceErrorById("msg.this.before.super");
        }
        return super.get(name, start);
    }
    
    @Override
    public void put(String name, Scriptable start, Object value) {
        if (uninitialized) {
            throw ScriptRuntime.referenceErrorById("msg.this.before.super");
        }
        super.put(name, start, value);
    }
    
    @Override
    public String getClassName() {
        return "UninitializedObject";
    }
}
```

#### 5.7.8 构造器 return 语义

**ES6 规范要求：**
- 构造器可以 `return` 一个对象，该对象将替代 `this`
- 构造器 `return` 非对象值（如 `return 42`）被忽略
- 构造器无 `return` 或 `return undefined` 返回 `this`

**实现方案：**

```java
// ===== NativeClass.java =====

/**
 * Process constructor return value per ES6 semantics.
 * 
 * @param result the value returned by the constructor
 * @param defaultThis the 'this' object that was passed to constructor
 * @return the final object (either result if object, or defaultThis)
 */
private Scriptable processConstructorResult(Object result, Scriptable defaultThis) {
    if (result instanceof Scriptable) {
        // 构造器返回对象，使用该对象
        return (Scriptable) result;
    } else {
        // 构造器返回非对象或 undefined，使用 this
        return defaultThis;
    }
}
```

**测试用例：**

```javascript
// Test 1: return 对象替代 this
class A {
    constructor() {
        return { custom: true };
    }
}
new A() instanceof A;  // false
new A().custom;        // true

// Test 2: return 非对象被忽略
class B {
    constructor() {
        return 42;  // 被忽略
    }
}
new B() instanceof B;  // true

// Test 3: 派生类 return 对象
class Parent {}
class Child extends Parent {
    constructor() {
        super();
        return { override: true };
    }
}
new Child() instanceof Child;   // false
new Child() instanceof Parent;  // false
new Child().override;           // true
```

#### 5.7.9 私有字段 Brand Check 实现

**ES2022 规范要求：**
- 私有字段只能在声明它的类内部访问
- 同类的不同实例可以互相访问私有字段
- 不同类的实例即使有同名字段也不能互相访问

**实现原理：**
每个类有一个唯一的 "brand"（标识），访问私有字段时检查实例是否带有相同 brand。

```java
// ===== NativeClass.java =====

/**
 * Private field brand implementation.
 * Each class has a unique brand object used to validate private field access.
 */
public class NativeClass extends BaseFunction {
    
    // 类的唯一标识，用于私有字段访问验证
    private final Object classBrand = new Object();
    
    // 私有字段存储：brand -> (instance -> (fieldName -> value))
    // 使用 brand 作为外层 key 确保不同类的私有字段完全隔离
    private static final WeakHashMap<Object, WeakHashMap<Object, Map<String, Object>>> 
        privateFieldStorage = new WeakHashMap<>();
    
    /**
     * Get the class brand for private field validation.
     */
    public Object getClassBrand() {
        return classBrand;
    }
    
    /**
     * Check if an object has this class's brand (is an instance of this class).
     * Used for private field access validation.
     */
    public boolean hasBrand(Object obj) {
        if (obj instanceof Scriptable) {
            Object brand = ((Scriptable) obj).get("__brand__", (Scriptable) obj);
            return classBrand.equals(brand);
        }
        return false;
    }
    
    /**
     * Apply this class's brand to an instance.
     * Called when creating an instance via constructor.
     */
    public void applyBrand(Scriptable instance) {
        instance.put("__brand__", instance, classBrand);
        
        // 同时在私有字段存储中注册
        WeakHashMap<Object, Map<String, Object>> classStorage = 
            privateFieldStorage.computeIfAbsent(classBrand, k -> new WeakHashMap<>());
        classStorage.put(instance, new HashMap<>());
    }
}

// ===== ScriptRuntime.java =====

/**
 * Get a private field value with brand check.
 * 
 * @param instance the object to read from
 * @param fieldName the private field name (without #)
 * @param classBrand the class brand for validation
 * @return the field value
 * @throws TypeError if instance doesn't have the brand
 */
public static Object getPrivateField(Object instance, String fieldName, Object classBrand) {
    // 检查 brand
    if (instance instanceof Scriptable) {
        Object instanceBrand = ((Scriptable) instance).get("__brand__", (Scriptable) instance);
        if (!classBrand.equals(instanceBrand)) {
            throw ScriptRuntime.typeErrorById("msg.private.field.access", fieldName);
        }
    } else {
        throw ScriptRuntime.typeErrorById("msg.private.field.access", fieldName);
    }
    
    // 从存储中获取值
    WeakHashMap<Object, Map<String, Object>> classStorage = privateFieldStorage.get(classBrand);
    if (classStorage == null) {
        return Undefined.instance;
    }
    
    Map<String, Object> fields = classStorage.get(instance);
    if (fields == null) {
        return Undefined.instance;
    }
    
    Object value = fields.get(fieldName);
    return value != null ? value : Undefined.instance;
}

/**
 * Set a private field value with brand check.
 */
public static void setPrivateField(Object instance, String fieldName, Object value, Object classBrand) {
    // 检查 brand
    if (instance instanceof Scriptable) {
        Object instanceBrand = ((Scriptable) instance).get("__brand__", (Scriptable) instance);
        if (!classBrand.equals(instanceBrand)) {
            throw ScriptRuntime.typeErrorById("msg.private.field.access", fieldName);
        }
    } else {
        throw ScriptRuntime.typeErrorById("msg.private.field.access", fieldName);
    }
    
    // 设置值
    WeakHashMap<Object, Map<String, Object>> classStorage = 
        privateFieldStorage.computeIfAbsent(classBrand, k -> new WeakHashMap<>());
    
    Map<String, Object> fields = classStorage.computeIfAbsent(instance, k -> new HashMap<>());
    fields.put(fieldName, value);
}

/**
 * Check if a private field exists on an object (for 'in' operator support).
 */
public static boolean hasPrivateField(Object instance, String fieldName, Object classBrand) {
    if (!classBrand.equals(((Scriptable) instance).get("__brand__", (Scriptable) instance))) {
        return false;
    }
    
    WeakHashMap<Object, Map<String, Object>> classStorage = privateFieldStorage.get(classBrand);
    if (classStorage == null) return false;
    
    Map<String, Object> fields = classStorage.get(instance);
    return fields != null && fields.containsKey(fieldName);
}
```

**跨实例访问示例：**

```javascript
class A {
    #x = 1;
    
    getX(other) {
        // 同类实例可以访问私有字段
        return other.#x;  // ✅ 允许（如果 other 是 A 的实例）
    }
}

class B {
    #x = 2;
}

var a1 = new A();
var a2 = new A();
var b = new B();

a1.getX(a2);  // ✅ 返回 1（同类实例）
a1.getX(b);   // ❌ TypeError: Cannot read private field #x of an object
```

#### 5.7.10 Symbol.species 处理

**ES6 规范要求：**
- 内置类（Array, Map, Set 等）使用 `Symbol.species` 创建派生对象
- 派生类继承时，数组方法返回派生类实例而非 Array 实例

**实现方案：**

```java
// ===== ScriptRuntime.java =====

/**
 * Get the species constructor for a constructor.
 * Used by built-in methods that create derived instances.
 * 
 * @param constructor the original constructor
 * @param defaultConstructor the default constructor to use if no species
 * @return the constructor to use for creating derived instances
 */
public static Object getSpeciesConstructor(Scriptable constructor, Scriptable defaultConstructor) {
    // 获取 constructor[Symbol.species]
    Object species = null;
    if (constructor instanceof Scriptable) {
        Object symbolKey = getSymbolKey("species");
        if (symbolKey != null) {
            species = ((Scriptable) constructor).get(symbolKey, constructor);
        }
    }
    
    // species 为 undefined 或 null 时使用原构造器
    if (species == null || species == Undefined.instance) {
        return constructor;
    }
    
    // species 必须是构造器
    if (!(species instanceof Callable)) {
        throw ScriptRuntime.typeErrorById("msg.not.ctor", ScriptRuntime.toString(species));
    }
    
    return species;
}

/**
 * Create a new instance using species constructor.
 * Used by Array.prototype.map, filter, etc.
 */
public static Scriptable speciesCreate(
        Context cx, Scriptable scope, 
        Scriptable originalConstructor,
        Object[] args) {
    
    Object speciesCtor = getSpeciesConstructor(originalConstructor, null);
    
    if (speciesCtor instanceof Scriptable) {
        Scriptable ctor = (Scriptable) speciesCtor;
        // 调用构造器
        if (ctor instanceof BaseFunction) {
            return ((BaseFunction) ctor).construct(cx, scope, args);
        }
    }
    
    // 默认使用 Array
    return cx.newArray(scope, args.length);
}
```

**NativeClass 中支持 Symbol.species：**

```java
// ===== NativeClass.java =====

static {
    // 注册 Symbol.species getter
    try {
        ScriptableObject.defineProperty(
            NativeClass.prototype, 
            "species", 
            new SpeciesGetter(),
            ScriptableObject.DONTENUM | ScriptableObject.READONLY
        );
    } catch (Exception e) {
        // Ignore if already defined
    }
}

/**
 * Getter for Symbol.species - returns 'this' by default.
 */
private static class SpeciesGetter extends BaseFunction {
    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        // 默认返回 this（即构造器本身）
        return thisObj;
    }
}
```

**测试用例：**

```javascript
// Test 1: 继承 Array，map 返回派生类实例
class MyArray extends Array {
    static get [Symbol.species]() { return Array; }
}

var ma = new MyArray(1, 2, 3);
var mapped = ma.map(x => x * 2);

mapped instanceof MyArray;  // false（因为 Symbol.species 返回 Array）
mapped instanceof Array;    // true

// Test 2: 不覆盖 Symbol.species
class MyArray2 extends Array {}

var ma2 = new MyArray2(1, 2, 3);
var mapped2 = ma2.map(x => x * 2);

mapped2 instanceof MyArray2;  // true（默认行为）
mapped2 instanceof Array;     // true
```

#### 5.7.11 类字面量中的 this 绑定语义

**ES6 规范要求：**
- 类方法中的 `this` 动态绑定到调用者
- 静态方法中的 `this` 指向类本身
- 箭头函数在类中捕获定义时的 `this`
- 字段初始化器中的 `this` 是正在构造的实例

**实现方案：**

```java
// ===== IRFactory.java - 处理类方法中的 this =====

/**
 * Transform class method with proper this binding semantics.
 */
private Node transformClassMethod(ClassElement element, ClassNode classNode) {
    FunctionNode method = element.getMethod();
    
    // 标记方法类型
    if (element.isGetter()) {
        method.setFunctionForm(FunctionNode.Form.GETTER);
    } else if (element.isSetter()) {
        method.setFunctionForm(FunctionNode.Form.SETTER);
    } else {
        method.setFunctionForm(FunctionNode.Form.METHOD);
    }
    
    // 方法不是箭头函数，this 是动态绑定
    // 无需特殊处理，运行时自动绑定
    
    Node methodIR = transformFunction(method);
    
    // 设置 home object 用于 super 访问
    if (classNode.hasSuperClass() && !element.isStatic()) {
        methodIR.putProp(Node.HOME_OBJECT_PROP, 1);
    }
    
    return methodIR;
}

/**
 * Transform arrow function in class context.
 * Arrow functions capture this from enclosing scope.
 */
private Node transformArrowFunctionInClass(FunctionNode arrowFn, ClassNode classNode) {
    // 箭头函数需要捕获 this
    // 在构造器/方法中，this 指向实例
    // 在静态方法/字段中，this 指向类
    
    // 标记为箭头函数
    arrowFn.setFunctionForm(FunctionNode.Form.ARROW);
    
    // 转换时会自动捕获 this
    return transformFunction(arrowFn);
}
```

**字段初始化器中的 this：**

```java
/**
 * Transform field initializer - this refers to the instance being constructed.
 */
private Node transformClassField(ClassElement element, boolean isDerived) {
    Name key = element.getKey();
    AstNode valueNode = element.getFieldValue();
    
    // 创建 this.fieldName = value
    Node thisNode = new Node(Token.THIS);
    Node lhs = new Node(Token.GETPROP, thisNode, Node.newString(key.getString()));
    
    // 转换初始化表达式
    // 初始化器中的 this 指向正在构造的实例
    Node rhs = valueNode != null ? transform(valueNode) : new Node(Token.UNDEFINED);
    
    Node assign = createAssignment(Token.ASSIGN, lhs, rhs);
    return new Node(Token.EXPR_VOID, assign);
}
```

**测试用例：**

```javascript
// Test 1: 方法中的 this 动态绑定
class A {
    getValue() { return this.x; }
}

var obj = { x: 42, method: A.prototype.getValue };
obj.method();  // 42（this 是 obj）

// Test 2: 箭头函数捕获 this
class B {
    constructor() {
        this.x = 1;
    }
    getArrow() {
        return () => this.x;  // 捕获实例 this
    }
}

var b = new B();
var arrow = b.getArrow();
arrow();  // 1（即使独立调用）

// Test 3: 静态方法中的 this
class C {
    static whoAmI() {
        return this.name;  // this 是类本身
    }
}
C.whoAmI();  // "C"

// Test 4: 字段初始化器中的 this
class D {
    x = 1;
    y = this.x + 1;  // this 是实例
}
new D().y;  // 2
```

#### 5.7.12 静态初始化块控制流限制

**ES2022 规范要求：**
- 静态块中不能使用 `return`、`break`、`continue` 跨出块
- `super` 访问受限
- `arguments` 和 `new.target` 不可用

**Parser 层检查：**

```java
// ===== Parser.java =====

/**
 * Parse static initialization block.
 * Static blocks have special restrictions on control flow.
 */
private AstNode parseStaticBlock(int lineno) throws IOException {
    // 检查是否在类上下文中
    if (!inClassContext) {
        reportError("msg.static.block.outside.class");
    }
    
    Block block = new Block(lineno);
    pushScope(block);
    
    try {
        // 标记在静态块中，用于控制流检查
        inStaticBlock = true;
        
        mustMatchToken(Token.LC, "msg.no.brace.static.block");
        
        // 解析块内容
        while (true) {
            int tt = peekToken();
            if (tt == Token.RC) {
                break;
            }
            
            AstNode stmt = parseStatement();
            
            // 检查非法控制流语句
            validateStaticBlockStatement(stmt);
            
            block.addStatement(stmt);
        }
        
        mustMatchToken(Token.RC, "msg.no.brace.after.static.block");
        
    } finally {
        inStaticBlock = false;
        popScope();
    }
    
    return block;
}

/**
 * Validate that a statement is valid in a static block.
 * return, break, continue are not allowed.
 */
private void validateStaticBlockStatement(AstNode stmt) {
    if (stmt instanceof ReturnStatement) {
        reportError("msg.static.block.return", stmt.getLineno());
    }
    
    if (stmt instanceof BreakStatement) {
        // 只有在循环内部的 break 才允许
        BreakStatement brk = (BreakStatement) stmt;
        if (brk.getTarget() == null) {
            // 没有 target 说明是 break; 形式，不允许
            if (!isInLoop()) {
                reportError("msg.static.block.break", stmt.getLineno());
            }
        }
    }
    
    if (stmt instanceof ContinueStatement) {
        // 只有在循环内部的 continue 才允许
        if (!isInLoop()) {
            reportError("msg.static.block.continue", stmt.getLineno());
        }
    }
    
    // 递归检查子节点
    for (AstNode child : stmt) {
        validateStaticBlockStatement(child);
    }
}

/**
 * Check if we're inside a loop (for break/continue validation).
 */
private boolean isInLoop() {
    for (AstNode node : scopeStack) {
        if (node instanceof ForLoop || 
            node instanceof WhileLoop || 
            node instanceof DoLoop ||
            node instanceof ForInLoop) {
            return true;
        }
    }
    return false;
}
```

**IRFactory 层检查：**

```java
// ===== IRFactory.java =====

/**
 * Transform static block with IIFE pattern.
 * Validates no illegal control flow.
 */
private Node transformStaticBlock(ClassElement element, String className) {
    // 运行时再次检查（以防 AST 被修改）
    validateStaticBlockIR(element.getStaticBlock());
    
    Block block = element.getStaticBlock();
    
    // 创建 IIFE: (function() { ... }).call(ClassName)
    FunctionNode fn = new FunctionNode(block.getLineno());
    fn.setFunctionName("");
    fn.setFunctionType(FunctionNode.FUNCTION_EXPRESSION);
    fn.setBody(block);
    
    Node fnIR = transformFunction(fn);
    
    // .call(ClassName)
    Node call = new Node(Token.CALL);
    Node callMethod = new Node(Token.GETPROP);
    callMethod.addChildToBack(fnIR);
    callMethod.addChildToBack(Node.newString("call"));
    call.addChildToBack(callMethod);
    call.addChildToBack(parser.createName(className));
    
    return new Node(Token.EXPR_VOID, call);
}

/**
 * Runtime validation of static block control flow.
 */
private void validateStaticBlockIR(AstNode block) {
    // 遍历 AST 检查非法语句
    validateStaticBlockNode(block);
}

private void validateStaticBlockNode(AstNode node) {
    if (node == null) return;
    
    // 检查 return（除非在嵌套函数中）
    if (node instanceof ReturnStatement && !isInNestedFunction()) {
        throw new IllegalArgumentException("Invalid return in static block");
    }
    
    // 递归检查
    for (AstNode child : node) {
        validateStaticBlockNode(child);
    }
}
```

#### 5.7.13 类声明提升行为

**ES6 规范要求：**
- 类声明**不会**提升（与函数声明不同）
- 类声明有 TDZ（Temporal Dead Zone）
- 访问类声明之前的类会导致 ReferenceError

**实现方案：**

```java
// ===== Parser.java =====

/**
 * Parse class declaration.
 * Class declarations are NOT hoisted (unlike function declarations).
 * 
 * <p>This is handled by:
 * <ol>
 *   <li>Not adding class name to scope during parsing</li>
 *   <li>Creating binding only when reaching the class declaration</li>
 * </ol>
 */
private AstNode parseClassDeclaration() throws IOException {
    int lineno = ts.lineno;
    
    mustMatchToken(Token.CLASS, "msg.no.class");
    
    // 获取类名（可选）
    Name className = null;
    if (peekToken() == Token.NAME) {
        consumeToken();
        className = createNameNode();
    }
    
    // 创建 ClassNode
    ClassNode classNode = new ClassNode(lineno);
    classNode.setName(className != null ? className.getIdentifier() : "");
    classNode.setIsClassStatement(true);
    
    // 解析 extends 和类体
    if (matchToken(Token.EXTENDS)) {
        AstNode superClass = parseExpression();
        classNode.setSuperClass(superClass);
    }
    
    parseClassBody(classNode);
    
    // 类声明不会提升，在当前位置创建绑定
    // 变量声明已经在当前作用域中创建（如果有 let/const）
    
    return classNode;
}

/**
 * Handle class declaration hoisting check.
 * Unlike functions, classes are not hoisted.
 */
private void checkClassHoisting(String className, int lineno) {
    // 检查是否在类声明之前有引用
    // 这需要在运行时检测
    
    // 在作用域中记录类声明的位置
    currentScope.putDeclarationPosition(className, lineno);
}
```

**运行时 TDZ 检查：**

```java
// ===== ScriptRuntime.java =====

/**
 * Get a variable with TDZ check for class declarations.
 * 
 * @param name the variable name
 * @param scope the current scope
 * @param currentLine current line number
 * @return the variable value
 * @throws ReferenceError if accessing before declaration (TDZ)
 */
public static Object getVarWithTDZCheck(String name, Scriptable scope, int currentLine) {
    // 检查 TDZ
    if (scope instanceof NativeBlock) {
        Integer declLine = ((NativeBlock) scope).getDeclarationLine(name);
        if (declLine != null && currentLine < declLine) {
            // 在声明行之前访问
            throw ScriptRuntime.referenceErrorById("msg.let.before.init", name);
        }
    }
    
    return getObjectProp(scope, name, scope);
}
```

**测试用例：**

```javascript
// Test 1: 类声明不提升
try {
    new A();  // ReferenceError
    class A {}
} catch(e) {
    console.log("Class not hoisted");
}

// Test 2: 函数声明提升（对比）
foo();  // ✅ 可以调用
function foo() { console.log("hoisted"); }

// Test 3: 类在块级作用域中的 TDZ
{
    try {
        B;  // ReferenceError (TDZ)
    } catch(e) {}
    class B {}
    B;  // ✅ OK
}
```

#### 5.7.14 类表达式名称绑定作用域

**ES6 规范要求：**
- 类表达式的名称只在类内部可见
- 类表达式名称不会泄漏到外部作用域
- 类表达式的 `name` 属性是类名

**实现方案：**

```java
// ===== Parser.java =====

/**
 * Parse class expression with scoped name binding.
 * The class name is only visible inside the class.
 */
private AstNode parseClassExpression() throws IOException {
    int lineno = ts.lineno;
    
    mustMatchToken(Token.CLASS, "msg.no.class");
    
    // 获取可选的类名
    Name className = null;
    String classNameStr = null;
    
    if (peekToken() == Token.NAME) {
        consumeToken();
        className = createNameNode();
        classNameStr = className.getIdentifier();
    }
    
    // 创建 ClassNode
    ClassNode classNode = new ClassNode(lineno);
    classNode.setName(classNameStr != null ? classNameStr : "");
    classNode.setIsClassStatement(false);  // 表达式，不是声明
    
    // 类名只在类内部可见
    // 创建一个新的作用域用于类名绑定
    if (classNameStr != null) {
        pushClassScope(classNode, classNameStr);
    }
    
    try {
        // 解析 extends 和类体
        if (matchToken(Token.EXTENDS)) {
            AstNode superClass = parseExpression();
            classNode.setSuperClass(superClass);
        }
        
        parseClassBody(classNode);
        
    } finally {
        if (classNameStr != null) {
            popClassScope();
        }
    }
    
    return classNode;
}

/**
 * Push a scope for class expression name binding.
 */
private void pushClassScope(ClassNode classNode, String className) {
    // 创建一个特殊的作用域，类名只在这个作用域内可见
    // 这个作用域不会影响外部变量查找
    classNode.setClassNameScope(className);
}
```

**IRFactory 处理：**

```java
// ===== IRFactory.java =====

/**
 * Transform class expression.
 * The class name (if any) is only visible inside the class.
 */
private Node transformClassExpression(ClassNode classNode) {
    String className = classNode.getName();
    
    // 类表达式名称不在外部作用域可见
    // 但在类内部（静态方法、静态字段）可以引用
    
    // 转换为 IR
    Node classIR = transformClass(classNode);
    
    // 如果有类名，确保 name 属性被设置
    if (className != null && !className.isEmpty()) {
        // name 属性已在 NativeClass 中设置
    }
    
    return classIR;
}
```

**测试用例：**

```javascript
// Test 1: 类表达式名称不泄漏
var A = class B {};
typeof B;  // "undefined"（B 不在外部可见）
A.name;    // "B"（name 属性是 B）

// Test 2: 类表达式名称在内部可见
var C = class D {
    static whoAmI() {
        return D.name;  // ✅ D 在内部可见
    }
};
C.whoAmI();  // "D"

// Test 3: 匿名类表达式
var E = class {};
E.name;  // "E"（从变量名推断，或 "E"）

// Test 4: 类表达式名称与外部变量
var F = class G {
    static getOuter() {
        return typeof G;  // "function"（内部可见）
    }
};
typeof G;  // "undefined"（外部不可见）
F.getOuter();  // "function"
```

```properties
# ========================================
# Class Declaration Errors
# ========================================
msg.no.brace.class=Expected '{' after class declaration
msg.no.brace.after.class=Expected '}' at end of class body
msg.no.brace.static.block=Expected '}' at end of static block
msg.bad.class.element=Invalid class element
msg.bad.getter.setter=Invalid getter or setter definition
msg.bad.class.name=Invalid class name

# ========================================
# Constructor Errors
# ========================================
msg.constructor.static=Class constructor may not be static
msg.constructor.generator=Class constructor may not be a generator
msg.constructor.async=Class constructor may not be async
msg.constructor.getter=Class constructor may not be a getter
msg.constructor.setter=Class constructor may not be a setter
msg.constructor.accessor=Class constructor may not be an accessor
msg.duplicate.constructor=Duplicate constructor in the same class
msg.class.not.new=Class constructor {0} cannot be invoked without 'new'

# ========================================
# super() Related Errors
# ========================================
msg.super.not.called=Must call super() in derived constructor before accessing 'this' or returning
msg.super.already.called=super() has already been called in this constructor
msg.super.call.not.derived=super() can only be called in derived class constructors
msg.super.call.outside=super() cannot be called outside a class constructor
msg.this.before.super=Cannot access 'this' before calling super()

# ========================================
# Private Field Errors (ES2022)
# ========================================
msg.private.field.access=Cannot access private field #{0} from outside the class
msg.private.field.not.found=Private field #{0} does not exist on this class
msg.private.field.invalid.receiver=Cannot read private field #{0} from an object whose class did not declare it
msg.private.constructor=Private field '#constructor' is not allowed
msg.invalid.private.field=Invalid private field name
msg.duplicate.private.field=Duplicate private field #{0}
msg.private.static.instance=Cannot access static private field #{0} on an instance
msg.private.instance.static=Cannot access instance private field #{0} on the class

# ========================================
# extends Related Errors
# ========================================
msg.extends.not.constructor=Class extends value {0} is not a constructor or null
msg.extends.null=Class extends null is not yet supported
msg.circular.extends=Circular inheritance detected

# ========================================
# Static Block Errors
# ========================================
msg.static.block.return=Invalid return in static block
msg.static.block.break=Invalid break in static block
msg.static.block.continue=Invalid continue in static block
```

#### 5.7.10 线程安全与序列化（v1.11 新增）

##### 5.7.10.1 问题分析

根据代码探索发现，计划中的私有字段存储方案存在以下风险：

| 问题 | 风险等级 | 说明 |
|------|----------|------|
| WeakHashMap 线程安全 | **中** | 多线程访问可能数据不一致 |
| classBrand 序列化 | **高** | 每次反序列化生成新对象，brand 检查失效 |
| transient 字段丢失 | **高** | 反序列化后私有字段数据丢失 |

##### 5.7.10.2 并发访问解决方案

**问题**：`WeakHashMap` 非线程安全，多线程环境可能导致数据不一致。

**解决方案**：使用 `ConcurrentHashMap` 或同步包装器。

```java
// NativeClass.java 修改

// 方案 1: 使用 ConcurrentHashMap（推荐）
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class NativeClass extends BaseFunction {
    // 私有字段存储：instance -> (fieldName -> value)
    private transient ConcurrentMap<Object, Map<String, Object>> privateFieldStorage =
        new ConcurrentHashMap<>();
    
    // 线程安全的私有字段访问
    public Object getPrivateField(Object instance, String fieldName) {
        Map<String, Object> fields = privateFieldStorage.get(instance);
        if (fields == null) {
            return undefinedValue;
        }
        synchronized (fields) {
            return fields.getOrDefault(fieldName, undefinedValue);
        }
    }
    
    public void setPrivateField(Object instance, String fieldName, Object value) {
        privateFieldStorage.computeIfAbsent(instance, k -> new ConcurrentHashMap<>())
            .put(fieldName, value);
    }
}
```

##### 5.7.10.3 序列化支持

**问题**：`classBrand = new Object()` 每次反序列化会创建新对象，导致 brand 检查失败。

**解决方案**：使用 UUID 字符串作为 brand。

```java
// NativeClass.java 修改

import java.util.UUID;

public class NativeClass extends BaseFunction {
    private static final long serialVersionUID = 1L;
    
    // 使用 UUID 替代 Object 引用
    private final String classBrand = UUID.randomUUID().toString();
    
    // transient 字段需要在反序列化时重建
    private transient ConcurrentMap<Object, Map<String, Object>> privateFieldStorage =
        new ConcurrentHashMap<>();
    
    // 序列化支持
    private void readObject(java.io.ObjectInputStream stream)
            throws java.io.IOException, ClassNotFoundException {
        stream.defaultReadObject();
        // 重建私有字段存储
        privateFieldStorage = new ConcurrentHashMap<>();
    }
    
    // Brand 检查方法
    public boolean checkBrand(Object instance, String brand) {
        return this.classBrand.equals(brand);
    }
    
    public String getBrand() {
        return classBrand;
    }
}
```

##### 5.7.10.4 完整实现代码

```java
package org.mozilla.javascript;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Runtime representation of a JavaScript class (ES2022).
 * Extends BaseFunction to support constructor call semantics.
 */
public class NativeClass extends BaseFunction {
    private static final long serialVersionUID = 1L;
    
    // Unique identifier for private field brand checking
    private final String classBrand = UUID.randomUUID().toString();
    
    // Thread-safe storage for private fields: instance -> (fieldName -> value)
    private transient ConcurrentMap<Object, Map<String, Object>> privateFieldStorage =
        new ConcurrentHashMap<>();
    
    // Class metadata
    private final String className;
    private final Scriptable superClass;
    private final Scriptable prototype;
    
    // Constructor
    public NativeClass(String className, Scriptable superClass, Scriptable prototype) {
        this.className = className;
        this.superClass = superClass;
        this.prototype = prototype;
    }
    
    // ===== Private Field Access =====
    
    public Object getPrivateField(Object instance, String fieldName) {
        Map<String, Object> fields = privateFieldStorage.get(instance);
        if (fields == null) {
            throw ScriptRuntime.typeErrorById("msg.private.field.not.found", fieldName);
        }
        Object value = fields.get(fieldName);
        return value != null ? value : Undefined.instance;
    }
    
    public void setPrivateField(Object instance, String fieldName, Object value) {
        privateFieldStorage
            .computeIfAbsent(instance, k -> new ConcurrentHashMap<>())
            .put(fieldName, value);
    }
    
    public boolean hasPrivateField(Object instance, String fieldName) {
        Map<String, Object> fields = privateFieldStorage.get(instance);
        return fields != null && fields.containsKey(fieldName);
    }
    
    // ===== Brand Checking =====
    
    /**
     * Check if the instance belongs to this class (for private field access).
     */
    public boolean checkBrand(Object instance) {
        // Check if instance was created by this class
        return privateFieldStorage.containsKey(instance);
    }
    
    public String getBrand() {
        return classBrand;
    }
    
    // ===== Serialization Support =====
    
    private void readObject(ObjectInputStream stream) 
            throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        // Rebuild transient field
        privateFieldStorage = new ConcurrentHashMap<>();
    }
    
    // ===== BaseFunction Overrides =====
    
    @Override
    public String getFunctionName() {
        return className;
    }
    
    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        // Classes cannot be called without new
        throw ScriptRuntime.typeErrorById("msg.class.requires.new", className);
    }
    
    @Override
    public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
        // Create new instance
        Scriptable instance = createInstance(cx, scope);
        // Call constructor
        callConstructor(cx, scope, instance, args);
        return instance;
    }
}
```

### 5.8 优化编译器支持（v1.11 新增）

#### 5.8.1 同时支持两种模式的实现策略

根据代码探索发现，`optimizer/Codegen.java` 当前完全不支持类定义。为实现同时支持解释模式和优化模式，采用并行开发策略。

| 模式 | 优化级别 | 支持状态 | 实现内容 | 开发阶段 |
|------|----------|----------|----------|----------|
| **解释模式** | -1, -2 | ✅ 可用 | Token、Parser、Interpreter 支持 | M0-M5 |
| **优化模式** | 0-9 | ❌ 待实现 | Codegen、BodyCodegen JVM 字节码生成 | M8（与 M5 并行）|

**并行开发策略**：
- M0-M4：解释模式核心实现（顺序执行）
- M5 + M8：运行时支持与优化编译器支持并行开发
- M6：统一测试验证两种模式

**关键原则**：
1. 解释模式优先验证语义正确性
2. 优化模式复用解释模式的 IR 结构
3. 双模式共享相同的测试用例
4. 自动降级机制作为后备方案

#### 5.8.2 第一阶段实现（解释模式）

第一阶段不需要修改优化器，类定义在解释模式下运行：

```java
// 使用方式
Context cx = Context.enter();
cx.setOptimizationLevel(-1);  // 解释模式
// cx.setOptimizationLevel(9);  // 优化模式 - 第二阶段支持
```

#### 5.8.3 第二阶段需要修改的文件

| 文件 | 位置 | 修改内容 | 预估代码量 |
|------|------|----------|-----------|
| `optimizer/Codegen.java` | `transform()` | 类节点 JVM 字节码生成入口 | ~50 行 |
| `optimizer/BodyCodegen.java` | `visitStatement()` | 添加 `case Token.CLASS` | ~80 行 |
| `optimizer/Optimizer.java` | 全局 | 类节点优化规则 | ~30 行 |
| `optimizer/OptFunctionNode.java` | 新增字段 | 类节点信息存储 | ~20 行 |

#### 5.8.4 Codegen.java 修改示例

```java
// optimizer/Codegen.java

@Override
public byte[] compileToClassFile(
        CompilerEnvirons compilerEnv,
        JSDescriptor.Builder<?> builder,
        OptJSCode.BuilderEnv builderEnv,
        String mainClassName,
        ScriptNode scriptOrFn,
        String rawSource,
        boolean returnFunction) {
    this.compilerEnv = compilerEnv;

    // 检查是否包含类定义（优化模式暂不支持）
    if (!compilerEnv.isInterpretedMode() && containsClassDefinition(scriptOrFn)) {
        // 方案 A: 自动降级到解释模式
        // 方案 B: 抛出异常提示用户
        throw new RuntimeException(
            "Class definitions are not yet supported in optimized mode. " +
            "Use Context.setOptimizationLevel(-1) for interpreted mode.");
    }

    transform(scriptOrFn);
    // ... 其余代码 ...
}

/**
 * 检查脚本是否包含类定义
 */
private boolean containsClassDefinition(ScriptNode node) {
    // 递归检查 AST 是否包含 Token.CLASS
    return containsClassDefinition_r(node);
}

private boolean containsClassDefinition_r(Node node) {
    if (node.getType() == Token.CLASS) {
        return true;
    }
    for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
        if (containsClassDefinition_r(child)) {
            return true;
        }
    }
    return false;
}
```

#### 5.8.5 BodyCodegen.java 修改示例

```java
// optimizer/BodyCodegen.java

private void visitStatement(Node node) {
    int type = node.getType();
    
    switch (type) {
        // ... 现有 case ...
        
        case Token.CLASS:
            // 第二阶段：生成类定义的 JVM 字节码
            visitClassDefinition(node);
            break;
            
        // ... 其他 case ...
    }
}

/**
 * 生成类定义的 JVM 字节码（第二阶段实现）
 */
private void visitClassDefinition(Node classNode) {
    // TODO: 第二阶段实现
    // 1. 生成构造器函数字节码
    // 2. 生成原型方法字节码
    // 3. 生成静态方法字节码
    // 4. 生成字段初始化代码
    // 5. 生成静态块执行代码
    throw new UnsupportedOperationException(
        "Class definitions in optimized mode will be supported in phase 2");
}
```

#### 5.8.6 自动降级策略

为了更好的用户体验，可以实现自动降级：

```java
// Codegen.java - 改进的 transform() 方法

private void transform(ScriptNode tree) {
    initOptFunctions_r(tree);

    // 检测类定义，自动降级到解释模式
    if (!compilerEnv.isInterpretedMode() && containsClassDefinition(tree)) {
        // 记录警告日志
        if (compilerEnv.isGenerateDebugInfo()) {
            System.err.println(
                "Warning: Class definition detected, " +
                "falling back to interpreted mode");
        }
        // 强制使用解释模式
        compilerEnv.setInterpretedMode(true);
    }

    // ... 原有的 transform 逻辑 ...
}
```

#### 5.8.7 实现优先级（并行开发）

| 优先级 | 任务 | 依赖 | 开发阶段 |
|--------|------|------|----------|
| P0 | 解释模式核心实现（Token、Parser、IRFactory） | 无 | M0-M4 |
| P1 | 运行时支持（NativeClass、ScriptRuntime） | P0 | M5 |
| P1 | 优化编译器支持（Codegen、BodyCodegen） | P0 | M8（与 M5 并行）|
| P2 | 自动降级机制 | P1 | M8 |
| P3 | 双模式验证测试 | P1, P1 | M6 |

#### 5.8.8 同时支持两种模式的代码结构

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Class 实现代码结构                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  AST 层（共享）                                                      │
│  ├── ClassNode.java                                                 │
│  ├── ClassElement.java                                              │
│  └── Parser.java → parseClass()                                     │
│                                                                     │
│  IR 层（共享）                                                       │
│  ├── IRFactory.java → transformClass()                              │
│  └── Node.java → CLASS_NAME_PROP 等                                 │
│                                                                     │
│  ┌─────────────────────┐     ┌─────────────────────┐               │
│  │    解释模式路径      │     │    优化模式路径      │               │
│  │    (Interpreter)    │     │     (Codegen)       │               │
│  ├─────────────────────┤     ├─────────────────────┤               │
│  │ CodeGenerator.java  │     │ optimizer/          │               │
│  │ ├── visitClass()    │     │ ├── Codegen.java    │               │
│  │ └── Icode 指令      │     │ └── BodyCodegen.java│               │
│  └─────────────────────┘     └─────────────────────┘               │
│           ↓                           ↓                             │
│  ┌─────────────────────────────────────────────────────┐           │
│  │              运行时层（共享）                          │           │
│  ├── NativeClass.java                                              │
│  ├── UninitializedObject.java                                      │
│  └── ScriptRuntime.java → createClass(), getPrivateField()         │
└─────────────────────────────────────────────────────────────────────┘
```

### 5.9 并行开发流程图（v1.12 新增）

#### 5.9.1 整体开发流程

```
时间轴
  │
  │  M0: Token 验证 + TDZ 原型
  │  ├── Token.java: 新增 CLASS/EXTENDS 等
  │  ├── UniqueTag.java: 新增 TDZ_VALUE
  │  └── 验证测试通过
  │
  │  M1: AST 节点实现
  │  ├── ClassNode.java
  │  └── ClassElement.java
  │
  │  M2: TokenStream 词法分析
  │  └── TokenStream.java: # 私有字段支持
  │
  │  M3: Parser 解析逻辑
  │  └── Parser.java: parseClass*() 系列方法
  │
  │  M4: IRFactory 转换
  │  └── IRFactory.java: transformClass()
  │
  ▼  ┌───────────────────────────────────────────────────────┐
     │                  M5 + M8 并行开发                        │
     ├───────────────────────────┬───────────────────────────┤
     │      M5: 运行时支持        │     M8: 优化编译器支持      │
     ├───────────────────────────┼───────────────────────────┤
     │ NativeClass.java          │ CodeGenerator.java        │
     │ UninitializedObject.java  │   ├── visitClass()        │
     │ ScriptRuntime.createClass │   └── visitPrivateField() │
     │ ScriptRuntime.getPrivate  │ optimizer/Codegen.java    │
     │ BaseFunction.construct    │   └── generateClassCode() │
     │                           │ BodyCodegen.java          │
     │                           │   └── visitStatement()    │
     └───────────────────────────┴───────────────────────────┘
                        │
                        ▼
     ┌───────────────────────────────────────────────────────┐
     │              共享测试验证                               │
     │  ├── ClassTest.java (单元测试)                         │
     │  ├── class.js (JS 脚本测试)                            │
     │  └── test262 集成测试                                  │
     └───────────────────────────────────────────────────────┘
                        │
                        ▼
     ┌───────────────────────────────────────────────────────┐
     │              M6: 测试与验证                             │
     │  ├── 解释模式测试 (-1, -2)                             │
     │  ├── 优化模式测试 (0-9)                                │
     │  └── 双模式对比验证                                    │
     └───────────────────────────────────────────────────────┘
                        │
                        ▼
     ┌───────────────────────────────────────────────────────┐
     │              M7: Bug 修复与完善                         │
     └───────────────────────────────────────────────────────┘
```

#### 5.9.2 并行开发依赖关系

```
                    ┌─────────────┐
                    │    M0-M4    │
                    │  (顺序执行) │
                    └──────┬──────┘
                           │
              ┌────────────┴────────────┐
              │                         │
              ▼                         ▼
     ┌─────────────────┐      ┌─────────────────┐
     │       M5        │      │       M8        │
     │   运行时支持     │◄────►│  优化编译器支持  │
     │                 │  共享  │                 │
     │ NativeClass     │  测试  │ Codegen        │
     │ ScriptRuntime   │  用例  │ CodeGenerator  │
     └────────┬────────┘      └────────┬────────┘
              │                         │
              └────────────┬────────────┘
                           │
                           ▼
                    ┌─────────────┐
                    │     M6      │
                    │  测试验证   │
                    └──────┬──────┘
                           │
                           ▼
                    ┌─────────────┐
                    │     M7      │
                    │  Bug 修复   │
                    └─────────────┘
```

#### 5.9.3 并行开发的同步点

| 同步点 | 触发条件 | 同步内容 |
|--------|----------|----------|
| **SP1** | M4 完成 | IR 结构确认，M5/M8 可以开始 |
| **SP2** | M5 完成 NativeClass | M8 可以开始生成类创建字节码 |
| **SP3** | M5 完成 getPrivateField | M8 可以开始生成私有字段访问字节码 |
| **SP4** | M8 完成 CodeGenerator | 可以开始双模式测试 |
| **SP5** | M5 + M8 都完成 | 进入 M6 统一测试 |

### 5.10 自动降级机制（v1.12 新增）

#### 5.10.1 设计目标

当优化模式遇到不支持的 class 特性时，自动降级到解释模式，保证代码可执行。

#### 5.10.2 降级触发条件

| 条件 | 触发时机 | 降级行为 |
|------|----------|----------|
| 检测到 class 定义 | 编译开始时 | 整个脚本降级到解释模式 |
| 检测到私有字段 | 编译开始时 | 整个脚本降级到解释模式 |
| 检测到静态块 | 编译开始时 | 整个脚本降级到解释模式 |
| 优化级别 > 0 且有 class | 编译开始时 | 警告并降级 |

#### 5.10.3 Codegen.java 自动降级实现

```java
// optimizer/Codegen.java

/**
 * 检查脚本是否需要降级到解释模式。
 * 在 transform() 开始时调用。
 * 
 * @param tree 脚本 AST
 * @return true 如果需要降级
 */
private boolean checkAndDowngradeIfNeeded(ScriptNode tree) {
    // 检查优化级别
    int optLevel = compilerEnv.getOptimizationLevel();
    if (optLevel < 0) {
        // 已经是解释模式，无需降级
        return false;
    }
    
    // 检查是否包含类定义
    ClassDefinitionInfo classInfo = detectClassDefinitions(tree);
    if (classInfo.hasClassDefinition) {
        // 记录降级日志
        if (compilerEnv.isGenerateDebugInfo()) {
            System.err.println(
                "[Rhino] Warning: Class definition detected in optimization mode.\n" +
                "[Rhino] Class features: " + classInfo.features + "\n" +
                "[Rhino] Falling back to interpreted mode for correct semantics.\n" +
                "[Rhino] To suppress this warning, use Context.setOptimizationLevel(-1)");
        }
        
        // 强制降级到解释模式
        compilerEnv.setOptimizationLevel(-1);
        return true;
    }
    
    return false;
}

/**
 * 检测脚本中的类定义信息
 */
private static class ClassDefinitionInfo {
    boolean hasClassDefinition;
    boolean hasPrivateFields;
    boolean hasStaticBlocks;
    boolean hasSuperCall;
    String features;
    
    void addFeature(String feature) {
        if (features == null) {
            features = feature;
        } else {
            features += ", " + feature;
        }
    }
}

private ClassDefinitionInfo detectClassDefinitions(Node node) {
    ClassDefinitionInfo info = new ClassDefinitionInfo();
    detectClassDefinitions_r(node, info);
    return info;
}

private void detectClassDefinitions_r(Node node, ClassDefinitionInfo info) {
    int type = node.getType();
    
    if (type == Token.CLASS) {
        info.hasClassDefinition = true;
        info.addFeature("class");
    }
    
    if (type == Token.PRIVATE_FIELD || type == Token.GET_PRIVATE_FIELD) {
        info.hasPrivateFields = true;
        info.addFeature("private-field");
    }
    
    if (type == Token.STATIC_BLOCK) {
        info.hasStaticBlocks = true;
        info.addFeature("static-block");
    }
    
    // 递归检查子节点
    for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
        detectClassDefinitions_r(child, info);
    }
}
```

#### 5.10.4 降级对用户的影响

| 场景 | 用户可见行为 | 性能影响 |
|------|--------------|----------|
| 纯 ES5 代码 | 无变化 | 无影响 |
| ES6 class（优化模式） | 警告日志 + 降级 | 可能变慢 |
| ES6 class（解释模式） | 无变化 | 无影响 |
| 混合代码（含 class） | 整体降级 | 非类代码也变慢 |

#### 5.10.5 用户控制选项

```java
// 用户可以通过以下方式控制降级行为

// 方式 1：显式使用解释模式
Context cx = Context.enter();
cx.setOptimizationLevel(-1);  // 无警告

// 方式 2：允许优化模式失败抛异常
cx.setClassOptimizationFallback(false);  // 新增选项
// 此选项为 false 时，遇到 class 定义会抛出异常而非降级

// 方式 3：查询当前是否降级
boolean isDowngraded = cx.isOptimizationDowngraded();
```

### 5.11 双模式验证方案（v1.12 新增）

#### 5.11.1 验证目标

确保解释模式和优化模式对 class 语法的执行结果完全一致。

#### 5.11.2 验证矩阵

| 验证项 | 解释模式 (-1) | 优化模式 (9) | 验证方法 |
|--------|---------------|--------------|----------|
| 基础 class 声明 | ✅ | ⚠️ 待验证 | 比较执行结果 |
| class 表达式 | ✅ | ⚠️ 待验证 | 比较执行结果 |
| constructor | ✅ | ⚠️ 待验证 | 比较实例属性 |
| 方法（实例/静态） | ✅ | ⚠️ 待验证 | 比较返回值 |
| getter/setter | ✅ | ⚠️ 待验证 | 比较属性访问 |
| extends 继承 | ✅ | ⚠️ 待验证 | 比较 instanceof |
| super() 调用 | ✅ | ⚠️ 待验证 | 比较父类构造 |
| super 属性访问 | ✅ | ⚠️ 待验证 | 比较属性值 |
| 公有字段 | ✅ | ⚠️ 待验证 | 比较字段值 |
| 私有字段 | ✅ | ⚠️ 待验证 | 比较私有值 |
| 私有方法 | ✅ | ⚠️ 待验证 | 比较方法结果 |
| 静态块 | ✅ | ⚠️ 待验证 | 比较副作用 |
| new.target | ✅ | ⚠️ 待验证 | 比较 new.target 值 |

#### 5.11.3 自动化验证脚本

```java
// tests/src/test/java/org/mozilla/javascript/tests/es2022/DualModeVerificationTest.java

package org.mozilla.javascript.tests.es2022;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 双模式验证测试：确保解释模式和优化模式执行结果一致
 */
public class DualModeVerificationTest {
    
    /**
     * 在两种模式下执行相同代码并比较结果
     */
    private void assertDualModeEquals(String code, Object expected) {
        // 解释模式执行
        Object interpretedResult = executeWithOptLevel(code, -1);
        // 优化模式执行
        Object optimizedResult = executeWithOptLevel(code, 9);
        
        // 验证两种模式结果一致
        assertEquals(interpretedResult, optimizedResult, 
            "Interpreted and optimized modes should produce same result");
        
        // 验证结果符合预期
        assertEquals(expected, interpretedResult);
    }
    
    private Object executeWithOptLevel(String code, int optLevel) {
        Context cx = Context.enter();
        try {
            cx.setOptimizationLevel(optLevel);
            Scriptable scope = cx.initStandardObjects();
            return cx.evaluateString(scope, code, "test", 1, null);
        } finally {
            Context.exit();
        }
    }
    
    // ===== 基础 class 测试 =====
    
    @Test
    public void testBasicClassDeclaration() {
        assertDualModeEquals(
            "class A {} typeof A",
            "function"
        );
    }
    
    @Test
    public void testClassConstructor() {
        assertDualModeEquals(
            "class B { constructor(x) { this.x = x; } }" +
            "var b = new B(42); b.x",
            42.0
        );
    }
    
    @Test
    public void testClassMethod() {
        assertDualModeEquals(
            "class C { method() { return 42; } }" +
            "new C().method()",
            42.0
        );
    }
    
    // ===== 继承测试 =====
    
    @Test
    public void testBasicInheritance() {
        assertDualModeEquals(
            "class Parent { getValue() { return 'parent'; } }" +
            "class Child extends Parent {}" +
            "new Child().getValue()",
            "parent"
        );
    }
    
    @Test
    public void testSuperMethodCall() {
        assertDualModeEquals(
            "class Parent { method() { return 'parent'; } }" +
            "class Child extends Parent { method() { return super.method() + '-child'; } }" +
            "new Child().method()",
            "parent-child"
        );
    }
    
    @Test
    public void testSuperConstructorCall() {
        assertDualModeEquals(
            "class Parent { constructor(x) { this.x = x; } }" +
            "class Child extends Parent { constructor(x, y) { super(x); this.y = y; } }" +
            "var c = new Child(1, 2); c.x + ',' + c.y",
            "1,2"
        );
    }
    
    // ===== 私有字段测试 =====
    
    @Test
    public void testPrivateField() {
        assertDualModeEquals(
            "class D {" +
            "  #secret = 42;" +
            "  getSecret() { return this.#secret; }" +
            "}" +
            "new D().getSecret()",
            42.0
        );
    }
    
    @Test
    public void testPrivateMethod() {
        assertDualModeEquals(
            "class E {" +
            "  #privateMethod(a, b) { return a + b; }" +
            "  publicMethod(a, b) { return this.#privateMethod(a, b); }" +
            "}" +
            "new E().publicMethod(2, 3)",
            5.0
        );
    }
    
    // ===== 静态块测试 =====
    
    @Test
    public void testStaticBlock() {
        assertDualModeEquals(
            "var executed = false;" +
            "class F { static { executed = true; } }" +
            "executed",
            true
        );
    }
}
```

#### 5.11.4 test262 双模式验证

```bash
# 运行 test262 class 测试（解释模式）
./gradlew :tests:test262 \
    -Dtest262.filter=language/statements/class,language/expressions/class \
    -Dtest262.optLevel=-1

# 运行 test262 class 测试（优化模式）
./gradlew :tests:test262 \
    -Dtest262.filter=language/statements/class,language/expressions/class \
    -Dtest262.optLevel=9

# 比较两种模式的通过率
# 期望：两种模式的通过率应该一致
```

#### 5.11.5 验证检查表

| 检查项 | 检查方法 | 预期结果 |
|--------|----------|----------|
| 解释模式基础测试通过 | `./gradlew test --tests "*ClassTest*"` | 100% 通过 |
| 优化模式基础测试通过 | `setOptimizationLevel(9)` 运行相同测试 | 100% 通过 |
| 双模式结果一致性 | DualModeVerificationTest | 全部断言通过 |
| test262 解释模式通过率 | 上述命令 | > 95% |
| test262 优化模式通过率 | 上述命令 | 与解释模式一致 |
| 性能基准测试 | JMH 或手动计时 | 优化模式 >= 解释模式 |

#### 5.11.6 已知差异与处理

| 差异类型 | 原因 | 处理方式 |
|----------|------|----------|
| 调试信息行号 | 编译后代码行号映射不同 | 可接受，不影响执行 |
| 异常堆栈深度 | 优化可能内联函数 | 可接受，不影响语义 |
| 属性枚举顺序 | 不同实现可能有差异 | 需验证，如发现需修复 |
| 性能差异 | 解释模式较慢 | 可接受，语义正确即可 |

## 七、测试用例

### 7.0 测试文件结构分析

#### 7.0.1 测试目录结构

```
Rhino-For-AutoJs/
├── tests/
│   ├── src/test/java/org/mozilla/javascript/
│   │   ├── drivers/               # 测试驱动和工具类
│   │   │   ├── ScriptTestsBase.java    # JS 脚本测试基类
│   │   │   └── RhinoTest.java          # 测试注解
│   │   └── tests/
│   │       ├── es5/               # ES5 特性测试
│   │       ├── es6/               # ES6 特性测试
│   │       ├── es2022/            # ES2022 特性测试（目标目录）
│   │       │   └── NativeObjectTest.java  # 已有示例
│   │       ├── es2023/
│   │       ├── es2024/
│   │       ├── es2025/
│   │       └── Test262SuiteTest.java    # test262 集成测试
│   ├── testsrc/
│   │   ├── jstests/               # JS 测试脚本
│   │   │   └── es6/               # ES6 JS 测试脚本
│   │   └── test262.properties     # test262 配置文件
│   └── test262/                   # test262 测试套件（git submodule）
```

#### 7.0.2 ClassTest 文件路径建议

| 测试类型 | 文件路径 | 说明 |
|----------|----------|------|
| **单元测试** | `tests/src/test/java/org/mozilla/javascript/tests/es2022/ClassTest.java` | Java 单元测试 |
| **JS 脚本测试** | `tests/testsrc/jstests/es2022/class.js` | JavaScript 测试脚本 |
| **注解测试** | `tests/src/test/java/org/mozilla/javascript/tests/es2022/ClassScriptTest.java` | 使用 @RhinoTest 注解 |

#### 7.0.3 测试基类选择

**方案一：ScriptTestsBase + @RhinoTest（推荐）**

```java
package org.mozilla.javascript.tests.es2022;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.drivers.LanguageVersion;
import org.mozilla.javascript.drivers.RhinoTest;
import org.mozilla.javascript.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/es2022/class.js")
@LanguageVersion(Context.VERSION_ES6)
public class ClassScriptTest extends ScriptTestsBase {
    // 测试逻辑在 class.js 中实现
    // 自动运行解释模式和编译模式
}
```

**方案二：直接使用 Evaluator（适合简单测试）**

```java
package org.mozilla.javascript.tests.es2022;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.tests.Evaluator;

public class ClassTest {
    
    @Test
    public void testBasicClass() {
        Object result = Evaluator.eval("class A {} typeof A");
        assertEquals("function", result.toString());
    }
    
    @Test
    public void testClassConstructor() {
        Object result = Evaluator.eval(
            "class B { constructor(x) { this.x = x; } }" +
            "var b = new B(42); b.x"
        );
        assertEquals(42.0, ((Number)result).doubleValue(), 0.001);
    }
}
```

#### 7.0.4 test262 集成配置

**当前状态**（test262.properties L3980, L5522）：

```properties
~language/expressions/class 4059/4059 (100.0%)
~language/statements/class 4366/4366 (100.0%)
```

- `~` 前缀表示排除这些测试
- 当前所有 class 相关测试都被跳过

**启用 class 测试**：

1. **创建自定义配置文件** `testsrc/test262-class.properties`：

```properties
# 仅运行 class 相关测试
language/expressions/class
language/statements/class
```

2. **运行命令**：

```bash
# 运行自定义 test262 配置
./gradlew :tests:test262 -Dtest262properties=testsrc/test262-class.properties

# 更新 test262.properties（在实现后）
RHINO_TEST_JAVA_VERSION=11 ./gradlew :tests:test262 --rerun-tasks -DupdateTest262properties
```

#### 7.0.5 构建脚本配置

**tests/build.gradle 已配置**：

| 任务 | 命令 | 说明 |
|------|------|------|
| 全部测试 | `./gradlew test` | 包含 test262 |
| 单元测试 | `./gradlew :tests:testWithoutTest262` | 排除 test262 |
| test262 | `./gradlew :tests:test262` | 仅 test262 |

**新增测试无需修改构建脚本**，只需：
1. 将 Java 测试放在 `tests/src/test/java/.../tests/es2022/`
2. 将 JS 脚本放在 `tests/testsrc/jstests/es2022/`

### 7.1 基础测试

```javascript
// Test 1: Basic class declaration
class A {}
typeof A === "function";

// Test 2: Class with constructor
class B {
    constructor(x) {
        this.x = x;
    }
}
var b = new B(1);
b.x === 1;

// Test 3: Class with methods
class C {
    method() { return 42; }
}
var c = new C();
c.method() === 42;

// Test 4: Static methods
class D {
    static staticMethod() { return "static"; }
}
D.staticMethod() === "static";

// Test 5: Getter and setter
class E {
    get value() { return this._value; }
    set value(v) { this._value = v; }
}
var e = new E();
e.value = 10;
e.value === 10;
```

### 7.2 继承测试

```javascript
// Test 6: Basic inheritance
class Animal {
    constructor(name) {
        this.name = name;
    }
    speak() {
        return this.name;
    }
}

class Dog extends Animal {
    constructor(name, breed) {
        super(name);
        this.breed = breed;
    }
    speak() {
        return super.speak() + " barks!";
    }
}

var dog = new Dog("Rex", "German Shepherd");
dog.name === "Rex";
dog.breed === "German Shepherd";
dog.speak() === "Rex barks!";
dog instanceof Dog;
dog instanceof Animal;
```

### 7.3 ES2022 特性测试

```javascript
// Test 7: Public instance fields
class F {
    x = 1;
    y = this.x + 1;
}
var f = new F();
f.x === 1;
f.y === 2;

// Test 8: Static fields
class G {
    static count = 0;
    static {
        G.count = 10;
    }
}
G.count === 10;

// Test 9: Private fields
class H {
    #private = 1;
    getPrivate() { return this.#private; }
    setPrivate(v) { this.#private = v; }
}
var h = new H();
h.getPrivate() === 1;
h.setPrivate(2);
h.getPrivate() === 2;
// h.#private should throw
```

### 7.4 完整 ES2022 Class 测试脚本

```javascript
/**
 * ES2022 Class Feature Test Suite for Rhino
 * 
 * This file tests all ES2022 class features:
 * - Class declarations and expressions
 * - Constructor and super()
 * - Methods (instance and static)
 * - Getters and setters
 * - Public instance fields
 * - Private instance fields (#field)
 * - Private methods (#method)
 * - Private getters/setters
 * - Static fields
 * - Static private fields
 * - Static blocks
 * - Inheritance with extends
 * - Super property access
 * - new.target in constructors
 * 
 * Run with: Rhino ES6+ mode
 */

// ============================================================
// SECTION 1: Class Declaration Basics
// ============================================================

// Test 1.1: Empty class declaration
(function testEmptyClass() {
    class EmptyClass {}
    var result = typeof EmptyClass === "function" && 
                 EmptyClass.prototype.constructor === EmptyClass;
    console.log("1.1 Empty class: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 1.2: Class expression
(function testClassExpression() {
    var ExprClass = class {
        getValue() { return 42; }
    };
    var result = typeof ExprClass === "function" && 
                 new ExprClass().getValue() === 42;
    console.log("1.2 Class expression: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 1.3: Named class expression
(function testNamedClassExpression() {
    var NamedClass = class NamedClassInner {
        getName() { return NamedClassInner.name; }
    };
    var result = NamedClass.name === "NamedClassInner";
    console.log("1.3 Named class expression: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 1.4: Class with constructor
(function testConstructor() {
    class WithConstructor {
        constructor(value) {
            this.value = value;
        }
    }
    var instance = new WithConstructor("test");
    var result = instance.value === "test" && 
                 instance instanceof WithConstructor;
    console.log("1.4 Constructor: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 1.5: Constructor with default parameter
(function testConstructorDefaultParam() {
    class WithDefaultParam {
        constructor(x = 10) {
            this.x = x;
        }
    }
    var result = new WithDefaultParam().x === 10 &&
                 new WithDefaultParam(20).x === 20;
    console.log("1.5 Constructor default param: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// ============================================================
// SECTION 2: Methods
// ============================================================

// Test 2.1: Instance methods
(function testInstanceMethods() {
    class MethodClass {
        add(a, b) { return a + b; }
        multiply(a, b) { return a * b; }
    }
    var instance = new MethodClass();
    var result = instance.add(2, 3) === 5 &&
                 instance.multiply(2, 3) === 6;
    console.log("2.1 Instance methods: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 2.2: Static methods
(function testStaticMethods() {
    class StaticMethodClass {
        static create() { return new StaticMethodClass(); }
        static PI = 3.14159;
    }
    var instance = StaticMethodClass.create();
    var result = instance instanceof StaticMethodClass &&
                 StaticMethodClass.PI === 3.14159;
    console.log("2.2 Static methods: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 2.3: Getter and setter
(function testGetterSetter() {
    class PropertyClass {
        constructor() { this._value = 0; }
        get value() { return this._value; }
        set value(v) { this._value = v; }
    }
    var instance = new PropertyClass();
    instance.value = 42;
    var result = instance.value === 42;
    console.log("2.3 Getter/setter: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 2.4: Static getter and setter
(function testStaticGetterSetter() {
    class ConfigClass {
        static _config = {};
        static get config() { return this._config; }
        static set config(v) { this._config = v; }
    }
    ConfigClass.config = { debug: true };
    var result = ConfigClass.config.debug === true;
    console.log("2.4 Static getter/setter: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 2.5: Computed method names
(function testComputedMethodNames() {
    var methodName = "dynamicMethod";
    class ComputedNameClass {
        [methodName]() { return "computed"; }
        ["add" + "Method"](a, b) { return a + b; }
    }
    var instance = new ComputedNameClass();
    var result = instance.dynamicMethod() === "computed" &&
                 instance.addMethod(1, 2) === 3;
    console.log("2.5 Computed method names: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// ============================================================
// SECTION 3: Inheritance (extends)
// ============================================================

// Test 3.1: Basic inheritance
(function testBasicInheritance() {
    class Animal {
        constructor(name) {
            this.name = name;
        }
        speak() {
            return this.name + " makes a sound";
        }
    }
    
    class Dog extends Animal {
        constructor(name, breed) {
            super(name);
            this.breed = breed;
        }
        speak() {
            return this.name + " barks";
        }
    }
    
    var dog = new Dog("Rex", "German Shepherd");
    var result = dog.name === "Rex" &&
                 dog.breed === "German Shepherd" &&
                 dog.speak() === "Rex barks" &&
                 dog instanceof Dog &&
                 dog instanceof Animal;
    console.log("3.1 Basic inheritance: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 3.2: super method call
(function testSuperMethodCall() {
    class Parent {
        greet() { return "Hello from Parent"; }
    }
    class Child extends Parent {
        greet() { return super.greet() + " and Child"; }
    }
    var child = new Child();
    var result = child.greet() === "Hello from Parent and Child";
    console.log("3.2 super method call: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 3.3: super property access
(function testSuperPropertyAccess() {
    class Base {
        constructor() {
            this.baseProp = "base value";
        }
    }
    class Derived extends Base {
        constructor() {
            super();
            this.derivedProp = "derived value";
        }
        getBaseProp() {
            return super.baseProp;  // undefined (super is prototype)
        }
    }
    var derived = new Derived();
    var result = derived.baseProp === "base value" &&
                 derived.derivedProp === "derived value";
    console.log("3.3 super property access: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 3.4: Inheritance chain
(function testInheritanceChain() {
    class A {
        method() { return "A"; }
    }
    class B extends A {
        method() { return super.method() + "B"; }
    }
    class C extends B {
        method() { return super.method() + "C"; }
    }
    var result = new A().method() === "A" &&
                 new B().method() === "AB" &&
                 new C().method() === "ABC";
    console.log("3.4 Inheritance chain: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 3.5: Default constructor in derived class
(function testDefaultConstructorDerived() {
    class Parent {
        constructor(x) {
            this.x = x;
        }
    }
    class Child extends Parent {}
    // Should have implicit constructor: constructor(...args) { super(...args); }
    var child = new Child(42);
    var result = child.x === 42 && child instanceof Child;
    console.log("3.5 Default constructor derived: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// ============================================================
// SECTION 4: Public Instance Fields
// ============================================================

// Test 4.1: Basic public field
(function testBasicPublicField() {
    class WithField {
        x = 10;
    }
    var instance = new WithField();
    var result = instance.x === 10;
    console.log("4.1 Basic public field: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 4.2: Field with initializer expression
(function testFieldInitializer() {
    class WithInitializer {
        x = 1;
        y = this.x + 1;
        z = (function() { return 3; })();
    }
    var instance = new WithInitializer();
    var result = instance.x === 1 && instance.y === 2 && instance.z === 3;
    console.log("4.2 Field initializer: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 4.3: Field initialized after super()
(function testFieldAfterSuper() {
    class Parent {
        parentValue = "parent";
    }
    class Child extends Parent {
        childValue = this.parentValue + "-child";
    }
    var child = new Child();
    var result = child.childValue === "parent-child";
    console.log("4.3 Field after super: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 4.4: Computed field names
(function testComputedFieldNames() {
    var fieldName = "dynamicField";
    class ComputedFieldClass {
        [fieldName] = "computed value";
        ["field" + "2"] = 42;
    }
    var instance = new ComputedFieldClass();
    var result = instance.dynamicField === "computed value" &&
                 instance.field2 === 42;
    console.log("4.4 Computed field names: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 4.5: Field without initializer
(function testFieldWithoutInitializer() {
    class EmptyFieldClass {
        x;
        y;
    }
    var instance = new EmptyFieldClass();
    var result = instance.hasOwnProperty("x") && 
                 instance.hasOwnProperty("y") &&
                 instance.x === undefined &&
                 instance.y === undefined;
    console.log("4.5 Field without initializer: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// ============================================================
// SECTION 5: Private Fields (ES2022)
// ============================================================

// Test 5.1: Basic private field
(function testBasicPrivateField() {
    class PrivateFieldClass {
        #secret = "hidden";
        getSecret() { return this.#secret; }
    }
    var instance = new PrivateFieldClass();
    var result = instance.getSecret() === "hidden";
    console.log("5.1 Basic private field: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 5.2: Private field assignment
(function testPrivateFieldAssignment() {
    class PrivateAssignClass {
        #value = 0;
        setValue(v) { this.#value = v; }
        getValue() { return this.#value; }
    }
    var instance = new PrivateAssignClass();
    instance.setValue(42);
    var result = instance.getValue() === 42;
    console.log("5.2 Private field assignment: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 5.3: Private field with initializer
(function testPrivateFieldInitializer() {
    class PrivateInitClass {
        #a = 1;
        #b = this.#a + 1;
        getA() { return this.#a; }
        getB() { return this.#b; }
    }
    var instance = new PrivateInitClass();
    var result = instance.getA() === 1 && instance.getB() === 2;
    console.log("5.3 Private field initializer: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 5.4: Private methods
(function testPrivateMethods() {
    class PrivateMethodClass {
        #privateMethod(a, b) {
            return a + b;
        }
        publicMethod(a, b) {
            return this.#privateMethod(a, b);
        }
    }
    var instance = new PrivateMethodClass();
    var result = instance.publicMethod(2, 3) === 5;
    console.log("5.4 Private methods: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 5.5: Private getter and setter
(function testPrivateGetterSetter() {
    class PrivateAccessorClass {
        #value = 0;
        get #internalValue() { return this.#value; }
        set #internalValue(v) { this.#value = v; }
        setValue(v) { this.#internalValue = v; }
        getValue() { return this.#internalValue; }
    }
    var instance = new PrivateAccessorClass();
    instance.setValue(100);
    var result = instance.getValue() === 100;
    console.log("5.5 Private getter/setter: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 5.6: Private static fields
(function testPrivateStaticFields() {
    class PrivateStaticClass {
        static #instanceCount = 0;
        static getCount() { return PrivateStaticClass.#instanceCount; }
        constructor() {
            PrivateStaticClass.#instanceCount++;
        }
    }
    new PrivateStaticClass();
    new PrivateStaticClass();
    new PrivateStaticClass();
    var result = PrivateStaticClass.getCount() === 3;
    console.log("5.6 Private static fields: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 5.7: Private static methods
(function testPrivateStaticMethods() {
    class PrivateStaticMethodClass {
        static #validate(x) {
            return typeof x === "number";
        }
        static process(x) {
            if (!PrivateStaticMethodClass.#validate(x)) {
                throw new Error("Invalid input");
            }
            return x * 2;
        }
    }
    var result = PrivateStaticMethodClass.process(21) === 42;
    console.log("5.7 Private static methods: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 5.8: Private field brand check (same class access)
(function testPrivateFieldBrandCheck() {
    class ClassA {
        #field = "A";
        accessOther(other) {
            try {
                return other.#field;  // Should fail if other is not ClassA
            } catch(e) {
                return "error";
            }
        }
    }
    class ClassB {
        #field = "B";
    }
    var a = new ClassA();
    var b = new ClassB();
    var result = a.accessOther(a) === "A" &&
                 a.accessOther(b) === "error";
    console.log("5.8 Private field brand check: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// ============================================================
// SECTION 6: Static Features
// ============================================================

// Test 6.1: Static public fields
(function testStaticPublicFields() {
    class StaticFieldClass {
        static count = 0;
        static name = "StaticClass";
    }
    var result = StaticFieldClass.count === 0 &&
                 StaticFieldClass.name === "StaticClass";
    console.log("6.1 Static public fields: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 6.2: Static field initialization order
(function testStaticFieldInitOrder() {
    var initOrder = [];
    class InitOrderClass {
        static a = initOrder.push("a");
        static b = initOrder.push("b");
        static c = initOrder.push("c");
    }
    var result = initOrder[0] === "a" &&
                 initOrder[1] === "b" &&
                 initOrder[2] === "c";
    console.log("6.2 Static field init order: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 6.3: Static block (ES2022)
(function testStaticBlock() {
    var blockExecuted = false;
    var staticFieldAtBlockTime;
    
    class StaticBlockClass {
        static value = 10;
        static {
            blockExecuted = true;
            staticFieldAtBlockTime = StaticBlockClass.value;
            StaticBlockClass.value = 20;
        }
    }
    
    var result = blockExecuted &&
                 staticFieldAtBlockTime === 10 &&
                 StaticBlockClass.value === 20;
    console.log("6.3 Static block: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 6.4: Multiple static blocks
(function testMultipleStaticBlocks() {
    var order = [];
    class MultiBlockClass {
        static { order.push(1); }
        static a = order.push(2);
        static { order.push(3); }
        static b = order.push(4);
        static { order.push(5); }
    }
    var result = order[0] === 1 && order[1] === 2 &&
                 order[2] === 3 && order[3] === 4 && order[4] === 5;
    console.log("6.4 Multiple static blocks: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 6.5: Static block with private access
(function testStaticBlockPrivateAccess() {
    class StaticBlockPrivateClass {
        static #privateStatic = "secret";
        static revealed;
        static {
            StaticBlockPrivateClass.revealed = StaticBlockPrivateClass.#privateStatic;
        }
    }
    var result = StaticBlockPrivateClass.revealed === "secret";
    console.log("6.5 Static block private access: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// ============================================================
// SECTION 7: Constructor Behavior
// ============================================================

// Test 7.1: Class cannot be called without new
(function testClassRequiresNew() {
    class NoCallClass {
        constructor() {}
    }
    var errorCaught = false;
    try {
        NoCallClass();  // Should throw
    } catch(e) {
        errorCaught = true;
    }
    var result = errorCaught;
    console.log("7.1 Class requires new: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 7.2: new.target in constructor
(function testNewTarget() {
    var capturedNewTarget;
    class NewTargetClass {
        constructor() {
            capturedNewTarget = new.target;
        }
    }
    new NewTargetClass();
    var result = capturedNewTarget === NewTargetClass;
    console.log("7.2 new.target: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 7.3: new.target with inheritance
(function testNewTargetInheritance() {
    var capturedNewTarget;
    class Parent {
        constructor() {
            capturedNewTarget = new.target;
        }
    }
    class Child extends Parent {}
    
    new Child();
    var result = capturedNewTarget === Child;
    console.log("7.3 new.target inheritance: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 7.4: super() must be called in derived constructor
(function testSuperRequired() {
    class Parent {}
    class Child extends Parent {
        constructor() {
            // Missing super() call
        }
    }
    var errorCaught = false;
    try {
        new Child();
    } catch(e) {
        errorCaught = true;
    }
    var result = errorCaught;
    console.log("7.4 super() required: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 7.5: this before super() error
(function testThisBeforeSuper() {
    class Parent {}
    class Child extends Parent {
        constructor() {
            this.x = 1;  // Error: this before super
            super();
        }
    }
    var errorCaught = false;
    try {
        new Child();
    } catch(e) {
        errorCaught = true;
    }
    var result = errorCaught;
    console.log("7.5 this before super error: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 7.6: Constructor return value
(function testConstructorReturn() {
    class ReturnObjectClass {
        constructor() {
            return { custom: true };
        }
    }
    class ReturnValueClass {
        constructor() {
            return 42;  // Primitive is ignored
        }
    }
    var result1 = new ReturnObjectClass().custom === true;
    var result2 = new ReturnValueClass() instanceof ReturnValueClass;
    var result = result1 && result2;
    console.log("7.6 Constructor return value: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// ============================================================
// SECTION 8: Edge Cases and Error Handling
// ============================================================

// Test 8.1: Prototype chain integrity
(function testPrototypeChain() {
    class A {}
    class B extends A {}
    class C extends B {}
    
    var c = new C();
    var result = Object.getPrototypeOf(c) === C.prototype &&
                 Object.getPrototypeOf(C.prototype) === B.prototype &&
                 Object.getPrototypeOf(B.prototype) === A.prototype &&
                 Object.getPrototypeOf(A.prototype) === Object.prototype;
    console.log("8.1 Prototype chain: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 8.2: Class name in expressions
(function testClassNameInExpressions() {
    var classes = [];
    for (var i = 0; i < 3; i++) {
        classes.push(class {
            getIndex() { return i; }
        });
    }
    var result = new classes[0]().getIndex() === 2;  // Closure captures final i
    console.log("8.2 Class name in expressions: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 8.3: Extending null
(function testExtendingNull() {
    var errorCaught = false;
    try {
        class ExtendsNull extends null {
            constructor() {
                super();  // Should error: super is null
            }
        }
        new ExtendsNull();
    } catch(e) {
        errorCaught = true;
    }
    var result = errorCaught;
    console.log("8.3 Extending null: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 8.4: Method enumerability
(function testMethodEnumerability() {
    class EnumClass {
        method() {}
        get prop() { return 1; }
    }
    var instance = new EnumClass();
    var keys = [];
    for (var k in instance) {
        keys.push(k);
    }
    // Methods should not be enumerable
    var result = keys.length === 0;
    console.log("8.4 Method enumerability: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 8.5: Static method enumerability
(function testStaticMethodEnumerability() {
    class StaticEnumClass {
        static staticMethod() {}
    }
    var keys = Object.keys(StaticEnumClass);
    // Static methods should not be enumerable
    var result = keys.indexOf("staticMethod") === -1;
    console.log("8.5 Static method enumerability: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 8.6: Duplicate constructor error
(function testDuplicateConstructor() {
    // This should be a parse error - cannot be tested at runtime
    // Parser should reject: class A { constructor() {} constructor() {} }
    console.log("8.6 Duplicate constructor: SKIP (parse-time check)");
    return true;
})();

// Test 8.7: Super in non-derived class
(function testSuperInNonDerived() {
    class NoExtendClass {
        constructor() {
            super();  // Should error
        }
    }
    var errorCaught = false;
    try {
        new NoExtendClass();
    } catch(e) {
        errorCaught = true;
    }
    var result = errorCaught;
    console.log("8.7 Super in non-derived class: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// ============================================================
// SECTION 9: Integration Tests
// ============================================================

// Test 9.1: Complex class with all features
(function testComplexClass() {
    class Base {
        static baseCount = 0;
        #basePrivate = "base";
        
        constructor(name) {
            this.name = name;
            Base.baseCount++;
        }
        
        getBasePrivate() { return this.#basePrivate; }
    }
    
    class Derived extends Base {
        static #derivedStatic = 0;
        #derivedPrivate;
        
        static {
            Derived.#derivedStatic = 10;
        }
        
        constructor(name, extra) {
            super(name);
            this.extra = extra;
            this.#derivedPrivate = extra + "-" + this.getBasePrivate();
        }
        
        getDerivedPrivate() { return this.#derivedPrivate; }
        
        static getStatic() { return Derived.#derivedStatic; }
    }
    
    var instance = new Derived("test", "extra");
    var result = instance.name === "test" &&
                 instance.extra === "extra" &&
                 instance.getBasePrivate() === "base" &&
                 instance.getDerivedPrivate() === "extra-base" &&
                 Base.baseCount === 1 &&
                 Derived.getStatic() === 10;
    console.log("9.1 Complex class: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 9.2: Class with Symbol.species
(function testSymbolSpecies() {
    class MyArray extends Array {
        static get [Symbol.species]() { return Array; }
    }
    
    var a = new MyArray(1, 2, 3);
    var mapped = a.map(x => x * 2);
    var result = mapped instanceof Array && !(mapped instanceof MyArray);
    console.log("9.2 Symbol.species: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 9.3: Class with Symbol.iterator
(function testSymbolIterator() {
    class Range {
        constructor(start, end) {
            this.start = start;
            this.end = end;
        }
        
        [Symbol.iterator]() {
            var current = this.start;
            var end = this.end;
            return {
                next() {
                    if (current <= end) {
                        return { value: current++, done: false };
                    }
                    return { done: true };
                }
            };
        }
    }
    
    var range = new Range(1, 3);
    var values = [];
    for (var v of range) {
        values.push(v);
    }
    var result = values[0] === 1 && values[1] === 2 && values[2] === 3;
    console.log("9.3 Symbol.iterator: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// Test 9.4: Mixin pattern
(function testMixinPattern() {
    var Serializable = (superclass) => class extends superclass {
        serialize() {
            return JSON.stringify(this);
        }
    };
    
    var Loggable = (superclass) => class extends superclass {
        log() {
            console.log(this.serialize());
        }
    };
    
    class Entity {
        constructor(id) {
            this.id = id;
        }
    }
    
    class User extends Loggable(Serializable(Entity)) {
        constructor(id, name) {
            super(id);
            this.name = name;
        }
    }
    
    var user = new User(1, "Test");
    var serialized = user.serialize();
    var result = serialized.indexOf('"id":1') >= 0 &&
                 serialized.indexOf('"name":"Test"') >= 0;
    console.log("9.4 Mixin pattern: " + (result ? "PASS" : "FAIL"));
    return result;
})();

// ============================================================
// Summary
// ============================================================
(function summary() {
    console.log("\n========================================");
    console.log("ES2022 Class Feature Test Summary");
    console.log("========================================");
    console.log("Run all tests above to verify ES2022 class support.");
    console.log("Each test should output 'PASS' for successful execution.");
    console.log("========================================\n");
})();
```

### 7.5 Java 单元测试 (ClassTest.java)

```java
/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests.es2022;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.tests.Evaluator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ES2022 class syntax support.
 */
public class ClassTest {

    // ===== Basic Class Declaration Tests =====

    @Test
    public void testEmptyClassDeclaration() {
        Object result = Evaluator.eval("class A {} typeof A");
        assertEquals("function", result.toString());
    }

    @Test
    public void testClassExpression() {
        Object result = Evaluator.eval("var A = class {}; typeof A");
        assertEquals("function", result.toString());
    }

    @Test
    public void testNamedClassExpression() {
        Object result = Evaluator.eval("var A = class B {}; A.name");
        assertEquals("B", result.toString());
    }

    @Test
    public void testClassWithConstructor() {
        Object result = Evaluator.eval(
            "class A {" +
            "  constructor(x) { this.x = x; }" +
            "}" +
            "var a = new A(42); a.x"
        );
        assertEquals(42.0, ((Number)result).doubleValue(), 0.001);
    }

    @Test
    public void testClassWithMethod() {
        Object result = Evaluator.eval(
            "class A {" +
            "  method() { return 42; }" +
            "}" +
            "new A().method()"
        );
        assertEquals(42.0, ((Number)result).doubleValue(), 0.001);
    }

    @Test
    public void testClassWithStaticMethod() {
        Object result = Evaluator.eval(
            "class A {" +
            "  static staticMethod() { return 'static'; }" +
            "}" +
            "A.staticMethod()"
        );
        assertEquals("static", result.toString());
    }

    // ===== Getter and Setter Tests =====

    @Test
    public void testGetterSetter() {
        Object result = Evaluator.eval(
            "class A {" +
            "  get value() { return this._value; }" +
            "  set value(v) { this._value = v; }" +
            "}" +
            "var a = new A(); a.value = 10; a.value"
        );
        assertEquals(10.0, ((Number)result).doubleValue(), 0.001);
    }

    @Test
    public void testStaticGetterSetter() {
        Object result = Evaluator.eval(
            "class A {" +
            "  static get value() { return A._value; }" +
            "  static set value(v) { A._value = v; }" +
            "}" +
            "A.value = 20; A.value"
        );
        assertEquals(20.0, ((Number)result).doubleValue(), 0.001);
    }

    // ===== Inheritance Tests =====

    @Test
    public void testBasicInheritance() {
        Object result = Evaluator.eval(
            "class Parent { method() { return 'parent'; } }" +
            "class Child extends Parent {}" +
            "new Child().method()"
        );
        assertEquals("parent", result.toString());
    }

    @Test
    public void testSuperMethodCall() {
        Object result = Evaluator.eval(
            "class Parent { method() { return 'parent'; } }" +
            "class Child extends Parent { method() { return super.method() + '-child'; } }" +
            "new Child().method()"
        );
        assertEquals("parent-child", result.toString());
    }

    @Test
    public void testSuperConstructorCall() {
        Object result = Evaluator.eval(
            "class Parent { constructor(x) { this.x = x; } }" +
            "class Child extends Parent { constructor(x, y) { super(x); this.y = y; } }" +
            "var c = new Child(1, 2); c.x + c.y"
        );
        assertEquals(3.0, ((Number)result).doubleValue(), 0.001);
    }

    @Test
    public void testInstanceofWithInheritance() {
        Object result = Evaluator.eval(
            "class Parent {}" +
            "class Child extends Parent {}" +
            "var c = new Child();" +
            "c instanceof Child && c instanceof Parent"
        );
        assertEquals(true, result);
    }

    // ===== Public Field Tests =====

    @Test
    public void testPublicInstanceField() {
        Object result = Evaluator.eval(
            "class A { x = 10; }" +
            "new A().x"
        );
        assertEquals(10.0, ((Number)result).doubleValue(), 0.001);
    }

    @Test
    public void testPublicFieldWithInitializer() {
        Object result = Evaluator.eval(
            "class A { x = 1; y = this.x + 1; }" +
            "var a = new A(); a.y"
        );
        assertEquals(2.0, ((Number)result).doubleValue(), 0.001);
    }

    @Test
    public void testStaticField() {
        Object result = Evaluator.eval(
            "class A { static count = 0; }" +
            "A.count"
        );
        assertEquals(0.0, ((Number)result).doubleValue(), 0.001);
    }

    // ===== Private Field Tests =====

    @Test
    public void testPrivateFieldBasic() {
        Object result = Evaluator.eval(
            "class A {" +
            "  #secret = 42;" +
            "  getSecret() { return this.#secret; }" +
            "}" +
            "new A().getSecret()"
        );
        assertEquals(42.0, ((Number)result).doubleValue(), 0.001);
    }

    @Test
    public void testPrivateFieldAssignment() {
        Object result = Evaluator.eval(
            "class A {" +
            "  #value = 0;" +
            "  setValue(v) { this.#value = v; }" +
            "  getValue() { return this.#value; }" +
            "}" +
            "var a = new A(); a.setValue(100); a.getValue()"
        );
        assertEquals(100.0, ((Number)result).doubleValue(), 0.001);
    }

    @Test
    public void testPrivateMethod() {
        Object result = Evaluator.eval(
            "class A {" +
            "  #privateMethod(a, b) { return a + b; }" +
            "  publicMethod(a, b) { return this.#privateMethod(a, b); }" +
            "}" +
            "new A().publicMethod(2, 3)"
        );
        assertEquals(5.0, ((Number)result).doubleValue(), 0.001);
    }

    @Test
    public void testPrivateStaticField() {
        Object result = Evaluator.eval(
            "class A {" +
            "  static #count = 0;" +
            "  static increment() { return ++A.#count; }" +
            "}" +
            "A.increment(); A.increment()"
        );
        assertEquals(2.0, ((Number)result).doubleValue(), 0.001);
    }

    // ===== Static Block Tests =====

    @Test
    public void testStaticBlock() {
        Object result = Evaluator.eval(
            "var executed = false;" +
            "class A {" +
            "  static { executed = true; }" +
            "}" +
            "executed"
        );
        assertEquals(true, result);
    }

    @Test
    public void testStaticBlockWithPrivateAccess() {
        Object result = Evaluator.eval(
            "class A {" +
            "  static #value = 10;" +
            "  static revealed;" +
            "  static { A.revealed = A.#value; }" +
            "}" +
            "A.revealed"
        );
        assertEquals(10.0, ((Number)result).doubleValue(), 0.001);
    }

    // ===== Error Cases =====

    @Test
    public void testClassCannotBeCalledWithoutNew() {
        assertThrows(EvaluatorException.class, () -> {
            Evaluator.eval("class A {} A()");
        });
    }

    @Test
    public void testSuperRequiredInDerivedClass() {
        assertThrows(EvaluatorException.class, () -> {
            Evaluator.eval(
                "class Parent {}" +
                "class Child extends Parent { constructor() {} }" +
                "new Child()"
            );
        });
    }

    @Test
    public void testThisBeforeSuper() {
        assertThrows(EvaluatorException.class, () -> {
            Evaluator.eval(
                "class Parent {}" +
                "class Child extends Parent { constructor() { this.x = 1; super(); } }" +
                "new Child()"
            );
        });
    }

    @Test
    public void testSuperCallInNonDerivedClass() {
        assertThrows(EvaluatorException.class, () -> {
            Evaluator.eval(
                "class A { constructor() { super(); } }" +
                "new A()"
            );
        });
    }

    // ===== new.target Tests =====

    @Test
    public void testNewTarget() {
        Object result = Evaluator.eval(
            "var target;" +
            "class A { constructor() { target = new.target; } }" +
            "new A();" +
            "target === A"
        );
        assertEquals(true, result);
    }

    // ===== Prototype Chain Tests =====

    @Test
    public void testPrototypeChain() {
        Object result = Evaluator.eval(
            "class A {}" +
            "class B extends A {}" +
            "var b = new B();" +
            "Object.getPrototypeOf(b) === B.prototype && " +
            "Object.getPrototypeOf(B.prototype) === A.prototype"
        );
        assertEquals(true, result);
    }
}
```

## 八、开发里程碑

### 8.1 里程碑概览

| 里程碑 | 内容 | 预估时间 | 依赖 | 开发模式 |
|--------|------|----------|------|----------|
| **M0** | 基础设施（Token 定义 + TDZ 框架原型验证） | 2 天 | 无 | 顺序 |
| **M1** | Token + AST 节点 (ClassNode, ClassElement) | 3 天 | M0 | 顺序 |
| **M2** | TokenStream 词法分析器修改（# 私有字段支持） | 1 天 | M1 | 顺序 |
| **M3** | Parser 解析逻辑 (classDefinition, parseClassBody, parseClassElement) | 5 天 | M2 | 顺序 |
| **M4** | IRFactory 转换逻辑 (transformClass, 字段注入) | 4 天 | M3 | 顺序 |
| **M5** | 运行时支持 (NativeClass, ScriptRuntime.createClass) | 4 天 | M4 | **并行** |
| **M8** | 优化编译器支持 (Codegen, CodeGenerator, 自动降级) | 3 天 | M4 | **并行** |
| **M6** | 单元测试 + test262 class 相关用例验证（双模式） | 5 天 | M5, M8 | 顺序 |
| **M7** | Bug 修复 + 边界情况处理 | 3 天 | M6 | 顺序 |
| **总计** | | **31 天** | | |

#### 8.1.1 里程碑说明（v1.12 更新）

**M0（基础设施）**：在正式开发前完成 Token 定义验证和 TDZ 框架原型。
- 确认 Token 值分配不冲突
- 实现 `UniqueTag.TDZ_VALUE` 原型
- 验证 let/const TDZ 行为

**M5 + M8（并行开发）**：运行时支持与优化编译器并行开发。
- **M5（运行时）**：NativeClass、UninitializedObject、ScriptRuntime.createClass
- **M8（优化器）**：Codegen JVM 字节码生成、CodeGenerator 指令、自动降级机制
- 两个里程碑共享测试用例，在同步点（SP2-SP4）协调进度
- 并行开发可节省约 3 天时间

**M6（双模式验证）**：测试需覆盖解释模式和优化模式。
- 解释模式测试：`setOptimizationLevel(-1)`
- 优化模式测试：`setOptimizationLevel(9)`
- 双模式对比验证：确保两种模式结果一致

**关键同步点**：
- **SP1**：M4 完成，M5/M8 开始
- **SP2**：M5 完成 NativeClass，M8 可生成类创建字节码
- **SP3**：M5 完成 getPrivateField，M8 可生成私有字段访问字节码
- **SP4**：M5 + M8 都完成，进入 M6

### 8.2 Git 提交任务列表

#### M0: 基础设施（2 天）

| 提交 | 修改文件 | 主要内容 | 耗时 |
|------|----------|----------|------|
| `feat(class): verify Token allocation` | `Token.java` | 确认 LAST_TOKEN 值；验证新 Token 分配无冲突 | 0.5 天 |
| `feat(class): add TDZ UniqueTag` | `UniqueTag.java` | 添加 TDZ_VALUE 标记；验证 let/const TDZ 行为 | 0.5 天 |
| `feat(class): implement getVarWithTDZCheck prototype` | `ScriptRuntime.java` | TDZ 检查方法原型；单元测试 | 1 天 |

#### M1: Token + AST 节点（3 天）

| 提交 | 修改文件 | 主要内容 | 耗时 |
|------|----------|----------|------|
| `feat(class): add Token types for class syntax` | `Token.java` | 添加 CLASS, EXTENDS, CLASS_ELEMENT, PRIVATE_FIELD, NEW_CLASS, STATIC_BLOCK；更新 typeToName(), keywordToName() | 0.5 天 |
| `feat(class): add ClassNode AST node` | `ClassNode.java`, `AstNode.java` | 实现 ClassNode 类（继承 AstNode）；AstNode.visit() 添加 ClassNode 分支 | 1 天 |
| `feat(class): add ClassElement AST node` | `ClassElement.java` | 实现 ClassElement 类（方法、字段、getter/setter、静态块） | 1 天 |
| `docs(class): add error messages for class syntax` | `Messages.properties` | 添加 40+ 条 class 相关错误消息 | 0.5 天 |


#### M2: TokenStream 词法分析器（1 天）

| 提交 | 修改文件 | 主要内容 | 耗时 |
|------|----------|----------|------|
| `feat(class): add private field lexing support` | `TokenStream.java` | 支持 `#` 作为标识符起始字符；添加私有标识符 token 化逻辑 | 1 天 |

#### M3: Parser 解析逻辑（5 天）

| 提交 | 修改文件 | 主要内容 | 耗时 |
|------|----------|----------|------|
| `feat(class): implement class keyword parsing` | `Parser.java` | 修改 statement() 和 primaryExpr() 识别 class 关键字 | 0.5 天 |
| `feat(class): implement classDeclaration and classExpression` | `Parser.java` | 实现 parseClassDeclaration() 和 parseClassExpression() | 1 天 |
| `feat(class): implement class body parsing` | `Parser.java` | 实现 parseClassBody()；处理类体严格模式 | 1 天 |
| `feat(class): implement class element parsing` | `Parser.java` | 实现 parseClassElement()；处理方法、字段、静态块、getter/setter | 1.5 天 |
| `feat(class): add constructor validation and default constructor` | `Parser.java` | 构造器验证；自动生成默认构造器 | 1 天 |

#### M4: IRFactory 转换逻辑（4 天）

| 提交 | 修改文件 | 主要内容 | 耗时 |
|------|----------|----------|------|
| `feat(class): implement transformClass skeleton` | `IRFactory.java`, `Node.java` | transformClass() 框架；添加 CLASS_PROP, PRIVATE_FIELDS_PROP | 1 天 |
| `feat(class): implement method and field transformation` | `IRFactory.java` | 类方法和字段的 IR 转换 | 1 天 |
| `feat(class): implement field injection into constructor` | `IRFactory.java` | 将字段初始化注入构造器函数体 | 1 天 |
| `feat(class): implement static block and super() handling` | `IRFactory.java` | 静态块执行；super() 调用检测和转换 | 1 天 |

#### M5: 运行时支持（4 天）

| 提交 | 修改文件 | 主要内容 | 耗时 |
|------|----------|----------|------|
| `feat(class): implement NativeClass runtime object` | `NativeClass.java` | 继承 BaseFunction；实现 call() 和 construct() | 1.5 天 |
| `feat(class): implement UninitializedObject for derived constructors` | `UninitializedObject.java` | 派生类构造器 this 未初始化状态管理 | 1 天 |
| `feat(class): implement createClass helper and prototype chain` | `ScriptRuntime.java` | createClass() 方法；原型链设置；home object 绑定 | 1 天 |
| `feat(class): implement private field storage` | `NativeClass.java`, `ScriptRuntime.java` | WeakHashMap 存储私有字段；访问控制检查 | 0.5 天 |

#### M6: 测试（4 天）

| 提交 | 修改文件 | 主要内容 | 耗时 |
|------|----------|----------|------|
| `test(class): add basic class unit tests` | `ClassTest.java` | 基础类声明、构造器、方法测试 | 1 天 |
| `test(class): add inheritance tests` | `class.js`, `ClassScriptTest.java` | extends, super, 原型链测试 | 1 天 |
| `test(class): add ES2022 feature tests` | `class.js` | 公共/私有字段、静态块测试 | 1 天 |
| `test(class): enable test262 class tests` | `test262.properties` | 移除 class 测试排除标记；验证通过率 | 1 天 |

#### M7: Bug 修复（3 天）

| 提交 | 修改文件 | 主要内容 | 耗时 |
|------|----------|----------|------|
| `fix(class): fix edge cases and spec compliance` | 多文件 | 边界情况修复；规范符合性调整 | 2 天 |
| `refactor(class): code cleanup and optimization` | 多文件 | 代码清理；性能优化 | 1 天 |

## 九、风险与注意事项

### 9.1 技术风险

| 风险 | 等级 | 影响 | 缓解措施 |
|------|------|------|----------|
| super() 调用的正确性 | **高** | 派生类构造器语义复杂，super() 必须在 this 访问前调用 | 实现 UninitializedObject 追踪状态；参考 V8/SpiderMonkey 实现 |
| 派生类构造器语义 | **高** | 默认构造器需自动调用 super(...args)；this 初始化顺序严格 | Parser 生成默认构造器时插入 super() 调用 |
| super 属性访问 this 绑定 | **高** | super.method() 中 this 必须正确绑定到子类实例 | 使用现有 SUPER_PROPERTY_ACCESS 机制；设置 home object |
| **优化模式字节码生成** | **高** | Codegen 需要正确生成 JVM 字节码，与解释模式语义一致 | 并行开发；共享测试用例；双模式验证测试 |
| **双模式执行结果不一致** | **高** | 解释模式和优化模式执行结果可能不同 | DualModeVerificationTest 自动化验证；test262 双模式运行 |
| **TDZ 未实现** | **高** | 类声明提升行为不符合 ES 规范 | 新增 getVarWithTDZCheck() 方法；Parser 标记 TDZ 位置 |
| 私有字段访问控制 | **中** | 只能在类内部访问；跨实例访问需要权限检查 | ConcurrentHashMap 存储；在 AST/IRFactory 双层检查访问权限 |
| **私有字段线程安全** | **中** | WeakHashMap 非线程安全，多线程环境数据不一致 | 使用 ConcurrentHashMap 替代 WeakHashMap |
| **序列化 brand 丢失** | **中** | 反序列化后 classBrand 变为新对象，私有字段访问失败 | 使用 UUID 字符串作为 classBrand |
| **并行开发同步点** | **中** | M5 运行时和 M8 优化器需要协调进度 | 明确同步点 SP1-SP4；共享测试用例早期发现问题 |
| **自动降级策略** | **中** | 降级可能影响性能；降级检测逻辑可能遗漏场景 | 完善检测逻辑；提供用户控制选项 |
| 优化器兼容性 | **中** | 字段注入到构造器可能影响优化器假设 | 字段注入在 IR 转换阶段完成，优化器处理后端；使用现有 body.addChildToFront() 模式 |
| 原型链设置 | **中** | ES6 类继承有两层原型链：实例原型链和构造器原型链 | NativeClass.setPrototype() 设置构造器原型链；prototype.setPrototype() 设置实例原型链 |
| 静态块执行时机 | **低** | 静态块必须在类定义时立即执行 | IRFactory 在类定义处生成静态块执行代码 |
| 与现有 super 支持冲突 | **低** | Rhino 已有 super 属性访问支持，需确保不冲突 | 复用现有 Token.SUPER、SUPER_PROPERTY_ACCESS 机制 |
| test262 测试用例量大 | **低** | class 相关测试有 8000+ 个用例 | 分阶段启用；优先通过核心语义测试 |

### 9.1.1 风险等级说明（v1.12 更新）

**高等级风险**：
- 必须在开发前充分评估，否则可能导致重大返工
- 需要专项测试用例覆盖
- **新增**：优化模式相关风险需要双模式验证

**中等级风险**：
- 影响特定场景或边界情况
- 有现成解决方案可参考
- **新增**：并行开发同步风险需明确同步点

**低等级风险**：
- 影响范围小
- 现有代码已提供解决方案

### 9.2 已发现的技术细节

#### 9.2.1 现有 super 支持（无需重复实现）

Rhino 已完整支持 ES6 super 关键字：

| 层级 | 文件 | 功能 |
|------|------|------|
| Token | `Token.java:121` | `SUPER = 121` |
| Token | `Token.java:76-83` | `GETPROP_SUPER`, `SETPROP_SUPER`, `GETELEM_SUPER`, `SETELEM_SUPER` |
| Node | `Node.java:70` | `SUPER_PROPERTY_ACCESS = 31` |
| IRFactory | `IRFactory.java:2272-2274` | 标记 super 属性访问 |
| CodeGenerator | `CodeGenerator.java:678,772,817` | 生成 super 相关字节码 |
| Interpreter | `Interpreter.java:1501,2943,3036` | `DoSuper`, `DoGetPropSuper`, `DoGetElemSuper` |
| ScriptRuntime | `ScriptRuntime.java:1885,1970` | `getSuperElem()`, `getSuperProp()` |

**仍需实现：**
- `super()` 构造器调用
- home object 绑定
- 派生类构造器约束

#### 9.2.2 函数体操作安全模式

IRFactory 已有类似机制可复用：

```java
// 默认参数注入 (IRFactory.java L682-703)
body.addChildToFront(paramInit);

// 解构参数注入 (IRFactory.java L718-720)
body.addChildToFront(new Node(Token.EXPR_VOID, destructuring));
```

字段注入可复用相同模式，不会影响优化器（注入发生在 IR 转换阶段）。

#### 9.2.3 私有字段存储方案

**推荐方案：NativeClass 内部 WeakHashMap**

```java
public class NativeClass extends BaseFunction {
    // 私有字段存储：instance -> (fieldName -> value)
    private transient WeakHashMap<Object, Map<String, Object>> privateFieldStorage;
    
    public Object getPrivateField(Object instance, String fieldName) {
        // 检查访问权限（在编译时已验证）
        Map<String, Object> fields = privateFieldStorage.get(instance);
        return fields != null ? fields.get(fieldName) : null;
    }
}
```

**优势：**
- 完全隔离，外部无法访问
- GC 友好，实例回收时自动清理
- 复用 NativeWeakMap 的实现模式

### 9.3 注意事项

1. **派生类构造器**
   - 必须在访问 `this` 之前调用 `super()`
   - 默认构造器会自动调用 `super(...args)`
   - 使用 UninitializedObject 追踪 this 初始化状态

2. **super 属性访问**
   - `super.method()` 需要正确绑定 `this`
   - 静态方法中 `super` 指向父类而非父类原型
   - 使用 home object 机制

3. **私有字段**
   - 只能在类内部访问
   - 不参与原型继承
   - 跨实例访问需权限检查（同类实例可互访）

4. **静态块**
   - 在类定义时立即执行
   - 可以访问私有静态字段
   - 使用块作用域

5. **原型链**
   - `class A extends B {}` 创建两层原型链：
     - `a.__proto__ === A.prototype`
     - `A.prototype.__proto__ === B.prototype`
     - `A.__proto__ === B`（构造器继承）

### 9.4 优化器兼容性验证清单

#### 9.4.1 编译器架构理解

Rhino 有两个后端编译器：

| 后端 | 文件 | 输出 | 用途 |
|------|------|------|------|
| **字节码编译器** | `CodeGenerator.java` | 字节码指令 | 解释执行 |
| **类编译器** | `Codegen.java` | JVM 字节码类 | 优化执行 |

**执行路径：**
```
AST → IRFactory → IR → CodeGenerator/Codegen → 字节码 → Interpreter/JVM
```

#### 9.4.2 新增节点类型验证清单

**检查项 1：CodeGenerator 支持新 Token**

```java
// CodeGenerator.java - 必须处理的 Token 类型
// 在 switch(node.getType()) 中添加 case 分支

case Token.CLASS:           // ✓ 需要添加
case Token.CLASS_ELEMENT:   // ✓ 需要添加
case Token.PRIVATE_FIELD:   // ✓ 需要添加
case Token.NEW_CLASS:       // ✓ 需要添加
case Token.STATIC_BLOCK:    // ✓ 需要添加
```

**验证方法：**
1. 搜索 `CodeGenerator.java` 中的 `switch` 语句
2. 确保所有新 Token 都有对应的 case 分支
3. 每个分支应生成正确的字节码指令

**检查项 2：Codegen (JVM 编译器) 支持**

```java
// Codegen.java - 必须处理的 Token 类型
// 在 generateStatementCode() 或 generateExpressionCode() 中添加

case Token.CLASS:
    // 生成创建类的字节码
    break;
```

**验证方法：**
1. 搜索 `Codegen.java` 中的 `visit()` 方法
2. 确保新节点类型能正确生成 JVM 字节码
3. 测试：`Context.setOptimizationLevel(1)` 编译执行

#### 9.4.3 IR 节点属性验证清单

**检查项 3：Node 属性处理**

```java
// Node.java 中新增的属性常量
FUNCTION_PROP = 1        // 现有
LOCAL_PROP = 2           // 现有
...
SUPER_PROPERTY_ACCESS = 31  // 现有

// 新增属性（需要在 LAST_PROP 之前）
CLASS_NAME_PROP = 34
CONSTRUCTOR_PROP = 35
DERIVED_CONSTRUCTOR_PROP = 36
HOME_OBJECT_PROP = 37
SUPER_CONSTRUCTOR_CALL = 38
PRIVATE_FIELD_NAME_PROP = 39
PROTOTYPE_METHODS_PROP = 40
STATIC_METHODS_PROP = 41

// 更新 LAST_PROP
LAST_PROP = STATIC_METHODS_PROP;
```

**验证方法：**
1. 确保 `LAST_PROP` 已更新
2. 检查 `Node.getProp()` 和 `Node.putProp()` 能正确存取新属性
3. 序列化/反序列化测试：编译后重新加载

#### 9.4.4 字段注入对优化器的影响

**检查项 4：函数体修改兼容性**

```java
// IRFactory.transformFunction() 现有模式
body.addChildToFront(node);  // ✓ 已用于默认参数和解构

// 字段注入使用相同模式
body.addChildToFront(fieldInitNode);  // ✅ 应该兼容
```

**潜在问题：**
| 问题 | 原因 | 解决方案 |
|------|------|----------|
| 变量作用域 | 字段初始化表达式可能引用外部变量 | 使用相同的闭包规则 |
| 优化假设失效 | 优化器可能假设函数体不变 | IR 转换在优化前完成 |
| 调试信息 | 注入代码的行号需要正确 | 使用类定义的行号 |

**验证方法：**
1. 在优化模式下运行测试：`Context.setOptimizationLevel(9)`
2. 比较解释模式和优化模式的执行结果
3. 检查调试时断点是否正确

#### 9.4.5 super() 调用优化验证

**检查项 5：super 字节码生成**

```java
// CodeGenerator.java 中现有的 super 处理
case Token.GETPROP_SUPER:
    // 已有实现
    break;
case Token.SETPROP_SUPER:
    // 已有实现
    break;

// 新增：super() 构造器调用
case Token.CALL:
    if (node.hasProp(SUPER_CONSTRUCTOR_CALL)) {
        // 生成 ScriptRuntime.superCall() 调用
    }
    break;
```

**验证方法：**
1. 创建继承测试用例
2. 分别在解释模式和优化模式下运行
3. 检查 super() 是否正确调用父类构造器

#### 9.4.6 私有字段访问优化验证

**检查项 6：私有字段访问性能**

```java
// 私有字段访问转换为运行时调用
this.#field  →  ScriptRuntime.getPrivateField(this, "#field", classToken)

// 优化考虑：
// 1. classToken 可以缓存
// 2. WeakHashMap 访问可能比普通属性慢
// 3. 考虑内联优化
```

**性能验证：**
```java
// 性能测试代码
class PrivatePerf {
    #x = 0;
    increment() { return ++this.#x; }
}

class PublicPerf {
    x = 0;
    increment() { return ++this.x; }
}

// 比较两种实现的性能
```

#### 9.4.7 完整验证流程

**步骤 1：单元测试验证**

```bash
# 运行所有 class 相关测试
./gradlew test --tests "org.mozilla.javascript.tests.es2022.ClassTest"
```

**步骤 2：解释模式验证**

```java
Context cx = Context.enter();
cx.setOptimizationLevel(-1);  // 解释模式
// 运行所有测试
```

**步骤 3：优化模式验证**

```java
Context cx = Context.enter();
cx.setOptimizationLevel(9);  // 最高优化级别
// 运行所有测试，确保结果一致
```

**步骤 4：混合模式测试**

```java
// 测试类定义在优化模式，实例方法在解释模式
// 或反过来，确保兼容性
```

#### 9.4.8 优化器兼容性检查表

| 检查项 | 文件 | 状态 | 说明 |
|--------|------|------|------|
| Token 定义 | `Token.java` | ⬜ 待验证 | 新增 CLASS/EXTENDS 等 Token |
| Token 名称映射 | `Token.java` | ⬜ 待验证 | `tokenToName()` 更新 |
| Node 属性 | `Node.java` | ⬜ 待验证 | 新增 CLASS_NAME_PROP 等 |
| CodeGenerator case | `CodeGenerator.java` | ⬜ 待验证 | 处理新 Token 类型 |
| Codegen case | `Codegen.java` | ⬜ 待验证 | 生成 JVM 字节码 |
| Interpreter case | `Interpreter.java` | ⬜ 待验证 | 解释执行新指令 |
| IRFactory transform | `IRFactory.java` | ⬜ 待验证 | transformClass() 实现 |
| 函数体修改 | `IRFactory.java` | ⬜ 待验证 | 字段注入不影响优化 |
| super() 调用 | `CodeGenerator.java` | ⬜ 待验证 | 正确生成调用代码 |
| 私有字段访问 | `CodeGenerator.java` | ⬜ 待验证 | 运行时调用生成 |
| 静态块执行 | `CodeGenerator.java` | ⬜ 待验证 | IIFE 正确生成 |
| 优化级别 0 | 测试 | ⬜ 待验证 | 最小优化模式测试 |
| 优化级别 9 | 测试 | ⬜ 待验证 | 最大优化模式测试 |
| 解释模式 | 测试 | ⬜ 待验证 | 无编译模式测试 |

#### 9.4.9 调试技巧

**启用调试输出：**

```java
// 启用 IR 转换日志
System.setProperty("rhino.debug.ir", "true");

// 启用字节码生成日志
System.setProperty("rhino.debug.codegen", "true");

// 查看生成的类
String script = "class A { x = 1; }";
Script compiled = cx.compileString(script, "test", 1, null);
// 在调试器中查看 compiled 对象结构
```

**常见问题排查：**

| 问题 | 可能原因 | 排查方法 |
|------|----------|----------|
| ClassCastException | 新节点类型未处理 | 检查 switch 是否覆盖所有 case |
| NullPointerException | 属性未正确设置 | 检查 putProp() 调用位置 |
| VerifyError | 字节码无效 | 使用 `-noverify` 启动调试 |
| 类找不到 | Codegen 生成错误 | 检查类名生成逻辑 |
| 优化结果不一致 | IR 转换问题 | 比较两种模式的 IR 输出 |

## 十、参考资料

- [ECMAScript 2022 Specification - Class Definitions](https://tc39.es/ecma262/#sec-class-definitions)
- [ECMAScript 2022 Specification - Super Keyword](https://tc39.es/ecma262/#sec-super-keyword)
- [V8 Class Implementation](https://v8.dev/blog/understanding-ecmascript-part-2)
- [SpiderMonkey Class Implementation](https://spidermonkey.dev/)
- [test262 Class Tests](https://github.com/tc39/test262/tree/main/test/language/statements/class)

## 十一、设计决策总结

### 11.1 继承选择：ClassNode 继承 AstNode

**决策**：`ClassNode` 直接继承 `AstNode`，而非 `Scope` 或 `ScriptNode`。

**理由**：
1. Class 不是执行单元，不像 Function 有执行上下文
2. 与 ObjectLiteral 结构类似（都是成员容器）
3. Scope 的符号表用于变量查找，类成员访问语义不同
4. 私有字段名在编译时确定，不需要运行时符号查找

**参考**：
- `ObjectLiteral` 继承 `AstNode`
- `FunctionNode` 继承 `ScriptNode`（因为函数是执行单元）

### 11.2 遗漏方法补充

通过对比 `FunctionNode` 和 `ObjectLiteral`，补充了以下方法：

| 方法 | 来源 | 说明 |
|------|------|------|
| `NO_ELEMENTS` 常量 | ObjectLiteral.NO_ELEMS | 空列表优化 |
| `hasSideEffects()` | 覆盖默认实现 | Class 总有副作用 |
| `getConstructor()` | FunctionNode 便捷方法 | 快速访问构造器 |
| `getMethods()` | 新增 | 按类型过滤元素 |
| `getStaticMethods()` | 新增 | 静态方法过滤 |
| `getInstanceMethods()` | 新增 | 实例方法过滤 |
| `getFields()` | 新增 | 字段过滤 |
| `getStaticFields()` | 新增 | 静态字段过滤 |
| `getInstanceFields()` | 新增 | 实例字段过滤 |
| `getStaticBlocks()` | 新增 | 静态块过滤 |
| `isDerived()` | 新增 | 是否派生类 |

### 11.3 ClassElement 增强

| 方法 | 说明 |
|------|------|
| `isGetter()` | 是否 getter 方法 |
| `isSetter()` | 是否 setter 方法 |
| `isGenerator()` | 是否生成器方法 |
| `isAsync()` | 是否异步方法 |
| `isComputed` | 计算属性键标记 |
| `getKeyString()` | 获取键名字符串 |

---

## 十二、变更历史

| 版本 | 日期 | 作者 | 变更内容 |
|------|------|------|----------|
| 1.0 | 2026-03-22 | iFlow CLI | 初始版本 |
| 1.1 | 2026-03-22 | iFlow CLI | 添加继承层次分析和选择理由 |
| 1.1 | 2026-03-22 | iFlow CLI | 补充遗漏方法：NO_ELEMENTS、hasSideEffects、便捷方法 |
| 1.1 | 2026-03-22 | iFlow CLI | 增强 ClassElement：isComputed、便捷判断方法 |
| 1.1 | 2026-03-22 | iFlow CLI | 添加设计决策总结章节 |
| 1.2 | 2026-03-23 | iFlow CLI | 修正 Token 分配错误（原计划 `CLASS=YIELD+2` 会冲突） |
| 1.2 | 2026-03-23 | iFlow CLI | 添加 Token 结构分析和兼容性检查 |
| 1.2 | 2026-03-23 | iFlow CLI | 补充 typeToName/keywordToName 方法更新说明 |
| 1.3 | 2026-03-23 | iFlow CLI | 添加 TokenStream 词法分析器修改方案（# 私有字段支持） |
| 1.3 | 2026-03-23 | iFlow CLI | 更新文件变更清单和里程碑（M2 词法分析器） |
| 1.3 | 2026-03-23 | iFlow CLI | 分析结论：# 字符目前不支持，需新增 ~40 行代码 |
| 1.4 | 2026-03-23 | iFlow CLI | 添加 Parser 详细分析章节 |
| 1.4 | 2026-03-23 | iFlow CLI | 确认 class/extends 关键字当前仅为保留字，可直接使用 |
| 1.4 | 2026-03-23 | iFlow CLI | 添加作用域管理逻辑复用方案 |
| 1.4 | 2026-03-23 | iFlow CLI | 添加严格模式处理（类体始终严格模式） |
| 1.4 | 2026-03-23 | iFlow CLI | 分析错误消息命名规范，新增完整错误消息列表 |
| 1.4 | 2026-03-23 | iFlow CLI | 添加 Parser 修改风险点分析 |
| 1.5 | 2026-03-23 | iFlow CLI | 添加 IRFactory 详细分析章节（5.6节） |
| 1.5 | 2026-03-23 | iFlow CLI | **重大发现**：Rhino 已完整支持 ES6 super 关键字 |
| 1.5 | 2026-03-23 | iFlow CLI | 分析函数体操作安全性（字段注入机制） |
| 1.5 | 2026-03-23 | iFlow CLI | 添加 IR 转换实现复杂度评估（中等偏低） |
| 1.5 | 2026-03-23 | iFlow CLI | 列出实现类继承的剩余工作（super() 调用、home object） |
| 1.6 | 2026-03-23 | iFlow CLI | 分析 BaseFunction，确定 NativeClass 继承方案 |
| 1.6 | 2026-03-23 | iFlow CLI | 完善 NativeClass 实现（含私有字段 WeakHashMap 存储） |
| 1.6 | 2026-03-23 | iFlow CLI | 添加 UninitializedObject 设计（派生类构造器 super() 检查） |
| 1.6 | 2026-03-23 | iFlow CLI | 分析原型链处理，确认 extends 原型链连接方案 |
| 1.6 | 2026-03-23 | iFlow CLI | 分析 NativeWeakMap，确定私有字段存储方案 |
| 1.6 | 2026-03-23 | iFlow CLI | 扩展错误消息列表（40+ 条消息） |
| 1.7 | 2026-03-23 | iFlow CLI | 分析测试目录结构，确定 ClassTest 文件路径 |
| 1.7 | 2026-03-23 | iFlow CLI | 分析测试基类（ScriptTestsBase, Evaluator） |
| 1.7 | 2026-03-23 | iFlow CLI | 分析 test262 集成配置（当前 class 测试被排除） |
| 1.7 | 2026-03-23 | iFlow CLI | 确认新增测试无需修改构建脚本 |
| 1.8 | 2026-03-23 | iFlow CLI | 修正文件变更清单（新增 UninitializedObject，更新代码量） |
| 1.8 | 2026-03-23 | iFlow CLI | 细化开发里程碑为 26 个 Git 提交任务 |
| 1.8 | 2026-03-23 | iFlow CLI | 扩展风险分析（9 项风险，含等级评估） |
| 1.8 | 2026-03-23 | iFlow CLI | 添加已发现技术细节（super 支持、函数体操作、私有字段存储） |
| 1.9 | 2026-03-23 | iFlow CLI | 添加 IRFactory.transformClass() 完整实现代码（5.6.7） |
| 1.9 | 2026-03-23 | iFlow CLI | 添加 super() 构造器调用检测与转换逻辑（5.6.8） |
| 1.9 | 2026-03-23 | iFlow CLI | 添加私有字段访问编译时检查代码（5.6.9） |
| 1.9 | 2026-03-23 | iFlow CLI | 添加静态块处理实现（5.6.10） |
| 1.9 | 2026-03-23 | iFlow CLI | 添加完整 ES2022 class 测试脚本（7.4，60+ 测试用例） |
| 1.9 | 2026-03-23 | iFlow CLI | 添加 Java 单元测试 ClassTest.java（7.5） |
| 1.9 | 2026-03-23 | iFlow CLI | 添加优化器兼容性验证清单（9.4，15 项检查） |
| 1.10 | 2026-03-23 | iFlow CLI | 添加 new.target 支持实现（5.7.6，含 Context 修改） |
| 1.10 | 2026-03-23 | iFlow CLI | 添加 super() 返回值处理（5.7.7，UninitializedObject 增强） |
| 1.10 | 2026-03-23 | iFlow CLI | 添加构造器 return 语义（5.7.8） |
| 1.10 | 2026-03-23 | iFlow CLI | 添加私有字段 brand check 实现（5.7.9，跨实例访问） |
| 1.10 | 2026-03-23 | iFlow CLI | 添加 Symbol.species 处理（5.7.10，继承内置类） |
| 1.10 | 2026-03-23 | iFlow CLI | 添加类字面量 this 绑定语义（5.7.11） |
| 1.10 | 2026-03-23 | iFlow CLI | 添加静态块控制流限制（5.7.12，return/break/continue 检查） |
| 1.10 | 2026-03-23 | iFlow CLI | 添加类声明提升行为（5.7.13，TDZ 检查） |
| 1.10 | 2026-03-23 | iFlow CLI | 添加类表达式名称绑定作用域（5.7.14） |
| 1.11 | 2026-03-23 | iFlow CLI | 新增字节码指令详细规范（5.1.7） |
| 1.11 | 2026-03-23 | iFlow CLI | 新增 TDZ 检查实现（5.5.6） |
| 1.11 | 2026-03-23 | iFlow CLI | 新增线程安全与序列化（5.7.10） |
| 1.11 | 2026-03-23 | iFlow CLI | 新增优化编译器支持（5.8） |
| 1.11 | 2026-03-23 | iFlow CLI | 新增 M0、M8 里程碑，调整工期为 29 天 |
| 1.11 | 2026-03-23 | iFlow CLI | 新增代码库核对与文件调整清单（十三） |
| 1.12 | 2026-03-23 | iFlow CLI | 更新策略：同时支持解释模式和优化模式（5.8.1） |
| 1.12 | 2026-03-23 | iFlow CLI | 新增同时支持两种模式的代码结构图（5.8.8） |
| 1.12 | 2026-03-23 | iFlow CLI | 新增并行开发流程图章节（5.9） |
| 1.12 | 2026-03-23 | iFlow CLI | 新增自动降级机制章节（5.10） |
| 1.12 | 2026-03-23 | iFlow CLI | 新增双模式验证方案章节（5.11） |
| 1.12 | 2026-03-23 | iFlow CLI | 更新里程碑：M5+M8 并行开发，调整工期为 31 天（8.1） |
| 1.12 | 2026-03-23 | iFlow CLI | 更新技术风险：添加优化模式、双模式验证、并行开发风险（9.1） |

## 十三、代码库核对与文件调整清单

### 13.1 执行摘要

- **计划版本**: v1.12
- **核对日期**: 2026-03-23
- **假设验证通过率**: 85%
- **主要风险**:
  1. 优化模式字节码生成（高）
  2. 双模式执行结果不一致（高）
  3. TDZ 机制未实现（高）
  3. 私有字段线程安全（中）

### 13.2 计划假设核对结果

| 模块 | 检查项 | 计划假设 | 实际发现 | 状态 | 备注 |
|------|--------|----------|----------|------|------|
| Token | LAST_TOKEN 值 | ≈164 | OBJECT_REST + 1 | ✅ | 确认新 Token 从 LAST_TOKEN 开始追加 |
| Token | OBJECT_REST | 存在 | L248 定义 | ✅ | QUESTION_DOT + 1 |
| Super | SUPER_PROPERTY_ACCESS | =31 | Node.java L70 | ✅ | 属性存在，可复用 |
| Super | getSuperProp 方法 | 存在 | ScriptRuntime.java L1970 | ✅ | 完整实现，可复用 |
| Super | setHomeObject | 存在 | BaseFunction.java L800 | ✅ | homeObject 机制完整 |
| TDZ | UniqueTag 结构 | 需新增 TDZ_VALUE | 仅有 NOT_FOUND/NULL_VALUE/DOUBLE_MARK | ⚠️ | 需新增 ID_TDZ_VALUE = 4 |
| Optimizer | Codegen 类支持 | 无 | 确认无 Token.CLASS 处理 | ✅ | 需新增 |
| Thread | NativeWeakMap 实现 | 参考 | 使用 WeakHashMap，非线程安全 | ⚠️ | 需改用 ConcurrentHashMap |
| Runtime | BaseFunction.construct | 可继承 | L542，支持重写 | ✅ | createObject() 可自定义 |

### 13.3 核对评估计划

#### 13.3.1 阶段划分

| 阶段 | 对应里程碑 | 重点验证文件 | 验证命令/方法 |
|------|------------|--------------|---------------|
| 基础设施 | M0 | Token.java, UniqueTag.java, Node.java | `grep "LAST_TOKEN"` `grep "class UniqueTag"` |
| 解析层 | M1-M3 | Parser.java, TokenStream.java, ClassNode.java | `grep "statement()"` `grep "primaryExpr()"` |
| 转换层 | M4 | IRFactory.java, CodeGenerator.java | `grep "transformFunction"` `grep "visitStatement"` |
| 运行时 | M5 | NativeClass.java, ScriptRuntime.java, BaseFunction.java | 检查继承链、construct 方法 |
| 测试 | M6 | ClassTest.java, test262.properties | 运行单元测试 |
| 优化器 | M8 | optimizer/Codegen.java, optimizer/BodyCodegen.java | 检查 switch case |

#### 13.3.2 高风险文件预警

| 文件 | 风险等级 | 原因 | 缓解措施 |
|------|----------|------|----------|
| Parser.java | 高 | 核心解析逻辑，影响所有语法 | 增加回归测试覆盖，保持向后兼容 |
| IRFactory.java | 高 | IR 转换逻辑，影响执行语义 | 分步实现，每步验证 |
| ScriptRuntime.java | 中 | 运行时核心，影响性能 | 保持方法签名稳定 |
| optimizer/Codegen.java | 中 | 优化器代码生成 | 第一阶段降级到解释模式 |

### 13.4 具体文件调整清单 (File Adjustment Checklist)

#### 13.4.1 新增文件

| 优先级 | 文件路径 | 用途 | 预估代码量 | 依赖模块 |
|--------|----------|------|------------|----------|
| P0 | `rhino/.../ast/ClassNode.java` | Class AST 节点 | 250 LOC | Token.java |
| P0 | `rhino/.../ast/ClassElement.java` | Class 元素节点 | 180 LOC | ClassNode |
| P0 | `rhino/.../NativeClass.java` | 运行时类对象 | 300 LOC | BaseFunction |
| P1 | `rhino/.../UninitializedObject.java` | 派生类 this 占位 | 80 LOC | NativeClass |
| P2 | `tests/.../es2022/ClassTest.java` | 单元测试 | 200 LOC | JUnit |
| P2 | `tests/testsrc/jstests/es2022/class.js` | JS 测试脚本 | 300 LOC | 无 |

#### 13.4.2 修改文件 (精确到方法)

| 优先级 | 文件路径 | 方法/位置 | 修改内容 | 预估代码量 | 风险等级 |
|--------|----------|-----------|----------|------------|----------|
| P0 | `Token.java` | 常量定义区 (L249) | 新增 CLASS, EXTENDS, CLASS_ELEMENT, PRIVATE_FIELD, NEW_CLASS, STATIC_BLOCK | 20 LOC | 低 |
| P0 | `Token.java` | `typeToName()` (L260+) | 添加新 Token 名称映射 | 12 LOC | 低 |
| P0 | `UniqueTag.java` | 常量定义 (L22+) | 新增 ID_TDZ_VALUE = 4, TDZ_VALUE 实例 | 8 LOC | 低 |
| P0 | `Node.java` | 常量定义 (L70+) | 新增 CLASS_NAME_PROP, PRIVATE_FIELDS_PROP 等 | 15 LOC | 低 |
| P0 | `TokenStream.java` | `getToken()` | 添加 `#` 私有字段识别逻辑 | 30 LOC | 中 |
| P0 | `TokenStream.java` | `stringToKeywordForES()` | 修改 class/extends 返回对应 Token | 5 LOC | 低 |
| P0 | `Parser.java` | `statement()` (L~200) | 添加 Token.CLASS 分支 | 10 LOC | 中 |
| P0 | `Parser.java` | 新增方法 | 实现 `parseClassDefinition()` | 150 LOC | 高 |
| P0 | `Parser.java` | 新增方法 | 实现 `parseClassBody()` | 100 LOC | 高 |
| P0 | `Parser.java` | 新增方法 | 实现 `parseClassElement()` | 80 LOC | 高 |
| P0 | `Parser.java` | 新增方法 | 实现 `parseFieldDefinition()` | 50 LOC | 中 |
| P1 | `AstNode.java` | `visit()` 方法 | 添加 ClassNode 分支 | 5 LOC | 低 |
| P1 | `IRFactory.java` | `transform()` (L~200) | 添加 Token.CLASS 处理分支 | 20 LOC | 高 |
| P1 | `IRFactory.java` | 新增方法 | 实现 `transformClass()` | 200 LOC | 高 |
| P1 | `IRFactory.java` | 新增方法 | 实现 `injectFieldInitializers()` | 50 LOC | 中 |
| P1 | `IRFactory.java` | 新增方法 | 实现 `transformSuperConstructorCall()` | 40 LOC | 中 |
| P1 | `ScriptRuntime.java` | 新增方法 | 实现 `createClass()` | 100 LOC | 高 |
| P1 | `ScriptRuntime.java` | 新增方法 | 实现 `getPrivateField()` | 30 LOC | 中 |
| P1 | `ScriptRuntime.java` | 新增方法 | 实现 `setPrivateField()` | 30 LOC | 中 |
| P1 | `ScriptRuntime.java` | 新增方法 | 实现 `getVarWithTDZCheck()` | 25 LOC | 中 |
| P1 | `ScriptRuntime.java` | 新增方法 | 实现 `superConstructorCall()` | 50 LOC | 高 |
| P2 | `CodeGenerator.java` | switch 语句 (L~500) | 添加新 Token 的 case 分支 | 50 LOC | 中 |
| P2 | `Interpreter.java` | 指令注册 (L~1400) | 新增 DoClass, DoPrivateField 指令 | 100 LOC | 中 |
| P2 | `optimizer/Codegen.java` | `transform()` (L~150) | 添加类节点降级或支持逻辑 | 50 LOC | 高 |
| P3 | `Messages.properties` | 文件末尾 | 新增 40+ 条错误消息 | 45 LOC | 低 |
| P3 | `test262.properties` | 排除列表 | 移除 class 测试排除标记 | 2 LOC | 低 |

### 13.5 Git 提交策略建议

#### 13.5.1 提交分组

| 提交组 | 包含文件 | 说明 |
|--------|----------|------|
| group-01 | Token.java, Node.java, UniqueTag.java | 基础常量定义，无逻辑依赖 |
| group-02 | ClassNode.java, ClassElement.java | AST 节点定义，独立编译 |
| group-03 | TokenStream.java, Parser.java | 解析逻辑，影响语法树生成 |
| group-04 | IRFactory.java, CodeGenerator.java, Interpreter.java | 转换与字节码，影响执行 |
| group-05 | NativeClass.java, ScriptRuntime.java, UninitializedObject.java | 运行时逻辑，核心功能 |
| group-06 | Tests, Messages.properties, test262.properties | 测试与资源配置 |

#### 13.5.2 回滚计划

- 若 **group-03** 导致解析失败，立即回滚并检查 Token 定义和 Parser 入口分支。
- 若 **group-04** 导致 IR 转换错误，检查 transformClass() 的返回值和 Node 结构。
- 若 **group-05** 导致运行时错误，检查 `super()` 调用链、原型链设置和私有字段 brand check。

### 13.6 结论与下一步

- **计划可行性**: 可行，但需注意 TDZ 和线程安全问题
- **主要阻碍**: 
  1. TDZ 需要新增 UniqueTag.TDZ_VALUE
  2. 私有字段存储需使用 ConcurrentHashMap
  3. 优化编译器第一阶段不支持
- **建议行动**: 立即启动 M0 里程碑（Token 定义 + TDZ 框架原型验证）
