/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests.compat;

import org.mozilla.javascript.drivers.RhinoTest;
import org.mozilla.javascript.drivers.ScriptTestsBase;

/**
 * Test for JavaExtendCompat class. Tests Java.extend() API replacement including single interface,
 * multiple interfaces, and class extension.
 */
@RhinoTest("testsrc/compat-tests/test_java_extend_compat.js")
public class JavaExtendCompatJsTest extends ScriptTestsBase {}
