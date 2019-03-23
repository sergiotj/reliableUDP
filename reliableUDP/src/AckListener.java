import com.esotericsoftware.kryo.Kryo;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Semaphore;

public class AckListener implements Runnable {

    private DatagramSocket socket;
    private Kryo kryo;

    private InetAddress address;
    private int port;

    private CopyOnWriteArrayList<Packet> chunks;

    private CopyOnWriteArraySet<Integer> success;

    private CopyOnWriteArrayList<Packet> priority;

    private Semaphore windowSemaph;
    private int parts;

    public AckListener(DatagramSocket socket, InetAddress address, int port, CopyOnWriteArraySet<Integer> success, CopyOnWriteArrayList<Packet> chunks, CopyOnWriteArrayList<Packet> priority, Semaphore windowSemaph, int parts){

        this.socket = socket;
        this.address = address;
        this.port = port;
        this.chunks = chunks;
        this.priority = priority;
        this.windowSemaph = windowSemaph;
        this.kryo = new Kryo();
        this.parts = parts;
        this.success = success;
    }

    // SERVER
    @Override
    public void run() {

        while (true) {

            if (success.size() == chunks.size()) {

                System.out.println("ACABOU A ESPERA POR ACKS");
                return;
            }

            // receiving acknowledgments
            // Create another packet by setting a byte array and creating data gram packet
            byte[] ack = new byte[50];
            DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

            try {

                Ack a = null;

                // set the socket timeout for the packet acknowledgment
                socket.setSoTimeout(10000);
                socket.receive(ackpack);

                ack = ackpack.getData();

                a = Ack.bytesToAck(this.kryo, ack, TypeAck.DATAFLOW);

                if (a.getStatus() == 1 && a.getType() == TypeAck.DATAFLOW){

                    int ackReceived = a.getSeqNumber();

                    System.out.println("Ack received: " + ackReceived);

                    success.add(ackReceived);

                    windowSemaph.release();

                }

                if (a.getStatus() == -1 && a.getType() == TypeAck.DATAFLOW){

                    int ackReceived = a.getSeqNumber();

                    System.out.println("Pedido de reenvio = " + ackReceived);

                    priority.add(chunks.get(ackReceived));

                }

            }

            // we did not receive an ack
            catch (SocketTimeoutException e) {

                System.out.println("Socket timed out waiting for ACKs");
                return;
            } catch (IOException ioex) {

                ioex.printStackTrace();
            }

        }

    }
}
