package compatibility;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.ClientTransportConfig;
import org.a2aproject.sdk.client.transport.spi.ClientTransportWrapper;

/** A pre-companion decorator compiled against the unchanged historical SPI. */
public class LegacyWrapper implements ClientTransportWrapper {
    public static boolean fail;

    public ClientTransport wrap(ClientTransport delegate, ClientTransportConfig<?> config) {
        if (fail) {
            throw new IllegalStateException("legacy configured wrapper failure");
        }
        return (ClientTransport) Proxy.newProxyInstance(ClientTransport.class.getClassLoader(),
                new Class<?>[] {ClientTransport.class}, (proxy, method, arguments) -> {
                    try {
                        return method.invoke(delegate, arguments);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
    }
}
