import com.esotericsoftware.kryo.Kryo;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;

public class AckListener implements Runnable {

    private DatagramSocket socket;
    private Kryo kryo;

    private InetAddress address;
    private int port;

    private CopyOnWriteArrayList<Packet> chunks;

    private ArrayList<Packet> copy;

    private CopyOnWriteArrayList<Packet> priority;

    private Semaphore windowSemaph;
    private int flag;

    public AckListener(DatagramSocket socket, InetAddress address, int port, CopyOnWriteArrayList<Packet> chunks, CopyOnWriteArrayList<Packet> priority, Semaphore windowSemaph, int flag){

        this.socket = socket;
        this.address = address;
        this.port = port;
        this.chunks = chunks;
        this.priority = priority;
        this.windowSemaph = windowSemaph;
        this.kryo = new Kryo();
        this.flag = flag;
        this.copy = new ArrayList<>(chunks);
    }

    // SERVER
    @Override
    public void run() {

        while (true) {

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

                    // System.out.println("Ack received: Sequence Number = " + ackReceived);

                    for (Packet p : chunks) {

                        if (p.getSeqNumber() == ackReceived) {

                            chunks.remove(p);
                        }
                    }

                        windowSemaph.release();
                }

                if (a.getStatus() == -1 && a.getType() == TypeAck.DATAFLOW){

                    int ackReceived = a.getSeqNumber();

                    System.out.println("Pedido de reenvio = " + ackReceived);

                    priority.add(copy.get(ackReceived));

                }

            }

            // we did not receive an ack
            catch (SocketTimeoutException e) {

                System.out.println("Socket timed out waiting for ACKs");
                return;
            } catch (IOException ioex) {

                ioex.printStackTrace();
            }

            if (flag == 1) {

                return;
            }

        }

    }
}
