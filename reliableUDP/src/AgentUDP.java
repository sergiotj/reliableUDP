import com.esotericsoftware.kryo.Kryo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;

public class AgentUDP implements Runnable {

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

    // SERVER
    @Override
    public void run() {

        try {

            // 3 way handshake
            this.sendStatusAck(TypeAck.CONTROL, 1);

            Ack a = this.receiveStatusAck();

            if (a.getStatus() != 1) {
                return;
            }

            byte[] message = new byte[200];

            // receive what client wants
            DatagramPacket receivedPacket = new DatagramPacket(message, message.length);

            // 25 cenas para o gajo decidir-se...
            // socket.setSoTimeout(25);
            socket.receive(receivedPacket);
            message = receivedPacket.getData();

            Packet p = Packet.bytesToPacket(this.kryo,message, TypePk.FNOP);

            String filename = p.getFilename();

            String operation = p.getOperation();

            System.out.println("Nome do ficheiro: " + filename);
            System.out.println("Operação recebida: " + operation);

            // se é um put file
            if (operation.equals("put")){

                this.receiveInServer(filename);
            }

            // se é um get file
            if (operation.equals("get")){

                this.sendInServer(filename);
            }

        } catch (IOException | ClassNotFoundException | NoSuchAlgorithmException | InterruptedException exc) {

            exc.printStackTrace();

        }

    }

    ///////////////////////////////////
    /// CLIENT DOWNLOADING
    ///////////////////////////////////
    public void receiveInClient(String filename) throws IOException, ClassNotFoundException, NoSuchAlgorithmException {

        // envia get file e o servidor vai verificar se tem o ficheiro
        Packet p = new Packet(filename, "get");

        byte[] message = Packet.packetToBytes(this.kryo, p, TypePk.FNOP);
        DatagramPacket sendPacket = new DatagramPacket(message, message.length, this.address, this.port);
        socket.send(sendPacket);

        System.out.println("enviou pacote de get");

        // recebe ACK
        Ack a = receiveStatusAck();
        if (a.getStatus() != 1) {
            return;
        }

        // envia ACK
        this.sendStatusAck(TypeAck.CONTROL, 1);

        // recebe hash e nrParts do ficheiro
        byte[] message1 = new byte[sizeOfPacket];

        DatagramPacket receivedPacket = new DatagramPacket(message1, message1.length);
        socket.receive(receivedPacket);
        message1 = receivedPacket.getData();
        Packet p1 = Packet.bytesToPacket(this.kryo, message1, TypePk.HASHPARTS);

        int nrParts = p1.getParts();
        byte[] hashFile = p1.getHash();

        // envia ACK
        this.sendStatusAck(TypeAck.CONTROL, 1);

        System.out.println("preparado para receber");

        // recebe ficheiro
        receptionDataFlow(this.socket, nrParts, filename, hashFile);

        // envia ACK
        this.sendStatusAck(TypeAck.CONTROL, 1);

    }

    public void sendInServer(String filename) throws IOException, ClassNotFoundException, NoSuchAlgorithmException, InterruptedException {

        // verifica se tem o ficheiro

        /*
        System.out.println("Present Project Directory : "+ System.getProperty("user.dir"));
        File f = new File(filename);
        if(!f.isFile()) {

            System.out.println("FICHEIRO NÃO EXISTE!");
            return;
        }
        */

        // envia ACK
        this.sendStatusAck(TypeAck.CONTROL, 1);

        // recebe ACK
        Ack a = receiveStatusAck();
        if (a.getStatus() != 1) {
            return;
        }

        // envia hash e nrParts
        byte[] hash = this.getHashFile(filename);
        int nrParts = Packet.fileToNrParts(filename, sizeOfPacket);

        Packet p = new Packet(hash, nrParts);

        byte[] message = Packet.packetToBytes(this.kryo, p, TypePk.HASHPARTS);
        DatagramPacket sendPacket = new DatagramPacket(message, message.length, this.address, this.port);
        socket.send(sendPacket);

        // recebe ACK
        Ack a1 = receiveStatusAck();
        if (a1.getStatus() != 1) {
            return;
        }

        System.out.println("preparado para enviar");

        // envia ficheiro
        dispatchDataFlow(socket, filename);

        // recebe ACK
        Ack a2 = receiveStatusAck();
        if (a2.getStatus() != 1) {
            return;
        }

        // envia ACK
        this.sendStatusAck(TypeAck.CONTROL, 1);
    }

