package org.a2aproject.sdk.extras.queuemanager.replicated.tests.multiinstance;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfig;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.images.builder.Transferable;

/**
 * Real topology for the Phase 3 authority tests: one PostgreSQL 16 container, one SASL/ACL Kafka broker, two Quarkus
 * application containers built from the existing {@code quarkus-app-1/2} modules, and an in-JVM webhook receiver
 * that the nodes reach through the Testcontainers host port exposure. Nothing in here is mocked.
 */
final class Phase3Cluster {

    static final String KAFKA_IMAGE = "mirror.gcr.io/apache/kafka:4.2.0";
    static final String POSTGRES_IMAGE = "postgres:16-alpine";
    static final String TOPIC = "replicated-events";
    static final String NEW_FLEET_USER = "newfleet";
    static final String NEW_FLEET_SECRET = "new-secret";
    static final String OLD_FLEET_USER = "oldfleet";
    static final String OLD_FLEET_SECRET = "old-secret";
    static final String ADMIN_SECRET = "admin-secret";

    final Network network = Network.newNetwork();
    GenericContainer<?> kafka;
    PostgreSQLContainer<?> postgres;
    Node app1;
    Node app2;
    Receiver receiver;
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    static String plainJaas(String user, String secret) {
        return "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"" + user
                + "\" password=\"" + secret + "\";";
    }

