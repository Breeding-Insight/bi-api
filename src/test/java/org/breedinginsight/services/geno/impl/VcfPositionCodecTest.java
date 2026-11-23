package org.breedinginsight.services.geno.impl;

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.SortingCollection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

class VcfPositionCodecTest {
    @TempDir
    Path directory;

    @Test
    void duplicateKeysSurviveDiskSpillAndFilesAreCleanedUp() throws Exception {
        SortingCollection<String> keys = SortingCollection.newInstance(
                String.class, new VcfPositionCodec(), Comparator.naturalOrder(), 2, directory);
        try {
            keys.add("SNP:chr2:1");
            keys.add("SNP:chr1:1");
            keys.add("INDEL:chr1:1");
            keys.add("SNP:chr1:1");
            keys.doneAdding();
            try (CloseableIterator<String> sorted = keys.iterator()) {
                assertEquals("INDEL:chr1:1", sorted.next());
                assertEquals("SNP:chr1:1", sorted.next());
                assertEquals("SNP:chr1:1", sorted.next());
                assertEquals("SNP:chr2:1", sorted.next());
                assertFalse(sorted.hasNext());
            }
        } finally {
            keys.cleanup();
        }
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }
}
