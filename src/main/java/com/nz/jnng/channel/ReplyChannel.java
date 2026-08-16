package com.nz.jnng.channel;

import com.nz.jnng.Subscription;
import com.nz.jnng.codec.WireCodec;
import com.nz.jnng.message.NativeWireMessage;
import com.nz.jnng.message.WireMessage;
import com.nz.jnng.socket.INngSocket;
import com.nz.jnng.socket.NativeMessage;
import com.nz.jnng.exception.NngException;
import com.nz.jnng.constants.NngErrorCode;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** REP channel preserving the required receive-then-reply state machine. */
public final class ReplyChannel extends AbstractChannel {
    public ReplyChannel(INngSocket socket, WireCodec codec) { super(socket, codec); }

    public WireMessage receiveRequest() { return receiveMessage(); }
    public void reply(WireMessage response) { sendMessage(response); }

    public Subscription onRequest(Function<WireMessage, WireMessage> handler) {
        Objects.requireNonNull(handler, "handler");
        return onNativeRequest(NngChannelExecutors.shared(), nativeRequest -> handler.apply(
                new WireMessage(
                        nativeRequest.wireVersion(), nativeRequest.messageTypeId(),
                        nativeRequest.messageId(), nativeRequest.correlationId(),
                        nativeRequest.copyPayload())));
    }

    public Subscription onRequest(
            Executor executor,
            Function<WireMessage, WireMessage> handler
    ) {
        Objects.requireNonNull(handler, "handler");
        return onNativeRequest(executor, nativeRequest -> handler.apply(new WireMessage(
                nativeRequest.wireVersion(), nativeRequest.messageTypeId(),
                nativeRequest.messageId(), nativeRequest.correlationId(),
                nativeRequest.copyPayload())));
    }

    public Subscription onNativeRequest(
            Executor executor,
            Function<NativeWireMessage, WireMessage> handler
    ) {
        return new ReplySubscription(executor, handler);
    }

    public Subscription onNativeRequest(
            Function<NativeWireMessage, WireMessage> handler
    ) {
        return onNativeRequest(NngChannelExecutors.shared(), handler);
    }

    private final class ReplySubscription implements Subscription {
        private final Executor executor;
        private final Function<NativeWireMessage, WireMessage> handler;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicReference<CompletableFuture<?>> pending = new AtomicReference<>();

        private ReplySubscription(
                Executor executor,
                Function<NativeWireMessage, WireMessage> handler
        ) {
            this.executor = Objects.requireNonNull(executor, "executor");
            this.handler = Objects.requireNonNull(handler, "handler");
            arm();
        }

        private void arm() {
            if (!active.get()) return;
            CompletableFuture<NativeMessage> receive = socket.receiveNativeAsync();
            pending.set(receive);
            receive.whenComplete((nativeMessage, receiveError) -> {
                if (!active.get()) {
                    if (nativeMessage != null) nativeMessage.close();
                    return;
                }
                if (receiveError != null) {
                    Throwable cause = receiveError.getCause() == null
                            ? receiveError : receiveError.getCause();
                    if (cause instanceof NngException nngError
                            && nngError.getCode() == NngErrorCode.ETIMEDOUT) {
                        arm();
                    } else {
                        report(cause);
                        active.set(false);
                    }
                    return;
                }
                try {
                    executor.execute(() -> handleAndReply(nativeMessage));
                } catch (RejectedExecutionException rejected) {
                    nativeMessage.close();
                    report(rejected);
                    active.set(false);
                }
            });
        }

        private void handleAndReply(NativeMessage nativeMessage) {
            WireMessage reply;
            try (nativeMessage) {
                try (NativeWireMessage request = codec.decodeNative(nativeMessage)) {
                    reply = Objects.requireNonNull(handler.apply(request), "handler reply");
                }
            } catch (Throwable error) {
                report(error);
                active.set(false);
                return;
            }

            CompletableFuture<Void> send = sendMessageAsync(reply);
            pending.set(send);
            send.whenComplete((ignored, sendError) -> {
                if (sendError != null) {
                    report(sendError);
                    active.set(false);
                } else {
                    arm();
                }
            });
        }

        @Override
        public void close() {
            if (active.compareAndSet(true, false)) {
                CompletableFuture<?> operation = pending.getAndSet(null);
                if (operation != null) operation.cancel(true);
            }
        }

        @Override
        public boolean isActive() { return active.get(); }

        private void report(Throwable error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            Thread.currentThread().getUncaughtExceptionHandler()
                    .uncaughtException(Thread.currentThread(), cause);
        }
    }
}
