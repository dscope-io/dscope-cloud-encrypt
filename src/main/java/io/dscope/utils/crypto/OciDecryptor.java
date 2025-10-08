package io.dscope.utils.crypto;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.keymanagement.KmsCryptoClient;
import com.oracle.bmc.keymanagement.model.DecryptDataDetails;
import com.oracle.bmc.keymanagement.requests.DecryptRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public class OciDecryptor implements CloudDecryptor {

    private final AuthenticationDetailsProvider authProvider;
    private final String endpoint;
    private final String keyId;

    public OciDecryptor(Map<String, String> config) {
        Map<String, String> safeConfig = config != null ? config : Map.of();
        this.authProvider = OciKmsSupport.authenticationProvider(safeConfig);
        this.endpoint = OciKmsSupport.resolveEndpoint(safeConfig);
        this.keyId = OciKmsSupport.requireKeyId(safeConfig);
    }

    @Override
    public String decrypt(String cipherBase64) throws Exception {
        try (KmsCryptoClient client = KmsCryptoClient.builder().build(authProvider)) {
            client.setEndpoint(endpoint);
            DecryptDataDetails details = DecryptDataDetails.builder()
                    .keyId(keyId)
                    .ciphertext(cipherBase64)
                    .build();
            DecryptRequest request = DecryptRequest.builder()
                    .decryptDataDetails(details)
                    .build();
            String plaintextBase64 = client.decrypt(request).getDecryptedData().getPlaintext();
            byte[] decoded = Base64.getDecoder().decode(plaintextBase64);
            return new String(decoded, StandardCharsets.UTF_8);
        }
    }
}
