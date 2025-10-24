package io.dscope.cloud.kms;

import io.dscope.utils.crypto.CloudDecryptFactory;
import io.dscope.utils.crypto.CloudDecryptor;
import io.dscope.utils.crypto.CloudEncryptFactory;
import io.dscope.utils.crypto.CloudEncryptor;
import io.dscope.utils.crypto.CloudKmsConfig;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.BiFunction;

/**
 * Service that performs envelope encryption for files using the configured cloud KMS provider. The
 * implementation generates a random AES-GCM data key per file, protects it with the provider-specific
 * KMS encryptor, and stores the encrypted data key alongside the ciphertext in a lightweight header.
 */
public class CloudKmsFileService {

    private static final String MAGIC = "DSCOPE-KMS-FILE-ENC-v1";
    private static final String DELIMITER = "----";
    private static final int GCM_TAG_BITS = 128;
    private static final int DATA_KEY_BYTES = 32;
    private static final int IV_BYTES = 12;

    private final BiFunction<String, Map<String, String>, CloudEncryptor> encryptorFactory;
    private final BiFunction<String, Map<String, String>, CloudDecryptor> decryptorFactory;
    private final SecureRandom secureRandom;

    public CloudKmsFileService() {
        this((provider, settings) -> CloudEncryptFactory.create(provider, new HashMap<>(settings)),
                (provider, settings) -> CloudDecryptFactory.create(provider, new HashMap<>(settings)),
                new SecureRandom());
    }

