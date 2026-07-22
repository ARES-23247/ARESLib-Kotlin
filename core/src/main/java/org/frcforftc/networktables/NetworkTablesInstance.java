package org.frcforftc.networktables;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton class representing the NetworkTables instance.
 * Provides methods to start and stop the {@link NT4Client} and {@link NT4Server},
 * and to manage NetworkTables entries.
 */
public class NetworkTablesInstance {

    /**
     * Singleton instance of {@link NetworkTablesInstance}.
     * Ensures only one instance of this class exists.
     */
    private static final NetworkTablesInstance m_instance = new NetworkTablesInstance();

    /**
     * A thread-safe map to store NetworkTables entries, where each entry is associated with a topic name.
     */
    private final Map<String, NetworkTablesEntry> m_entries = new ConcurrentHashMap<>();

    /**
     * Instance of {@link NT4Client} used to connect to the NT4 server.
     */
    private NT4Client m_client = null;

    /**
     * Instance of {@link NT4Server} used to manage server-side operations.
     */
    private NT4Server m_server = null;

    /**
     * Returns the default singleton instance of {@link NetworkTablesInstance}.
     * This method ensures that only one instance of the class is used throughout the application.
     *
     * @return the singleton instance of {@link NetworkTablesInstance}
     */
    public static NetworkTablesInstance getDefaultInstance() {
        return m_instance;
    }

    /**
     * Returns the map of NetworkTables entries.
     * This map contains all the entries with their associated topics.
     *
     * @return the map of NetworkTables entries
     */
    public Map<String, NetworkTablesEntry> getEntries() {
        return m_entries;
    }

    /**
     * Starts the NT4 server.
     * Creates an instance of {@link NT4Server}, initializes it, and starts it.
     * This method should be called before any client operations can occur.
     */
    public void startNT4Server(String address, int port) {
        this.m_server = NT4Server.createInstance(address, port);
        m_server.start();
    }

    /**
     * Starts the NT4 client.
     * Creates an instance of {@link NT4Client} with the given URI and attempts to connect it to the server.
     *
     * @param path the URI for the NT4 server
     * @throws InvalidURIException if the URI syntax is invalid
     */
    public void startNT4Client(URI path) {
        this.m_client = new NT4Client(path);
        m_client.connect();
    }

    /**
     * Puts a numeric value into the specified topic.
     * The value must not be an instance of {@link NetworkTablesEntry}. If it is, an {@link IllegalArgumentException} is thrown.
     *
     * @param topic  the topic name
     * @param number the {@link Number} value to put into the topic
     */
    public void putNumber(String topic, Number number) {
        m_server.putTopic(topic, number);
    }

    /**
     * Puts an array of double values into the specified topic.
     *
     * @param topic the topic name
     * @param arr   the {@link double} array to put into the topic
     */
    public void putNumberArray(String topic, double[] arr) {
        m_server.putTopic(topic, arr);
    }

    /**
     * Puts an array of int values into the specified topic.
     *
     * @param topic the topic name
     * @param arr   the {@link int} array to put into the topic
     */
    public void putNumberArray(String topic, int[] arr) {
        m_server.putTopic(topic, arr);
    }

    /**
     * Puts an array of float values into the specified topic.
     *
     * @param topic the topic name
     * @param arr   the {@link float} array to put into the topic
     */
    public void putNumberArray(String topic, float[] arr) {
        m_server.putTopic(topic, arr);
    }

    /**
     * Puts a string value into the specified topic.
     * The value must not be an instance of {@link NetworkTablesEntry}. If it is, an {@link IllegalArgumentException} is thrown.
     *
     * @param topic the topic name
     * @param text  the {@link String} value to put into the topic
     */
    public void putString(String topic, String text) {
        m_server.putTopic(topic, text);
    }

    /**
     * Puts an array of string values into the specified topic.
     *
     * @param topic the topic name
     * @param arr   the {@link String} array to put into the topic
     */
    public void putStringArray(String topic, String[] arr) {
        m_server.putTopic(topic, arr);
    }

    /**
     * Puts a boolean value into the specified topic.
     *
     * @param topic the topic name
     * @param b     the {@link boolean} value to put into the topic
     */
    public void putBoolean(String topic, boolean b) {
        m_server.putTopic(topic, b);
    }

    /**
     * Puts an array of boolean values into the specified topic.
     *
     * @param topic the topic name
     * @param arr   the {@link boolean} array to put into the topic
     */
    public void putBooleanArray(String topic, boolean[] arr) {
        m_server.putTopic(topic, arr);
    }

    /**
     * Gets the {@link NetworkTablesEntry} for the specified topic.
     * This method retrieves the entry associated with the given topic from the server.
     *
     * @param topic the topic name
     * @return the {@link NetworkTablesEntry} for the specified topic, or null if not found
     */
    public NetworkTablesEntry get(String topic) {
        if (m_server == null) return null;
        NetworkTablesEntry entry = m_server.getEntries().get(topic);
        if (entry == null && topic.startsWith("/")) {
            entry = m_server.getEntries().get(topic.substring(1));
        }
        if (entry == null && !topic.startsWith("/")) {
            entry = m_server.getEntries().get("/" + topic);
        }
        return entry;
    }


    /**
     * Stops the NT4 server.
     * Attempts to stop the server and throws a {@link ServerStopException} if interrupted.
     */
    public void closeServer() {
        try {
            if (this.m_server != null) {
                this.m_server.stop();
                this.m_server = null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupt status
            throw new ServerStopException("Interrupted while stopping the NT4 server.", e);
        }
    }

    /**
     * Closes the NT4 client connection.
     * Sends a close frame to the client with code 1000 and reason "Closed client", and then closes the client.
     */
    public void closeClient() {
        if (this.m_client != null) {
            this.m_client.closeConnection(1000, "Closed client");
            this.m_client.close();
        }
    }

    public NT4Server getServer() {
        return m_server;
    }

    public void flushServer() {
        if (m_server != null) {
            m_server.flush();
        }
    }

    /**
     * Custom exception class for invalid URI syntax errors in NT4Client initialization.
     */
    public static class InvalidURIException extends RuntimeException {
        public InvalidURIException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Custom exception class for errors occurring while stopping the NT4 server.
     */
    public static class ServerStopException extends RuntimeException {
        public ServerStopException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
