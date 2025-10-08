package io.dscope.utils.crypto;

public interface CloudDecryptor {
    String decrypt(String cipherBase64) throws Exception;
}
