/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests;

import static org.mozilla.javascript.drivers.TestUtils.JS_FILE_FILTER;
import static org.mozilla.javascript.drivers.TestUtils.recursiveListFilesHelper;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Kit;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.SymbolKey;
import org.mozilla.javascript.TopLevel;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.drivers.TestUtils;
import org.mozilla.javascript.tools.SourceReader;
import org.mozilla.javascript.tools.shell.ShellContextFactory;
import org.mozilla.javascript.typedarrays.NativeArrayBuffer;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Batch 6: Async Tests (ES2017/ES2018 features).
 *
 * <p>Tests async methods in class contexts:
 *
 * <ul>
 *   <li>async-method/ - Instance async methods (async-functions feature)
 *   <li>async-method-static/ - Static async methods (async-functions feature)
 *   <li>async-gen-method/ - Async generator methods (async-iteration feature)
 *   <li>async-gen-method-static/ - Static async generator methods (async-iteration feature)
 * </ul>
 *
 * <p>NOTE: async-functions and async-iteration are currently UNSUPPORTED. This test class will skip
 * all tests requiring these features.
 *
 * <p>Estimated test count: ~700 (most will be skipped due to unsupported features)
 */
@Execution(ExecutionMode.CONCURRENT)
public class ES2022ClassAsyncTest262Test {

    private static final String FLAG_RAW = "raw";
    private static final String FLAG_ONLY_STRICT = "onlyStrict";
    private static final String FLAG_NO_STRICT = "noStrict";

    private static final File testDir = new File("test262/test");
    private static final String testHarnessDir = "test262/harness/";

    static final Map<String, Script> HARNESS_SCRIPT_CACHE = new ConcurrentHashMap<>();

    static ShellContextFactory CTX_FACTORY =
            new ShellContextFactory() {
                protected boolean hasFeature(Context cx, int featureIndex) {
                    if (Context.FEATURE_INTL_402 == featureIndex) {
                        return true;
                    }
                    return super.hasFeature(cx, featureIndex);
                }
            };

    // async-functions and async-iteration are NOT supported
    // Tests with these features will be skipped
    static final Set<String> UNSUPPORTED_FEATURES =
            new HashSet<>(
                    Arrays.asList(
                            "Atomics",
                            "IsHTMLDDA",
                            "async-functions", // NOT SUPPORTED
                            "async-iteration", // NOT SUPPORTED
                            "new.target",
                            "SharedArrayBuffer",
                            "tail-call-optimization",
                            "Temporal",
                            "upsert",
                            "u180e"));

    // Async test directories
    private static final String[] CLASS_TEST_DIRS = {
        "language/statements/class/async-method",
        "language/statements/class/async-method-static",
        "language/expressions/class/async-method",
        "language/expressions/class/async-method-static",
        "language/statements/class/async-gen-method",
        "language/statements/class/async-gen-method-static",
        "language/expressions/class/async-gen-method",
        "language/expressions/class/async-gen-method-static"
    };

    @BeforeAll
    public static void setUpClass() {
        CTX_FACTORY.setLanguageVersion(Context.VERSION_ES6);
        TestUtils.setGlobalContextFactory(CTX_FACTORY);
    }

    public static class $262 extends ScriptableObject {

        $262() {
            super();
        }

        $262(Scriptable scope, Scriptable prototype) {
            super(scope, prototype);
        }

        static $262 init(Context cx, Scriptable scope) {
            $262 proto = new $262();
            proto.setPrototype(getObjectPrototype(scope));
            proto.setParentScope(scope);

            proto.defineProperty(scope, "gc", 0, $262::gc);
            proto.defineProperty(scope, "createRealm", 0, $262::createRealm);
            proto.defineProperty(scope, "evalScript", 1, $262::evalScript);
            proto.defineProperty(scope, "detachArrayBuffer", 0, $262::detachArrayBuffer);

            proto.defineProperty(cx, "global", $262::getGlobal, null, DONTENUM | READONLY);
            proto.defineProperty(cx, "agent", $262::getAgent, null, DONTENUM | READONLY);

            proto.defineProperty(SymbolKey.TO_STRING_TAG, "__262__", DONTENUM | READONLY);

            ScriptableObject.defineProperty(scope, "__262__", proto, DONTENUM);
            return proto;
        }

