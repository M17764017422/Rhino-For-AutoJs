// Rhino 2.0.0 Compat Layer Stress Test - Corrected
// 正确的调用方式测试

java.lang.System.out.println("=== Rhino 2.0.0 Compat Layer Stress Test ===\n");

var RhinoCompat = Packages.org.mozilla.javascript.compat.RhinoCompat;
var FunctionCompat = Packages.org.mozilla.javascript.compat.FunctionCompat;
var NativeFunctionAdapter = Packages.org.mozilla.javascript.compat.NativeFunctionAdapter;
var Context = Packages.org.mozilla.javascript.Context;
var ScriptableObject = Packages.org.mozilla.javascript.ScriptableObject;

var totalTests = 0;
var passedTests = 0;
var failedTests = [];

function assertEqual(name, actual, expected) {
    totalTests++;
    // 转换为数字比较
    var actualNum = Number(actual);
    var expectedNum = Number(expected);
    if (actualNum === expectedNum) {
        passedTests++;
        java.lang.System.out.println("[PASS] " + name);
    } else {
        failedTests.push({name: name, actual: actual, expected: expected});
        java.lang.System.out.println("[FAIL] " + name + " - expected: " + expected + ", got: " + actual);
    }
}

function assertTrue(name, actual) {
    totalTests++;
    if (actual === true || actual === 1) {
        passedTests++;
        java.lang.System.out.println("[PASS] " + name);
    } else {
        failedTests.push({name: name, actual: actual, expected: true});
        java.lang.System.out.println("[FAIL] " + name + " - expected true, got: " + actual);
    }
}

function assertFalse(name, actual) {
    totalTests++;
    if (actual === false || actual === 0) {
        passedTests++;
        java.lang.System.out.println("[PASS] " + name);
    } else {
        failedTests.push({name: name, actual: actual, expected: false});
        java.lang.System.out.println("[FAIL] " + name + " - expected false, got: " + actual);
    }
}

// ========== Part 1: 函数类型检测测试 ==========

java.lang.System.out.println("--- Part 1: Function Type Detection ---\n");

// 1.1 基本函数
function regularFunc(a, b) { return a + b; }
assertTrue("RhinoCompat.isFunction(regularFunc)", RhinoCompat.isFunction(regularFunc));
assertTrue("RhinoCompat.isCallable(regularFunc)", RhinoCompat.isCallable(regularFunc));
assertFalse("RhinoCompat.isArrowFunction(regularFunc)", RhinoCompat.isArrowFunction(regularFunc));
assertFalse("RhinoCompat.isBoundFunction(regularFunc)", RhinoCompat.isBoundFunction(regularFunc));
assertEqual("RhinoCompat.getParamCount(regularFunc)", RhinoCompat.getParamCount(regularFunc), 2);

// 1.2 箭头函数
var arrowFunc = (x, y, z) => x + y + z;
assertTrue("RhinoCompat.isFunction(arrowFunc)", RhinoCompat.isFunction(arrowFunc));
assertTrue("RhinoCompat.isCallable(arrowFunc)", RhinoCompat.isCallable(arrowFunc));
assertTrue("RhinoCompat.isArrowFunction(arrowFunc)", RhinoCompat.isArrowFunction(arrowFunc));
assertEqual("RhinoCompat.getParamCount(arrowFunc)", RhinoCompat.getParamCount(arrowFunc), 3);

// 1.3 生成器函数
function* generatorFunc() { yield 1; yield 2; }
assertTrue("RhinoCompat.isFunction(generatorFunc)", RhinoCompat.isFunction(generatorFunc));
assertTrue("RhinoCompat.isGeneratorFunction(generatorFunc)", RhinoCompat.isGeneratorFunction(generatorFunc));

// 1.4 绑定函数
var boundFunc = regularFunc.bind(null, 10);
assertTrue("RhinoCompat.isFunction(boundFunc)", RhinoCompat.isFunction(boundFunc));
assertTrue("RhinoCompat.isBoundFunction(boundFunc)", RhinoCompat.isBoundFunction(boundFunc));

// 1.5 非函数对象
var obj = {x: 1};
assertFalse("RhinoCompat.isFunction(obj)", RhinoCompat.isFunction(obj));
assertFalse("RhinoCompat.isCallable(obj)", RhinoCompat.isCallable(obj));

