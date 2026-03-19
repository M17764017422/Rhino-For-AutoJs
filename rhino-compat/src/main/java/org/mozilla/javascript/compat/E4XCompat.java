/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.mozilla.javascript.compat;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.xmlimpl.XMLLibImpl;

/**
 * E4X 兼容层
 *
 * <p>自动检测并使用正确的 E4X 初始化方式。
 *
 * <p><b>注意</b>：Rhino 2.0.0+ 已统一使用 XMLLibImpl.init() 静态方法初始化，
 * 不再需要检测 ClassDescriptor API（仅 Rhino 1.7.x 需要区分处理）。
 *
 * @since 2.0.0
 */
final class E4XCompat {

    private E4XCompat() {} // 禁止实例化

    /**
     * 初始化 E4X 支持
     *
     * <p>直接委托给 XMLLibImpl.init() 静态方法，这是 Rhino 2.0.0+ 的标准初始化方式。
     *
     * @param cx 当前 Context
     * @param scope 作用域
     * @param sealed 是否封存
     */
    static void init(Context cx, Scriptable scope, boolean sealed) {
        try {
            // Rhino 2.0.0+: 使用 XMLLibImpl.init() 静态方法
            XMLLibImpl.init(cx, scope, sealed);
        } catch (Exception e) {
            // E4X 初始化失败不阻止脚本运行
            // 仅记录警告
            if (cx != null && cx.hasFeature(Context.FEATURE_WARNING_AS_ERROR)) {
                throw new RuntimeException("E4X initialization failed", e);
            }
            System.err.println("Warning: E4X initialization failed: " + e.getMessage());
        }
    }
}
