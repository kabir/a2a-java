package org.a2aproject.sdk.extras.taskstore.database.jpa;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** PostgreSQL profile for cross-instance webhook lane compare-and-set tests. */
public final class JpaWebhookDispatchPostgresProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> properties = new HashMap<>();
        properties.put("quarkus.hibernate-orm.persistence-xml.ignore", "true");
        properties.put("quarkus.datasource.\"a2a-java\".db-kind", "postgresql");
        properties.put("quarkus.datasource.\"a2a-java\".devservices.enabled", "false");
        properties.put("quarkus.hibernate-orm.\"a2a-java\".datasource", "a2a-java");
        properties.put("quarkus.hibernate-orm.\"a2a-java\".packages", JpaWebhookDispatchLane.class.getPackageName());
        properties.put("quarkus.hibernate-orm.\"a2a-java\".database.generation", "drop-and-create");
        properties.put("quarkus.hibernate-orm.log.sql", "false");
        properties.put("quarkus.hibernate-orm.\"a2a-java\".log.sql", "false");
        properties.put("a2a.authorization.required", "false");
        return properties;
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(JpaWebhookDispatchDatabaseResource.class));
    }
}
