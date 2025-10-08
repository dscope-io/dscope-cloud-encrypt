package io.dscope.utils.crypto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

@Command(
        name = "cloud-encrypt",
    description = "Encrypt, decrypt, and audit configuration files across AWS, Azure, GCP, and OCI.",
        mixinStandardHelpOptions = true,
        version = "1.0.0",
        subcommands = {
                CloudEncryptCLI.InitCommand.class,
                CloudEncryptCLI.StoreCommand.class
        }
)
public class CloudEncryptCLI implements Callable<Integer> {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static class Config {
        String provider;
        String defaultMode;
        List<String> include;
        List<String> exclude;
        boolean json = false;
        boolean autoDetect = true;
        Map<String, String> kms = new LinkedHashMap<>();
    }

    private static Config config = new Config();

    @Spec
    CommandSpec spec;

    @Parameters(paramLabel = "TARGET", arity = "0..*", description = "File, directory, or glob to process")
    private final List<String> targets = new ArrayList<>();

    @Option(names = "--dry-run", description = "Preview changes without writing files")
    boolean dryRun;

    @Option(names = "--decrypt", description = "Decrypt mode (default is encrypt unless overridden in config)")
    boolean decrypt;

    @Option(names = "--json", description = "Emit machine-readable JSON summary")
    boolean json;

