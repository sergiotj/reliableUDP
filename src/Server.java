import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Server {

    private DatagramSocket socket;
    private byte[] buf;

    public Server() {

        this.buf = new byte[256];
    }

    public void startServer() throws IOException {

        DatagramSocket socket = new DatagramSocket(4445);

        while (true) {

            System.out.println("Server started at port 4445. Waiting for connection...");

            // This method blocks until a message arrives and it stores the message inside the byte array of the DatagramPacket passed to it.
            DatagramPacket receivePacket = new DatagramPacket(buf, buf.length);
            socket.receive(receivePacket);

            System.out.println("Connection received... Starting AgenteUDP to handle connection with client.");

            AgentUDP agent = new AgentUDP();

            // parsing to String
            String sentence = new String(receivePacket.getData());
            System.out.println("RECEIVED: " + sentence);

            // we retrieve the address and port of the client, since we are going to send the response back.
            InetAddress address = receivePacket.getAddress();
            int portClient = receivePacket.getPort();

            // send response
            DatagramPacket sendPacket = new DatagramPacket(buf, buf.length, address, portClient);
            socket.send(sendPacket);

        }
    }

}