package com.migration.spa.services;

import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import java.util.List;
import java.util.Map;

/**
 * Service for locating and migrating AEM editable templates and component policies
 * to SPA-compatible resource types.
 */
public interface TemplateMigrationService {

    /**
     * Finds all editable templates under the given search path.
     * Searches both /conf and /apps paths for editable template resources.
     */
    List<Resource> findAllEditableTemplates(ResourceResolver rr, String searchPath);

    /**
     * Returns true if the given template resource already has spa:enabled = true.
     */
    boolean isSPAEnabled(Resource templateResource);

    /**
     * Enables SPA support on the given template resource by setting spa:enabled = true.
     * In dry-run mode, only logs what would be changed without writing.
     */
    void enableSPAOnTemplate(Resource templateResource, boolean dryRun);

    /**
     * Walks all editable template structure nodes under {@code /conf} and rewrites
     * any {@code sling:resourceType} that is a key in {@code resourceTypeMappings}.
     *
     * <p>Covers both {@code structure/jcr:content} (page-level component type)
     * and all descendant component nodes placed in the template structure.
     * Also covers {@code initial/jcr:content} subtrees.
     *
     * @param rr                   resource resolver
     * @param confRootPath         root path to scan (typically {@code "/conf"})
     * @param resourceTypeMappings source → target mapping table
     * @param dryRun               if true, log only without writing
     * @return total count of sling:resourceType properties rewritten
     */
    int migrateTemplateResourceTypes(ResourceResolver rr,
                                     String confRootPath,
                                     Map<String, String> resourceTypeMappings,
                                     boolean dryRun) throws PersistenceException;

    /**
     * Walks all component policy nodes under {@code /conf} and rewrites
     * any {@code sling:resourceType} that is a key in {@code resourceTypeMappings}.
     *
     * <p>Policy nodes live at {@code /conf/{site}/settings/wcm/policies/**}.
     * The {@code sling:resourceType} on a {@code cq:Policy} node identifies which
     * component the policy belongs to, and must be updated to the new SPA resource type.
     *
     * @param rr                   resource resolver
     * @param confRootPath         root path to scan (typically {@code "/conf"})
     * @param resourceTypeMappings source → target mapping table
     * @param dryRun               if true, log only without writing
     * @return total count of sling:resourceType properties rewritten
     */
    int migratePolicyResourceTypes(ResourceResolver rr,
                                   String confRootPath,
                                   Map<String, String> resourceTypeMappings,
                                   boolean dryRun) throws PersistenceException;
}
