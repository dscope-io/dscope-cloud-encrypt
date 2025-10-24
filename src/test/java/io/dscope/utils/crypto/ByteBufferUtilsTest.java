package io.dscope.utils.crypto;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ByteBufferUtilsTest {

    @Test
    void copyRemainingPreservesOnlyReadableBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.put("hello".getBytes(StandardCharsets.UTF_8));
        buffer.flip();

        byte[] bytes = ByteBufferUtils.copyRemaining(buffer);

        assertEquals(5, bytes.length);
        assertEquals(0, buffer.position());
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), bytes);
    }

    @Test
    void copyRemainingSupportsDirectBuffers() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(8);
        buffer.putInt(42);
        buffer.flip();

        byte[] bytes = ByteBufferUtils.copyRemaining(buffer);

        assertEquals(4, bytes.length);
        ByteBuffer view = ByteBuffer.wrap(bytes);
        assertEquals(42, view.getInt());
    }
}
