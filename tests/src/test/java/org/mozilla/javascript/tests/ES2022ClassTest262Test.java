/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mozilla.javascript.drivers.TestUtils.JS_FILE_FILTER;
import static org.mozilla.javascript.drivers.TestUtils.recursiveListFilesHelper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
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
 * Dedicated test class for ES2022 class-related Test262 tests. This runs only the class-related
 * tests from: - language/statements/class - language/expressions/class -
 * built-ins/ClassStaticBlockDefinition - built-ins/ClassStringLookup
 */
@Execution(ExecutionMode.CONCURRENT)
public class ES2022ClassTest262Test {

    private static final String FLAG_RAW = "raw";
    private static final String FLAG_ONLY_STRICT = "onlyStrict";
    private static final String FLAG_NO_STRICT = "noStrict";

    private static final File testDir = new File("test262/test");
    private static final String testHarnessDir = "test262/harness/";
    private static final String testProperties = "testsrc/test262-class.properties";

    private static final boolean updateTest262Properties;
    private static final boolean rollUpEnabled;
    private static final boolean statsEnabled;
    private static final boolean includeUnsupported;

    static final Map<String, Script> HARNESS_SCRIPT_CACHE = new ConcurrentHashMap<>();
    static final Map<Test262Case, TestResultTracker> RESULT_TRACKERS = new LinkedHashMap<>();

    static ShellContextFactory CTX_FACTORY =
            new ShellContextFactory() {
                protected boolean hasFeature(Context cx, int featureIndex) {
                    if (Context.FEATURE_INTL_402 == featureIndex) {
                        return true;
                    }
                    return super.hasFeature(cx, featureIndex);
                }
            };

    // ES2022 class features are supported - only exclude truly unsupported features
    static final Set<String> UNSUPPORTED_FEATURES =
            new HashSet<>(
                    Arrays.asList(
                            "Atomics",
                            "IsHTMLDDA",
                            "async-functions",
                            "async-iteration",
                            "new.target",
                            "SharedArrayBuffer",
                            "tail-call-optimization",
                            "Temporal",
                            "upsert",
                            "u180e"));

    // Class-specific test directories
    private static final String[] CLASS_TEST_DIRS = {
        "language/expressions/class",
        "language/statements/class",
        "built-ins/ClassStaticBlockDefinition",
        "built-ins/ClassStringLookup"
    };

    static {
        String updateProps = System.getProperty("updateTest262properties");

        if (updateProps != null) {
            updateTest262Properties = true;

            switch (updateProps) {
                case "all":
                    rollUpEnabled = statsEnabled = includeUnsupported = true;
                    break;
                case "none":
                    rollUpEnabled = statsEnabled = includeUnsupported = false;
                    break;
                default:
                    rollUpEnabled = updateProps.isEmpty() || updateProps.indexOf("rollup") != -1;
                    statsEnabled = updateProps.isEmpty() || updateProps.indexOf("stats") != -1;
                    includeUnsupported =
                            updateProps.isEmpty() || updateProps.indexOf("unsupported") != -1;
            }
        } else {
            updateTest262Properties = rollUpEnabled = statsEnabled = includeUnsupported = false;
        }
    }

    @BeforeAll
    public static void setUpClass() {
        CTX_FACTORY.setLanguageVersion(Context.VERSION_ES6);
        TestUtils.setGlobalContextFactory(CTX_FACTORY);
    }

