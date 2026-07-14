package org.frcforftc.networktables;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class NT4Client extends WebSocketClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Integer> topicIdMap = new HashMap<>();
    private int nextTopicId = 1;

    public NT4Client(URI serverUri) {
        super(serverUri);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            this.close(1000, "Closed connections");
        }));
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
//        System.out.println("Connected to server");
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode data = objectMapper.readTree(message);
//            System.out.println(message);
            processMessage(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessage(ByteBuffer message) {
        try {
            decodeNT4Message(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void processMessage(JsonNode node) {
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
//        System.out.println("Disconnected from server");
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    public void publish(String topic, Object value) {
        int topicId = topicIdMap.computeIfAbsent(topic, k -> nextTopicId++);
        try {
            ByteBuffer encodedMessage = encodeNT4Message(System.currentTimeMillis(), topicId, topicId, determineDataType(value), value);
            send(encodedMessage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void publishEntry(NetworkTablesEntry entry) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("method", "publish");
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", entry.getTopic());
        params.put("pubuid", entry.getId());
        params.put("type", entry.getValue().getType());
        ObjectNode properties = objectMapper.createObjectNode();
        // Add any properties here if needed
        params.set("properties", properties);
        message.set("params", params);
        ArrayNode messageArray = objectMapper.createArrayNode();
        messageArray.add(message);
        send(messageArray.toString());
    }

    private ByteBuffer encodeNT4Message(long timestamp, long topicId, long pubUID, int dataType, Object dataValue) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessagePacker packer = MessagePack.newDefaultPacker(out);

        packer.packArrayHeader(4);
        packer.packLong(topicId);
        packer.packLong(timestamp);
        packer.packInt(dataType);

        switch (dataType) {
            case 0: // boolean
                packer.packBoolean((Boolean) dataValue);
                break;
            case 1: // double
                packer.packDouble((Double) dataValue);
                break;
            case 2: // int
                packer.packLong((Integer) dataValue);
                break;
            case 3: // float
                packer.packFloat((Float) dataValue);
                break;
            case 4: // string
                packer.packString((String) dataValue);
                break;
            case 5: // binary
                byte[] binaryData = (byte[]) dataValue;
                packer.packBinaryHeader(binaryData.length);
                packer.writePayload(binaryData);
                break;
            case 16: // boolean array
                boolean[] boolArray = (boolean[]) dataValue;
                packer.packArrayHeader(boolArray.length);
                for (boolean b : boolArray) {
                    packer.packBoolean(b);
                }
                break;
            case 17: // double array
                double[] doubleArray = (double[]) dataValue;
                packer.packArrayHeader(doubleArray.length);
                for (double d : doubleArray) {
                    packer.packDouble(d);
                }
                break;
            case 18: // int array
                int[] intArray = (int[]) dataValue;
                packer.packArrayHeader(intArray.length);
                for (int i : intArray) {
                    packer.packInt(i);
                }
                break;
            case 19: // float array
                float[] floatArray = (float[]) dataValue;
                packer.packArrayHeader(floatArray.length);
                for (float f : floatArray) {
                    packer.packFloat(f);
                }
                break;
            case 20: // string array
                String[] stringArray = (String[]) dataValue;
                packer.packArrayHeader(stringArray.length);
                for (String s : stringArray) {
                    packer.packString(s);
                }
                break;
            default:
                throw new IOException("Unknown data type: " + dataType);
        }

        packer.close();
        return ByteBuffer.wrap(out.toByteArray());
    }

    private int determineDataType(Object value) {
        if (value instanceof Boolean) return 0;
        if (value instanceof Double) return 1;
        if (value instanceof Integer) return 2;
        if (value instanceof Float) return 3;
        if (value instanceof String) return 4;
        if (value instanceof byte[]) return 5;
        if (value instanceof boolean[]) return 16;
        if (value instanceof double[]) return 17;
        if (value instanceof int[]) return 18;
        if (value instanceof float[]) return 19;
        if (value instanceof String[]) return 20;
        throw new IllegalArgumentException("Unsupported data type: " + value.getClass().getName());
    }

    private void decodeNT4Message(ByteBuffer buffer) throws IOException {
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(buffer)) {
            while (unpacker.hasNext()) {
                int arraySize = unpacker.unpackArrayHeader();
                if (arraySize != 4) {
                    throw new IOException("Invalid array size for NT4 message: " + arraySize);
                }

                long topicID = unpacker.unpackLong();
                long timestamp = unpacker.unpackLong();
                int dataType = unpacker.unpackInt();

                Object dataValue = null;
                switch (dataType) {
                    case 0: // boolean
                        dataValue = unpacker.unpackBoolean();
                        break;
                    case 1: // double
                        dataValue = unpacker.unpackDouble();
                        break;
                    case 2: // int
                        dataValue = unpacker.unpackLong();
                        break;
                    case 3: // float
                        dataValue = unpacker.unpackFloat();
                        break;
                    case 4: // string
                        dataValue = unpacker.unpackString();
                        break;
                    case 5: // binary
                        int binarySize = unpacker.unpackBinaryHeader();
                        byte[] binaryData = unpacker.readPayload(binarySize);
                        dataValue = binaryData;
                        break;
                    case 16: // boolean array
                        int boolArraySize = unpacker.unpackArrayHeader();
                        boolean[] boolArray = new boolean[boolArraySize];
                        for (int i = 0; i < boolArraySize; i++) {
                            boolArray[i] = unpacker.unpackBoolean();
                        }
                        dataValue = boolArray;
                        break;
                    case 17: // double array
                        int doubleArraySize = unpacker.unpackArrayHeader();
                        double[] doubleArray = new double[doubleArraySize];
                        for (int i = 0; i < doubleArraySize; i++) {
                            doubleArray[i] = unpacker.unpackDouble();
                        }
                        dataValue = doubleArray;
                        break;
                    case 18: // int array
                        int intArraySize = unpacker.unpackArrayHeader();
                        int[] intArray = new int[intArraySize];
                        for (int i = 0; i < intArraySize; i++) {
                            intArray[i] = unpacker.unpackInt();
                        }
                        dataValue = intArray;
                        break;
                    case 19: // float array
                        int floatArraySize = unpacker.unpackArrayHeader();
                        float[] floatArray = new float[floatArraySize];
                        for (int i = 0; i < floatArraySize; i++) {
                            floatArray[i] = unpacker.unpackFloat();
                        }
                        dataValue = floatArray;
                        break;
                    case 20: // string array
                        int stringArraySize = unpacker.unpackArrayHeader();
                        String[] stringArray = new String[stringArraySize];
                        for (int i = 0; i < stringArraySize; i++) {
                            stringArray[i] = unpacker.unpackString();
                        }
                        dataValue = stringArray;
                        break;
                    default:
                        throw new IOException("Unknown data type: " + dataType);
                }

                processMessage(topicID, timestamp, dataType, dataValue);
            }
        }
    }

    private void processMessage(long topicId, long timestamp, int dataType, Object dataValue) {
        // Implement message processing logic here
//        System.out.println("Received data - Topic ID: " + topicId + ", Timestamp: " + timestamp + ", Data Type: " + dataType + ", Data Value: " + dataValue);
    }
}
