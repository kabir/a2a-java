package io.a2a.server;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.logging.Logger;

import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentInterface;
import io.a2a.spec.TransportProtocol;

/**
 * Validates AgentCard transport configuration against available transport endpoints.
 */
public class AgentCardValidator {
    
    private static final Logger LOGGER = Logger.getLogger(AgentCardValidator.class.getName());
    
    /**
     * Validates the transport configuration of an AgentCard against available transports found on the classpath.
     * Logs warnings for missing transports and errors for unsupported transports.
     * 
     * @param agentCard the agent card to validate
     */
    public static void validateTransportConfiguration(AgentCard agentCard) {
        validateTransportConfiguration(agentCard, getAvailableTransports());
    }

    /**
     * Validates the transport configuration of an AgentCard against a given set of available transports.
     * This method is package-private for testability.
     *
     * @param agentCard the agent card to validate
     * @param availableTransports the set of available transport protocols
     */
    static void validateTransportConfiguration(AgentCard agentCard, Set<TransportProtocol> availableTransports) {
        Set<TransportProtocol> agentCardTransports = getAgentCardTransports(agentCard);
        
        // Check for missing transports (warn if AgentCard doesn't include all available transports)
        Set<TransportProtocol> missingTransports = availableTransports.stream()
                .filter(transport -> !agentCardTransports.contains(transport))
                .collect(Collectors.toSet());
        
        if (!missingTransports.isEmpty()) {
            LOGGER.warning(String.format(
                "AgentCard does not include all available transports. Missing: %s. " +
                "Available transports: %s. AgentCard transports: %s",
                formatTransports(missingTransports),
                formatTransports(availableTransports), 
                formatTransports(agentCardTransports)
            ));
        }
        
        // Check for unsupported transports (error if AgentCard specifies unavailable transports)
        Set<TransportProtocol> unsupportedTransports = agentCardTransports.stream()
                .filter(transport -> !availableTransports.contains(transport))
                .collect(Collectors.toSet());
        
        if (!unsupportedTransports.isEmpty()) {
            String errorMessage = String.format(
                "AgentCard specifies transport interfaces for unavailable transports: %s. " +
                "Available transports: %s. Consider removing these interfaces or adding the required transport dependencies.",
                formatTransports(unsupportedTransports),
                formatTransports(availableTransports)
            );
            LOGGER.severe(errorMessage);
            
            // Following the GitHub issue suggestion to use an error instead of warning
            throw new IllegalStateException(errorMessage);
        }
    }
    
    /**
     * Extracts all transport protocols specified in the AgentCard.
     * Includes both the preferred transport and additional interface transports.
     * 
     * @param agentCard the agent card to analyze
     * @return set of transport protocols specified in the agent card
     */
    private static Set<TransportProtocol> getAgentCardTransports(AgentCard agentCard) {
        List<String> transportStrings = new ArrayList<>();
        
        // Add preferred transport
        if (agentCard.preferredTransport() != null) {
            transportStrings.add(agentCard.preferredTransport());
        }
        
        // Add additional interface transports
        if (agentCard.additionalInterfaces() != null) {
            for (AgentInterface agentInterface : agentCard.additionalInterfaces()) {
                if (agentInterface.transport() != null) {
                    transportStrings.add(agentInterface.transport());
                }
            }
        }
        
        return transportStrings.stream()
                .distinct()
                .map(TransportProtocol::fromString)
                .collect(Collectors.toSet());
    }
    
    /**
     * Formats a set of transport protocols for logging.
     * 
     * @param transports the transport protocols to format
     * @return formatted string representation
     */
    private static String formatTransports(Set<TransportProtocol> transports) {
        return transports.stream()
                .map(TransportProtocol::asString)
                .collect(Collectors.joining(", ", "[", "]"));
    }
    
    /**
     * Discovers available transport endpoints using ServiceLoader.
     * This searches the classpath for implementations of TransportMetadata.
     * 
     * @return set of available transport protocols
     */
    private static Set<TransportProtocol> getAvailableTransports() {
        return ServiceLoader.load(TransportMetadata.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(TransportMetadata::isAvailable)
                .map(TransportMetadata::getTransportProtocol)
                .collect(Collectors.toSet());
    }
}
