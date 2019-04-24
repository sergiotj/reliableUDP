import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AgentUDP {

    private DatagramSocket socket;
    private InetAddress address;
    private int port;

    private int sizeOfPacket;
    private int window;

    private Kryo kryo;

    private String key;

    private AtomicLong recentRtt;
    private AtomicLong lastRtt;

    public AgentUDP(DatagramSocket socket, InetAddress address, int port, int sizeOfPacket, int window, Kryo kryo) {

        this.socket = socket;
        this.address = address;
        this.port = port;
        this.sizeOfPacket = sizeOfPacket;
        this.window = window;
        this.kryo = kryo;
        this.key = "ChaveEncriProjCC"; // 128 bit key
        this.lastRtt = new AtomicLong(2000);
        this.recentRtt = new AtomicLong(2000);
    }

    public void receive(TypeEnt ent, String filename) throws IOException, InterruptedException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {

        // se for server
        // recebe pacote com o nome do ficheiro // FEITO NO RUN LOGO AQUI NÃO SE FAZ

        if (ent == TypeEnt.CLIENT) {

            // envia get file e o servidor vai verificar se tem o ficheiro
            Packet p = new Packet(filename, "get", key);

            this.sendReliableInfo(p, TypePk.FNOP);

        }

        // recebe hash e nrParts do ficheiro
        Packet p = (Packet) receiveReliableInfo(TypePk.HASHPARTS);

        if (p == null) {

            System.out.println("Falha na conexão. Terminado.");
            return;
        }

        int nrParts = p.getParts();
        byte[] hashFile = p.getHash();

        // recebe ficheiro
        receptionDataFlow(nrParts, filename, hashFile);

        // envia ACK
        Ack a = new Ack(TypeAck.CLOSE, 1, new Timestamp(System.currentTimeMillis()));

        sendReliableInfo(a, TypeAck.CLOSE);

        Thread.sleep(1000);

        System.out.println("PROCESSO CONCLUIDO");
    }

    public void send(TypeEnt ent, String filename) throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InterruptedException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {

        // verifica se tem o ficheiro
        File f = new File(filename);
        if(!f.isFile()) {

            System.out.println("FICHEIRO NÃO EXISTE!");
            return;
        }

        if (ent == TypeEnt.CLIENT) {

            // Envia pacote a dizer que quer mandar ficheiro
            Packet p = new Packet(filename, "put", key);

            this.sendReliableInfo(p, TypePk.FNOP);

        }

        // envia hash e nrParts e Timestamp para rtt
        byte[] hash = this.getHashFile(filename);
        int nrParts = Packet.fileToNrParts(filename, sizeOfPacket);

        Packet p = new Packet(hash, nrParts, new Timestamp(System.currentTimeMillis()));

        Ack a = this.sendReliableInfo(p, TypePk.HASHPARTS);
        Long time = AgentUDP.calculateRTT(System.currentTimeMillis(), a.getTimestamp());

        lastRtt.set(time * 2);
        recentRtt.set(time * 2);

        // envia ficheiro
        dispatchDataFlow(filename);

        // recebe ACK
        receiveReliableInfo(TypeAck.CLOSE);

        System.out.println("PROCESSO CONCLUIDO");
    }

    public void receptionDataFlow(int nrParts, String filename, byte[] hash) throws IOException, InterruptedException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        String directoryName = "sent";
        File directory = new File(directoryName);

        if (!directory.exists()) {

            directory.mkdir();
        }

        String newFilename = directoryName + "/" + filename;

        AtomicInteger iWritten = new AtomicInteger(0);

        System.out.println("partes: " + nrParts);

        FileOutputStream outToFile = new FileOutputStream(newFilename);

        int maxSize = 5;

        Map<Integer, Packet> buffer = Collections.synchronizedMap(new HashMap<>(maxSize));

        PacketListener pListener = new PacketListener(socket, address, port, buffer, iWritten, maxSize);
        Thread t1 = new Thread(pListener);
        t1.start();

        Key aesKey = new SecretKeySpec(key.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");

        cipher.init(Cipher.DECRYPT_MODE, aesKey);

        AtomicInteger dataReceived = new AtomicInteger(0);
        AtomicBoolean stop = new AtomicBoolean(false);

        Thread t2 = new Thread(new Timer(dataReceived, stop));
        t2.start();

        // ciclo de escrita
        while(true) {

            synchronized (buffer) {

                Long maxWait = lastRtt.get();
                while (!buffer.containsKey(iWritten.get())) {

                    buffer.wait(lastRtt.get());

                    if (buffer.containsKey(iWritten.get())) break;

                    // pedir reenvio
                    System.out.println("PEDINDO REENVIO do " + iWritten.get());

                    int size = maxSize - buffer.size();

                    Ack ack = new Ack(TypeAck.DATAFLOW, iWritten.get(), -1, size, new Timestamp(-1));
                    byte[] ackpack = Ack.ackToBytes(this.kryo, ack, ack.getType());
                    DatagramPacket sendPacket = new DatagramPacket(ackpack, ackpack.length, this.address, this.port);
                    socket.send(sendPacket);

                    Thread.sleep(maxWait);

                    maxWait = (long) (maxWait / 0.6);

                }
            }

             // se tem o ficheiro
            if (buffer.containsKey(iWritten.get())) {

                while(buffer.containsKey(iWritten.get())) {

                    Packet p = buffer.get(iWritten.get());

                    byte[] dataToBeWrite = cipher.doFinal(p.getData());

                    dataReceived.getAndAdd(dataToBeWrite.length);

                    outToFile.write(dataToBeWrite);
                    System.out.println("A escrever o segmento: " + iWritten);

                    buffer.remove(iWritten.get());

                    iWritten.incrementAndGet();
                }
            }

            if (iWritten.get() == nrParts + 1) {

                outToFile.close();

                if (Arrays.equals(hash, getHashFile(newFilename))) {

                    t1.stop();
                    t1.interrupt();
                    stop.set(true);
                    System.out.println("Ficheiro recebido com sucesso.");
                    return;

                } else {

                    System.out.println("Falha a receber o ficheiro");
                    return;
                }
            }

        }
    }

    public void dispatchDataFlow(String fileName) throws IOException, NoSuchAlgorithmException, InterruptedException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {

        File file = new File(fileName);

        CopyOnWriteArrayList<Packet> chunks = new CopyOnWriteArrayList<>(Packet.fileToChunks(file, sizeOfPacket, this.key));

        CopyOnWriteArrayList<Packet> priority = new CopyOnWriteArrayList<>();

        CopyOnWriteArraySet<Integer> success = new CopyOnWriteArraySet<>();

        AtomicBoolean stop = new AtomicBoolean(false);

        int parts = chunks.size();

        ResizeableSemaphore windowSemaph = new ResizeableSemaphore();
        windowSemaph.release(5);

        AckListener aListener = new AckListener(socket, success, chunks, priority, windowSemaph, stop, recentRtt, lastRtt);
        Thread t1 = new Thread(aListener);
        t1.start();

        int index = 0;
        int savedIndex = 0;

        System.out.println("Partes a enviar: " + parts);

        while (true) {

            while (windowSemaph.availablePermits() >= 0) {

                if (!priority.isEmpty()) {

                    for (Packet p : priority) {

                        p.addTimestamp(new Timestamp(System.currentTimeMillis()));

                        byte[] message = Packet.packetToBytes(this.kryo, p, TypePk.DATA);
                        DatagramPacket sendPacket = new DatagramPacket(message, message.length, this.address, this.port);

                        Thread.sleep(lastRtt.get());

                        socket.send(sendPacket);

                        System.out.println("REENVIO Sent: Sequence number = " + p.getSeqNumber());

                        priority.remove(p);
                    }

                    index = savedIndex;

                }

                if ((success.size() == chunks.size() && priority.isEmpty()) | stop.get()){

                    t1.interrupt();
                    System.out.println("Ficheiro enviado com sucesso.");
                    return;

                }

                if (stop.get()) break;
                if (success.size() == chunks.size()) break;

                for (; index < chunks.size();) {

                    if (!priority.isEmpty()) break;

                    Packet p = chunks.get(index);

                    if (success.contains(p.getSeqNumber())) continue;

                    p.addTimestamp(new Timestamp(System.currentTimeMillis()));

                    byte[] message = Packet.packetToBytes(this.kryo, p, TypePk.DATA);
                    DatagramPacket sendPacket = new DatagramPacket(message, message.length, this.address, this.port);

                    Thread.sleep(lastRtt.get());

                    socket.send(sendPacket);

                    windowSemaph.acquire();

                    System.out.println("Sent: " + p.getSeqNumber());

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

    public static Ack sendHandshake(Client client, DatagramSocket socket, InetAddress IPAddress, int port, Kryo kryo, TypeAck type) throws IOException {

        Ack ack = new Ack(type, 1, new Timestamp(System.currentTimeMillis()));
        byte[] ackB = Ack.ackToBytes(kryo, ack, type);
        DatagramPacket sendPacket = new DatagramPacket(ackB, ackB.length, IPAddress, port);
        socket.send(sendPacket);
        System.out.println("Mandei um Handshake");

        byte[] message = new byte[200];
        DatagramPacket receivedPacket = new DatagramPacket(message, message.length);

        int retry = 0;

        while (retry < 5) {

            try {

                socket.setSoTimeout(4000);
                socket.receive(receivedPacket);
                System.out.println("Recebi um Handshake");

                client.setSvPort(receivedPacket.getPort());
                sendPacket = new DatagramPacket(ackB, ackB.length, IPAddress, receivedPacket.getPort());

                message = receivedPacket.getData();
                break;

            } catch (SocketTimeoutException timeout) {

                retry++;
                System.out.println("First connect TIMED-OUT... Sending again!! retry " + retry);
                // socket.send(sendPacket);
                // System.out.println("Mandei um Handshake");

            }
        }

        if (retry == 5) return null;

        socket.send(sendPacket);
        System.out.println("Mandei um Handshake");

        return Ack.bytesToAck(kryo, message, type);
    }

    public Ack receiveHandshake(TypeAck type) throws IOException {

        // só fazer em diferentes de connect pq no connect ja recebeu no server.java
        if (!type.equals(TypeAck.CONNECT)) {

            byte[] ack = new byte[50];
            DatagramPacket receivePacket = new DatagramPacket(ack, ack.length);
            socket.receive(receivePacket);
        }

        Ack ack = new Ack(type, 1, new Timestamp(System.currentTimeMillis()));
        byte[] ackB = Ack.ackToBytes(kryo, ack, type);
        DatagramPacket sendPacket = new DatagramPacket(ackB, ackB.length, address, port);

        byte[] message = new byte[200];
        int retry = 0;

        socket.send(sendPacket);
        System.out.println("Mandei um Handshake");

        while (retry < 5) {

            try {

                DatagramPacket receivedPacket = new DatagramPacket(message, message.length);

                socket.setSoTimeout(4000);
                socket.receive(receivedPacket);
                System.out.println("Recebi um Handshake");

                return Ack.bytesToAck(kryo, receivedPacket.getData(), type);

            } catch (SocketTimeoutException timeout) {

                retry++;
                System.out.println("First connect TIMED-OUT... Sending again!! retry " + retry);
                socket.send(sendPacket);
                System.out.println("Mandei um Handshake");

            } catch (IllegalArgumentException ex) {

                System.out.println("Recebeu pacote fora do contexto... descartado");
                socket.send(sendPacket);
                System.out.println("Mandei um Handshake");
            }
        }

        return null;
    }

    private Ack sendReliableInfo(Object info, Enum typeP) throws IOException {

        socket.setSoTimeout(4000);

        boolean packet = false;
        if (Objects.equals(typeP, TypePk.FNOP) || Objects.equals(typeP, TypePk.HASHPARTS)) {

            packet = true;
        }

        byte[] infoB = null;

        if (packet) {

            infoB = Packet.packetToBytes(kryo, (Packet) info, (TypePk) typeP);
        }

        if (!packet) {
            infoB = Ack.ackToBytes(kryo, (Ack) info, (TypeAck) typeP);
        }

        DatagramPacket sendPacket = new DatagramPacket(infoB, infoB.length, address, port);

        socket.send(sendPacket);
        System.out.println("Enviei " + typeP);
        byte[] message = new byte[200];
        DatagramPacket receivedPacket = new DatagramPacket(message, message.length);

        int retry = 0;

        while (retry < 5) {

            try {

                socket.receive(receivedPacket);

                message = receivedPacket.getData();

                Ack ack = new Ack(TypeAck.CONTROL, 1, new Timestamp(System.currentTimeMillis()));
                byte[] ackB = Ack.ackToBytes(kryo, ack, TypeAck.CONTROL);
                DatagramPacket sendPacket1 = new DatagramPacket(ackB, ackB.length, address, port);

                socket.send(sendPacket1);

                Ack ackb = Ack.bytesToAck(kryo, message, TypeAck.CONTROL);

                System.out.println("Recebi ACK: " + ackb.getType());
                System.out.println("Enviei CONTROL");

                if (ackb.getType() != TypeAck.CONTROL) throw new KryoException("Não é do tipo pretendido.");

                return ackb;

            } catch (SocketTimeoutException timeout) {

                retry++;
                System.out.println("First connect TIMED-OUT... Sending again!! retry " + retry);
                socket.send(sendPacket);
                System.out.println("Enviei " + typeP);

            } catch (IllegalArgumentException | KryoException ex) {

                System.out.println("Recebeu pacote fora do contexto... descartado");
                socket.send(sendPacket);
                System.out.println("Enviei " + typeP);
            }

        }

        if (retry == 5) return null;

        return null;
    }

    public Object receiveReliableInfo(Enum typeP) throws IOException {

        int disconnect = 0;
        boolean packet = false;
        if (Objects.equals(typeP, TypePk.FNOP) || Objects.equals(typeP, TypePk.HASHPARTS)) {

            packet = true;

            if (typeP == TypePk.FNOP) {
                socket.setSoTimeout(15000);
                disconnect = 1;
            }

            else socket.setSoTimeout(4000);
        }

        byte[] info = new byte[50];

        int retry = 0;

        while (retry < 5) {

            try {

                DatagramPacket receivePacket = new DatagramPacket(info, info.length);
                socket.receive(receivePacket);

                if (packet) {

                    Packet p = Packet.bytesToPacket(kryo, receivePacket.getData(), (TypePk) typeP);

                    System.out.println("Recebi pacote: ");

                }

                if (!packet) {

                    Ack a1 = Ack.bytesToAck(kryo, receivePacket.getData(), (TypeAck) typeP);

                    System.out.println("Recebi ACK: " + a1.getType());

                }

                break;

            } catch (SocketTimeoutException timeout) {

                if (disconnect == 1) {

                    System.out.println("Cliente Inativo, conexão encerrada");
                    return null;
                }

                retry++;
                System.out.println("First connect TIMED-OUT... Sending again!! retry " + retry);


            } catch (IllegalArgumentException | KryoException ex) {

                retry++;
                System.out.println("Recebeu pacote fora do contexto... descartado");
            }
        }

        if (retry == 5) return null;

        Ack ack = new Ack(TypeAck.CONTROL, 1, new Timestamp(System.currentTimeMillis()));
        byte[] ackB = Ack.ackToBytes(kryo, ack, TypeAck.CONTROL);
        DatagramPacket sendPacket1 = new DatagramPacket(ackB, ackB.length, address, port);

        socket.send(sendPacket1);
        System.out.println("Enviei CONTROL");

        while (retry < 5) {

            try {
                byte[] message = new byte[200];

                DatagramPacket receivedPacket = new DatagramPacket(message, message.length);
                socket.setSoTimeout(4000);
                socket.receive(receivedPacket);

                if (retry == 5) {
                    System.out.println("Falha na comunicação. Pode ocorrer erros...");
                }

                Ack a1 = Ack.bytesToAck(kryo, receivedPacket.getData(), TypeAck.CONTROL);
                System.out.println("Recebi ACK: " + a1.getType());
                if (a1.getType() != TypeAck.CONTROL) throw new KryoException();

                if (packet) {
                    return Packet.bytesToPacket(kryo, info, (TypePk) typeP);
                }

                if (!packet) {
                    return Ack.bytesToAck(kryo, info, (TypeAck) typeP);
                }

            } catch (SocketTimeoutException timeout) {

                retry++;
                System.out.println("TIMEOUT... Retry " + retry);
                socket.send(sendPacket1);
                System.out.println("Enviei CONTROL");


            } catch (IllegalArgumentException | KryoException ex) {

                System.out.println("Recebeu pacote fora do contexto... descartado");
                socket.send(sendPacket1);
                System.out.println("Enviei CONTROL");
            }
        }

        return null;
    }

    public static long calculateRTT(Long now, Timestamp ts2) {

        return now - ts2.getTime();
    }

}
