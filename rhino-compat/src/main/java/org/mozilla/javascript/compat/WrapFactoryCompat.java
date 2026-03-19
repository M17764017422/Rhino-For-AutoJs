/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.mozilla.javascript.compat;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.mozilla.javascript.*;
import org.mozilla.javascript.lc.type.TypeInfo;
import org.mozilla.javascript.lc.type.TypeInfoFactory;

/**
 * WrapFactory 兼容基类
 *
 * <p>让下游项目可以继续使用 Class&lt;?&gt; 参数签名，内部自动转换为 TypeInfo。
 *
 * <h2>调用链（重要）</h2>
 *
 * <pre>
 * caller.wrap(cx, scope, obj, Class)           [final - WrapFactory]
 *   → WrapFactory.wrap(cx, scope, obj, TypeInfo)  [覆写]
 *     → WrapFactoryCompat.wrap(TypeInfo)          [覆写，调用 wrapCompat]
 *       → wrapCompat(cx, scope, obj, Class)       [子类实现]
 * </pre>
 *
 * <h2>使用方式</h2>
 *
 * <pre>
 * // 旧代码
 * class MyWrapFactory extends WrapFactory {
 *     override fun wrap(..., staticType: Class&lt;?&gt;) { ... }
 * }
 *
 * // 新代码（只需改继承和方法名）
 * class MyWrapFactory extends WrapFactoryCompat {
 *     override fun wrapCompat(..., staticType: Class&lt;?&gt;) { ... }
 * }
 * </pre>
 *
 * @since 2.0.0
 * @see org.mozilla.javascript.WrapFactory
 */
public abstract class WrapFactoryCompat extends WrapFactory {

    // ========== wrap 方法链 ==========

    /**
     * 覆写 TypeInfo 版本，将 TypeInfo 转换为 Class 并调用 wrapCompat
     *
     * <p>⚠️ 子类不应重写此方法，应重写 wrapCompat
     */
    @Override
    public Object wrap(Context cx, Scriptable scope, Object obj, TypeInfo staticType) {
        Class<?> staticClass =
                (staticType != null && staticType != TypeInfo.NONE) ? staticType.asClass() : null;
        return wrapCompat(cx, scope, obj, staticClass);
    }

    /**
     * 子类重写此方法，使用 Class 参数（旧版 API 风格）
     *
     * <p>⚠️ 警告：不要调用 super.wrap(..., Class)，会导致死循环！
     *
     * <p>默认实现：复制自 WrapFactory.wrap() 核心逻辑
     *
     * @param cx 当前线程的 Context
     * @param scope 执行脚本的作用域
     * @param obj 要包装的对象，可以为 null
     * @param staticType 类型提示，用于改进泛型支持和回退类型
     * @return 包装后的值
     */
    protected Object wrapCompat(Context cx, Scriptable scope, Object obj, Class<?> staticType) {
        // === 复制自 WrapFactory.wrap() 核心逻辑 ===
        // 注意：不能调用 super.wrap(..., Class)，会死循环！

        if (obj == null || obj == Undefined.instance || obj instanceof Scriptable) {
            return obj;
        }

        // 处理原始类型
        if (staticType != null && staticType.isPrimitive()) {
            if (staticType == Void.TYPE) {
                return Undefined.instance;
            } else if (staticType == Character.TYPE) {
                return (int) (Character) obj;
            }
            return obj;
        }

        // 处理 Java 原始类型包装
        if (!isJavaPrimitiveWrap()) {
            if (obj instanceof String
                    || obj instanceof Boolean
                    || obj instanceof Integer
                    || obj instanceof Byte
                    || obj instanceof Short
                    || obj instanceof Long
                    || obj instanceof Float
                    || obj instanceof Double
                    || obj instanceof BigInteger) {
                return obj;
            } else if (obj instanceof Character) {
                return String.valueOf(((Character) obj).charValue());
            }
        }

        return wrapAsJavaObjectCompat(cx, scope, obj, staticType);
    }

    // ========== wrapAsJavaObject 方法链 ==========

    /**
     * 覆写 TypeInfo 版本，调用 wrapAsJavaObjectCompat
     *
     * <p>⚠️ 子类不应重写此方法，应重写 wrapAsJavaObjectCompat
     */
    @Override
    public Scriptable wrapAsJavaObject(
            Context cx, Scriptable scope, Object javaObject, TypeInfo staticType) {
        Class<?> staticClass =
                (staticType != null && staticType != TypeInfo.NONE) ? staticType.asClass() : null;
        return wrapAsJavaObjectCompat(cx, scope, javaObject, staticClass);
    }

    /**
     * 子类重写此方法，使用 Class 参数（旧版 API 风格）
     *
     * <p>默认实现：复制自 WrapFactory.wrapAsJavaObject() 核心逻辑
     *
     * @param cx 当前线程的 Context
     * @param scope 执行脚本的作用域
     * @param javaObject 要包装的 Java 对象
     * @param staticType 类型提示
     * @return 包装后的 Scriptable
     */
    protected Scriptable wrapAsJavaObjectCompat(
            Context cx, Scriptable scope, Object javaObject, Class<?> staticType) {
        // === 复制自 WrapFactory.wrapAsJavaObject() 核心逻辑 ===

        // 获取实际类型
        TypeInfoFactory factory = TypeInfoFactory.getOrElse(scope, TypeInfoFactory.GLOBAL);
        TypeInfo actualType = (staticType != null) ? factory.create(staticType) : TypeInfo.NONE;

        if (actualType.shouldReplace() && javaObject != null) {
            actualType = factory.create(javaObject.getClass());
            staticType = javaObject.getClass();
        }

        // 根据类型选择包装器
        if (staticType != null) {
            if (List.class.isAssignableFrom(staticType)) {
                return new NativeJavaList(scope, javaObject, actualType);
            } else if (Map.class.isAssignableFrom(staticType)) {
                return new NativeJavaMap(scope, javaObject, actualType);
            } else if (staticType.isArray()) {
                return new NativeJavaArray(scope, javaObject, actualType);
            }
        }

        return new NativeJavaObject(scope, javaObject, actualType);
    }

    // ========== wrapNewObject 保持不变 ==========

    /** wrapNewObject 通常不需要重写 如需自定义，重写此方法 */
    @Override
    public Scriptable wrapNewObject(Context cx, Scriptable scope, Object obj) {
        if (obj instanceof Scriptable) {
            return (Scriptable) obj;
        }
        return wrapAsJavaObjectCompat(cx, scope, obj, null);
    }
}
