/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/**
 * Rhino 兼容层模块
 *
 * <p>提供统一的兼容 API，帮助下游项目从 Rhino 1.x 迁移到 2.x。
 *
 * <h2>主要组件</h2>
 *
 * <ul>
 *   <li>{@link org.mozilla.javascript.compat.WrapFactoryCompat} - WrapFactory 兼容基类，支持旧版
 *       Class&lt;?&gt; 签名
 *   <li>{@link org.mozilla.javascript.compat.RhinoCompat} - 主入口类，提供初始化和函数操作方法
 *   <li>{@link org.mozilla.javascript.compat.FunctionCompat} - 函数类型兼容工具
 *   <li>{@link org.mozilla.javascript.compat.NativeFunctionAdapter} - JSFunction 到 NativeFunction
 *       的适配器
 * </ul>
 *
 * <h2>迁移指南</h2>
 *
 * <h3>1. WrapFactory 迁移</h3>
 *
 * <pre>
 * // 旧代码
 * class MyWrapFactory extends WrapFactory {
 *     override fun wrap(..., staticType: Class&lt;?&gt;) { ... }
 * }
 *
 * // 新代码
 * class MyWrapFactory extends WrapFactoryCompat {
 *     override fun wrapCompat(..., staticType: Class&lt;?&gt;) { ... }
 * }
 * </pre>
 *
 * <h3>2. 函数类型检查迁移</h3>
 *
 * <pre>
 * // 旧代码
 * if (fn instanceof NativeFunction) { ... }
 *
 * // 新代码（推荐）
 * if (RhinoCompat.isFunction(fn)) { ... }
 * // 或者
 * if (FunctionCompat.isJavaScriptFunction(fn)) { ... }
 * </pre>
 *
 * @since 2.0.0
 */
package org.mozilla.javascript.compat;
