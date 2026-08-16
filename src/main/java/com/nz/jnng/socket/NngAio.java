package com.nz.jnng.socket;

import com.nz.jnng.constants.NngErrorCode;
import com.nz.jnng.exception.NngException;
import com.nz.jnng.nng_aio_alloc$x0;
import com.nz.jnng.nng_h;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Shared Panama upcall used by all one-shot NNG AIO operations. */
final class NngAio {

    private static final Arena CALLBACK_ARENA = Arena.ofAuto();
    private static final ConcurrentMap<Long, Operation<?>> OPERATIONS = new ConcurrentHashMap<>();
    private static final MemorySegment CALLBACK = nng_aio_alloc$x0.allocate(
            NngAio::complete,
            CALLBACK_ARENA
    );

    private NngAio() {
    }

    static CompletableFuture<Void> send(
            MemorySegment socket,
            byte[] payload,
            Optional<Duration> timeout
    ) {
        Objects.requireNonNull(payload, "payload");
        NativeMessage message = NativeMessage.copyOf(payload);
        SendOperation operation = new SendOperation(socket, message, timeoutMillis(timeout));
        operation.start();
        return operation.future;
    }

    static CompletableFuture<NativeMessage> receive(
            MemorySegment socket,
            Optional<Duration> timeout
    ) {
        ReceiveOperation operation = new ReceiveOperation(socket, timeoutMillis(timeout));
        operation.start();
        return operation.future;
    }

    private static int timeoutMillis(Optional<Duration> timeout) {
        return timeout.map(NngSocketConfig::toNngMillis).orElse(-1);
    }

    private static void complete(MemorySegment token) {
        Operation<?> operation = OPERATIONS.remove(token.address());
        if (operation != null) {
            operation.completeFromNative();
        }
    }

    private abstract static class Operation<T> {
        final MemorySegment socket;
        final Arena arena = Arena.ofShared();
        final MemorySegment token = arena.allocate(1);
        final MemorySegment aioPointer = arena.allocate(ValueLayout.ADDRESS);
        final CompletableFuture<T> future = new CompletableFuture<>();
        final int timeoutMillis;
        MemorySegment aio;

        Operation(MemorySegment socket, int timeoutMillis) {
            this.socket = socket;
            this.timeoutMillis = timeoutMillis;
        }

        final void allocateAio() {
            OPERATIONS.put(token.address(), this);
            int rc = nng_h.nng_aio_alloc(aioPointer, CALLBACK, token);
            if (rc != NngErrorCode.OK) {
                OPERATIONS.remove(token.address(), this);
                arena.close();
                throw new NngException(rc);
            }
            aio = aioPointer.get(ValueLayout.ADDRESS, 0);
            nng_h.nng_aio_set_timeout(aio, timeoutMillis);
            future.whenComplete((ignored, error) -> {
                if (future.isCancelled()) {
                    nng_h.nng_aio_cancel(aio);
                }
            });
        }

        abstract void start();

        abstract void complete(int result);

        final void completeFromNative() {
            int result = nng_h.nng_aio_result(aio);
            try {
                complete(result);
            } catch (Throwable error) {
                future.completeExceptionally(error);
            } finally {
                nng_h.nng_aio_reap(aio);
                arena.close();
            }
        }
    }

    private static final class SendOperation extends Operation<Void> {
        private final NativeMessage message;

        SendOperation(MemorySegment socket, NativeMessage message, int timeoutMillis) {
            super(socket, timeoutMillis);
            this.message = message;
        }

        @Override
        void start() {
            try {
                allocateAio();
                nng_h.nng_aio_set_msg(aio, message.handle());
                nng_h.nng_send_aio(socket, aio);
            } catch (Throwable error) {
                message.close();
                throw error;
            }
        }

        @Override
        void complete(int result) {
            if (result == NngErrorCode.OK) {
                message.transferredToNng();
                future.complete(null);
            } else {
                nng_h.nng_aio_set_msg(aio, MemorySegment.NULL);
                message.close();
                future.completeExceptionally(new NngException(result));
            }
        }
    }

    private static final class ReceiveOperation extends Operation<NativeMessage> {

        ReceiveOperation(MemorySegment socket, int timeoutMillis) {
            super(socket, timeoutMillis);
        }

        @Override
        void start() {
            allocateAio();
            nng_h.nng_recv_aio(socket, aio);
        }

        @Override
        void complete(int result) {
            if (result == NngErrorCode.OK) {
                MemorySegment message = nng_h.nng_aio_get_msg(aio);
                nng_h.nng_aio_set_msg(aio, MemorySegment.NULL);
                future.complete(NativeMessage.adopt(message));
            } else {
                future.completeExceptionally(new NngException(result));
            }
        }
    }
}
