package com.nz.jnng.socket;

import com.nz.jnng.constants.NngErrorCode;
import com.nz.jnng.exception.NngException;
import com.nz.jnng.nng_h;
import com.nz.jnng.utils.Nng;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owning view of a native {@code nng_msg}.
 *
 * <p>The body segment is valid until this message is closed or successfully
 * transferred to NNG by a socket send operation.</p>
 */
public final class NativeMessage implements AutoCloseable {

    private final MemorySegment handle;
    private final AtomicBoolean owned = new AtomicBoolean(true);

    private NativeMessage(MemorySegment handle) {
        this.handle = Objects.requireNonNull(handle, "handle");
        if (handle.equals(MemorySegment.NULL)) {
            throw new IllegalArgumentException("handle must not be NULL");
        }
    }

    public static NativeMessage allocate(long bodySize) {
        if (bodySize < 0) {
            throw new IllegalArgumentException("bodySize must be >= 0");
        }
        Nng.load();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
            int rc = nng_h.nng_msg_alloc(pointer, bodySize);
            if (rc != NngErrorCode.OK) {
                throw new NngException(rc);
            }
            return adopt(pointer.get(ValueLayout.ADDRESS, 0));
        }
    }

    public static NativeMessage copyOf(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        NativeMessage message = allocate(bytes.length);
        if (bytes.length > 0) {
            MemorySegment.copy(bytes, 0, message.body(), ValueLayout.JAVA_BYTE, 0, bytes.length);
        }
        return message;
    }

    static NativeMessage adopt(MemorySegment handle) {
        return new NativeMessage(handle);
    }

    public long size() {
        ensureOwned();
        return nng_h.nng_msg_len(handle);
    }

    public MemorySegment body() {
        ensureOwned();
        return nng_h.nng_msg_body(handle).reinterpret(size());
    }

    public byte[] toByteArray() {
        return body().toArray(ValueLayout.JAVA_BYTE);
    }

    MemorySegment handle() {
        ensureOwned();
        return handle;
    }

    void transferredToNng() {
        if (!owned.compareAndSet(true, false)) {
            throw new IllegalStateException("Native message is no longer owned by Java");
        }
    }

    public boolean isOpen() {
        return owned.get();
    }

    @Override
    public void close() {
        if (owned.compareAndSet(true, false)) {
            nng_h.nng_msg_free(handle);
        }
    }

    private void ensureOwned() {
        if (!owned.get()) {
            throw new IllegalStateException("Native message is closed or owned by NNG");
        }
    }
}