    public CloudKmsFileService(
            BiFunction<String, Map<String, String>, CloudEncryptor> encryptorFactory,
            BiFunction<String, Map<String, String>, CloudDecryptor> decryptorFactory,
            SecureRandom secureRandom) {
        this.encryptorFactory = Objects.requireNonNull(encryptorFactory, "encryptorFactory");
        this.decryptorFactory = Objects.requireNonNull(decryptorFactory, "decryptorFactory");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    /**
     * Encrypts {@code input} and writes a KMS-encrypted payload to {@code output}. The output file contains a
     * small ASCII header followed by Base64-encoded ciphertext.
     */
    public void encryptFile(Path input, Path output, CloudKmsConfig config) throws Exception {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(config, "config");
        ensureRegularFile(input);

        Map<String, String> settings = config.toSettings();
        CloudEncryptor encryptor = encryptorFactory.apply(config.getProvider(), settings);

        byte[] dataKey = randomBytes(DATA_KEY_BYTES);
        byte[] iv = randomBytes(IV_BYTES);

        String encryptedKey = encryptor.encrypt(Base64.getEncoder().encodeToString(dataKey));

        Cipher cipher = initCipher(Cipher.ENCRYPT_MODE, dataKey, iv);

        Path parent = output.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        try (InputStream in = Files.newInputStream(input);
             OutputStream fileOut = Files.newOutputStream(output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
             BufferedOutputStream buffered = new BufferedOutputStream(fileOut)) {

            writeHeader(buffered, config.getProvider(), encryptedKey, iv);

            try (CipherInputStream cipherIn = new CipherInputStream(in, cipher);
                 OutputStream base64Out = Base64.getMimeEncoder().wrap(buffered)) {
                cipherIn.transferTo(base64Out);
            }
        }
    }

    /**
     * Decrypts a file previously produced by {@link #encryptFile(Path, Path, CloudKmsConfig)}.
     */
    public void decryptFile(Path input, Path output, CloudKmsConfig config) throws Exception {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(config, "config");
        ensureRegularFile(input);

        EnvelopeMetadata metadata = readMetadata(input);
        String requestedProvider = config.getProvider();
        if (!metadata.provider.equals(requestedProvider)) {
            throw new IllegalArgumentException("File encrypted with provider '" + metadata.provider + "' but config targeted '" + requestedProvider + "'");
        }

        Map<String, String> settings = config.toSettings();
        CloudDecryptor decryptor = decryptorFactory.apply(metadata.provider, settings);

        String dataKeyBase64 = decryptor.decrypt(metadata.encryptedKey);
        byte[] dataKey = Base64.getDecoder().decode(dataKeyBase64);
        byte[] iv = Base64.getDecoder().decode(metadata.ivBase64);

        Cipher cipher = initCipher(Cipher.DECRYPT_MODE, dataKey, iv);

        Path parent = output.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        byte[] ciphertext = readCiphertextSection(input);
        byte[] plaintext = cipher.doFinal(ciphertext);
        try (OutputStream out = Files.newOutputStream(output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            out.write(plaintext);
        }
    }

    /**
     * Returns metadata extracted from the file header without reading the ciphertext body.
     */
    public KmsFileMetadata inspect(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        ensureRegularFile(path);
        EnvelopeMetadata metadata = readMetadata(path);
        return new KmsFileMetadata(metadata.provider, metadata.algorithm, metadata.encryptedKey);
    }

    private void writeHeader(OutputStream output, String provider, String encryptedKey, byte[] iv) throws IOException {
        String header = MAGIC + '\n'
                + "provider:" + provider + '\n'
                + "encKey:" + encryptedKey + '\n'
                + "iv:" + Base64.getEncoder().encodeToString(iv) + '\n'
                + "algo:AES/GCM/NoPadding\n"
                + DELIMITER + '\n';
        output.write(header.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private EnvelopeMetadata readMetadata(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        String normalized = content.replace("\r", "");
        int delimiterIndex = normalized.indexOf("\n" + DELIMITER + "\n");
        if (delimiterIndex < 0) {
            throw new IllegalArgumentException("File does not contain expected KMS header delimiter");
        }
        String header = normalized.substring(0, delimiterIndex);
        String[] lines = header.split("\n");
        if (lines.length < 4 || !MAGIC.equals(lines[0])) {
            throw new IllegalArgumentException("File is not a DSCOPE KMS encrypted payload");
        }

        Map<String, String> values = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            values.put(key, value);
        }

        String provider = values.get("provider");
        String encryptedKey = values.get("enckey");
        String iv = values.get("iv");
        String algo = values.getOrDefault("algo", "AES/GCM/NoPadding");
        if (provider == null || encryptedKey == null || iv == null) {
            throw new IllegalArgumentException("Header missing required metadata");
        }
        return new EnvelopeMetadata(provider, encryptedKey, iv, algo);
    }

    private byte[] readCiphertextSection(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        String normalized = content.replace("\r", "");
        int delimiterIndex = normalized.indexOf("\n" + DELIMITER + "\n");
        if (delimiterIndex < 0) {
            throw new IllegalArgumentException("File does not contain expected ciphertext delimiter");
        }
        String body = normalized.substring(delimiterIndex + (DELIMITER.length() + 2));
        String sanitized = body.replace("\n", "");
        return Base64.getDecoder().decode(sanitized);
    }

    private Cipher initCipher(int mode, byte[] key, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_BITS, iv);
        cipher.init(mode, keySpec, gcmSpec);
        return cipher;
    }

    private byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private void ensureRegularFile(Path input) throws IOException {
        if (!Files.exists(input)) {
            throw new IOException("File does not exist: " + input);
        }
        if (!Files.isRegularFile(input)) {
            throw new IOException("File is not a regular file: " + input);
        }
    }

    private record EnvelopeMetadata(String provider, String encryptedKey, String ivBase64, String algorithm) { }

    public record KmsFileMetadata(String provider, String algorithm, String encryptedKey) {
        @Override
        public String toString() {
            return new StringJoiner(", ", KmsFileMetadata.class.getSimpleName() + "[", "]")
                    .add("provider='" + provider + '\'')
                    .add("algorithm='" + algorithm + '\'')
                    .add("encryptedKey='" + encryptedKey + '\'')
                    .toString();
        }
    }
}
