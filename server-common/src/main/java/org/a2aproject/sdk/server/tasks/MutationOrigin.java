package org.a2aproject.sdk.server.tasks;

import org.a2aproject.sdk.spec.Message.Role;

/** Authenticated source of a proposed task mutation. */
public enum MutationOrigin {
    /** An authenticated client/user ingress. */
    USER(Role.ROLE_USER),
    /** An agent-side task delta. */
    AGENT(Role.ROLE_AGENT);

    private final Role artifactRole;

    MutationOrigin(Role artifactRole) {
        this.artifactRole = artifactRole;
    }

    Role artifactRole() {
        return artifactRole;
    }
}
