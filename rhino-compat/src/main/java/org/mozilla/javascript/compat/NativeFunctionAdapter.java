/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.mozilla.javascript.compat;

import org.mozilla.javascript.*;
import org.mozilla.javascript.debug.DebuggableScript;

/**
 * JSFunction 到 NativeFunction 的适配器
 *
 * <p>让新版 JSFunction 可以伪装成旧版 NativeFunction， 以便与期望 NativeFunction 的旧代码兼容。
 *
 * <h2>使用场景</h2>
 *
 * <ul>
 *   <li>旧代码使用 instanceof NativeFunction 检查
 *   <li>旧代码调用 NativeFunction 特有方法
 * </ul>
 *
 * <h2>限制</h2>
 *
 * <ul>
 *   <li>仅支持单向包装：JSFunction → NativeFunctionAdapter
 *   <li>某些方法需要适配实现
 * </ul>
 *
 * @since 2.0.0
 */
public final class NativeFunctionAdapter extends NativeFunction {

    private static final long serialVersionUID = 1L;

    /** 委托的函数对象 */
    private final BaseFunction delegate;

    /** 语言版本缓存 */
    private final int languageVersion;

    /** 是否为严格模式 */
    private final boolean strict;

    /**
     * 私有构造函数
     */
    private NativeFunctionAdapter(BaseFunction delegate) {
        this.delegate = delegate;

        // 缓存属性 - 通过 JSDescriptor 访问（JSDescriptor 的方法是 public）
        if (delegate instanceof JSFunction) {
            JSDescriptor<?> desc = ((JSFunction) delegate).getDescriptor();
            this.languageVersion = desc.getLanguageVersion();
            this.strict = desc.isStrict();
        } else if (delegate instanceof NativeFunction) {
            // NativeFunction 的 getLanguageVersion() 是 protected
            // 子类可以访问，这里需要使用当前 Context 的版本
            Context cx = Context.getCurrentContext();
            this.languageVersion =
                    (cx != null) ? cx.getLanguageVersion() : Context.VERSION_DEFAULT;
            this.strict = false; // NativeFunction 没有 public 方法检测 strict
        } else {
            Context cx = Context.getCurrentContext();
            this.languageVersion =
                    (cx != null) ? cx.getLanguageVersion() : Context.VERSION_DEFAULT;
            this.strict = false;
        }
    }

    // ========== 工厂方法 ==========

    /**
     * 包装函数对象
     *
     * @param obj 要包装的对象
     * @return 如果是 JSFunction 返回适配器，否则原样返回
     */
    public static Object wrap(Object obj) {
        if (obj instanceof JSFunction) {
            return new NativeFunctionAdapter((JSFunction) obj);
        }
        return obj;
    }

    /**
     * 解包函数对象
     *
     * @param obj 可能被包装的函数对象
     * @return 原始函数对象
     */
    public static Object unwrap(Object obj) {
        if (obj instanceof NativeFunctionAdapter) {
            return ((NativeFunctionAdapter) obj).delegate;
        }
        return obj;
    }

    /**
     * 检查是否为适配器
     *
     * @param obj 要检查的对象
     * @return 如果是适配器返回 true
     */
    public static boolean isAdapter(Object obj) {
        return obj instanceof NativeFunctionAdapter;
    }

    /**
     * 获取原始函数对象
     *
     * @return 被委托的函数对象
     */
    public BaseFunction getDelegate() {
        return delegate;
    }