    ///////////////////////////////////
    /// CLIENT UPLOADING
    ///////////////////////////////////
    public void receiveInServer(String filename) throws IOException, ClassNotFoundException, NoSuchAlgorithmException {

        // recebe pacote com o nome do ficheiro // FEITO NO RUN LOGO AQUI NÃO SE FAZ

        // envia ACK
        this.sendStatusAck(TypeAck.CONTROL, 1);

        // recebe hash e nrParts do ficheiro
        byte[] message = new byte[sizeOfPacket];

        DatagramPacket receivedPacket = new DatagramPacket(message, message.length);
        socket.receive(receivedPacket);
        message = receivedPacket.getData();
        Packet p = Packet.bytesToPacket(this.kryo, message, TypePk.HASHPARTS);

        int nrParts = p.getParts();
        byte[] hashFile = p.getHash();

        // envia ACK
        this.sendStatusAck(TypeAck.CONTROL, 1);

        // recebe ficheiro
        receptionDataFlow(this.socket, nrParts, filename, hashFile);

        // envia ACK
        this.sendStatusAck(TypeAck.CONTROL, 1);

        // recebe ACK
        Ack a = receiveStatusAck();
        if (a.getStatus() != 1) {
            return;
        }
    }

    public void sendInClient(String filename) throws IOException, ClassNotFoundException, NoSuchAlgorithmException, InterruptedException {

        // Envia pacote a dizer que quer mandar ficheiro
        Packet p = new Packet(filename, "put");

        byte[] message = Packet.packetToBytes(this.kryo, p, TypePk.FNOP);
        DatagramPacket sendPacket = new DatagramPacket(message, message.length, this.address, this.port);
        socket.send(sendPacket);

        // recebe ACK
        Ack a = receiveStatusAck();
        if (a.getStatus() != 1) {
            return;
        }

        // envia hash e nrParts
        byte[] hash = this.getHashFile(filename);
        int nrParts = Packet.fileToNrParts(filename, sizeOfPacket);

        Packet p1 = new Packet(hash, nrParts);

        byte[] message1 = Packet.packetToBytes(this.kryo, p1, TypePk.HASHPARTS);
        DatagramPacket sendPacket1 = new DatagramPacket(message1, message1.length, this.address, this.port);
        socket.send(sendPacket1);

        // recebe ACK
        Ack a1 = receiveStatusAck();
        if (a1.getStatus() != 1) {
            return;
        }

        // envia ficheiro
        dispatchDataFlow(socket, filename);

        // recebe ACK
        Ack a2 = receiveStatusAck();
        if (a2.getStatus() != 1) {
            return;
        }

        // envia ACK
        this.sendStatusAck(TypeAck.CONTROL, 1);
    }


    public void receptionDataFlow(DatagramSocket socket, int nrParts, String filename, byte[] hash) throws IOException, ClassNotFoundException, NoSuchAlgorithmException {

        int iWritten = 0;
        int iWait = 0;

        System.out.println("partes: " + nrParts);

        FileOutputStream outToFile = new FileOutputStream("saida.mp3");

        HashMap<Integer, Packet> buffer = new HashMap<>();

        // ciclo de escrita
        while(true) {

            // se tem o ficheiro
            if (buffer.containsKey(iWritten)){

                 while (buffer.containsKey(iWritten)) {

                     Packet p = buffer.get(iWritten);

                     outToFile.write(p.getData());
                     System.out.println("A escrever o segmento: " + iWritten);
                     iWritten++;
                 }

                iWait = 0;

            } else {

                iWait++;

                if (iWait == window / 2) {

                    System.out.println("PEDINDO REENVIO!!!");

                    // Send acknowledgement
                    Ack ack = new Ack(TypeAck.DATAFLOW, iWritten, -1);
                    byte[] ackpack = Ack.ackToBytes(this.kryo, ack, ack.getType());
                    DatagramPacket sendPacket = new DatagramPacket(ackpack, ackpack.length, this.address, this.port);
                    socket.send(sendPacket);
                }

            }

            // ciclo pacotes
            while(true) {

                // recebe pacote
                byte[] message = new byte[100000];
                DatagramPacket receivedPacket = new DatagramPacket(message, message.length);
                socket.receive(receivedPacket);
                message = receivedPacket.getData();
                Packet p = Packet.bytesToPacket(this.kryo, message, TypePk.DATA);

                // seqNumber from packet
                int seqNumber = p.getSeqNumber();

                // comparar com o que se quer agora
                if (seqNumber >= iWritten) {

                    if (!Arrays.equals(p.getHash(), this.getHashChunk(p.getData()))) {

                        System.out.println("Chegou um pacote corrompido. Sending ACK to resend...");

                        // Send acknowledgement
                        Ack ack = new Ack(TypeAck.DATAFLOW, p.getSeqNumber(), -1);
                        byte[] ackpack = Ack.ackToBytes(this.kryo, ack, ack.getType());
                        DatagramPacket sendPacket = new DatagramPacket(ackpack, ackpack.length, this.address, this.port);
                        socket.send(sendPacket);

                        // sai do ciclo para começar de novo
                        continue;
                    }

                    else {

                        if (seqNumber == iWritten) {

                            outToFile.write(p.getData());
                            System.out.println("A escrever o segmento: " + iWritten);
                            iWritten++;

                        }

                        if (seqNumber > iWritten) {

                            buffer.put(p.getSeqNumber(), p);

                        }

                        // Send acknowledgement
                        Ack ack = new Ack(TypeAck.DATAFLOW, p.getSeqNumber(), 1);
                        byte[] ackpack = Ack.ackToBytes(this.kryo, ack, ack.getType());
                        DatagramPacket sendPacket = new DatagramPacket(ackpack, ackpack.length, this.address, this.port);
                        socket.send(sendPacket);

                        System.out.println("Mandei um ACK" + p.getSeqNumber());

                        if (iWritten == nrParts + 1) {

                            outToFile.close();

                            if (Arrays.equals(hash, getHashFile("saida.mp3"))) {

                                System.out.println("Ficheiro recebido com sucesso.");
                                return;

                            } else {

                                System.out.println("Falha a receber o ficheiro");
                                return;
                            }
                        }

                        break;
                    }
                }


                else {

                    System.out.println("Pacote fora do contexto ou duplicado! DESCARTADO!");

                }

            }
            // there are missing parts... so, while loop should continue
        }
    }

