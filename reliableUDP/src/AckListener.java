import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import java.io.IOException;
import java.net.*;
import java.sql.Timestamp;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AckListener implements Runnable {

    private DatagramSocket socket;
    private Kryo kryo;
    private CopyOnWriteArrayList<Packet> chunks;
    private CopyOnWriteArraySet<Integer> success;
    private ResizeableSemaphore windowSemaph;
    private AtomicBoolean stop;
    private AtomicLong recentRtt;
    private AtomicLong lastRtt;
    private AtomicInteger recIndex;
    private InetAddress address;
    private int port;

    private int threshold;

    public AckListener(DatagramSocket socket, CopyOnWriteArraySet<Integer> success, CopyOnWriteArrayList<Packet> chunks,
                       ResizeableSemaphore windowSemaph, AtomicBoolean stop, AtomicLong recentRtt, AtomicLong lastRtt, AtomicInteger recIndex, InetAddress address, int port, int threshold){

        this.socket = socket;
        this.chunks = chunks;
        this.windowSemaph = windowSemaph;
        this.kryo = new Kryo();
        this.success = success;
        this.stop = stop;
        this.recentRtt = recentRtt;
        this.lastRtt = lastRtt;
        this.recIndex = recIndex;
        this.address = address;
        this.port = port;
        this.threshold = threshold;
    }

    // SERVER
    @Override
    public void run() {

        int congestWindow = 1;
        int consecutiveSuccess = 0;

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

                if (a.getStatus() == 1 && a.getType() == TypeAck.DATAFLOW) {

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

                    System.out.println("Ack received: " + ackReceived + " -> WINDOW = " + a.getWindow() + " -> RTT " + rtt + "ms");

                    success.add(ackReceived);

                    this.recIndex.set(a.getRecIndex());

                    // janela de congestão fica linear depois de pedido de reenvio
                    if (!reSent) {

                        if (congestWindow > threshold) congestWindow = consecutiveSuccess + 1;
                        else congestWindow = (int) Math.pow(2, consecutiveSuccess);

                    } else congestWindow = consecutiveSuccess + 1;

                    int flowWindow = a.getWindow();
                    if (flowWindow < 0) flowWindow = 0;

                    int finalWindow = Math.min(congestWindow, flowWindow);

                    windowSemaph.changePermits(finalWindow);

                    consecutiveSuccess++;

                    System.out.println("WINDOW changed to: " + finalWindow + " because congWindow = " + congestWindow + " and recWindow = " + flowWindow);

                }

                if (a.getStatus() == -1 && a.getType() == TypeAck.DATAFLOW) {

                    reSent = true;
                    consecutiveSuccess = 0;

                    int ackReceived = a.getSeqNumber();
                    this.recIndex.set(a.getRecIndex());

                    // evitar repetidos

                    System.out.println("Pedido de reenvio = " + ackReceived);

                    congestWindow = congestWindow / 2;
                    int flowWindow = a.getWindow();
                    if (flowWindow < 0) flowWindow = 0;

                    int finalWindow = Math.min(congestWindow, flowWindow);

                    windowSemaph.changePermits(finalWindow);

                    // reenvio... logo ele precisa que lhe mande um pacote
                    if (windowSemaph.availablePermits() == 0) {
                        windowSemaph.changePermits(1);
                    }

                    for (Packet p : chunks) {

                        if (p.getSeqNumber() == ackReceived) {

                            System.out.println("WINDOW MESMO: " + windowSemaph.availablePermits());

                            p.addTimestamp(new Timestamp(System.currentTimeMillis()));

                            byte[] message = Packet.packetToBytes(this.kryo, p, TypePk.DATA);
                            DatagramPacket sendPacket = new DatagramPacket(message, message.length, this.address, this.port);

                            socket.send(sendPacket);
                            windowSemaph.acquire();

                            System.out.println("REENVIO Sent: Sequence number = " + p.getSeqNumber());
                        }

                    }

                }

                // we did not receive an ack
            } catch (SocketTimeoutException e) {

                System.out.println("Socket timed out waiting for ACKs");
                stop.getAndSet(true);
                return;

            } catch (KryoException | IllegalArgumentException ioex) {

                System.out.println("Warning...");

            } catch (IOException | InterruptedException ioex) {

                System.out.println("exceção");
                stop.getAndSet(true);
                return;
            }

        }

    }
}
