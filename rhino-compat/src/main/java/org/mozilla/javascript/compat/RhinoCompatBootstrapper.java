/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.mozilla.javascript.compat;

import org.mozilla.javascript.*;

/**
 * One-line migration bootstrap for Rhino 2.0.0+.
 *
 * <p>Call {@code RhinoCompatBootstrapper.bootstrap(cx, scope)} once at startup to enable all
 * compatibility features. This provides the closest experience to "one-line upgrade" from Rhino
 * 1.7.x.
 *
 * <h2>Usage</h2>
 *
 * <pre>
 * // In your initialization code, add ONE line:
 * RhinoCompatBootstrapper.bootstrap(cx, scope);
 *
 * // Now your old code works with Rhino 2.0.0+:
 * // - Java.extend is restored
 * // - E4X XML is initialized
 * </pre>
 *
 * @since 2.0.0
 */
public final class RhinoCompatBootstrapper {

    private static boolean bootstrapped = false;

    private RhinoCompatBootstrapper() {} // Prevent instantiation

    /**
     * Bootstrap all compatibility features.
     *
     * <p>Call this once after creating Context and scope. This will:
     *
     * <ul>
     *   <li>Inject {@code Java.extend} as global function
     *   <li>Initialize E4X XML support
     * </ul>
     *
     * @param cx the Rhino context
     * @param scope the top-level scope
     */
    public static synchronized void bootstrap(Context cx, Scriptable scope) {
        if (bootstrapped) {
            return;
        }

        // 1. Inject Java.extend as global function
        injectJavaExtend(cx, scope);

        // 2. Initialize E4X support
        E4XCompat.init(cx, scope, false);

        bootstrapped = true;
    }

    /**
     * Bootstrap with auto-detection of context.
     *
     * <p>Convenience method that gets current context automatically.
     *
     * @param scope the top-level scope
     */
    public static void bootstrap(Scriptable scope) {
        Context cx = Context.getCurrentContext();
        if (cx == null) {
            cx = Context.enter();
            try {
                bootstrap(cx, scope);
            } finally {
                Context.exit();
            }
        } else {
            bootstrap(cx, scope);
        }
    }

    /**
     * Bootstrap with minimal setup (only Java.extend).
     *
     * <p>Use this if you only need Java.extend and don't want other modifications.
     *
     * @param cx the Rhino context
     * @param scope the top-level scope
     */
    public static void bootstrapMinimal(Context cx, Scriptable scope) {
        injectJavaExtend(cx, scope);
    }

    /**
     * Check if bootstrap has been called.
     *
     * @return true if bootstrapped
     */
    public static boolean isBootstrapped() {
        return bootstrapped;
    }

    /**
     * Reset bootstrap state (for testing).
     */
    public static synchronized void reset() {
        bootstrapped = false;
    }

    // --- Internal implementation ---

    private static void injectJavaExtend(Context cx, Scriptable scope) {
        // Create a LambdaFunction for Java.extend
        var extendFunc = new LambdaFunction(
                scope,
                "extend",
                2,
                (SerializableCallable) (context, s, thisObj, args) -> {
                    if (args.length < 2) {
                        throw ScriptRuntime.typeErrorById("msg.function.arg1", "Java.extend");
                    }

                    // Parse arguments: Class(es) + implementation object
                    var classCount = 0;
                    for (int i = 0; i < args.length - 1; i++) {
                        if (args[i] instanceof NativeJavaClass || args[i] instanceof Class) {
                            classCount++;
                        } else {
                            break;
                        }
                    }

                    // Collect classes
                    Class<?>[] classes = new Class<?>[classCount];
                    for (int i = 0; i < classCount; i++) {
                        if (args[i] instanceof NativeJavaClass) {
                            classes[i] = ((NativeJavaClass) args[i]).getClassObject();
                        } else if (args[i] instanceof Class) {
                            classes[i] = (Class<?>) args[i];
                        }
                    }

                    // Implementation is last non-Class argument
                    Scriptable impl = ScriptableObject.ensureScriptable(args[classCount]);

                    // Use JavaExtendCompat
                    if (classCount == 1) {
                        return JavaExtendCompat.extend(context, s, classes[0], impl);
                    } else {
                        return JavaExtendCompat.extend(context, s, classes, impl);
                    }
                });

        // Inject as global function: extend(...)
        ScriptableObject.defineProperty(scope, "extend", extendFunc, ScriptableObject.DONTENUM);

        // Also create Java.extend syntax: Java.extend(...)
        var Java = ScriptableObject.getProperty(scope, "Java");
        if (Java == Scriptable.NOT_FOUND) {
            // Create Java object
            var javaObj = cx.newObject(scope);
            ScriptableObject.defineProperty(javaObj, "extend", extendFunc, ScriptableObject.DONTENUM);
            ScriptableObject.defineProperty(scope, "Java", javaObj, ScriptableObject.DONTENUM);
        } else if (Java instanceof Scriptable) {
            // Add extend to existing Java object
            ScriptableObject.defineProperty((Scriptable) Java, "extend", extendFunc, ScriptableObject.DONTENUM);
        }
    }
}
