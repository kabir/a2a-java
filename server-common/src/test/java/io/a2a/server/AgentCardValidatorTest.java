package io.a2a.server;

import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentInterface;
import io.a2a.spec.TransportProtocol;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AgentCardValidatorTest {

    @Test
    void testValidationWithSimpleAgentCard() {
        // Create a simple AgentCard (uses default JSONRPC transport)
        AgentCard agentCard = new AgentCard.Builder()
                .name("Test Agent")
                .description("Test Description")
                .url("http://localhost:9999")
                .version("1.0.0")
                .capabilities(new AgentCapabilities.Builder().build())
                .defaultInputModes(Collections.singletonList("text"))
                .defaultOutputModes(Collections.singletonList("text"))
                .skills(Collections.emptyList())
                .build();

        // Define available transports
        Set<String> availableTransports = Set.of(TransportProtocol.JSONRPC.asString());

        // Validation should now pass
        assertDoesNotThrow(() -> AgentCardValidator.validateTransportConfiguration(agentCard, availableTransports));
    }

    @Test
    void testValidationWithMultipleTransports() {
        // Create AgentCard that specifies multiple transports
        AgentCard agentCard = new AgentCard.Builder()
                .name("Test Agent")
                .description("Test Description")
                .url("http://localhost:9999")
                .version("1.0.0")
                .capabilities(new AgentCapabilities.Builder().build())
                .defaultInputModes(Collections.singletonList("text"))
                .defaultOutputModes(Collections.singletonList("text"))
                .skills(Collections.emptyList())
                .preferredTransport(TransportProtocol.JSONRPC.asString())
                .additionalInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), "http://localhost:9999"),
                        new AgentInterface(TransportProtocol.GRPC.asString(), "http://localhost:9000")
                ))
                .build();

        // Define available transports
        Set<String> availableTransports =
                Set.of(TransportProtocol.JSONRPC.asString(), TransportProtocol.GRPC.asString());

        // Validation should now pass
        assertDoesNotThrow(() -> AgentCardValidator.validateTransportConfiguration(agentCard, availableTransports));
    }

    @Test
    void testLogWarningWhenExtraTransportsFound() {
        // Create an AgentCard with only JSONRPC
        AgentCard agentCard = new AgentCard.Builder()
                .name("Test Agent")
                .description("Test Description")
                .url("http://localhost:9999")
                .version("1.0.0")
                .capabilities(new AgentCapabilities.Builder().build())
                .defaultInputModes(Collections.singletonList("text"))
                .defaultOutputModes(Collections.singletonList("text"))
                .skills(Collections.emptyList())
                .preferredTransport(TransportProtocol.JSONRPC.asString())
                .build();

        // Define available transports (more than in AgentCard)
        Set<String> availableTransports =
                Set.of(TransportProtocol.JSONRPC.asString(), TransportProtocol.GRPC.asString());

        // Capture logs
        Logger logger = Logger.getLogger(AgentCardValidator.class.getName());
        TestLogHandler testLogHandler = new TestLogHandler();
        logger.addHandler(testLogHandler);

        try {
            AgentCardValidator.validateTransportConfiguration(agentCard, availableTransports);
        } finally {
            logger.removeHandler(testLogHandler);
        }

        // Assert that a warning was logged
        assertTrue(testLogHandler.getLogMessages().stream()
                .anyMatch(msg -> msg.contains("AgentCard does not include all available transports. Missing: [GRPC]")));
    }

    @Test
    void testValidationWithUnavailableTransport() {
        // Create a simple AgentCard (uses default JSONRPC transport)
        AgentCard agentCard = new AgentCard.Builder()
                .name("Test Agent")
                .description("Test Description")
                .url("http://localhost:9999")
                .version("1.0.0")
                .capabilities(new AgentCapabilities.Builder().build())
                .defaultInputModes(Collections.singletonList("text"))
                .defaultOutputModes(Collections.singletonList("text"))
                .skills(Collections.emptyList())
                .build();

        // Define available transports (empty)
        Set<String> availableTransports = Collections.emptySet();

        // Should throw exception because no transports are available
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> AgentCardValidator.validateTransportConfiguration(agentCard, availableTransports));
        assertTrue(exception.getMessage().contains("unavailable transports: [JSONRPC]"));
    }

    // A simple log handler for testing
    private static class TestLogHandler extends Handler {
        private final List<String> logMessages = new java.util.ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            logMessages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }

        public List<String> getLogMessages() {
            return logMessages;
        }
    }
}
