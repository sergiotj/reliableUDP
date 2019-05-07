import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class AckListener implements Runnable {

    private DatagramSocket socket;
    private Kryo kryo;

    private CopyOnWriteArrayList<Packet> chunks;

    private CopyOnWriteArraySet<Integer> success;

    private CopyOnWriteArrayList<Packet> priority;

    private ResizeableSemaphore windowSemaph;

    private AtomicBoolean stop;
    private AtomicLong recentRtt;
    private AtomicLong lastRtt;

    private int consecutiveSuccess;

    public AckListener(DatagramSocket socket, CopyOnWriteArraySet<Integer> success, CopyOnWriteArrayList<Packet> chunks, CopyOnWriteArrayList<Packet> priority, ResizeableSemaphore windowSemaph, AtomicBoolean stop, AtomicLong recentRtt, AtomicLong lastRtt){

        this.socket = socket;
        this.chunks = chunks;
        this.priority = priority;
        this.windowSemaph = windowSemaph;
        this.kryo = new Kryo();
        this.success = success;
        this.stop = stop;
        this.recentRtt = recentRtt;
        this.lastRtt = lastRtt;
        this.consecutiveSuccess = 0;
    }

    // SERVER
    @Override
    public void run() {

        // flag que nos diz se já houve pedidos de reenvio ou não
        boolean reSent = false;

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

                Ack a;

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

                    Long rtt = 0L;

                    if (a.getTimestamp().getTime() > 0) {

                        rtt = AgentUDP.calculateRTT(System.currentTimeMillis(), a.getTimestamp());

                        Long estRtt = lastRtt.get() * (long) 0.875 + (long) 0.125 * rtt;
                        Long devRtt = (long) 0.75 * recentRtt.get() + (long) 0.25 * Math.abs(rtt - estRtt);
                        Long timeout = estRtt + 4 * devRtt;

                        recentRtt = lastRtt;
                        lastRtt.set(timeout);
                    }

                    System.out.println("Ack received: " + ackReceived + " -> WINDOW = " + a.getWindow() + " -> RTT " + rtt);

                    success.add(ackReceived);

                    int congestWindow;

                    // janela de congestão fica linear depois de pedido de reenvio
                    if (!reSent) {
                        congestWindow = (int) Math.pow(2, consecutiveSuccess);
                    }
                    else congestWindow = consecutiveSuccess + 1;

                    int flowWindow = a.getWindow();
                    if (flowWindow < 0) flowWindow = 0;
                    int finalWindow = Math.min(congestWindow, flowWindow);

                    windowSemaph.changePermits(finalWindow);

                    consecutiveSuccess++;

                    System.out.println("WINDOW: " + finalWindow);

                }

                if (a.getStatus() == -1 && a.getType() == TypeAck.DATAFLOW){

                    reSent = true;

                    int ackReceived = a.getSeqNumber();

                    // evitar repetidos
                    if (!priority.contains(chunks.get(ackReceived))) {

                        System.out.println("Pedido de reenvio = " + ackReceived);

                        int congestWindow = 1;
                        int flowWindow = a.getWindow();
                        if (flowWindow < 0) flowWindow = 0;

                        int finalWindow = Math.min(congestWindow, flowWindow);

                        windowSemaph.changePermits(finalWindow);

                        /*
                        // reenvio... logo ele precisa que lhe mande um pacote
                        if (windowSemaph.availablePermits() == 0) {
                            windowSemaph.changePermits(1);
                        }
                        */

                        priority.add(chunks.get(ackReceived));
                    }

                }

            }

            // we did not receive an ack
            catch (SocketTimeoutException e) {

                System.out.println("Socket timed out waiting for ACKs");
                return;

            } catch (KryoException | IllegalArgumentException ioex) {

                System.out.println("Warning...");

            } catch (IOException ioex) {

                System.out.println("exceção");
                stop.getAndSet(true);
                return;
            }

        }

    }
}
