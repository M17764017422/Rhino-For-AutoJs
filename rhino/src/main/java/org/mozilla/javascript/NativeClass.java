/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime representation of an ES6+ class constructor.
 *
 * <p>NativeClass extends BaseFunction and represents a class constructor created via class
 * declaration or class expression. It handles:
 *
 * <ul>
 *   <li>Class instantiation via [[Construct]]
 *   <li>Prototype chain setup for inheritance
 *   <li>Private field storage and access control
 *   <li>Static method and property access
 * </ul>
 *
 * @see org.mozilla.javascript.ast.ClassNode
 * @see ScriptRuntime#createClass
 */
public class NativeClass extends BaseFunction {
    private static final long serialVersionUID = 1L;

    private static final String CLASS_TAG = "Class";

    /** The brand used for private field access validation */
    private final Object classBrand;

    /** The superclass constructor (null for base classes) */
    private final Scriptable superClass;

    /** The constructor function */
    private final Function constructor;

    /** Private field storage: instance -> (fieldName -> value) */
    private final Map<Object, Map<String, Object>> privateFieldStorage;

    /** Static private field storage: fieldName -> value */
    private final Map<String, Object> staticPrivateFields;

    /** Private method storage: fieldName -> Function (shared across instances) */
    private final Map<String, Function> privateMethods;

    /** Private static method storage: fieldName -> Function */
    private final Map<String, Function> privateStaticMethods;

    /** Private getter storage: fieldName -> Function */
    private final Map<String, Function> privateGetters;

    /** Private setter storage: fieldName -> Function */
    private final Map<String, Function> privateSetters;

    /** Private static getter storage: fieldName -> Function */
    private final Map<String, Function> privateStaticGetters;

    /** Private static setter storage: fieldName -> Function */
    private final Map<String, Function> privateStaticSetters;

    /** Declared private field names (for brand validation and existence checks) */
    private final Set<String> declaredPrivateFields;

    /** Instance field initializers: fieldName -> initializer function */
    private Scriptable instanceFieldInitializers;

    /** Static field initializers to execute */
    private Scriptable staticFieldInitializers;

    /** Static blocks to execute */
    private Scriptable staticBlocks;

    /** Whether this is a derived class (has extends clause) */
    private final boolean isDerived;

    /**
     * Creates a new NativeClass.
     *
     * @param className the class name (or empty string for anonymous classes)
     * @param superClass the superclass constructor (null for base classes)
     * @param constructor the constructor function
     * @param scope the parent scope
     */
    public NativeClass(
            String className, Scriptable superClass, Function constructor, Scriptable scope) {
        this.classBrand = new Object(); // Unique brand for private field access
        this.superClass = superClass;
        this.constructor = constructor;
        this.isDerived = superClass != null;
        this.privateFieldStorage = new ConcurrentHashMap<>();
        this.staticPrivateFields = new ConcurrentHashMap<>();
        // Initialize private method and accessor storage
        this.privateMethods = new ConcurrentHashMap<>();
        this.privateStaticMethods = new ConcurrentHashMap<>();
        this.privateGetters = new ConcurrentHashMap<>();
        this.privateSetters = new ConcurrentHashMap<>();
        this.privateStaticGetters = new ConcurrentHashMap<>();
        this.privateStaticSetters = new ConcurrentHashMap<>();
        this.declaredPrivateFields = ConcurrentHashMap.newKeySet();

        setParentScope(scope);
        setFunctionName(className != null ? className : "");

        // Set up prototype chain
        setPrototype(ScriptableObject.getFunctionPrototype(scope));

        // Create the prototype property for instances
        Scriptable classPrototype = new NativeObject();
        classPrototype.setParentScope(scope);
        if (superClass != null) {
            // Set up inheritance: classPrototype inherits from superClass.prototype
            Object superProto = superClass.get("prototype", superClass);
            if (superProto instanceof Scriptable) {
                classPrototype.setPrototype((Scriptable) superProto);
            }
        } else {
            classPrototype.setPrototype(ScriptableObject.getObjectPrototype(scope));
        }
        defineProperty("prototype", classPrototype, DONTENUM | PERMANENT);

        // Also set the prototypeProperty field so that .prototype access works correctly
        // BaseFunction.prototypeGetter uses getPrototypeProperty() which reads this field
        setPrototypeProperty(classPrototype);

        // Set constructor reference on prototype
        ScriptableObject.putProperty(classPrototype, "constructor", this);

        // Set up constructor prototype chain (class inherits from superClass)
        if (superClass != null) {
            setPrototype(superClass);
        }
    }

