import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;

public class Packet implements Serializable {

    private TypePk type;
    private byte[] data;
    private int seqNumber;
    private byte[] hash;
    private String filename;
    private String operation;
    private int nrParts;
    private String key;
    private Timestamp rtt;
    private int window;
    private int rttNow;

    public Packet(byte[] fullPacket) {

        this.data = fullPacket;
    }

    public Packet(TypePk type, String filename, String operation, int window, String key) {

        this.type = type;
        this.filename = filename;
        this.operation = operation;
        this.window = window;
        this.key = key;
    }

    public Packet(byte[] data, int seqNumber, int rttNow, byte[] hash, Timestamp rtt) {

        this.data = data;
        this.seqNumber = seqNumber;
        this.hash = hash;
        this.rtt = rtt;
        this.rttNow = rttNow;
    }

    public Packet(TypePk type, byte[] hash, int nrParts, Timestamp rtt) {

        this.type = type;
        this.hash = hash;
        this.nrParts = nrParts;
        this.rtt = rtt;
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

    public String getKey() {

        return this.key;
    }

    public Timestamp getTimestamp() {

        return this.rtt;
    }

    public TypePk getType() {

        return this.type;
    }

    public int getWindow() {

        return this.window;
    }

    public int getRttNow() {

        return this.rttNow;
    }

    public void setSeqNumber(int seqNumber) {

        this.seqNumber = seqNumber;
    }

    public void setRttNow(int rtt) {

        this.rttNow = rtt;
    }

    public static ArrayList<Packet> fileToChunks(File filename, int sizeOfPacket, String key) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {

        // Create a byte array to store file
        byte[] fileContent = Files.readAllBytes(filename.toPath());

        ArrayList<Packet> packets = new ArrayList<>();

        boolean flag;

        Key aesKey = new SecretKeySpec(key.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey);

        for(int i = 0, start = 0; start < fileContent.length; i++) {

            if ((start + sizeOfPacket) >= fileContent.length) {
                flag = true;
            }

            else flag = false;

            byte[] chunk = null;

            if (flag) {

                chunk = Arrays.copyOfRange(fileContent, start, start + (fileContent.length - start));
            }
            else {

                chunk = Arrays.copyOfRange(fileContent, start, start + sizeOfPacket);
            }

            Packet p = new Packet(cipher.doFinal(chunk));

            p.setSeqNumber(i);

            p.addHash();

            packets.add(p);

            start += sizeOfPacket;
        }

        return packets;

    }

    public static int fileToNrParts(String filename, int sizeOfPacket) throws IOException {

        File file = new File(filename);

        // Create a byte array to store file
        byte[] fileContent = Files.readAllBytes(file.toPath());

        int start;
        boolean flag;

        int i = 0;

        for(start = 0; start < fileContent.length; i++) {

            if ((start + sizeOfPacket) >= fileContent.length) {
                flag = true;
            }

            else flag = false;


            if (flag) start = start + (fileContent.length - start);
            else start = start + sizeOfPacket;

        }

        i--;

        return i;
    }

    public void addHash() throws NoSuchAlgorithmException {

        byte[] hash = MessageDigest.getInstance("MD5").digest(this.data);

        this.hash = Arrays.copyOf(hash, 8);

    }

    public void addTimestamp(Timestamp ts) {

        this.rtt = ts;
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
