import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.sql.Timestamp;

public class AckSerializer extends Serializer<Ack> {

    private TypeAck op;

    public AckSerializer(TypeAck op) {

        this.op = op;
    }

    public void write(Kryo kryo, Output output, Ack a) {

        if (op == TypeAck.DATAFLOW) {

            String type = this.typeToString(a.getType());

            output.writeString(type);
            output.writeInt(a.getSeqNumber());
            output.writeInt(a.getStatus());
            output.writeInt(a.getWindow());
            output.writeInt(a.getRecIndex());
            output.writeString(a.getTimestamp().toString());
        }

        if (op == TypeAck.CONTROL || op == TypeAck.CLOSE) {

            String type = this.typeToString(a.getType());

            output.writeString(type);
            output.writeInt(a.getStatus());
            output.writeString(a.getTimestamp().toString());
        }

        if (op == TypeAck.CONNECT) {

            String type = this.typeToString(a.getType());

            output.writeString(type);
            output.writeInt(a.getStatus());

            output.writeString(a.getUsername());
            output.writeString(a.getPassword());

            output.writeString(a.getTimestamp().toString());

        }

    }

    public Ack read(Kryo kryo, Input input, Class<Ack> type) {

        Ack a = null;

        if (op == TypeAck.DATAFLOW) {

            TypeAck t = this.stringToType(input.readString());

            a = new Ack(t, input.readInt(), input.readInt(), input.readInt(), input.readInt(), Timestamp.valueOf(input.readString()));
        }

        if (op == TypeAck.CONTROL || op == TypeAck.CLOSE) {

            TypeAck t = this.stringToType(input.readString());

            a = new Ack(t, input.readInt(), Timestamp.valueOf(input.readString()));

        }

        if (op == TypeAck.CONNECT) {

            TypeAck t = this.stringToType(input.readString());

            a = new Ack(t, input.readInt(), input.readString(), input.readString(), Timestamp.valueOf(input.readString()));

        }

        return a;
    }

    private String typeToString(TypeAck type) {

        return type.name();
    }

    private TypeAck stringToType(String s) {

        return TypeAck.valueOf(TypeAck.class, s);

    }

}
