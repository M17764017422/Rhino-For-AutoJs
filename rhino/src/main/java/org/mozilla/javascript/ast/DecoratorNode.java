/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

/**
 * AST node representing an ES2023 decorator.
 *
 * <p>Decorators are expressions prefixed with @ that can be applied to classes, methods, fields,
 * getters, setters, and accessors.
 *
 * <pre>
 * Decorator:
 *     @ DecoratorMemberExpression
 *     @ DecoratorCallExpression
 *
 * DecoratorMemberExpression:
 *     IdentifierName
 *     DecoratorMemberExpression . IdentifierName
 *
 * DecoratorCallExpression:
 *     DecoratorMemberExpression Arguments
 * </pre>
 *
 * <p>Examples:
 *
 * <pre>
 * &#64;decorator
 * &#64;decorator(arg1, arg2)
 * &#64;namespace.decorator
 * &#64;namespace.decorator(arg)
 * </pre>
 *
 * @see ClassNode
 * @see ClassElement
 */
public class DecoratorNode extends AstNode {

    // The decorator expression - can be Name, PropertyGet, or FunctionCall
    private AstNode expression;

    {
        type = Token.DECORATOR;
    }

    public DecoratorNode() {}

    public DecoratorNode(int pos) {
        super(pos);
    }

    public DecoratorNode(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns the decorator expression.
     *
     * @return the expression node (Name, PropertyGet, or FunctionCall)
     */
    public AstNode getExpression() {
        return expression;
    }

    /**
     * Sets the decorator expression.
     *
     * @param expression the expression node
     */
    public void setExpression(AstNode expression) {
        this.expression = expression;
        if (expression != null) {
            expression.setParent(this);
        }
    }

    /**
     * Returns true if this decorator has a call expression (with arguments).
     *
     * @return true if this is a decorator factory call
     */
    public boolean isDecoratorFactory() {
        return expression != null && expression.getType() == Token.CALL;
    }

    /**
     * Returns the decorator name as a string if it's a simple identifier or property chain. Returns
     * null for computed expressions.
     *
     * @return the decorator name or null
     */
    public String getDecoratorName() {
        if (expression == null) {
            return null;
        }
        return getDecoratorNameHelper(expression);
    }

    private String getDecoratorNameHelper(AstNode node) {
        if (node instanceof Name) {
            return ((Name) node).getIdentifier();
        } else if (node instanceof PropertyGet) {
            PropertyGet pg = (PropertyGet) node;
            String target = getDecoratorNameHelper(pg.getTarget());
            if (target != null) {
                return target + "." + pg.getProperty().getIdentifier();
            }
        } else if (node instanceof FunctionCall) {
            FunctionCall fc = (FunctionCall) node;
            return getDecoratorNameHelper(fc.getTarget());
        }
        return null;
    }

    @Override
    public boolean hasSideEffects() {
        // Decorators always have side effects - they modify classes/elements
        return true;
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append("@");
        if (expression != null) {
            sb.append(expression.toSource(0));
        }
        return sb.toString();
    }

    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            if (expression != null) {
                expression.visit(v);
            }
        }
    }
}
