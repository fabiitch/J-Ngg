package com.nz.jnng.neww;

import com.nz.jnng.Subscription;
import com.nz.jnng.neww.codec.ChannelMessageCodec;
import com.nz.jnng.neww.listener.ChannelMessageListener;
import com.nz.jnng.socket.INngSocket;
import com.nz.jnng.socket.NativeMessage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Common AIO receive loop for PAIR, SUB and PULL channels. */
public abstract class AbstractReceivingChannel extends AbstractChannel {
    private final AtomicReference<CompletableFuture<NativeMessage>> pendingReceive =
            new AtomicReference<>();

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

            WireEnvelope envelope;
            try {
                envelope = decodeEnvelope(message);
            } catch (Throwable decodeError) {
                reportError(decodeError);
                armReceive();
                return;
            }

            armReceive();
            execute(() -> {
                try {
                    dispatchMessage(envelope);
                } catch (Throwable handlerError) {
                    reportError(handlerError);
                }
            });
        });
    }
}