        static $262 install(ScriptableObject scope, Scriptable parentScope) {
            $262 instance = new $262(scope, parentScope);

            scope.put("$262", scope, instance);
            scope.setAttributes("$262", ScriptableObject.DONTENUM);

            return instance;
        }

        private static Object gc(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            System.gc();
            return Undefined.instance;
        }

        public static Object evalScript(
                Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            if (args.length == 0) {
                throw ScriptRuntime.throwError(cx, scope, "not enough args");
            }
            String source = Context.toString(args[0]);
            return cx.evaluateString(scope, source, "<evalScript>", 1, null);
        }

        public static Object getGlobal(Scriptable scriptable) {
            return scriptable.getParentScope();
        }

        public static $262 createRealm(
                Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            ScriptableObject realm = (ScriptableObject) cx.initSafeStandardObjects(new TopLevel());
            return install(realm, thisObj.getPrototype());
        }

        public static Object detachArrayBuffer(
                Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            Scriptable buf = ScriptRuntime.toObject(scope, args[0]);
            if (buf instanceof NativeArrayBuffer) {
                ((NativeArrayBuffer) buf).detach();
            }
            return Undefined.instance;
        }

        public static Object getAgent(Scriptable scriptable) {
            throw new UnsupportedOperationException("$262.agent property not yet implemented");
        }

        @Override
        public String getClassName() {
            return "__262__";
        }
    }

    private Scriptable buildScope(Context cx, Test262Case testCase) {
        ScriptableObject scope = (ScriptableObject) cx.initSafeStandardObjects(new TopLevel());

        for (String harnessFile : testCase.harnessFiles) {
            Script harnessScript =
                    HARNESS_SCRIPT_CACHE.computeIfAbsent(
                            harnessFile,
                            k -> {
                                String harnessPath = testHarnessDir + harnessFile;
                                try (Reader reader = new FileReader(harnessPath)) {
                                    String script = Kit.readReader(reader);
                                    return cx.compileString(script, harnessPath, 1, null);
                                } catch (IOException ioe) {
                                    throw new RuntimeException(
                                            "Error reading test file " + harnessPath, ioe);
                                }
                            });
            harnessScript.exec(cx, scope, scope);
        }

        $262 proto = $262.init(cx, scope);
        $262.install(scope, proto);
        return scope;
    }

    @ParameterizedTest
    @MethodSource("classTestValues")
    public void test262ClassCase(
            String testFilePath, TestMode testMode, boolean useStrict, Test262Case testCase) {
        try (Context cx = Context.enter()) {
            cx.setInterpretedMode(testMode == TestMode.INTERPRETED);
            cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
            cx.setGeneratingDebug(true);

            boolean failedEarly = false;
            try {
                Scriptable scope = buildScope(cx, testCase);
                String str = testCase.source;
                int line = 1;
                if (useStrict) {
                    str = "\"use strict\";\n" + str;
                    line--;
                }

                failedEarly = true;
                Script caseScript = cx.compileString(str, testFilePath, line, null);

                failedEarly = false;
                caseScript.exec(cx, scope, scope);

                if (testCase.isNegative()) {
                    org.junit.jupiter.api.Assertions.fail(
                            String.format(
                                    "Failed a negative test. Expected error: %s (at phase '%s')",
                                    testCase.expectedError,
                                    testCase.hasEarlyError ? "early" : "runtime"));
                }

            } catch (RhinoException ex) {
                if (!testCase.isNegative()) {
                    org.junit.jupiter.api.Assertions.fail(
                            String.format("%s%n%s", ex.getMessage(), ex.getScriptStackTrace()));
                }

                String errorName = extractJSErrorName(ex);

                if (testCase.hasEarlyError && !failedEarly) {
                    org.junit.jupiter.api.Assertions.fail(
                            String.format(
                                    "Expected an early error: %s, got: %s in the runtime",
                                    testCase.expectedError, errorName));
                }

                org.junit.jupiter.api.Assertions.assertEquals(
                        testCase.expectedError, errorName, ex.details());
            }
        }
    }

    private static String extractJSErrorName(RhinoException ex) {
        if (ex instanceof EvaluatorException) {
            return "SyntaxError";
        }

        String exceptionName = ex.details();
        if (exceptionName.contains(":")) {
            exceptionName = exceptionName.substring(0, exceptionName.indexOf(":"));
        }
        return exceptionName;
    }

