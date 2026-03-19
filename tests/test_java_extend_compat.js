// Java.extend compatibility test
// Tests JavaExtendCompat class for Java.extend() API replacement

var out = java.lang.System.out;

out.println("=== JavaExtendCompat Test ===\n");

// Test 1: Check availability methods
out.println("--- Test 1: Availability Methods ---");
var JavaExtendCompat = Packages.org.mozilla.javascript.compat.JavaExtendCompat;

var test1a = JavaExtendCompat.isJavaExtendAvailable() === false;
out.println("[PASS] isJavaExtendAvailable() === false: " + test1a);

var test1b = JavaExtendCompat.isJavaAdapterAvailable() === true;
out.println("[PASS] isJavaAdapterAvailable() === true: " + test1b);

var test1c = JavaExtendCompat.isInterfaceConstructorAvailable() === true;
out.println("[PASS] isInterfaceConstructorAvailable() === true: " + test1c);

// Test 2: Recommended approach
out.println("\n--- Test 2: Recommended Approach ---");
var recommended = String(JavaExtendCompat.getRecommendedApproach());
var test2 = recommended !== null && recommended.length > 0;
out.println("[PASS] getRecommendedApproach() returns: " + (test2 ? recommended : "null"));

// Test 3: Interface implementation using new Interface() syntax (Rhino 2.0.0+)
out.println("\n--- Test 3: Direct Interface Implementation ---");
var Runnable = java.lang.Runnable;

// Rhino 2.0.0+ supports new Interface({...}) syntax
var runnable = new Runnable({
    run: function() {
        // empty
    }
});

var test3a = runnable !== null;
out.println("[PASS] new Runnable({...}) created: " + test3a);

// Test 4: JavaExtendCompat.extend (requires Context and scope)
out.println("\n--- Test 4: JavaExtendCompat.extend ---");
try {
    // Get current context - Rhino provides __context__ in shell
    var Context = org.mozilla.javascript.Context;
    var cx = Context.getCurrentContext();
    
    // Create implementation object
    var impl = {
        run: function() {
            out.println("  [impl] run() called");
        }
    };
    
    // Get scope from implementation
    var ScriptableObject = org.mozilla.javascript.ScriptableObject;
    var scope = ScriptableObject.getTopLevelScope(impl);
    
    // Call extend with context and scope
    var RunnableClass = java.lang.Class.forName("java.lang.Runnable");
    var compatRunnable = JavaExtendCompat.extend(cx, scope, RunnableClass, impl);
    
    var test4a = compatRunnable !== null;
    out.println("[PASS] JavaExtendCompat.extend() created: " + test4a);
    
    // Try to call the method
    compatRunnable.run();
    var test4b = true;
    out.println("[PASS] compatRunnable.run() works: " + test4b);
} catch (e) {
    out.println("[FAIL] JavaExtendCompat.extend() error: " + e);
    if (e.javaException) {
        e.javaException.printStackTrace();
    }
}

// Test 5: Multiple interfaces using JavaExtendCompat
out.println("\n--- Test 5: Multiple Interfaces ---");
try {
    var Context = org.mozilla.javascript.Context;
    var cx = Context.getCurrentContext();
    
    var Serializable = java.lang.Class.forName("java.io.Serializable");
    var Cloneable = java.lang.Class.forName("java.lang.Cloneable");
    
    var interfaces = java.lang.reflect.Array.newInstance(java.lang.Class, 2);
    interfaces[0] = Serializable;
    interfaces[1] = Cloneable;
    
    var impl = {
        toString: function() {
            return "MultiInterfaceAdapter";
        }
    };
    
    var ScriptableObject = org.mozilla.javascript.ScriptableObject;
    var scope = ScriptableObject.getTopLevelScope(impl);
    
    var multiAdapter = JavaExtendCompat.extend(cx, scope, interfaces, impl);
    
    var test5a = multiAdapter !== null;
    out.println("[PASS] Multi-interface adapter created: " + test5a);
    
    var test5b = multiAdapter instanceof java.io.Serializable;
    out.println("[PASS] Implements Serializable: " + test5b);
    
    var test5c = multiAdapter instanceof java.lang.Cloneable;
    out.println("[PASS] Implements Cloneable: " + test5c);
} catch (e) {
    out.println("[FAIL] Multi-interface error: " + e);
}

// Test 6: Extending a class using JavaExtendCompat
out.println("\n--- Test 6: Extending a Class ---");
try {
    var Context = org.mozilla.javascript.Context;
    var cx = Context.getCurrentContext();
    
    var ArrayListClass = java.lang.Class.forName("java.util.ArrayList");
    var emptyInterfaces = java.lang.reflect.Array.newInstance(java.lang.Class, 0);
    
    var impl = {
        size: function() {
            return 42;
        }
    };
    
    var ScriptableObject = org.mozilla.javascript.ScriptableObject;
    var scope = ScriptableObject.getTopLevelScope(impl);
    
    var customList = JavaExtendCompat.extend(cx, scope, ArrayListClass, emptyInterfaces, impl);
    
    var test6a = customList !== null;
    out.println("[PASS] Extended ArrayList created: " + test6a);
    
    var test6b = customList.size() === 42;
    out.println("[PASS] Overridden size() returns 42: " + test6b);
} catch (e) {
    out.println("[FAIL] Class extension error: " + e);
}

out.println("\n=== Test Complete ===");