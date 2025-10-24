package io.dscope.utils.crypto;

import java.nio.ByteBuffer;
import java.util.Objects;

final class ByteBufferUtils {

    private ByteBufferUtils() {
    }

    static byte[] copyRemaining(ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "buffer");
        ByteBuffer duplicate = buffer.asReadOnlyBuffer();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }
}
