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
 * <h2>主要功能</h2>
 *
 * <ul>
 *   <li>E4X/XML 自动兼容初始化
 *   <li>函数类型统一检查
 *   <li>函数安全调用
 *   <li>类型转换适配
 * </ul>
 *
 * @since 2.0.0
 */
public final class RhinoCompat {

    private RhinoCompat() {} // 禁止实例化

    // ========== 初始化 ==========

    private static volatile boolean initialized = false;
    private static final Object INIT_LOCK = new Object();

    /**
     * 初始化 Rhino 环境（包含 E4X 支持）
     *
     * <p>兼容 Rhino 1.x 的初始化方式，自动处理 E4X XMLLib 注册
     *
     * @param cx 当前 Context
     * @param scope 作用域
     */
    public static void init(Context cx, Scriptable scope) {
        init(cx, scope, false);
    }

    /**
     * 初始化 Rhino 环境（包含 E4X 支持）
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

            // 初始化标准对象
            ScriptableObject.initStandardObjects(cx, scope, sealed);

            // 初始化 E4X 支持
            E4XCompat.init(cx, scope, sealed);

            initialized = true;
        }
    }

    // ========== 函数类型检查 ==========

    /**
     * 检查对象是否为 JavaScript 函数
     *
     * <p>兼容所有版本的函数类型：NativeFunction、JSFunction、BoundFunction、LambdaFunction
     *
     * @param obj 要检查的对象
     * @return 如果是 JavaScript 函数返回 true
     */
    public static boolean isFunction(Object obj) {
        return obj instanceof BaseFunction;
    }

    /**
     * 检查对象是否可调用
     *
     * <p>基于 Callable 接口，是最稳定的判断方式
     *
     * @param obj 要检查的对象
     * @return 如果可调用返回 true
     */
    public static boolean isCallable(Object obj) {
        return obj instanceof Callable;
    }

    /**
     * 检查是否为箭头函数
     *
     * @param obj 要检查的对象
     * @return 如果是箭头函数返回 true
     */
    public static boolean isArrowFunction(Object obj) {
        return FunctionCompat.isArrowFunction(obj);
    }

    /**
     * 检查是否为生成器函数
     *
     * @param obj 要检查的对象
     * @return 如果是生成器函数返回 true
     */
    public static boolean isGeneratorFunction(Object obj) {
        if (obj instanceof BaseFunction) {
            return ((BaseFunction) obj).isGeneratorFunction();
        }
        return false;
    }

    /**
     * 检查是否为绑定函数
     *
     * @param obj 要检查的对象
     * @return 如果是绑定函数返回 true
     */
    public static boolean isBoundFunction(Object obj) {
        return obj instanceof BoundFunction;
    }

    // ========== 函数调用 ==========

    /**
     * 安全调用函数
     *
     * <p>自动处理 Callable 和非 Callable 对象
     *
     * @param fn 函数对象
     * @param cx 当前 Context
     * @param scope 作用域
     * @param thisObj this 对象
     * @param args 参数数组
     * @return 调用结果
     * @throws RuntimeException 如果对象不可调用
     */
    public static Object call(
            Object fn, Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        if (fn instanceof Callable) {
            return ((Callable) fn).call(cx, scope, thisObj, args);
        }
        throw ScriptRuntime.notFunctionError(fn);
    }

    /**
     * 构造对象
     *
     * @param fn 构造函数
     * @param cx 当前 Context
     * @param scope 作用域
     * @param args 参数数组
     * @return 构造的对象
     */
    public static Scriptable construct(Object fn, Context cx, Scriptable scope, Object[] args) {
        if (fn instanceof Function) {
            return ((Function) fn).construct(cx, scope, args);
        }
        throw ScriptRuntime.notFunctionError(fn);
    }

    // ========== 函数信息 ==========

    /**
     * 获取函数参数个数
     *
     * @param fn 函数对象
     * @return 参数个数
     */
    public static int getParamCount(Object fn) {
        return FunctionCompat.getParamCount(fn);
    }

    /**
     * 获取函数名
     *
     * @param fn 函数对象
     * @return 函数名
     */
    public static String getFunctionName(Object fn) {
        return FunctionCompat.getFunctionName(fn);
    }

    // ========== 类型转换 ==========

    /**
     * 包装函数为兼容类型
     *
     * <p>将 JSFunction 包装为 NativeFunction 适配器
     *
     * @param fn 函数对象
     * @return 包装后的对象
     */
    public static Object wrapFunction(Object fn) {
        return NativeFunctionAdapter.wrap(fn);
    }

    /**
     * 解包函数对象
     *
     * @param fn 可能被包装的函数对象
     * @return 原始函数对象
     */
    public static Object unwrapFunction(Object fn) {
        return NativeFunctionAdapter.unwrap(fn);
    }
}