package org.frcforftc.networktables;

import java.util.EnumSet;
import java.util.function.Consumer;

/**
 * Listens for specific NetworkTables events and executes a callback when those events occur.
 */
public class NetworkTablesEventListener {
    /**
     * The consumer that handles the event when it occurs.
     */
    private final Consumer<NetworkTablesEvent> m_listener;

    /**
     * The set of events that this listener is interested in.
     */
    private final EnumSet<NetworkTablesEvent> m_eventTypes;

    /**
     * Constructs a new `NetworkTablesEventListener` with the specified event types and listener.
     *
     * @param events   The set of events this listener is interested in.
     * @param listener The callback to be executed when an event occurs.
     */
    public NetworkTablesEventListener(EnumSet<NetworkTablesEvent> events, Consumer<NetworkTablesEvent> listener) {
        this.m_listener = listener;
        this.m_eventTypes = events;
    }

    /**
     * Applies the listener to the specified event.
     *
     * @param event The event to be handled by the listener.
     */
    public void apply(NetworkTablesEvent event) {
        m_listener.accept(event);
    }

    /**
     * Returns the set of events that this listener is interested in.
     *
     * @return The set of event types.
     */
    public EnumSet<NetworkTablesEvent> getEventTypes() {
        return m_eventTypes;
    }
}
