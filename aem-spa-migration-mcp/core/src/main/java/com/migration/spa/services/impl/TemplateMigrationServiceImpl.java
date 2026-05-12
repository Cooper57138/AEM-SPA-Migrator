package com.migration.spa.services.impl;

import com.migration.spa.services.TemplateMigrationService;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component(service = TemplateMigrationService.class, immediate = true)
public class TemplateMigrationServiceImpl implements TemplateMigrationService {

    private static final Logger log = LoggerFactory.getLogger(TemplateMigrationServiceImpl.class);

    private static final String PROP_RESOURCE_TYPE          = "sling:resourceType";
    private static final String PROP_PRIMARY_TYPE           = "jcr:primaryType";
    private static final String EDITABLE_TEMPLATE_RT        = "wcm/core/components/editable-template";
    private static final String PROP_SPA_ENABLED            = "spa:enabled";
    private static final int    COMMIT_BATCH_SIZE           = 50;

    // -------------------------------------------------------------------------
    // Existing methods (unchanged)
    // -------------------------------------------------------------------------

    @Override
    public List<Resource> findAllEditableTemplates(ResourceResolver rr, String searchPath) {
        if (rr == null || searchPath == null || searchPath.isEmpty()) {
            log.warn("findAllEditableTemplates called with null/empty arguments");
            return Collections.emptyList();
        }

        List<Resource> templates = new ArrayList<>();

        Resource confResource = rr.getResource("/conf");
        if (confResource != null) {
            collectTemplates(confResource, templates);
        }

        Resource appsResource = rr.getResource(searchPath);
        if (appsResource != null) {
            collectTemplates(appsResource, templates);
        }

        log.info("Found {} editable templates under /conf and {}", templates.size(), searchPath);
        return templates;
    }

    @Override
    public boolean isSPAEnabled(Resource templateResource) {
        if (templateResource == null) {
            return false;
        }
        return Boolean.TRUE.equals(templateResource.getValueMap().get(PROP_SPA_ENABLED, Boolean.class));
    }

    @Override
    public void enableSPAOnTemplate(Resource templateResource, boolean dryRun) {
        if (templateResource == null) {
            log.warn("enableSPAOnTemplate called with null resource");
            return;
        }

        String path = templateResource.getPath();

        if (isSPAEnabled(templateResource)) {
            log.debug("Template already SPA-enabled, skipping: {}", path);
            return;
        }

        if (dryRun) {
            log.info("[DRY RUN] Would set spa:enabled=true on template: {}", path);
            return;
        }

        try {
            ModifiableValueMap mvm = templateResource.adaptTo(ModifiableValueMap.class);
            if (mvm == null) {
                log.error("Cannot obtain ModifiableValueMap for template: {}", path);
                return;
            }
            mvm.put(PROP_SPA_ENABLED, Boolean.TRUE);
            templateResource.getResourceResolver().commit();
            log.info("Enabled SPA on template: {}", path);
        } catch (PersistenceException e) {
            log.error("Failed to enable SPA on template: {}", path, e);
            try {
                templateResource.getResourceResolver().revert();
            } catch (Exception revertEx) {
                log.error("Failed to revert changes after error on template: {}", path, revertEx);
            }
        }
    }

    // -------------------------------------------------------------------------
    // New: Migrate template structure nodes
    // -------------------------------------------------------------------------

    @Override
    public int migrateTemplateResourceTypes(ResourceResolver rr,
                                            String confRootPath,
                                            Map<String, String> resourceTypeMappings,
                                            boolean dryRun) throws PersistenceException {
        if (rr == null || resourceTypeMappings == null || resourceTypeMappings.isEmpty()) {
            log.info("migrateTemplateResourceTypes: nothing to do (empty mappings or null args)");
            return 0;
        }

        Resource confRoot = rr.getResource(confRootPath != null ? confRootPath : "/conf");
        if (confRoot == null) {
            log.warn("Conf root not found: {}", confRootPath);
            return 0;
        }

        log.info("Scanning template structure nodes under {} for {} resource type rewrites",
                confRoot.getPath(), resourceTypeMappings.size());

        int[] count = {0};
        int[] pending = {0};

        // Walk all nodes under /conf and rewrite sling:resourceType in template subtrees
        walkAndRewrite(confRoot, resourceTypeMappings, dryRun, count, pending, rr, "template");

        // Final commit
        if (!dryRun && pending[0] > 0) {
            rr.commit();
        }

        log.info("Template structure migration complete: {} sling:resourceType properties rewritten (dryRun={})",
                count[0], dryRun);
        return count[0];
    }

