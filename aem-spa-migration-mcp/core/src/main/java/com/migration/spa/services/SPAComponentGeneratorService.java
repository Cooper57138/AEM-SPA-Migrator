package com.migration.spa.services;

import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.ResourceResolver;

import java.util.Map;

/**
 * Generates real {@code cq:Component} JCR nodes in {@code /apps} for the SPA target namespace,
 * setting {@code sling:resourceSuperType} to the source component so rendering is inherited.
 *
 * <p>This is the critical missing piece for a full AEM → SPA migration: content rewriting
 * alone isn't enough if the target {@code sling:resourceType} has no backing component node.
 */
public interface SPAComponentGeneratorService {

    /** Result of a single component generation attempt. */
    enum GenerationStatus {
        CREATED,
        ALREADY_EXISTS,
        SKIPPED_DRY_RUN,
        FAILED
    }

    class GenerationResult {
        public final GenerationStatus status;
        public final String targetPath;
        public final String message;

        public GenerationResult(GenerationStatus status, String targetPath, String message) {
            this.status = status;
            this.targetPath = targetPath;
            this.message = message;
        }
    }

    /**
     * Creates a single {@code cq:Component} proxy node at {@code /apps/{targetResourceType}}
     * with {@code sling:resourceSuperType={sourceResourceType}}.
     *
     * @param rr                 resource resolver (caller manages commit)
     * @param sourceResourceType e.g. {@code "wknd/components/text"}
     * @param targetResourceType e.g. {@code "wknd-spa/components/text"}
     * @param componentGroup     e.g. {@code "WKND SPA"}
     * @param dryRun             if {@code true}, logs intent without writing
     * @return generation result
     */
    GenerationResult generateSPAComponent(ResourceResolver rr,
                                          String sourceResourceType,
                                          String targetResourceType,
                                          String componentGroup,
                                          boolean dryRun) throws PersistenceException;

    /**
     * Iterates all entries in {@code resourceTypeMappings} and calls
     * {@link #generateSPAComponent} for each one.
     *
     * @return number of component nodes actually created
     */
    int generateAllSPAComponents(ResourceResolver rr,
                                 Map<String, String> resourceTypeMappings,
                                 String componentGroup,
                                 boolean dryRun) throws PersistenceException;

    /**
     * Returns {@code true} if a {@code cq:Component} node already exists
     * at {@code /apps/{targetResourceType}}.
     */
    boolean componentNodeExists(ResourceResolver rr, String targetResourceType);

    /**
     * Derives a human-readable component group label from a target app namespace.
     * E.g. {@code "wknd-spa"} → {@code "WKND SPA"}.
     */
    String deriveComponentGroup(String targetAppNamespace);
}
