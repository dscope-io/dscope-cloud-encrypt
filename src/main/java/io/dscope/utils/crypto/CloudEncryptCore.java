package io.dscope.utils.crypto;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

public class CloudEncryptCore {

    public static class Result {
        public List<String> outputLines;
        public int changedCount;
        public int unencryptedCount;
        public List<String> affectedKeys;
    }

    public static Map<String, Object> processFile(Path path, String provider,
                                                  Map<String, String> kmsConfig,
                                                  boolean dryRun, boolean decryptMode,
                                                  boolean jsonMode, boolean checkMode) throws Exception {
        List<String> lines = Files.readAllLines(path);

        Map<String, String> cfg = kmsConfig != null ? new HashMap<>(kmsConfig) : new HashMap<>();
        Function<String,String> enc = null;
        Function<String,String> dec = null;

        if (!checkMode) {
            if (decryptMode) {
                CloudDecryptor d = CloudDecryptFactory.create(provider, cfg);
                dec = s -> {
                    try { return d.decrypt(stripEnc(s)); } catch (Exception e) { throw new RuntimeException(e); }
                };
            } else {
                CloudEncryptor e = CloudEncryptFactory.create(provider, cfg);
                enc = s -> {
                    try { return e.encrypt(s); } catch (Exception ex) { throw new RuntimeException(ex); }
                };
            }
        }

        Result r = processLines(lines, dryRun, decryptMode, checkMode, enc, dec);

        if (!checkMode && !dryRun && r.changedCount > 0) {
            Files.write(path, r.outputLines);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("file", path.toString());
        res.put("provider", provider);
        res.put("mode", checkMode ? "check" : (decryptMode ? "decrypt" : "encrypt"));
        res.put("dryRun", dryRun);
        res.put("changed", r.changedCount);
        res.put("unencrypted", r.unencryptedCount);
        res.put("keys", r.affectedKeys);
        return res;
    }

    /** Pure transformation logic used by tests. */
    public static Result processLines(List<String> lines, boolean dryRun, boolean decryptMode,
                                      boolean checkMode, Function<String,String> encryptFn,
                                      Function<String,String> decryptFn) {
        List<String> out = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        int changed = 0;
        int unenc = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                out.add(line);
                continue;
            }
            String[] parts = line.split("=", 2);
            if (parts.length != 2) {
                out.add(line);
                continue;
            }
            String key = parts[0].trim();
            String value = parts[1].trim();
            boolean sensitive = key.toLowerCase().matches(".*(password|secret|token|key).*");

            if (checkMode && sensitive && !value.startsWith("ENC(")) {
                unenc++;
                keys.add(key);
                out.add(line);
                continue;
            }

            if (decryptMode && value.startsWith("ENC(")) {
                changed++;
                keys.add(key);
                if (dryRun || decryptFn == null) out.add(line);
                else out.add(key + "=" + decryptFn.apply(value));
            } else if (!decryptMode && sensitive && !value.startsWith("ENC(")) {
                changed++;
                keys.add(key);
                if (dryRun || encryptFn == null) out.add(line);
                else out.add(key + "=ENC(" + encryptFn.apply(value) + ")");
            } else {
                out.add(line);
            }
        }

        Result r = new Result();
        r.outputLines = out;
        r.changedCount = changed;
        r.unencryptedCount = unenc;
        r.affectedKeys = keys;
        return r;
    }

    private static String stripEnc(String v) {
        if (v.startsWith("ENC(") && v.endsWith(")")) return v.substring(4, v.length() - 1);
        return v;
    }
}
