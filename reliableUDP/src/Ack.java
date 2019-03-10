import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.*;

public class Ack implements Serializable {

    private TypeAck type;
    private int seqNumber;
    private int status;

    public Ack() {

    }

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

    public static Ack bytesToAck(byte[] bytes) {

        Kryo kryo = new Kryo();
        kryo.register(Ack.class);

        ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
        Input input = new Input(stream);

        Ack ack = kryo.readObject(input, Ack.class);

        input.close();

        return ack;

    }


    public static byte[] ackToBytes(Ack p) {

        Kryo kryo = new Kryo();
        kryo.register(Ack.class);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        Output output = new Output(stream);
        kryo.writeObject(output, p);

        output.close();

        return stream.toByteArray();
    }


}
