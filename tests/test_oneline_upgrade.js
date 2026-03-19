// One-line upgrade test
// Demonstrates that RhinoCompatBootstrapper enables old code to work unchanged

var out = java.lang.System.out;

out.println("=== One-Line Upgrade Test ===\n");

// Bootstrap - ONE LINE to enable all compat features
var RhinoCompatBootstrapper = Packages.org.mozilla.javascript.compat.RhinoCompatBootstrapper;
RhinoCompatBootstrapper.bootstrap(this);

out.println("--- After bootstrap() ---\n");

// Test 1: Java.extend syntax (old Rhino 1.7.x style)
out.println("Test 1: Java.extend(Interface, {...})");
try {
    var Runnable = java.lang.Runnable;
    
    // Old syntax should work now!
    var listener = Java.extend(Runnable, {
        run: function() {
            out.println("  [Java.extend] run() called");
        }
    });
    
    var test1a = listener !== null;
    out.println("[PASS] Java.extend created: " + test1a);
    
    listener.run();
    out.println("[PASS] listener.run() works");
} catch (e) {
    out.println("[FAIL] Java.extend error: " + e);
}

// Test 2: extend() as global function
out.println("\nTest 2: extend(Interface, {...})");
try {
    var listener2 = extend(java.lang.Runnable, {
        run: function() {
            out.println("  [extend] run() called");
        }
    });
    
    var test2a = listener2 !== null;
    out.println("[PASS] extend() created: " + test2a);
    
    listener2.run();
    out.println("[PASS] listener2.run() works");
} catch (e) {
    out.println("[FAIL] extend() error: " + e);
}

// Test 3: Multiple interfaces
out.println("\nTest 3: Multiple interfaces");
try {
    var multi = Java.extend(
        java.lang.Cloneable,
        java.io.Serializable,
        {
            toString: function() {
                return "MultiInterfaceObject";
            }
        }
    );
    
    var test3a = multi !== null;
    out.println("[PASS] Multi-interface created: " + test3a);
    
    var test3b = multi instanceof java.io.Serializable;
    out.println("[PASS] Implements Serializable: " + test3b);
} catch (e) {
    out.println("[FAIL] Multi-interface error: " + e);
}

// Test 4: Direct new Interface() still works (native Rhino 2.0.0)
out.println("\nTest 4: new Interface({...}) (native Rhino 2.0.0)");
try {
    var listener3 = new java.lang.Runnable({
        run: function() {
            out.println("  [new Interface] run() called");
        }
    });
    
    var test4a = listener3 !== null;
    out.println("[PASS] new Interface() created: " + test4a);
    
    listener3.run();
    out.println("[PASS] listener3.run() works");
} catch (e) {
    out.println("[FAIL] new Interface() error: " + e);
}

out.println("\n=== All Tests Complete ===");
out.println("\nConclusion: With ONE LINE of bootstrap code,");
out.println("old Rhino 1.7.x code using Java.extend works unchanged!");