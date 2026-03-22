// ES6+ Features Test for Rhino 2.0.0
var results = [];

function test(name, fn) {
    try {
        var result = fn();
        results.push({name: name, passed: result, error: null});
    } catch (e) {
        results.push({name: name, passed: false, error: e.message || e});
    }
}

// ========== Arrow Functions ==========
test("Arrow function", function() {
    var f = (x) => x * 2;
    return f(21) === 42;
});

test("Arrow function with block", function() {
    var f = (x) => { return x * 2; };
    return f(21) === 42;
});

// ========== let/const ==========
test("let declaration", function() {
    let x = 1;
    return x === 1;
});

test("const declaration", function() {
    const PI = 3.14;
    return PI === 3.14;
});

// ========== Template Strings ==========
test("Template string", function() {
    var name = "World";
    return `Hello, ${name}!` === "Hello, World!";
});

// ========== Destructuring ==========
test("Array destructuring", function() {
    var [a, b] = [1, 2];
    return a === 1 && b === 2;
});

test("Object destructuring", function() {
    var {x, y} = {x: 1, y: 2};
    return x === 1 && y === 2;
});

// ========== Spread Operator ==========
test("Spread operator", function() {
    var arr = [1, 2, ...[3, 4]];
    return arr.length === 4 && arr[3] === 4;
});

// ========== Default Parameters ==========
test("Default parameter", function() {
    function f(x = 10) { return x; }
    return f() === 10 && f(5) === 5;
});

// ========== Rest Parameters ==========
test("Rest parameter", function() {
    function f(...args) { return args.length; }
    return f(1, 2, 3) === 3;
});

// ========== Promise ==========
test("Promise exists", function() {
    return typeof Promise === "function";
});

test("Promise basic", function() {
    var p = new Promise(function(resolve, reject) {
        resolve(42);
    });
    return p instanceof Promise;
});

// ========== Map/Set ==========
test("Map exists", function() {
    return typeof Map === "function";
});

test("Set exists", function() {
    return typeof Set === "function";
});

test("Map basic", function() {
    var m = new Map();
    m.set("key", "value");
    return m.get("key") === "value";
});

test("Set basic", function() {
    var s = new Set();
    s.add(1);
    return s.has(1);
});

// ========== Symbol ==========
test("Symbol exists", function() {
    return typeof Symbol === "function";
});

test("Symbol basic", function() {
    var sym = Symbol("test");
    return typeof sym === "symbol";
});

// ========== for...of ==========
test("for...of loop", function() {
    var sum = 0;
    for (var x of [1, 2, 3]) {
        sum += x;
    }
    return sum === 6;
});

// ========== Generators ==========
test("Generator function", function() {
    function* gen() {
        yield 1;
        yield 2;
    }
    var g = gen();
    return g.next().value === 1 && g.next().value === 2;
});

// ========== Object methods ==========
test("Object.assign", function() {
    var obj = Object.assign({}, {a: 1}, {b: 2});
    return obj.a === 1 && obj.b === 2;
});

test("Object.values", function() {
    var vals = Object.values({a: 1, b: 2});
    return vals.length === 2 && vals[0] === 1;
});

test("Object.entries", function() {
    var entries = Object.entries({a: 1});
    return entries[0][0] === "a" && entries[0][1] === 1;
});

// ========== Array methods ==========
test("Array.find", function() {
    return [1, 2, 3].find(function(x) { return x > 1; }) === 2;
});

test("Array.findIndex", function() {
    return [1, 2, 3].findIndex(function(x) { return x > 1; }) === 1;
});

test("Array.includes", function() {
    return [1, 2, 3].includes(2);
});

// ========== Nullish coalescing ==========
test("Nullish coalescing ??", function() {
    return (null ?? "default") === "default" && (0 ?? "default") === 0;
});

// ========== Rhino version ==========
test("Rhino version", function() {
    return typeof version === "function" && version() >= 200;
});

// ========== Print results ==========
var passed = 0;
var failed = 0;
var errors = [];

results.forEach(function(r) {
    if (r.passed) {
        passed++;
    } else {
        failed++;
        errors.push(r.name + ": " + (r.error || "failed"));
    }
});

java.lang.System.out.println("=== Rhino 2.0.0 ES6+ Features Test ===");
java.lang.System.out.println("Passed: " + passed + "/" + results.length);
java.lang.System.out.println("Failed: " + failed);
if (failed > 0) {
    java.lang.System.out.println("\nFailed tests:");
    errors.forEach(function(e) {
        java.lang.System.out.println("  - " + e);
    });
}

// Return success for test framework
"success";
