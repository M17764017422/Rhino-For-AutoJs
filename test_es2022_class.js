/**
 * ES2022 Class Features Test Suite for Rhino
 * Direct class syntax test (no eval)
 */

// Test 1: Basic class declaration
print("Test 1: Basic class declaration");
class Test1 {}
print("  typeof Test1: " + typeof Test1);
print("  Result: " + (typeof Test1 === 'function' ? "PASS" : "FAIL"));

// Test 2: Class with constructor
print("\nTest 2: Class with constructor");
class Test2 {
    constructor(x) {
        this.x = x;
    }
}
var t2 = new Test2(42);
print("  t2.x: " + t2.x);
print("  Result: " + (t2.x === 42 ? "PASS" : "FAIL"));

// Test 3: Class with methods
print("\nTest 3: Class with methods");
class Test3 {
    m() {
        return 'method';
    }
}
var t3 = new Test3();
print("  t3.m(): " + t3.m());
print("  Result: " + (t3.m() === 'method' ? "PASS" : "FAIL"));

// Test 4: Class with getter/setter
print("\nTest 4: Class with getter/setter");
class Test4 {
    get x() {
        return this._x;
    }
    set x(v) {
        this._x = v;
    }
}
var t4 = new Test4();
t4.x = 10;
print("  t4.x: " + t4.x);
print("  Result: " + (t4.x === 10 ? "PASS" : "FAIL"));

// Test 5: Class extends
print("\nTest 5: Class extends");
class Test5Base {
    constructor() {
        this.a = 1;
    }
}
class Test5Derived extends Test5Base {
    constructor() {
        super();
        this.b = 2;
    }
}
var t5 = new Test5Derived();
print("  t5.a: " + t5.a + ", t5.b: " + t5.b);
print("  Result: " + (t5.a === 1 && t5.b === 2 ? "PASS" : "FAIL"));

// Test 6: Class expression
print("\nTest 6: Class expression");
var Test6 = class {
    m() {
        return 1;
    }
};
var t6 = new Test6();
print("  t6.m(): " + t6.m());
print("  Result: " + (t6.m() === 1 ? "PASS" : "FAIL"));

// Test 7: Named class expression
print("\nTest 7: Named class expression");
var Test7 = class Named {
    m() {
        return 'named';
    }
};
var t7 = new Test7();
print("  t7.m(): " + t7.m());
print("  Result: " + (t7.m() === 'named' ? "PASS" : "FAIL"));

// Test 8: Private field declaration
print("\nTest 8: Private field declaration");
class Test8 {
    #x = 1;
    getX() {
        return this.#x;
    }
}
var t8 = new Test8();
print("  t8.getX(): " + t8.getX());
print("  Result: " + (t8.getX() === 1 ? "PASS" : "FAIL"));

// Test 9: Private field assignment
print("\nTest 9: Private field assignment");
class Test9 {
    #x = 0;
    setX(v) {
        this.#x = v;
    }
    getX() {
        return this.#x;
    }
}
var t9 = new Test9();
t9.setX(42);
print("  t9.getX(): " + t9.getX());
print("  Result: " + (t9.getX() === 42 ? "PASS" : "FAIL"));

// Test 10: Private method
print("\nTest 10: Private method");
class Test10 {
    #m() {
        return 'private';
    }
    callM() {
        return this.#m();
    }
}
var t10 = new Test10();
print("  t10.callM(): " + t10.callM());
print("  Result: " + (t10.callM() === 'private' ? "PASS" : "FAIL"));

// Test 11: Private getter/setter
print("\nTest 11: Private getter/setter");
class Test11 {
    get #x() {
        return this._x * 2;
    }
    set #x(v) {
        this._x = v;
    }
    setX(v) {
        this.#x = v;
    }
    getX() {
        return this.#x;
    }
}
var t11 = new Test11();
t11.setX(5);
print("  t11.getX(): " + t11.getX());
print("  Result: " + (t11.getX() === 10 ? "PASS" : "FAIL"));

// Test 12: Private static field
print("\nTest 12: Private static field");
class Test12 {
    static #count = 0;
    static inc() {
        return ++Test12.#count;
    }
}
print("  Test12.inc(): " + Test12.inc() + ", " + Test12.inc());
print("  Result: " + (Test12.inc() === 3 ? "PASS" : "FAIL"));

// Test 13: Private static method
print("\nTest 13: Private static method");
class Test13 {
    static #secret = 'hidden';
    static getSecret() {
        return Test13.#secret;
    }
}
print("  Test13.getSecret(): " + Test13.getSecret());
print("  Result: " + (Test13.getSecret() === 'hidden' ? "PASS" : "FAIL"));

// Test 14: Static method
print("\nTest 14: Static method");
class Test14 {
    static sm() {
        return 'static';
    }
}
print("  Test14.sm(): " + Test14.sm());
print("  Result: " + (Test14.sm() === 'static' ? "PASS" : "FAIL"));

// Test 15: Static field
print("\nTest 15: Static field");
class Test15 {
    static count = 0;
}
print("  Test15.count: " + Test15.count);
print("  Result: " + (Test15.count === 0 ? "PASS" : "FAIL"));

// Test 16: Static field initialization
print("\nTest 16: Static field initialization");
class Test16 {
    static arr = [1, 2, 3];
}
print("  Test16.arr.length: " + Test16.arr.length);
print("  Result: " + (Test16.arr.length === 3 ? "PASS" : "FAIL"));

// Test 17: Static initialization block
print("\nTest 17: Static initialization block");
class Test17 {
    static x;
    static {
        Test17.x = 42;
    }
}
print("  Test17.x: " + Test17.x);
print("  Result: " + (Test17.x === 42 ? "PASS" : "FAIL"));

