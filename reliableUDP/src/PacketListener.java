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
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class PacketListener implements Runnable {

    private DatagramSocket socket;
    private InetAddress address;
    private int port;
    private Kryo kryo;

    private AtomicInteger iWritten;

    private volatile boolean stop;

    private Map<Integer, Packet> bufferToWait;
    private Map<Integer, Packet> bufferToWrite;

    private int maxSize;

    private ReentrantLock rl;
    private Condition rCond;

    public PacketListener(DatagramSocket socket, InetAddress address, int port, Map<Integer, Packet> bufferToWait, Map<Integer, Packet> bufferToWrite, AtomicInteger iWritten, Integer maxSize, ReentrantLock rl, Condition rCond) {

        this.socket = socket;
        this.address = address;
        this.port = port;
        this.kryo = new Kryo();
        this.bufferToWait = bufferToWait;
        this.bufferToWrite = bufferToWrite;
        this.iWritten = iWritten;
        this.maxSize = maxSize;
        this.rl = rl;
        this.rCond = rCond;
    }

    @Override
    public void run() {

        // ciclo pacotes
        while(!stop) {

            try {

                socket.setSoTimeout(20000);

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

                        int size = maxSize - bufferToWait.size();

                        Ack ack = new Ack(TypeAck.DATAFLOW, p.getSeqNumber(), -1, size, iWritten.get(), p.getTimestamp());
                        byte[] ackpack = Ack.ackToBytes(this.kryo, ack, ack.getType());
                        DatagramPacket sendPacket = new DatagramPacket(ackpack, ackpack.length, this.address, this.port);
                        socket.send(sendPacket);

                        // sai do ciclo para começar de novo
                        continue;

                    } else {

                        int size = maxSize - bufferToWait.size();

                        // Send acknowledgement
                        Ack ack;

                        System.out.println("Tamanho do Buff: " + size + " | WANT TO WRITE " + iWritten.get());

                        if (size > 0 || p.getSeqNumber() == iWritten.get()) {

                            ack = new Ack(TypeAck.DATAFLOW, p.getSeqNumber(), 1, size, iWritten.get(), p.getTimestamp());

                            if (iWritten.get() < p.getSeqNumber()) {
                                bufferToWait.put(p.getSeqNumber(), p);
                                System.out.println(p.getSeqNumber() + " -> buffer WAIT");
                            }

                            byte[] ackpack = Ack.ackToBytes(this.kryo, ack, ack.getType());
                            DatagramPacket sendPacket = new DatagramPacket(ackpack, ackpack.length, this.address, this.port);
                            socket.send(sendPacket);

                            System.out.println("Enviando o ACK = " + p.getSeqNumber() + " | window = " + ack.getWindow());

                            rl.lock();
                            if (iWritten.get() == p.getSeqNumber()) {
                                bufferToWrite.put(p.getSeqNumber(), p);
                                System.out.println(p.getSeqNumber() + " -> buffer WRITE");
                                rCond.signal();
                            }
                            rl.unlock();
                        } else System.out.println("Pacote " + p.getSeqNumber() + " descartado por falta de espaço!");

                    }
                } else {

                    System.out.println("Pacote " + p.getSeqNumber() + " descartado!");

                }

            } catch (SocketException exc) {

                System.out.println("SOCKET TIMED-OUT");
                return;

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
