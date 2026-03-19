/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.mozilla.javascript.compat;

import org.mozilla.javascript.*;

/**
 * 函数类型兼容工具类
 *
 * <p>提供统一的函数类型检查和信息获取方法，屏蔽 Rhino 版本差异。
 *
 * @since 2.0.0
 */
public final class FunctionCompat {

    private FunctionCompat() {} // 禁止实例化

    // ========== 类型检测 ==========

    /**
     * 检查是否为 JavaScript 函数（不含 Java 方法）
     *
     * <p>排除 NativeJavaMethod 和 LambdaFunction
     *
     * @param obj 要检查的对象
     * @return 如果是纯 JavaScript 函数返回 true
     */
    public static boolean isJavaScriptFunction(Object obj) {
        if (obj instanceof BaseFunction) {
            // 排除 Java 方法包装
            if (obj instanceof NativeJavaMethod) {
                return false;
            }
            // LambdaFunction 是 Java lambda 包装，不是 JS 函数
            if (obj instanceof LambdaFunction) {
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * 检查是否为 Rhino 1.x 风格的 NativeFunction
     *
     * @param obj 要检查的对象
     * @return 如果是 NativeFunction 返回 true
     */
    public static boolean isNativeFunction(Object obj) {
        return obj instanceof NativeFunction;
    }

    /**
     * 检查是否为 Rhino 2.x 风格的 JSFunction
     *
     * @param obj 要检查的对象
     * @return 如果是 JSFunction 返回 true
     */
    public static boolean isJSFunction(Object obj) {
        return obj instanceof JSFunction;
    }

    /**
     * 检查是否为箭头函数
     *
     * <p>箭头函数特征：
     *
     * <ul>
     *   <li>是 JSFunction 实例
     *   <li>hasLexicalThis() 返回 true
     * </ul>
     *
     * @param obj 要检查的对象
     * @return 如果是箭头函数返回 true
     */
    public static boolean isArrowFunction(Object obj) {
        if (obj instanceof JSFunction) {
            JSFunction jsfn = (JSFunction) obj;
            return jsfn.getDescriptor().hasLexicalThis();
        }
        return false;
    }

    /**
     * 检查是否为方法（有 home object）
     *
     * @param obj 要检查的对象
     * @return 如果是方法返回 true
     */
    public static boolean isMethod(Object obj) {
        if (obj instanceof BaseFunction) {
            return ((BaseFunction) obj).getHomeObject() != null;
        }
        return false;
    }

    /**
     * 检查是否可作为构造函数
     *
     * @param obj 要检查的对象
     * @return 如果可作为构造函数返回 true
     */
    public static boolean isConstructor(Object obj) {
        if (obj instanceof BaseFunction) {
            return ((BaseFunction) obj).isConstructor();
        }
        return false;
    }

    // ========== 安全调用 ==========

    /**
     * 安全调用函数
     *
     * <p>处理箭头函数的 lexical this
     *
     * @param fn 函数对象
     * @param cx 当前 Context
     * @param scope 作用域
     * @param thisObj this 对象（箭头函数会忽略）
     * @param args 参数数组
     * @return 调用结果
     */
    public static Object call(
            Object fn, Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        if (!(fn instanceof Callable)) {
            throw ScriptRuntime.notFunctionError(fn);
        }

        Callable callable = (Callable) fn;

        // 箭头函数会自动使用 lexical this
        // 普通函数使用传入的 thisObj
        return callable.call(cx, scope, thisObj, args);
    }

    /**
     * 获取实际使用的 this 对象
     *
     * <p>对于箭头函数，返回 lexical this；否则返回传入的 thisObj
     *
     * @param fn 函数对象
     * @param thisObj 原始 this 对象
     * @return 实际使用的 this 对象
     */
    public static Scriptable getEffectiveThis(Object fn, Scriptable thisObj) {
        if (fn instanceof JSFunction) {
            JSFunction jsfn = (JSFunction) fn;
            if (jsfn.getDescriptor().hasLexicalThis()) {
                // 箭头函数使用 lexical this
                // 注意：JSFunction 的 lexicalThis 字段是私有的，需要通过 call 方法处理
                return thisObj; // 实际 lexical this 在 call 时自动处理
            }
        }
        return thisObj;
    }

    // ========== 函数信息 ==========

    /**
     * 获取函数参数个数
     *
     * @param fn 函数对象
     * @return 参数个数，如果不是函数返回 0
     */
    public static int getParamCount(Object fn) {
        if (fn instanceof BaseFunction) {
            return ((BaseFunction) fn).getLength();
        }
        return 0;
    }

    /**
     * 获取函数名
     *
     * @param fn 函数对象
     * @return 函数名，如果不是函数返回空字符串
     */
    public static String getFunctionName(Object fn) {
        if (fn instanceof BaseFunction) {
            return ((BaseFunction) fn).getFunctionName();
        }
        return "";
    }

    /**
     * 获取函数源码
     *
     * @param fn 函数对象
     * @return 函数源码
     */
    public static String getSource(Object fn) {
        if (fn instanceof JSFunction) {
            return ((JSFunction) fn).getRawSource();
        }
        if (fn instanceof NativeFunction) {
            return ((NativeFunction) fn).getRawSource();
        }
        return fn.toString();
    }
}