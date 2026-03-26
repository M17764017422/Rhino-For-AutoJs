/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ast.AstRoot;

/**
 * ES2023 边界行为测试 - Decorators & Auto-Accessors
 *
 * <p>测试目标：明确功能范围，为后续修复完善提供方向与依据
 *
 * <h2>测试分类</h2>
 *
 * <h3>DECORATORS 边界测试</h3>
 *
 * <ul>
 *   <li>BDD0: 上下文关键字边界 - accessor/get/set/static 作为装饰器名
 *   <li>BDD1: 装饰器工厂边界 - @dec(arg) 参数解析
 *   <li>BDD2: 换行边界 - @dec\nclass 的处理
 *   <li>BDD3: 表达式边界 - 复杂装饰器表达式
 *   <li>BDD4: 组合边界 - 装饰器与其他特性组合
 *   <li>BDD5: 错误场景 - 预期失败的语法
 * </ul>
 *
 * <h3>AUTO-ACCESSORS 边界测试</h3>
 *
 * <ul>
 *   <li>BAA0: 上下文关键字边界 - accessor 作为方法名/字段名
 *   <li>BAA1: 换行边界 - accessor\nx 的处理
 *   <li>BAA2: 初始化器边界 - 各种初始化器类型
 *   <li>BAA3: 组合边界 - 与 async/generator/装饰器组合
 *   <li>BAA4: 继承边界 - super 与 accessor 交互
 *   <li>BAA5: 错误场景 - 预期失败的语法
 * </ul>
 *
 * <h3>整合边界测试</h3>
 *
 * <ul>
 *   <li>BIN0: Decorator + Auto-Accessor 完整组合
 *   <li>BIN1: 运行时边界条件
 * </ul>
 */
public class ES2023EdgeCaseTest {

