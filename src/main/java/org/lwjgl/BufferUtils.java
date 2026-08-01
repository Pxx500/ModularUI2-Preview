package org.lwjgl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public final class BufferUtils {

    private static final int BYTES_PER_NUMBER = 4;

    private BufferUtils() {}

    public static ByteBuffer createByteBuffer(int size) {
        return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    }

    public static FloatBuffer createFloatBuffer(int size) {
        return createByteBuffer(size * BYTES_PER_NUMBER).asFloatBuffer();
    }

    public static IntBuffer createIntBuffer(int size) {
        return createByteBuffer(size * BYTES_PER_NUMBER).asIntBuffer();
    }
}
