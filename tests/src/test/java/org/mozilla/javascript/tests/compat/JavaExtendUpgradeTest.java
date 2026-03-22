/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests.compat;

import org.mozilla.javascript.drivers.RhinoTest;
import org.mozilla.javascript.drivers.ScriptTestsBase;

/**
 * One-line upgrade test for RhinoCompat.init(). Tests that a single line of code enables all
 * compatibility features including Java.extend() and extend() function.
 */
@RhinoTest("testsrc/compat-tests/test_oneline_upgrade.js")
public class JavaExtendUpgradeTest extends ScriptTestsBase {}
