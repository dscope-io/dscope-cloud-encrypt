package io.dscope.samples.springkms;

import io.dscope.utils.crypto.CloudKmsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(KmsProperties.class)
public class Application {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    CommandLineRunner decryptAndPrint(CloudKmsClient kmsClient, KmsProperties properties) {
        return args -> {
            if (properties.getEncryptedSecret() == null || properties.getEncryptedSecret().isBlank()) {
                throw new IllegalStateException("Set app.kms.encrypted-secret (or GCP_ENCRYPTED_SECRET, etc.) before running");
            }

            String plaintext = kmsClient.decryptValue(properties.toConfig(), properties.getEncryptedSecret());
            log.info("Decrypted secret from Google Cloud KMS: {}", plaintext);
            System.out.println("PLAINTEXT=" + plaintext);
        };
    }
}
