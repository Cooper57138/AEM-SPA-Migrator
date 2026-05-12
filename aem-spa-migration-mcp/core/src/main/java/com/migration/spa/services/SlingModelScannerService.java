package com.migration.spa.services;

import org.apache.sling.api.resource.ResourceResolver;

import java.util.List;

/**
 * Service for scanning JCR for Sling Models that may be missing @Exporter configuration.
 * Identifies models that need Jackson exporter support for SPA JSON rendering.
 */
public interface SlingModelScannerService {

    /**
     * Scans nodes under the given apps path for Sling Models lacking @Exporter configuration.
     * Looks for nodes with slingModels:className but no slingModels:exporterName property,
     * or nodes with slingModels:exporterConfigured = false.
     *
     * @param rr       the resource resolver
     * @param appsPath the /apps path to scan
     * @return list of resource paths / class names needing @Exporter annotation
     */
    List<String> findModelsWithoutExporter(ResourceResolver rr, String appsPath);
}