    /** Returns true if this is a derived class (has extends clause) */
    public boolean isDerived() {
        return isDerived;
    }

    /** Returns the superclass constructor */
    public Scriptable getSuperClass() {
        return superClass;
    }

    /** Returns the constructor function */
    public Function getConstructor() {
        return constructor;
    }

    /** Returns the class brand used for private field access validation */
    public Object getClassBrand() {
        return classBrand;
    }

    /** Sets the instance field initializers */
    public void setInstanceFieldInitializers(Scriptable initializers) {
        this.instanceFieldInitializers = initializers;
    }

    /** Sets the static field initializers */
    public void setStaticFieldInitializers(Scriptable initializers) {
        this.staticFieldInitializers = initializers;
    }

    /** Sets the static blocks to execute */
    public void setStaticBlocks(Scriptable blocks) {
        this.staticBlocks = blocks;
    }

    /**
     * Executes static initialization (static fields and static blocks). Called once when the class
     * is created.
     */
    public void executeStaticInitialization(Context cx, Scriptable scope) {
        // Execute static initialization function with 'this' bound to the class itself
        // This allows static field initializers to use 'this.fieldName' instead of
        // 'ClassName.fieldName'
        if (staticBlocks != null) {
            executeBlock(staticBlocks, cx, scope, this);
        }
    }

    /**
     * Executes instance field initializers on a newly created instance.
     *
     * @param instance the instance to initialize
     * @param cx the current context
     * @param scope the current scope
     */
    public void initializeInstanceFields(Scriptable instance, Context cx, Scriptable scope) {
        if (instanceFieldInitializers != null) {
            executeBlock(instanceFieldInitializers, cx, scope, instance);
        }
    }

    private void executeBlock(Scriptable block, Context cx, Scriptable scope) {
        executeBlock(block, cx, scope, null);
    }

    private void executeBlock(Scriptable block, Context cx, Scriptable scope, Scriptable thisObj) {
        // The block contains statements to execute
        // In practice, this is handled by the IR transformation
        if (block instanceof Function) {
            ((Function) block)
                    .call(cx, scope, thisObj != null ? thisObj : this, ScriptRuntime.emptyArgs);
        }
    }

    // ==================== Private Field Management ====================

    /**
     * Declares a private field name. Called during class initialization to register all declared
     * private fields.
     *
     * @param fieldName the private field name (without # prefix)
     */
    public void declarePrivateField(String fieldName) {
        declaredPrivateFields.add(fieldName);
    }

    /**
     * Gets a private field value from an instance.
     *
     * @param instance the instance
     * @param fieldName the private field name (without # prefix)
     * @param brand the expected class brand for validation
     * @return the field value
     * @throws EcmaError if access is denied or field doesn't exist
     */
    public Object getPrivateField(Object instance, String fieldName, Object brand) {
        validatePrivateAccess(brand);

        Map<String, Object> fields = privateFieldStorage.get(instance);
        if (fields == null || !fields.containsKey(fieldName)) {
            throw ScriptRuntime.referenceErrorById("msg.class.private.field.not.found", fieldName);
        }
        return fields.get(fieldName);
    }

    /**
     * Sets a private field value on an instance.
     *
     * @param instance the instance
     * @param fieldName the private field name (without # prefix)
     * @param value the value to set
     * @param brand the expected class brand for validation
     */
    public void setPrivateField(Object instance, String fieldName, Object value, Object brand) {
        validatePrivateAccess(brand);

        Map<String, Object> fields =
                privateFieldStorage.computeIfAbsent(instance, k -> new ConcurrentHashMap<>());
        fields.put(fieldName, value);
    }