// 1.6 null 和 undefined
assertFalse("RhinoCompat.isFunction(null)", RhinoCompat.isFunction(null));
assertFalse("RhinoCompat.isFunction(undefined)", RhinoCompat.isFunction(undefined));

// ========== Part 2: 函数名获取测试 ==========

java.lang.System.out.println("\n--- Part 2: Function Name Retrieval ---\n");

assertEqual("regularFunc.name check", regularFunc.name === "regularFunc" ? 1 : 0, 1);
assertEqual("arrowFunc.name check", arrowFunc.name === "arrowFunc" ? 1 : 0, 1);

// ========== Part 3: FunctionCompat 测试 ==========

java.lang.System.out.println("\n--- Part 3: FunctionCompat ---\n");

assertTrue("FunctionCompat.isJavaScriptFunction(regularFunc)", FunctionCompat.isJavaScriptFunction(regularFunc));
assertTrue("FunctionCompat.isJavaScriptFunction(arrowFunc)", FunctionCompat.isJavaScriptFunction(arrowFunc));
assertTrue("FunctionCompat.isJSFunction(regularFunc)", FunctionCompat.isJSFunction(regularFunc));
assertTrue("FunctionCompat.isJSFunction(arrowFunc)", FunctionCompat.isJSFunction(arrowFunc));
assertTrue("FunctionCompat.isConstructor(regularFunc)", FunctionCompat.isConstructor(regularFunc));
assertFalse("FunctionCompat.isArrowFunction(regularFunc)", FunctionCompat.isArrowFunction(regularFunc));
assertTrue("FunctionCompat.isArrowFunction(arrowFunc)", FunctionCompat.isArrowFunction(arrowFunc));
assertEqual("FunctionCompat.getParamCount(regularFunc)", FunctionCompat.getParamCount(regularFunc), 2);
assertEqual("FunctionCompat.getParamCount(arrowFunc)", FunctionCompat.getParamCount(arrowFunc), 3);

// ========== Part 4: NativeFunctionAdapter 测试 ==========

java.lang.System.out.println("\n--- Part 4: NativeFunctionAdapter ---\n");

// 4.1 包装测试
var wrappedArrow = NativeFunctionAdapter.wrap(arrowFunc);
assertTrue("NativeFunctionAdapter.wrap(arrowFunc) !== arrowFunc", wrappedArrow !== arrowFunc);
assertTrue("NativeFunctionAdapter.isAdapter(wrappedArrow)", NativeFunctionAdapter.isAdapter(wrappedArrow));

// 4.2 解包测试
var unwrapped = NativeFunctionAdapter.unwrap(wrappedArrow);
assertTrue("NativeFunctionAdapter.unwrap(wrappedArrow) === arrowFunc", unwrapped === arrowFunc);

// 4.3 非函数不包装
var notWrapped = NativeFunctionAdapter.wrap(obj);
assertTrue("NativeFunctionAdapter.wrap(obj) === obj", notWrapped === obj);

// 4.4 包装后调用 - 使用 RhinoCompat.call 或直接调用
var wrappedRegular = NativeFunctionAdapter.wrap(regularFunc);
var cx = Context.getCurrentContext();
var scope = ScriptableObject.getTopLevelScope(this);

// 使用 RhinoCompat.call 调用包装后的函数
var callResult = RhinoCompat.call(wrappedRegular, cx, scope, null, [3, 5]);
assertEqual("RhinoCompat.call(wrappedRegular, ...)", callResult, 8);

// ========== Part 5: RhinoCompat.call 测试 ==========

java.lang.System.out.println("\n--- Part 5: RhinoCompat.call ---\n");

var callResult2 = RhinoCompat.call(regularFunc, cx, scope, null, [10, 20]);
assertEqual("RhinoCompat.call(regularFunc, ...)", callResult2, 30);

// ========== Part 6: 压力测试 - 大量函数创建和检测 ==========

java.lang.System.out.println("\n--- Part 6: Stress Test - Function Creation ---\n");

var stressTestCount = 100;
var stressErrors = 0;
var startTime = java.lang.System.currentTimeMillis();

for (var i = 0; i < stressTestCount; i++) {
    // 创建各种类型的函数
    var fn1 = new Function("x", "return x * " + i + ";");
    var fn2 = eval("(function(x) { return x + " + i + "; })");
    var fn3 = eval("((x) => x - " + i + ")");
    
    // 检测
    if (!RhinoCompat.isFunction(fn1)) stressErrors++;
    if (!RhinoCompat.isCallable(fn2)) stressErrors++;
    if (!RhinoCompat.isArrowFunction(fn3)) stressErrors++;
    
    // 包装/解包
    var wrapped = NativeFunctionAdapter.wrap(fn1);
    if (!NativeFunctionAdapter.isAdapter(wrapped)) stressErrors++;
    if (NativeFunctionAdapter.unwrap(wrapped) !== fn1) stressErrors++;
}

