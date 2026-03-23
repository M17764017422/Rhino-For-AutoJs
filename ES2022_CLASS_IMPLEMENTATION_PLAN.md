# Rhino ES2022 Class 支持开发计划

> 版本: 1.0
> 日期: 2026-03-22
> 状态: 规划阶段

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
| `rhino/src/main/java/org/mozilla/javascript/ast/ClassNode.java` | 类声明/表达式 AST 节点 | ~180 行 |
| `rhino/src/main/java/org/mozilla/javascript/ast/ClassElement.java` | 类元素 AST 节点 | ~150 行 |
| `rhino/src/main/java/org/mozilla/javascript/NativeClass.java` | 类构造器运行时对象 | ~200 行 |
| `rhino/src/test/java/org/mozilla/javascript/ClassTest.java` | 单元测试 | ~400 行 |

### 4.2 修改文件

| 文件路径 | 修改内容 | 预估代码量 |
|----------|----------|------------|
| `rhino/src/main/java/org/mozilla/javascript/Token.java` | 新增 Token 类型 | ~25 行 |
| `rhino/src/main/java/org/mozilla/javascript/Parser.java` | 类解析逻辑 | ~350 行 |
| `rhino/src/main/java/org/mozilla/javascript/IRFactory.java` | IR 转换逻辑 | ~280 行 |
| `rhino/src/main/java/org/mozilla/javascript/Node.java` | 新增属性常量 | ~20 行 |
| `rhino/src/main/java/org/mozilla/javascript/ScriptRuntime.java` | 类创建辅助方法 | ~220 行 |
| `rhino/src/main/java/org/mozilla/javascript/ast/AstNode.java` | 添加 visit 支持 | ~5 行 |

**总计：约 1830 行代码**

## 五、详细实现方案

### 5.1 Token 新增 (`Token.java`)

```java
// 在 Token 枚举中添加，位于 LAST_TOKEN 之前
CLASS = YIELD + 2,              // class 关键字
EXTENDS = CLASS + 1,            // extends 关键字
CLASS_ELEMENT = EXTENDS + 1,    // 类元素节点类型
PRIVATE_FIELD = CLASS_ELEMENT + 1,  // 私有字段 # 前缀
NEW_CLASS = PRIVATE_FIELD + 1,  // 运行时创建类的 IR 节点
LAST_TOKEN = NEW_CLASS + 1;
```

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

#### 5.3.1 ClassNode

```java
package org.mozilla.javascript.ast;

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
 * </pre>
 */
public class ClassNode extends AstNode {

    /** Class declaration (statement context) */
    public static final int CLASS_STATEMENT = 1;

    /** Class expression (expression context) */
    public static final int CLASS_EXPRESSION = 2;

    private Name className;
    private AstNode superClass;           // extends clause (optional)
    private List<ClassElement> elements;   // methods, fields, static blocks
    private int classType;
    private int extendsPosition = -1;
    private int lcPosition = -1;           // left curly position
    private int rcPosition = -1;           // right curly position

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

    public boolean hasSuperClass() {
        return superClass != null;
    }

    public List<ClassElement> getElements() {
        return elements != null ? elements : new ArrayList<>();
    }

    public void setElements(List<ClassElement> elements) {
        if (this.elements != null) this.elements.clear();
        for (ClassElement element : elements) {
            addElement(element);
        }
    }

    public void addElement(ClassElement element) {
        assertNotNull(element);
        if (elements == null) {
            elements = new ArrayList<>();
        }
        elements.add(element);
        element.setParent(this);
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

    public int getLcPosition() {
        return lcPosition;
    }

    public void setLcPosition(int lcPosition) {
        this.lcPosition = lcPosition;
    }

    public int getRcPosition() {
        return rcPosition;
    }

    public void setRcPosition(int rcPosition) {
        this.rcPosition = rcPosition;
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

```java
package org.mozilla.javascript.ast;

