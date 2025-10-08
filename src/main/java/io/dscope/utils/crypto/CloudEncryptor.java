package io.dscope.utils.crypto;

public interface CloudEncryptor {
    String encrypt(String plainText) throws Exception;
}
