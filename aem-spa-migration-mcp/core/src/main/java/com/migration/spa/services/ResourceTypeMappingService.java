package com.migration.spa.services;

import java.util.Map;
import java.util.Set;

/**
 * OSGi service that provides resource type mappings — both from static OSGi
 * configuration and computed dynamically from the live JCR component tree.
 */
public interface ResourceTypeMappingService {

    /**
     * Returns all statically configured resource type mappings from OSGi config.
     * Keys are source resource types; values are target resource types.
     */
    Map<String, String> getMappings();

    /**
     * Computes source→target mappings dynamically from a set of discovered
     * component resource types.
     *
     * <p>For every entry in {@code sourceComponentResourceTypes} whose first
     * path segment matches {@code sourceAppNamespace}, replaces that prefix
     * with {@code targetAppNamespace}.
     *
     * <p>Example: given source {@code "wknd/components/text"}, sourceNS {@code "wknd"},
     * targetNS {@code "wknd-spa"}, returns mapping {@code "wknd/components/text" → "wknd-spa/components/text"}.
     *
     * @param sourceComponentResourceTypes resource types discovered under /apps
     * @param sourceAppNamespace           detected source namespace (e.g. "wknd")
     * @param targetAppNamespace           derived target namespace (e.g. "wknd-spa")
     * @return computed mappings (does not include OSGi-configured static mappings)
     */
    Map<String, String> computeDynamicMappings(Set<String> sourceComponentResourceTypes,
                                               String sourceAppNamespace,
                                               String targetAppNamespace);

    /**
     * Merges {@code dynamicMappings} with the OSGi-configured static mappings.
     * Static mappings take precedence over dynamic ones (manual config wins).
     *
     * @param dynamicMappings computed mappings from {@link #computeDynamicMappings}
     * @return merged unmodifiable map
     */
    Map<String, String> getMergedMappings(Map<String, String> dynamicMappings);
}