/**
 * Represents an element within a class body: method, field, or static block.
 *
 * <p>Node type is {@link Token#CLASS_ELEMENT}.
 */
public class ClassElement extends AstNode {

    public static final int METHOD = 1;
    public static final int FIELD = 2;
    public static final int STATIC_BLOCK = 3;

    private int elementType;
    private boolean isStatic;
    private boolean isPrivate;     // ES2022 私有字段/方法
    private AstNode key;           // property name
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

    public boolean isMethod() {
        return elementType == METHOD;
    }

    public boolean isField() {
        return elementType == FIELD;
    }

    public boolean isStaticBlock() {
        return elementType == STATIC_BLOCK;
    }

    public boolean isConstructor() {
        return isMethod() && method != null 
            && method.getIntProp(Node.CONSTRUCTOR_METHOD, 0) == 1;
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

    // ===== toSource and visit =====

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        
        if (isStatic && elementType != STATIC_BLOCK) {
            sb.append("static ");
        }
        
        switch (elementType) {
            case METHOD:
                if (method.isGetterMethod()) {
                    sb.append("get ");
                } else if (method.isSetterMethod()) {
                    sb.append("set ");
                }
                if (method.isGenerator()) {
                    sb.append("*");
                }
                sb.append(key.toSource(0));
                sb.append(method.toSource(0).replaceFirst("^function\\s*", ""));
                break;
            case FIELD:
                if (isPrivate) {
                    sb.append("#");
                }
                sb.append(key.toSource(0));
                if (fieldValue != null) {
                    sb.append(" = ");
                    sb.append(fieldValue.toSource(0));
                }
                sb.append(";");
                break;
            case STATIC_BLOCK:
                sb.append("static ");
                sb.append(staticBlock.toSource(0));
                break;
        }
        return sb.toString();
    }

    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            if (key != null) key.visit(v);
            if (method != null) method.visit(v);
            if (fieldValue != null) fieldValue.visit(v);
            if (staticBlock != null) staticBlock.visit(v);
        }
    }
}
```

### 5.4 Parser 解析方法 (`Parser.java`)

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

### 5.5 IRFactory 转换逻辑 (`IRFactory.java`)

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

### 5.6 运行时支持

#### 5.6.1 NativeClass (`NativeClass.java`)

```java
package org.mozilla.javascript;

/**
 * Represents a JavaScript class constructor created via class syntax.
 * Extends BaseFunction with class-specific features.
 */
public class NativeClass extends BaseFunction {
    private static final long serialVersionUID = 1L;

    private static final Object CLASS_TAG = "Class";

    // The prototype object for instances created by this class
    private Scriptable classPrototype;

    // The super class (for extends)
    private Scriptable superClass;

    // Whether this is a derived class
    private boolean isDerived;

    public NativeClass() {
        super();
    }

    public NativeClass(Scriptable scope, Scriptable prototype) {
        super(scope, prototype);
    }

    @Override
    public String getClassName() {
        return "Function";  // Classes are functions in JS
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        // When called as a function, classes must be called with 'new'
        // This matches ES6 behavior where class constructors throw when called without new
        throw ScriptRuntime.typeErrorById("msg.class.not.new");
    }

    @Override
    public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
        // Get the constructor method
        Object constructor = get("constructor", this);
        
