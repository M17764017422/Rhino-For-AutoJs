/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

/**
 * Represents an uninitialized object during derived class construction.
 *
 * <p>In ES6+, when a derived class constructor is called, the `this` value starts as uninitialized
 * until `super()` is called. This class represents that uninitialized state and throws appropriate
 * errors if accessed before super() completes.
 *
 * <p>See ECMAScript 2015 specification, section 9.2.2 for the details of derived constructor
 * behavior.
 *
 * @see NativeClass
 * @see ScriptRuntime#throwTDZError
 */
public class UninitializedObject extends ScriptableObject {
    private static final long serialVersionUID = 1L;

    /** The class that is being constructed */
    private final NativeClass constructingClass;

    /** The scope in which construction is happening */
    private Scriptable scope;

    /** The actual instance after super() completes */
    private Scriptable initializedInstance;

    /** True if super() has been called and the object is now initialized */
    private boolean isInitialized;

    /**
     * Creates a new uninitialized object for derived class construction.
     *
     * @param constructingClass the class being constructed
     * @param scope the current scope
     */
    private UninitializedObject(NativeClass constructingClass, Scriptable scope) {
        this.constructingClass = constructingClass;
        this.scope = scope;
        this.isInitialized = false;
        this.initializedInstance = null;

        setParentScope(scope);
    }

    /**
     * Creates a new uninitialized object instance.
     *
     * @param constructingClass the class being constructed
     * @param scope the current scope
     * @return the uninitialized object
     */
    public static UninitializedObject create(NativeClass constructingClass, Scriptable scope) {
        return new UninitializedObject(constructingClass, scope);
    }

    /** Returns true if this object has been initialized via super() */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Returns the initialized instance after super() completes.
     *
     * @return the initialized instance
     * @throws EcmaError if super() has not been called yet
     */
    public Scriptable getInitializedInstance() {
        if (!isInitialized) {
            throw ScriptRuntime.referenceErrorById("msg.class.super.before.this");
        }
        return initializedInstance;
    }

    /**
     * Initializes this object by calling super() with the given arguments.
     *
     * @param cx the current context
     * @param args the arguments to pass to super()
     * @return the initialized instance
     */
    public Scriptable initializeWithSuper(Context cx, Object[] args) {
        if (isInitialized) {
            throw ScriptRuntime.referenceErrorById("msg.class.super.duplicate");
        }

        Scriptable superClass = constructingClass.getSuperClass();
        if (!(superClass instanceof NativeClass)) {
            // Super is a regular function or null - handle accordingly
            if (superClass instanceof Function) {
                initializedInstance = ((Function) superClass).construct(cx, scope, args);
            } else if (superClass == null) {
                throw ScriptRuntime.typeErrorById("msg.class.extends.null");
            } else {
                throw ScriptRuntime.typeErrorById("msg.class.extends.not.constructor");
            }
        } else {
            // Super is another class - use its construct
            initializedInstance = ((NativeClass) superClass).construct(cx, scope, args);
            // If it's also derived, get the initialized instance
            if (initializedInstance instanceof UninitializedObject) {
                // This shouldn't happen - super() should return initialized
                initializedInstance =
                        ((UninitializedObject) initializedInstance).getInitializedInstance();
            }
        }

        isInitialized = true;
        return initializedInstance;
    }

    /**
     * Checks if this object can access the given class's private fields. Only valid after
     * initialization.
     */
    public boolean canAccessPrivateFieldsOf(NativeClass targetClass) {
        if (!isInitialized) {
            return false;
        }

        // Check if the target class is in our inheritance chain
        Object brand = initializedInstance.get("_classBrand", initializedInstance);
        return brand == targetClass.getClassBrand();
    }

    // ==================== ScriptableObject overrides ====================

    @Override
    public Object get(String name, Scriptable start) {
        if (!isInitialized) {
            throw ScriptRuntime.referenceErrorById("msg.class.super.before.this");
        }
        return initializedInstance.get(name, start);
    }

    @Override
    public Object get(int index, Scriptable start) {
        if (!isInitialized) {
            throw ScriptRuntime.referenceErrorById("msg.class.super.before.this");
        }
        return initializedInstance.get(index, start);
    }

    @Override
    public boolean has(String name, Scriptable start) {
        if (!isInitialized) {
            return false;
        }
        return initializedInstance.has(name, start);
    }

    @Override
    public boolean has(int index, Scriptable start) {
        if (!isInitialized) {
            return false;
        }
        return initializedInstance.has(index, start);
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        if (!isInitialized) {
            throw ScriptRuntime.referenceErrorById("msg.class.super.before.this");
        }
        initializedInstance.put(name, start, value);
    }

    @Override
    public void put(int index, Scriptable start, Object value) {
        if (!isInitialized) {
            throw ScriptRuntime.referenceErrorById("msg.class.super.before.this");
        }
        initializedInstance.put(index, start, value);
    }

    @Override
    public void delete(String name) {
        if (isInitialized) {
            initializedInstance.delete(name);
        }
    }

    @Override
    public void delete(int index) {
        if (isInitialized) {
            initializedInstance.delete(index);
        }
    }

    @Override
    public Scriptable getPrototype() {
        if (!isInitialized) {
            return null;
        }
        return initializedInstance.getPrototype();
    }

    @Override
    public void setPrototype(Scriptable prototype) {
        if (isInitialized) {
            initializedInstance.setPrototype(prototype);
        }
    }

    @Override
    public Scriptable getParentScope() {
        return scope;
    }

    @Override
    public void setParentScope(Scriptable parent) {
        this.scope = parent;
    }

    @Override
    public Object[] getIds() {
        if (!isInitialized) {
            return ScriptRuntime.emptyArgs;
        }
        return initializedInstance.getIds();
    }

    @Override
    public Object getDefaultValue(Class<?> hint) {
        if (!isInitialized) {
            throw ScriptRuntime.referenceErrorById("msg.class.super.before.this");
        }
        return initializedInstance.getDefaultValue(hint);
    }

    @Override
    public boolean hasInstance(Scriptable instance) {
        if (!isInitialized) {
            return false;
        }
        return initializedInstance.hasInstance(instance);
    }

    @Override
    public String getClassName() {
        return "UninitializedObject";
    }

    @Override
    public String toString() {
        if (!isInitialized) {
            return "[UninitializedObject]";
        }
        return initializedInstance.toString();
    }
}
