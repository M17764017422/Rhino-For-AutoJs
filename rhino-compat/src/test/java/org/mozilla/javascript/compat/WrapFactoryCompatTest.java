package org.mozilla.javascript.compat;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

/**
 * Tests for {@link WrapFactoryCompat}.
 *
 * <p>验证兼容层：
 *
 * <ul>
 *   <li>不会产生死循环
 *   <li>正确转换 TypeInfo 和 Class
 *   <li>子类可以重写 wrapCompat 方法
 * </ul>
 */
public class WrapFactoryCompatTest {

    /**
     * 验证 WrapFactoryCompat 不会产生死循环。
     *
     * <p>这是最重要的测试，确保兼容层可以正常工作。
     */
    @Test
    public void testNoInfiniteLoop() {
        try (Context cx = Context.enter()) {
            TestWrapFactory factory = new TestWrapFactory();
            factory.setJavaPrimitiveWrap(false);  // 禁用原始类型包装，使 String 直接返回
            cx.setWrapFactory(factory);

            Scriptable scope = cx.newObject(new ImporterTopLevel(cx));

            // 测试各种类型的包装
            Object result1 = factory.wrap(cx, scope, "test string", String.class);
            assertNotNull(result1);
            assertEquals("test string", result1);

            Object result2 = factory.wrap(cx, scope, Integer.valueOf(42), Integer.class);
            assertNotNull(result2);

            Object result3 = factory.wrap(cx, scope, new ArrayList<>(), List.class);
            assertNotNull(result3);

            // 验证没有死循环（如果死循环，测试不会到达这里）
            assertTrue("wrapCompat should have been called", factory.wrapCompatCalled);
        }
    }

    /** 测试 null 值处理。 */
    @Test
    public void testWrapNull() {
        try (Context cx = Context.enter()) {
            TestWrapFactory factory = new TestWrapFactory();
            cx.setWrapFactory(factory);

            Scriptable scope = cx.newObject(new ImporterTopLevel(cx));

            Object result = factory.wrap(cx, scope, null, Object.class);
            assertNull(result);
        }
    }

    /** 测试 undefined 值处理。 */
    @Test
    public void testWrapUndefined() {
        try (Context cx = Context.enter()) {
            TestWrapFactory factory = new TestWrapFactory();
            cx.setWrapFactory(factory);

            Scriptable scope = cx.newObject(new ImporterTopLevel(cx));

            Object result = factory.wrap(cx, scope, Undefined.instance, Object.class);
            assertSame(Undefined.instance, result);
        }
    }

    /** 测试 Scriptable 对象直接返回。 */
    @Test
    public void testWrapScriptable() {
        try (Context cx = Context.enter()) {
            TestWrapFactory factory = new TestWrapFactory();
            cx.setWrapFactory(factory);

            Scriptable scope = cx.newObject(new ImporterTopLevel(cx));
            NativeArray array = new NativeArray(new Object[] {1, 2, 3});

            Object result = factory.wrap(cx, scope, array, Object.class);
            assertSame(array, result);
        }
    }

    /** 测试原始类型包装。 */
    @Test
    public void testWrapPrimitive() {
        try (Context cx = Context.enter()) {
            TestWrapFactory factory = new TestWrapFactory();
            factory.setJavaPrimitiveWrap(false);
            cx.setWrapFactory(factory);

            Scriptable scope = cx.newObject(new ImporterTopLevel(cx));

            // String 应该直接返回
            Object result = factory.wrap(cx, scope, "hello", String.class);
            assertEquals("hello", result);

            // Boolean 应该直接返回
            result = factory.wrap(cx, scope, Boolean.TRUE, Boolean.class);
            assertEquals(Boolean.TRUE, result);

            // Integer 应该直接返回
            result = factory.wrap(cx, scope, Integer.valueOf(123), Integer.class);
            assertEquals(Integer.valueOf(123), result);
        }
    }

    /** 测试 List 包装为 NativeJavaList。 */
    @Test
    public void testWrapList() {
        try (Context cx = Context.enter()) {
            TestWrapFactory factory = new TestWrapFactory();
            cx.setWrapFactory(factory);

            Scriptable scope = cx.newObject(new ImporterTopLevel(cx));
            List<String> list = new ArrayList<>();
            list.add("test");

            Object result = factory.wrap(cx, scope, list, List.class);
            assertNotNull(result);
            assertTrue(result instanceof Scriptable);
        }
    }

