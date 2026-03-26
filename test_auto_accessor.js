// Test ES2023 Auto-Accessors

// Test 1: Basic auto-accessor
class A {
    accessor x = 10;
}
var a = new A();
console.log("Test 1: a.x = " + a.x); // Should be 10
a.x = 20;
console.log("Test 1: a.x after set = " + a.x); // Should be 20

// Test 2: Static auto-accessor
class B {
    static accessor count = 0;
}
console.log("Test 2: B.count = " + B.count); // Should be 0
B.count = 5;
console.log("Test 2: B.count after set = " + B.count); // Should be 5

// Test 3: Private auto-accessor
class C {
    accessor #secret = "hidden";
    
    reveal() {
        return this.#secret;
    }
}
var c = new C();
console.log("Test 3: c.reveal() = " + c.reveal()); // Should be "hidden"

// Test 4: Auto-accessor without initializer
class D {
    accessor value;
}
var d = new D();
console.log("Test 4: d.value = " + d.value); // Should be undefined
d.value = 42;
console.log("Test 4: d.value after set = " + d.value); // Should be 42

// Test 5: accessor as property name (not in accessor context)
class E {
    accessor() {
        return "method";
    }
}
var e = new E();
console.log("Test 5: e.accessor() = " + e.accessor()); // Should be "method"

console.log("All tests completed!");
