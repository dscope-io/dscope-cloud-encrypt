package io.dscope.utils.crypto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class EncryptorMockTest {

    @Test
    void dry_run_makes_no_changes() {
        List<String> lines = Arrays.asList(
                "DB_PASSWORD=myPass",
                "API_TOKEN=abcd"
        );
        Function<String,String> enc = s -> "ENCODED(" + s + ")";
        CloudEncryptCore.Result r = CloudEncryptCore.processLines(lines, true, false, false, enc, null);
        assertEquals(2, r.changedCount);
        assertEquals(lines, r.outputLines);
    }
}
