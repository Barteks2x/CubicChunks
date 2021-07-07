package io.github.opencubicchunks.cubicchunks.server;

public interface CCServerSavedData {

    void setPackedXZ(int packedXZ);

    boolean blockPosLongNoMatch();

    int getServerPackedXZ();
}
