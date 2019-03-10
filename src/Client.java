import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Client {

    public static void main(String args[]) throws IOException {

        if (!args[0].equals("connect") && args.length != 2) {

            System.out.println("Operação inválida");
            return;
        }

        DatagramSocket clientSocket = new DatagramSocket();

        InetAddress IPAddress = InetAddress.getByName(args[1]);
        int port = Integer.parseInt(args[2]);

        Ack ack = new Ack(TypeAck.CONTROL, 1);
        byte[] ackB = Ack.ackToBytes(ack);
        DatagramPacket sendPacket = new DatagramPacket(ackB, ackB.length, IPAddress, port);
        clientSocket.send(sendPacket);

        // 3 way handshake
        int success = AgentUDP.receiveACK(clientSocket);

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
        String sentence = inFromUser.readLine();

        boolean right = false;
        String firstWord = null;

        while(!right) {

            System.out.println("Escreva: put file ou get file");

            sentence = inFromUser.readLine();

            firstWord = sentence.substring(0, sentence.indexOf(' '));

            if (!firstWord.equals("get") || !firstWord.equals("put")) {

                System.out.println("Operação inválida");
            }

            else right = true;

        }

        String file = sentence.substring(1, sentence.indexOf(' '));
        System.out.println("Starting AgenteUDP to handle connection with server.");

        AgentUDP agent = new AgentUDP(clientSocket, IPAddress, port, 100, 5);

        if (firstWord.equals("get")) {

            agent.receive(file);

        }

        if (firstWord.equals("put")) {

            agent.send(file);

        }

        clientSocket.close();
    }

}
