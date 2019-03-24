import com.esotericsoftware.kryo.Kryo;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.NoSuchAlgorithmException;

public class ServerWorker implements Runnable {

    private DatagramSocket socket;
    private InetAddress address;
    private int port;

    private int sizeOfPacket;
    private int window;

    private Kryo kryo;

    public ServerWorker(DatagramSocket socket, InetAddress address, int port, int sizeOfPacket, int window, Kryo kryo) {

        this.socket = socket;
        this.address = address;
        this.port = port;
        this.sizeOfPacket = sizeOfPacket;
        this.window = window;
        this.kryo = kryo;
    }

    // SERVER
    @Override
    public void run() {


        AgentUDP agent = new AgentUDP(socket, address, port, sizeOfPacket, window, kryo);

        try {

            Ack a = agent.receiveHandshake(socket, address, port, kryo, TypeAck.CONNECT);

            if (a == null) {

                System.out.println("Problema na conexão com o cliente. Conexão abortada...");
                return;
            }

            // 25 cenas para o gajo decidir-se...
            socket.setSoTimeout(0);
            Packet p = agent.receivePacketInfo(socket, address, port, kryo, TypePk.FNOP);

            String filename = p.getFilename();

            String operation = p.getOperation();

            System.out.println("Nome do ficheiro: " + filename);
            System.out.println("Operação recebida: " + operation);

            // se é um put file
            if (operation.equals("put")) {

                agent.receive(TypeEnt.SERVER, filename);
            }

            // se é um get file
            if (operation.equals("get")) {

                agent.send(TypeEnt.SERVER, filename);
            }

        } catch (IOException | ClassNotFoundException | NoSuchAlgorithmException | InterruptedException exc) {

            exc.printStackTrace();

        }

    }
}