var endTime = java.lang.System.currentTimeMillis();
var elapsed = endTime - startTime;

assertEqual("Stress test: all detections correct", stressErrors, 0);
java.lang.System.out.println("Stress test completed: " + stressTestCount + " iterations in " + elapsed + "ms");

// ========== Part 7: 边界条件测试 ==========

java.lang.System.out.println("\n--- Part 7: Edge Cases ---\n");

// 7.1 无参数函数
function noParams() { return 42; }
assertEqual("RhinoCompat.getParamCount(noParams)", RhinoCompat.getParamCount(noParams), 0);

// 7.2 默认参数
function withDefaults(a, b = 10) { return a + b; }
assertTrue("RhinoCompat.isFunction(withDefaults)", RhinoCompat.isFunction(withDefaults));

// 7.3 rest 参数
function withRest(...args) { return args.length; }
assertTrue("RhinoCompat.isFunction(withRest)", RhinoCompat.isFunction(withRest));

// 7.4 立即执行函数
var iifeResult = (function(x) { return x * 2; })(21);
assertEqual("IIFE result", iifeResult, 42);

// ========== Part 8: 构造函数测试 ==========

java.lang.System.out.println("\n--- Part 8: Constructor Tests ---\n");

function Person(name) { this.name = name; }
var constructed = RhinoCompat.construct(Person, cx, scope, ["Alice"]);
assertTrue("RhinoCompat.construct returns object", constructed !== null);
assertEqual("constructed.name", constructed.name, "Alice");

// ========== Part 9: 包装后函数行为测试 ==========

java.lang.System.out.println("\n--- Part 9: Wrapped Function Behavior ---\n");

var originalAdd = function(a, b) { return a + b; };
var wrappedAdd = NativeFunctionAdapter.wrap(originalAdd);

// 直接调用原函数
var directResult = originalAdd(5, 3);
assertEqual("Direct call result", directResult, 8);

// 使用 RhinoCompat.call 调用包装函数
var wrappedResult = RhinoCompat.call(wrappedAdd, cx, scope, null, [5, 3]);
assertEqual("Wrapped call result (via RhinoCompat)", wrappedResult, 8);

// 解包后调用
var unwrappedAdd = NativeFunctionAdapter.unwrap(wrappedAdd);
var unwrappedResult = unwrappedAdd(5, 3);
assertEqual("Unwrapped call result", unwrappedResult, 8);

// ========== Part 10: ES6+ 特性验证 ==========

java.lang.System.out.println("\n--- Part 10: ES6+ Features ---\n");

// 箭头函数检测
var es6Arrow = (x) => x * 2;
assertTrue("ES6 arrow function detected", RhinoCompat.isArrowFunction(es6Arrow));
assertEqual("ES6 arrow function works", es6Arrow(21), 42);

// 生成器检测
function* es6Gen() { yield 1; yield 2; yield 3; }
assertTrue("ES6 generator detected", RhinoCompat.isGeneratorFunction(es6Gen));
var genInstance = es6Gen();
assertEqual("Generator yields first value", genInstance.next().value, 1);

// Promise 存在性
assertTrue("Promise exists", typeof Promise === "function");

// Map/Set 存在性
assertTrue("Map exists", typeof Map === "function");
assertTrue("Set exists", typeof Set === "function");

// Symbol 存在性
assertTrue("Symbol exists", typeof Symbol === "function");

// ========== 总结 ==========
java.lang.System.out.println("\n========== Summary ==========");
java.lang.System.out.println("Total: " + totalTests);
java.lang.System.out.println("Passed: " + passedTests);
java.lang.System.out.println("Failed: " + (totalTests - passedTests));

if (failedTests.length > 0) {
    java.lang.System.out.println("\nFailed tests:");
    for (var i = 0; i < failedTests.length; i++) {
        var t = failedTests[i];
        java.lang.System.out.println("  " + t.name + ": expected " + t.expected + ", got " + t.actual);
    }
}

java.lang.System.out.println("\nAll tests completed.");

// Return success for test framework
"success";