    @Option(names = "--check", description = "Audit for plaintext secrets and return a non-zero exit code if any are found")
    boolean check;

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new CloudEncryptCLI());
        cmd.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
            commandLine.getErr().println("❌ " + ex.getMessage());
            return CommandLine.ExitCode.SOFTWARE;
        });
        System.exit(cmd.execute(args));
    }

    @Override
    public Integer call() throws Exception {
        loadConfig();

        boolean decryptMode = decrypt || "decrypt".equalsIgnoreCase(config.defaultMode);
        boolean checkMode = check || "check".equalsIgnoreCase(config.defaultMode);
        boolean jsonMode = json || config.json;

        Set<Path> targetSet = new LinkedHashSet<>();
        if (!targets.isEmpty()) {
            for (String targetValue : targets) {
                try {
                    targetSet.addAll(resolveFiles(Paths.get(targetValue), config.exclude));
                } catch (InvalidPathException ex) {
                    throw new CommandLine.ParameterException(spec.commandLine(),
                            "Invalid target '" + targetValue + "': " + ex.getMessage(), ex);
                } catch (IOException ex) {
                    throw new CommandLine.ParameterException(spec.commandLine(),
                            "Invalid target '" + targetValue + "': " + ex.getMessage(), ex);
                }
            }
        } else if (config.include != null) {
            for (String includePattern : config.include) {
                try {
                    targetSet.addAll(resolveFiles(Paths.get(includePattern), config.exclude));
                } catch (InvalidPathException ex) {
                    throw new CommandLine.ParameterException(spec.commandLine(),
                            "Invalid include path '" + includePattern + "' in .cloudencrypt.yml: " + ex.getMessage(), ex);
                } catch (IOException ex) {
                    throw new CommandLine.ParameterException(spec.commandLine(),
                            "Invalid include path '" + includePattern + "' in .cloudencrypt.yml: " + ex.getMessage(), ex);
                }
            }
        }

        if (targetSet.isEmpty()) {
            spec.commandLine().getOut().println("No target files matched. Provide a path or configure includes.");
            spec.commandLine().usage(spec.commandLine().getOut());
            return CommandLine.ExitCode.USAGE;
        }

        return processFiles(new ArrayList<>(targetSet), dryRun, decryptMode, jsonMode, checkMode);
    }

    @Command(name = "init", description = "Create a starter .cloudencrypt.yml config", mixinStandardHelpOptions = true)
    static class InitCommand implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            initConfig();
            return CommandLine.ExitCode.OK;
        }
    }

    @Command(name = "store", description = "Encrypt a secret via KMS and optionally append it to a file", mixinStandardHelpOptions = true)
    static class StoreCommand implements Callable<Integer> {

        @Spec
        CommandSpec spec;

    @Option(names = "--provider", paramLabel = "PROVIDER", description = "Override cloud provider (aws|azure|gcp|oci)")
        String provider;

        @Option(names = "--wrap", negatable = true, description = "Wrap ciphertext in ENC(...) (default: enabled)")
        Boolean wrap;

        @Option(names = "--stdin", description = "Read plaintext from STDIN")
        boolean stdin;

        @Option(names = "--output", paramLabel = "FILE", description = "Append ciphertext to the specified file")
        String output;

        @Option(names = "--name", paramLabel = "KEY", description = "Property key when writing key=value to --output")
        String propertyName;

        @Option(names = "--set", paramLabel = "KEY=VALUE", description = "Override provider setting (repeatable)")
        List<String> overridePairs = new ArrayList<>();

        @Parameters(index = "0", arity = "0..1", paramLabel = "SECRET", description = "Secret value or KEY=VALUE pair")
        String secretArgument;

        @Override
        public Integer call() throws Exception {
            loadConfig();

            StoreOptions options = new StoreOptions();
            options.wrap = wrap == null || wrap;
            options.readStdin = stdin;
            options.providerOverride = provider;
            options.outputPath = output;
            options.propertyName = propertyName;

            Map<String, String> overrides = new LinkedHashMap<>();
            for (String pair : overridePairs) {
                try {
                    addOverride(overrides, pair);
                } catch (IllegalArgumentException ex) {
                    throw new CommandLine.ParameterException(spec.commandLine(), ex.getMessage(), ex);
                }
            }
            options.overrides = overrides;

            if (secretArgument != null && options.propertyName == null && secretArgument.contains("=")) {
                String[] kv = secretArgument.split("=", 2);
                options.propertyName = kv[0];
                options.plaintext = kv[1];
            } else {
                options.plaintext = secretArgument;
            }

            try {
                executeStore(options);
            } catch (IllegalArgumentException ex) {
                throw new CommandLine.ParameterException(spec.commandLine(), ex.getMessage(), ex);
            }

            return CommandLine.ExitCode.OK;
        }
    }

    private static void initConfig() throws IOException {
        Path path = Paths.get(".cloudencrypt.yml");
        if (Files.exists(path)) {
            System.out.println("⚠️  .cloudencrypt.yml already exists. Skipping creation.");
            return;
        }

        String provider = Optional.ofNullable(detectProvider()).orElse("aws");

        Map<String, Object> yamlData = new LinkedHashMap<>();
        yamlData.put("provider", provider);
        yamlData.put("defaultMode", "encrypt");
        yamlData.put("include", List.of(
                "src/main/resources/**/*.properties",
                "src/main/resources/**/*.yml",
                "src/main/resources/**/*.env"
        ));
        yamlData.put("exclude", List.of(
                "target/**",
                "build/**",
                "node_modules/**",
                ".git/**"
        ));
        yamlData.put("json", false);
        yamlData.put("autoDetect", true);

        Map<String, Object> kms = new LinkedHashMap<>();
        switch (provider) {
            case "azure" -> kms.put("keyId", "https://<key-vault-name>.vault.azure.net/keys/<key-name>");
            case "gcp" -> {
                kms.put("project", "your-gcp-project");
                kms.put("location", "us-central1");
                kms.put("keyRing", "app-secrets");
                kms.put("key", "primary");
            }
            case "oci" -> {
                kms.put("configFile", System.getProperty("user.home") + "/.oci/config");
                kms.put("profile", "DEFAULT");
                kms.put("endpoint", "https://<vault>-crypto.kms.<region>.oraclecloud.com");
                kms.put("region", "us-ashburn-1");
                kms.put("vault", "<vault>");
                kms.put("keyId", "ocid1.key.oc1..<uniqueId>");
            }
            case "aws" -> {
                kms.put("region", "us-west-2");
                kms.put("keyId", "alias/your-key-alias");
            }
            default -> kms.put("keyId", "replace-with-your-key-id");
        }
        yamlData.put("kms", kms);

        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(opts);
        try (FileWriter writer = new FileWriter(path.toFile())) {
            yaml.dump(yamlData, writer);
        }

        System.out.println("✅ Created .cloudencrypt.yml with defaults.");
    }

    private static void loadConfig() {
        config = new Config();

        List<Path> locations = List.of(
                Paths.get(".cloudencrypt.yml"),
                Paths.get(System.getProperty("user.home"), ".cloudencrypt.yml")
        );

        for (Path location : locations) {
            if (!Files.exists(location)) {
                continue;
            }
            try (InputStream in = Files.newInputStream(location)) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(in);
                if (data == null) {
                    continue;
                }
                if (data.containsKey("provider")) {
                    config.provider = Objects.toString(data.get("provider"), null);
                }
                if (data.containsKey("defaultMode")) {
                    config.defaultMode = Objects.toString(data.get("defaultMode"), null);
                }
                if (data.containsKey("include")) {
                    config.include = new ArrayList<>();
                    for (Object value : (List<?>) data.get("include")) {
                        if (value != null) {
                            config.include.add(value.toString());
                        }
                    }
                }
                if (data.containsKey("exclude")) {
                    config.exclude = new ArrayList<>();
                    for (Object value : (List<?>) data.get("exclude")) {
                        if (value != null) {
                            config.exclude.add(value.toString());
                        }
                    }
                }
                if (data.containsKey("json")) {
                    config.json = Boolean.TRUE.equals(data.get("json"));
                }
                if (data.containsKey("autoDetect")) {
                    config.autoDetect = Boolean.TRUE.equals(data.get("autoDetect"));
                }
                config.kms.clear();
                if (data.containsKey("kms")) {
                    Object kmsObj = data.get("kms");
                    if (kmsObj instanceof Map<?, ?> map) {
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            if (entry.getKey() != null && entry.getValue() != null) {
                                config.kms.put(entry.getKey().toString(), Objects.toString(entry.getValue(), ""));
                            }
                        }
                    }
                }
                System.out.println("⚙️  Loaded config from " + location);
                break;
            } catch (Exception e) {
                System.out.println("⚠️  Failed to load config: " + e.getMessage());
            }
        }
    }

    private static List<Path> resolveFiles(Path target, List<String> excludes) throws IOException {
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(target)) {
            Files.walkFileTree(target, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String normalized = file.toString().replace("\\", "/");
                    boolean excluded = excludes != null && excludes.stream().anyMatch(normalized::matches);
                    if (!excluded && normalized.matches(".*\\.(properties|env|yml|yaml)$")) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } else if (target.toString().contains("*")) {
            Path base = target.getParent() != null ? target.getParent() : Paths.get(".");
            String pattern = target.getFileName().toString();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(base, pattern)) {
                for (Path p : stream) {
                    String normalized = p.toString().replace("\\", "/");
                    boolean excluded = excludes != null && excludes.stream().anyMatch(normalized::matches);
                    if (!excluded) {
                        files.add(p);
                    }
                }
            }
        } else if (Files.exists(target)) {
            String normalized = target.toString().replace("\\", "/");
            boolean excluded = excludes != null && excludes.stream().anyMatch(normalized::matches);
            if (!excluded) {
                files.add(target);
            }
        }
        return files;
    }

    private static int processFiles(List<Path> files, boolean dryRun, boolean decryptMode,
                                    boolean jsonMode, boolean checkMode) throws Exception {
        String provider = config.provider;
        if ((provider == null || provider.isEmpty()) && config.autoDetect) {
            provider = Optional.ofNullable(detectProvider()).orElse("unknown");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        List<String> insecureFiles = new ArrayList<>();

        for (Path file : files) {
            Map<String, Object> res = CloudEncryptCore.processFile(file, provider, config.kms, dryRun, decryptMode, jsonMode, checkMode);
            results.add(res);
            if (res.containsKey("unencrypted") && ((Number) res.get("unencrypted")).intValue() > 0) {
                insecureFiles.add(file.toString());
            }
        }

        if (jsonMode) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("provider", provider);
            summary.put("fileCount", files.size());
            summary.put("results", results);
            summary.put("mode", checkMode ? "check" : (decryptMode ? "decrypt" : "encrypt"));
            summary.put("insecureFiles", insecureFiles);
            System.out.println(gson.toJson(summary));
            if (checkMode && !insecureFiles.isEmpty()) {
                return CommandLine.ExitCode.SOFTWARE;
            }
            return CommandLine.ExitCode.OK;
        }

        System.out.println("☁️  Provider: " + provider);
        System.out.println("📦 Processed " + files.size() + " file(s)");
        if (checkMode && !insecureFiles.isEmpty()) {
            System.out.println("❌ Found unencrypted secrets in:");
            for (String f : insecureFiles) {
                System.out.println("  - " + f);
            }
            return CommandLine.ExitCode.SOFTWARE;
        } else if (checkMode) {
            System.out.println("✅ All secrets are encrypted.");
        }
        return CommandLine.ExitCode.OK;
    }

    private static class StoreOptions {
        boolean wrap = true;
        boolean readStdin;
        String providerOverride;
        String outputPath;
        String propertyName;
        Map<String, String> overrides = new LinkedHashMap<>();
        String plaintext;
    }

    private static void executeStore(StoreOptions options) throws Exception {
        Objects.requireNonNull(options, "options");

        if (options.readStdin) {
            String fromStdin = new String(System.in.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!fromStdin.isEmpty()) {
                options.plaintext = fromStdin;
            }
        }

        if (options.plaintext == null || options.plaintext.isBlank()) {
            throw new IllegalArgumentException("Provide a secret value to store (argument or --stdin).");
        }

        String provider = resolveProvider(options.providerOverride);
        CloudKmsConfig kmsConfig = buildKmsConfig(provider, config.kms, options.overrides);

        CloudKmsClient client = new CloudKmsClient();
        String ciphertext = client.encryptValue(kmsConfig, options.plaintext, options.wrap);

        System.out.println(ciphertext);

        if (options.outputPath != null) {
            Path path = Paths.get(options.outputPath);
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            if (options.propertyName != null) {
                List<String> lines = Files.exists(path)
                        ? Files.readAllLines(path)
                        : new ArrayList<>();
                List<String> updated = upsertProperty(lines, options.propertyName, ciphertext);
                Files.write(path, updated, StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                System.out.println("💾 Stored " + options.propertyName + " in " + options.outputPath);
            } else {
                Files.writeString(path, ciphertext + System.lineSeparator(), StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                System.out.println("💾 Appended ciphertext to " + options.outputPath);
            }
        }
    }

    private static String resolveProvider(String override) {
        if (override != null && !override.isBlank()) {
            return override.trim().toLowerCase(Locale.ROOT);
        }
        if (config.provider != null && !config.provider.isBlank()) {
            return config.provider.trim().toLowerCase(Locale.ROOT);
        }
        if (config.autoDetect) {
            String detected = detectProvider();
            if (detected != null) {
                return detected.toLowerCase(Locale.ROOT);
            }
        }
        throw new IllegalStateException("Unable to determine provider. Set provider in .cloudencrypt.yml or pass --provider.");
    }

    static CloudKmsConfig buildKmsConfig(String provider, Map<String, String> base, Map<String, String> overrides) {
        Objects.requireNonNull(provider, "provider");
        Map<String, String> merged = new LinkedHashMap<>();
        if (base != null) {
            merged.putAll(base);
        }
        if (overrides != null) {
            merged.putAll(overrides);
        }
        CloudKmsConfig.Builder builder = CloudKmsConfig.builder(provider);
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            builder.with(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    static List<String> upsertProperty(List<String> lines, String key, String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        List<String> updated = new ArrayList<>();
        boolean replaced = false;
        for (String line : lines) {
            if (!replaced && line.trim().startsWith(key + "=")) {
                updated.add(key + "=" + value);
                replaced = true;
            } else {
                updated.add(line);
            }
        }
        if (!replaced) {
            updated.add(key + "=" + value);
        }
        return updated;
    }

    private static void addOverride(Map<String, String> overrides, String keyValue) {
        int idx = keyValue.indexOf('=');
        if (idx <= 0) {
            throw new IllegalArgumentException("--set expects key=value but was: " + keyValue);
        }
        String key = keyValue.substring(0, idx).trim();
        String value = keyValue.substring(idx + 1).trim();
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Override key cannot be empty");
        }
        overrides.put(key, value);
    }

    private static String detectProvider() {
        try {
            if (new ProcessBuilder("aws", "sts", "get-caller-identity").start().waitFor() == 0) {
                return "aws";
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            if (new ProcessBuilder("az", "account", "show").start().waitFor() == 0) {
                return "azure";
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            Process gcp = new ProcessBuilder("gcloud", "auth", "list", "--filter=status:ACTIVE",
                    "--format=value(account)").start();
            gcp.waitFor();
            if (new String(gcp.getInputStream().readAllBytes()).trim().length() > 0) {
                return "gcp";
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            Process oci = new ProcessBuilder("oci", "session", "validate").start();
            if (oci.waitFor() == 0) {
                return "oci";
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return null;
    }
}