    public static Collection<Object[]> classTestValues() throws IOException {
        List<Object[]> result = new ArrayList<>();

        // Collect test files from async directories
        List<File> testFiles = new LinkedList<>();
        for (String classDir : CLASS_TEST_DIRS) {
            File dir = new File(testDir, classDir);
            if (dir.exists() && dir.isDirectory()) {
                recursiveListFilesHelper(dir, JS_FILE_FILTER, testFiles);
            }
        }

        System.out.println("Batch 6: Found " + testFiles.size() + " async test files");

        int skippedCount = 0;
        fileLoop:
        for (File testFile : testFiles) {
            String caseShortPath = testDir.toPath().relativize(testFile.toPath()).toString();

            Test262Case testCase;
            try {
                testCase = Test262Case.fromSource(testFile);
            } catch (YAMLException ex) {
                throw new RuntimeException(
                        "Error while parsing metadata of " + testFile.getPath(), ex);
            }

            // Check for unsupported features (async-functions, async-iteration)
            for (String feature : testCase.features) {
                if (UNSUPPORTED_FEATURES.contains(feature)) {
                    skippedCount++;
                    continue fileLoop;
                }
            }

            // Skip module and async flag tests
            if (testCase.hasFlag("module") || testCase.hasFlag("async")) {
                continue;
            }

            for (TestMode testMode : new TestMode[] {TestMode.INTERPRETED, TestMode.COMPILED}) {
                if (!testCase.hasFlag(FLAG_ONLY_STRICT) || testCase.hasFlag(FLAG_RAW)) {
                    result.add(new Object[] {caseShortPath, testMode, false, testCase});
                }

                if (!testCase.hasFlag(FLAG_NO_STRICT) && !testCase.hasFlag(FLAG_RAW)) {
                    result.add(new Object[] {caseShortPath, testMode, true, testCase});
                }
            }
        }

        System.out.println(
                "Batch 6: Generated "
                        + result.size()
                        + " test cases (skipped "
                        + skippedCount
                        + " due to unsupported features)");
        return result;
    }

    private static class Test262Case {
        private static final Yaml YAML = new Yaml();

        private final File file;
        private final String source;
        private final String expectedError;
        private final boolean hasEarlyError;
        private final Set<String> flags;
        private final List<String> harnessFiles;
        final Set<String> features;

        Test262Case(
                File file,
                String source,
                List<String> harnessFiles,
                String expectedError,
                boolean hasEarlyError,
                Set<String> flags,
                Set<String> features) {
            this.file = file;
            this.source = source;
            this.harnessFiles = harnessFiles;
            this.expectedError = expectedError;
            this.hasEarlyError = hasEarlyError;
            this.flags = flags;
            this.features = features;
        }

        boolean hasFlag(String flag) {
            return flags != null && flags.contains(flag);
        }

        boolean isNegative() {
            return expectedError != null;
        }

        @SuppressWarnings("unchecked")
        static Test262Case fromSource(File testFile) throws IOException {
            String testSource =
                    (String) SourceReader.readFileOrUrl(testFile.getPath(), true, "UTF-8");

            List<String> harnessFiles = new ArrayList<>();

            Map<String, Object> metadata;

            if (testSource.indexOf("/*---") != -1) {
                String metadataStr =
                        testSource.substring(
                                testSource.indexOf("/*---") + 5, testSource.indexOf("---*/"));
                metadata = (Map<String, Object>) YAML.load(metadataStr);
            } else {
                metadata = new java.util.HashMap<>();
            }

            String expectedError = null;
            boolean isEarly = false;
            if (metadata.containsKey("negative")) {
                Map<String, String> negative = (Map<String, String>) metadata.get("negative");
                expectedError = negative.get("type");
                isEarly = "early".equals(negative.get("phase"));
            }

            Set<String> flags = new HashSet<>();
            if (metadata.containsKey("flags")) {
                flags.addAll((Collection<String>) metadata.get("flags"));
            }

            Set<String> features = new HashSet<>();
            if (metadata.containsKey("features")) {
                features.addAll((Collection<String>) metadata.get("features"));
            }

            harnessFiles.add("assert.js");
            harnessFiles.add("sta.js");

            if (metadata.containsKey("includes")) {
                harnessFiles.addAll((List<String>) metadata.get("includes"));
            }

            return new Test262Case(
                    testFile, testSource, harnessFiles, expectedError, isEarly, flags, features);
        }
    }

    private enum TestMode {
        INTERPRETED,
        COMPILED,
    }
}
