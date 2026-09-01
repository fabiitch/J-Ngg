package com.nz.jnng.service.channel;

import com.nz.jnng.Subscription;
import com.nz.jnng.service.codec.ChannelMessageCodec;
import com.nz.jnng.service.listener.ChannelMessageListener;
import com.nz.jnng.socket.INngSocket;
import com.nz.jnng.socket.NativeMessage;
import com.nz.jnng.socket.NngCallbackBridge;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/** Common AIO receive loop for PAIR, SUB and PULL channels. */
public abstract class AbstractReceivingChannel extends AbstractChannel {
    private final AtomicReference<CompletableFuture<NativeMessage>> pendingReceive =
            new AtomicReference<>();
    private final AtomicInteger pendingDispatches = new AtomicInteger();
    private final LongAdder droppedMessages = new LongAdder();

    protected AbstractReceivingChannel(
            ChannelConfiguration configuration,
            Executor dispatcherExecutor,
            Supplier<? extends INngSocket> socketFactory
    ) {
        super(configuration, dispatcherExecutor, socketFactory);
    }

    public final <T> Subscription registerMessage(
            int messageTypeId,
            Class<T> messageType,
            ChannelMessageCodec<T> codec,
            ChannelMessageListener<T> listener
    ) {
        return registerIncomingMessage(messageTypeId, messageType, codec, listener);
    }

    @Override
    protected final void onOpened() {
        armReceive();
        afterReceiveLoopStarted();
    }

    protected void afterReceiveLoopStarted() {
    }

    /** Number of messages discarded locally because the application queue was full. */
    public final long droppedMessages() {
        return droppedMessages.sum();
    }

    @Override
    protected void onClosing() {
        CompletableFuture<NativeMessage> operation = pendingReceive.getAndSet(null);
        if (operation != null) operation.cancel(true);
    }

    private void armReceive() {
        if (!isOpen()) return;
        CompletableFuture<NativeMessage> operation = socket().receiveNativeAsync();
        pendingReceive.set(operation);
        operation.whenComplete((message, error) -> {
            pendingReceive.compareAndSet(operation, null);
            if (!isOpen()) {
                if (message != null) message.close();
                return;
            }
            if (error != null) {
                if (!isClosingOrClosedError(error) && !isTimeoutError(error)) {
                    reportError(error);
                }
                armReceive();
                return;
            }

            if (!tryReserveDispatchSlot()) {
                message.close();
                droppedMessages.increment();
                armReceive();
                return;
            }

            // Keep decoding and codec work out of the native NNG callback stack.
            armReceive();
            NngCallbackBridge.execute(() -> decodeAndDispatch(message));
        });
    }

    private void decodeAndDispatch(NativeMessage message) {
        if (!isOpen()) {
            message.close();
            pendingDispatches.decrementAndGet();
            return;
        }
        WireEnvelope envelope;
        try {
            envelope = decodeEnvelope(message);
        } catch (Throwable decodeError) {
            pendingDispatches.decrementAndGet();
            reportError(decodeError);
            return;
        }

        dispatchToApplication(() -> {
            try {
                if (isOpen()) dispatchMessage(envelope);
            } catch (Throwable handlerError) {
                reportError(handlerError);
            } finally {
                pendingDispatches.decrementAndGet();
            }
        }, pendingDispatches::decrementAndGet);
    }

    private boolean tryReserveDispatchSlot() {
        int capacity = configuration().queuePolicy().capacity();
        while (true) {
            int current = pendingDispatches.get();
            if (current >= capacity) return false;
            if (pendingDispatches.compareAndSet(current, current + 1)) return true;
        }
    }
}
