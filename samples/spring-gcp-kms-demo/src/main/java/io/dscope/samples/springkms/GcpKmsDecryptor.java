package io.dscope.samples.springkms;

import io.dscope.utils.crypto.CloudKmsClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes {@link CloudKmsClient} as a Spring bean so the sample can reuse the shared KMS logic.
 */
@Configuration
class GcpKmsClientConfiguration {

    @Bean
    CloudKmsClient cloudKmsClient() {
        return new CloudKmsClient();
    }
}
