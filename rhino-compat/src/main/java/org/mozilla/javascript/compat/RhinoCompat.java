/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.mozilla.javascript.compat;

import org.mozilla.javascript.*;

/**
 * Rhino 兼容层主入口
 *
 * <p>提供统一的兼容 API，屏蔽 Rhino 1.x 与 2.x 版本差异。
 *
 * <h2>一行代码迁移</h2>
 *
 * <pre>
 * // 初始化后，旧代码无需修改
 * RhinoCompat.init(cx, scope);
 *
 * // Java.extend 等旧 API 自动可用
 * var listener = Java.extend(OnClickListener, { ... });
 * </pre>
 *
 * @since 2.0.0
 */
public final class RhinoCompat {

    private RhinoCompat() {} // 禁止实例化

    // ========== 初始化 ==========

    private static volatile boolean initialized = false;
    private static final Object INIT_LOCK = new Object();

    /**
     * 初始化兼容层（包含 E4X 和 Java.extend）
     *
     * <p>调用此方法后，旧版 Rhino 1.x API 自动可用：
     *
     * <ul>
     *   <li>Java.extend(Interface, {...})
     *   <li>extend(Interface, {...})
     *   <li>E4X XML 支持
     * </ul>
     *
     * @param cx 当前 Context
     * @param scope 作用域
     */
    public static void init(Context cx, Scriptable scope) {
        init(cx, scope, false);
    }

    /**
     * 初始化兼容层（包含 E4X 和 Java.extend）
     *
     * @param cx 当前 Context
     * @param scope 作用域
     * @param sealed 是否封存对象
     */
    public static synchronized void init(Context cx, Scriptable scope, boolean sealed) {
        if (initialized) {
            return;
        }
        synchronized (INIT_LOCK) {
            if (initialized) {
                return;
            }

            // 1. 初始化标准对象
            if (scope instanceof ScriptableObject) {
                cx.initStandardObjects((ScriptableObject) scope, sealed);
            }

            // 2. 注入 Java.extend
            injectJavaExtend(cx, scope);

            // 3. 初始化 E4X 支持
            E4XCompat.init(cx, scope, sealed);

            initialized = true;
        }
    }

    /**
     * 便捷初始化（自动获取 Context）
     *
     * @param scope 作用域
     */
    public static void init(Scriptable scope) {
        Context cx = Context.getCurrentContext();
        if (cx == null) {
            cx = Context.enter();
            try {
                init(cx, scope);
            } finally {
                Context.exit();
            }
        } else {
            init(cx, scope);
        }
    }

    /** 检查是否已初始化 */
    public static boolean checkInitialized() {
        return initialized;
    }

    /** 重置状态（仅用于测试） */
    public static void reset() {
        initialized = false;
    }

    // ========== Java.extend 注入 ==========

    private static void injectJavaExtend(Context cx, Scriptable scope) {
        var extendFunc =
                new LambdaFunction(
                        scope,
                        "extend",
                        2,
                        (SerializableCallable)
                                (context, s, thisObj, args) -> {
                                    if (args.length < 2) {
                                        throw ScriptRuntime.typeErrorById(
                                                "msg.function.arg1", "Java.extend");
                                    }

                                    // 解析参数：Class(es) + implementation
                                    var classCount = 0;
                                    for (int i = 0; i < args.length - 1; i++) {
                                        if (args[i] instanceof NativeJavaClass
                                                || args[i] instanceof Class) {
                                            classCount++;
                                        } else {
                                            break;
                                        }
                                    }

                                    // 收集类
                                    Class<?>[] classes = new Class<?>[classCount];
                                    for (int i = 0; i < classCount; i++) {
                                        if (args[i] instanceof NativeJavaClass) {
                                            classes[i] =
                                                    ((NativeJavaClass) args[i]).getClassObject();
                                        } else if (args[i] instanceof Class) {
                                            classes[i] = (Class<?>) args[i];
                                        }
                                    }

                                    // 实现对象
                                    Scriptable impl =
                                            ScriptableObject.ensureScriptable(args[classCount]);

                                    // 调用 JavaExtendCompat
                                    if (classCount == 1) {
                                        return JavaExtendCompat.extend(
                                                context, s, classes[0], impl);
                                    } else {
                                        return JavaExtendCompat.extend(context, s, classes, impl);
                                    }
                                });

        // 注入全局函数: extend(...)
        ScriptableObject.defineProperty(scope, "extend", extendFunc, ScriptableObject.DONTENUM);

        // 注入 Java.extend: Java.extend(...)
        var Java = ScriptableObject.getProperty(scope, "Java");
        if (Java == Scriptable.NOT_FOUND) {
            var javaObj = cx.newObject(scope);
            ScriptableObject.defineProperty(
                    javaObj, "extend", extendFunc, ScriptableObject.DONTENUM);
            ScriptableObject.defineProperty(scope, "Java", javaObj, ScriptableObject.DONTENUM);
        } else if (Java instanceof Scriptable) {
            ScriptableObject.defineProperty(
                    (Scriptable) Java, "extend", extendFunc, ScriptableObject.DONTENUM);
        }
    }

    // ========== 函数类型检查 ==========

    /** 检查对象是否为 JavaScript 函数 */
    public static boolean checkFunction(Object obj) {
        return obj instanceof BaseFunction;
    }

    /** 检查对象是否可调用 */
    public static boolean checkCallable(Object obj) {
        return obj instanceof Callable;
    }

    /** 检查是否为箭头函数 */
    public static boolean checkArrowFunction(Object obj) {
        return FunctionCompat.isArrowFunction(obj);
    }

    /** 检查是否为生成器函数 */
    public static boolean checkGeneratorFunction(Object obj) {
        if (obj instanceof JSFunction) {
            return ((JSFunction) obj).getDescriptor().isES6Generator();
        }
        if (obj instanceof NativeFunctionAdapter) {
            return ((NativeFunctionAdapter) obj).isGeneratorFunctionAdapter();
        }
        return false;
    }

    /** 检查是否为绑定函数 */
    public static boolean checkBoundFunction(Object obj) {
        return obj instanceof BoundFunction;
    }

    // ========== 函数调用 ==========

    /** 安全调用函数 */
    public static Object call(
            Object fn, Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        if (fn instanceof Callable) {
            return ((Callable) fn).call(cx, scope, thisObj, args);
        }
        throw ScriptRuntime.notFunctionError(fn);
    }

    /** 构造对象 */
    public static Scriptable construct(Object fn, Context cx, Scriptable scope, Object[] args) {
        if (fn instanceof Function) {
            return ((Function) fn).construct(cx, scope, args);
        }
        throw ScriptRuntime.notFunctionError(fn);
    }

    // ========== 函数信息 ==========

    /** 获取函数参数个数 */
    public static int getParamCount(Object fn) {
        return FunctionCompat.getParamCount(fn);
    }

    /** 获取函数名 */
    public static String getFunctionName(Object fn) {
        return FunctionCompat.getFunctionName(fn);
    }

    // ========== 类型转换 ==========

    /** 包装函数为兼容类型 */
    public static Object wrapFunction(Object fn) {
        return NativeFunctionAdapter.wrap(fn);
    }

    /** 解包函数对象 */
    public static Object unwrapFunction(Object fn) {
        return NativeFunctionAdapter.unwrap(fn);
    }
}
