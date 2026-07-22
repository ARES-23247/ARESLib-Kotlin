package org.frcforftc.networktables;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.protocols.IProtocol;
import org.java_websocket.protocols.Protocol;
import org.java_websocket.server.WebSocketServer;
import org.msgpack.core.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class NT4Server extends WebSocketServer {
    private static final Map<String, NetworkTablesEntry> m_entries = new ConcurrentHashMap<>();
    private static final Map<Long, NetworkTablesEntry> m_publisherUIDSMap = new ConcurrentHashMap<>();
    private static NT4Server m_server = null;
    private static boolean m_shutdownHookAdded = false;
    private final Set<WebSocket> m_connections = new CopyOnWriteArraySet<>();
    // Maps prefix to subscribers
    private final Map<String, Set<WebSocket>> m_clientSubscriptions = new ConcurrentHashMap<>();
    private final Set<NetworkTablesEntry> m_dirtyEntries = new CopyOnWriteArraySet<>();
    private final ObjectMapper m_objectMapper = new ObjectMapper();

    private org.msgpack.core.MessageBufferPacker m_packer;

    public NT4Server(InetSocketAddress address, Draft_6455 draft_protocols) {
        super(address, Collections.singletonList(draft_protocols));
        org.msgpack.core.MessageBufferPacker tempPacker;
        try {
            tempPacker = org.msgpack.core.MessagePack.newDefaultBufferPacker();
        } catch (Throwable t) {
            tempPacker = new org.msgpack.core.MessagePack.PackerConfig().newBufferPacker();
        }
        this.m_packer = tempPacker;
    }


    public static NT4Server createInstance(String address, int port) {
        ArrayList<IProtocol> protocols = new ArrayList<IProtocol>();
        protocols.add(new Protocol("v4.1.networktables.first.wpi.edu"));
        protocols.add(new Protocol("rtt.networktables.first.wpi.edu"));
        Draft_6455 draft_protocols = new Draft_6455(Collections.emptyList(), protocols);
        m_server = new NT4Server(new InetSocketAddress(address, port), draft_protocols);
        m_server.setConnectionLostTimeout(Integer.MAX_VALUE);
        if (!m_shutdownHookAdded) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    m_server.stop(0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            m_shutdownHookAdded = true;
        }
        return m_server;
    }

    public NetworkTablesEntry getSubTable(String path) {
        return m_entries.get("/" + path);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        setConnectionLostTimeout(Integer.MAX_VALUE);
        m_connections.add(conn);
        String subprotocol = handshake.getFieldValue("Sec-WebSocket-Protocol");
        if(subprotocol == null) return;
        for (String s : subprotocol.split(", ")) {
            if (s.equals("v4.1.networktables.first.wpi.edu")) {
                conn.setAttachment(s);
                for (NetworkTablesEntry entry : m_entries.values()) {
                    announceEntry(entry);
                }
            }
            if (s.equals("rtt.networktables.first.wpi.edu")) {
                conn.setAttachment(s);
                try {
                    heartbeat(conn, System.currentTimeMillis() * 1000L);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        m_connections.remove(conn);
        for (Set<WebSocket> subscribers : m_clientSubscriptions.values()) {
            subscribers.remove(conn);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            JsonNode data = m_objectMapper.readTree(message);
            if (data.isArray()) {
                for (JsonNode node : data) {
                    processMessage(conn, node);
                }
            } else {
                processMessage(conn, data);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        try {
            NetworkTablesMessage decodedMessage = decodeNT4Message(message);
            if (decodedMessage.id == -1) {
                heartbeat(conn, (Long) decodedMessage.dataValue);
            } else {
                if (m_publisherUIDSMap.containsKey(decodedMessage.id)) {
                    NetworkTablesEntry entry = m_publisherUIDSMap.get(decodedMessage.id);
                    if (!decodedMessage.dataValue.equals(entry.getValue().get())) {
                        NetworkTablesValue newValue = new NetworkTablesValue(decodedMessage.dataValue, entry.getValue().getType());
                        entry.update(newValue);
                        m_publisherUIDSMap.put(decodedMessage.id, entry);
                        entry.callListenersOfEventType(NetworkTablesEvent.kTopicUpdated, entry, newValue);
                        m_dirtyEntries.add(entry);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
    }

    public synchronized byte[] encodeNT4Messages(long timestamp, java.util.List<NetworkTablesEntry> entries) throws IOException {
        m_packer.clear();

        for (NetworkTablesEntry entry : entries) {
            int dataType = NetworkTablesValueType.getFromString(entry.getValue().getType()).id;
            Object dataValue = entry.getValue().getAs();

            m_packer.packArrayHeader(4);
            m_packer.packLong(entry.getId());
            m_packer.packLong(timestamp);
            m_packer.packInt(dataType);

            packDataValue(dataType, dataValue);
        }

        return m_packer.toByteArray();
    }

    public synchronized byte[] encodeNT4Message(long timestamp, long topicId, long pubUID, int dataType, Object dataValue) throws IOException {
        m_packer.clear();

        // Previous implementation sent a single array without wrapping. 
        // We preserve it here for legacy single-message compatibility, 
        // though encodeNT4Messages is strictly correct.
        m_packer.packArrayHeader(4);
        m_packer.packLong(topicId);
        m_packer.packLong(timestamp);
        m_packer.packInt(dataType);

        packDataValue(dataType, dataValue);

        return m_packer.toByteArray();
    }

    private void packDataValue(int dataType, Object dataValue) throws IOException {
        NetworkTablesValueType dataTypeAsEnum = NetworkTablesValueType.getFromId(dataType);
        switch (dataTypeAsEnum) {
            case Boolean:
                m_packer.packBoolean((Boolean) dataValue);
                break;
            case Double:
                m_packer.packDouble(((Number) dataValue).doubleValue());
                break;
            case Int:
                m_packer.packLong(((Number) dataValue).longValue());
                break;
            case Float:
                m_packer.packFloat(((Number) dataValue).floatValue());
                break;
            case String:
                m_packer.packString((String) dataValue);
                break;
            case BooleanArray:
                boolean[] bArray = (boolean[]) dataValue;
                m_packer.packArrayHeader(bArray.length);
                for (boolean b : bArray) m_packer.packBoolean(b);
                break;
            case DoubleArray:
                double[] dArray = (double[]) dataValue;
                m_packer.packArrayHeader(dArray.length);
                for (double d : dArray) m_packer.packDouble(d);
                break;
            case IntArray:
                int[] iArray = (int[]) dataValue;
                m_packer.packArrayHeader(iArray.length);
                for (int i : iArray) m_packer.packLong(i);
                break;
            case FloatArray:
                float[] fArray = (float[]) dataValue;
                m_packer.packArrayHeader(fArray.length);
                for (float f : fArray) m_packer.packFloat(f);
                break;
            case StringArray:
                String[] sArray = (String[]) dataValue;
                m_packer.packArrayHeader(sArray.length);
                for (String s : sArray) m_packer.packString(s);
                break;
            default:
                m_packer.packNil();
                break;
        }
    }

    public NetworkTablesMessage decodeNT4Message(ByteBuffer message) throws IOException {
        byte[] bytes;
        if (message.hasArray()) {
            int offset = message.arrayOffset() + message.position();
            int length = message.remaining();
            bytes = java.util.Arrays.copyOfRange(message.array(), offset, offset + length);
        } else {
            bytes = new byte[message.remaining()];
            ByteBuffer copy = message.duplicate();
            copy.get(bytes);
        }
        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(bytes);
        unpacker.unpackArrayHeader();
        long id = unpacker.unpackLong();
        long timestamp = unpacker.unpackLong();
        int dataType = unpacker.unpackInt();
        Object value = null;
        NetworkTablesValueType dataTypeAsEnum = NetworkTablesValueType.getFromId(dataType);
        switch (dataTypeAsEnum) {
            case Boolean: value = unpacker.unpackBoolean(); break;
            case Double: value = unpacker.unpackDouble(); break;
            case Int: value = unpacker.unpackLong(); break;
            case Float: value = unpacker.unpackFloat(); break;
            case String: value = unpacker.unpackString(); break;
            case BooleanArray:
                int bLen = unpacker.unpackArrayHeader();
                boolean[] bArr = new boolean[bLen];
                for(int i=0; i<bLen; i++) bArr[i] = unpacker.unpackBoolean();
                value = bArr; break;
            case DoubleArray:
                int dLen = unpacker.unpackArrayHeader();
                double[] dArr = new double[dLen];
                for(int i=0; i<dLen; i++) dArr[i] = unpacker.unpackDouble();
                value = dArr; break;
            case IntArray:
                int iLen = unpacker.unpackArrayHeader();
                int[] iArr = new int[iLen];
                for(int i=0; i<iLen; i++) iArr[i] = (int)unpacker.unpackLong();
                value = iArr; break;
            case FloatArray:
                int fLen = unpacker.unpackArrayHeader();
                float[] fArr = new float[fLen];
                for(int i=0; i<fLen; i++) fArr[i] = unpacker.unpackFloat();
                value = fArr; break;
            case StringArray:
                int sLen = unpacker.unpackArrayHeader();
                String[] sArr = new String[sLen];
                for(int i=0; i<sLen; i++) sArr[i] = unpacker.unpackString();
                value = sArr; break;
            default: break;
        }
        unpacker.close();
        return new NetworkTablesMessage(id, timestamp, dataType, value);
    }

    private void processMessage(WebSocket conn, JsonNode data) {
        String method = data.get("method").asText();
        try {
            switch (method) {
                case "publish":
                    handlePublish(data);
                    break;
                case "unpublish":
                    handleUnpublish(data);
                    break;
                case "subscribe":
                    handleSubscribe(conn, data);
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleUnpublish(JsonNode data) {
        String topic = data.get("params").get("name").asText().substring(1);
        if (m_entries.containsKey(topic)) {
            NetworkTablesEntry entry = m_entries.get(topic);
            m_publisherUIDSMap.remove((long) data.get("params").get("pubuid").asInt());
            entry.callListenersOfEventType(NetworkTablesEvent.kTopicUnAnnounced, entry, entry.getValue());
        }
    }

    private void handleSubscribe(WebSocket conn, JsonNode data) throws IOException {
        JsonNode topicsNode = data.get("params").get("topics");
        Set<String> prefixes = new HashSet<>();
        for (JsonNode tNode : topicsNode) {
            String prefix = tNode.asText();
            if(prefix.startsWith("/")) prefix = prefix.substring(1);
            m_clientSubscriptions.computeIfAbsent(prefix, k -> new CopyOnWriteArraySet<>()).add(conn);
            prefixes.add(prefix);
        }
        
        // Send initial values for all matching topics without duplicates
        for (Map.Entry<String, NetworkTablesEntry> entry : m_entries.entrySet()) {
            boolean matches = false;
            for (String prefix : prefixes) {
                if (entry.getKey().startsWith(prefix)) {
                    matches = true;
                    break;
                }
            }
            if (matches) {
                sendBinaryUpdate(conn, entry.getValue());
            }
        }
    }

    private void handlePublish(JsonNode data) {
        JsonNode params = data.get("params");
        String topic = params.get("name").asText();
        if(topic.startsWith("/")) topic = topic.substring(1);
        int pubUID = params.get("pubuid").asInt();
        String type = params.has("type") ? params.get("type").asText() : "string";
        
        NetworkTablesEntry entry;
        boolean isNew = false;
        if (m_entries.containsKey(topic)) {
            entry = m_entries.get(topic);
        } else {
            isNew = true;
            Object defaultValue = "";
            if (type.equals("boolean")) defaultValue = false;
            else if (type.equals("double") || type.equals("float") || type.equals("int")) defaultValue = 0.0;
            else if (type.equals("boolean[]")) defaultValue = new boolean[0];
            else if (type.equals("double[]") || type.equals("float[]") || type.equals("int[]")) defaultValue = new double[0];
            else if (type.equals("string[]")) defaultValue = new String[0];
            
            int id = m_entries.size() + 1;
            entry = new NetworkTablesEntry(topic, new NetworkTablesValue(defaultValue, type));
            entry.setId(id);
            m_entries.put(topic, entry);
        }
        m_publisherUIDSMap.put((long) pubUID, entry);
        if (isNew) {
            announceEntry(entry);
        }
        entry.callListenersOfEventType(NetworkTablesEvent.kTopicPublished, entry, entry.getValue());
    }

    private void heartbeat(WebSocket conn, long clientTime) throws IOException {
        ObjectNode message = m_objectMapper.createObjectNode();
        message.put("method", "announce");
        ObjectNode params = m_objectMapper.createObjectNode();
        params.put("name", "/stamp");
        String typeString = NetworkTablesValueType.determineType((int) clientTime).typeString;
        int id = -1;
        params.put("id", id);
        params.put("value", clientTime);
        params.put("type", typeString);
        params.put("pubuid", id);
        ObjectNode properties = m_objectMapper.createObjectNode();
        params.set("properties", properties);
        message.set("params", params);
        ArrayNode messagesArray = m_objectMapper.createArrayNode();
        messagesArray.add(message);
        conn.send(encodeNT4Message(System.currentTimeMillis() * 1000L, id, 0, 2, clientTime));
    }

    public NetworkTablesEntry putTopic(String topic, Object value) {
        return putTopic(topic, new NetworkTablesValue(value, NetworkTablesValueType.determineType(value).typeString));
    }

    public NetworkTablesEntry putTopic(String topic, NetworkTablesValue value) {
        boolean isNew = false;
        NetworkTablesEntry entry;
        int id;
        
        if (m_entries.containsKey(topic)) {
            entry = m_entries.get(topic);
            id = entry.getId();
            entry.update(value);
            m_dirtyEntries.add(entry);
        } else {
            isNew = true;
            id = m_entries.size() + 1;
            entry = new NetworkTablesEntry(topic, value);
            entry.setId(id);
            m_entries.put(topic, entry);
            m_publisherUIDSMap.put((long) id, entry);
            m_dirtyEntries.add(entry);
        }

        if (isNew) {
            announceEntry(entry);
        }

        return entry;
    }
    
    private void announceEntry(NetworkTablesEntry entry) {
        ObjectNode message = m_objectMapper.createObjectNode();
        message.put("method", "announce");
        ObjectNode params = m_objectMapper.createObjectNode();
        params.put("name", "/" + entry.getTopic());
        params.put("id", entry.getId());
        params.put("type", entry.getValue().getType());
        params.put("pubuid", entry.getId());
        ObjectNode properties = m_objectMapper.createObjectNode();
        params.set("properties", properties);
        message.set("params", params);
        ArrayNode messagesArray = m_objectMapper.createArrayNode();
        messagesArray.add(message);
        broadcast(messagesArray.toString());
    }
    
    private void sendBinaryUpdate(WebSocket conn, NetworkTablesEntry entry) {
        try {
            int typeId = NetworkTablesValueType.getFromString(entry.getValue().getType()).id;
            
            // For single initial values, wrap it in a size-1 array of arrays to be strictly spec-compliant,
            // or use encodeNT4Messages to do it correctly.
            java.util.List<NetworkTablesEntry> single = new ArrayList<>();
            single.add(entry);
            byte[] binMsg = encodeNT4Messages(System.currentTimeMillis() * 1000L, single);
            
            conn.send(binMsg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void flush() {
        if (m_dirtyEntries.isEmpty() || m_clientSubscriptions.isEmpty()) return;

        long timestamp = System.currentTimeMillis() * 1000L;

        for (WebSocket conn : m_connections) {
            java.util.List<NetworkTablesEntry> entriesToSend = new ArrayList<>();
            for (NetworkTablesEntry entry : m_dirtyEntries) {
                boolean subscribed = false;
                for (Map.Entry<String, Set<WebSocket>> sub : m_clientSubscriptions.entrySet()) {
                    if (sub.getValue().contains(conn) && entry.getTopic().startsWith(sub.getKey())) {
                        subscribed = true;
                        break;
                    }
                }
                if (subscribed) {
                    entriesToSend.add(entry);
                }
            }

            if (!entriesToSend.isEmpty()) {
                try {
                    byte[] binMsg = encodeNT4Messages(timestamp, entriesToSend);
                    conn.send(binMsg);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        m_dirtyEntries.clear();
    }

    public Map<String, NetworkTablesEntry> getEntries() {
        return m_entries;
    }

    public static double getDouble(String topic, double defaultValue) {
        if (m_server == null) return defaultValue;
        NetworkTablesEntry entry = m_entries.get(topic);
        if (entry == null) {
            entry = m_entries.get("/" + topic);
        }
        if (entry == null && topic.startsWith("/")) {
            entry = m_entries.get(topic.substring(1));
        }
        if (entry != null && entry.getValue() != null && entry.getValue().get() != null) {
            Object v = entry.getValue().get();
            if (v instanceof Number) {
                return ((Number) v).doubleValue();
            }
        }
        return defaultValue;
    }

    public static double[] getDoubleArray(String topic, double[] defaultValue) {
        if (m_server == null) return defaultValue;
        NetworkTablesEntry entry = m_entries.get(topic);
        if (entry == null) {
            entry = m_entries.get("/" + topic);
        }
        if (entry == null && topic.startsWith("/")) {
            entry = m_entries.get(topic.substring(1));
        }
        if (entry != null && entry.getValue() != null && entry.getValue().get() != null) {
            Object v = entry.getValue().get();
            if (v instanceof double[]) {
                return (double[]) v;
            }
        }
        return defaultValue;
    }

    public static String getString(String topic, String defaultValue) {
        if (m_server == null) return defaultValue;
        NetworkTablesEntry entry = m_entries.get(topic);
        if (entry == null) {
            entry = m_entries.get("/" + topic);
        }
        if (entry == null && topic.startsWith("/")) {
            entry = m_entries.get(topic.substring(1));
        }
        if (entry != null && entry.getValue() != null && entry.getValue().get() != null) {
            Object v = entry.getValue().get();
            if (v instanceof String) {
                return (String) v;
            }
        }
        return defaultValue;
    }

    public static void publishTopic(String topic, Object value) {
        if (m_server != null) {
            String cleanTopic = topic.startsWith("/") ? topic.substring(1) : topic;
            m_server.putTopic(cleanTopic, value);
            m_server.flush();
        }
    }
}