        if (isDerived) {
            // For derived classes, create a "this" value that's not yet initialized
            // The super() call will initialize it
            Scriptable newInstance = createUninitializedObject(cx, scope);
            
            if (constructor instanceof Callable) {
                Object result = ((Callable) constructor).call(cx, scope, newInstance, args);
                if (result instanceof Scriptable) {
                    return (Scriptable) result;
                }
            }
            return newInstance;
        } else {
            // For base classes, create new instance normally
            Scriptable newInstance = cx.newObject(this, getClassPrototype());
            
            if (constructor instanceof Callable) {
                ((Callable) constructor).call(cx, scope, newInstance, args);
            }
            
            return newInstance;
        }
    }

    /**
     * Create an uninitialized object for derived class construction.
     */
    private Scriptable createUninitializedObject(Context cx, Scriptable scope) {
        // Create an object with the correct prototype but not yet initialized
        Scriptable obj = cx.newObject(scope);
        obj.setPrototype(getClassPrototype());
        // Mark as uninitialized until super() is called
        obj.put("__uninitialized__", obj, Boolean.TRUE);
        return obj;
    }

    // Getters and setters

    public Scriptable getClassPrototype() {
        return classPrototype;
    }

    public void setClassPrototype(Scriptable classPrototype) {
        this.classPrototype = classPrototype;
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

#### 5.6.2 ScriptRuntime 辅助方法 (`ScriptRuntime.java`)

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

## 六、错误消息 (`messages.properties`)

```properties
# Class-related error messages
msg.no.brace.class=Expected '{' after class declaration
msg.no.brace.after.class=Expected '}' at end of class body
msg.no.brace.static.block=Expected '}' at end of static block
msg.bad.class.element=Invalid class element
msg.bad.getter.setter=Invalid getter or setter definition
msg.constructor.static=Class constructor may not be static
msg.constructor.generator=Class constructor may not be a generator
msg.class.not.new=Class constructor cannot be called without 'new'
msg.super.not.called=Must call super() in derived constructor before accessing 'this'
msg.super.already.called=super() has already been called
msg.private.field.access=Cannot access private field from outside class
```

## 七、测试用例

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

## 八、开发里程碑

| 里程碑 | 内容 | 预估时间 | 依赖 |
|--------|------|----------|------|
| **M1** | Token + AST 节点 (ClassNode, ClassElement) | 3 天 | 无 |
| **M2** | Parser 解析逻辑 (classDefinition, parseClassBody, parseClassElement) | 5 天 | M1 |
| **M3** | IRFactory 转换逻辑 (transformClass, 字段注入) | 4 天 | M2 |
| **M4** | 运行时支持 (NativeClass, ScriptRuntime.createClass) | 4 天 | M3 |
| **M5** | 单元测试 + test262 class 相关用例验证 | 4 天 | M4 |
| **M6** | Bug 修复 + 边界情况处理 | 3 天 | M5 |
| **总计** | | **23 天** | |

## 九、风险与注意事项

### 9.1 技术风险

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| super 调用的正确性 | 高 | 参考 V8/SpiderMonkey 实现，完善测试用例 |
| 派生类构造器语义 | 高 | 确保 super() 必须调用，this 初始化顺序正确 |
| 私有字段访问 | 中 | 使用 WeakMap 或 Symbol 实现私有存储 |
| 与现有代码兼容性 | 中 | 渐进式开发，每个阶段独立测试 |

### 9.2 注意事项

1. **派生类构造器**
   - 必须在访问 `this` 之前调用 `super()`
   - 默认构造器会自动调用 `super(...args)`

2. **super 属性访问**
   - `super.method()` 需要正确绑定 `this`
   - 静态方法中 `super` 指向父类而非父类原型

3. **私有字段**
   - 只能在类内部访问
   - 不参与原型继承

4. **静态块**
   - 在类定义时立即执行
   - 可以访问私有静态字段

## 十、参考资料

- [ECMAScript 2022 Specification - Class Definitions](https://tc39.es/ecma262/#sec-class-definitions)
- [ECMAScript 2022 Specification - Super Keyword](https://tc39.es/ecma262/#sec-super-keyword)
- [V8 Class Implementation](https://v8.dev/blog/understanding-ecmascript-part-2)
- [SpiderMonkey Class Implementation](https://spidermonkey.dev/)
- [test262 Class Tests](https://github.com/tc39/test262/tree/main/test/language/statements/class)

## 十一、变更历史

| 版本 | 日期 | 作者 | 变更内容 |
|------|------|------|----------|
| 1.0 | 2026-03-22 | iFlow CLI | 初始版本 |
