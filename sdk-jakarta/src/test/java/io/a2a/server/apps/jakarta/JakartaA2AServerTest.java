package io.a2a.server.apps.jakarta;


import jakarta.inject.Inject;

import io.a2a.server.apps.common.AbstractA2AServerTest;
import org.jboss.arquillian.junit5.ArquillianExtension;
import io.a2a.server.events.InMemoryQueueManager;
import io.a2a.server.tasks.TaskStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ArquillianExtension.class)
public class JakartaA2AServerTest extends AbstractA2AServerTest {

    @Deployment
    public static WebArchive createTestArchive() throws IOException {
        WebArchive archive = ShrinkWrap.create(WebArchive.class, "test.war")
//                .addPackages(true, "io.a2a.server")
//                .addPackages(true, "io.a2a.spec")
//                .addPackages(true, "io.a2a.util")
//                .addPackages(true, "io.a2a.client")
//                .addPackages(true, "io.a2a.http")
                .addClass(A2AServerResource.class)
                .addClass(A2ARequestFilter.class)
                .addAsManifestResource("META-INF/beans.xml", "beans.xml")
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
        archive.as(ZipExporter.class).exportTo(Files.newOutputStream(Paths.get("test.war"), StandardOpenOption.CREATE));
        return archive;
    }
    
    @Inject
    TaskStore taskStore;

    @Inject
    InMemoryQueueManager queueManager;

    @Override
    protected TaskStore getTaskStore() {
        return taskStore;
    }

    @Override
    protected InMemoryQueueManager getQueueManager() {
        return queueManager;
    }

    @Override
    protected void setStreamingSubscribedRunnable(Runnable runnable) {
        A2AServerResource.setStreamingIsSubscribedRunnable(runnable);
    }
}
