package org.breedinginsight.services.geno.impl;

import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.samtools.util.SortingCollection;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Length-prefixed positional keys for HTSJDK's disk-backed sort. */
final class VcfPositionCodec implements SortingCollection.Codec<String> {
    private DataInputStream input;
    private DataOutputStream output;

    @Override
    public void setInputStream(InputStream stream) {
        input = new DataInputStream(stream);
    }

    @Override
    public void setOutputStream(OutputStream stream) {
        output = new DataOutputStream(stream);
    }

    @Override
    public void encode(String key) {
        try {
            byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
            output.writeInt(bytes.length);
            output.write(bytes);
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    public String decode() {
        try {
            int first = input.read();
            if (first == -1) {
                return null;
            }
            int length = (first << 24) | (input.readUnsignedByte() << 16)
                    | (input.readUnsignedByte() << 8) | input.readUnsignedByte();
            byte[] bytes = new byte[length];
            input.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    public VcfPositionCodec clone() {
        return new VcfPositionCodec();
    }
}