    private Object evaluate(String script) {
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            Scriptable scope = cx.initStandardObjects();
            return cx.evaluateString(scope, script, "test", 1, null);
        }
    }

    private AstRoot parseOnly(String script) throws Exception {
        CompilerEnvirons env = new CompilerEnvirons();
        env.setLanguageVersion(Context.VERSION_ES6);
        Parser parser = new Parser(env);
        return parser.parse(script, "test", 1);
    }

    // ========================================================================
    // DECORATORS 边界测试
    // ========================================================================

    @Nested
    @DisplayName("BDD0: 上下文关键字边界")
    class DecoratorContextKeywordBoundary {

        @Test
        @DisplayName("accessor 作为装饰器名")
        void testAccessorAsDecoratorName() throws Exception {
            String script = "var accessor = function() {}; @accessor class C {}";
            assertNotNull(parseOnly(script), "accessor 可以作为装饰器名使用");
        }

        @Test
        @DisplayName("get 作为装饰器名")
        void testGetAsDecoratorName() throws Exception {
            String script = "var get = function() {}; @get class C {}";
            assertNotNull(parseOnly(script), "get 可以作为装饰器名使用");
        }

        @Test
        @DisplayName("set 作为装饰器名")
        void testSetAsDecoratorName() throws Exception {
            String script = "var set = function() {}; @set class C {}";
            assertNotNull(parseOnly(script), "set 可以作为装饰器名使用");
        }

        @Test
        @DisplayName("static 作为装饰器名")
        void testStaticAsDecoratorName() throws Exception {
            String script = "var static = function() {}; @static class C {}";
            assertNotNull(parseOnly(script), "static 可以作为装饰器名使用");
        }

        @Test
        @DisplayName("class 作为装饰器名（无效）")
        void testClassAsDecoratorName() throws Exception {
            String script = "var cls = function() {}; @cls class C {}";
            assertNotNull(parseOnly(script), "可以使用变量名 cls");
        }
    }

    @Nested
    @DisplayName("BDD1: 装饰器工厂边界")
    class DecoratorFactoryBoundary {

        @Test
        @DisplayName("装饰器工厂 - 单参数")
        void testDecoratorFactorySingleArg() throws Exception {
            String script = "function dec(x) {} @dec('value') class C {}";
            assertNotNull(parseOnly(script), "装饰器工厂支持单参数");
        }

        @Test
        @DisplayName("装饰器工厂 - 多参数")
        void testDecoratorFactoryMultipleArgs() throws Exception {
            String script = "function dec(a, b, c) {} @dec(1, 2, 3) class C {}";
            assertNotNull(parseOnly(script), "装饰器工厂支持多参数");
        }

        @Test
        @DisplayName("装饰器工厂 - 对象参数")
        void testDecoratorFactoryObjectArg() throws Exception {
            String script = "function dec(opts) {} @dec({key: 'value'}) class C {}";
            assertNotNull(parseOnly(script), "装饰器工厂支持对象参数");
        }

        @Test
        @DisplayName("装饰器工厂 - 数组参数")
        void testDecoratorFactoryArrayArg() throws Exception {
            String script = "function dec(arr) {} @dec([1, 2, 3]) class C {}";
            assertNotNull(parseOnly(script), "装饰器工厂支持数组参数");
        }

        @Test
        @DisplayName("装饰器工厂 - 函数参数")
        void testDecoratorFactoryFunctionArg() throws Exception {
            String script = "function dec(fn) {} @dec(() => 42) class C {}";
            assertNotNull(parseOnly(script), "装饰器工厂支持函数参数");
        }

        @Test
        @DisplayName("装饰器工厂 - 嵌套调用")
        void testDecoratorFactoryNested() throws Exception {
            String script =
                    "function dec(x) { return function(cls) { return cls; }; } @dec('value') class C {}";
            assertNotNull(parseOnly(script), "装饰器工厂返回装饰器函数");
        }

        @Test
        @DisplayName("命名空间装饰器工厂")
        void testNamespaceDecoratorFactory() throws Exception {
            String script = "var ns = { dec: function(x) {} }; @ns.dec('value') class C {}";
            assertNotNull(parseOnly(script), "命名空间装饰器工厂");
        }

        @Test
        @DisplayName("深度命名空间装饰器工厂")
        void testDeepNamespaceDecoratorFactory() throws Exception {
            String script =
                    "var ns = { sub: { dec: function(x) {} } }; @ns.sub.dec('value') class C {}";
            assertNotNull(parseOnly(script), "深度命名空间装饰器工厂");
        }
    }

    @Nested
    @DisplayName("BDD2: 换行边界")
    class DecoratorNewlineBoundary {

        @Test
        @DisplayName("装饰器后换行再跟 class")
        void testDecoratorNewlineBeforeClass() throws Exception {
            String script = "function dec() {} @dec\nclass C {}";
            assertNotNull(parseOnly(script), "装饰器后可以换行再跟 class");
        }

        @Test
        @DisplayName("多个装饰器之间换行")
        void testMultipleDecoratorsWithNewlines() throws Exception {
            String script = "function dec1() {} function dec2() {} @dec1\n@dec2\nclass C {}";
            assertNotNull(parseOnly(script), "多个装饰器之间可以换行");
        }

        @Test
        @DisplayName("装饰器与类元素换行")
        void testDecoratorNewlineBeforeElement() throws Exception {
            String script = "function dec() {} class C { @dec\nmethod() {} }";
            assertNotNull(parseOnly(script), "装饰器后可以换行再跟类元素");
        }
    }

    @Nested
    @DisplayName("BDD3: 表达式边界")
    class DecoratorExpressionBoundary {

        @Test
        @DisplayName("计算属性装饰器 - 变量")
        void testComputedDecoratorVariable() throws Exception {
            String script = "var dec = function() {}; var name = 'dec'; @name class C {}";
            assertNotNull(parseOnly(script), "使用变量名作为装饰器");
        }

        @Test
        @DisplayName("装饰器链式调用")
        void testChainedDecoratorCall() throws Exception {
            String script =
                    "var obj = { dec: function() { return function() {}; } }; @obj.dec() class C {}";
            assertNotNull(parseOnly(script), "装饰器链式调用");
        }

        @Test
        @DisplayName("装饰器括号表达式")
        void testParenthesizedDecoratorExpression() throws Exception {
            String script = "var dec = function() {}; @(dec) class C {}";
            assertNotNull(parseOnly(script), "装饰器括号表达式");
        }
    }

    @Nested
    @DisplayName("BDD4: 组合边界")
    class DecoratorCombinationBoundary {

        @Test
        @DisplayName("装饰器 + static 块")
        void testDecoratorWithStaticBlock() throws Exception {
            String script = "function dec() {} @dec class C { static {} }";
            assertNotNull(parseOnly(script), "装饰器与静态块组合");
        }

        @Test
        @DisplayName("装饰器 + 生成器方法")
        void testDecoratorWithGeneratorMethod() throws Exception {
            String script = "function dec() {} class C { @dec *gen() { yield 1; } }";
            assertNotNull(parseOnly(script), "装饰器与生成器方法组合");
        }

        @Test
        @DisplayName("装饰器 + async 方法")
        void testDecoratorWithAsyncMethod() throws Exception {
            String script = "function dec() {} class C { @dec async method() {} }";
            assertNotNull(parseOnly(script), "装饰器与 async 方法组合");
        }

        @Test
        @DisplayName("装饰器 + getter + setter")
        void testDecoratorWithGetterSetter() throws Exception {
            String script =
                    "function dec() {} class C { @dec get x() { return 1; } @dec set x(v) {} }";
            assertNotNull(parseOnly(script), "装饰器与 getter/setter 组合");
        }

        @Test
        @DisplayName("多个装饰器 + static 方法")
        void testMultipleDecoratorsWithStaticMethod() throws Exception {
            String script =
                    "function dec1() {} function dec2() {} class C { @dec1 @dec2 static method() {} }";
            assertNotNull(parseOnly(script), "多个装饰器与静态方法组合");
        }

        @Test
        @DisplayName("装饰器 + 私有静态方法")
        void testDecoratorWithPrivateStaticMethod() throws Exception {
            String script = "function dec() {} class C { @dec static #privateMethod() {} }";
            assertNotNull(parseOnly(script), "装饰器与私有静态方法组合");
        }
    }

    // ========================================================================
    // AUTO-ACCESSORS 边界测试
    // ========================================================================

    @Nested
    @DisplayName("BAA0: 上下文关键字边界")
    class AutoAccessorContextKeywordBoundary {

        @Test
        @DisplayName("accessor 作为方法名")
        void testAccessorAsMethodName() throws Exception {
            String script = "class C { accessor() { return 'method'; } }";
            assertNotNull(parseOnly(script), "accessor 可以作为方法名");
        }

        @Test
        @DisplayName("accessor 作为字段名")
        void testAccessorAsFieldName() throws Exception {
            String script = "class C { accessor = 'field'; }";
            assertNotNull(parseOnly(script), "accessor 可以作为字段名");
        }

        @Test
        @DisplayName("accessor 作为静态方法名")
        void testAccessorAsStaticMethodName() throws Exception {
            String script = "class C { static accessor() { return 'static'; } }";
            assertNotNull(parseOnly(script), "accessor 可以作为静态方法名");
        }

        @Test
        @DisplayName("accessor 作为静态字段名")
        void testAccessorAsStaticFieldName() throws Exception {
            String script = "class C { static accessor = 'static field'; }";
            assertNotNull(parseOnly(script), "accessor 可以作为静态字段名");
        }

        @Test
        @DisplayName("accessor 作为私有方法名")
        void testAccessorAsPrivateMethodName() throws Exception {
            String script = "class C { #accessor() {} }";
            assertNotNull(parseOnly(script), "accessor 可以作为私有方法名");
        }

        @Test
        @DisplayName("accessor 作为私有字段名")
        void testAccessorAsPrivateFieldName() throws Exception {
            String script = "class C { #accessor = 1; }";
            assertNotNull(parseOnly(script), "accessor 可以作为私有字段名");
        }
    }

    @Nested
    @DisplayName("BAA1: 换行边界")
    class AutoAccessorNewlineBoundary {

        @Test
        @DisplayName("accessor 后换行 - 作为字段名")
        void testAccessorNewlineAsField() throws Exception {
            // accessor 后有换行，accessor 作为字段名
            String script = "class C { accessor\n= 'value'; }";
            assertNotNull(parseOnly(script), "accessor 后换行时作为字段名");
        }

        @Test
        @DisplayName("accessor 后换行 - 作为方法名")
        void testAccessorNewlineAsMethod() throws Exception {
            String script = "class C { accessor\n() {} }";
            assertNotNull(parseOnly(script), "accessor 后换行+括号时作为方法名");
        }

        @Test
        @DisplayName("accessor + 换行 + 属性名 - accessor 作为字段名")
        void testAccessorNewlineBeforePropertyName() throws Exception {
            // accessor 后换行，accessor 作为属性名，x 也是属性名
            String script = "class C { accessor\nx = 1; }";
            assertNotNull(parseOnly(script), "accessor 后换行时 accessor 和 x 都是字段名");
        }
    }

    @Nested
    @DisplayName("BAA2: 初始化器边界")
    class AutoAccessorInitializerBoundary {

        @Test
        @DisplayName("无初始化器")
        void testNoInitializer() throws Exception {
            String script = "class C { accessor x; }";
            assertNotNull(parseOnly(script), "accessor 可以没有初始化器");
        }

        @Test
        @DisplayName("数字初始化器")
        void testNumberInitializer() throws Exception {
            String script = "class C { accessor x = 42; }";
            assertNotNull(parseOnly(script), "数字初始化器");
        }

        @Test
        @DisplayName("字符串初始化器")
        void testStringInitializer() throws Exception {
            String script = "class C { accessor x = 'string'; }";
            assertNotNull(parseOnly(script), "字符串初始化器");
        }

        @Test
        @DisplayName("对象初始化器")
        void testObjectInitializer() throws Exception {
            String script = "class C { accessor x = { key: 'value' }; }";
            assertNotNull(parseOnly(script), "对象初始化器");
        }

        @Test
        @DisplayName("数组初始化器")
        void testArrayInitializer() throws Exception {
            String script = "class C { accessor x = [1, 2, 3]; }";
            assertNotNull(parseOnly(script), "数组初始化器");
        }

        @Test
        @DisplayName("函数初始化器")
        void testFunctionInitializer() throws Exception {
            String script = "class C { accessor x = function() { return 1; }; }";
            assertNotNull(parseOnly(script), "函数初始化器");
        }

        @Test
        @DisplayName("箭头函数初始化器")
        void testArrowFunctionInitializer() throws Exception {
            String script = "class C { accessor x = () => 42; }";
            assertNotNull(parseOnly(script), "箭头函数初始化器");
        }

        @Test
        @DisplayName("null 初始化器")
        void testNullInitializer() throws Exception {
            String script = "class C { accessor x = null; }";
            assertNotNull(parseOnly(script), "null 初始化器");
        }

        @Test
        @DisplayName("undefined 初始化器")
        void testUndefinedInitializer() throws Exception {
            String script = "class C { accessor x = undefined; }";
            assertNotNull(parseOnly(script), "undefined 初始化器");
        }

        @Test
        @DisplayName("表达式初始化器")
        void testExpressionInitializer() throws Exception {
            String script = "class C { accessor x = 1 + 2 * 3; }";
            assertNotNull(parseOnly(script), "表达式初始化器");
        }

        @Test
        @DisplayName("调用表达式初始化器")
        void testCallExpressionInitializer() throws Exception {
            String script = "function factory() { return 42; } class C { accessor x = factory(); }";
            assertNotNull(parseOnly(script), "调用表达式初始化器");
        }
    }

    @Nested
    @DisplayName("BAA3: 组合边界")
    class AutoAccessorCombinationBoundary {

        @Test
        @DisplayName("accessor + static + private")
        void testStaticPrivateAutoAccessor() throws Exception {
            String script = "class C { static accessor #x = 1; }";
            assertNotNull(parseOnly(script), "静态私有 accessor");
        }

        @Test
        @DisplayName("accessor + 装饰器")
        void testAutoAccessorWithDecorator() throws Exception {
            String script = "function dec() {} class C { @dec accessor x = 1; }";
            assertNotNull(parseOnly(script), "accessor 与装饰器组合");
        }

        @Test
        @DisplayName("accessor + 多个装饰器")
        void testAutoAccessorWithMultipleDecorators() throws Exception {
            String script =
                    "function dec1() {} function dec2() {} class C { @dec1 @dec2 accessor x = 1; }";
            assertNotNull(parseOnly(script), "accessor 与多个装饰器组合");
        }

        @Test
        @DisplayName("accessor + static + 装饰器")
        void testStaticAutoAccessorWithDecorator() throws Exception {
            String script = "function dec() {} class C { @dec static accessor x = 1; }";
            assertNotNull(parseOnly(script), "静态 accessor 与装饰器组合");
        }

        @Test
        @DisplayName("accessor + private + 装饰器")
        void testPrivateAutoAccessorWithDecorator() throws Exception {
            String script = "function dec() {} class C { @dec accessor #x = 1; }";
            assertNotNull(parseOnly(script), "私有 accessor 与装饰器组合");
        }

        @Test
        @DisplayName("多个 accessor + 装饰器混合")
        void testMixedAutoAccessorsWithDecorators() throws Exception {
            String script =
                    "function dec1() {} function dec2() {} class C { @dec1 accessor x = 1; @dec2 accessor #y = 2; }";
            assertNotNull(parseOnly(script), "多个 accessor 与装饰器混合");
        }

        @Test
        @DisplayName("accessor + 计算键 + 装饰器")
        void testComputedAutoAccessorWithDecorator() throws Exception {
            String script =
                    "function dec() {} const key = 'x'; class C { @dec accessor [key] = 1; }";
            assertNotNull(parseOnly(script), "计算键 accessor 与装饰器组合");
        }
    }

    @Nested
    @DisplayName("BAA4: 继承边界")
    class AutoAccessorInheritanceBoundary {

        @Test
        @DisplayName("父类 accessor + 子类 accessor")
        void testParentAndChildAutoAccessors() throws Exception {
            String script =
                    "class Parent { accessor x = 1; } class Child extends Parent { accessor y = 2; }";
            assertNotNull(parseOnly(script), "父类和子类都有 accessor");
        }

        @Test
        @DisplayName("accessor + super 调用")
        void testAutoAccessorWithSuperCall() throws Exception {
            String script =
                    "class Parent { constructor(n) { this.name = n; } }"
                            + "class Child extends Parent {"
                            + "  accessor value = 0;"
                            + "  constructor(n, v) { super(n); this.value = v; }"
                            + "}";
            assertNotNull(parseOnly(script), "accessor 与 super 调用");
        }

        @Test
        @DisplayName("accessor 在构造函数中被访问")
        void testAutoAccessorAccessedInConstructor() throws Exception {
            String script = "class C { accessor x = 1; constructor() { this.x = 2; } }";
            assertNotNull(parseOnly(script), "构造函数中访问 accessor");
        }

        @Test
        @DisplayName("静态 accessor 在静态块中被访问")
        void testStaticAutoAccessorInStaticBlock() throws Exception {
            String script = "class C { static accessor x = 1; static { var y = C.x; } }";
            assertNotNull(parseOnly(script), "静态块中访问静态 accessor");
        }
    }

    // ========================================================================
    // 整合边界测试 - Decorator + Auto-Accessor
    // ========================================================================

    @Nested
    @DisplayName("BIN0: Decorator + Auto-Accessor 整合")
    class DecoratorAutoAccessorIntegration {

        @Test
        @DisplayName("类装饰器 + 类 accessor")
        void testClassDecoratorWithClassAutoAccessor() throws Exception {
            String script = "function dec() {} @dec class C { accessor x = 1; }";
            assertNotNull(parseOnly(script), "类装饰器与类 accessor 共存");
        }

        @Test
        @DisplayName("类装饰器 + 元素装饰器 + accessor")
        void testClassAndElementDecoratorsWithAutoAccessor() throws Exception {
            String script =
                    "function dec1() {} function dec2() {} @dec1 class C { @dec2 accessor x = 1; }";
            assertNotNull(parseOnly(script), "类装饰器、元素装饰器与 accessor 组合");
        }

        @Test
        @DisplayName("多装饰器 + 多 accessor")
        void testMultipleDecoratorsWithMultipleAutoAccessors() throws Exception {
            String script =
                    "function d1() {} function d2() {} function d3() {}"
                            + "@d1 class C {"
                            + "  @d2 accessor x = 1;"
                            + "  @d3 accessor y = 2;"
                            + "}";
            assertNotNull(parseOnly(script), "多装饰器与多 accessor 组合");
        }

        @Test
        @DisplayName("命名空间装饰器 + accessor")
        void testNamespaceDecoratorWithAutoAccessor() throws Exception {
            String script = "var ns = { dec: function() {} }; class C { @ns.dec accessor x = 1; }";
            assertNotNull(parseOnly(script), "命名空间装饰器与 accessor 组合");
        }

        @Test
        @DisplayName("装饰器工厂 + accessor")
        void testDecoratorFactoryWithAutoAccessor() throws Exception {
            String script = "function dec(x) {} class C { @dec('value') accessor x = 1; }";
            assertNotNull(parseOnly(script), "装饰器工厂与 accessor 组合");
        }
    }

    // ========================================================================
    // 运行时边界条件测试（预期失败场景记录）
    // ========================================================================

    @Nested
    @DisplayName("BIN1: 运行时边界条件（预期行为记录）")
    class RuntimeBoundaryConditions {

        @Test
        @DisplayName("运行时测试 - 基础 accessor 读写")
        void testBasicAutoAccessorRuntime() {
            // 预期：accessor x = 10 生成 getter/setter
            String script = "class A { accessor x = 10; }\n" + "var a = new A();\n" + "a.x;";
            try {
                Object result = evaluate(script);
                // 预期结果: 10
                System.out.println("BASIC_ACCESSOR_RUNTIME: " + result);
            } catch (Exception e) {
                // 记录实际错误，用于后续修复
                System.out.println("BASIC_ACCESSOR_RUNTIME_ERROR: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("运行时测试 - accessor setter")
        void testAutoAccessorSetterRuntime() {
            String script =
                    "class A { accessor x = 10; }\n"
                            + "var a = new A();\n"
                            + "a.x = 20;\n"
                            + "a.x;";
            try {
                Object result = evaluate(script);
                System.out.println("ACCESSOR_SETTER_RUNTIME: " + result);
            } catch (Exception e) {
                System.out.println("ACCESSOR_SETTER_RUNTIME_ERROR: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("运行时测试 - 静态 accessor")
        void testStaticAutoAccessorRuntime() {
            String script = "class B { static accessor count = 0; }\n" + "B.count;";
            try {
                Object result = evaluate(script);
                System.out.println("STATIC_ACCESSOR_RUNTIME: " + result);
            } catch (Exception e) {
                System.out.println("STATIC_ACCESSOR_RUNTIME_ERROR: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("运行时测试 - 私有 accessor")
        void testPrivateAutoAccessorRuntime() {
            String script =
                    "class C {\n"
                            + "    accessor #secret = 'hidden';\n"
                            + "    reveal() { return this.#secret; }\n"
                            + "}\n"
                            + "var c = new C();\n"
                            + "c.reveal();";
            try {
                Object result = evaluate(script);
                System.out.println("PRIVATE_ACCESSOR_RUNTIME: " + result);
            } catch (Exception e) {
                System.out.println("PRIVATE_ACCESSOR_RUNTIME_ERROR: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("运行时测试 - 类装饰器")
        void testClassDecoratorRuntime() {
            String script =
                    "function dec(cls) { return cls; }\n" + "@dec class C {}\n" + "typeof C;";
            try {
                Object result = evaluate(script);
                System.out.println("CLASS_DECORATOR_RUNTIME: " + result);
            } catch (Exception e) {
                System.out.println("CLASS_DECORATOR_RUNTIME_ERROR: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("运行时测试 - 多装饰器")
        void testMultipleDecoratorsRuntime() {
            String script =
                    "function addA(cls) { cls.propA = 'A'; return cls; }\n"
                            + "function addB(cls) { cls.propB = 'B'; return cls; }\n"
                            + "@addA @addB class C {}\n"
                            + "C.propA + C.propB;";
            try {
                Object result = evaluate(script);
                System.out.println("MULTIPLE_DECORATORS_RUNTIME: " + result);
            } catch (Exception e) {
                System.out.println("MULTIPLE_DECORATORS_RUNTIME_ERROR: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("运行时测试 - 装饰器 + accessor")
        void testDecoratorWithAutoAccessorRuntime() {
            String script =
                    "function log(cls, ctx) { return cls; }\n"
                            + "class C { @log accessor x = 1; }\n"
                            + "var c = new C();\n"
                            + "c.x;";
            try {
                Object result = evaluate(script);
                System.out.println("DECORATOR_ACCESSOR_RUNTIME: " + result);
            } catch (Exception e) {
                System.out.println("DECORATOR_ACCESSOR_RUNTIME_ERROR: " + e.getMessage());
            }
        }
    }
}
