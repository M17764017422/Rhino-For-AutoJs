/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests.compat;

import org.mozilla.javascript.drivers.RhinoTest;
import org.mozilla.javascript.drivers.ScriptTestsBase;

/**
 * Full flow test for JavaExtendCompat. Tests single interface implementation, multiple interface
 * implementation, and compares with native new Interface() syntax.
 */
@RhinoTest("testsrc/compat-tests/test_full_flow.js")
public class JavaExtendFullFlowTest extends ScriptTestsBase {}