    @AfterAll
    public static void tearDownClass() {
        TestUtils.setGlobalContextFactory(null);

        for (Entry<Test262Case, TestResultTracker> entry : RESULT_TRACKERS.entrySet()) {
            if (entry.getKey().file.isFile()) {
                TestResultTracker tt = entry.getValue();

                if (tt.expectedFailure && tt.expectationsMet()) {
                    System.out.println(
                            String.format(
                                    "Test is marked as failing but it does not: %s",
                                    entry.getKey().file));
                }
            }
        }

        if (updateTest262Properties) {
            Test262SuitePropertiesBuilder builder =
                    new Test262SuitePropertiesBuilder(testDir.toPath());
            for (Entry<Test262Case, TestResultTracker> entry : RESULT_TRACKERS.entrySet()) {
                builder.addTest(entry.getKey(), entry.getValue());
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(testProperties))) {
                builder.write(writer, statsEnabled, rollUpEnabled);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static final Pattern LINE_SPLITTER =
            Pattern.compile(
                    "(~|(?:\\s*)(?:!|#)(?:\\s*)|\\s+)?(\\S+)(?:[^\\S\\r\\n]+"
                            + "(?:strict|non-strict|compiled-strict|compiled-non-strict|interpreted-strict|interpreted-non-strict|compiled|interpreted|"
                            + "\\d+/\\d+ \\(\\d+(?:\\.\\d+)?%%\\)|\\{(?:non-strict|strict|unsupported): \\[.*\\],?\\}))?[^\\S\\r\\n]*(.*)");

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

    private Scriptable buildScope(Context cx, Test262Case testCase, boolean interpretedMode) {
        ScriptableObject scope = (ScriptableObject) cx.initSafeStandardObjects(new TopLevel());

        for (String harnessFile : testCase.harnessFiles) {
            String harnessKey = harnessFile + '-' + interpretedMode;
            Script harnessScript =
                    HARNESS_SCRIPT_CACHE.computeIfAbsent(
                            harnessKey,
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

    @ParameterizedTest
    @MethodSource("classTestValues")
    public void test262ClassCase(
            String testFilePath,
            TestMode testMode,
            boolean useStrict,
            Test262Case testCase,
            boolean markedAsFailing) {
        try (Context cx = Context.enter()) {
            cx.setInterpretedMode(testMode == TestMode.INTERPRETED);
            cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
            cx.setGeneratingDebug(true);

            boolean failedEarly = false;
            try {
                Scriptable scope = buildScope(cx, testCase, testMode == TestMode.INTERPRETED);
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
                    fail(
                            String.format(
                                    "Failed a negative test. Expected error: %s (at phase '%s')",
                                    testCase.expectedError,
                                    testCase.hasEarlyError ? "early" : "runtime"));
                }

                synchronized (RESULT_TRACKERS) {
                    TestResultTracker tracker = RESULT_TRACKERS.get(testCase);
                    if (tracker != null) {
                        tracker.passes(testMode, useStrict);
                    }
                }
            } catch (RhinoException ex) {
                if (!testCase.isNegative()) {
                    if (markedAsFailing) return;

                    fail(String.format("%s%n%s", ex.getMessage(), ex.getScriptStackTrace()));
                }

                String errorName = extractJSErrorName(ex);

                if (testCase.hasEarlyError && !failedEarly) {
                    if (markedAsFailing) return;

                    fail(
                            String.format(
                                    "Expected an early error: %s, got: %s in the runtime",
                                    testCase.expectedError, errorName));
                }

                try {
                    assertEquals(ex.details(), testCase.expectedError, errorName);
                } catch (AssertionError aex) {
                    if (markedAsFailing) return;

                    throw aex;
                }

                synchronized (RESULT_TRACKERS) {
                    TestResultTracker tracker = RESULT_TRACKERS.get(testCase);
                    if (tracker != null) {
                        tracker.passes(testMode, useStrict);
                    }
                }
            } catch (RuntimeException ex) {
                if (markedAsFailing) return;
                throw ex;
            } catch (AssertionError ex) {
                if (markedAsFailing) return;
                throw ex;
            }
        }
    }

    private static void addTestFiles(List<File> testFiles, Map<File, String> filesExpectedToFail)
            throws IOException {
        // Add all class-related test directories
        for (String classDir : CLASS_TEST_DIRS) {
            File dir = new File(testDir, classDir);
            if (dir.exists() && dir.isDirectory()) {
                List<File> files = new LinkedList<>();
                recursiveListFilesHelper(dir, JS_FILE_FILTER, files);
                testFiles.addAll(files);
            }
        }

        // Parse the properties file for expected failures
        File propsFile = new File(testProperties);
        if (propsFile.exists()) {
            parsePropertiesFile(propsFile, testFiles, filesExpectedToFail);
        }
    }

    private static void parsePropertiesFile(
            File propsFile, List<File> testFiles, Map<File, String> filesExpectedToFail)
            throws IOException {
        int lineNo = 0;
        String line;
        String path;
        String comment;

        try (Scanner scanner = new Scanner(propsFile)) {
            while (scanner.hasNextLine()) {
                line = scanner.nextLine();
                Matcher splitLine = LINE_SPLITTER.matcher(line);
                lineNo++;

                if (!splitLine.matches()) {
                    continue;
                }

                path = splitLine.group(2);
                comment = splitLine.group(3);

                if (splitLine.group(1) == null || splitLine.group(1).equals("~")) {
                    // Directory entry - skip for now
                    continue;
                } else if (splitLine.group(1).trim().length() > 0) {
                    // Comments
                    continue;
                }

                if (path.endsWith(".js")) {
                    File file = new File(testDir, path);
                    if (file.exists()) {
                        filesExpectedToFail.put(file, comment);
                    }
                }
            }
        }
    }

    public static Collection<Object[]> classTestValues() throws IOException {
        List<Object[]> result = new ArrayList<>();

        List<File> testFiles = new LinkedList<>();
        Map<File, String> failingFiles = new HashMap<>();
        addTestFiles(testFiles, failingFiles);

        fileLoop:
        for (File testFile : testFiles) {
            String caseShortPath = testDir.toPath().relativize(testFile.toPath()).toString();
            boolean markedAsFailing = failingFiles.containsKey(testFile);
            String comment = markedAsFailing ? failingFiles.get(testFile) : null;

            Test262Case testCase;
            try {
                testCase = Test262Case.fromSource(testFile);
            } catch (YAMLException ex) {
                throw new RuntimeException(
                        "Error while parsing metadata of " + testFile.getPath(), ex);
            }

            // Check for unsupported features
            for (String feature : testCase.features) {
                if (UNSUPPORTED_FEATURES.contains(feature)) {
                    if (includeUnsupported) {
                        TestResultTracker tracker =
                                RESULT_TRACKERS.computeIfAbsent(
                                        testCase, k -> new TestResultTracker(comment));
                        tracker.setExpectations(
                                TestMode.SKIPPED,
                                true,
                                testCase.hasFlag(FLAG_ONLY_STRICT),
                                testCase.hasFlag(FLAG_NO_STRICT),
                                true);
                    }
                    continue fileLoop;
                }
            }

            // Skip module and async tests
            if (testCase.hasFlag("module") || testCase.hasFlag("async")) {
                if (includeUnsupported) {
                    TestResultTracker tracker =
                            RESULT_TRACKERS.computeIfAbsent(
                                    testCase, k -> new TestResultTracker(comment));
                    tracker.setExpectations(
                            TestMode.SKIPPED,
                            true,
                            testCase.hasFlag(FLAG_ONLY_STRICT),
                            testCase.hasFlag(FLAG_NO_STRICT),
                            true);
                }
                continue;
            }

            for (TestMode testMode : new TestMode[] {TestMode.INTERPRETED, TestMode.COMPILED}) {
                if (!testCase.hasFlag(FLAG_ONLY_STRICT) || testCase.hasFlag(FLAG_RAW)) {
                    result.add(
                            new Object[] {
                                caseShortPath, testMode, false, testCase, markedAsFailing
                            });
                    TestResultTracker tracker =
                            RESULT_TRACKERS.computeIfAbsent(
                                    testCase, k -> new TestResultTracker(comment));
                    tracker.setExpectations(
                            testMode,
                            false,
                            testCase.hasFlag(FLAG_ONLY_STRICT),
                            testCase.hasFlag(FLAG_NO_STRICT),
                            markedAsFailing);
                }

                if (!testCase.hasFlag(FLAG_NO_STRICT) && !testCase.hasFlag(FLAG_RAW)) {
                    result.add(
                            new Object[] {
                                caseShortPath, testMode, true, testCase, markedAsFailing
                            });
                    TestResultTracker tracker =
                            RESULT_TRACKERS.computeIfAbsent(
                                    testCase, k -> new TestResultTracker(comment));
                    tracker.setExpectations(
                            testMode,
                            true,
                            testCase.hasFlag(FLAG_ONLY_STRICT),
                            testCase.hasFlag(FLAG_NO_STRICT),
                            markedAsFailing);
                }
            }
        }
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
        private final Set<String> features;

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
                System.err.format(
                        "WARN: file '%s' doesnt contain /*--- ... ---*/ directive",
                        testFile.getPath());
                metadata = new HashMap<String, Object>();
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
        SKIPPED,
    }

    private static class TestResultTracker {
        private final Set<String> modes = new HashSet<>();
        private boolean onlyStrict;
        private boolean noStrict;
        private boolean expectedFailure;
        private String comment;

        TestResultTracker(String comment) {
            this.comment = comment;
        }

        private static String makeKey(TestMode mode, boolean useStrict) {
            return mode.name().toLowerCase() + '-' + (useStrict ? "strict" : "non-strict");
        }

        public void setExpectations(
                TestMode mode,
                boolean useStrict,
                boolean onlyStrict,
                boolean noStrict,
                boolean expectedFailure) {
            modes.add(makeKey(mode, useStrict));
            this.onlyStrict = onlyStrict;
            this.noStrict = noStrict;
            this.expectedFailure = expectedFailure;
        }

        public boolean expectationsMet() {
            return modes.isEmpty();
        }

        public void passes(TestMode mode, boolean useStrict) {
            modes.remove(makeKey(mode, useStrict));
        }

        public String getResult(Test262Case tc) {
            if (modes.isEmpty()) {
                return null;
            }

            if (modes.contains("skipped-strict")) {
                List<String> feats = new ArrayList<>();

                if (tc.features != null) {
                    for (String feature : tc.features) {
                        if (UNSUPPORTED_FEATURES.contains(feature)) {
                            feats.add(feature);
                        }
                    }
                }

                if (tc.hasFlag("module")) {
                    feats.add("module");
                }

                if (tc.hasFlag("async")) {
                    feats.add("async");
                }

                return "{unsupported: " + Arrays.toString(feats.toArray()) + "}";
            }

            if (modes.size() == 4) {
                return "";
            }

            ArrayList<String> res = new ArrayList<>(modes);
            if (res.contains("compiled-non-strict") && res.contains("interpreted-non-strict")) {
                res.remove("compiled-non-strict");
                res.remove("interpreted-non-strict");
                res.add("non-strict");
            }
            if (res.contains("compiled-strict") && res.contains("interpreted-strict")) {
                res.remove("compiled-strict");
                res.remove("interpreted-strict");
                res.add("strict");
            }
            if (res.contains("compiled-strict") && res.contains("compiled-non-strict")) {
                res.remove("compiled-strict");
                res.remove("compiled-non-strict");
                res.add("compiled");
            }
            if (res.contains("interpreted-strict") && res.contains("interpreted-non-strict")) {
                res.remove("interpreted-strict");
                res.remove("interpreted-non-strict");
                res.add("interpreted");
            }

            if (res.size() > 1) {
                return '{' + String.join(",", res) + '}';
            }
            return String.join(",", res);
        }
    }

    private static class Test262SuitePropertiesBuilder {
        private Path testDir;
        private DirectoryNode rootNode;

        Test262SuitePropertiesBuilder(Path testDir) {
            this.testDir = testDir;
            rootNode = new DirectoryNode(Path.of(""));
        }

        void addTest(Test262Case testCase, TestResultTracker resultTracker) {
            Path testFilePath = testDir.relativize(testCase.file.toPath());
            if (testCase.file.isDirectory()) {
                List<File> excludedFiles = new ArrayList<>();
                TestUtils.recursiveListFilesHelper(testCase.file, JS_FILE_FILTER, excludedFiles);
                buildNodeTree(testFilePath, (p) -> new ExcludeNode(p, excludedFiles.size()), true);
                return;
            }

            boolean isFailure = resultTracker.getResult(testCase) != null;
            DirectoryNode parentNode =
                    buildNodeTree(
                            testFilePath,
                            (p) -> new TestNode(p, testCase, resultTracker),
                            isFailure);
            parentNode.count(isFailure);
        }

        void write(Writer writer, boolean statsEnabled, boolean rollUpEnabled) throws IOException {
            writer.write("# Test262 ES2022 Class Tests Configuration\n");
            rootNode.writeChildNodes(writer, null, statsEnabled, rollUpEnabled, false);
        }

        private DirectoryNode buildNodeTree(
                Path testFilePath, Function<Path, Node> mappingFunction, boolean isFailure) {
            DirectoryNode parentNode = rootNode;
            Path nodePath = Path.of("");

            for (int i = 0; i < testFilePath.getNameCount() - 1; i++) {
                nodePath = nodePath.resolve(testFilePath.getName(i));
                DirectoryNode nextNode = parentNode.childNodes.get(nodePath.toString());
                if (nextNode == null) {
                    nextNode = new DirectoryNode(nodePath);
                    parentNode.childNodes.put(nodePath.toString(), nextNode);
                }
                parentNode = nextNode;
            }

            Path leafPath = testFilePath.getFileName();
            Node leafNode = mappingFunction.apply(testFilePath);
            parentNode.childNodes.put(leafPath.toString(), leafNode);

            return parentNode;
        }
    }

    private abstract static class Node {
        protected final Path path;

        Node(Path path) {
            this.path = path;
        }

        abstract void write(
                Writer writer,
                String prefix,
                boolean statsEnabled,
                boolean rollUpEnabled,
                boolean skipDir)
                throws IOException;
    }

    private static class DirectoryNode extends Node {
        Map<String, Node> childNodes = new LinkedHashMap<>();
        int total;
        int failed;

        DirectoryNode(Path path) {
            super(path);
        }

        void count(boolean isFailure) {
            total++;
            if (isFailure) {
                failed++;
            }
        }

        void writeChildNodes(
                Writer writer,
                String prefix,
                boolean statsEnabled,
                boolean rollUpEnabled,
                boolean skipDir)
                throws IOException {
            List<Node> sortedNodes = new ArrayList<>(childNodes.values());
            sortedNodes.sort(
                    (n1, n2) -> {
                        boolean n1IsDir = n1 instanceof DirectoryNode;
                        boolean n2IsDir = n2 instanceof DirectoryNode;
                        if (n1IsDir && !n2IsDir) return -1;
                        if (!n1IsDir && n2IsDir) return 1;
                        return n1.path
                                .toString()
                                .replaceFirst("\\.js$", "")
                                .compareToIgnoreCase(n2.path.toString().replaceFirst("\\.js$", ""));
                    });

            for (Node node : sortedNodes) {
                node.write(writer, prefix, statsEnabled, rollUpEnabled, skipDir);
            }
        }

        @Override
        void write(
                Writer writer,
                String prefix,
                boolean statsEnabled,
                boolean rollUpEnabled,
                boolean skipDir)
                throws IOException {
            String name = path.getFileName() != null ? path.getFileName().toString() : "";
            String newPrefix = prefix == null ? name : prefix + "/" + name;

            if (rollUpEnabled && failed == 0 && total > 0) {
                writer.write(newPrefix);
                if (statsEnabled) {
                    writer.write(
                            " " + failed + "/" + total + " (" + (100.0 * failed / total) + "%)");
                }
                writer.write("\n");
                return;
            }

            if (skipDir) {
                writer.write("~" + newPrefix);
                if (statsEnabled) {
                    writer.write(
                            " " + failed + "/" + total + " (" + (100.0 * failed / total) + "%)");
                }
                writer.write("\n");
            }

            writeChildNodes(
                    writer, newPrefix, statsEnabled, rollUpEnabled, skipDir && failed == total);
        }
    }

    private static class TestNode extends Node {
        Test262Case testCase;
        TestResultTracker resultTracker;

        TestNode(Path path, Test262Case testCase, TestResultTracker resultTracker) {
            super(path);
            this.testCase = testCase;
            this.resultTracker = resultTracker;
        }

        @Override
        void write(
                Writer writer,
                String prefix,
                boolean statsEnabled,
                boolean rollUpEnabled,
                boolean skipDir)
                throws IOException {
            String result = resultTracker.getResult(testCase);
            if (result != null) {
                writer.write(" " + path.toString());
                if (!result.isEmpty()) {
                    writer.write(" " + result);
                }
                writer.write("\n");
            }
        }
    }

    private static class ExcludeNode extends Node {
        int count;

        ExcludeNode(Path path, int count) {
            super(path);
            this.count = count;
        }

        @Override
        void write(
                Writer writer,
                String prefix,
                boolean statsEnabled,
                boolean rollUpEnabled,
                boolean skipDir)
                throws IOException {
            // ExcludeNode is used for directories that are completely skipped
        }
    }
}
