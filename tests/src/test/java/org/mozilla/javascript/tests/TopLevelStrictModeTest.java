package org.mozilla.javascript.tests;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.drivers.LanguageVersion;
import org.mozilla.javascript.drivers.RhinoTest;
import org.mozilla.javascript.drivers.ScriptTestsBase;

@RhinoTest(value = "testsrc/jstests/top-level-strict-mode.js")
@LanguageVersion(Context.VERSION_1_8)
public class TopLevelStrictModeTest extends ScriptTestsBase {}
