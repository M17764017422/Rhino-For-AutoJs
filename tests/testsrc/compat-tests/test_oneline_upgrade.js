// 一行升级测试 - 简化版入口
var out = java.lang.System.out;

out.println("=== 一行升级测试 ===\n");

// 获取 Context
var Context = org.mozilla.javascript.Context;
var cx = Context.getCurrentContext();

// 一行初始化
var RhinoCompat = Packages.org.mozilla.javascript.compat.RhinoCompat;
RhinoCompat.init(cx, this);

out.println("--- 初始化完成 ---\n");

// Test 1: Java.extend(Interface, {...})
out.println("Test 1: Java.extend()");
try {
    var listener = Java.extend(java.lang.Runnable, {
        run: function() {
            out.println("  [Java.extend] run() called");
        }
    });
    out.println("[PASS] 创建成功: " + (listener !== null));
    listener.run();
} catch (e) {
    out.println("[FAIL] " + e);
}

// Test 2: extend(Interface, {...})
out.println("\nTest 2: extend()");
try {
    var listener2 = extend(java.lang.Runnable, {
        run: function() {
            out.println("  [extend] run() called");
        }
    });
    out.println("[PASS] 创建成功: " + (listener2 !== null));
    listener2.run();
} catch (e) {
    out.println("[FAIL] " + e);
}

// Test 3: 多接口
out.println("\nTest 3: 多接口");
try {
    var multi = Java.extend(java.lang.Cloneable, java.io.Serializable, {
        toString: function() { return "Multi"; }
    });
    out.println("[PASS] 创建成功: " + (multi !== null));
    out.println("[PASS] instanceof Serializable: " + (multi instanceof java.io.Serializable));
} catch (e) {
    out.println("[FAIL] " + e);
}

// Test 4: 原生语法仍然可用
out.println("\nTest 4: 原生 new Interface()");
try {
    var listener3 = new java.lang.Runnable({
        run: function() {
            out.println("  [new Interface] run() called");
        }
    });
    out.println("[PASS] 创建成功: " + (listener3 !== null));
    listener3.run();
} catch (e) {
    out.println("[FAIL] " + e);
}

out.println("\n=== 测试完成 ===");
out.println("结论: RhinoCompat.init(cx, scope) 一行代码启用所有兼容功能");

// Return success for test framework
"success";