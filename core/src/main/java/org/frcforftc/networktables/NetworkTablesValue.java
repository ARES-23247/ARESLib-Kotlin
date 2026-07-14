package org.frcforftc.networktables;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Represents a value in the network tables with a specified type.
 * This class encapsulates a value and its type, providing methods to retrieve the value.
 */
public class NetworkTablesValue {
    private final Supplier<?> m_value;
    private final Consumer<?> m_setter;
    private final String m_type;

    /**
     * Constructs a NetworkTablesValue with a specific value and type.
     *
     * @param value the value to be stored
     * @param type  the type of the value
     */
    public NetworkTablesValue(Object value, NetworkTablesValueType type) {
        this(() -> value, type);
    }

    /**
     * Constructs a NetworkTablesValue with a specific value and type string.
     *
     * @param value the value to be stored
     * @param type  the type of the value as a string
     */
    public NetworkTablesValue(Object value, String type) {
        this(() -> value, NetworkTablesValueType.getFromString(type));
    }

    public NetworkTablesValue(Object value) {
        this(value, NetworkTablesValueType.determineType(value));
    }

    /**
     * Constructs a NetworkTablesValue with a supplier for the value and a type string.
     *
     * @param value a supplier that provides the value
     * @param type  the type of the value as a string
     */
    public NetworkTablesValue(Supplier<?> value, String type) {
        this(value, NetworkTablesValueType.getFromString(type));
    }

    /**
     * Constructs a NetworkTablesValue with a supplier for the value and a specific type.
     *
     * @param getter a supplier that provides the value
     * @param type   the type of the value
     */
    public NetworkTablesValue(Supplier<?> getter, NetworkTablesValueType type) {
        this(getter, null, type);
    }

    public <T> NetworkTablesValue(Supplier<T> getter, Consumer<T> setter, NetworkTablesValueType type) {
        this.m_setter = setter;
        this.m_value = getter;
        this.m_type = type.typeString;
    }

    public <T> NetworkTablesValue(Supplier<T> getter, Consumer<T> setter, String type) {
        this.m_setter = setter;
        this.m_value = getter;
        this.m_type = type;
    }

    /**
     * Retrieves the value as a specific type.
     *
     * @param <T> the type of the value
     * @return the value cast to the specified type
     */
    @SuppressWarnings("unchecked")
    public <T> T getAs() {
        return (T) m_value.get();
    }

    /**
     * Retrieves the value as an object.
     *
     * @return the value as an object
     */
    public Object get() {
        return m_value.get();
    }

    /**
     * Retrieves the supplier for the value.
     *
     * @return the supplier providing the value
     */
    public Supplier<?> getGetter() {
        return m_value;
    }

    /**
     * Retrieves the type of the value.
     *
     * @return the type of the value
     */
    public String getType() {
        return m_type;
    }

    public Consumer<?> getSetter() {
        return m_setter;
    }
}
