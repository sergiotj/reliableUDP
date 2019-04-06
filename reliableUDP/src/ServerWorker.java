import com.esotericsoftware.kryo.Kryo;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class ServerWorker implements Runnable {

    private DatagramSocket socket;
    private InetAddress address;
    private int port;

    private int sizeOfPacket;
    private int window;

    private Kryo kryo;

    public ServerWorker(InetAddress address, int port, int sizeOfPacket, int window, Kryo kryo) {

        this.address = address;
        this.port = port;
        this.sizeOfPacket = sizeOfPacket;
        this.window = window;
        this.kryo = kryo;
    }

    // SERVER
    @Override
    public void run() {

        try {

            this.socket = new DatagramSocket(4446);

            System.out.println("Server worker started at " + socket.getLocalPort());

            AgentUDP agent = new AgentUDP(socket, address, port, sizeOfPacket, window, kryo);

            Ack a = agent.receiveHandshake(socket, address, port, kryo, TypeAck.CONNECT);

            if (a == null) {

                System.out.println("Problema na conexão com o cliente. Conexão abortada...");
                return;
            }

            // 25 cenas para o gajo decidir-se...
            socket.setSoTimeout(0);
            Packet p = (Packet) agent.receiveReliableInfo(socket, address, port, kryo, TypePk.FNOP);

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

        } catch (IOException | NoSuchAlgorithmException | NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException | InvalidKeyException | InterruptedException exc) {

            exc.printStackTrace();

        }

    }
}