    /** 测试 Map 包装为 NativeJavaMap。 */
    @Test
    public void testWrapMap() {
        try (Context cx = Context.enter()) {
            TestWrapFactory factory = new TestWrapFactory();
            cx.setWrapFactory(factory);

            Scriptable scope = cx.newObject(new ImporterTopLevel(cx));
            Map<String, String> map = new HashMap<>();
            map.put("key", "value");

            Object result = factory.wrap(cx, scope, map, Map.class);
            assertNotNull(result);
            assertTrue(result instanceof Scriptable);
        }
    }

    /** 测试数组包装为 NativeJavaArray。 */
    @Test
    public void testWrapArray() {
        try (Context cx = Context.enter()) {
            TestWrapFactory factory = new TestWrapFactory();
            cx.setWrapFactory(factory);

            Scriptable scope = cx.newObject(new ImporterTopLevel(cx));
            String[] array = new String[] {"a", "b", "c"};

            Object result = factory.wrap(cx, scope, array, String[].class);
            assertNotNull(result);
            assertTrue(result instanceof Scriptable);
        }
    }

    /** 测试普通 Java 对象包装。 */
    @Test
    public void testWrapJavaObject() {
        try (Context cx = Context.enter()) {
            TestWrapFactory factory = new TestWrapFactory();
            cx.setWrapFactory(factory);

            Scriptable scope = cx.newObject(new ImporterTopLevel(cx));
            TestObject obj = new TestObject("test", 42);

            Object result = factory.wrap(cx, scope, obj, TestObject.class);
            assertNotNull(result);
            assertTrue(result instanceof NativeJavaObject);
        }
    }

    /** 测试 wrapNewObject 方法。 */
    @Test
    public void testWrapNewObject() {
        try (Context cx = Context.enter()) {
            TestWrapFactory factory = new TestWrapFactory();
            cx.setWrapFactory(factory);

            Scriptable scope = cx.newObject(new ImporterTopLevel(cx));
            TestObject obj = new TestObject("new", 1);

            Scriptable result = factory.wrapNewObject(cx, scope, obj);
            assertNotNull(result);
            assertTrue(result instanceof NativeJavaObject);
        }
    }

    /** 测试子类可以自定义 wrapCompat 行为。 */
    @Test
    public void testCustomWrapCompat() {
        try (Context cx = Context.enter()) {
            CustomWrapFactory factory = new CustomWrapFactory();
            cx.setWrapFactory(factory);

            Scriptable scope = cx.newObject(new ImporterTopLevel(cx));

            // CustomWrapFactory 会将所有字符串转为大写
            Object result = factory.wrap(cx, scope, "hello", String.class);
            assertEquals("HELLO", result);
            assertTrue(factory.customWrapCalled);
        }
    }

    /** 测试子类可以自定义 wrapAsJavaObjectCompat 行为。 */
    @Test
    public void testCustomWrapAsJavaObjectCompat() {
        try (Context cx = Context.enter()) {
            CustomWrapFactory factory = new CustomWrapFactory();
            cx.setWrapFactory(factory);

            Scriptable scope = cx.newObject(new ImporterTopLevel(cx));
            TestObject obj = new TestObject("test", 42);

            Scriptable result = factory.wrapAsJavaObject(cx, scope, obj, TestObject.class);
            assertNotNull(result);
            assertTrue(factory.customWrapAsJavaObjectCalled);
        }
    }

    // ========== 测试用辅助类 ==========

    /** 简单的测试对象 */
    static class TestObject {
        final String name;
        final int value;

        TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    /** 基础测试 WrapFactory */
    static class TestWrapFactory extends WrapFactoryCompat {
        boolean wrapCompatCalled = false;

        @Override
        protected Object wrapCompat(Context cx, Scriptable scope, Object obj, Class<?> staticType) {
            wrapCompatCalled = true;
            return super.wrapCompat(cx, scope, obj, staticType);
        }
    }

    /** 自定义 WrapFactory，测试子类可以覆写方法 */
    static class CustomWrapFactory extends WrapFactoryCompat {
        boolean customWrapCalled = false;
        boolean customWrapAsJavaObjectCalled = false;

        @Override
        protected Object wrapCompat(Context cx, Scriptable scope, Object obj, Class<?> staticType) {
            customWrapCalled = true;
            // 自定义：字符串转大写
            if (obj instanceof String) {
                return ((String) obj).toUpperCase();
            }
            return super.wrapCompat(cx, scope, obj, staticType);
        }

        @Override
        protected Scriptable wrapAsJavaObjectCompat(
                Context cx, Scriptable scope, Object javaObject, Class<?> staticType) {
            customWrapAsJavaObjectCalled = true;
            return super.wrapAsJavaObjectCompat(cx, scope, javaObject, staticType);
        }
    }
}
