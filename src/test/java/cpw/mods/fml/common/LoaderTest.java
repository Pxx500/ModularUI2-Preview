package cpw.mods.fml.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

class LoaderTest {

    @Test
    void exposesTheCrashCallableExpectedByForge() throws Exception {
        Method method = Loader.class.getMethod("getCallableCrashInformation");
        Object callable = method.invoke(Loader.instance());

        assertEquals("cpw.mods.fml.common.ICrashCallable", method.getReturnType().getName());
        assertEquals("FML", callable.getClass().getMethod("getLabel").invoke(callable));
        assertEquals("", callable.getClass().getMethod("call").invoke(callable));
    }
}
