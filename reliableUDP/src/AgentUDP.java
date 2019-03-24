import com.esotericsoftware.kryo.Kryo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AgentUDP {

    private DatagramSocket socket;
    private InetAddress address;
    private int port;

    private int sizeOfPacket;
    private int window;

    private Kryo kryo;

    public AgentUDP(DatagramSocket socket, InetAddress address, int port, int sizeOfPacket, int window, Kryo kryo) {

        this.socket = socket;
        this.address = address;
        this.port = port;
        this.sizeOfPacket = sizeOfPacket;
        this.window = window;
        this.kryo = kryo;
    }

    public void receive(TypeEnt ent, String filename) throws IOException, InterruptedException, NoSuchAlgorithmException {

        // se for server
        // recebe pacote com o nome do ficheiro // FEITO NO RUN LOGO AQUI NÃO SE FAZ

        if (ent == TypeEnt.CLIENT) {

            // envia get file e o servidor vai verificar se tem o ficheiro
            Packet p = new Packet(filename, "get");

            this.sendPacketInfo(socket, address, port, kryo, p, TypePk.FNOP);

            System.out.println("enviou pacote de get");

        }

        // recebe hash e nrParts do ficheiro
        Packet p = receivePacketInfo(socket, address, port, kryo, TypePk.HASHPARTS);

        int nrParts = p.getParts();
        byte[] hashFile = p.getHash();

        // recebe ficheiro
        receptionDataFlow(this.socket, nrParts, filename, hashFile);

        // envia ACK
        sendHandshake(socket, address, port, kryo, TypeAck.CONTROL);
    }

    public void send(TypeEnt ent, String filename) throws IOException, ClassNotFoundException, NoSuchAlgorithmException, InterruptedException {

        // verifica se tem o ficheiro

        System.out.println("Present Project Directory : "+ System.getProperty("user.dir"));
        File f = new File(filename);
        if(!f.isFile()) {

            System.out.println("FICHEIRO NÃO EXISTE!");
            return;
        }

        if (ent == TypeEnt.CLIENT) {

            // Envia pacote a dizer que quer mandar ficheiro
            Packet p = new Packet(filename, "put");

            this.sendPacketInfo(socket, address, port, kryo, p, TypePk.FNOP);

        }

        // envia hash e nrParts
        byte[] hash = this.getHashFile(filename);
        int nrParts = Packet.fileToNrParts(filename, sizeOfPacket);

        Packet p = new Packet(hash, nrParts);

        this.sendPacketInfo(socket, address, port, kryo, p, TypePk.HASHPARTS);

        System.out.println("preparado para enviar");

        // envia ficheiro
        dispatchDataFlow(socket, filename);

        // recebe ACK
        this.receiveHandshake(socket, address, port, kryo, TypeAck.CONTROL);
    }

    public void receptionDataFlow(DatagramSocket socket, int nrParts, String filename, byte[] hash) throws IOException, InterruptedException, NoSuchAlgorithmException {

        AtomicInteger iWritten = new AtomicInteger(0);
        int iWait = 0;

        System.out.println("partes: " + nrParts);

        FileOutputStream outToFile = new FileOutputStream("saida.mp3");

        Map<Integer, Packet> buffer = Collections.synchronizedMap(new HashMap<>());

        PacketListener pListener = new PacketListener(socket, address, port, buffer, iWritten);
        Thread t1 = new Thread(pListener);
        t1.start();

        // ciclo de escrita
        while(true) {

            synchronized (buffer) {
                while (!buffer.containsKey(iWritten.get())) {

                    iWait++;

                    if (buffer.containsKey(iWritten.get())) break;

                    // pedir reenvio
                    if (iWait == 5 || buffer.size() >= 15) {

                        iWait = 0;
                        System.out.println("PEDINDO REENVIO do " + iWritten.get());

                        // Send acknowledgement
                        Ack ack = new Ack(TypeAck.DATAFLOW, iWritten.get(), -1);
                        byte[] ackpack = Ack.ackToBytes(this.kryo, ack, ack.getType());
                        DatagramPacket sendPacket = new DatagramPacket(ackpack, ackpack.length, this.address, this.port);
                        socket.send(sendPacket);
                    }

                    buffer.wait();

                }
            }

             // se tem o ficheiro
            if (buffer.containsKey(iWritten.get())) {

                while(buffer.containsKey(iWritten.get())) {

                    Packet p = buffer.get(iWritten.get());

                    outToFile.write(p.getData());
                    System.out.println("A escrever o segmento: "+iWritten);
                    iWritten.incrementAndGet();
                }
            }

            if (iWritten.get() == nrParts + 1) {

                outToFile.close();

                if (Arrays.equals(hash, getHashFile("saida.mp3"))) {

                    t1.stop();
                    t1.interrupt();
                    System.out.println("Ficheiro recebido com sucesso.");
                    return;

                } else {

                    System.out.println("Falha a receber o ficheiro");
                    return;
                }
            }

        }
    }

    public void dispatchDataFlow(DatagramSocket socket, String fileName) throws IOException, NoSuchAlgorithmException, InterruptedException {

        File file = new File(fileName);

        CopyOnWriteArrayList<Packet> chunks = new CopyOnWriteArrayList<>(Packet.fileToChunks(file, sizeOfPacket));

        CopyOnWriteArrayList<Packet> priority = new CopyOnWriteArrayList<>();

        CopyOnWriteArraySet<Integer> success = new CopyOnWriteArraySet<>();

        System.out.println("TAMANHO DO ARRAY:" + chunks.size());

        int parts = chunks.size();

        Semaphore windowSemaph = new Semaphore(window + 1);

        AckListener aListener = new AckListener(socket, address, port, success, chunks, priority, windowSemaph, parts);
        Thread t1 = new Thread(aListener);
        t1.start();

        int index = 0;
        int savedIndex = 0;

        System.out.println("partes a enviar : "+ parts);

        while (true) {

            if (!priority.isEmpty()) {

                for (Packet p : priority) {

                    byte[] message = Packet.packetToBytes(this.kryo, p, TypePk.DATA);
                    DatagramPacket sendPacket = new DatagramPacket(message, message.length, this.address, this.port);
                    socket.send(sendPacket);

                    System.out.println("REENVIO Sent: Sequence number = " + p.getSeqNumber());

                    priority.remove(p);
                }

                index = savedIndex;

            }

            if (success.size() == chunks.size() && priority.isEmpty()){

                t1.interrupt();
                System.out.println("Ficheiro enviado com sucesso.");
                return;

            }

            while (windowSemaph.availablePermits() >= 0) {

                if (success.size() == chunks.size()) break;

                for (; index < chunks.size();) {

                    Packet p = chunks.get(index);

                    if (!priority.isEmpty()) break;

                    if (success.contains(p.getSeqNumber())) continue;

                    byte[] message = Packet.packetToBytes(this.kryo, p, TypePk.DATA);
                    DatagramPacket sendPacket = new DatagramPacket(message, message.length, this.address, this.port);
                    socket.send(sendPacket);

                    windowSemaph.acquire();

                    System.out.println("Sent: " + p.getSeqNumber());

                    System.out.println("SIZE Do sucesso = " + success.size());

                    // System.out.println("SIZE DA JANELA DEPOIS = " + windowSemaph.availablePermits());

                    index++;
                    savedIndex = index;
                }

                if (!priority.isEmpty()) break;

            }
        }

    }

    private byte[] getHashFile(String filename) throws NoSuchAlgorithmException, IOException {

        byte[] b = Files.readAllBytes(Paths.get(filename));

        byte[] hash = MessageDigest.getInstance("MD5").digest(b);

        return Arrays.copyOf(hash, 8);
    }

    private void sendStatusAck(TypeAck type, int status) throws IOException {

        Ack ack = new Ack(type, status);
        byte[] ackpack = Ack.ackToBytes(this.kryo, ack, type);
        DatagramPacket sendPacket = new DatagramPacket(ackpack, ackpack.length, this.address, this.port);

        socket.send(sendPacket);
    }

    private Ack receiveStatusAck() throws IOException, ClassNotFoundException {

        byte[] ack = new byte[50];
        DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

        // socket.setSoTimeout(2000);
        socket.receive(ackpack);

        ack = ackpack.getData();

        return Ack.bytesToAck(this.kryo, ack, TypeAck.CONTROL);
    }

    public static Ack sendHandshake(DatagramSocket socket, InetAddress IPAddress, int port, Kryo kryo, TypeAck type) throws IOException {

        Ack ack = new Ack(type, 1);
        byte[] ackB = Ack.ackToBytes(kryo, ack, type);
        System.out.println("Tamanho do pacote: " + ackB.length);
        DatagramPacket sendPacket = new DatagramPacket(ackB, ackB.length, IPAddress, port);
        socket.send(sendPacket);
        System.out.println("mandei um pacote");

        byte[] message = new byte[200];
        DatagramPacket receivedPacket = new DatagramPacket(message, message.length);

        int retry = 0;

        while (retry < 5) {

            try {

                socket.setSoTimeout(4000);
                socket.receive(receivedPacket);

                message = receivedPacket.getData();
                break;

            } catch (SocketTimeoutException timeout) {

                retry++;
                System.out.println("First connect TIMED-OUT... Sending again!! retry " + retry);
                socket.send(sendPacket);

            }
        }

        if (retry == 5) return null;

        socket.send(sendPacket);

        return Ack.bytesToAck(kryo, message, type);
    }

    public Ack receiveHandshake(DatagramSocket socket, InetAddress IPAddress, int port, Kryo kryo, TypeAck type) throws IOException {

        // só fazer em diferentes de connect pq no connect ja recebeu no server.java
        if (!type.equals(TypeAck.CONNECT)) {

            byte[] ack = new byte[50];
            DatagramPacket receivePacket = new DatagramPacket(ack, ack.length);
            socket.receive(receivePacket);
        }

        Ack ack = new Ack(type, 1);
        byte[] ackB = Ack.ackToBytes(kryo, ack, type);
        DatagramPacket sendPacket = new DatagramPacket(ackB, ackB.length, IPAddress, port);

        byte[] message = new byte[200];
        int retry = 0;

        socket.send(sendPacket);

        while (retry < 5) {

            try {

                DatagramPacket receivedPacket = new DatagramPacket(message, message.length);

                socket.setSoTimeout(4000);
                socket.receive(receivedPacket);

                return Ack.bytesToAck(kryo, receivedPacket.getData(), type);

            } catch (SocketTimeoutException timeout) {

                retry++;
                System.out.println("First connect TIMED-OUT... Sending again!! retry " + retry);
                socket.send(sendPacket);

            }
        }

        return null;
    }

    public static Ack sendPacketInfo(DatagramSocket socket, InetAddress IPAddress, int port, Kryo kryo, Packet packet, TypePk type) throws IOException {

        byte[] packetB = Packet.packetToBytes(kryo, packet, type);

        DatagramPacket sendPacket = new DatagramPacket(packetB, packetB.length, IPAddress, port);
        socket.send(sendPacket);

        byte[] message = new byte[200];
        DatagramPacket receivedPacket = new DatagramPacket(message, message.length);

        int retry = 0;

        while (retry < 5) {

            try {

                socket.setSoTimeout(4000);
                socket.receive(receivedPacket);

                message = receivedPacket.getData();
                break;

            } catch (SocketTimeoutException timeout) {

                retry++;
                System.out.println("First connect TIMED-OUT... Sending again!! retry " + retry);
                socket.send(sendPacket);
            }
        }

        if (retry == 5) return null;

        Ack ack = new Ack(TypeAck.CONTROL, 1);
        byte[] ackB = Ack.ackToBytes(kryo, ack, TypeAck.CONTROL);
        DatagramPacket sendPacket1 = new DatagramPacket(ackB, ackB.length, IPAddress, port);

        socket.send(sendPacket1);

        return Ack.bytesToAck(kryo, message, TypeAck.CONTROL);
    }

    public Packet receivePacketInfo(DatagramSocket socket, InetAddress IPAddress, int port, Kryo kryo, TypePk type) throws IOException {

        byte[] packetB = new byte[50];
        DatagramPacket receivePacket = new DatagramPacket(packetB, packetB.length);

        Ack ack = new Ack(TypeAck.CONTROL, 1);
        byte[] ackB = Ack.ackToBytes(kryo, ack, TypeAck.CONTROL);
        DatagramPacket sendPacket = new DatagramPacket(ackB, ackB.length, IPAddress, port);

        int retry = 0;

        while (retry < 5) {

            try {

                socket.setSoTimeout(4000);
                socket.receive(receivePacket);

                socket.send(sendPacket);

                Packet p = Packet.bytesToPacket(kryo, receivePacket.getData(), type);

                return p;

            } catch (SocketTimeoutException timeout) {

                retry++;
                System.out.println("First connect TIMED-OUT... Sending again!! retry " + retry);
                socket.send(sendPacket);

            }
        }

        return null;
    }

}