    /**
     * Checks if a private field exists on an instance.
     *
     * @param instance the instance
     * @param fieldName the private field name (without # prefix)
     * @param brand the expected class brand for validation
     * @return true if the field exists
     */
    public boolean hasPrivateField(Object instance, String fieldName, Object brand) {
        validatePrivateAccess(brand);

        Map<String, Object> fields = privateFieldStorage.get(instance);
        return fields != null && fields.containsKey(fieldName);
    }

    /**
     * Gets a static private field value.
     *
     * @param fieldName the private field name (without # prefix)
     * @param brand the expected class brand for validation
     * @return the field value
     */
    public Object getStaticPrivateField(String fieldName, Object brand) {
        validatePrivateAccess(brand);

        if (!staticPrivateFields.containsKey(fieldName)) {
            throw ScriptRuntime.referenceErrorById("msg.class.private.field.not.found", fieldName);
        }
        return staticPrivateFields.get(fieldName);
    }

    /**
     * Sets a static private field value.
     *
     * @param fieldName the private field name (without # prefix)
     * @param value the value to set
     * @param brand the expected class brand for validation
     */
    public void setStaticPrivateField(String fieldName, Object value, Object brand) {
        validatePrivateAccess(brand);
        staticPrivateFields.put(fieldName, value);
    }

    // ==================== Private Method Management ====================

    /**
     * Gets a private method.
     *
     * @param fieldName the private method name (without # prefix)
     * @param brand the expected class brand for validation (unused, kept for API compatibility)
     * @return the method function
     */
    public Function getPrivateMethod(String fieldName, Object brand) {
        // Note: Brand validation is handled by the caller (ScriptRuntime.getPrivateFieldInternal)
        // which traverses the prototype chain to find the class that defines this private member.
        // The access is valid if we reach this point.
        Function method = privateMethods.get(fieldName);
        if (method == null) {
            throw ScriptRuntime.referenceErrorById("msg.class.private.method.access", fieldName);
        }
        return method;
    }

    /**
     * Sets a private method.
     *
     * @param fieldName the private method name (without # prefix)
     * @param method the method function
     * @param brand the expected class brand for validation
     */
    public void setPrivateMethod(String fieldName, Function method, Object brand) {
        validatePrivateAccess(brand);
        privateMethods.put(fieldName, method);
    }

    /** Gets a private static method. */
    public Function getPrivateStaticMethod(String fieldName, Object brand) {
        validatePrivateAccess(brand);
        Function method = privateStaticMethods.get(fieldName);
        if (method == null) {
            throw ScriptRuntime.referenceErrorById("msg.class.private.method.access", fieldName);
        }
        return method;
    }

    /** Sets a private static method. */
    public void setPrivateStaticMethod(String fieldName, Function method, Object brand) {
        validatePrivateAccess(brand);
        privateStaticMethods.put(fieldName, method);
    }

    // ==================== Private Accessor Management ====================

    /** Gets a private getter. */
    public Function getPrivateGetter(String fieldName, Object brand) {
        validatePrivateAccess(brand);
        return privateGetters.get(fieldName);
    }

    /** Sets a private getter. */
    public void setPrivateGetter(String fieldName, Function getter, Object brand) {
        validatePrivateAccess(brand);
        privateGetters.put(fieldName, getter);
    }

    /** Gets a private setter. */
    public Function getPrivateSetter(String fieldName, Object brand) {
        validatePrivateAccess(brand);
        return privateSetters.get(fieldName);
    }

    /** Sets a private setter. */
    public void setPrivateSetter(String fieldName, Function setter, Object brand) {
        validatePrivateAccess(brand);
        privateSetters.put(fieldName, setter);
    }

    /** Gets a private static getter. */
    public Function getPrivateStaticGetter(String fieldName, Object brand) {
        validatePrivateAccess(brand);
        return privateStaticGetters.get(fieldName);
    }

    /** Sets a private static getter. */
    public void setPrivateStaticGetter(String fieldName, Function getter, Object brand) {
        validatePrivateAccess(brand);
        privateStaticGetters.put(fieldName, getter);
    }

