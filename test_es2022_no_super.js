/**
 * ES2022 Class Features Test Suite for Rhino
 * Tests features that don't require super() constructor call
 */

var passed = 0;
var failed = 0;

function test(name, fn) {
    try {
        fn();
        passed++;
        print("✓ " + name);
    } catch (e) {
        failed++;
        print("✗ " + name + ": " + (e.message || e));
    }
}

print("=== ES2022 Class Declaration Tests ===\n");

test("Class declaration basic", function() {
    class A {}
    if (typeof A !== 'function') throw new Error("Expected function");
});

test("Class with constructor", function() {
    class B {
        constructor(x) {
            this.x = x;
        }
    }
    var b = new B(42);
    if (b.x !== 42) throw new Error("Expected 42");
});

test("Class with methods", function() {
    class C {
        m() {
            return 'method';
        }
    }
    var c = new C();
    if (c.m() !== 'method') throw new Error("Expected 'method'");
});

test("Class with getter/setter", function() {
    class D {
        get x() {
            return this._x;
        }
        set x(v) {
            this._x = v;
        }
    }
    var d = new D();
    d.x = 10;
    if (d.x !== 10) throw new Error("Expected 10");
});

test("Class expression", function() {
    var G = class {
        m() {
            return 1;
        }
    };
    var g = new G();
    if (g.m() !== 1) throw new Error("Expected 1");
});

test("Named class expression", function() {
    var H = class Named {
        m() {
            return 'named';
        }
    };
    var h = new H();
    if (h.m() !== 'named') throw new Error("Expected 'named'");
});

print("\n=== ES2022 Private Field Tests ===\n");

test("Private field declaration", function() {
    class P1 {
        #x = 1;
        getX() {
            return this.#x;
        }
    }
    var p = new P1();
    if (p.getX() !== 1) throw new Error("Expected 1");
});

test("Private field assignment", function() {
    class P2 {
        #x = 0;
        setX(v) {
            this.#x = v;
        }
        getX() {
            return this.#x;
        }
    }
    var p = new P2();
    p.setX(42);
    if (p.getX() !== 42) throw new Error("Expected 42");
});

test("Private method", function() {
    class P3 {
        #m() {
            return 'private';
        }
        callM() {
            return this.#m();
        }
    }
    var p = new P3();
    if (p.callM() !== 'private') throw new Error("Expected 'private'");
});

test("Private getter/setter", function() {
    class P4 {
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
    var p = new P4();
    p.setX(5);
    if (p.getX() !== 10) throw new Error("Expected 10");
});

test("Private static field", function() {
    class P5 {
        static #count = 0;
        static inc() {
            return ++P5.#count;
        }
    }
    if (P5.inc() !== 1) throw new Error("Expected 1");
    if (P5.inc() !== 2) throw new Error("Expected 2");
});

test("Private static method", function() {
    class P6 {
        static #secret = 'hidden';
        static getSecret() {
            return P6.#secret;
        }
    }
    if (P6.getSecret() !== 'hidden') throw new Error("Expected 'hidden'");
});

print("\n=== ES2022 Static Features Tests ===\n");

test("Static method", function() {
    class S1 {
        static sm() {
            return 'static';
        }
    }
    if (S1.sm() !== 'static') throw new Error("Expected 'static'");
});

test("Static field", function() {
    class S2 {
        static count = 0;
    }
    if (S2.count !== 0) throw new Error("Expected 0");
});

test("Static field initialization", function() {
    class S3 {
        static arr = [1, 2, 3];
    }
    if (S3.arr.length !== 3) throw new Error("Expected 3");
});

test("Static initialization block", function() {
    class S4 {
        static x;
        static {
            S4.x = 42;
        }
    }
    if (S4.x !== 42) throw new Error("Expected 42");
});

test("Static block with complex logic", function() {
    class S5 {
        static arr = [];
        static {
            for (var i = 0; i < 3; i++) {
                S5.arr.push(i);
            }
        }
    }
    if (S5.arr.join(',') !== '0,1,2') throw new Error("Expected '0,1,2'");
});

test("Multiple static blocks", function() {
    class S6 {
        static {
            S6.a = 1;
        }
        static {
            S6.b = 2;
        }
    }
    if (S6.a !== 1 || S6.b !== 2) throw new Error("Expected a=1, b=2");
});

test("Static field and block order", function() {
    class S7 {
        static log = [];
        static {
            S7.log.push('block1');
        }
        static field = (S7.log.push('field'), 'value');
        static {
            S7.log.push('block2');
        }
    }
    if (S7.log.join(',') !== 'block1,field,block2') throw new Error("Wrong order: " + S7.log.join(','));
});

print("\n=== ES2022 Instance Field Tests ===\n");

test("Instance field with default value", function() {
    class I1 {
        x = 10;
    }
    var i = new I1();
    if (i.x !== 10) throw new Error("Expected 10");
});

test("Instance field with expression", function() {
    class I2 {
        arr = [1, 2, 3];
    }
    var i = new I2();
    if (i.arr.length !== 3) throw new Error("Expected 3");
});

test("Instance field with this reference", function() {
    class I3 {
        name = 'test';
        greeting = 'Hello ' + this.name;
    }
    var i = new I3();
    if (i.greeting !== 'Hello test') throw new Error("Expected 'Hello test'");
});

test("Constructor and field initialization order", function() {
    class I4 {
        x = 10;
        constructor() {
            this.x = this.x * 2;
        }
    }
    var i = new I4();
    if (i.x !== 20) throw new Error("Expected 20");
});

print("\n=== ES2022 Combined Features Tests ===\n");

test("Full class with all features", function() {
    class Full {
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
            return Full.#sPriv;
        }
        
        static {
            Full.sPub = Full.sPub * 2;
        }
    }
    var f = new Full();
    if (f.getPriv() !== 1) throw new Error("Expected priv=1");
    if (f.pub !== 2) throw new Error("Expected pub=2");
    if (Full.getSPriv() !== 3) throw new Error("Expected sPriv=3");
    if (Full.sPub !== 8) throw new Error("Expected sPub=8");
});

test("Method on prototype chain", function() {
    class Proto {
        m() {
            return 'proto';
        }
    }
    var p = new Proto();
    if (Proto.prototype.m === undefined) throw new Error("Method should be on prototype");
    if (p.hasOwnProperty('m')) throw new Error("Method should not be own property");
});

print("\n=== Test Summary ===");
print("Passed: " + passed);
print("Failed: " + failed);
print(failed === 0 ? "\n✓ All tests passed!" : "\n✗ Some tests failed");
