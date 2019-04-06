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
import java.lang.reflect.Type;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.Key;
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

    private String key;

    public AgentUDP(DatagramSocket socket, InetAddress address, int port, int sizeOfPacket, int window, Kryo kryo) {

        this.socket = socket;
        this.address = address;
        this.port = port;
        this.sizeOfPacket = sizeOfPacket;
        this.window = window;
        this.kryo = kryo;
        this.key = "ChaveEncriProjCC"; // 128 bit key
    }

    public void receive(TypeEnt ent, String filename) throws IOException, InterruptedException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {

        // se for server
        // recebe pacote com o nome do ficheiro // FEITO NO RUN LOGO AQUI NÃO SE FAZ

        if (ent == TypeEnt.CLIENT) {

            // envia get file e o servidor vai verificar se tem o ficheiro
            Packet p = new Packet(filename, "get", key);

            this.sendReliableInfo(socket, address, port, kryo, p, TypePk.FNOP);

        }

        // recebe hash e nrParts do ficheiro
        Packet p = (Packet) receiveReliableInfo(socket, address, port, kryo, TypePk.HASHPARTS);

        int nrParts = p.getParts();
        byte[] hashFile = p.getHash();

        // recebe ficheiro
        receptionDataFlow(this.socket, nrParts, filename, hashFile);

        // envia ACK
        Ack a = new Ack(TypeAck.CONTROL, 1);
        System.out.println("vai mandar OK");
        sendReliableInfo(socket, address, port, kryo, a, TypeAck.CONTROL);
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

            this.sendReliableInfo(socket, address, port, kryo, p, TypePk.FNOP);

        }

        // envia hash e nrParts
        byte[] hash = this.getHashFile(filename);
        int nrParts = Packet.fileToNrParts(filename, sizeOfPacket);

        Packet p = new Packet(hash, nrParts);

        this.sendReliableInfo(socket, address, port, kryo, p, TypePk.HASHPARTS);

        System.out.println("preparado para enviar");

        // envia ficheiro
        dispatchDataFlow(socket, filename);

        // recebe ACK
        receiveReliableInfo(socket, address, port, kryo, TypeAck.CONTROL);
    }

    public void receptionDataFlow(DatagramSocket socket, int nrParts, String filename, byte[] hash) throws IOException, InterruptedException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        String directoryName = "out";
        File directory = new File(directoryName);

        if (!directory.exists()) {

            directory.mkdir();
        }

        String newFilename = directoryName + "/" + filename;

        AtomicInteger iWritten = new AtomicInteger(0);
        int iWait = 0;

        System.out.println("partes: " + nrParts);

        FileOutputStream outToFile = new FileOutputStream(newFilename);

        Map<Integer, Packet> buffer = Collections.synchronizedMap(new HashMap<>());

        PacketListener pListener = new PacketListener(socket, address, port, buffer, iWritten);
        Thread t1 = new Thread(pListener);
        t1.start();

        Key aesKey = new SecretKeySpec(key.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");

        cipher.init(Cipher.DECRYPT_MODE, aesKey);

        // ciclo de escrita
        while(true) {

            synchronized (buffer) {
                while (!buffer.containsKey(iWritten.get())) {

                    System.out.println("O wait está em: " + iWait);

                    Thread.sleep(50);

                    if (buffer.containsKey(iWritten.get())) break;

                    // pedir reenvio
                    if (iWait == 5) {

                        iWait = 0;
                        System.out.println("PEDINDO REENVIO do " + iWritten.get());

                        // Send acknowledgement
                        Ack ack = new Ack(TypeAck.DATAFLOW, iWritten.get(), -1);
                        byte[] ackpack = Ack.ackToBytes(this.kryo, ack, ack.getType());
                        DatagramPacket sendPacket = new DatagramPacket(ackpack, ackpack.length, this.address, this.port);
                        socket.send(sendPacket);
                    }

                    buffer.wait(500);

                    iWait++;

                }

                iWait = 0;
            }

             // se tem o ficheiro
            if (buffer.containsKey(iWritten.get())) {

                while(buffer.containsKey(iWritten.get())) {

                    Packet p = buffer.get(iWritten.get());

                    byte[] dataToBeWrite = cipher.doFinal(p.getData());

                    outToFile.write(dataToBeWrite);
                    System.out.println("A escrever o segmento: " + iWritten);
                    iWritten.incrementAndGet();
                }
            }

            if (iWritten.get() == nrParts + 1) {

                outToFile.close();

                if (Arrays.equals(hash, getHashFile(newFilename))) {

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

    public void dispatchDataFlow(DatagramSocket socket, String fileName) throws IOException, NoSuchAlgorithmException, InterruptedException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {

        File file = new File(fileName);

        CopyOnWriteArrayList<Packet> chunks = new CopyOnWriteArrayList<>(Packet.fileToChunks(file, sizeOfPacket, this.key));

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

        Ack ack = new Ack(type, 1);
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
                socket.send(sendPacket);
                System.out.println("Mandei um Handshake");

            }
        }

        if (retry == 5) return null;

        socket.send(sendPacket);
        System.out.println("Mandei um Handshake");

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

                retry++;
                System.out.println("Recebeu pacote fora do contexto... descartado");
                socket.send(sendPacket);
                System.out.println("Mandei um Handshake");
            }
        }

        return null;
    }

    public static Ack sendReliableInfo(DatagramSocket socket, InetAddress IPAddress, int port, Kryo kryo, Object info, Enum typeP) throws IOException {

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

        DatagramPacket sendPacket = new DatagramPacket(infoB, infoB.length, IPAddress, port);
        socket.send(sendPacket);

        byte[] message = new byte[200];
        DatagramPacket receivedPacket = new DatagramPacket(message, message.length);

        int retry = 0;

        while (retry < 5) {

            try {

                socket.setSoTimeout(2000);
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

    public Object receiveReliableInfo(DatagramSocket socket, InetAddress IPAddress, int port, Kryo kryo, Enum typeP) throws IOException {

        boolean packet = false;
        if (Objects.equals(typeP, TypePk.FNOP) || Objects.equals(typeP, TypePk.HASHPARTS)) {

            packet = true;
        }

        byte[] packetB = new byte[50];
        DatagramPacket receivePacket = new DatagramPacket(packetB, packetB.length);

        Ack ack = new Ack(TypeAck.CONTROL, 1);
        byte[] ackB = Ack.ackToBytes(kryo, ack, TypeAck.CONTROL);
        DatagramPacket sendPacket = new DatagramPacket(ackB, ackB.length, IPAddress, port);

        int retry = 0;

        while (retry < 5) {

            try {

                if (typeP == TypePk.FNOP && retry == 0) socket.setSoTimeout(0);
                else socket.setSoTimeout(4000);

                socket.receive(receivePacket);

                socket.send(sendPacket);

                if (receivePacket != null) {

                    if (packet) {
                        Packet p = Packet.bytesToPacket(kryo, receivePacket.getData(), (TypePk) typeP);

                        return p;
                    }

                    if (!packet) {
                        Ack a = Ack.bytesToAck(kryo, receivePacket.getData(), (TypeAck) typeP);

                        return a;
                    }
                }

            } catch (SocketTimeoutException timeout) {

                retry++;
                System.out.println("First connect TIMED-OUT... Sending again!! retry " + retry);
                socket.send(sendPacket);

            } catch (KryoException underflow) {

                retry++;
                System.out.println("Recebeu pacote fora do contexto... descartado");
                socket.send(sendPacket);
            }
        }

        return null;
    }

}
