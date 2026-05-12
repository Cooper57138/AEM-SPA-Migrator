package com.migration.spa.services.impl;

import com.migration.spa.services.ResourceTypeMappingService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component(service = ResourceTypeMappingService.class, immediate = true)
@Designate(ocd = ResourceTypeMappingServiceImpl.Config.class)
public class ResourceTypeMappingServiceImpl implements ResourceTypeMappingService {

    private static final Logger log = LoggerFactory.getLogger(ResourceTypeMappingServiceImpl.class);

    @ObjectClassDefinition(
            name = "SPA Migration - Resource Type Mapping Service",
            description = "Optional static resource type overrides. Leave empty to rely on auto-detection."
    )
    public @interface Config {
        @AttributeDefinition(
                name = "Resource Type Mappings",
                description = "Format: source=target, one per entry. Takes precedence over auto-generated mappings."
        )
        String[] resourceTypeMappings() default {};
    }

    /** Static mappings loaded from OSGi config. May be empty. */
    private Map<String, String> staticMappings = Collections.emptyMap();

    @Activate
    @Modified
    protected void activate(Config config) {
        Map<String, String> parsed = new HashMap<>();
        String[] entries = config.resourceTypeMappings();
        if (entries != null) {
            for (String entry : entries) {
                if (entry != null && entry.contains("=")) {
                    String[] parts = entry.split("=", 2);
                    String source = parts[0].trim();
                    String target = parts[1].trim();
                    if (!source.isEmpty() && !target.isEmpty()) {
                        parsed.put(source, target);
                        log.debug("Loaded static mapping: {} -> {}", source, target);
                    }
                } else if (entry != null && !entry.trim().isEmpty()) {
                    log.warn("Ignoring malformed resource type mapping entry: '{}'", entry);
                }
            }
        }
        this.staticMappings = Collections.unmodifiableMap(parsed);
        log.info("ResourceTypeMappingService activated with {} static mappings", staticMappings.size());
    }

    @Override
    public Map<String, String> getMappings() {
        return staticMappings;
    }

    @Override
    public Map<String, String> computeDynamicMappings(Set<String> sourceComponentResourceTypes,
                                                      String sourceAppNamespace,
                                                      String targetAppNamespace) {
        if (sourceComponentResourceTypes == null || sourceComponentResourceTypes.isEmpty()
                || sourceAppNamespace == null || sourceAppNamespace.trim().isEmpty()
                || targetAppNamespace == null || targetAppNamespace.trim().isEmpty()) {
            log.warn("computeDynamicMappings called with null/empty arguments, returning empty map");
            return Collections.emptyMap();
        }

        String prefix = sourceAppNamespace + "/";
        Map<String, String> result = new LinkedHashMap<>();

        for (String rt : sourceComponentResourceTypes) {
            if (rt != null && rt.startsWith(prefix)) {
                // Replace source namespace prefix with target namespace
                String target = targetAppNamespace + "/" + rt.substring(prefix.length());
                result.put(rt, target);
                log.debug("Dynamic mapping: {} -> {}", rt, target);
            }
        }

        log.info("Computed {} dynamic mappings from {} discovered types (source='{}', target='{}')",
                result.size(), sourceComponentResourceTypes.size(), sourceAppNamespace, targetAppNamespace);
        return Collections.unmodifiableMap(result);
    }

    @Override
    public Map<String, String> getMergedMappings(Map<String, String> dynamicMappings) {
        // Dynamic mappings as base, static OSGi config mappings override them
        Map<String, String> merged = new LinkedHashMap<>();
        if (dynamicMappings != null) {
            merged.putAll(dynamicMappings);
        }
        merged.putAll(staticMappings); // static wins
        log.debug("getMergedMappings: {} dynamic + {} static = {} total",
                dynamicMappings != null ? dynamicMappings.size() : 0,
                staticMappings.size(),
                merged.size());
        return Collections.unmodifiableMap(merged);
    }
}
