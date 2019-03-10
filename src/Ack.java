import java.io.*;

public class Ack implements Serializable {

    private TypeAck type;
    private int seqNumber;
    private int status;

    public Ack(TypeAck type, int seqNumber, int status) {

        this.type = type;
        this.seqNumber = seqNumber;
        this.status = status;

    }

    public Ack(TypeAck type, int status) {

        this.type = type;
        this.status = status;

    }

    public TypeAck getType() {

        return this.type;
    }

    public int getSeqNumber() {

        return this.seqNumber;
    }

    public int getStatus() {

        return this.status;
    }

    public static Ack bytesToAck(byte[] bytes) throws IOException, ClassNotFoundException {

        ByteArrayInputStream bytesIn = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bytesIn);

        Ack p = (Ack) ois.readObject();

        ois.close();

        return p;
    }


    public static byte[] ackToBytes(Ack p) throws IOException {

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
