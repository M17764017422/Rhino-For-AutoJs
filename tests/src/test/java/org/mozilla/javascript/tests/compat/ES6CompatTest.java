/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests.compat;

import org.mozilla.javascript.drivers.RhinoTest;
import org.mozilla.javascript.drivers.ScriptTestsBase;

/**
 * ES6+ features compatibility test for Rhino 2.0.0. Tests arrow functions, let/const, template
 * strings, destructuring, spread operator, default parameters, rest parameters, Promise, Map/Set,
 * Symbol, for...of, generators, and various Object/Array methods.
 */
@RhinoTest("testsrc/compat-tests/es6_test.js")
public class ES6CompatTest extends ScriptTestsBase {}
