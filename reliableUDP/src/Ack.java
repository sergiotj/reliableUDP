import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.io.*;
import java.sql.Timestamp;

public class Ack implements Serializable {

    private TypeAck type;
    private int seqNumber;
    private int status;
    private int window;

    private Timestamp rtt;

    private String username;
    private String password;

    public Ack(TypeAck type, int seqNumber, int status, int window, Timestamp rtt) {

        this.type = type;
        this.seqNumber = seqNumber;
        this.status = status;
        this.window = window;
        this.rtt = rtt;

    }

    public Ack(TypeAck type, int status, Timestamp rtt) {

        this.type = type;
        this.status = status;
        this.rtt = rtt;

    }

    public Ack(TypeAck type, int status, String username, String password, Timestamp rtt) {

        this.type = type;
        this.status = status;
        this.rtt = rtt;
        this.username = username;
        this.password = password;

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

    public int getWindow() {

        return this.window;
    }

    public Timestamp getTimestamp() {

        return this.rtt;
    }

    public String getUsername() {

        return this.username;
    }

    public String getPassword() {

        return this.password;
    }

    public static Ack bytesToAck(Kryo kryo, byte[] bytes, TypeAck type) {

        kryo.register(Ack.class, new AckSerializer(type));

        ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
        Input input = new Input(stream);

        Ack ack = kryo.readObject(input, Ack.class);

        input.close();

        return ack;

    }


    public static byte[] ackToBytes(Kryo kryo, Ack a, TypeAck type) {

        kryo.register(Ack.class, new AckSerializer(type));

        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        Output output = new Output(stream);
        kryo.writeObject(output, a);

        output.flush();
        output.close();

        return stream.toByteArray();
    }


}
