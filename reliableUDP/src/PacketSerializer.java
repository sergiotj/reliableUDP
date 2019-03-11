import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class PacketSerializer extends Serializer<Packet> {

    private TypePk op;

    public PacketSerializer (TypePk op) {

        this.op = op;
    }

    public void write(Kryo kryo, Output output, Packet p) {

        if (op == TypePk.FNOP) {

            output.writeString(p.getFilename());
            output.writeString(p.getOperation());
        }

        if (op == TypePk.DATA) {

            output.writeVarInt(p.getData().length + 1, true);
            output.writeBytes(p.getData());

            output.writeInt(p.getSeqNumber());

            output.writeVarInt(p.getHash().length + 1, true);
            output.writeBytes(p.getHash());
        }

        if (op == TypePk.HASHPARTS) {

            output.writeVarInt(p.getHash().length + 1, true);
            output.writeBytes(p.getHash());

            output.writeInt(p.getParts());

        }
    }

    public Packet read(Kryo kryo, Input input, Class<Packet> type) {

        Packet p = null;

        if (op == TypePk.FNOP) {

            p = new Packet(input.readString(), input.readString());
        }

        if (op == TypePk.DATA) {

            int length1 = input.readVarInt(true);
            byte[] data = input.readBytes(length1 - 1);

            int seqNumber = input.readInt();

            int length2 = input.readVarInt(true);
            byte[] hash = input.readBytes(length2 - 1);

            p = new Packet(data, seqNumber, hash);
        }

        if (op == TypePk.HASHPARTS) {

            int length1 = input.readVarInt(true);
            byte[] hash = input.readBytes(length1 - 1);

            int parts = input.readInt();

            p = new Packet(hash, parts);
        }

        return p;
    }
}
