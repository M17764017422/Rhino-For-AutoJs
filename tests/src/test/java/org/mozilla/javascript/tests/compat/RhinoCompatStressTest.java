/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests.compat;

import org.mozilla.javascript.drivers.RhinoTest;
import org.mozilla.javascript.drivers.ScriptTestsBase;

/**
 * Stress test for Rhino 2.0.0 compat layer. Tests function type detection, wrapping/unwrapping, and
 * ES6+ features.
 */
@RhinoTest("testsrc/compat-tests/stress_test.js")
public class RhinoCompatStressTest extends ScriptTestsBase {}
