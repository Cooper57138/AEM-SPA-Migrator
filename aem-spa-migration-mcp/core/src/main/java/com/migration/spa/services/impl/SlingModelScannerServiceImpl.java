package com.migration.spa.services.impl;

import com.migration.spa.services.SlingModelScannerService;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@Component(service = SlingModelScannerService.class, immediate = true)
public class SlingModelScannerServiceImpl implements SlingModelScannerService {

    private static final Logger log = LoggerFactory.getLogger(SlingModelScannerServiceImpl.class);

    private static final String PROP_SLING_MODELS_CLASS_NAME = "slingModels:className";
    private static final String PROP_SLING_MODELS_EXPORTER_NAME = "slingModels:exporterName";
    private static final String PROP_SLING_MODELS_EXPORTER_CONFIGURED = "slingModels:exporterConfigured";

    @Override
    public List<String> findModelsWithoutExporter(ResourceResolver rr, String appsPath) {
        if (rr == null || appsPath == null || appsPath.isEmpty()) {
            log.warn("findModelsWithoutExporter called with null/empty arguments");
            return Collections.emptyList();
        }

        Resource appsResource = rr.getResource(appsPath);
        if (appsResource == null) {
            log.warn("Apps path not found: {}", appsPath);
            return Collections.emptyList();
        }

        List<String> modelsNeedingExporter = new ArrayList<>();
        scanForModels(appsResource, modelsNeedingExporter);
        log.info("SlingModel scan completed under '{}': found {} models needing @Exporter",
                appsPath, modelsNeedingExporter.size());
        return modelsNeedingExporter;
    }

    private void scanForModels(Resource resource, List<String> modelsNeedingExporter) {
        if (resource == null) {
            return;
        }

        ValueMap vm = resource.getValueMap();

        // Check if this node has slingModels:exporterConfigured = false
        Boolean exporterConfigured = vm.get(PROP_SLING_MODELS_EXPORTER_CONFIGURED, Boolean.class);
        if (Boolean.FALSE.equals(exporterConfigured)) {
            String className = vm.get(PROP_SLING_MODELS_CLASS_NAME, String.class);
            String entry = buildEntry(resource.getPath(), className);
            modelsNeedingExporter.add(entry);
            log.debug("Found model needing exporter (exporterConfigured=false): {}", entry);
        }

        // Check if node has slingModels:className but no slingModels:exporterName
        String className = vm.get(PROP_SLING_MODELS_CLASS_NAME, String.class);
        if (className != null && !className.isEmpty()) {
            String exporterName = vm.get(PROP_SLING_MODELS_EXPORTER_NAME, String.class);
            if (exporterName == null || exporterName.isEmpty()) {
                // Only add if not already added
                if (exporterConfigured == null || !Boolean.FALSE.equals(exporterConfigured)) {
                    String entry = buildEntry(resource.getPath(), className);
                    modelsNeedingExporter.add(entry);
                    log.debug("Found model needing exporter (no exporterName): {}", entry);
                }
            }
        }

        // Recurse into children
        Iterator<Resource> children = resource.listChildren();
        while (children.hasNext()) {
            Resource child = children.next();
            scanForModels(child, modelsNeedingExporter);
        }
    }

    private String buildEntry(String path, String className) {
        if (className != null && !className.isEmpty()) {
            return path + " [" + className + "]";
        }
        return path;
    }
}
