import com.esotericsoftware.kryo.Kryo;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;

public class Server {

    private int window;
    private int sizeOfPacket;

    private HashMap<String, String> users;

    public Server(int window, int sizeOfPacket) {

        this.window = window;
        this.sizeOfPacket = sizeOfPacket;
        this.users = new HashMap<>();
    }

    public void startServer(String[] args) throws IOException, NumberFormatException {

        int port;

        if (args.length == 0) port = 4445;
        else port = Integer.parseInt(args[0]);

        DatagramSocket socket = new DatagramSocket(port);
        Kryo kryo = new Kryo();

        System.out.println("Server started at port " + port);

        while (true) {

            System.out.println("Waiting for connection...");

            // This method blocks until a message arrives and it stores the message inside the byte array of the DatagramPacket passed to it.
            byte[] ack = new byte[200];
            DatagramPacket receivePacket = new DatagramPacket(ack, ack.length);
            socket.receive(receivePacket);

            Ack a = Ack.bytesToAck(kryo, receivePacket.getData(), TypeAck.CONNECT);

            if (a.getType() == TypeAck.CONNECT) {

                String username = a.getUsername();
                String password = a.getPassword();

                // Login
                if (!users.get(username).equals(password)) {
                    return;
                }

                if (a.getStatus() != 1) {
                    return;
                }

                // we retrieve the address and port of the client, since we are going to send the response back.
                InetAddress address = receivePacket.getAddress();
                int portClient = receivePacket.getPort();

                System.out.println("Connection received... Starting AgenteUDP to handle connection with client.");

                ServerWorker worker = new ServerWorker(address, portClient, this.sizeOfPacket, this.window, kryo);
                Thread t1 = new Thread(worker);
                t1.start();
            }
        }
    }

    private void readUsers() {

        BufferedReader reader;
        try {

            reader = new BufferedReader(new FileReader("users.txt"));
            String line = reader.readLine();

            while (line != null) {

                String[] splited = line.split("\\s+");

                users.put(splited[0], splited[1]);

                // read next line
                line = reader.readLine();
            }

            reader.close();

        } catch (IOException e) {

            e.printStackTrace();
        }

        System.out.println("Utilizadores Registados: " + users.keySet());
    }

    public static void main(String [] args) {

        Server sv = new Server(10, 5000);

        sv.readUsers();

        try {

            sv.startServer(args);

        } catch (IOException ioex) {

            ioex.printStackTrace();
        }
        catch (NumberFormatException e)
        {
            System.out.println("Número de porta incorreto");
        }
    }

}
