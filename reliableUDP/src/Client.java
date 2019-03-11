import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.NoSuchAlgorithmException;

public class Client {

    private int window;
    private int sizeOfPacket;

    public Client(int window, int sizeOfPacket) {

        this.window = window;
        this.sizeOfPacket = sizeOfPacket;
    }

    public void startClient(String args[]) throws IOException, ClassNotFoundException, NoSuchAlgorithmException {

        if (!args[0].equals("connect") && args.length != 2) {

            System.out.println("Operação inválida");
            return;
        }

        DatagramSocket clientSocket = new DatagramSocket();

        InetAddress IPAddress = InetAddress.getByName(args[1]);
        int port = Integer.parseInt(args[2]);

        Ack ack = new Ack(TypeAck.CONNECT, 1);
        byte[] ackB = Ack.ackToBytes(ack);
        DatagramPacket sendPacket = new DatagramPacket(ackB, ackB.length, IPAddress, port);
        clientSocket.send(sendPacket);

        // 3 way handshake

        byte[] message = new byte[200];
        DatagramPacket receivedPacket = new DatagramPacket(message, message.length);
        clientSocket.receive(receivedPacket);
        message = receivedPacket.getData();

        Ack a = Ack.bytesToAck(message);

        int success = a.getStatus();

        if (success == -1) {

            System.out.println("Falha na conexão ao servidor");
            return;
        }

        System.out.println("Está conectado ao servidor.");

        // 3 way handshake
        Ack ack1 = new Ack(TypeAck.CONTROL, 1);
        byte[] ackB1 = Ack.ackToBytes(ack1);
        DatagramPacket sendPacket1 = new DatagramPacket(ackB1, ackB1.length, IPAddress, port);
        clientSocket.send(sendPacket1);

        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));

        boolean right = false;
        String firstWord = null;
        String sentence = null;

        System.out.println("Escreva: put file ou get file");

        while (!right) {

            sentence = inFromUser.readLine();

            firstWord = sentence.substring(0, sentence.indexOf(' '));

            if (!firstWord.equals("get") && !firstWord.equals("put")) {

                System.out.println("Operação inválida");

            } else right = true;

        }

        String file = sentence.split(" ")[1];
        System.out.println("Starting AgenteUDP to handle connection with server.");

        AgentUDP agent = new AgentUDP(clientSocket, IPAddress, port, this.sizeOfPacket, this.window);

        System.out.println("quero o ficheiro: " + file);

        if (firstWord.equals("get")) {

            agent.receiveInClient(file);

        }

        if (firstWord.equals("put")) {

            agent.sendInClient(file);

        }

        clientSocket.close();
    }

    public static void main(String args[]) {

        Client cli = new Client(5, 100);

        try {

            cli.startClient(args);

        } catch (IOException | ClassNotFoundException | NoSuchAlgorithmException exc) {

            exc.printStackTrace();
        }
    }
}
