package org.frcforftc.networktables;



import java.util.Objects;

/**
 * Enum representing the different value types that can be used in NetworkTables.
 */
public enum NetworkTablesValueType {
    /**
     * Boolean value type.
     */
    Boolean(0, "boolean"),

    /**
     * Double value type.
     */
    Double(1, "double"),

    /**
     * Integer value type.
     */
    Int(2, "int"),

    /**
     * Float value type.
     */
    Float(3, "float"),

    /**
     * String value type.
     */
    String(4, "string"),

    /**
     * Raw data value type.
     */
    Raw(5, "raw"),

    /**
     * Boolean array value type.
     */
    BooleanArray(16, "boolean[]"),

    /**
     * Double array value type.
     */
    DoubleArray(17, "double[]"),

    /**
     * Integer array value type.
     */
    IntArray(18, "int[]"),

    /**
     * Float array value type.
     */
    FloatArray(19, "float[]"),

    /**
     * String array value type.
     */
    StringArray(20, "string[]"),

    /**
     * Unknown value type.
     */
    Unknown(-1, "unknown");

    /**
     * The ID associated with the value type.
     */
    public final int id;

    /**
     * The string representation of the value type.
     */
    public final String typeString;

    /**
     * Constructs a NetworkTablesValueType with the specified ID and type string.
     *
     * @param id the ID of the value type
     * @param s  the string representation of the value type
     */
    NetworkTablesValueType(int id, String s) {
        this.id = id;
        this.typeString = s;
    }

    /**
     * Gets the {@link NetworkTablesValueType} from the given string.
     *
     * @param s the string representation of the value type
     * @return the corresponding {@link NetworkTablesValueType}, or {@link NetworkTablesValueType#Unknown} if not found
     */
    public static NetworkTablesValueType getFromString(String s) {
        for (NetworkTablesValueType val : NetworkTablesValueType.values()) {
            if (Objects.equals(val.typeString, s)) {
                return val;
            }
        }

        return NetworkTablesValueType.Unknown;
    }

    /**
     * Gets the {@link NetworkTablesValueType} from the given ID.
     *
     * @param id the ID of the value type
     * @return the corresponding {@link NetworkTablesValueType}, or {@link NetworkTablesValueType#Unknown} if not found
     */
    public static NetworkTablesValueType getFromId(int id) {
        for (NetworkTablesValueType val : NetworkTablesValueType.values()) {
            if (val.id == id) {
                return val;
            }
        }
        return NetworkTablesValueType.Unknown;
    }

    /**
     * Determines the {@link NetworkTablesValueType} from the given object.
     *
     * @param value the object to determine the type from
     * @return the corresponding {@link NetworkTablesValueType}, or {@link NetworkTablesValueType#Unknown} if not recognized
     */
    public static NetworkTablesValueType determineType(Object value) {
        if (value instanceof Integer) {
            return NetworkTablesValueType.Int;
        } else if (value instanceof Double) {
            return NetworkTablesValueType.Double;
        } else if (value instanceof Float) {
            return NetworkTablesValueType.Float;
        } else if (value instanceof String) {
            return NetworkTablesValueType.String;
        } else if (value instanceof Boolean) {
            return NetworkTablesValueType.Boolean;
        } else if (value instanceof byte[]) { // Raw data is usually represented as a byte array
            return NetworkTablesValueType.Raw;
        } else if (value instanceof boolean[]) {
            return NetworkTablesValueType.BooleanArray;
        } else if (value instanceof double[]) {
            return NetworkTablesValueType.DoubleArray;
        } else if (value instanceof int[]) {
            return NetworkTablesValueType.IntArray;
        } else if (value instanceof float[]) {
            return NetworkTablesValueType.FloatArray;
        } else if (value instanceof Integer[]) {
            return NetworkTablesValueType.IntArray;
        } else if (value instanceof Double[]) {
            return NetworkTablesValueType.DoubleArray;
        } else if (value instanceof Float[]) {
            return NetworkTablesValueType.FloatArray;
        } else if (value instanceof Boolean[]) {
            return NetworkTablesValueType.BooleanArray;
        } else if (value instanceof String[]) {
            return NetworkTablesValueType.StringArray;
        } else {
            return NetworkTablesValueType.Unknown;
        }
    }

}
