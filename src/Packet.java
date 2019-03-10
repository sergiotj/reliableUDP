import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;

public class Packet {

    private byte[] data;
    private int seqNumber;
    private byte[] hash;

    public Packet(byte[] fullPacket) {

        this.data = fullPacket;

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

    public byte[] retrieveData(int sizeOfPacket, int sizeOfHeader) {

        byte[] fileByteArray = new byte[sizeOfPacket - sizeOfHeader];

        // Retrieve data from message
        System.arraycopy(this.data, sizeOfHeader, fileByteArray, 0, sizeOfPacket - sizeOfHeader);

        return fileByteArray;
    }

    public static ArrayList<Packet> fileToChunks(File filename, int sizeOfPacket) throws IOException {

        // Create a byte array to store file
        byte[] fileContent = Files.readAllBytes(filename.toPath());

        // Divide byte array in chunks
        byte[][] chunks = new byte[(int) Math.ceil(fileContent.length/(double) 1021)][1021];

        for(int i = 0, start = 0; i < chunks.length; i++) {

            chunks[i] = Arrays.copyOfRange(fileContent, start, start + sizeOfPacket);
            start += sizeOfPacket;
        }

        int i = 0;

        ArrayList<Packet> packets = new ArrayList<>();
        for(byte[] chunk : chunks){

            Packet p = new Packet(chunk);

            p.setSeqNumber(i);

            packets.add(p);

            i++;

        }

        return packets;

    }

    public void addHash() throws NoSuchAlgorithmException {

        byte[] hash = MessageDigest.getInstance("MD5").digest(this.data);

        this.hash = Arrays.copyOf(hash, 8);

    }

    public static Packet bytesToPacket(byte[] bytes) throws IOException, ClassNotFoundException {

        ByteArrayInputStream bytesIn = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bytesIn);

        Packet p = (Packet) ois.readObject();

        ois.close();

        return p;
    }


    public static byte[] packetToBytes(Packet p) throws IOException {

        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bytesOut);

        oos.writeObject(p);
        oos.flush();

        byte[] bytes = bytesOut.toByteArray();

        bytesOut.close();
        oos.close();

        return bytes;
    }


}
