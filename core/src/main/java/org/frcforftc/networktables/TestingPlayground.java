package org.frcforftc.networktables;



import java.util.function.Consumer;
import java.util.function.Supplier;

class TestingPlayground {
    public static void main(String[] args) {
        NetworkTablesInstance inst = NetworkTablesInstance.getDefaultInstance();
        inst.startNT4Server("localhost", 5810);
        inst.putNumber("test1", 1);
        inst.putStringArray("test2", new String[]{"t", "b", "c"});
        inst.putString("test/value1", "Hello");
        inst.putString("test/value2", "Hello again");
    }
}
