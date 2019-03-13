import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;

public class Packet implements Serializable {

    private byte[] data;
    private int seqNumber;
    private byte[] hash;
    private String filename;
    private String operation;
    private int nrParts;

    public Packet(byte[] fullPacket) {

        this.data = fullPacket;
    }

    public Packet(String filename, String operation) {

        this.filename = filename;
        this.operation = operation;
    }

    public Packet(byte[] data, int seqNumber, byte[] hash) {

        this.data = data;
        this.seqNumber = seqNumber;
        this.hash = hash;
    }

    public Packet(byte[] hash, int nrParts) {

        this.hash = hash;
        this.nrParts = nrParts;
    }

    public String getFilename() {

        return this.filename;
    }

    public int getParts() {

        return this.nrParts;
    }

    public String getOperation() {

        return this.operation;
    }

    public int getSeqNumber() {

        return this.seqNumber;
    }

    public byte[] getData() {

        return this.data;
    }

    public byte[] getHash() {

        return this.hash;
    }

    public void setSeqNumber(int seqNumber) {

        this.seqNumber = seqNumber;
    }

    public static ArrayList<Packet> fileToChunks(File filename, int sizeOfPacket) throws IOException {

        // Create a byte array to store file
        byte[] fileContent = Files.readAllBytes(filename.toPath());

        ArrayList<Packet> packets = new ArrayList<>();

        System.out.println("Tamanho da cena: " + fileContent.length);

        for(int i = 0, start = 0; start < fileContent.length; i++) {

            byte[] chunk = Arrays.copyOfRange(fileContent, start, start + sizeOfPacket);

            Packet p = new Packet(chunk);

            p.setSeqNumber(i);

            packets.add(p);

            start += sizeOfPacket;
        }

        return packets;

    }

    public static int fileToNrParts(String filename, int sizeOfPacket) throws IOException {

        File file = new File(filename);

        // Create a byte array to store file
        byte[] fileContent = Files.readAllBytes(file.toPath());

        int i, start;
        for(i = 0, start = 0; start < fileContent.length; i++) {

            start += sizeOfPacket;
        }

        return i;
    }

    public void addHash() throws NoSuchAlgorithmException {

        byte[] hash = MessageDigest.getInstance("MD5").digest(this.data);

        this.hash = Arrays.copyOf(hash, 8);

    }

    public static Packet bytesToPacket(Kryo kryo, byte[] bytes, TypePk type) {

        kryo.register(Packet.class, new PacketSerializer(type));

        ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
        Input input = new Input(stream);

        Packet p = kryo.readObject(input, Packet.class);

        input.close();

        return p;
    }

    public static byte[] packetToBytes(Kryo kryo, Packet p, TypePk type) {

        kryo.register(Packet.class, new PacketSerializer(type));

        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        Output output = new Output(stream);
        kryo.writeObject(output, p);

        output.flush();
        output.close();

        return stream.toByteArray();
    }

}