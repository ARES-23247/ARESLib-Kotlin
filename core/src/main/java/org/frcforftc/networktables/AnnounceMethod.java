package org.frcforftc.networktables;

/**
 * A functional interface for announcing or publishing a topic and its associated value.
 * This interface defines a single method, {@link #apply}, which takes a topic name and
 * a value to be announced.
 */
@FunctionalInterface
public interface AnnounceMethod {
    /**
     * Applies the announcement method for a given topic and value.
     *
     * @param topic the name of the topic to be announced
     * @param value the value associated with the topic
     */
    void apply(String topic, Object value);
}
