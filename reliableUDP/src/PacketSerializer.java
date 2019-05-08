import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.sql.Timestamp;

public class PacketSerializer extends Serializer<Packet> {

    private TypePk op;

    public PacketSerializer (TypePk op) {

        this.op = op;
    }

    public void write(Kryo kryo, Output output, Packet p) {

        if (op == TypePk.FNOP) {

            String type = this.typeToString(p.getType());
            output.writeString(type);

            output.writeString(p.getFilename());
            output.writeString(p.getOperation());
            output.writeInt(p.getWindow());
            output.writeString(p.getKey());
        }

        if (op == TypePk.DATA) {

            output.writeVarInt(p.getData().length + 1, true);
            output.writeBytes(p.getData());

            output.writeInt(p.getSeqNumber());

            output.writeVarInt(p.getHash().length + 1, true);
            output.writeBytes(p.getHash());

            output.writeString(p.getTimestamp().toString());
        }

        if (op == TypePk.HASHPARTS) {

            String type = this.typeToString(p.getType());
            output.writeString(type);

            output.writeVarInt(p.getHash().length + 1, true);
            output.writeBytes(p.getHash());

            output.writeInt(p.getParts());
            output.writeString(p.getTimestamp().toString());

        }
    }

    public Packet read(Kryo kryo, Input input, Class<Packet> type) {

        Packet p = null;

        if (op == TypePk.FNOP) {

            TypePk t = this.stringToType(input.readString());

            String filename = input.readString();
            String operation = input.readString();
            int window = input.readInt();
            String key = input.readString();

            p = new Packet(t, filename, operation, window, key);
        }

        if (op == TypePk.DATA) {

            int length1 = input.readVarInt(true);
            byte[] data = input.readBytes(length1 - 1);

            int seqNumber = input.readInt();

            int length2 = input.readVarInt(true);
            byte[] hash = input.readBytes(length2 - 1);

            p = new Packet(data, seqNumber, hash, Timestamp.valueOf(input.readString()));
        }

        if (op == TypePk.HASHPARTS) {

            TypePk t = this.stringToType(input.readString());

            int length1 = input.readVarInt(true);
            byte[] hash = input.readBytes(length1 - 1);

            int parts = input.readInt();

            p = new Packet(t, hash, parts, Timestamp.valueOf(input.readString()));
        }

        return p;
    }

    private String typeToString(TypePk type) {

        return type.name();
    }

    private TypePk stringToType(String s) {

        return TypePk.valueOf(TypePk.class, s);

    }
}
