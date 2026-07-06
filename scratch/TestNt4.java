import edu.wpi.first.networktables.NetworkTableInstance;

public class TestNt4 {
    public static void main(String[] args) throws Exception {
        NetworkTableInstance nt = NetworkTableInstance.getDefault();
        nt.startClient4("TestClient");
        nt.setServer("127.0.0.1", 5810);
        
        System.out.println("Started client");
        
        Thread.sleep(2000);
        
        var topics = nt.getTopics();
        for(var t : topics) {
            System.out.println("Topic: " + t.getName() + " Type: " + t.getTypeString());
        }
        nt.close();
    }
}
