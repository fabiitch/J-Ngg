package com.nz.jnng.service.listener;

/** Handles one REP request type and returns a registered response message. */
@FunctionalInterface
public interface ChannelRequestHandler<T> {
    Object onRequest(T request) throws Exception;
}