    void start() throws Exception {
        receiver = new Receiver();
        Testcontainers.exposeHostPorts(receiver.port());

        kafka = new GenericContainer<>(DockerImageName.parse(KAFKA_IMAGE))
                .withNetwork(network).withNetworkAliases("kafka")
                .withEnv("CLUSTER_ID", "MkU3OEVBNTcwNTJENDM2Qk")
                .withEnv("KAFKA_NODE_ID", "1")
                .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
                .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9093")
                .withEnv("KAFKA_LISTENERS", "SASL://:19092,CONTROLLER://:9093")
                .withEnv("KAFKA_ADVERTISED_LISTENERS", "SASL://kafka:19092")
                .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "SASL:SASL_PLAINTEXT,CONTROLLER:PLAINTEXT")
                .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "SASL")
                .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
                .withEnv("KAFKA_SASL_ENABLED_MECHANISMS", "PLAIN")
                .withEnv("KAFKA_SASL_MECHANISM_INTER_BROKER_PROTOCOL", "PLAIN")
                .withEnv("KAFKA_LISTENER_NAME_SASL_PLAIN_SASL_JAAS_CONFIG",
                        plainJaas("admin", ADMIN_SECRET).replace(";", " user_admin=\"" + ADMIN_SECRET + "\" user_"
                                + NEW_FLEET_USER + "=\"" + NEW_FLEET_SECRET + "\" user_" + OLD_FLEET_USER + "=\""
                                + OLD_FLEET_SECRET + "\";"))
                .withEnv("KAFKA_AUTHORIZER_CLASS_NAME", "org.apache.kafka.metadata.authorizer.StandardAuthorizer")
                .withEnv("KAFKA_SUPER_USERS", "User:admin;User:ANONYMOUS")
                .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
                .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
                .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1).withStartupTimeout(Duration.ofMinutes(2)));
        kafka.start();
        kafka.copyFileToContainer(Transferable.of("security.protocol=SASL_PLAINTEXT\nsasl.mechanism=PLAIN\n"
                + "sasl.jaas.config=" + plainJaas("admin", ADMIN_SECRET) + "\n"), "/tmp/admin.properties");
        kafka.copyFileToContainer(Transferable.of("security.protocol=SASL_PLAINTEXT\nsasl.mechanism=PLAIN\n"
                + "sasl.jaas.config=" + plainJaas(OLD_FLEET_USER, OLD_FLEET_SECRET) + "\nenable.idempotence=false\n"), "/tmp/old.properties");
        kafka.copyFileToContainer(Transferable.of("security.protocol=SASL_PLAINTEXT\nsasl.mechanism=PLAIN\n"
                + "sasl.jaas.config=" + plainJaas(NEW_FLEET_USER, NEW_FLEET_SECRET) + "\nenable.idempotence=false\n"), "/tmp/new.properties");
        kafkaCli("kafka-topics.sh", "--bootstrap-server", "kafka:19092", "--command-config", "/tmp/admin.properties",
                "--create", "--topic", TOPIC, "--partitions", "1", "--replication-factor", "1");
        // New fleet: read/write/describe the replication topic and use any consumer group.
        kafkaCli("kafka-acls.sh", "--bootstrap-server", "kafka:19092", "--command-config", "/tmp/admin.properties",
                "--add", "--allow-principal", "User:" + NEW_FLEET_USER, "--operation", "Read", "--operation", "Write",
                "--operation", "Describe", "--topic", TOPIC);
        kafkaCli("kafka-acls.sh", "--bootstrap-server", "kafka:19092", "--command-config", "/tmp/admin.properties",
                "--add", "--allow-principal", "User:" + NEW_FLEET_USER, "--operation", "Read", "--group", "*");
        // Old fleet: still permitted until the operator fences it (the test does that explicitly).
        kafkaCli("kafka-acls.sh", "--bootstrap-server", "kafka:19092", "--command-config", "/tmp/admin.properties",
                "--add", "--allow-principal", "User:" + OLD_FLEET_USER, "--operation", "Write", "--operation",
                "Describe", "--topic", TOPIC);

        postgres = new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE))
                .withNetwork(network).withNetworkAliases("postgres")
                .withDatabaseName("a2adb").withUsername("a2auser").withPassword("a2apass");
        postgres.start();

        app1 = new Node("app1", 8081, "../quarkus-app-1");
        app2 = new Node("app2", 8082, "../quarkus-app-2");
        Map<String, String> env = authorityEnv(1);
        app1.start(env);
        app2.start(env);
    }

    Map<String, String> authorityEnv(long epoch) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("A2A_WRITER_EPOCH", Long.toString(epoch));
        env.put("A2A_BOOTSTRAP_EPOCH", "true");
        env.put("A2A_SCHEMA_GENERATION", "update");
        env.put("KAFKA_SESSION_TIMEOUT_MS", "6000");
        env.put("KAFKA_HEARTBEAT_MS", "2000");
        env.put("KAFKA_SECURITY_PROTOCOL", "SASL_PLAINTEXT");
        env.put("KAFKA_SASL_MECHANISM", "PLAIN");
        env.put("KAFKA_SASL_JAAS_CONFIG", plainJaas(NEW_FLEET_USER, NEW_FLEET_SECRET));
        return env;
    }

    void stop() {
        for (Node node : new Node[] {app2, app1}) {
            if (node != null) {
                node.stopQuietly();
            }
        }
        if (postgres != null) {
            postgres.stop();
        }
        if (kafka != null) {
            kafka.stop();
        }
        if (receiver != null) {
            receiver.close();
        }
        network.close();
    }

    /** execInContainer with a bounded retry: the container runtime occasionally drops the exec attach stream. */
    private org.testcontainers.containers.Container.ExecResult exec(String... command) throws IOException,
            InterruptedException {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return kafka.execInContainer(command);
            } catch (RuntimeException e) {
                last = e;
                Thread.sleep(1000);
            }
        }
        throw last;
    }

    /** Runs a Kafka CLI tool inside the broker container and returns stdout+stderr; never throws on non-zero exit. */
    String kafkaCli(String tool, String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("/opt/kafka/bin/" + tool);
        Collections.addAll(cmd, args);
        var result = exec(cmd.toArray(String[]::new));
        return result.getStdout() + result.getStderr() + "[exit=" + result.getExitCode() + "]";
    }

    /** The consumer-group member currently holding the replication partition for the given group, or null. */
    String consumerId(String group) throws IOException, InterruptedException {
        String out = kafkaCli("kafka-consumer-groups.sh", "--bootstrap-server", "kafka:19092", "--command-config",
                "/tmp/admin.properties", "--describe", "--group", group);
        for (String line : out.split("\n")) {
            String[] tokens = line.trim().split("\\s+");
            if (tokens.length >= 7 && tokens[1].equals(TOPIC)) {
                return tokens[6].equals("-") ? null : tokens[6];
            }
        }
        return null;
    }

    /** Produces already-serialized single-line records through the broker CLI with the given client properties file. */
    String produceAs(String propertiesFile, List<String> lines) throws IOException, InterruptedException {
        String name = "/tmp/produce-" + System.nanoTime() + ".txt";
        kafka.copyFileToContainer(Transferable.of(String.join("\n", lines) + "\n"), name);
        var result = exec("sh", "-c", "/opt/kafka/bin/kafka-console-producer.sh --bootstrap-server "
                + "kafka:19092 --topic " + TOPIC + " --producer.config " + propertiesFile + " < " + name);
        return result.getStdout() + result.getStderr() + "[exit=" + result.getExitCode() + "]";
    }

    /** Reads every record currently on the topic as the admin principal (one JSON document per line). */
    List<String> readTopic() throws IOException, InterruptedException {
        var result = exec("/opt/kafka/bin/kafka-console-consumer.sh", "--bootstrap-server",
                "kafka:19092", "--topic", TOPIC, "--from-beginning", "--timeout-ms", "4000", "--consumer.config",
                "/tmp/admin.properties");
        List<String> lines = new ArrayList<>();
        for (String line : result.getStdout().split("\n")) {
            if (line.startsWith("{")) {
                lines.add(line);
            }
        }
        return lines;
    }

    // ---- SQL evidence ----

    <T> T query(String sql, SqlFunction<ResultSet, T> mapper, Object... params) throws SQLException {
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword()); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return mapper.apply(rs);
            }
        }
    }

    interface SqlFunction<A, R> {
        R apply(A value) throws SQLException;
    }

    /** One line per row of the task's webhook lane: epoch, cursor, holder token and claimed range. */
    String laneRow(String taskId) throws SQLException {
        return query("SELECT * FROM a2a_webhook_dispatch_lanes WHERE task_id = ?", rs -> {
            if (!rs.next()) {
                return "<none>";
            }
            StringBuilder out = new StringBuilder();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                out.append(rs.getMetaData().getColumnName(i)).append('=').append(rs.getObject(i)).append(' ');
            }
            return out.toString().trim();
        }, taskId);
    }

    Long storedEpoch() throws SQLException {
        return query("SELECT epoch FROM a2a_writer_epoch", rs -> rs.next() ? rs.getLong(1) : null);
    }

    String postgresVersion() throws SQLException {
        return query("SELECT version()", rs -> {
            rs.next();
            return rs.getString(1);
        });
    }

    // ---- nodes ----

    final class Node {
        final String name;
        final int port;
        final String dir;
        final ImageFromDockerfile image;
        GenericContainer<?> container;
        String url = "";
        AgentCard card;

        Node(String name, int port, String dir) {
            this.name = name;
            this.port = port;
            this.dir = dir;
            this.image = new ImageFromDockerfile()
                    .withFileFromPath(".", new java.io.File(dir).getAbsoluteFile().toPath());
        }

        void start(Map<String, String> env) throws IOException, InterruptedException {
            String previousConsumer = consumerId(group());
            container = new GenericContainer<>(image)
                    .withNetwork(network).withNetworkAliases(name)
                    .withEnv("KAFKA_BOOTSTRAP_SERVERS", "kafka:19092")
                    .withEnv("DATABASE_URL", "jdbc:postgresql://postgres:5432/a2adb")
                    .withEnv("DATABASE_USER", "a2auser")
                    .withEnv("DATABASE_PASSWORD", "a2apass")
                    .withExposedPorts(port)
                    .waitingFor(Wait.forHttp("/q/health/ready").forPort(port)
                            .withStartupTimeout(Duration.ofMinutes(3)));
            env.forEach(container::withEnv);
            container.start();
            // A restarted node rejoins its consumer group only after the dead member's session expires; records are
            // not consumed before the partition is assigned to the NEW member, so wait for it instead of racing.
            long assignDeadline = System.nanoTime() + Duration.ofSeconds(150).toNanos();
            String assigned;
            while ((assigned = consumerId(group())) == null || assigned.equals(previousConsumer)) {
                if (System.nanoTime() > assignDeadline) {
                    throw new IllegalStateException("node " + name + " never got its replication partition assigned");
                }
                Thread.sleep(1000);
            }
            System.out.println("PHASE3-EVIDENCE node " + name + " consumer " + assigned + " assigned in group "
                    + group());
            url = "http://localhost:" + container.getMappedPort(port);
            AgentCard original = A2A.getAgentCard(url);
            card = AgentCard.builder(original)
                    .supportedInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), url, null,
                            "1.1")))
                    .build();
        }

        /** SIGKILL of the node's container (a real process kill: nothing runs in the node afterwards). */
        void kill() throws InterruptedException {
            for (int attempt = 0; attempt < 3 && container.isRunning(); attempt++) {
                try {
                    container.getDockerClient().killContainerCmd(container.getContainerId()).exec();
                } catch (RuntimeException e) {
                    Thread.sleep(1000); // the runtime may drop the connection; the loop re-checks the container state
                }
            }
        }

        /** Starts a fresh process of this node (new container, same image, same database and broker). */
        String group() {
            return "app-" + name.substring(3) + "-consumer-group";
        }

        void restart(Map<String, String> env) throws IOException, InterruptedException {
            stopQuietly();
            start(env);
        }

        void stopQuietly() {
            try {
                if (container != null) {
                    container.stop();
                }
            } catch (RuntimeException ignored) {
                // best effort teardown
            }
        }

        String logs() {
            return container.getLogs();
        }

        Client client() throws A2AClientException {
            return Client.builder(card)
                    .clientConfig(ClientConfig.builder().setProtocolVersion("1.1").setStreaming(false).build())
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                    .build();
        }

        Client streamingClient() throws A2AClientException {
            return Client.builder(card)
                    .clientConfig(ClientConfig.builder().setProtocolVersion("1.1").setStreaming(true).build())
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                    .build();
        }

        String admin(String method, String path) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url + path))
                    .method(method, HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(30)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException(method + " " + path + " -> " + response.statusCode() + " " + response.body());
            }
            return response.body();
        }

        JsonObject nodeInfo() throws IOException, InterruptedException {
            return JsonParser.parseString(admin("GET", "/phase3/node")).getAsJsonObject();
        }

        void arm(String barrier, String match) throws IOException, InterruptedException {
            admin("POST", "/phase3/arm?name=" + barrier + "&match=" + encode(match));
        }

        void release(String barrier) throws IOException, InterruptedException {
            admin("POST", "/phase3/release?name=" + barrier);
        }

        int waiting(String barrier) throws IOException, InterruptedException {
            return JsonParser.parseString(admin("GET", "/phase3/barrier?name=" + barrier)).getAsJsonObject()
                    .get("waiting").getAsInt();
        }

        List<JsonObject> trace() throws IOException, InterruptedException {
            JsonArray array = JsonParser.parseString(admin("GET", "/phase3/trace")).getAsJsonArray();
            List<JsonObject> out = new ArrayList<>();
            for (JsonElement element : array) {
                out.add(element.getAsJsonObject());
            }
            return out;
        }

        /** Trace records of the given kinds whose detail contains the fragment, in node-local sequence order. */
        List<JsonObject> trace(String fragment, String... kinds) throws IOException, InterruptedException {
            List<String> wanted = List.of(kinds);
            List<JsonObject> out = new ArrayList<>();
            for (JsonObject line : trace()) {
                if ((wanted.isEmpty() || wanted.contains(line.get("kind").getAsString()))
                        && line.get("detail").getAsString().contains(fragment)) {
                    out.add(line);
                }
            }
            return out;
        }
    }

    static String encode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    // ---- webhook receiver ----

    /** In-JVM receiver. Arrival records are kept separately from the nodes' HTTP-attempt-start records. */
    static final class Receiver {
        record Arrival(long seq, long nanos, String path, String body) {
        }

        private final HttpServer server;
        private final AtomicLong seq = new AtomicLong();
        final List<Arrival> arrivals = new CopyOnWriteArrayList<>();

        Receiver() throws IOException {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
            server.createContext("/hook", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                arrivals.add(new Arrival(seq.incrementAndGet(), System.nanoTime(), exchange.getRequestURI().getPath(),
                        new String(body, StandardCharsets.UTF_8)));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
        }

        int port() {
            return server.getAddress().getPort();
        }

        String urlFor(String name) {
            return "http://host.testcontainers.internal:" + port() + "/hook/" + name;
        }

        List<Arrival> arrivalsFor(String name) {
            return arrivals.stream().filter(a -> a.path().endsWith("/" + name)).toList();
        }

        void close() {
            server.stop(0);
        }
    }
}