// Test 18: Static block with complex logic
print("\nTest 18: Static block with complex logic");
class Test18 {
    static arr = [];
    static {
        for (var i = 0; i < 3; i++) {
            Test18.arr.push(i);
        }
    }
}
print("  Test18.arr: " + Test18.arr.join(','));
print("  Result: " + (Test18.arr.join(',') === '0,1,2' ? "PASS" : "FAIL"));

// Test 19: Multiple static blocks
print("\nTest 19: Multiple static blocks");
class Test19 {
    static {
        Test19.a = 1;
    }
    static {
        Test19.b = 2;
    }
}
print("  Test19.a: " + Test19.a + ", Test19.b: " + Test19.b);
print("  Result: " + (Test19.a === 1 && Test19.b === 2 ? "PASS" : "FAIL"));

// Test 20: Static field and block order (definition order)
print("\nTest 20: Static field and block order");
class Test20 {
    static log = [];
    static {
        Test20.log.push('block1');
    }
    static field = (Test20.log.push('field'), 'value');
    static {
        Test20.log.push('block2');
    }
}
print("  Test20.log: " + Test20.log.join(','));
print("  Result: " + (Test20.log.join(',') === 'block1,field,block2' ? "PASS" : "FAIL"));

// Test 21: Instance field with default value
print("\nTest 21: Instance field with default value");
class Test21 {
    x = 10;
}
var t21 = new Test21();
print("  t21.x: " + t21.x);
print("  Result: " + (t21.x === 10 ? "PASS" : "FAIL"));

// Test 22: Instance field with expression
print("\nTest 22: Instance field with expression");
class Test22 {
    arr = [1, 2, 3];
}
var t22 = new Test22();
print("  t22.arr.length: " + t22.arr.length);
print("  Result: " + (t22.arr.length === 3 ? "PASS" : "FAIL"));

// Test 23: Instance field with this reference
print("\nTest 23: Instance field with this reference");
class Test23 {
    name = 'test';
    greeting = 'Hello ' + this.name;
}
var t23 = new Test23();
print("  t23.greeting: " + t23.greeting);
print("  Result: " + (t23.greeting === 'Hello test' ? "PASS" : "FAIL"));

// Test 24: Instance field in subclass
print("\nTest 24: Instance field in subclass");
class Test24Base {
    x = 1;
}
class Test24Derived extends Test24Base {
    y = 2;
}
var t24 = new Test24Derived();
print("  t24.x: " + t24.x + ", t24.y: " + t24.y);
print("  Result: " + (t24.x === 1 && t24.y === 2 ? "PASS" : "FAIL"));

// Test 25: Constructor and field initialization order
print("\nTest 25: Constructor and field initialization order");
class Test25 {
    x = 10;
    constructor() {
        this.x = this.x * 2;
    }
}
var t25 = new Test25();
print("  t25.x: " + t25.x);
print("  Result: " + (t25.x === 20 ? "PASS" : "FAIL"));

// Test 26: Full class with all features
print("\nTest 26: Full class with all features");
class Test26 {
    #priv = 1;
    pub = 2;
    static #sPriv = 3;
    static sPub = 4;
    
    getPriv() {
        return this.#priv;
    }
    setPriv(v) {
        this.#priv = v;
    }
    
    static getSPriv() {
        return Test26.#sPriv;
    }
    
    static {
        Test26.sPub = Test26.sPub * 2;
    }
}
var t26 = new Test26();
print("  t26.getPriv(): " + t26.getPriv() + ", t26.pub: " + t26.pub);
print("  Test26.getSPriv(): " + Test26.getSPriv() + ", Test26.sPub: " + Test26.sPub);
print("  Result: " + (t26.getPriv() === 1 && t26.pub === 2 && Test26.getSPriv() === 3 && Test26.sPub === 8 ? "PASS" : "FAIL"));

// Test 27: Private field inheritance
print("\nTest 27: Private field inheritance");
class Test27Base {
    #secret = 'base';
    getSecret() {
        return this.#secret;
    }
}
class Test27Derived extends Test27Base {
    #ownSecret = 'derived';
    getOwn() {
        return this.#ownSecret;
    }
}
var t27 = new Test27Derived();
print("  t27.getSecret(): " + t27.getSecret() + ", t27.getOwn(): " + t27.getOwn());
print("  Result: " + (t27.getSecret() === 'base' && t27.getOwn() === 'derived' ? "PASS" : "FAIL"));

// Test 28: Method on prototype
print("\nTest 28: Method on prototype chain");
class Test28 {
    m() {
        return 'proto';
    }
}
var t28 = new Test28();
print("  Test28.prototype.m: " + (Test28.prototype.m ? 'exists' : 'missing'));
print("  t28.hasOwnProperty('m'): " + t28.hasOwnProperty('m'));
print("  Result: " + (Test28.prototype.m !== undefined && !t28.hasOwnProperty('m') ? "PASS" : "FAIL"));

// Test 29: Super method call
print("\nTest 29: Super method call");
class Test29Base {
    m() {
        return 'base';
    }
}
class Test29Derived extends Test29Base {
    m() {
        return super.m() + '-derived';
    }
}
var t29 = new Test29Derived();
print("  t29.m(): " + t29.m());
print("  Result: " + (t29.m() === 'base-derived' ? "PASS" : "FAIL"));

// Test 30: Static method inheritance
print("\nTest 30: Static method inheritance");
class Test30Base {
    static sm() {
        return 'base';
    }
}
class Test30Derived extends Test30Base {
    static sm() {
        return super.sm() + '-derived';
    }
}
print("  Test30Derived.sm(): " + Test30Derived.sm());
print("  Result: " + (Test30Derived.sm() === 'base-derived' ? "PASS" : "FAIL"));

print("\n=== All tests completed ===");