    public void dispatchDataFlow(DatagramSocket socket, String fileName) throws IOException, NoSuchAlgorithmException, InterruptedException {

        File file = new File(fileName);

        ArrayList<Packet> chunks1 = Packet.fileToChunks(file, sizeOfPacket);

        CopyOnWriteArrayList<Packet> chunks = new CopyOnWriteArrayList<>(chunks1);

        CopyOnWriteArrayList<Packet> priority = new CopyOnWriteArrayList<>();

        System.out.println("TAMANHO DO ARRAY:" + chunks.size());

        int flag = 0;

        Semaphore windowSemaph = new Semaphore(window + 1);

        AckListener aListener = new AckListener(socket, address, port, chunks, priority, windowSemaph, flag);
        Thread t1 = new Thread(aListener);
        t1.start();

        int sent = 0;
        int parts = chunks.size();

        System.out.println("partes a enviar : "+ parts);

        while (true) {

            if (!priority.isEmpty()) {

                for (Packet p : priority) {

                    System.out.println("ENTROU NAS PRIORIDADES e vai enviar reenvio");

                    byte[] message = Packet.packetToBytes(this.kryo, p, TypePk.DATA);
                    DatagramPacket sendPacket = new DatagramPacket(message, message.length, this.address, this.port);
                    socket.send(sendPacket);

                    System.out.println("REENVIO Sent: Sequence number = " + p.getSeqNumber());

                    priority.remove(p);
                }
            }

            System.out.println("Tamannho da Janela antes = " + windowSemaph.availablePermits());
            while (windowSemaph.availablePermits() >= 0) {

                if (chunks.isEmpty()) break;

                for (Packet p : chunks) {

                    if (!priority.isEmpty()) break;

                    System.out.println("OIEE");

                    p.addHash();

                    System.out.println("Vai enviar:" + p.getSeqNumber());

                    byte[] message = Packet.packetToBytes(this.kryo, p, TypePk.DATA);
                    DatagramPacket sendPacket = new DatagramPacket(message, message.length, this.address, this.port);
                    socket.send(sendPacket);

                    System.out.println("Enviou:" + p.getSeqNumber());

                    windowSemaph.acquire();

                    System.out.println("Sent: Sequence number = " + p.getSeqNumber());

                    System.out.println("SIZE DA CENA = " + chunks.size());

                    System.out.println("SIZE DA CENA PRIO = " + priority.size());

                    System.out.println("SIZE DA JANELA DEPOIS = " + windowSemaph.availablePermits());

                    sent++;

                    // rever isto
                    if (windowSemaph.availablePermits() == 0 && chunks.size() > 10) break;
                }



                if (chunks.isEmpty() && priority.isEmpty()){

                    flag = 1;
                    t1.interrupt();
                    System.out.println("Ficheiro enviado com sucesso.");
                    return;

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

    private byte[] getHashChunk(byte[] chunks) throws NoSuchAlgorithmException {

        byte[] hash = MessageDigest.getInstance("MD5").digest(chunks);

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

        // socket.setSoTimeout(50);
        socket.receive(ackpack);

        ack = ackpack.getData();

        return Ack.bytesToAck(this.kryo, ack, TypeAck.CONTROL);
    }

}
