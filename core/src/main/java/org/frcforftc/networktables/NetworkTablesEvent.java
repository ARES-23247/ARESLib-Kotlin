package org.frcforftc.networktables;

/**
 * Represents the various types of events that can occur in the NetworkTables system.
 */
public enum NetworkTablesEvent {
    /**
     * Event indicating that the topic has been updated.
     */
    kTopicUpdated,

    /**
     * Event indicating that the topic has been published.
     */
    kTopicPublished,

    /**
     * Event indicating that the topic has been announced.
     */
    kTopicAnnounced,

    /**
     * Event indicating that the topic has been unannounced.
     */
    kTopicUnAnnounced,

    /**
     * Event indicating that a connection has been established.
     */
    kConnected,
}
