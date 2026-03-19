// 完整流程测试 - 上个版本的兼容层
var out = java.lang.System.out;

out.println("=== 完整流程测试 ===\n");

// 获取兼容层类
var JavaExtendCompat = Packages.org.mozilla.javascript.compat.JavaExtendCompat;
var Context = org.mozilla.javascript.Context;

// Test 1: 单接口实现
out.println("--- Test 1: 单接口实现 ---");
try {
    var cx = Context.getCurrentContext();
    var RunnableClass = java.lang.Class.forName("java.lang.Runnable");
    
    var listener = JavaExtendCompat.extend(cx, this, RunnableClass, {
        run: function() {
            out.println("  [listener1] run() called");
        }
    });
    
    out.println("[PASS] 单接口创建: " + (listener !== null));
    listener.run();
    out.println("[PASS] run() 执行成功");
} catch (e) {
    out.println("[FAIL] " + e);
}

// Test 2: 多接口实现
out.println("\n--- Test 2: 多接口实现 ---");
try {
    var cx = Context.getCurrentContext();
    var SerializableClass = java.lang.Class.forName("java.io.Serializable");
    var CloneableClass = java.lang.Class.forName("java.lang.Cloneable");
    
    var interfaces = java.lang.reflect.Array.newInstance(java.lang.Class, 2);
    interfaces[0] = SerializableClass;
    interfaces[1] = CloneableClass;
    
    var multi = JavaExtendCompat.extend(cx, this, interfaces, {
        toString: function() {
            return "MultiInterface";
        }
    });
    
    out.println("[PASS] 多接口创建: " + (multi !== null));
    out.println("[PASS] instanceof Serializable: " + (multi instanceof java.io.Serializable));
    out.println("[PASS] instanceof Cloneable: " + (multi instanceof java.lang.Cloneable));
} catch (e) {
    out.println("[FAIL] " + e);
}

// Test 3: 对比原生 new Interface() 语法
out.println("\n--- Test 3: 原生 new Interface() 语法 ---");
try {
    var listener2 = new java.lang.Runnable({
        run: function() {
            out.println("  [listener2] run() called");
        }
    });
    
    out.println("[PASS] 原生语法创建: " + (listener2 !== null));
    listener2.run();
    out.println("[PASS] 原生 run() 执行成功");
} catch (e) {
    out.println("[FAIL] " + e);
}

// Test 4: 检测可用性
out.println("\n--- Test 4: 兼容层状态 ---");
out.println("Java.extend 可用: " + JavaExtendCompat.isJavaExtendAvailable());
out.println("JavaAdapter 可用: " + JavaExtendCompat.isJavaAdapterAvailable());
out.println("接口构造器可用: " + JavaExtendCompat.isInterfaceConstructorAvailable());

out.println("\n=== 测试完成 ===");
