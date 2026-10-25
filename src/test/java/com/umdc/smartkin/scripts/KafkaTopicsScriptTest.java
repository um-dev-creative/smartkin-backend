package com.umdc.smartkin.scripts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// `scripts/kafka-create-topics.sh` runs the Kafka CLI in a container, so the files a client.properties names must be paths the container
/// sees. A fake `docker` records the mounts and a copy of the properties file the script hands to it, which shows what Kafka would read,
/// without Docker. The operator who wrote the folder as it is on their machine (`/Users/x/kafka-aiven/ca.pem`) got `Unable to read file`.
class KafkaTopicsScriptTest {

    @TempDir
    Path temp;

    private Path bin;
    private Path log;
    private Path seen;

    @BeforeEach
    void fakeDocker() throws IOException {
        bin = Files.createDirectory(temp.resolve("bin"));
        log = temp.resolve("docker.log");
        seen = temp.resolve("seen.properties");
        Path docker = bin.resolve("docker");
        Files.writeString(docker, """
                #!/usr/bin/env bash
                echo "$*" >> "$FAKE_DOCKER_LOG"
                prev=""
                for arg in "$@"; do
                  if [ "$prev" = "-v" ]; then
                    case "$arg" in
                      *:/etc/kafka-client-conf:ro)
                        host="${arg%%:/etc/kafka-client-conf:ro}"
                        echo "$host" > "$FAKE_DOCKER_LOG.staged"
                        cp "$host/client.properties" "$FAKE_DOCKER_SEEN"
                        stat -f '%Lp' "$host/client.properties" > "$FAKE_DOCKER_SEEN.mode" 2>/dev/null || stat -c '%a' "$host/client.properties" > "$FAKE_DOCKER_SEEN.mode"
                        ;;
                    esac
                  fi
                  prev="$arg"
                done
                case "$*" in
                  *--describe*) printf 'Topic: x\\tTopicId: y\\tPartitionCount: 3\\tReplicationFactor: 3\\n' ;;
                esac
                """);
        Files.setPosixFilePermissions(docker, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    private Path propertiesIn(String folderName, String body) throws IOException {
        Path folder = Files.createDirectories(temp.resolve(folderName));
        Files.writeString(folder.resolve("ca.pem"), "ca");
        Files.writeString(folder.resolve("client-keystore.pem"), "key+cert");
        Path file = folder.resolve("client.properties");
        Files.writeString(file, body.replace("{DIR}", folder.toAbsolutePath().toString()));
        return file;
    }

    private int run(Path properties, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("bash", "scripts/kafka-create-topics.sh"));
        command.addAll(List.of(args));
        var builder = new ProcessBuilder(command).redirectErrorStream(true);
        Map<String, String> env = builder.environment();
        env.put("PATH", bin + ":" + env.get("PATH"));
        env.put("KAFKA_BOOTSTRAP_SERVERS", "broker.example.com:13828");
        env.put("FAKE_DOCKER_LOG", log.toString());
        env.put("FAKE_DOCKER_SEEN", seen.toString());
        if (properties != null) {
            env.put("KAFKA_CLIENT_PROPERTIES", properties.toString());
        }
        Process process = builder.start();
        process.getInputStream().readAllBytes();
        return process.waitFor();
    }

    @Test
    void aFolderWrittenAsOnTheOperatorsMachineIsRewrittenForTheContainer() throws Exception {
        Path properties = propertiesIn("kafka-aiven", """
                security.protocol=SSL
                ssl.truststore.type=PEM
                ssl.truststore.location={DIR}/ca.pem
                ssl.keystore.type=PEM
                ssl.keystore.location={DIR}/client-keystore.pem
                """);
        String original = Files.readString(properties);

        assertEquals(0, run(properties));

        String handedToKafka = Files.readString(seen);
        assertTrue(handedToKafka.contains("ssl.truststore.location=/etc/kafka-client/ca.pem"), handedToKafka);
        assertTrue(handedToKafka.contains("ssl.keystore.location=/etc/kafka-client/client-keystore.pem"), handedToKafka);
        assertFalse(handedToKafka.contains(temp.toString()), handedToKafka);
        assertTrue(handedToKafka.contains("security.protocol=SSL"));
        assertEquals(original, Files.readString(properties), "the operator's file is never modified");
        assertTrue(Files.readString(log).contains(properties.getParent() + ":/etc/kafka-client:ro"), "the folder is mounted read-only");
        assertEquals("600", Files.readString(Path.of(seen + ".mode")).trim());
    }

    @Test
    void pathsAlreadyWrittenForTheContainerAreLeftAlone() throws Exception {
        Path properties = propertiesIn("kafka-aiven", """
                security.protocol=SSL
                ssl.truststore.location=/etc/kafka-client/ca.pem
                """);

        assertEquals(0, run(properties));

        assertTrue(Files.readString(seen).contains("ssl.truststore.location=/etc/kafka-client/ca.pem"));
    }

    @Test
    void aFolderNameWithSpecialCharactersIsRewrittenToo() throws Exception {
        Path properties = propertiesIn("my.kafka+aiven$x", """
                ssl.truststore.location={DIR}/ca.pem
                sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required username="u" password="p|w&d";
                """);

        assertEquals(0, run(properties));

        String handedToKafka = Files.readString(seen);
        assertTrue(handedToKafka.contains("ssl.truststore.location=/etc/kafka-client/ca.pem"), handedToKafka);
        assertTrue(handedToKafka.contains("password=\"p|w&d\""), "secrets in the file are kept as they are: " + handedToKafka);
    }

    @Test
    void theCopyWithCredentialsIsRemovedWhenTheScriptEnds() throws Exception {
        Path properties = propertiesIn("kafka-aiven", "security.protocol=SSL\nssl.truststore.location={DIR}/ca.pem\n");

        assertEquals(0, run(properties));

        Path staged = Path.of(Files.readString(Path.of(log + ".staged")).trim());
        assertFalse(Files.exists(staged), "temporary copy left behind: " + staged);
    }

    @Test
    void withoutAPropertiesFileNothingIsMounted() throws Exception {
        assertEquals(0, run(null));

        assertFalse(Files.readString(log).contains("/etc/kafka-client"));
    }

    @Test
    void aMissingPropertiesFileStopsTheScript() throws Exception {
        assertEquals(2, run(temp.resolve("nope/client.properties")));
    }

    @Test
    void theScriptTellsYouToUseBashWhenAnotherShellRunsIt() throws Exception {
        var process = new ProcessBuilder("sh", "-c", "[ -n \"$BASH_VERSION\" ] && exit 99; sh scripts/kafka-create-topics.sh --dry-run")
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        int exit = process.waitFor();
        // on systems where `sh` is bash the guard cannot fire; where it is dash (CI) it must
        if (exit != 99) {
            assertNotEquals(0, exit, output);
            assertTrue(output.contains("Run it with bash"), output);
        }
    }
}
