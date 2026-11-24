package org.a2aproject.sdk.extras.taskstore.database.jpa;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * PostgreSQL profile for the writer-fence test: the application connects as the new identity with node
 * epoch 2 and schema generation disabled (the operator runs the DDL, as in production).
 */
public final class JpaWriterFencePostgresProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> properties = new HashMap<>();
        properties.put("quarkus.hibernate-orm.persistence-xml.ignore", "true");
        properties.put("quarkus.datasource.\"a2a-java\".db-kind", "postgresql");
        properties.put("quarkus.datasource.\"a2a-java\".devservices.enabled", "false");
        properties.put("quarkus.hibernate-orm.\"a2a-java\".datasource", "a2a-java");
        properties.put("quarkus.hibernate-orm.\"a2a-java\".packages", JpaTask.class.getPackageName());
        properties.put("quarkus.hibernate-orm.\"a2a-java\".database.generation", "none");
        properties.put("quarkus.hibernate-orm.log.sql", "false");
        properties.put("quarkus.hibernate-orm.\"a2a-java\".log.sql", "false");
        properties.put("a2a.authorization.required", "false");
        properties.put(JpaTaskMigration.NODE_EPOCH_PROPERTY, "2");
        return properties;
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(JpaWriterFenceDatabaseResource.class));
    }
}
