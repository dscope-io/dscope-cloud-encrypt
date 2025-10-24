To run a Java program that uses the Google Cloud Key Management Service (Cloud KMS), you primarily need to complete three steps: **setup (API & Credentials), writing the code (using the SDK), and handling deployment/authentication** where the code runs.

---

## **1\. Project Setup and Credentials 🔑**

Before writing any Java code, you must configure your Google Cloud project and obtain the correct libraries.

### **A. Google Cloud Configuration**

1. **Enable the API:** In your Google Cloud project, go to **APIs & Services** $\\rightarrow$ **Library** and enable the **Cloud Key Management Service API**.  
2. **Create a Key:** Navigate to **Security** $\\rightarrow$ **Key Management** and create a **Key Ring** and a **Key** (e.g., an encryption key). Note the key's **Resource ID/Path** (e.g., projects/p-id/locations/global/keyRings/kr-id/cryptoKeys/k-id).  
3. **Grant Permissions:** The identity running your Java code (usually a Service Account) must be granted the correct **IAM role** (e.g., **Cloud KMS CryptoKey Encrypter/Decrypter**).

### **B. Java Dependencies**

Use the Google Cloud Client Libraries for Java. Add the following dependency to your pom.xml (Maven) or build.gradle (Gradle):

| Tool | Dependency Snippet |
| :---- | :---- |
| **Maven** | xml\<dependency\>\<groupId\>com.google.cloud\</groupId\>\<artifactId\>google-cloud-kms\</artifactId\>\<version\>2.42.0\</version\>\</dependency\> |
| **Gradle** | groovyimplementation 'com.google.cloud:google-cloud-kms:2.42.0' |

---

## **2\. Writing the Java Code 📝**

You will use the KeyManagementServiceClient from the SDK to interact with Cloud KMS.

Java

import com.google.cloud.kms.v1.CryptoKeyName;  
import com.google.cloud.kms.v1.EncryptResponse;  
import com.google.cloud.kms.v1.KeyManagementServiceClient;  
import com.google.protobuf.ByteString;  
import java.io.IOException;

public class KmsEncryptor {  
      
    public void encryptData(String projectId, String locationId, String keyRingId, String keyId, String plaintext) throws IOException {  
          
        // 1\. Define the Key Name using its full resource path  
        CryptoKeyName keyName \= CryptoKeyName.newBuilder()  
            .setProject(projectId)  
            .setLocation(locationId)  
            .setKeyRing(keyRingId)  
            .setCryptoKey(keyId)  
            .build();

        // 2\. Convert plaintext to ByteString  
        ByteString plaintextBytes \= ByteString.copyFromUtf8(plaintext);

        // 3\. Initialize the KMS Client (Uses Application Default Credentials for auth)  
        try (KeyManagementServiceClient client \= KeyManagementServiceClient.create()) {  
              
            // 4\. Execute the encryption request  
            EncryptResponse response \= client.encrypt(keyName, plaintextBytes);

            // 5\. Output the ciphertext  
            String ciphertext \= response.getCiphertext().toStringUtf8();  
            System.out.println("Encrypted data (Ciphertext): " \+ ciphertext);  
        }  
    }

    public static void main(String\[\] args) throws IOException {  
        // Replace with your actual key details  
        String PROJECT\_ID \= "my-gcp-project";  
        String LOCATION\_ID \= "global";  
        String KEY\_RING\_ID \= "my-key-ring";  
        String KEY\_ID \= "my-crypto-key";  
        String DATA\_TO\_ENCRYPT \= "This is my secret\!";

        new KmsEncryptor().encryptData(PROJECT\_ID, LOCATION\_ID, KEY\_RING\_ID, KEY\_ID, DATA\_TO\_ENCRYPT);  
    }  
}

---

## **3\. Running and Authenticating the Program 🏃‍♂️**

Google's client libraries use a mechanism called **Application Default Credentials (ADC)** to find authentication credentials automatically, depending on where your code is running.

| Environment | Authentication Method | How to Run |
| :---- | :---- | :---- |
| **Local Development** | **User Account:** Authenticate your local user via Google Cloud CLI (gcloud). | Open a terminal and run the command: gcloud auth application-default login |
| **GCP Services** | **Service Account:** The environment automatically uses the Service Account assigned to the resource (e.g., Compute Engine VM, Cloud Run service, or Google Kubernetes Engine pod). | **Attach the correct Service Account** to your deployment resource during setup. No need to manage key files manually. |
| **External Servers** | **Service Account Key File:** Use the JSON key file associated with a Service Account. | Set the GOOGLE\_APPLICATION\_CREDENTIALS environment variable to the **path of the JSON key file** before running your Java program. |

Once authentication is configured, simply compile and run your Java program:

Bash

\# Example: Running locally (after gcloud auth)  
\# Assuming you use Maven:  
mvn clean compile exec:java  