    // -------------------------------------------------------------------------
    // New: Migrate policy nodes
    // -------------------------------------------------------------------------

    @Override
    public int migratePolicyResourceTypes(ResourceResolver rr,
                                          String confRootPath,
                                          Map<String, String> resourceTypeMappings,
                                          boolean dryRun) throws PersistenceException {
        if (rr == null || resourceTypeMappings == null || resourceTypeMappings.isEmpty()) {
            log.info("migratePolicyResourceTypes: nothing to do (empty mappings or null args)");
            return 0;
        }

        // Policies live under /conf/{site}/settings/wcm/policies
        // We walk the full /conf tree and rewrite any policy-context resource type we find
        Resource confRoot = rr.getResource(confRootPath != null ? confRootPath : "/conf");
        if (confRoot == null) {
            log.warn("Conf root not found for policy migration: {}", confRootPath);
            return 0;
        }

        log.info("Scanning policy nodes under {} for {} resource type rewrites",
                confRoot.getPath(), resourceTypeMappings.size());

        int[] count = {0};
        int[] pending = {0};

        walkAndRewrite(confRoot, resourceTypeMappings, dryRun, count, pending, rr, "policy");

        if (!dryRun && pending[0] > 0) {
            rr.commit();
        }

        log.info("Policy migration complete: {} sling:resourceType properties rewritten (dryRun={})",
                count[0], dryRun);
        return count[0];
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * BFS walk over {@code root} and its descendants. Rewrites any
     * {@code sling:resourceType} value that is a key in {@code mappings}.
     */
    private void walkAndRewrite(Resource root,
                                Map<String, String> mappings,
                                boolean dryRun,
                                int[] count,
                                int[] pending,
                                ResourceResolver rr,
                                String context) throws PersistenceException {

        Deque<Resource> queue = new ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            Resource current = queue.poll();
            ValueMap vm = current.getValueMap();
            String currentRt = vm.get(PROP_RESOURCE_TYPE, String.class);

            if (currentRt != null && mappings.containsKey(currentRt)) {
                String newRt = mappings.get(currentRt);
                if (dryRun) {
                    log.info("[DRY RUN] [{}] Would rewrite {} at {}: {} -> {}",
                            context, PROP_RESOURCE_TYPE, current.getPath(), currentRt, newRt);
                } else {
                    ModifiableValueMap mvm = current.adaptTo(ModifiableValueMap.class);
                    if (mvm != null) {
                        mvm.put(PROP_RESOURCE_TYPE, newRt);
                        log.info("[{}] Rewrote sling:resourceType at {}: {} -> {}",
                                context, current.getPath(), currentRt, newRt);
                        pending[0]++;
                        if (pending[0] >= COMMIT_BATCH_SIZE) {
                            rr.commit();
                            pending[0] = 0;
                        }
                    } else {
                        log.warn("[{}] Cannot get ModifiableValueMap at {}", context, current.getPath());
                    }
                }
                count[0]++;
            }

            // Enqueue children
            Iterator<Resource> children = current.listChildren();
            while (children.hasNext()) {
                queue.add(children.next());
            }
        }
    }

    private void collectTemplates(Resource resource, List<Resource> templates) {
        if (resource == null) {
            return;
        }

        String resourceType = resource.getValueMap().get(PROP_RESOURCE_TYPE, String.class);
        if (resourceType != null && resourceType.contains(EDITABLE_TEMPLATE_RT)) {
            templates.add(resource);
            log.debug("Found editable template: {}", resource.getPath());
        }

        Iterator<Resource> children = resource.listChildren();
        while (children.hasNext()) {
            collectTemplates(children.next(), templates);
        }
    }
}
