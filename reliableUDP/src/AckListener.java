import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public class AckListener implements Runnable {

    private DatagramSocket socket;
    private Kryo kryo;

    private CopyOnWriteArrayList<Packet> chunks;

    private CopyOnWriteArraySet<Integer> success;

    private CopyOnWriteArrayList<Packet> priority;

    private Semaphore windowSemaph;

    private AtomicBoolean stop;

    public AckListener(DatagramSocket socket, CopyOnWriteArraySet<Integer> success, CopyOnWriteArrayList<Packet> chunks, CopyOnWriteArrayList<Packet> priority, Semaphore windowSemaph, AtomicBoolean stop){

        this.socket = socket;
        this.chunks = chunks;
        this.priority = priority;
        this.windowSemaph = windowSemaph;
        this.kryo = new Kryo();
        this.success = success;
        this.stop = stop;
    }

    // SERVER
    @Override
    public void run() {

        try {
            socket.setSoTimeout(15000);
        } catch (SocketException exc) {

            System.out.println("SOCKET TIMED-OUT");
            return;
        }

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
                socket.receive(ackpack);

                ack = ackpack.getData();

                a = Ack.bytesToAck(this.kryo, ack, TypeAck.DATAFLOW);

                if (a.getType() == TypeAck.CLOSE) {

                    System.out.println("FECHOU");
                    System.out.println("ACABOU A ESPERA POR ACKS");

                    stop.getAndSet(true);
                    return;
                }

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

                    windowSemaph.release();

                }

            }

            // we did not receive an ack
            catch (SocketTimeoutException e) {

                System.out.println("Socket timed out waiting for ACKs");
                return;

            } catch (KryoException ioex) {

                System.out.println("exceção");

            } catch (IOException ioex) {

                System.out.println("exceção");
            }

        }

    }
}
