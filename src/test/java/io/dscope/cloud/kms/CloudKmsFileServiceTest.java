package io.dscope.cloud.kms;

import io.dscope.utils.crypto.CloudDecryptor;
import io.dscope.utils.crypto.CloudEncryptor;
import io.dscope.utils.crypto.CloudKmsConfig;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudKmsFileServiceTest {

    @Test
    void encryptsAndDecryptsRoundTrip() throws Exception {
        Path tempDir = Files.createTempDirectory("kms-file-service-test");
        Path plaintext = tempDir.resolve("input.txt");
        Files.writeString(plaintext, "hello kms", StandardCharsets.UTF_8);

        Path encrypted = tempDir.resolve("output.kms");
        Path decrypted = tempDir.resolve("output.txt");

        AtomicReference<String> lastPlainKey = new AtomicReference<>();

        CloudKmsFileService service = new CloudKmsFileService(
                (provider, settings) -> new CloudEncryptor() {
                    @Override
                    public String encrypt(String plainText) {
                        lastPlainKey.set(plainText);
                        return "enc:" + plainText;
                    }
                },
                (provider, settings) -> new CloudDecryptor() {
                    @Override
                    public String decrypt(String ciphertext) {
                        assertTrue(ciphertext.startsWith("enc:"));
                        return ciphertext.substring(4);
                    }
                },
                new java.security.SecureRandom());

        CloudKmsConfig config = CloudKmsConfig.builder("aws")
                .with("region", "us-west-2")
                .with("keyId", "alias/test")
                .build();

        service.encryptFile(plaintext, encrypted, config);

        assertTrue(Files.exists(encrypted));
        CloudKmsFileService.KmsFileMetadata metadata = service.inspect(encrypted);
        assertEquals("aws", metadata.provider());
        assertEquals("AES/GCM/NoPadding", metadata.algorithm());
        assertTrue(metadata.encryptedKey().startsWith("enc:"));
        assertEquals(lastPlainKey.get(), metadata.encryptedKey().substring(4));

        service.decryptFile(encrypted, decrypted, config);

        assertTrue(Files.exists(decrypted));
        assertEquals("hello kms", Files.readString(decrypted, StandardCharsets.UTF_8));
    }
}
