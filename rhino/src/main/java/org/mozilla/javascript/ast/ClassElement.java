/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

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
    private boolean isPrivate; // ES2022 私有字段/方法
    private boolean isComputed; // Computed property key [expr]
    private AstNode key; // property name (Name, StringLiteral, NumberLiteral, or expression for
    // computed)
    private FunctionNode method; // for methods (including getter/setter)
    private AstNode fieldValue; // for fields
    private Block staticBlock; // for static blocks

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
     * Returns true if this element is the constructor method. A constructor is a method named
     * "constructor" that is not static.
     */
    public boolean isConstructor() {
        return isMethod()
                && !isStatic
                && method != null
                && method.getIntProp(Node.CONSTRUCTOR_METHOD, 0) == 1;
    }

    /** Marks this element as the constructor method. */
    public void setIsConstructor() {
        if (method != null) {
            method.putIntProp(Node.CONSTRUCTOR_METHOD, 1);
        }
    }

    /** Returns true if this method is a getter. */
    public boolean isGetter() {
        return isMethod() && method != null && method.isGetterMethod();
    }

    /** Returns true if this method is a setter. */
    public boolean isSetter() {
        return isMethod() && method != null && method.isSetterMethod();
    }

    /** Returns true if this method is a generator (prefixed with *). */
    public boolean isGenerator() {
        return isMethod() && method != null && method.isGenerator();
    }

    /**
     * Returns the property key as a string, or null if it's a computed key. For computed keys, use
     * getKey() to get the expression.
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
     * Class elements always have side effects. - Methods and fields modify the class structure -
     * Static blocks execute code
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

        // Parameters and body - strip the leading "function" keyword
        String methodSource = method.toSource(0);
        // Remove "function" or "function*" prefix for method shorthand
        if (methodSource.startsWith("function*")) {
            methodSource = methodSource.substring(9); // Remove "function*"
        } else if (methodSource.startsWith("function")) {
            methodSource = methodSource.substring(8); // Remove "function"
        }
        sb.append(methodSource);
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
     * Visits this node, the key, and the method/fieldValue/staticBlock depending on the element
     * type.
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            if (key != null) {
                key.visit(v);
            }
            switch (elementType) {
                case METHOD:
                    if (method != null) {
                        method.visit(v);
                    }
                    break;
                case FIELD:
                    if (fieldValue != null) {
                        fieldValue.visit(v);
                    }
                    break;
                case STATIC_BLOCK:
                    if (staticBlock != null) {
                        staticBlock.visit(v);
                    }
                    break;
            }
        }
    }
}
