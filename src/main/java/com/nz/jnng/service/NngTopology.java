package com.nz.jnng.service;

import com.nz.jnng.ConnectionMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable PAIR topology. The first endpoint of a link listens. */
public final class NngTopology<E> {
    private final List<Link<E>> links;

    private NngTopology(List<Link<E>> links) {
        this.links = List.copyOf(links);
    }

    public static <E> Builder<E> builder() {
        return new Builder<>();
    }

    public List<Link<E>> linksFor(E endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        return links.stream().filter(link -> link.contains(endpoint)).toList();
    }

    public record Link<E>(E listener, E dialer, String address) {
        public Link {
            Objects.requireNonNull(listener, "listener");
            Objects.requireNonNull(dialer, "dialer");
            Objects.requireNonNull(address, "address");
            if (listener.equals(dialer)) {
                throw new IllegalArgumentException("A topology link needs two different endpoints");
            }
            if (address.isBlank()) throw new IllegalArgumentException("address must not be blank");
        }

        public boolean contains(E endpoint) {
            return listener.equals(endpoint) || dialer.equals(endpoint);
        }

        public E peerOf(E endpoint) {
            if (listener.equals(endpoint)) return dialer;
            if (dialer.equals(endpoint)) return listener;
            throw new IllegalArgumentException(endpoint + " does not belong to this link");
        }

        public ConnectionMode modeOf(E endpoint) {
            if (listener.equals(endpoint)) return ConnectionMode.LISTEN;
            if (dialer.equals(endpoint)) return ConnectionMode.DIAL;
            throw new IllegalArgumentException(endpoint + " does not belong to this link");
        }
    }

    public static final class Builder<E> {
        private final List<Link<E>> links = new ArrayList<>();
        private final Map<Set<E>, Link<E>> byPair = new HashMap<>();

        private Builder() {
        }

        /** Adds a link where {@code listener} owns the address and {@code dialer} connects. */
        public Builder<E>   link(E listener, E dialer, String address) {
            Link<E> link = new Link<>(listener, dialer, address);
            Set<E> pair = new HashSet<>(List.of(listener, dialer));
            Link<E> conflict = byPair.putIfAbsent(pair, link);
            if (conflict != null) {
                throw new IllegalArgumentException("Duplicate topology link between "
                        + listener + " and " + dialer);
            }
            links.add(link);
            return this;
        }

        public NngTopology<E> build() {
            return new NngTopology<>(links);
        }
    }
}
