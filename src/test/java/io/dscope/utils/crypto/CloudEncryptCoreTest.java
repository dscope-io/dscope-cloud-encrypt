package io.dscope.utils.crypto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class CloudEncryptCoreTest {

    @Test
    void encrypt_sensitive_keys_in_lines() {
        List<String> lines = Arrays.asList(
                "DB_USER=myuser",
                "DB_PASSWORD=myPass",
                "# comment",
                "API_TOKEN=abcd"
        );

        Function<String,String> enc = s -> "ENCODED(" + s + ")";
        CloudEncryptCore.Result r = CloudEncryptCore.processLines(lines, false, false, false, enc, null);

        assertEquals(2, r.changedCount);
        assertTrue(r.outputLines.get(1).contains("ENC("));
        assertTrue(r.outputLines.get(3).contains("ENC("));
    }

    @Test
    void decrypt_when_value_wrapped_in_ENC() {
        List<String> lines = Arrays.asList(
                "DB_PASSWORD=ENC(ciph)",
                "API_TOKEN=ENC(t1)"
        );
        Function<String,String> dec = s -> "plain";
        CloudEncryptCore.Result r = CloudEncryptCore.processLines(lines, false, true, false, null, dec);
        assertEquals(2, r.changedCount);
        assertEquals("DB_PASSWORD=plain", r.outputLines.get(0));
        assertEquals("API_TOKEN=plain", r.outputLines.get(1));
    }

    @Test
    void check_mode_detects_unencrypted() {
        List<String> lines = Arrays.asList(
                "DB_PASSWORD=myPass",
                "USER=admin",
                "API_TOKEN=abcd"
        );
        CloudEncryptCore.Result r = CloudEncryptCore.processLines(lines, false, false, true, null, null);
        assertEquals(2, r.unencryptedCount);
        assertTrue(r.affectedKeys.contains("DB_PASSWORD"));
        assertTrue(r.affectedKeys.contains("API_TOKEN"));
        // lines unchanged
        assertEquals("DB_PASSWORD=myPass", r.outputLines.get(0));
    }
}
