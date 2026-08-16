package com.nz.jnng.message;

import com.nz.jnng.socket.NativeMessage;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/** Zero-copy view of a decoded J-NNG envelope and its native payload. */
public final class NativeWireMessage implements AutoCloseable {

    private final NativeMessage nativeMessage;
    private final int wireVersion;
    private final int messageTypeId;
    private final long messageId;
    private final long correlationId;
    private final MemorySegment payload;

    public NativeWireMessage(
            NativeMessage nativeMessage,
            int wireVersion,
            int messageTypeId,
            long messageId,
            long correlationId,
            MemorySegment payload
    ) {
        this.nativeMessage = Objects.requireNonNull(nativeMessage, "nativeMessage");
        this.wireVersion = wireVersion;
        this.messageTypeId = messageTypeId;
        this.messageId = messageId;
        this.correlationId = correlationId;
        this.payload = Objects.requireNonNull(payload, "payload");
    }

    public int wireVersion() {
        return wireVersion;
    }

    public int messageTypeId() {
        return messageTypeId;
    }

    public long messageId() {
        return messageId;
    }

    public long correlationId() {
        return correlationId;
    }

    /** Valid only while this message remains open. */
    public MemorySegment payload() {
        if (!nativeMessage.isOpen()) {
            throw new IllegalStateException("Native wire message is closed");
        }
        return payload;
    }

    public byte[] copyPayload() {
        return payload().toArray(ValueLayout.JAVA_BYTE);
    }

    @Override
    public void close() {
        nativeMessage.close();
    }
}
