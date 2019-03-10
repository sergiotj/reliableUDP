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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class AgentUDP implements Runnable {

    private DatagramSocket socket;
    private InetAddress address;
    private int port;

    private int sizeOfPacket;
    private int window;

    public AgentUDP(DatagramSocket socket, InetAddress address, int port, int sizeOfPacket, int window) {

        this.socket = socket;
        this.address = address;
        this.port = port;
        this.sizeOfPacket = sizeOfPacket;
        this.window = window;
    }

    // SERVER
    @Override
    public void run() {

        try {

            byte[] message = new byte[50];

            // receive what client wants
            DatagramPacket receivedPacket = new DatagramPacket(message, message.length);

            // 25 cenas para o gajo decidir-se...
            socket.setSoTimeout(25);
            socket.receive(receivedPacket);
            message = receivedPacket.getData();

            Packet p = Packet.bytesToPacket(message);

            String filename = p.getFilename();

            String operation = p.getOperation();

            // se é um put file
            if (operation.equals("put")){

                this.receiveInServer(filename);
            }

            // se é um get file
            if (operation.equals("get")){

                this.sendInServer(filename);
            }

        } catch (IOException | ClassNotFoundException | NoSuchAlgorithmException exc) {

            exc.printStackTrace();

        }

    }

    ///////////////////////////////////
    /// CLIENT DOWNLOADING
    ///////////////////////////////////
    public void receiveInClient(String filename) throws IOException, ClassNotFoundException, NoSuchAlgorithmException {

        // envia get file e o servidor vai verificar se tem o ficheiro

        // recebe ACK
        Ack a = receiveStatusAck();
        if (a.getStatus() != 1) {
            return;
        }

        // envia ACK
        this.sendStatusAck(TypeAck.CONTROL, 1);

        // recebe hash e nrParts do ficheiro

        int nrParts = 0;
        byte[] hashFile = null;

        // envia ACK
        this.sendStatusAck(TypeAck.CONTROL, 1);

        // recebe ficheiro
        receptionDataFlow(this.socket, nrParts, filename, hashFile);

        // envia ACK
        this.sendStatusAck(TypeAck.CONTROL, 1);

        // recebe ACK
        Ack a1 = receiveStatusAck();
        if (a1.getStatus() != 1) {
            return;
        }
    }

    public void sendInServer(String filename) throws IOException, ClassNotFoundException, NoSuchAlgorithmException {

        // verifica se tem o ficheiro

        // envia ACK
        this.sendStatusAck(TypeAck.CONTROL, 1);

        // recebe ACK
        Ack a = receiveStatusAck();
        if (a.getStatus() != 1) {
            return;
        }

        // envia hash e nrParts do ficheiro

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

    ///////////////////////////////////
    /// CLIENT UPLOADING
    ///////////////////////////////////
    public void receiveInServer(String filename) throws IOException, ClassNotFoundException, NoSuchAlgorithmException {

        // recebe pacote com o nome do ficheiro // FEITO NO RUN LOGO AQUI NÃO SE FAZ

        // envia ACK
        this.sendStatusAck(TypeAck.CONTROL, 1);

        // recebe hash e nrParts

        int nrParts = 0;
        byte[] hashFile = null;

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

    public void sendInClient(String filename) throws IOException, ClassNotFoundException, NoSuchAlgorithmException {

        // Envia pacote a dizer que quer mandar ficheiro

        // recebe ACK
        Ack a = receiveStatusAck();
        if (a.getStatus() != 1) {
            return;
        }

        // envia hash e nrParts

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

        FileOutputStream outToFile = new FileOutputStream(filename);

        ArrayList<Integer> missingParts = new ArrayList<>();

        HashMap<Integer, byte[]> buffer = new HashMap<>();

        for (int i = 0; i < nrParts; i++) {

            missingParts.add(i);
        }

        while(true) {

            byte[] message = new byte[sizeOfPacket];

            DatagramPacket receivedPacket = new DatagramPacket(message, message.length);
            socket.receive(receivedPacket);

            message = receivedPacket.getData();

            Packet p = Packet.bytesToPacket(message);

            // Retrieve data from message
            byte[] newData = p.getData();

            if (!Arrays.equals(p.getHash(), this.getHashChunk(newData))) {

                System.out.println("Chegou um pacote corrompido. Será descartado...");
                break;
            }

            // Send acknowledgement
            Ack ack = new Ack(TypeAck.DATAFLOW, p.getSeqNumber(), 1);
            byte[] ackpack = Ack.ackToBytes(ack);
            DatagramPacket sendPacket = new DatagramPacket(ackpack, ackpack.length, this.address, this.port);
            socket.send(sendPacket);

            // removes seqNumber from list of parts missing
            missingParts.remove(Integer.valueOf(p.getSeqNumber()));

            if (iWritten == p.getSeqNumber()) {

                outToFile.write(newData);
                iWritten++;
            }

            else {

                // if part cant be written, it should go to buffer
                buffer.put(p.getSeqNumber(), newData);
            }

            if (missingParts.isEmpty()) {

                // all parts received...
                while (iWritten <= nrParts) {

                    outToFile.write(buffer.get(iWritten));
                    iWritten++;
                }

                if (Arrays.equals(getHashFile(filename), hash)) {

                    System.out.println("Ficheiro recebido com sucesso.");
                    return;
                }

                else {

                    System.out.println("Falha a receber o ficheiro");
                    return;
                }
            }
            // there are missing parts... so, while loop should continue
        }
    }

    public void dispatchDataFlow(DatagramSocket socket, String fileName) throws IOException, NoSuchAlgorithmException, ClassNotFoundException {

        File file = new File(fileName);

        ArrayList<Packet> chunks = Packet.fileToChunks(file, sizeOfPacket);

        while (window > 0) {

            for (Packet p : chunks) {

                p.addHash();

                byte[] message = Packet.packetToBytes(p);
                DatagramPacket sendPacket = new DatagramPacket(message, message.length, this.address, this.port);
                socket.send(sendPacket);

                window--;

                if (window == 0) break;

                System.out.println("Sent: Sequence number = " + p.getSeqNumber());
            }

            while (true) {

                // receiving acknowledgments

                // Create another packet by setting a byte array and creating data gram packet
                byte[] ack = new byte[10];
                DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

                try {

                    // set the socket timeout for the packet acknowledgment
                    socket.setSoTimeout(50);
                    socket.receive(ackpack);

                    ack = ackpack.getData();

                    Ack a = Ack.bytesToAck(ack);

                    if (a.getStatus() == 1 && a.getType() == TypeAck.DATAFLOW){

                        int ackReceived = a.getSeqNumber();

                        for(Packet p : chunks) {

                            if (p.getSeqNumber() == ackReceived) chunks.remove(p);
                        }

                        System.out.println("Ack received: Sequence Number = " + ackReceived);
                    }

                }

                // we did not receive an ack
                catch (SocketTimeoutException e) {

                    System.out.println("Socket timed out waiting for ACKs");
                    return;
                }

                window++;

                if (chunks.isEmpty()) {

                    System.out.println("Envio realizado com sucesso.");
                    return;
                }
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
        byte[] ackpack = Ack.ackToBytes(ack);
        DatagramPacket sendPacket = new DatagramPacket(ackpack, ackpack.length, this.address, this.port);

        socket.send(sendPacket);
    }

    private Ack receiveStatusAck() throws IOException, ClassNotFoundException {

        byte[] ack = new byte[10];
        DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

        socket.setSoTimeout(50);
        socket.receive(ackpack);

        ack = ackpack.getData();

        return Ack.bytesToAck(ack);
    }

}
