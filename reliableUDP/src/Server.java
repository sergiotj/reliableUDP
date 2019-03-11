import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Server {

    private int window;
    private int sizeOfPacket;

    public Server(int window, int sizeOfPacket) {

        this.window = window;
        this.sizeOfPacket = sizeOfPacket;
    }

    public void startServer() throws IOException, ClassNotFoundException {

        DatagramSocket socket = new DatagramSocket(4445);

        System.out.println("Server started at port 4445.");



            System.out.println("Waiting for connection...");

            // This method blocks until a message arrives and it stores the message inside the byte array of the DatagramPacket passed to it.
            byte[] ack = new byte[50];
            DatagramPacket receivePacket = new DatagramPacket(ack, ack.length);
            socket.receive(receivePacket);

            Ack a = Ack.bytesToAck(receivePacket.getData());

            if (a.getType() == TypeAck.CONNECT) {

                if (a.getStatus() != 1) {
                    return;
                }

                // we retrieve the address and port of the client, since we are going to send the response back.
                InetAddress address = receivePacket.getAddress();
                int portClient = receivePacket.getPort();

                System.out.println("Connection received... Starting AgenteUDP to handle connection with client.");

                AgentUDP agent = new AgentUDP(socket, address, portClient, this.sizeOfPacket, this.window);
                Thread t1 = new Thread(agent);
                t1.start();
            }

    }

    public static void main(String [] args) {

        Server sv = new Server(5, 100);

        try {

            sv.startServer();

        } catch (IOException | ClassNotFoundException ioex) {

            ioex.printStackTrace();
        }

    }

}
