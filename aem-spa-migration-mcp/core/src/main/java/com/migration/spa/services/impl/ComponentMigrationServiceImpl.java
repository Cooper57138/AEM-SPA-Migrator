package com.migration.spa.services.impl;

import com.migration.spa.services.ComponentMigrationService;
import com.migration.spa.services.ResourceTypeMappingService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component(service = ComponentMigrationService.class, immediate = true)
public class ComponentMigrationServiceImpl implements ComponentMigrationService {

    private static final Logger log = LoggerFactory.getLogger(ComponentMigrationServiceImpl.class);

    /** Hard-coded upgrades: AEM foundation components → Core WCM equivalents. */
    private static final Map<String, String> DEFAULT_MAPPINGS;

    static {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("foundation/components/text",       "core/wcm/components/text/v2/text");
        defaults.put("foundation/components/image",      "core/wcm/components/image/v3/image");
        defaults.put("foundation/components/title",      "core/wcm/components/title/v3/title");
        defaults.put("foundation/components/list",       "core/wcm/components/list/v3/list");
        defaults.put("foundation/components/breadcrumb", "core/wcm/components/breadcrumb/v3/breadcrumb");
        defaults.put("foundation/components/navigation", "core/wcm/components/navigation/v1/navigation");
        defaults.put("foundation/components/carousel",   "core/wcm/components/carousel/v1/carousel");
        defaults.put("foundation/components/tabs",       "core/wcm/components/tabs/v1/tabs");
        defaults.put("foundation/components/accordion",  "core/wcm/components/accordion/v1/accordion");
        defaults.put("wcm/foundation/components/parsys", "wcm/foundation/components/responsivegrid");
        DEFAULT_MAPPINGS = Collections.unmodifiableMap(defaults);
    }

    @Reference
    private ResourceTypeMappingService resourceTypeMappingService;

    /** Active merged mappings: defaults + dynamic + static OSGi config. Volatile for visibility. */
    private volatile Map<String, String> mergedMappings;

    @Activate
    public void activate() {
        // At startup, no dynamic mappings yet — build with defaults + static config
        rebuildMappings(Collections.emptyMap());
    }

    @Override
    public String resolveTargetResourceType(String sourceResourceType) {
        if (sourceResourceType == null || sourceResourceType.isEmpty()) {
            return null;
        }
        return mergedMappings.get(sourceResourceType);
    }

    @Override
    public Map<String, String> getAllMappings() {
        return mergedMappings;
    }

    @Override
    public boolean hasMappingFor(String resourceType) {
        return resourceType != null && !resourceType.isEmpty()
                && mergedMappings.containsKey(resourceType);
    }

    @Override
    public synchronized void injectDynamicMappings(Map<String, String> dynamicMappings) {
        rebuildMappings(dynamicMappings != null ? dynamicMappings : Collections.emptyMap());
        log.info("Dynamic mappings injected: {} dynamic, {} total active",
                dynamicMappings != null ? dynamicMappings.size() : 0, mergedMappings.size());
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /** Rebuilds the active mapping table: defaults < dynamic < static config (static wins). */
    private synchronized void rebuildMappings(Map<String, String> dynamicMappings) {
        Map<String, String> merged = new HashMap<>(DEFAULT_MAPPINGS);
        merged.putAll(dynamicMappings);                                // dynamic overrides defaults
        merged.putAll(resourceTypeMappingService.getMappings());       // static OSGi config wins over both
        this.mergedMappings = Collections.unmodifiableMap(merged);
        log.debug("Mapping table rebuilt: {} defaults, {} dynamic, {} static -> {} total",
                DEFAULT_MAPPINGS.size(), dynamicMappings.size(),
                resourceTypeMappingService.getMappings().size(), merged.size());
    }
}
