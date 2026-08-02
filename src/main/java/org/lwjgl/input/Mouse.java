package org.lwjgl.input;

import org.lwjgl.LWJGLException;

public final class Mouse {

    private Mouse() {}

    public static Cursor setNativeCursor(Cursor cursor) throws LWJGLException {
        return cursor;
    }
}