    /** Gets a private static setter. */
    public Function getPrivateStaticSetter(String fieldName, Object brand) {
        validatePrivateAccess(brand);
        return privateStaticSetters.get(fieldName);
    }

    /** Sets a private static setter. */
    public void setPrivateStaticSetter(String fieldName, Function setter, Object brand) {
        validatePrivateAccess(brand);
        privateStaticSetters.put(fieldName, setter);
    }

    /**
     * Checks if a private member (field, method, or accessor) exists.
     *
     * @param fieldName the private member name (without # prefix)
     * @param brand the expected class brand for validation
     * @return true if the member exists
     */
    public boolean hasPrivateMember(String fieldName, Object brand) {
        validatePrivateAccess(brand);
        return privateMethods.containsKey(fieldName)
                || privateGetters.containsKey(fieldName)
                || privateSetters.containsKey(fieldName);
    }

    /**
     * Checks if this class defines a private field with the given name (including static private
     * fields). This is used for brand validation in inheritance chains.
     *
     * @param fieldName the private field name (without # prefix)
     * @return true if this class defines the field
     */
    public boolean hasPrivateFieldDefinition(String fieldName) {
        return declaredPrivateFields.contains(fieldName)
                || staticPrivateFields.containsKey(fieldName)
                || privateMethods.containsKey(fieldName)
                || privateStaticMethods.containsKey(fieldName)
                || privateGetters.containsKey(fieldName)
                || privateSetters.containsKey(fieldName)
                || privateStaticGetters.containsKey(fieldName)
                || privateStaticSetters.containsKey(fieldName);
    }

    /** Validates that the provided brand matches this class's brand. */
    private void validatePrivateAccess(Object brand) {
        if (brand != this.classBrand) {
            throw ScriptRuntime.typeErrorById("msg.class.private.field.access");
        }
    }

    // ==================== Function Implementation ====================

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        // Class constructors cannot be called without new (ES6 spec)
        // However, super() calls this when constructing derived class instances
        if (thisObj == null || thisObj == Undefined.instance) {
            throw ScriptRuntime.typeErrorById("msg.class.constructor.new");
        }

        // Call the constructor function with thisObj as this
        return constructor.call(cx, scope, thisObj, args);
    }

    @Override
    public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
        if (isDerived) {
            // Derived classes must call super() first
            // Return an uninitialized object that will be filled in by super()
            return UninitializedObject.create(this, scope);
        } else {
            // Base class: create instance and call constructor
            Scriptable instance = createInstance(cx, scope);
            constructor.call(cx, scope, instance, args);
            initializeInstanceFields(instance, cx, scope);
            return instance;
        }
    }

    /**
     * Creates a new instance of this class without calling the constructor. Used by derived class
     * construction after super() returns.
     */
    public Scriptable createInstance(Context cx, Scriptable scope) {
        Scriptable instance = cx.newObject(scope);

        // Set up prototype chain
        Object proto = get("prototype", this);
        if (proto instanceof Scriptable) {
            instance.setPrototype((Scriptable) proto);
        }

        // Set parent scope
        instance.setParentScope(scope);

        // Store brand for private field access validation using defineProperty
        // for stable, non-enumerable, read-only storage
        ScriptableObject.defineProperty(
                instance, "_classBrand", classBrand, DONTENUM | READONLY | PERMANENT);

        return instance;
    }

    /**
     * Completes construction of a derived class instance after super() returns.
     *
     * @param instance the uninitialized instance (from super())
     * @param cx the current context
     * @param scope the current scope
     */
    public void completeConstruction(Scriptable instance, Context cx, Scriptable scope) {
        // Initialize instance fields
        initializeInstanceFields(instance, cx, scope);
    }

    @Override
    public String getClassName() {
        return CLASS_TAG;
    }

    @Override
    public String toString() {
        String name = getFunctionName();
        if (name == null || name.isEmpty()) {
            return "class { [native code] }";
        }
        return "class " + name + " { [native code] }";
    }
}
