/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

/**
 * ES2023 Decorator context object passed to decorators.
 *
 * <p>When a decorator is invoked, it receives a context object that describes what is being
 * decorated. This class provides the runtime representation of that context.
 *
 * <pre>
 * interface DecoratorContext {
 *   kind: "class" | "method" | "getter" | "setter" | "field" | "accessor";
 *   name: string | symbol | private;
 *   isStatic: boolean;
 *   isPrivate: boolean;
 *   access: { get, set, has };
 *   addInitializer?: (initializer: Function) =&gt; void;
 * }
 * </pre>
 *
 * @see DecoratorNode
 * @see ClassNode
 * @see ClassElement
 */
public class DecoratorContext extends ScriptableObject {

    private static final long serialVersionUID = 1L;

    // Decoration kinds
    public static final String KIND_CLASS = "class";
    public static final String KIND_METHOD = "method";
    public static final String KIND_GETTER = "getter";
    public static final String KIND_SETTER = "setter";
    public static final String KIND_FIELD = "field";
    public static final String KIND_ACCESSOR = "accessor";

    // Context properties
    private String kind;
    private Object name; // String, Symbol, or PrivateName
    private boolean isStatic;
    private boolean isPrivate;
    private AccessorInfo access;
    private java.util.List<Scriptable> initializers;

    /** Default constructor for ScriptableObject. */
    public DecoratorContext() {}

    /**
     * Create a new DecoratorContext.
     *
     * @param scope the parent scope
     * @param kind the kind of decoration (class, method, getter, setter, field, accessor)
     * @param name the name of the decorated element
     * @param isStatic whether the element is static
     * @param isPrivate whether the element is private
     */
    public DecoratorContext(
            Scriptable scope, String kind, Object name, boolean isStatic, boolean isPrivate) {
        this.kind = kind;
        this.name = name;
        this.isStatic = isStatic;
        this.isPrivate = isPrivate;
        this.initializers = new java.util.ArrayList<>();
        setParentScope(scope);
        setPrototype(ScriptableObject.getClassPrototype(scope, "Object"));
    }

    @Override
    public String getClassName() {
        return "DecoratorContext";
    }

    // ===== Property Getters =====

    /**
     * Returns the kind of decoration.
     *
     * @return "class", "method", "getter", "setter", "field", or "accessor"
     */
    public String getKind() {
        return kind;
    }

    /**
     * Returns the name of the decorated element.
     *
     * @return the name (String, Symbol, or PrivateName)
     */
    public Object getName() {
        return name;
    }

    /**
     * Returns whether the element is static.
     *
     * @return true if static
     */
    public boolean getIsStatic() {
        return isStatic;
    }

    /**
     * Returns whether the element is private.
     *
     * @return true if private
     */
    public boolean getIsPrivate() {
        return isPrivate;
    }

    /**
     * Returns the access object for private members.
     *
     * @return the access info, or null for non-private members
     */
    public AccessorInfo getAccess() {
        return access;
    }

    // ===== Property Access for JavaScript =====

    @Override
    public Object get(String name, Scriptable start) {
        switch (name) {
            case "kind":
                return kind;
            case "name":
                return this.name;
            case "isStatic":
                return isStatic;
            case "isPrivate":
                return isPrivate;
            case "access":
                return access;
            case "addInitializer":
                // Return a function that adds an initializer
                return new BaseFunction() {
                    @Override
                    public Object call(
                            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                        if (args.length > 0 && args[0] instanceof Scriptable) {
                            initializers.add((Scriptable) args[0]);
                        }
                        return Undefined.instance;
                    }
                };
            default:
                return super.get(name, start);
        }
    }

    @Override
    public boolean has(String name, Scriptable start) {
        switch (name) {
            case "kind":
            case "name":
            case "isStatic":
            case "isPrivate":
            case "access":
            case "addInitializer":
                return true;
            default:
                return super.has(name, start);
        }
    }

    // ===== Initializer Management =====

