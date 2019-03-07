public class Packet {

    private byte[] fullPacket;
    private int seqNumber;
    private int hash;

    public Packet(byte[] fullPacket) {

        this.fullPacket = fullPacket;

    }

    public int getSeqNumber() {

        // sequence number
        this.seqNumber = this.fullPacket[0];

        // byte to Int
        int seqNumberInt = (seqNumber & 0xFF);

        return seqNumberInt;
    }

    public byte[] retrieveData(int sizeOfPacket, int sizeOfHeader) {

        byte[] fileByteArray = new byte[sizeOfPacket - sizeOfHeader];

        // Retrieve data from message
        System.arraycopy(this.fullPacket, sizeOfHeader, fileByteArray, 0, sizeOfPacket - sizeOfHeader);

        return fileByteArray;
    }


}
