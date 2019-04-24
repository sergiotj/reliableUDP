import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class PacketListener implements Runnable {

    private DatagramSocket socket;
    private InetAddress address;
    private int port;
    private Kryo kryo;

    private AtomicInteger iWritten;

    private volatile boolean stop;

    private Map<Integer, Packet> buffer;

    private int maxSize;

    public PacketListener(DatagramSocket socket, InetAddress address, int port, Map<Integer, Packet> buffer, AtomicInteger iWritten, Integer maxSize){

        this.socket = socket;
        this.address = address;
        this.port = port;
        this.kryo = new Kryo();
        this.buffer = buffer;
        this.iWritten = iWritten;
        this.maxSize = maxSize;
    }

    @Override
    public void run() {

        try {
            socket.setSoTimeout(10000);
        } catch (SocketException exc) {

            System.out.println("SOCKET TIMED-OUT");
            return;
        }

        // ciclo pacotes
        while(!stop) {

            try {

                // recebe pacote
                byte[] message = new byte[100000];
                DatagramPacket receivedPacket = new DatagramPacket(message, message.length);
                socket.receive(receivedPacket);
                message = receivedPacket.getData();

                Packet p = Packet.bytesToPacket(this.kryo, message, TypePk.DATA);

                // seqNumber from packet
                int seqNumber = p.getSeqNumber();

                // comparar com o que se quer agora
                if (seqNumber >= iWritten.get()) {

                    if (!Arrays.equals(p.getHash(), this.getHashChunk(p.getData()))) {

                        System.out.println("Chegou um pacote corrompido. Sending ACK to resend...");

                        // Send acknowledgement

                        int size = maxSize - buffer.size();

                        Ack ack = new Ack(TypeAck.DATAFLOW, p.getSeqNumber(), -1, size, p.getTimestamp());
                        byte[] ackpack = Ack.ackToBytes(this.kryo, ack, ack.getType());
                        DatagramPacket sendPacket = new DatagramPacket(ackpack, ackpack.length, this.address, this.port);
                        socket.send(sendPacket);

                        // sai do ciclo para começar de novo
                        continue;

                    }

                    else {

                        buffer.put(p.getSeqNumber(), p);

                        synchronized(buffer) {

                            buffer.notifyAll();
                        }

                        // Send acknowledgement

                        int size = maxSize - buffer.size();

                        Ack ack = null;

                        if (size < 0) {

                            ack = new Ack(TypeAck.DATAFLOW, p.getSeqNumber(), -1, size, p.getTimestamp());
                        } else {
                            ack = new Ack(TypeAck.DATAFLOW, p.getSeqNumber(), 1, size, p.getTimestamp());
                        }

                        byte[] ackpack = Ack.ackToBytes(this.kryo, ack, ack.getType());
                        DatagramPacket sendPacket = new DatagramPacket(ackpack, ackpack.length, this.address, this.port);
                        socket.send(sendPacket);

                        System.out.println("Mandei o ACK = " + p.getSeqNumber() + " com window = " + ack.getWindow());

                    }

                }

                else {

                    System.out.println("Pacote " + p.getSeqNumber() + " descartado!");

                }

            } catch (NoSuchAlgorithmException | IOException ex) {

                ex.printStackTrace();

            } catch (NegativeArraySizeException | KryoException nase) {

                System.out.println("Foi recebido um pacote que não é de dados. Foi descartado!");
                continue;
            }

        }

        // there are missing parts... so, while loop should continue
    }

    private byte[] getHashChunk(byte[] chunks) throws NoSuchAlgorithmException {

        byte[] hash = MessageDigest.getInstance("MD5").digest(chunks);

        return Arrays.copyOf(hash, 8);
    }

    public void stop(){
        stop = true;
    }
}
