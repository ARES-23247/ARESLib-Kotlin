package org.frcforftc.networktables;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an entry in the NetworkTables with a specific topic, value, and associated listeners.
 */
public class NetworkTablesEntry {
    private final String m_topic;
    private final Map<NetworkTablesEvent, List<NetworkTablesEventListener>> m_listeners = new ConcurrentHashMap<>();
    private final Map<String, NetworkTablesEntry> m_properties = new ConcurrentHashMap<>();
    private int m_id = -1;
    private NetworkTablesValue m_localValue;

    /**
     * Constructs a NetworkTablesEntry with the specified topic, and initial value.
     *
     * @param topic      the topic name of the entry
     * @param localValue the initial value of the entry
     */
    public NetworkTablesEntry(String topic, NetworkTablesValue localValue) {
        this.m_topic = topic;
        update(localValue);
    }

    public NetworkTablesEntry(String topic, Object value) {
        this.m_topic = topic;
        if (!(value instanceof NetworkTablesValue)) {
            update(new NetworkTablesValue(value));
        } else {
            update(value);
        }
    }

    public void addProperty(NetworkTablesEntry value) {
        m_properties.put(value.getTopic(), value);
    }

    public void addProperty(String key, Object value) {
        NetworkTablesEntry createdEntry = new NetworkTablesEntry(key, new NetworkTablesValue(value, NetworkTablesValueType.determineType(value)));
        addProperty(createdEntry);
    }

    public void removeProperty(String key) {
        m_properties.remove(key);
    }

    public NetworkTablesEntry[] getProperties() {
        return m_properties.values().toArray(new NetworkTablesEntry[0]);
    }

    public NetworkTablesEntry getProperty(String key) {
        return m_properties.get(key);
    }

    /**
     * Adds a listener for specific events to this entry.
     *
     * @param l the event listener to be added
     */
    public void addListener(NetworkTablesEventListener l) {
        EnumSet<NetworkTablesEvent> eventTypes = l.getEventTypes();

        for (NetworkTablesEvent type : eventTypes) {
            if (!m_listeners.containsKey(type)) {
                ArrayList<NetworkTablesEventListener> events = new ArrayList<>();
                events.add(l);
                m_listeners.put(type, events);
            } else {
                m_listeners.get(type).add(l);
            }
        }
    }

    /**
     * Retrieves the current value of this entry.
     *
     * @return the current NetworkTablesValue of the entry
     */
    public NetworkTablesValue getValue() {
        return m_localValue;
    }

    /**
     * Updates the value of this entry with a new NetworkTablesValue.
     *
     * @param newValue the new NetworkTablesValue to be set
     */
    public void update(NetworkTablesValue newValue) {
        if (!Objects.equals(newValue.getType(), NetworkTablesValueType.Unknown.typeString) || NetworkTablesValueType.getFromString(newValue.getType()) == NetworkTablesValueType.Unknown) // Doesnt actually fix the reconnection issue
            this.m_localValue = newValue;
    }

    /**
     * Updates the value of this entry with a new value object, creating a NetworkTablesValue with the current type.
     *
     * @param val the new value to be set
     */
    public void update(Object val) {
        update(new NetworkTablesValue(val, m_localValue.getType()));
    }

    /**
     * Calls the listeners associated with the specified event type.
     *
     * @param eventTypes the event type for which listeners are to be called
     * @param entry      the NetworkTablesEntry that triggered the event
     * @param value      the new value associated with the event
     */
    void callListenersOfEventType(NetworkTablesEvent eventTypes, NetworkTablesEntry entry, NetworkTablesValue value) {
        if (m_listeners.get(eventTypes) == null) return;
        for (NetworkTablesEventListener listener : m_listeners.get(eventTypes)) {
            listener.apply(eventTypes);
        }
    }

    /**
     * Retrieves the topic of this entry.
     *
     * @return the topic name of the entry
     */
    public String getTopic() {
        return m_topic;
    }

    public int getId() {
        return m_id;
    }

    public void setId(int m_id) {
        this.m_id = m_id;
    }
}
