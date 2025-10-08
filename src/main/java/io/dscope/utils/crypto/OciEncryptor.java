package io.dscope.utils.crypto;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.keymanagement.KmsCryptoClient;
import com.oracle.bmc.keymanagement.model.EncryptDataDetails;
import com.oracle.bmc.keymanagement.requests.EncryptRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public class OciEncryptor implements CloudEncryptor {

    private final AuthenticationDetailsProvider authProvider;
    private final String endpoint;
    private final String keyId;

    public OciEncryptor(Map<String, String> config) {
        Map<String, String> safeConfig = config != null ? config : Map.of();
        this.authProvider = OciKmsSupport.authenticationProvider(safeConfig);
        this.endpoint = OciKmsSupport.resolveEndpoint(safeConfig);
        this.keyId = OciKmsSupport.requireKeyId(safeConfig);
    }

    @Override
    public String encrypt(String plainText) throws Exception {
        try (KmsCryptoClient client = KmsCryptoClient.builder().build(authProvider)) {
            client.setEndpoint(endpoint);
            EncryptDataDetails details = EncryptDataDetails.builder()
                    .keyId(keyId)
                    .plaintext(Base64.getEncoder().encodeToString(plainText.getBytes(StandardCharsets.UTF_8)))
                    .build();
            EncryptRequest request = EncryptRequest.builder()
                    .encryptDataDetails(details)
                    .build();
            return client.encrypt(request).getEncryptedData().getCiphertext();
        }
    }
}
