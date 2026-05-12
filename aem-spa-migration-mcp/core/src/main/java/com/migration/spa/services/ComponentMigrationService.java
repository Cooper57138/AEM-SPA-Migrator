package com.migration.spa.services;

import java.util.Map;

/**
 * Service interface for resolving AEM component resource type migrations.
 * Maps legacy foundation/custom resource types to their SPA-compatible equivalents.
 *
 * <p>Holds two layers of mappings:
 * <ol>
 *   <li>Hard-coded defaults (foundation → core component upgrades)</li>
 *   <li>Custom mappings injected at runtime (auto-computed or from OSGi config)</li>
 * </ol>
 * OSGi-configured static mappings always take precedence over dynamically injected ones.
 */
public interface ComponentMigrationService {

    /**
     * Resolves the target SPA-compatible resource type for a given source resource type.
     *
     * @param sourceResourceType the legacy resource type (e.g. "wknd/components/text")
     * @return the target resource type, or null if no mapping exists
     */
    String resolveTargetResourceType(String sourceResourceType);

    /**
     * Returns all active resource type mappings (built-in defaults + injected + static config).
     */
    Map<String, String> getAllMappings();

    /**
     * Checks whether a mapping exists for the given resource type.
     */
    boolean hasMappingFor(String resourceType);

    /**
     * Injects dynamically computed mappings at migration runtime.
     *
     * <p>Called by {@code SPAMigrationProcess} after the auto-detection step has
     * computed source → target mappings from the live JCR tree. The injected
     * mappings are merged with the built-in defaults and any OSGi-configured
     * static overrides; static config wins in case of conflict.
     *
     * <p>This method is thread-safe — concurrent MCP runs inject their own
     * mapping sets independently.
     *
     * @param dynamicMappings computed mappings (source resourceType → target resourceType)
     */
    void injectDynamicMappings(Map<String, String> dynamicMappings);
}
