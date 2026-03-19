/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.mozilla.javascript.compat;

import java.lang.reflect.Method;
import org.mozilla.javascript.*;

/**
 * Java.extend compatibility layer for Rhino 2.0.0+.
 *
 * <p>Rhino 2.0.0 removed the Java.extend API. This class provides a compatibility layer that uses
 * JavaAdapter internally.
 *
 * <h2>Usage comparison</h2>
 *
 * <pre>
 * // Rhino 1.7.x style (removed in 2.0.0)
 * var listener = Java.extend(OnClickListener, {
 *     onClick: function(view) { ... }
 * });
 *
 * // Rhino 2.0.0+ style
 * // Method 1: Direct interface implementation
 * var listener = new OnClickListener({
 *     onClick: function(view) { ... }
 * });
 *
 * // Method 2: Use JavaAdapter
 * var listener = new JavaAdapter(OnClickListener, {
 *     onClick: function(view) { ... }
 * });
 *
 * // Method 3: Use this compatibility layer
 * var listener = JavaExtendCompat.extend(OnClickListener, {
 *     onClick: function(view) { ... }
 * });
 * </pre>
 *
 * @since 2.0.0
 */
public final class JavaExtendCompat {

    private static final Method jsCreateAdapterMethod;

    static {
        Method m = null;
        try {
            m = JavaAdapter.class.getDeclaredMethod(
                    "js_createAdapter", Context.class, Scriptable.class, Object[].class);
            m.setAccessible(true);
        } catch (NoSuchMethodException e) {
            // Should not happen in Rhino 2.0.0+
        }
        jsCreateAdapterMethod = m;
    }

    private JavaExtendCompat() {} // Prevent instantiation

    /**
     * Simulates Java.extend behavior for a single interface.
     *
     * <p>Uses JavaAdapter internally.
     *
     * @param cx the current context
     * @param scope the scope
     * @param interfaceClass the interface to implement
     * @param implementation the implementation object
     * @return an object that implements the interface
     */
    public static Object extend(
            Context cx, Scriptable scope, Class<?> interfaceClass, Scriptable implementation) {
        return createAdapter(cx, scope, new Class<?>[] {interfaceClass}, implementation);
    }

    /**
     * Simulates Java.extend behavior for multiple interfaces.
     *
     * @param cx the current context
     * @param scope the scope
     * @param interfaces array of interfaces to implement
     * @param implementation the implementation object
     * @return an object that implements all interfaces
     */
    public static Object extend(
            Context cx, Scriptable scope, Class<?>[] interfaces, Scriptable implementation) {
        return createAdapter(cx, scope, interfaces, implementation);
    }

    /**
     * Simulates Java.extend behavior for extending a class and implementing interfaces.
     *
     * @param cx the current context
     * @param scope the scope
     * @param superClass the class to extend
     * @param interfaces array of interfaces to implement
     * @param implementation the implementation object
     * @return an object that extends the class and implements all interfaces
     */
    public static Object extend(
            Context cx,
            Scriptable scope,
            Class<?> superClass,
            Class<?>[] interfaces,
            Scriptable implementation) {
        Class<?>[] allClasses = new Class<?>[interfaces.length + 1];
        allClasses[0] = superClass;
        System.arraycopy(interfaces, 0, allClasses, 1, interfaces.length);
        return createAdapter(cx, scope, allClasses, implementation);
    }

    /**
     * Creates an adapter instance using JavaAdapter.
     *
     * <p>The args must contain NativeJavaClass objects followed by the implementation Scriptable.
     */
    private static Object createAdapter(
            Context cx, Scriptable scope, Class<?>[] classes, Scriptable implementation) {
        if (jsCreateAdapterMethod == null) {
            throw new IllegalStateException("JavaAdapter.js_createAdapter method not found");
        }

        // Build JavaAdapter arguments: NativeJavaClass objects + implementation
        Object[] args = new Object[classes.length + 1];
        for (int i = 0; i < classes.length; i++) {
            // Wrap Class as NativeJavaClass
            args[i] = new NativeJavaClass(scope, classes[i]);
        }
        args[classes.length] = implementation;

        // Call JavaAdapter.js_createAdapter via reflection
        try {
            return jsCreateAdapterMethod.invoke(null, cx, scope, args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create JavaAdapter", e);
        }
    }

    /**
     * Checks if Java.extend is available.
     *
     * @return false (removed in Rhino 2.0.0+)
     */
    public static boolean isJavaExtendAvailable() {
        return false;
    }

    /**
     * Checks if JavaAdapter is available.
     *
     * @return true (still supported in Rhino 2.0.0+)
     */
    public static boolean isJavaAdapterAvailable() {
        return true;
    }

    /**
     * Checks if interface constructor syntax is available.
     *
     * <p>Rhino 2.0.0+ supports new InterfaceName({...}) syntax.
     *
     * @return true
     */
    public static boolean isInterfaceConstructorAvailable() {
        return true;
    }

    /**
     * Gets the recommended approach for interface implementation.
     *
     * @return description of the recommended approach
     */
    public static String getRecommendedApproach() {
        return "Rhino 2.0.0+ recommends: new InterfaceName({...}) or new JavaAdapter(InterfaceName, {...})";
    }
}
