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
 * <p>This node type resembles {@link ObjectLiteral} in that it contains a collection of elements,
 * but differs in that it supports inheritance and has special semantics for constructor methods.
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
    private AstNode superClass; // extends clause (optional)
    private List<ClassElement> elements; // methods, fields, static blocks
    private int classType;
    private int extendsPosition = -1;
    private int lcPosition = -1; // left curly position
    private int rcPosition = -1; // right curly position

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
     *
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
     *
     * @return the element list, never null
     */
    public List<ClassElement> getElements() {
        return elements != null ? elements : NO_ELEMENTS;
    }

    /**
     * Sets the element list, and updates the parent of each element. Replaces any existing
     * elements.
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
     *
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
     *
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
     *
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
     *
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
     *
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
     *
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
     *
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
     *
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
     *
     * @return true if derived
     */
    public boolean isDerived() {
        return hasSuperClass();
    }

    // ===== hasSideEffects =====

    /**
     * Class declarations and expressions always have side effects because they create a new
     * constructor function and modify the scope chain.
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
     * Visits this node, the class name (if present), the super class (if present), and each class
     * element in source order.
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
