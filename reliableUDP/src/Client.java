import com.esotericsoftware.kryo.Kryo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.security.NoSuchAlgorithmException;

public class Client {

    private int window;
    private int sizeOfPacket;
    private int svPort;

    public Client(int window, int sizeOfPacket) {

        this.window = window;
        this.sizeOfPacket = sizeOfPacket;
    }

    public void startClient(String args[]) throws IOException, ClassNotFoundException, NoSuchAlgorithmException, InterruptedException {

        if (!args[0].equals("connect") && args.length != 2) {

            System.out.println("Operação inválida");
            return;
        }

        DatagramSocket clientSocket = new DatagramSocket();
        Kryo kryo = new Kryo();

        InetAddress IPAddress = InetAddress.getByName(args[1]);
        int port = Integer.parseInt(args[2]);

        Ack a = AgentUDP.sendHandshake(this, clientSocket, IPAddress, port, kryo, TypeAck.CONNECT);

        if (a == null) {

            System.out.println("Problema na conexão com o servidor. Conexão abortada...");
            return;
        }

        int success = a.getStatus();

        if (success == -1) {

            System.out.println("Falha na conexão ao servidor");
            return;
        }

        System.out.println("Está conectado ao servidor.");

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

        AgentUDP agent = new AgentUDP(clientSocket, IPAddress, this.svPort, this.sizeOfPacket, this.window, kryo);

        System.out.println("quero o ficheiro: " + file);

        if (firstWord.equals("get")) {

            agent.receive(TypeEnt.CLIENT, file);

        }

        if (firstWord.equals("put")) {

            agent.send(TypeEnt.CLIENT, file);

        }

        clientSocket.close();
    }

    public void setSvPort(int port) {

        this.svPort = port;
    }

    public static void main(String args[]) {

        Client cli = new Client(10, 5000);

        try {

            cli.startClient(args);

        } catch (IOException | ClassNotFoundException | NoSuchAlgorithmException | InterruptedException exc) {

            exc.printStackTrace();
        }
    }
}
