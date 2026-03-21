package org.mozilla.javascript;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.mozilla.javascript.testutils.Utils;

public class ThreadSafeScriptableObjectTest {
    @Test(timeout = 30000) // 30秒超时，避免死锁导致 CI 卡住
    public void canSealGlobalObjectWithoutDeadlock() {
        ContextFactory.getGlobalSetter()
                .setContextFactoryGlobal(
                        Utils.contextFactoryWithFeatures(Context.FEATURE_THREAD_SAFE_OBJECTS));

        try (Context cx = Context.enter()) {
            ScriptableObject global = cx.initStandardObjects();
            global.sealObject();

            // Registered by NativeJavaTopPackage
            assertNotNull(global.get("Packages", global));
        }
    }
}