    /**
     * Returns the list of registered initializers.
     *
     * @return the initializers list
     */
    public java.util.List<Scriptable> getInitializers() {
        return initializers;
    }

    /**
     * Returns true if there are any registered initializers.
     *
     * @return true if initializers exist
     */
    public boolean hasInitializers() {
        return initializers != null && !initializers.isEmpty();
    }

    /**
     * Sets the access object for private members.
     *
     * @param access the access info
     */
    public void setAccess(AccessorInfo access) {
        this.access = access;
    }

    // ===== Factory Methods =====

    /**
     * Create a DecoratorContext for a class decoration.
     *
     * @param scope the parent scope
     * @param className the class name
     * @return the context
     */
    public static DecoratorContext forClass(Scriptable scope, String className) {
        return new DecoratorContext(scope, KIND_CLASS, className, false, false);
    }

    /**
     * Create a DecoratorContext for a method decoration.
     *
     * @param scope the parent scope
     * @param methodName the method name
     * @param isStatic whether the method is static
     * @param isPrivate whether the method is private
     * @return the context
     */
    public static DecoratorContext forMethod(
            Scriptable scope, Object methodName, boolean isStatic, boolean isPrivate) {
        return new DecoratorContext(scope, KIND_METHOD, methodName, isStatic, isPrivate);
    }

    /**
     * Create a DecoratorContext for a getter decoration.
     *
     * @param scope the parent scope
     * @param getterName the getter name
     * @param isStatic whether the getter is static
     * @param isPrivate whether the getter is private
     * @return the context
     */
    public static DecoratorContext forGetter(
            Scriptable scope, Object getterName, boolean isStatic, boolean isPrivate) {
        return new DecoratorContext(scope, KIND_GETTER, getterName, isStatic, isPrivate);
    }

    /**
     * Create a DecoratorContext for a setter decoration.
     *
     * @param scope the parent scope
     * @param setterName the setter name
     * @param isStatic whether the setter is static
     * @param isPrivate whether the setter is private
     * @return the context
     */
    public static DecoratorContext forSetter(
            Scriptable scope, Object setterName, boolean isStatic, boolean isPrivate) {
        return new DecoratorContext(scope, KIND_SETTER, setterName, isStatic, isPrivate);
    }

    /**
     * Create a DecoratorContext for a field decoration.
     *
     * @param scope the parent scope
     * @param fieldName the field name
     * @param isStatic whether the field is static
     * @param isPrivate whether the field is private
     * @return the context
     */
    public static DecoratorContext forField(
            Scriptable scope, Object fieldName, boolean isStatic, boolean isPrivate) {
        return new DecoratorContext(scope, KIND_FIELD, fieldName, isStatic, isPrivate);
    }

    // ===== Inner class for private member access =====

    /**
     * Represents the access object for private members in ES2023 decorators. Provides get, set, and
     * has methods for accessing private fields/methods.
     */
    public static class AccessorInfo extends ScriptableObject {

        private static final long serialVersionUID = 1L;

        private final Scriptable getter;
        private final Scriptable setter;
        private final Scriptable hasChecker;

        public AccessorInfo(
                Scriptable scope, Scriptable getter, Scriptable setter, Scriptable hasChecker) {
            this.getter = getter;
            this.setter = setter;
            this.hasChecker = hasChecker;
            setParentScope(scope);
            setPrototype(ScriptableObject.getClassPrototype(scope, "Object"));
        }

        @Override
        public String getClassName() {
            return "AccessorInfo";
        }

        @Override
        public Object get(String name, Scriptable start) {
            switch (name) {
                case "get":
                    return getter;
                case "set":
                    return setter;
                case "has":
                    return hasChecker;
                default:
                    return super.get(name, start);
            }
        }

        @Override
        public boolean has(String name, Scriptable start) {
            switch (name) {
                case "get":
                case "set":
                case "has":
                    return true;
                default:
                    return super.has(name, start);
            }
        }
    }
}