    // ========== 委托 BaseFunction 方法 ==========

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        return delegate.call(cx, scope, thisObj, args);
    }

    @Override
    public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
        return delegate.construct(cx, scope, args);
    }

    @Override
    public String getFunctionName() {
        return delegate.getFunctionName();
    }

    @Override
    public int getLength() {
        return delegate.getLength();
    }

    @Override
    public int getArity() {
        return delegate.getArity();
    }

    @Override
    public String getClassName() {
        return delegate.getClassName();
    }

    @Override
    public boolean hasInstance(Scriptable instance) {
        return delegate.hasInstance(instance);
    }

    @Override
    public Scriptable createObject(Context cx, Scriptable scope) {
        return delegate.createObject(cx, scope);
    }

    @Override
    public boolean isConstructor() {
        return delegate.isConstructor();
    }

    // ========== 委托 ScriptableObject 方法 ==========

    @Override
    public Scriptable getParentScope() {
        return delegate.getParentScope();
    }

    @Override
    public Scriptable getPrototype() {
        return delegate.getPrototype();
    }

    // ========== NativeFunction 抽象方法实现 ==========

    @Override
    protected int getLanguageVersion() {
        return languageVersion;
    }

    @Override
    protected int getParamCount() {
        return delegate.getLength();
    }

    @Override
    protected int getParamAndVarCount() {
        // JSFunction 的 getParamAndVarCount() 是 protected
        // 通过 JSDescriptor 访问（public 方法）
        if (delegate instanceof JSFunction) {
            return ((JSFunction) delegate).getDescriptor().getParamAndVarCount();
        }
        return delegate.getLength();
    }

    @Override
    protected String getParamOrVarName(int index) {
        if (delegate instanceof JSFunction) {
            return ((JSFunction) delegate).getDescriptor().getParamOrVarName(index);
        }
        // 降级：返回索引作为名称
        return "arg" + index;
    }

    @Override
    protected boolean getParamOrVarConst(int index) {
        if (delegate instanceof JSFunction) {
            return ((JSFunction) delegate).getDescriptor().getParamOrVarConst(index);
        }
        return false;
    }

    @Override
    public boolean isStrict() {
        return strict;
    }

    /**
     * 检查是否为生成器函数（供 RhinoCompat 使用）
     *
     * @return 如果是生成器函数返回 true
     */
    boolean isGeneratorFunctionAdapter() {
        if (delegate instanceof JSFunction) {
            return ((JSFunction) delegate).getDescriptor().isES6Generator();
        }
        return false;
    }

    // ========== 其他方法 ==========

    /**
     * 反编译函数源码
     *
     * <p>Rhino 2.0.0+ 中 BaseFunction.decompile() 是包私有的， 使用 Context.decompileFunction() 或 getRawSource() 替代
     *
     * <p>注意：此方法不能覆盖父类的包私有方法，仅作为辅助方法提供
     *
     * @param indent 缩进
     * @return 源码字符串
     */
    public String getDecompiledSource(int indent) {
        // 优先使用 getRawSource()
        String rawSource = getRawSource();
        if (rawSource != null) {
            return rawSource;
        }
        // 降级：使用 Context.decompileFunction
        Context cx = Context.getCurrentContext();
        if (cx != null) {
            return cx.decompileFunction(this, indent);
        }
        return "function " + getFunctionName() + "() { [native code] }";
    }

    @Override
    public String getRawSource() {
        if (delegate instanceof JSFunction) {
            return ((JSFunction) delegate).getRawSource();
        }
        if (delegate instanceof NativeFunction) {
            return ((NativeFunction) delegate).getRawSource();
        }
        return null;
    }

    @Override
    public DebuggableScript getDebuggableView() {
        if (delegate instanceof JSFunction) {
            return ((JSFunction) delegate).getDebuggableView();
        }
        if (delegate instanceof NativeFunction) {
            return ((NativeFunction) delegate).getDebuggableView();
        }
        return null;
    }

    @Override
    public Object resumeGenerator(
            Context cx, Scriptable scope, int operation, Object state, Object value) {
        if (delegate instanceof JSFunction) {
            return ((JSFunction) delegate).resumeGenerator(cx, scope, operation, state, value);
        }
        if (delegate instanceof NativeFunction) {
            return ((NativeFunction) delegate).resumeGenerator(cx, scope, operation, state, value);
        }
        throw new EvaluatorException("resumeGenerator() not supported");
    }
}