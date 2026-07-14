package org.frcforftc.networktables;

/**
 * Represents a message in the NetworkTables system.
 */
public class NetworkTablesMessage {
    /**
     * Unique identifier for the message.
     */
    public long id;

    /**
     * Timestamp indicating when the message was created.
     */
    public long stamp;

    /**
     * Data type of the message value.
     */
    public int dataType;

    /**
     * The value associated with the message.
     */
    public Object dataValue;

    /**
     * Constructs a new `NetworkTablesMessage` with the specified parameters.
     *
     * @param id        The unique identifier for the message.
     * @param stamp     The timestamp when the message was created.
     * @param dataType  The data type of the message value.
     * @param dataValue The value associated with the message.
     */
    public NetworkTablesMessage(long id, long stamp, int dataType, Object dataValue) {
        this.id = id;
        this.stamp = stamp;
        this.dataType = dataType;
        this.dataValue = dataValue;
    }
}
