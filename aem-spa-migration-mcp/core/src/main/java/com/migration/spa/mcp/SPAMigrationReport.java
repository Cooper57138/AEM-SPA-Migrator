package com.migration.spa.mcp;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ChildResource;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sling Model that reads back the SPA migration report node from JCR.
 * Adapt from a Resource pointing to /var/spa-migration/reports/{timestamp}.
 */
@Model(adaptables = Resource.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class SPAMigrationReport {

    @ValueMapValue
    private Long totalPages;

    @ValueMapValue
    private Long totalComponents;

    @ValueMapValue
    private Long migratedComponents;

    @ValueMapValue
    private Long migratedTemplates;

    @ValueMapValue
    private Long generatedStubs;

    @ValueMapValue
    private Boolean dryRun;

    @ValueMapValue
    private String status;

    @ValueMapValue
    private String timestamp;

    @ChildResource
    private List<Resource> modelsNeedingExporterNodes;

    public Long getTotalPages() {
        return totalPages != null ? totalPages : 0L;
    }

    public Long getTotalComponents() {
        return totalComponents != null ? totalComponents : 0L;
    }

    public Long getMigratedComponents() {
        return migratedComponents != null ? migratedComponents : 0L;
    }

    public Long getMigratedTemplates() {
        return migratedTemplates != null ? migratedTemplates : 0L;
    }

    public Long getGeneratedStubs() {
        return generatedStubs != null ? generatedStubs : 0L;
    }

    public Boolean getDryRun() {
        return dryRun != null ? dryRun : false;
    }

    public String getStatus() {
        return status != null ? status : "UNKNOWN";
    }

    public String getTimestamp() {
        return timestamp;
    }

    public List<String> getModelsNeedingExporter() {
        if (modelsNeedingExporterNodes == null || modelsNeedingExporterNodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Resource node : modelsNeedingExporterNodes) {
            String className = node.getValueMap().get("className", String.class);
            if (className != null) {
                result.add(className);
            } else {
                result.add(node.getPath());
            }
        }
        return result;
    }

    /**
     * Returns a human-readable summary of the migration report.
     */
    public String getSummary() {
        return String.format(
                "SPA Migration Report [%s] | Status: %s | DryRun: %s | " +
                "Pages: %d | Components: %d | Migrated: %d | Templates: %d | Stubs: %d | Models needing @Exporter: %d",
                timestamp,
                getStatus(),
                getDryRun(),
                getTotalPages(),
                getTotalComponents(),
                getMigratedComponents(),
                getMigratedTemplates(),
                getGeneratedStubs(),
                getModelsNeedingExporter().size()
        );
    }
}
